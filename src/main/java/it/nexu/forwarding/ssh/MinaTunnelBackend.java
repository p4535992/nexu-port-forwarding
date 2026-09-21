package it.nexu.forwarding.ssh;

import it.nexu.forwarding.config.HostKeyStore;
import it.nexu.forwarding.model.TunnelProfile;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.client.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.forward.ForwardingFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Uses SSH directly: no PowerShell child process, terminal, or password on a command line. */
public final class MinaTunnelBackend implements TunnelBackend {
    private final HostKeyStore hostKeys;
    private final HostTrustPrompt prompt;
    public MinaTunnelBackend(HostKeyStore hostKeys, HostTrustPrompt prompt) { this.hostKeys = hostKeys; this.prompt = prompt; }

    @Override public Connection open(TunnelProfile p, char[] secret, Cancellation token) throws Exception {
        token.check();
        if (p.auth() == TunnelProfile.Auth.PASSWORD && secret.length == 0)
            throw new Failure("Inserire la password SSH.", false);
        SshClient client = SshClient.setUpDefaultClient();
        AtomicReference<String> rejectedKey = new AtomicReference<>();
        boolean handedOff = false;
        AutoCloseable cancellationHook = token.onCancel(() -> client.close(true));
        try {
            // Profiles are self-contained: do not import ~/.ssh/config, proxy commands, or ambient identities.
            client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
            client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
            // Null agent/X11 policies deny those channels. TCP is scoped to this profile's destination.
            client.setForwardingFilter(ForwardingFilter.asForwardingFilter(null, null, new ProfileForwardingFilter(p, token)));
            client.setUserAuthFactories(p.auth() == TunnelProfile.Auth.PASSWORD
                ? List.of(UserAuthPasswordFactory.INSTANCE) : List.of(UserAuthPublicKeyFactory.INSTANCE));
            client.setServerKeyVerifier((session, address, key) -> {
                try {
                    if (token.isCancelled()) return false;
                    String fingerprint = KeyUtils.getFingerPrint(key);
                    HostKeyStore.Match match = hostKeys.check(p.hostKeyId(), fingerprint);
                    if (match == HostKeyStore.Match.CHANGED) {
                        rejectedKey.set("CHIAVE HOST CAMBIATA per " + p.hostKeyId() + ". Attesa " + hostKeys.expected(p.hostKeyId()) + "; ricevuta " + fingerprint + ". Verificare con l'amministratore.");
                        return false;
                    }
                    if (match == HostKeyStore.Match.MATCH) return true;
                    HostTrustPrompt.Decision decision = prompt.ask(p, fingerprint, token);
                    if (token.isCancelled() || decision == HostTrustPrompt.Decision.REJECT) {
                        rejectedKey.set("Chiave host non autorizzata. Connessione annullata."); return false;
                    }
                    hostKeys.accept(p.hostKeyId(), fingerprint, decision == HostTrustPrompt.Decision.REMEMBER);
                    return true;
                } catch (Exception e) {
                    rejectedKey.set("Impossibile verificare o salvare la chiave host. Connessione rifiutata."); return false;
                }
            });
            // Apache MINA duration properties accept numeric milliseconds. Reply-wait bounds the wait
            // for an SSH heartbeat response; it is NOT exactly OpenSSH's missed-message counter.
            client.getProperties().put("heartbeat-interval", p.keepAliveSeconds() * 1000L);
            client.getProperties().put("heartbeat-reply-wait", p.keepAliveSeconds() * p.keepAliveMisses() * 1000L);
            client.getProperties().put("idle-timeout", 0L);
            client.getProperties().put("auth-timeout", (p.connectTimeoutSeconds() + 120L) * 1000L);
            client.getProperties().put("tcpip-forwarding-request-timeout", p.connectTimeoutSeconds() * 1000L);
            token.check();
            client.start();
            ClientSession session;
            try {
                session = client.connect(p.username(), p.sshHost(), p.sshPort())
                    .verify(p.connectTimeoutSeconds() * 1000L).getSession();
            } catch (Exception e) {
                token.check();
                if (rejectedKey.get() != null) throw new Failure(rejectedKey.get(), false, e);
                throw new Failure("Connessione SSH non riuscita: controllare host, porta, rete e timeout.", true, e);
            }
            token.check();
            String password = new String(secret);
            try {
                if (p.auth() == TunnelProfile.Auth.PASSWORD) session.addPasswordIdentity(password);
                else {
                    Path keyFile = Path.of(p.privateKey());
                    if (!Files.isRegularFile(keyFile) || !Files.isReadable(keyFile))
                        throw new Failure("Chiave privata inesistente o non leggibile.", false);
                    FileKeyPairProvider provider = new FileKeyPairProvider(keyFile);
                    provider.setPasswordFinder(FilePasswordProvider.of(password));
                    boolean anyKey = false;
                    for (KeyPair pair : provider.loadKeys(session)) { session.addPublicKeyIdentity(pair); anyKey = true; }
                    if (!anyKey) throw new Failure("Nessuna chiave privata caricata: verificare formato e passphrase.", false);
                }
                // Includes time for the explicit first-use host-key confirmation dialog.
                session.auth().verify((p.connectTimeoutSeconds() + 120L) * 1000L);
            } catch (Exception e) {
                token.check();
                if (rejectedKey.get() != null) throw new Failure(rejectedKey.get(), false, e);
                if (e instanceof Failure) throw e;
                throw new Failure("Autenticazione SSH fallita. Controllare utente, password/chiave, passphrase e metodo consentito dal server.", false, e);
            } finally {
                if (p.auth() == TunnelProfile.Auth.PASSWORD) session.removePasswordIdentity(password);
                password = null;
            }
            token.check();
            DynamicSocksForwarder dynamic = null;
            try {
                SshdSocketAddress bind = new SshdSocketAddress(p.bindHost(), p.bindPort());
                switch (p.mode()) {
                    case REMOTE -> session.startRemotePortForwarding(bind, new SshdSocketAddress(p.targetHost(),p.targetPort()));
                    case LOCAL -> session.startLocalPortForwarding(bind, new SshdSocketAddress(p.targetHost(),p.targetPort()));
                    // MINA 2.19.0 has a fragmented SOCKS5 parsing bug in its built-in -D frontend.
                    // Use our stream-safe SOCKS frontend while still creating target channels through MINA/SSH.
                    case DYNAMIC -> dynamic = DynamicSocksForwarder.start(session,p,token);
                }
            } catch (Exception e) {
                if (dynamic != null) dynamic.close();
                token.check();
                throw new Failure("Forwarding rifiutato: porta occupata, bind non disponibile o policy SSH (AllowTcpForwarding / PermitListen / GatewayPorts).", false, e);
            }
            token.check();
            DynamicSocksForwarder socks = dynamic;
            AtomicBoolean disposed = new AtomicBoolean();
            Connection result = new Connection() {
                @Override public boolean isOpen() { return session.isOpen() && session.isAuthenticated() && !disposed.get() && (socks == null || socks.isOpen()); }
                @Override public String closedReason() { return "Connessione SSH interrotta o keepalive scaduto."; }
                @Override public void close() {
                    if (!disposed.compareAndSet(false, true)) return;
                    if (socks != null) socks.close();
                    try { cancellationHook.close(); } catch (Exception ignored) { }
                    session.close(true);
                    client.stop();
                }
            };
            handedOff = true;
            return result;
        } finally {
            if (!handedOff) {
                try { cancellationHook.close(); } catch (Exception ignored) { }
                client.stop();
            }
        }
    }
}
