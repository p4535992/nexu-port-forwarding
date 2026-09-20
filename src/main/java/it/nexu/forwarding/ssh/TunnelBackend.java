package it.nexu.forwarding.ssh;

import it.nexu.forwarding.model.TunnelProfile;

public interface TunnelBackend {
    /** Returns only AFTER authentication and successful registration of the forwarding. */
    Connection open(TunnelProfile profile, char[] secret, Cancellation cancellation) throws Exception;
    interface Connection extends AutoCloseable {
        boolean isOpen();
        String closedReason();
        @Override void close();
    }
    final class Failure extends Exception {
        private final boolean retryable;
        public Failure(String message, boolean retryable) { super(message); this.retryable = retryable; }
        public Failure(String message, boolean retryable, Throwable cause) { super(message, cause); this.retryable = retryable; }
        public boolean retryable() { return retryable; }
    }
}
