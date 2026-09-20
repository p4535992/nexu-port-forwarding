package it.nexu.forwarding.ssh;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Cancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CopyOnWriteArrayList<Runnable> callbacks = new CopyOnWriteArrayList<>();
    public boolean isCancelled() { return cancelled.get(); }
    public void check() throws InterruptedException {
        if (isCancelled() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Arresto richiesto");
    }
    /** Callbacks MUST be non-blocking. Suitable for SSH close(true) or Platform.runLater. */
    public AutoCloseable onCancel(Runnable callback) {
        AtomicBoolean once = new AtomicBoolean();
        Runnable guarded = () -> { if (once.compareAndSet(false, true)) callback.run(); };
        callbacks.add(guarded);
        if (isCancelled()) { callbacks.remove(guarded); guarded.run(); }
        return () -> callbacks.remove(guarded);
    }
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            callbacks.forEach(r -> { try { r.run(); } catch (RuntimeException ignored) { } });
            callbacks.clear();
        }
    }
}
