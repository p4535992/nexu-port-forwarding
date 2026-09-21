package it.nexu.forwarding.config;

import it.nexu.forwarding.ssh.TunnelEngine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.*;

/** Whitelisted application diagnostics only: no raw SSH exceptions, secrets or network payloads. */
public final class AppLog implements AutoCloseable {
    private final Logger logger=Logger.getLogger("it.nexu.forwarding.events");
    private final FileHandler handler;
    private final Path directory;
    public AppLog(Path home) throws IOException { this(home.resolve("logs"), true); }
    public static AppLog inDirectory(Path directory) throws IOException { return new AppLog(directory, true); }
    private AppLog(Path logDirectory, boolean explicitDirectory) throws IOException {
        directory=logDirectory; SafeFiles.directory(directory);
        logger.setUseParentHandlers(false); logger.setLevel(Level.INFO);
        handler=new FileHandler(directory.resolve("nexu-%g.log").toString(),2_000_000,5,true);
        handler.setEncoding("UTF-8"); handler.setFormatter(new Formatter() {
            @Override public String format(LogRecord r) { return r.getInstant()+" "+r.getLevel()+" "+r.getMessage()+System.lineSeparator(); }
        });
        logger.addHandler(handler); mark("application-started version=1.1.0");
    }
    public Path directory() { return directory; }
    public void event(TunnelEngine.Event event) {
        Diagnostic diagnostic = diagnostic(event);
        String message = "tunnel="+event.id()+" state="+event.state().name();
        if (diagnostic.category() != null) message += " category=" + diagnostic.category();
        if (diagnostic.detail() != null) message += " detail=\"" + escape(diagnostic.detail()) + "\"";
        logger.info(message);
    }
    private record Diagnostic(String category,String detail) { }
    private static Diagnostic diagnostic(TunnelEngine.Event event) {
        if (event.state() != TunnelEngine.State.ERROR && event.state() != TunnelEngine.State.RECONNECTING)
            return new Diagnostic(null,null);
        String d = event.detail() == null ? "" : event.detail().replaceAll("[\\p{Cntrl}]", " ").trim();
        if (d.startsWith("Porta locale già in uso:")) return new Diagnostic("LOCAL_BIND_IN_USE", bounded(d));
        if (d.startsWith("Impossibile usare l'indirizzo di ascolto locale")) return new Diagnostic("LOCAL_BIND_UNAVAILABLE", bounded(d));
        if (d.startsWith("DNS:")) return new Diagnostic("DNS_FAILURE", bounded(d));
        if (d.startsWith("Timeout durante la connessione TCP/SSH diretta")) return new Diagnostic("SSH_TIMEOUT", bounded(d));
        if (d.startsWith("Connessione rifiutata da")) return new Diagnostic("SSH_CONNECTION_REFUSED", bounded(d));
        if (d.startsWith("Nessun percorso di rete verso")) return new Diagnostic("NO_ROUTE", bounded(d));
        if (d.startsWith("Connessione verso") && d.contains("handshake SSH")) return new Diagnostic("SSH_HANDSHAKE_INTERRUPTED", bounded(d));
        if (d.startsWith("Server SSH raggiunto") && d.contains("autenticazione fallita")) return new Diagnostic("SSH_AUTH_FAILED", bounded(d));
        if (d.startsWith("CHIAVE HOST CAMBIATA") || d.startsWith("Chiave host non autorizzata")
            || d.startsWith("Impossibile verificare o salvare la chiave host"))
            return new Diagnostic("HOST_KEY_REJECTED", bounded(d));
        if (d.startsWith("Impossibile aprire la porta di ascolto")) return new Diagnostic("LOCAL_BIND_FAILED", bounded(d));
        if (d.startsWith("Connessione SSH autenticata, ma il forwarding è stato rifiutato"))
            return new Diagnostic("FORWARDING_REJECTED", bounded(d));
        return new Diagnostic("UNKNOWN",null);
    }
    private static String bounded(String value) { return value.length() > 360 ? value.substring(0,360) : value; }
    private static String escape(String value) { return value.replace("\\","\\\\").replace("\"","\\\""); }
    public void mark(String fixedMessage) { logger.info(fixedMessage); }
    @Override public void close() { mark("application-stopped"); logger.removeHandler(handler); handler.close(); }
}
