package it.nexu.forwarding.config;

import java.util.*;

/** Session-only secrets. Does not promise erasure of Strings held inside SSH/JavaFX. */
public final class SecretStore implements AutoCloseable {
    private final Map<UUID, char[]> values = new HashMap<>();
    public synchronized void put(UUID id, char[] secret) {
        forget(id);
        if (secret != null && secret.length != 0) values.put(id, secret.clone());
    }
    public synchronized char[] copy(UUID id) { return values.containsKey(id) ? values.get(id).clone() : new char[0]; }
    public synchronized boolean contains(UUID id) { return values.containsKey(id); }
    public synchronized void forget(UUID id) { char[] old = values.remove(id); if (old != null) Arrays.fill(old, '\0'); }
    @Override public synchronized void close() { values.values().forEach(v -> Arrays.fill(v, '\0')); values.clear(); }
}
