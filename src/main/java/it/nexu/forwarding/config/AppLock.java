package it.nexu.forwarding.config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** OS-level lock, not a stale PID-file heuristic. */
public final class AppLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    public AppLock(Path folder) throws IOException {
        SafeFiles.directory(folder);
        channel = FileChannel.open(folder.resolve("app.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = channel.tryLock();
            if (lock == null) throw new IOException("NexU Port Forwarding è già aperto per questa configurazione.");
        } catch (IOException | RuntimeException e) { channel.close(); throw e; }
    }
    @Override public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
}
