package it.nexu.forwarding;

import it.nexu.forwarding.config.HostKeyStore;
import it.nexu.forwarding.model.TunnelProfile;
import it.nexu.forwarding.ssh.*;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Integration tests bind ONLY 127.0.0.1. They never use the example's real endpoints. */
final class MinaIntegrationTest {
    @TempDir Path directory;

    @Test void remoteForwardingTransfersTcpBothWays() throws Exception { echoThrough(TunnelProfile.Mode.REMOTE); }
    @Test void localForwardingTransfersTcpBothWays() throws Exception { echoThrough(TunnelProfile.Mode.LOCAL); }

    @Test void dynamicSocks5TransfersTcpAndClosesListener() throws Exception { echoThroughSocks(5, false); }
    @Test void dynamicSocks5AcceptsClientChosenHostnames() throws Exception { echoThroughSocks(5, true); }
    @Test void dynamicSocks4TransfersTcp() throws Exception { echoThroughSocks(4, false); }

    private void echoThroughSocks(int version, boolean domain) throws Exception {
        try (Fixture fixture = new Fixture(directory); Echo first = new Echo(); Echo second = new Echo()) {
            int port = freePort();
            TunnelProfile p = profile(fixture.server.getPort(),port,first.port(),TunnelProfile.Mode.DYNAMIC);
            try (TunnelBackend.Connection tunnel = backend(HostTrustPrompt.Decision.REMEMBER).open(p,"test-password".toCharArray(),new Cancellation())) {
                assertTrue(tunnel.isOpen());
                // One SOCKS listener must route to two independently chosen targets, not a fixed -L destination.
                for (int targetPort : new int[]{first.port(),second.port()}) {
                    try (Socket socket = new Socket("127.0.0.1",port)) {
                        socket.setSoTimeout(5000);
                        java.io.DataOutputStream out = new java.io.DataOutputStream(socket.getOutputStream());
                        java.io.DataInputStream in = new java.io.DataInputStream(socket.getInputStream());
                        if (version == 5) {
                            out.write(new byte[]{5,1,0}); out.flush();
                            assertArrayEquals(new byte[]{5,0},in.readNBytes(2));
                            out.write(new byte[]{5,1,0});
                            if (domain) {
                                byte[] host="localhost".getBytes(StandardCharsets.US_ASCII);
                                out.writeByte(3); out.writeByte(host.length); out.write(host);
                            } else { out.writeByte(1); out.write(new byte[]{127,0,0,1}); }
                            out.writeShort(targetPort); out.flush();
                            assertEquals(5,in.readUnsignedByte()); assertEquals(0,in.readUnsignedByte());
                            assertEquals(0,in.readUnsignedByte());
                            int atyp=in.readUnsignedByte();
                            int length=switch(atyp){case 1 -> 4; case 4 -> 16; case 3 -> in.readUnsignedByte(); default -> throw new IOException("Invalid SOCKS reply");};
                            assertEquals(length,in.readNBytes(length).length); in.readUnsignedShort();
                        } else {
                            out.writeByte(4); out.writeByte(1); out.writeShort(targetPort);
                            out.write(new byte[]{127,0,0,1,0}); out.flush();
                            byte[] reply=in.readNBytes(8); assertEquals(8,reply.length); assertEquals(90,reply[1]&255);
                        }
                        byte[] payload="SOCKS TCP loopback test\n".repeat(200).getBytes(StandardCharsets.UTF_8);
                        out.write(payload); out.flush(); assertArrayEquals(payload,in.readNBytes(payload.length));
                    }
                }
            }
            assertPortEventuallyClosed(port);
        }
    }

    private void echoThrough(TunnelProfile.Mode mode) throws Exception {
        try (Fixture fixture = new Fixture(directory); Echo echo = new Echo()) {
            int bindPort = freePort();
            TunnelProfile profile = profile(fixture.server.getPort(),bindPort,echo.port(),mode);
            MinaTunnelBackend backend = backend(HostTrustPrompt.Decision.REMEMBER);
            try (TunnelBackend.Connection tunnel = backend.open(profile,"test-password".toCharArray(),new Cancellation())) {
                assertTrue(tunnel.isOpen());
                try (Socket socket = new Socket("127.0.0.1",bindPort)) {
                    socket.setSoTimeout(5000);
                    byte[] sent = "nexu-integration-test\n".repeat(400).getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(sent); socket.getOutputStream().flush();
                    assertArrayEquals(sent,socket.getInputStream().readNBytes(sent.length));
                }
            }
            assertPortEventuallyClosed(bindPort);
        }
    }
    @Test void wrongPasswordDoesNotCreateAForward() throws Exception {
        try (Fixture fixture = new Fixture(directory); Echo echo = new Echo()) {
            TunnelProfile profile = profile(fixture.server.getPort(),freePort(),echo.port(),TunnelProfile.Mode.REMOTE);
            TunnelBackend.Failure error = assertThrows(TunnelBackend.Failure.class,
                () -> backend(HostTrustPrompt.Decision.REMEMBER).open(profile,"wrong".toCharArray(),new Cancellation()));
            assertFalse(error.retryable());
        }
    }
    @Test void rejectingHostKeyPreventsPasswordAuthentication() throws Exception {
        try (Fixture fixture = new Fixture(directory); Echo echo = new Echo()) {
            TunnelProfile profile = profile(fixture.server.getPort(),freePort(),echo.port(),TunnelProfile.Mode.REMOTE);
            TunnelBackend.Failure error = assertThrows(TunnelBackend.Failure.class,
                () -> backend(HostTrustPrompt.Decision.REJECT).open(profile,"test-password".toCharArray(),new Cancellation()));
            assertFalse(error.retryable()); assertEquals(0,fixture.authenticationAttempts.get());
        }
    }
    @Test void conflictingRemoteBindFailsRatherThanBecomingActive() throws Exception {
        try (Fixture fixture = new Fixture(directory); Echo echo = new Echo(); ServerSocket occupied = localSocket()) {
            TunnelProfile profile = profile(fixture.server.getPort(),occupied.getLocalPort(),echo.port(),TunnelProfile.Mode.REMOTE);
            TunnelBackend.Failure error = assertThrows(TunnelBackend.Failure.class,
                () -> backend(HostTrustPrompt.Decision.REMEMBER).open(profile,"test-password".toCharArray(),new Cancellation()));
            assertFalse(error.retryable());
        }
    }
    @Test void changedPinIsRejectedBeforeAuthentication() throws Exception {
        try (Fixture fixture = new Fixture(directory); Echo echo = new Echo()) {
            TunnelProfile profile = profile(fixture.server.getPort(),freePort(),echo.port(),TunnelProfile.Mode.REMOTE);
            HostKeyStore pins = new HostKeyStore(directory.resolve("changed.properties"));
            pins.accept(profile.hostKeyId(),"SHA256:"+"Z".repeat(43),true);
            MinaTunnelBackend backend = new MinaTunnelBackend(pins,(p,fp,c) -> {
                fail("A changed key must not offer automatic acceptance"); return HostTrustPrompt.Decision.REJECT;
            });
            assertThrows(TunnelBackend.Failure.class,()->backend.open(profile,"test-password".toCharArray(),new Cancellation()));
            assertEquals(0,fixture.authenticationAttempts.get());
        }
    }
    private MinaTunnelBackend backend(HostTrustPrompt.Decision decision) throws IOException {
        return new MinaTunnelBackend(new HostKeyStore(directory.resolve("pins-"+UUID.randomUUID()+".properties")),(p,fp,c)->decision);
    }
    private static TunnelProfile profile(int sshPort,int bindPort,int targetPort,TunnelProfile.Mode mode) {
        return new TunnelProfile(UUID.randomUUID(),"Loopback test",mode,"127.0.0.1",sshPort,"test",
            "127.0.0.1",bindPort,"127.0.0.1",targetPort,TunnelProfile.Auth.PASSWORD,"",10,2,2,false,1,1,"");
    }
    private static void assertPortEventuallyClosed(int port) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1",port),250);
            } catch (IOException expected) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Forwarding listener remained reachable after the tunnel was closed: " + port);
    }
    private static int freePort() throws IOException { try (ServerSocket socket=localSocket()) { return socket.getLocalPort(); } }
    private static ServerSocket localSocket() throws IOException {
        ServerSocket socket = new ServerSocket(); socket.bind(new InetSocketAddress("127.0.0.1",0)); return socket;
    }
    private static final class Fixture implements AutoCloseable {
        final SshServer server = SshServer.setUpDefaultServer();
        final AtomicInteger authenticationAttempts = new AtomicInteger();
        Fixture(Path path) throws IOException {
            server.setHost("127.0.0.1"); server.setPort(0);
            SimpleGeneratorHostKeyProvider keys = new SimpleGeneratorHostKeyProvider(path.resolve("server-"+UUID.randomUUID()+".ser"));
            keys.setAlgorithm("RSA"); keys.setKeySize(2048); server.setKeyPairProvider(keys);
            server.setPasswordAuthenticator((user,password,session)-> {
                authenticationAttempts.incrementAndGet(); return user.equals("test") && password.equals("test-password");
            });
            // Test-only fixture. Production client does not run or configure an SSH server.
            server.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE); server.start();
        }
        @Override public void close() throws IOException { server.stop(true); }
    }
    private static final class Echo implements AutoCloseable {
        final ServerSocket server = localSocket();
        Echo() throws IOException {
            Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) try {
                    Socket socket=server.accept();
                    Thread.ofVirtual().start(() -> { try(socket) { socket.getInputStream().transferTo(socket.getOutputStream()); } catch(IOException ignored) { } });
                } catch(IOException e) { break; }
            });
        }
        int port() { return server.getLocalPort(); }
        @Override public void close() throws IOException { server.close(); }
    }
}
