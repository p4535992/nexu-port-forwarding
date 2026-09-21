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
import org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.forward.ForwardingFilter;
import java.net.BindException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
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
        preflightLocalListener(p);
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
            // Avoid ServiceLoader/reflection differences in jpackage images: NIO2 is our explicit runtime backend.
            client.setIoServiceFactoryFactory(new Nio2ServiceFactoryFactory());
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
                throw connectionFailure(p, e);
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
                throw authenticationFailure(p, e);
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
                throw forwardingFailure(p, e);
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

    public static void preflightLocalListener(TunnelProfile p) throws Failure {
        if (p.mode() == TunnelProfile.Mode.REMOTE) return;
        InetSocketAddress address = new InetSocketAddress(p.bindHost(), p.bindPort());
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(address);
        } catch (Exception e) {
            String messages = causeMessages(e).toLowerCase(java.util.Locale.ROOT);
            if (hasCause(e, BindException.class) && (messages.contains("already in use")
                || messages.contains("address in use") || messages.contains("only one usage"))) {
                throw new Failure("Porta locale già in uso: " + p.listener()
                    + ". Un altro processo (per esempio Tabby o MobaXterm) sta probabilmente già ascoltando su questa porta. "
                    + "Chiudilo oppure scegli un'altra porta di ascolto.", false, e);
            }
            throw new Failure("Impossibile usare l'indirizzo di ascolto locale " + p.listener()
                + ". Verificare che l'indirizzo appartenga a questo PC e che firewall/policy locali consentano il bind.", false, e);
        }
    }

    static Failure connectionFailure(TunnelProfile p, Exception error) {
        String endpoint = p.sshHost() + ":" + p.sshPort();
        Throwable root = rootCause(error);
        String messages = causeMessages(error).toLowerCase(java.util.Locale.ROOT);
        if (hasCause(error, ClassNotFoundException.class) || hasCause(error, NoClassDefFoundError.class))
            return runtimeDependencyFailure(error);
        if (hasCause(error, UnknownHostException.class) || hasCause(error, UnresolvedAddressException.class))
            return new Failure("DNS: impossibile risolvere il server SSH «" + p.sshHost() + "». Verificare DNS, VPN e suffissi DNS aziendali. Nessuna connessione TCP è stata aperta.", true, error);
        if (hasCause(error, SocketTimeoutException.class) || messages.contains("timed out") || messages.contains("timeout"))
            return new Failure("Timeout durante la connessione TCP/SSH diretta a " + endpoint + " dopo " + p.connectTimeoutSeconds() + " s. Nexu Port Forwarding non usa automaticamente il proxy HTTP/SOCKS configurato nel sistema. Se la rete aziendale richiede proxy o VPN, la connessione diretta può essere bloccata.", true, error);
        if (hasCause(error, NoRouteToHostException.class) || messages.contains("no route") || messages.contains("network is unreachable"))
            return new Failure("Nessun percorso di rete verso " + endpoint + ". Verificare VPN, routing, firewall e rete aziendale. La connessione SSH è diretta e non usa automaticamente il proxy di sistema.", true, error);
        if (hasCause(error, ConnectException.class) && (messages.contains("refused") || messages.contains("actively refused")))
            return new Failure("Connessione rifiutata da " + endpoint + ". L'host è raggiungibile, ma la porta SSH non accetta la connessione: possibile porta errata, servizio SSH spento o firewall con rifiuto esplicito.", true, error);
        if (messages.contains("reset") || messages.contains("closed") || messages.contains("eof"))
            return new Failure("Connessione verso " + endpoint + " aperta ma interrotta durante l'handshake SSH. Possibili cause: servizio non SSH sulla porta, firewall/proxy trasparente o chiusura dal server.", true, error);
        return new Failure("Connessione TCP/SSH diretta verso " + endpoint + " fallita (" + root.getClass().getSimpleName() + "). Il proxy HTTP/SOCKS di sistema non viene usato automaticamente. Verificare DNS, VPN, firewall, porta SSH e policy della rete aziendale.", true, error);
    }

    static Failure runtimeDependencyFailure(Throwable error) {
        String missing = missingClassName(error);
        String suffix = missing.isEmpty() ? "" : " (" + missing + ")";
        return new Failure("Errore runtime SSH: dipendenza Java mancante" + suffix
            + ". Il pacchetto dell'applicazione è incompleto o incompatibile; reinstallare/aggiornare Nexu Port Forwarding. "
            + "Non è un errore di DNS, firewall, proxy o credenziali.", false,
            error instanceof Exception e ? e : new Exception(error));
    }

    private static String missingClassName(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError) {
                String value = t.getMessage();
                if (value == null) return "";
                value = value.trim().replace('/', '.');
                return value.matches("[A-Za-z0-9_.$-]{1,240}") ? value : "";
            }
        }
        return "";
    }

    static Failure authenticationFailure(TunnelProfile p, Exception error) {
        String endpoint = p.sshHost() + ":" + p.sshPort();
        String messages = causeMessages(error).toLowerCase(java.util.Locale.ROOT);
        if (hasCause(error, SocketTimeoutException.class) || messages.contains("timed out") || messages.contains("timeout"))
            return new Failure("Timeout durante handshake/autenticazione SSH su " + endpoint + ". Il collegamento TCP è stato avviato, ma il server non ha completato l'autenticazione nei tempi previsti. Verificare latenza, MFA/metodi non supportati e policy SSH del server.", false, error);
        return new Failure("Server SSH raggiunto su " + endpoint + ", ma autenticazione fallita per l'utente «" + p.username() + "» con metodo " + p.auth().name() + ". Controllare utente, password/chiave, passphrase e i metodi consentiti dal server.", false, error);
    }

    static Failure forwardingFailure(TunnelProfile p, Exception error) {
        if (hasCause(error, BindException.class))
            return new Failure("Impossibile aprire la porta di ascolto " + p.listener() + ": indirizzo/porta già occupati o non disponibili.", false, error);
        return new Failure("Connessione SSH autenticata, ma il forwarding è stato rifiutato. Verificare porta di ascolto e policy SSH del server (AllowTcpForwarding / PermitListen / GatewayPorts).", false, error);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause()) if (type.isInstance(t)) return true;
        return false;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable root = error;
        for (int i = 0; i < 20 && root.getCause() != null && root.getCause() != root; i++) root = root.getCause();
        return root;
    }

    private static String causeMessages(Throwable error) {
        StringBuilder out = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) if (t.getMessage() != null) out.append(' ').append(t.getMessage());
        return out.toString();
    }

}
