package it.nexu.forwarding.config;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pins are scoped to the typed SSH hostname AND port. Never silently replaces a pin. */
public final class HostKeyStore {
    public enum Match { UNKNOWN, MATCH, CHANGED }
    private final Path file;
    private final Properties persistent;
    private final Map<String, String> temporary = new HashMap<>();
    public HostKeyStore(Path file) throws IOException {
        this.file = file;
        persistent = SafeFiles.read(file);
        for (String key : persistent.stringPropertyNames()) validate(persistent.getProperty(key));
    }
    private static void validate(String fingerprint) throws IOException {
        if (fingerprint == null || !fingerprint.matches("SHA256:[A-Za-z0-9+/]{43}=?"))
            throw new IOException("Archivio chiavi host non valido: attesa impronta SHA256.");
    }
    public synchronized String expected(String hostKeyId) {
        return persistent.getProperty(hostKeyId, temporary.get(hostKeyId));
    }
    public synchronized Match check(String hostKeyId, String fingerprint) {
        String known = expected(hostKeyId);
        return known == null ? Match.UNKNOWN : constantEquals(known, fingerprint) ? Match.MATCH : Match.CHANGED;
    }
    public synchronized void accept(String hostKeyId, String fingerprint, boolean remember) throws IOException {
        validate(fingerprint);
        if (check(hostKeyId, fingerprint) == Match.CHANGED)
            throw new IOException("La chiave host è cambiata durante la verifica: connessione rifiutata.");
        if (remember) {
            Properties next = new Properties(); next.putAll(persistent); next.setProperty(hostKeyId, fingerprint);
            SafeFiles.write(file, next, "NexU SSH SHA256 host key pins");
            persistent.clear(); persistent.putAll(next);
        } else temporary.put(hostKeyId, fingerprint);
    }
    public synchronized Map<String, String> entries() {
        Map<String, String> result = new TreeMap<>(temporary);
        for (String key : persistent.stringPropertyNames()) result.put(key, persistent.getProperty(key));
        return Map.copyOf(result);
    }
    /** Invoked only from an explicit, confirmed local UI action, never during a failed handshake. */
    public synchronized void forget(String hostKeyId) throws IOException {
        Properties next = new Properties(); next.putAll(persistent); next.remove(hostKeyId);
        SafeFiles.write(file, next, "NexU SSH SHA256 host key pins");
        persistent.clear(); persistent.putAll(next); temporary.remove(hostKeyId);
    }
    private static boolean constantEquals(String a, String b) {
        return b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
}
