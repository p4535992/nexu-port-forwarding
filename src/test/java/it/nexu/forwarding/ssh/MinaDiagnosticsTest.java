package it.nexu.forwarding.ssh;

import it.nexu.forwarding.model.TunnelProfile;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import static org.junit.jupiter.api.Assertions.*;

final class MinaDiagnosticsTest {
    private final TunnelProfile profile = TunnelProfile.example();
    @Test void timeoutExplainsDirectConnectionAndSystemProxy() {
        TunnelBackend.Failure f=MinaTunnelBackend.connectionFailure(profile,new SocketTimeoutException("connect timed out"));
        assertTrue(f.getMessage().contains("Timeout")); assertTrue(f.getMessage().contains("proxy HTTP/SOCKS")); assertTrue(f.retryable());
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
}
