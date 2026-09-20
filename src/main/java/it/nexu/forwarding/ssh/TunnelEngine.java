package it.nexu.forwarding.ssh;

import it.nexu.forwarding.model.TunnelProfile;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** No JavaFX dependency. Each row owns an independent connection and bounded retry budget. */
public final class TunnelEngine implements AutoCloseable {
    public enum State {
        STOPPED("Fermo"), CONNECTING("Connessione"), ACTIVE("Attivo"),
        RECONNECTING("Riconnessione"), STOPPING("Arresto"), ERROR("Errore");
        private final String label;
        State(String label) { this.label = label; }
        public String label() { return label; }
        public boolean busy() { return this != STOPPED && this != ERROR; }
    }
    public record Event(UUID id, State state, String detail, Instant time) { }
    private static final class Run {
        final TunnelProfile profile;
        final char[] secret;
        final Cancellation cancellation = new Cancellation();
        volatile Thread thread;
        Run(TunnelProfile profile, char[] secret) { this.profile = profile; this.secret = secret.clone(); }
    }
    private final Object lock = new Object();
    private final Map<UUID, Run> runs = new HashMap<>();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final TunnelBackend backend;
    private final Consumer<Event> listener;
    private boolean closed;
    public TunnelEngine(TunnelBackend backend, Consumer<Event> listener) {
        this.backend = Objects.requireNonNull(backend); this.listener = Objects.requireNonNull(listener);
    }
    public boolean start(TunnelProfile profile, char[] secret) {
        synchronized (lock) {
            if (closed || runs.containsKey(profile.id())) return false;
            Run run = new Run(profile, Objects.requireNonNull(secret));
            runs.put(profile.id(), run);
            emit(run, State.CONNECTING, "Apertura della connessione SSH…");
            workers.execute(() -> work(run));
            return true;
        }
    }
    public boolean isRunning(UUID id) { synchronized (lock) { return runs.containsKey(id); } }
    public int runningCount() { synchronized (lock) { return runs.size(); } }
    public void stop(UUID id) {
        synchronized (lock) {
            Run run = runs.get(id);
            if (run == null || run.cancellation.isCancelled()) return;
            emit(run, State.STOPPING, "Chiusura connessione e canali…");
            run.cancellation.cancel();
            if (run.thread != null) run.thread.interrupt();
        }
    }
    public void stopAll() { synchronized (lock) { List.copyOf(runs.keySet()).forEach(this::stop); } }
    private void work(Run run) {
        run.thread = Thread.currentThread();
        State end = State.STOPPED;
        String detail = "Tunnel arrestato.";
        int retries = 0;
        try {
            while (!run.cancellation.isCancelled()) {
                run.cancellation.check();
                try (TunnelBackend.Connection connection = backend.open(run.profile, run.secret, run.cancellation)) {
                    run.cancellation.check();
                    if (!connection.isOpen()) throw new TunnelBackend.Failure(connection.closedReason(), true);
                    synchronized (lock) {
                        if (!run.cancellation.isCancelled()) emit(run, State.ACTIVE,
                            "Forwarding stabilito. La salute del servizio di destinazione non è verificata.");
                    }
                    while (connection.isOpen()) {
                        run.cancellation.check();
                        Thread.sleep(200);
                    }
                    run.cancellation.check();
                    throw new TunnelBackend.Failure(connection.closedReason(), true);
                } catch (InterruptedException e) { throw e; }
                catch (Exception e) {
                    run.cancellation.check();
                    boolean retryable = e instanceof TunnelBackend.Failure f && f.retryable();
                    if (!run.profile.reconnect() || !retryable || retries >= run.profile.reconnectAttempts()) {
                        end = State.ERROR; detail = safeMessage(e, run.secret); break;
                    }
                    retries++;
                    // Total retries per manual start, not an unbounded loop after intermittent successes.
                    long delay = Math.min(60L, run.profile.reconnectDelaySeconds() * (1L << Math.min(retries - 1, 6)));
                    synchronized (lock) {
                        if (!run.cancellation.isCancelled()) emit(run, State.RECONNECTING,
                            safeMessage(e, run.secret) + " · tentativo " + retries + "/" + run.profile.reconnectAttempts() + " tra " + delay + " s");
                    }
                    Thread.sleep(delay * 1000L);
                    run.cancellation.check();
                    synchronized (lock) {
                        if (!run.cancellation.isCancelled()) emit(run, State.CONNECTING, "Riconnessione SSH in corso…");
                    }
                }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finally {
            synchronized (lock) {
                if (run.cancellation.isCancelled()) { end = State.STOPPED; detail = "Tunnel arrestato."; }
                emit(run, end, detail);
                // Publish final state before a replacement run can be started.
                runs.remove(run.profile.id(), run);
            }
            Arrays.fill(run.secret, '\0');
        }
    }
    private void emit(Run run, State state, String detail) {
        try { listener.accept(new Event(run.profile.id(), state, detail, Instant.now())); }
        catch (RuntimeException ignored) { /* UI failure must never orphan a tunnel. */ }
    }
    private static String safeMessage(Exception error, char[] secret) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        if (secret.length > 0) message = message.replace(new String(secret), "[segreto]");
        message = message.replaceAll("[\\p{Cntrl}]", " ");
        return message.length() > 400 ? message.substring(0, 400) : message;
    }
    public boolean awaitStopped(long timeoutMillis) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (runningCount() != 0 && System.nanoTime() < until) Thread.sleep(20);
        return runningCount() == 0;
    }
    @Override public void close() {
        synchronized (lock) { closed = true; stopAll(); }
        workers.shutdown();
    }
}
