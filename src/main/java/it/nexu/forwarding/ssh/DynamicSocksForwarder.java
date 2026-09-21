package it.nexu.forwarding.ssh;

import it.nexu.forwarding.model.TunnelProfile;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.net.SshdSocketAddress;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stream-safe SOCKS4/4a/5 frontend for DYNAMIC profiles.
 *
 * Apache MINA SSHD 2.19.0's built-in SOCKS5 frontend assumes that a CONNECT request arrives in one
 * network read. This frontend parses the byte stream independently and creates a temporary MINA
 * local-forwarding tracker for each requested destination. Target traffic still travels through
 * direct-tcpip channels in the authenticated SSH session.
 */
final class DynamicSocksForwarder implements AutoCloseable {
    private static final int MAX_TEXT = 1024;
    private final ClientSession session;
    private final TunnelProfile profile;
    private final ServerSocket listener;
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AutoCloseable cancellationHook;

    private DynamicSocksForwarder(ClientSession session, TunnelProfile profile, Cancellation cancellation) throws IOException {
        this.session = session;
        this.profile = profile;
        this.listener = new ServerSocket();
        listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress(profile.bindHost(), profile.bindPort()));
        this.cancellationHook = cancellation.onCancel(this::close);
        Thread.ofVirtual().name("nexu-socks-accept-" + profile.id()).start(this::acceptLoop);
    }

    static DynamicSocksForwarder start(ClientSession session, TunnelProfile profile, Cancellation cancellation) throws IOException {
        return new DynamicSocksForwarder(session, profile, cancellation);
    }

    boolean isOpen() { return open.get() && !listener.isClosed(); }

    private void acceptLoop() {
        while (isOpen() && session.isOpen()) {
            try {
                Socket client = listener.accept();
                if (!isOpen()) { closeQuietly(client); break; }
                sockets.add(client);
                Thread.ofVirtual().name("nexu-socks-client-" + profile.id()).start(() -> handle(client));
            } catch (SocketException e) {
                if (isOpen()) close();
                break;
            } catch (IOException e) {
                if (isOpen()) close();
                break;
            }
        }
    }

    private void handle(Socket client) {
        try (client) {
            client.setSoTimeout(Math.max(5_000, profile.connectTimeoutSeconds() * 1000));
            DataInputStream in = new DataInputStream(new BufferedInputStream(client.getInputStream()));
            // SocketOutputStream is intentionally unbuffered: proxy replies and upstream data must be visible immediately.\n            OutputStream rawOut = client.getOutputStream();
            int version = in.readUnsignedByte();
            Request request = switch (version) {
                case 4 -> readSocks4(in, rawOut);
                case 5 -> readSocks5(in, rawOut);
                default -> throw new IOException("Unsupported SOCKS version");
            };
            if (request == null) return;
            bridge(client, in, rawOut, version, request);
        } catch (IOException ignored) {
            // Per-client failures do not take down the SSH tunnel/listener.
        } finally {
            sockets.remove(client);
        }
    }

    private Request readSocks5(DataInputStream in, OutputStream out) throws IOException {
        int methodCount = in.readUnsignedByte();
        if (methodCount < 1 || methodCount > 255) throw new IOException("Invalid SOCKS5 greeting");
        byte[] methods = in.readNBytes(methodCount);
        if (methods.length != methodCount) throw new EOFException();
        boolean noAuth = false;
        for (byte method : methods) if ((method & 0xff) == 0) noAuth = true;
        out.write(new byte[]{5, (byte)(noAuth ? 0 : 0xff)}); out.flush();
        if (!noAuth) return null;

        if (in.readUnsignedByte() != 5) throw new IOException("Invalid SOCKS5 request");
        if (in.readUnsignedByte() != 1) { sendSocks5(out, 7); return null; }
        in.readUnsignedByte(); // RSV
        int atyp = in.readUnsignedByte();
        String host = switch (atyp) {
            case 1 -> readIpv4(in);
            case 3 -> readLengthPrefixedAscii(in);
            case 4 -> readIpv6(in);
            default -> { sendSocks5(out, 8); yield null; }
        };
        if (host == null || host.isBlank()) return null;
        int port = in.readUnsignedShort();
        if (port < 1) { sendSocks5(out, 1); return null; }
        return new Request(host, port);
    }

    private Request readSocks4(DataInputStream in, OutputStream out) throws IOException {
        int cmd = in.readUnsignedByte();
        int port = in.readUnsignedShort();
        byte[] ip = in.readNBytes(4);
        if (ip.length != 4) throw new EOFException();
        readNullTerminated(in);
        if (cmd != 1 || port < 1) { sendSocks4(out, 91, port); return null; }
        String host;
        if (ip[0] == 0 && ip[1] == 0 && ip[2] == 0 && (ip[3] & 0xff) != 0) host = readNullTerminated(in);
        else host = (ip[0]&255)+"."+(ip[1]&255)+"."+(ip[2]&255)+"."+(ip[3]&255);
        if (host.isBlank()) { sendSocks4(out, 91, port); return null; }
        return new Request(host, port);
    }

    private void bridge(Socket client, InputStream clientIn, OutputStream clientOut, int version, Request request) throws IOException {
        SshdSocketAddress remote = new SshdSocketAddress(request.host(), request.port());
        try (var tracker = session.createLocalPortForwardingTracker(
                new SshdSocketAddress("127.0.0.1", 0), remote)) {
            SshdSocketAddress bound = tracker.getBoundAddress();
            try (Socket upstream = new Socket()) {
                sockets.add(upstream);
                try {
                    upstream.connect(new InetSocketAddress("127.0.0.1", bound.getPort()), profile.connectTimeoutSeconds() * 1000);
                    upstream.setTcpNoDelay(true); client.setTcpNoDelay(true); client.setSoTimeout(0);
                    if (version == 5) sendSocks5(clientOut, 0); else sendSocks4(clientOut, 90, request.port());
                    OutputStream upOut = upstream.getOutputStream();
                    InputStream upIn = upstream.getInputStream();
                    Thread back = Thread.ofVirtual().name("nexu-socks-back-" + profile.id()).start(() -> {
                        try { upIn.transferTo(clientOut); clientOut.flush(); } catch (IOException ignored) { }
                        finally { closeQuietly(client); closeQuietly(upstream); }
                    });
                    try { clientIn.transferTo(upOut); upOut.flush(); }
                    finally { closeQuietly(upstream); closeQuietly(client); }
                    try { back.join(2_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                } finally { sockets.remove(upstream); }
            }
        } catch (IOException e) {
            try { if (version == 5) sendSocks5(clientOut, 1); else sendSocks4(clientOut, 91, request.port()); }
            catch (IOException ignored) { }
            throw e;
        }
    }

    private static String readIpv4(DataInputStream in) throws IOException {
        byte[] a=in.readNBytes(4); if(a.length!=4)throw new EOFException();
        return (a[0]&255)+"."+(a[1]&255)+"."+(a[2]&255)+"."+(a[3]&255);
    }
    private static String readIpv6(DataInputStream in) throws IOException {
        byte[] a=in.readNBytes(16); if(a.length!=16)throw new EOFException();
        return InetAddress.getByAddress(a).getHostAddress();
    }
    private static String readLengthPrefixedAscii(DataInputStream in) throws IOException {
        int length=in.readUnsignedByte(); if(length<1||length>255)throw new IOException("Invalid SOCKS hostname");
        byte[] b=in.readNBytes(length); if(b.length!=length)throw new EOFException();
        String value=new String(b,StandardCharsets.US_ASCII);
        if(value.chars().anyMatch(c->c<0x21||c>0x7e))throw new IOException("Invalid SOCKS hostname");
        return value;
    }
    private static String readNullTerminated(DataInputStream in) throws IOException {
        ByteArrayOutputStream value=new ByteArrayOutputStream();
        for(int i=0;i<MAX_TEXT;i++){int b=in.readUnsignedByte();if(b==0)return value.toString(StandardCharsets.US_ASCII);value.write(b);}
        throw new IOException("SOCKS text field too long");
    }
    private static void sendSocks5(OutputStream out,int status) throws IOException {
        out.write(new byte[]{5,(byte)status,0,1,0,0,0,0,0,0}); out.flush();
    }
    private static void sendSocks4(OutputStream out,int status,int port) throws IOException {
        out.write(new byte[]{0,(byte)status,(byte)(port>>>8),(byte)port,0,0,0,0}); out.flush();
    }
    private static void closeQuietly(Closeable closeable) { try { if(closeable!=null)closeable.close(); } catch(IOException ignored) { } }

    @Override public void close() {
        if (!open.compareAndSet(true,false)) return;
        closeQuietly(listener);
        for (Socket socket : sockets) closeQuietly(socket);
        sockets.clear();
        try { cancellationHook.close(); } catch (Exception ignored) { }
    }

    private record Request(String host,int port) { }
}
