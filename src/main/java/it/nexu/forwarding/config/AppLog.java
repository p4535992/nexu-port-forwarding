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
    public AppLog(Path home) throws IOException {
        directory=home.resolve("logs"); SafeFiles.directory(directory);
        logger.setUseParentHandlers(false); logger.setLevel(Level.INFO);
        handler=new FileHandler(directory.resolve("nexu-%g.log").toString(),2_000_000,5,true);
        handler.setEncoding("UTF-8"); handler.setFormatter(new Formatter() {
            @Override public String format(LogRecord r) { return r.getInstant()+" "+r.getLevel()+" "+r.getMessage()+System.lineSeparator(); }
        });
        logger.addHandler(handler); mark("application-started version=1.0.0");
    }
    public Path directory() { return directory; }
    public void event(TunnelEngine.Event event) {
        // State and UUID are useful for diagnostics without persisting arbitrary server error text.
        logger.info("tunnel="+event.id()+" state="+event.state().name());
    }
    public void mark(String fixedMessage) { logger.info(fixedMessage); }
    @Override public void close() { mark("application-stopped"); logger.removeHandler(handler); handler.close(); }
}
