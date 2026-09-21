package it.nexu.forwarding.model;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/** Immutable configuration. Deliberately contains NO password or passphrase. */
public record TunnelProfile(
    UUID id, String name, Mode mode, String sshHost, int sshPort, String username,
    String bindHost, int bindPort, String targetHost, int targetPort,
    Auth auth, String privateKey, int connectTimeoutSeconds, int keepAliveSeconds,
    int keepAliveMisses, boolean reconnect, int reconnectAttempts,
    int reconnectDelaySeconds, String notes, String installation, Origin origin, String sourceKey) {

    public enum Mode { REMOTE, LOCAL, DYNAMIC }
    public enum Origin { CUSTOM, TABBY, MOBAXTERM }

    /** Reads existing v1 profiles without inventing provenance. */
    public TunnelProfile(UUID id, String name, Mode mode, String sshHost, int sshPort, String username,
        String bindHost, int bindPort, String targetHost, int targetPort, Auth auth, String privateKey,
        int connectTimeoutSeconds, int keepAliveSeconds, int keepAliveMisses, boolean reconnect,
        int reconnectAttempts, int reconnectDelaySeconds, String notes) {
        this(id,name,mode,sshHost,sshPort,username,bindHost,bindPort,targetHost,targetPort,auth,privateKey,
            connectTimeoutSeconds,keepAliveSeconds,keepAliveMisses,reconnect,reconnectAttempts,
            reconnectDelaySeconds,notes,"",Origin.CUSTOM,"");
    }
    public enum Auth { PASSWORD, PRIVATE_KEY }

    public TunnelProfile {
        if (id == null || mode == null || auth == null) throw new IllegalArgumentException("ID, modalità e autenticazione obbligatori.");
        name = text(name, "Nome", 120, false);
        sshHost = host(sshHost, "Server SSH", false);
        username = text(username, "Utente SSH", 128, false);
        if (username.chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException("L'utente SSH non può contenere spazi.");
        bindHost = host(bindHost, "Indirizzo di ascolto", true);
        port(sshPort); port(bindPort);
        if (mode == Mode.DYNAMIC) { targetHost = ""; targetPort = 0; }
        else { targetHost = host(targetHost, "Destinazione", false); port(targetPort); }
        installation = text(installation, "Installazione", 120, true);
        if (origin == null) throw new IllegalArgumentException("Origine obbligatoria.");
        sourceKey = text(sourceKey, "Identificativo importazione", 64, true);
        if (!sourceKey.isEmpty() && !sourceKey.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Identificativo importazione non valido.");
        if (origin == Origin.CUSTOM) sourceKey = "";
        privateKey = text(privateKey, "Chiave privata", 4096, true);
        notes = text(notes, "Note", 2000, true);
        if (auth == Auth.PRIVATE_KEY) {
            if (privateKey.isBlank()) throw new IllegalArgumentException("Selezionare il file della chiave privata.");
            Path.of(privateKey); // Reject invalid local paths, without opening them here.
        }
        range(connectTimeoutSeconds, 3, 300, "Timeout connessione");
        range(keepAliveSeconds, 1, 3600, "Intervallo keepalive");
        range(keepAliveMisses, 1, 10, "Soglia keepalive");
        range(reconnectAttempts, 1, 20, "Tentativi riconnessione");
        range(reconnectDelaySeconds, 1, 300, "Attesa riconnessione");
    }

    private static String text(String value, String field, int max, boolean empty) {
        String s = value == null ? "" : value.trim();
        if ((!empty && s.isBlank()) || s.length() > max || s.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(field + ": valore assente, troppo lungo o con caratteri di controllo.");
        return s;
    }

    private static String host(String value, String field, boolean bind) {
        String s = text(value, field, 253, false);
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        if (bind && s.equals("*")) return "0.0.0.0";
        if (!s.matches("[a-zA-Z0-9][a-zA-Z0-9._:%-]*|::[a-zA-Z0-9:%.-]*"))
            throw new IllegalArgumentException(field + ": usare un hostname ASCII/punycode oppure un indirizzo IPv4/IPv6, senza protocollo o porta.");
        return s;
    }

    private static void port(int value) { range(value, 1, 65535, "Porta"); }
    private static void range(int n, int min, int max, String field) {
        if (n < min || n > max) throw new IllegalArgumentException(field + ": valore ammesso " + min + "–" + max + ".");
    }

    public static TunnelProfile example() {
        return new TunnelProfile(UUID.randomUUID(), "Maven aziendale", Mode.REMOTE,
            "203.0.113.10", 22, "utente", "127.0.0.1", 8989, "maven.example.com", 8081,
            Auth.PASSWORD, "", 15, 15, 3, false, 5, 3, "Esempio: non viene avviato automaticamente.");
    }
    public TunnelProfile duplicate() {
        return new TunnelProfile(UUID.randomUUID(), name.substring(0, Math.min(name.length(), 112)) + " (copia)", mode, sshHost, sshPort,
            username, bindHost, bindPort, targetHost, targetPort, auth, privateKey,
            connectTimeoutSeconds, keepAliveSeconds, keepAliveMisses, reconnect,
            reconnectAttempts, reconnectDelaySeconds, notes, installation, Origin.CUSTOM, "");
    }
    /** Backup restore preserves the tab and provenance while allocating a new credential identity. */
    public TunnelProfile copyWithNewId() {
        return new TunnelProfile(UUID.randomUUID(),name,mode,sshHost,sshPort,username,bindHost,bindPort,
            targetHost,targetPort,auth,privateKey,connectTimeoutSeconds,keepAliveSeconds,keepAliveMisses,
            reconnect,reconnectAttempts,reconnectDelaySeconds,notes,installation,origin,sourceKey);
    }
    public TunnelProfile withInstallation(String label) {
        return new TunnelProfile(id,name,mode,sshHost,sshPort,username,bindHost,bindPort,targetHost,targetPort,
            auth,privateKey,connectTimeoutSeconds,keepAliveSeconds,keepAliveMisses,reconnect,
            reconnectAttempts,reconnectDelaySeconds,notes,label,origin,sourceKey);
    }
    public TunnelProfile withName(String value) {
        return new TunnelProfile(id,value,mode,sshHost,sshPort,username,bindHost,bindPort,targetHost,targetPort,
            auth,privateKey,connectTimeoutSeconds,keepAliveSeconds,keepAliveMisses,reconnect,
            reconnectAttempts,reconnectDelaySeconds,notes,installation,origin,sourceKey);
    }
    public String forwardingSummary() {
        return switch (mode) {
            case REMOTE -> "R · SSH:" + sshPort + " · SERVER[" + listener() + "] → PC → " + destination();
            case LOCAL -> "L · SSH:" + sshPort + " · PC[" + listener() + "] → SERVER → " + destination();
            case DYNAMIC -> "D · SSH:" + sshPort + " · PC[" + listener() + "] → SOCKS → SERVER";
        };
    }
    public String endpoint() { return username + "@" + address(sshHost, sshPort); }
    public String listener() { return address(bindHost, bindPort); }
    public String destination() { return mode == Mode.DYNAMIC ? "SOCKS · destinazione scelta dal client" : address(targetHost, targetPort); }
    public String hostKeyId() { return address(sshHost.toLowerCase(Locale.ROOT), sshPort); }
    public boolean isLoopbackBind() {
        return bindHost.equals("127.0.0.1") || bindHost.equals("::1") || bindHost.equalsIgnoreCase("localhost");
    }
    public String searchable() { return (installation + " " + origin + " " + name + " " + mode + " " + endpoint() + " " + listener() + " " + destination() + " " + notes).toLowerCase(Locale.ROOT); }
    public static String address(String host, int port) { return bracket(host) + ":" + port; }
    public static String bracket(String host) { return host.contains(":") ? "[" + host + "]" : host; }
}
