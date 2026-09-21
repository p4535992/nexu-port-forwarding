package it.nexu.forwarding.ssh;

import it.nexu.forwarding.model.TunnelProfile;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import static org.junit.jupiter.api.Assertions.*;

final class MinaDiagnosticsTest {
    private final TunnelProfile profile = TunnelProfile.example();
    @Test void timeoutExplainsDirectConnectionAndSystemProxy() {
        TunnelBackend.Failure f=MinaTunnelBackend.connectionFailure(profile,new SocketTimeoutException("connect timed out"));
        assertTrue(f.getMessage().contains("Timeout")); assertTrue(f.getMessage().contains("proxy HTTP/SOCKS")); assertTrue(f.retryable());
    }
    @Test void missingRuntimeClassIsNotMisreportedAsNetwork() {
        TunnelBackend.Failure f=MinaTunnelBackend.connectionFailure(profile,
            new IOException("wrapper",new ClassNotFoundException("org.example.MissingRuntimeClass")));
        assertTrue(f.getMessage().contains("dipendenza Java mancante"));
        assertTrue(f.getMessage().contains("org.example.MissingRuntimeClass"));
        assertTrue(f.getMessage().contains("Non è un errore di DNS"));
        assertFalse(f.retryable());
    }
    @Test void dnsFailureIsSpecific() {
        assertTrue(MinaTunnelBackend.connectionFailure(profile,new UnknownHostException(profile.sshHost())).getMessage().startsWith("DNS:"));
    }
    @Test void refusalIsSpecificEvenWhenWrapped() {
        assertTrue(MinaTunnelBackend.connectionFailure(profile,new IOException("wrapper",new ConnectException("Connection refused"))).getMessage().contains("Connessione rifiutata"));
    }
    @Test void noRouteIsSpecific() {
        assertTrue(MinaTunnelBackend.connectionFailure(profile,new NoRouteToHostException("No route to host")).getMessage().contains("Nessun percorso di rete"));
    }
    @Test void authenticationSaysServerWasReached() {
        TunnelBackend.Failure f=MinaTunnelBackend.authenticationFailure(profile,new IOException("auth failed"));
        assertTrue(f.getMessage().contains("Server SSH raggiunto")); assertFalse(f.retryable());
    }
    @Test void localOccupiedPortIsDetectedBeforeSsh() throws Exception {
        try (ServerSocket occupied = new ServerSocket()) {
            occupied.bind(new InetSocketAddress("127.0.0.1",0));
            TunnelProfile p = new TunnelProfile(profile.id(),profile.name(),TunnelProfile.Mode.LOCAL,
                "does-not-matter.invalid",22,profile.username(),"127.0.0.1",occupied.getLocalPort(),
                profile.targetHost(),profile.targetPort(),profile.auth(),profile.privateKey(),
                profile.connectTimeoutSeconds(),profile.keepAliveSeconds(),profile.keepAliveMisses(),
                false,profile.reconnectAttempts(),profile.reconnectDelaySeconds(),profile.notes());
            TunnelBackend.Failure f = assertThrows(TunnelBackend.Failure.class, () -> MinaTunnelBackend.preflightLocalListener(p));
            assertTrue(f.getMessage().startsWith("Porta locale già in uso:"));
            assertTrue(f.getMessage().contains("Tabby o MobaXterm"));
            assertFalse(f.retryable());
        }
    }
    @Test void remoteListenerIsNotProbedLocally() throws Exception {
        try (ServerSocket occupied = new ServerSocket()) {
            occupied.bind(new InetSocketAddress("127.0.0.1",0));
            TunnelProfile p = new TunnelProfile(profile.id(),profile.name(),TunnelProfile.Mode.REMOTE,
                profile.sshHost(),profile.sshPort(),profile.username(),"127.0.0.1",occupied.getLocalPort(),
                profile.targetHost(),profile.targetPort(),profile.auth(),profile.privateKey(),
                profile.connectTimeoutSeconds(),profile.keepAliveSeconds(),profile.keepAliveMisses(),
                false,profile.reconnectAttempts(),profile.reconnectDelaySeconds(),profile.notes());
            assertDoesNotThrow(() -> MinaTunnelBackend.preflightLocalListener(p));
        }
    }
}
