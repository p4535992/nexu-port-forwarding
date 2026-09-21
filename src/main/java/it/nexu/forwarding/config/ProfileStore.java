package it.nexu.forwarding.config;

import it.nexu.forwarding.model.TunnelProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ProfileStore {
    private ProfileStore() { }
    public static List<TunnelProfile> load(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        return decode(SafeFiles.readBytes(file, 2_000_000));
    }
    public static List<TunnelProfile> decode(byte[] bytes) throws IOException {
        Properties props = SafeFiles.decode(bytes);
        String version = props.getProperty("format.version");
        if (!"1".equals(version) && !"2".equals(version) && !"3".equals(version)) throw new IOException("Versione della configurazione assente o non supportata.");
        try {
            int count = Integer.parseInt(props.getProperty("profile.count"));
            if (count < 0 || count > 1000) throw new IllegalArgumentException("Numero profili non valido (massimo 1000).");
            List<TunnelProfile> result = new ArrayList<>();
            Set<UUID> ids = new HashSet<>();
            for (int i = 0; i < count; i++) {
                String b = "profile." + i + ".";
                TunnelProfile p = new TunnelProfile(UUID.fromString(required(props, b+"id")), required(props,b+"name"),
                    TunnelProfile.Mode.valueOf(required(props,b+"mode")), required(props,b+"sshHost"), number(props,b+"sshPort"),
                    required(props,b+"username"), required(props,b+"bindHost"), number(props,b+"bindPort"),
                    required(props,b+"targetHost"), number(props,b+"targetPort"), TunnelProfile.Auth.valueOf(required(props,b+"auth")),
                    props.getProperty(b+"privateKey", ""), number(props,b+"connectTimeoutSeconds"), number(props,b+"keepAliveSeconds"),
                    number(props,b+"keepAliveMisses"), bool(props,b+"reconnect"), number(props,b+"reconnectAttempts"),
                    number(props,b+"reconnectDelaySeconds"), props.getProperty(b+"notes", ""),
                    !"1".equals(version) ? props.getProperty(b+"installation", "") : "",
                    !"1".equals(version) ? TunnelProfile.Origin.valueOf(required(props,b+"origin")) : TunnelProfile.Origin.CUSTOM,
                    !"1".equals(version) ? props.getProperty(b+"sourceKey", "") : "",
                    "3".equals(version) ? TunnelProfile.ProxyType.valueOf(props.getProperty(b+"proxyType","DIRECT")) : TunnelProfile.ProxyType.DIRECT,
                    "3".equals(version) ? props.getProperty(b+"proxyHost","") : "",
                    "3".equals(version) ? Integer.parseInt(props.getProperty(b+"proxyPort","0")) : 0,
                    "3".equals(version) ? props.getProperty(b+"proxyUsername","") : "");
                if ("1".equals(version) && p.mode() == TunnelProfile.Mode.DYNAMIC)
                    throw new IllegalArgumentException("DYNAMIC richiede il formato 2.");
                if (!ids.add(p.id())) throw new IllegalArgumentException("ID profilo duplicato.");
                result.add(p);
            }
            return List.copyOf(result);
        } catch (RuntimeException e) { throw new IOException("Configurazione non valida: " + e.getMessage(), e); }
    }
    private static String required(Properties p, String key) { return Objects.requireNonNull(p.getProperty(key), "Campo mancante: " + key); }
    private static int number(Properties p, String key) { return Integer.parseInt(required(p, key)); }
    private static boolean bool(Properties p, String key) {
        String s = required(p, key);
        if (!s.equals("true") && !s.equals("false")) throw new IllegalArgumentException("Booleano non valido: " + key);
        return Boolean.parseBoolean(s);
    }
    public static void save(Path file, List<TunnelProfile> profiles) throws IOException {
        SafeFiles.writeBytes(file, encode(profiles));
    }
    public static byte[] encode(List<TunnelProfile> profiles) throws IOException {
        if (profiles.size() > 1000) throw new IOException("Massimo 1000 profili.");
        Set<UUID> ids = new HashSet<>();
        Properties props = new Properties();
        props.setProperty("format.version", "3");
        props.setProperty("profile.count", Integer.toString(profiles.size()));
        for (int i = 0; i < profiles.size(); i++) {
            TunnelProfile p = profiles.get(i);
            if (!ids.add(p.id())) throw new IOException("ID profilo duplicato.");
            String b = "profile." + i + ".";
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", p.id()); values.put("name", p.name()); values.put("mode", p.mode());
            values.put("sshHost", p.sshHost()); values.put("sshPort", p.sshPort()); values.put("username", p.username());
            values.put("bindHost", p.bindHost()); values.put("bindPort", p.bindPort());
            values.put("targetHost", p.targetHost()); values.put("targetPort", p.targetPort());
            values.put("auth", p.auth()); values.put("privateKey", p.privateKey());
            values.put("connectTimeoutSeconds", p.connectTimeoutSeconds()); values.put("keepAliveSeconds", p.keepAliveSeconds());
            values.put("keepAliveMisses", p.keepAliveMisses()); values.put("reconnect", p.reconnect());
            values.put("reconnectAttempts", p.reconnectAttempts()); values.put("reconnectDelaySeconds", p.reconnectDelaySeconds());
            values.put("notes", p.notes()); values.put("installation",p.installation());
            values.put("origin",p.origin()); values.put("sourceKey",p.sourceKey());
            values.put("proxyType",p.proxyType()); values.put("proxyHost",p.proxyHost());
            values.put("proxyPort",p.proxyPort()); values.put("proxyUsername",p.proxyUsername());
            values.forEach((k, v) -> props.setProperty(b+k, String.valueOf(v)));
        }
        byte[] encoded = SafeFiles.encode(props, "Nexu Port Forwarding - configuration without passwords");
        if (encoded.length > 2_000_000) throw new IOException("Configurazione oltre il limite di 2 MB. Nessuna modifica salvata.");
        return encoded;
    }
}
