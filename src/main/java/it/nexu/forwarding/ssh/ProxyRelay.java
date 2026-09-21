package it.nexu.forwarding.ssh;

import it.nexu.forwarding.model.TunnelProfile;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Loopback-only transport adapter: Apache MINA SSHD talks to localhost; this relay talks to the configured proxy. */
final class ProxyRelay implements AutoCloseable {
    private static final int HEADER_LIMIT = 16_384;
    private final ServerSocket listener;
    private final Socket upstream;
    private final AutoCloseable cancellationHook;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Socket local;

    private ProxyRelay(ServerSocket listener, Socket upstream, Cancellation token) {
        this.listener=listener; this.upstream=upstream;
        this.cancellationHook=token.onCancel(this::close);
        Thread.ofVirtual().name("npf-proxy-relay").start(this::acceptAndRelay);
    }

    static ProxyRelay open(TunnelProfile p,char[] password,Cancellation token) throws TunnelBackend.Failure,InterruptedException {
        token.check();
        Socket upstream=new Socket();
        AutoCloseable connectHook=token.onCancel(()->closeQuietly(upstream));
        try {
            upstream.connect(new InetSocketAddress(p.proxyHost(),p.proxyPort()),p.connectTimeoutSeconds()*1000);
            upstream.setSoTimeout(p.connectTimeoutSeconds()*1000);
            token.check();
            switch(p.proxyType()) {
                case SOCKS5 -> socks5(upstream,p,password);
                case HTTP_CONNECT -> httpConnect(upstream,p,password);
                case DIRECT -> throw new IllegalArgumentException("Relay proxy richiesto per connessione diretta.");
            }
            token.check();
            upstream.setSoTimeout(0);
            ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));
            return new ProxyRelay(listener,upstream,token);
        } catch(SocketTimeoutException e) {
            closeQuietly(upstream);
            throw new TunnelBackend.Failure("Timeout durante la connessione al proxy "+p.proxyLabel()+" "+p.proxyEndpoint()+" dopo "+p.connectTimeoutSeconds()+" s.",true,e);
        } catch(UnknownHostException e) {
            closeQuietly(upstream);
            throw new TunnelBackend.Failure("DNS: impossibile risolvere il proxy «"+p.proxyHost()+"».",true,e);
        } catch(ConnectException e) {
            closeQuietly(upstream);
            throw new TunnelBackend.Failure("Connessione al proxy "+p.proxyLabel()+" "+p.proxyEndpoint()+" rifiutata.",true,e);
        } catch(TunnelBackend.Failure e) {
            closeQuietly(upstream); throw e;
        } catch(IOException e) {
            closeQuietly(upstream);
            throw new TunnelBackend.Failure("Connessione al proxy "+p.proxyLabel()+" "+p.proxyEndpoint()+" fallita ("+e.getClass().getSimpleName()+").",true,e);
        } finally {
            try { connectHook.close(); } catch(Exception ignored) { }
        }
    }

    String host(){return "127.0.0.1";}
    int port(){return listener.getLocalPort();}

    private void acceptAndRelay() {
        try {
            local=listener.accept();
            closeQuietly(listener);
            Socket a=local,b=upstream;
            Thread.ofVirtual().start(()->pump(a,b));
            Thread.ofVirtual().start(()->pump(b,a));
        } catch(IOException ignored) { close(); }
    }

    private void pump(Socket from,Socket to) {
        try(InputStream in=from.getInputStream(); OutputStream out=to.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        } catch(IOException ignored) { }
        finally { close(); }
    }

    private static void socks5(Socket socket,TunnelProfile p,char[] password) throws IOException,TunnelBackend.Failure {
        DataInputStream in=new DataInputStream(socket.getInputStream());
        OutputStream out=socket.getOutputStream();
        boolean auth=!p.proxyUsername().isBlank();
        out.write(auth?new byte[]{5,1,2}:new byte[]{5,1,0}); out.flush();
        int version=in.readUnsignedByte(),method=in.readUnsignedByte();
        if(version!=5||method==0xff) throw new TunnelBackend.Failure("Il proxy SOCKS5 non accetta un metodo di autenticazione supportato.",false);
        if(method==2) {
            if(!auth) throw new TunnelBackend.Failure("Il proxy SOCKS5 richiede username/password: specificare l'utente proxy nel profilo.",false);
            byte[] user=p.proxyUsername().getBytes(StandardCharsets.UTF_8);
            byte[] passExact=utf8(password);
            try {
                if(user.length==0||user.length>255||passExact.length>255) throw new TunnelBackend.Failure("Credenziali SOCKS5 troppo lunghe.",false);
                out.write(1); out.write(user.length); out.write(user); out.write(passExact.length); out.write(passExact); out.flush();
                if(in.readUnsignedByte()!=1||in.readUnsignedByte()!=0) throw new TunnelBackend.Failure("Autenticazione SOCKS5 rifiutata dal proxy.",false);
            } finally { Arrays.fill(passExact,(byte)0); }
        } else if(method!=0) throw new TunnelBackend.Failure("Metodo di autenticazione SOCKS5 non supportato: "+method+".",false);

        byte[] host=p.sshHost().getBytes(StandardCharsets.US_ASCII);
        if(host.length==0||host.length>255) throw new TunnelBackend.Failure("Hostname SSH troppo lungo per SOCKS5.",false);
        out.write(new byte[]{5,1,0,3}); out.write(host.length); out.write(host); out.write((p.sshPort()>>>8)&255); out.write(p.sshPort()&255); out.flush();
        if(in.readUnsignedByte()!=5) throw new TunnelBackend.Failure("Risposta SOCKS5 non valida.",false);
        int reply=in.readUnsignedByte(); in.readUnsignedByte(); int atyp=in.readUnsignedByte();
        if(reply!=0) throw new TunnelBackend.Failure("Il proxy SOCKS5 non riesce ad aprire "+p.sshHost()+":"+p.sshPort()+" (codice "+reply+").",reply==3||reply==4||reply==5);
        int n=switch(atyp){case 1->4;case 4->16;case 3->in.readUnsignedByte();default->throw new TunnelBackend.Failure("Indirizzo di risposta SOCKS5 non valido.",false);};
        in.readNBytes(n); in.readUnsignedShort();
    }

    private static void httpConnect(Socket socket,TunnelProfile p,char[] password) throws IOException,TunnelBackend.Failure {
        OutputStream out=socket.getOutputStream();
        String authority=TunnelProfile.address(p.sshHost(),p.sshPort());
        StringBuilder request=new StringBuilder("CONNECT ").append(authority).append(" HTTP/1.1\r\nHost: ").append(authority).append("\r\nProxy-Connection: Keep-Alive\r\n");
        byte[] authBytes=null;
        if(!p.proxyUsername().isBlank()) {
            byte[] user=(p.proxyUsername()+":").getBytes(StandardCharsets.UTF_8);
            byte[] pass=utf8(password);
            authBytes=new byte[user.length+pass.length];
            System.arraycopy(user,0,authBytes,0,user.length); System.arraycopy(pass,0,authBytes,user.length,pass.length); Arrays.fill(pass,(byte)0);
            request.append("Proxy-Authorization: Basic ").append(Base64.getEncoder().encodeToString(authBytes)).append("\r\n");
        }
        try {
            request.append("\r\n");
            out.write(request.toString().getBytes(StandardCharsets.ISO_8859_1)); out.flush();
        } finally { if(authBytes!=null) Arrays.fill(authBytes,(byte)0); }
        String headers=readHeaders(socket.getInputStream());
        String first=headers.lines().findFirst().orElse("");
        String[] parts=first.split(" ",3);
        int code=-1; if(parts.length>=2) try{code=Integer.parseInt(parts[1]);}catch(NumberFormatException ignored){}
        if(code<200||code>=300) {
            if(code==407) throw new TunnelBackend.Failure("Autenticazione HTTP CONNECT rifiutata dal proxy (407).",false);
            throw new TunnelBackend.Failure("Il proxy HTTP CONNECT ha rifiutato "+authority+" (HTTP "+(code<0?"?":code)+").",code>=500||code<0);
        }
    }

    private static String readHeaders(InputStream in) throws IOException,TunnelBackend.Failure {
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        int state=0;
        while(b.size()<HEADER_LIMIT) {
            int x=in.read(); if(x<0) throw new EOFException("Proxy HTTP chiuso durante CONNECT.");
            b.write(x);
            state=switch(state){case 0->x=='\r'?1:0;case 1->x=='\n'?2:0;case 2->x=='\r'?3:0;case 3->x=='\n'?4:0;default->4;};
            if(state==4) return b.toString(StandardCharsets.ISO_8859_1);
        }
        throw new TunnelBackend.Failure("Risposta HTTP CONNECT troppo grande.",false);
    }

    private static byte[] utf8(char[] chars) {
        java.nio.ByteBuffer buffer=StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(chars));
        byte[] out=new byte[buffer.remaining()]; buffer.get(out);
        if(buffer.hasArray()) Arrays.fill(buffer.array(),(byte)0);
        return out;
    }

    @Override public void close() {
        if(!closed.compareAndSet(false,true)) return;
        closeQuietly(listener); closeQuietly(local); closeQuietly(upstream);
        try { cancellationHook.close(); } catch(Exception ignored) { }
    }
    private static void closeQuietly(Closeable c){if(c!=null)try{c.close();}catch(IOException ignored){}}
}
