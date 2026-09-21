package it.nexu.forwarding.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Local-only storage policy. Resolving a location does not create or migrate user data. */
public final class StorageLocations {
    public static final String PREFERENCE_FILE = "portable.properties";
    public enum Mode { PORTABLE, APPDATA, CUSTOM }
    public record Selection(Path dataDirectory, Path logsDirectory, Path preferenceFile,
                            Mode mode, Mode preference) { }
    private StorageLocations() { }

    public static Selection current() throws IOException {
        final Path codeLocation;
        try {
            codeLocation = Path.of(StorageLocations.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new IOException("Impossibile individuare la cartella di Nexu Port Forwarding.", e);
        }
        return resolve(codeLocation, System.getProperties(), System.getenv());
    }

    /** Public for offline testing of Windows/Linux package layouts without changing the host OS. */
    public static Selection resolve(Path codeLocation, Properties system, Map<String,String> environment)
            throws IOException {
        Objects.requireNonNull(codeLocation); Objects.requireNonNull(system); Objects.requireNonNull(environment);
        boolean windows = system.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
        Path root = packageRoot(codeLocation.toAbsolutePath().normalize(), windows);
        Path preferenceFile = root == null ? null : root.resolve(PREFERENCE_FILE);
        if (preferenceFile != null && !Files.exists(preferenceFile, LinkOption.NOFOLLOW_LINKS)) preferenceFile = null;
        String override = nonBlank(system.getProperty("nexu.home"));
        if (override == null) override = nonBlank(environment.get("NEXU_PF_HOME"));
        // Explicit existing overrides keep their original layout and take precedence over a marker.
        if (override != null) {
            Path data = Path.of(override).toAbsolutePath().normalize();
            return new Selection(data, data.resolve("logs"), preferenceFile, Mode.CUSTOM, Mode.CUSTOM);
        }
        Mode preferred = preferenceFile == null ? Mode.APPDATA : readPreference(preferenceFile);
        if (preferred == Mode.PORTABLE) {
            return new Selection(root.resolve("data"), root.resolve("logs"), preferenceFile,
                Mode.PORTABLE, preferred);
        }
        Path rootData = userDataDirectory(system, environment);
        return new Selection(rootData.resolve("data"), rootData.resolve("logs"), preferenceFile, Mode.APPDATA, preferred);
    }

    /** Only the application JAR's package root is inspected, never the process working directory. */
    private static Path packageRoot(Path code, boolean windows) {
        if (code.getFileName() == null || !"nexu-port-forwarding.jar".equals(code.getFileName().toString())) return null;
        Path app = code.getParent();
        if (app == null || app.getFileName() == null) return null;
        if (!"app".equals(app.getFileName().toString())) return app; // standalone Java bundle
        Path parent = app.getParent();
        if (parent == null) return null;
        if (!windows && parent.getFileName() != null && "lib".equals(parent.getFileName().toString()))
            return parent.getParent(); // Linux jpackage: <root>/lib/app/application.jar
        return parent; // Windows jpackage: <root>/app/application.jar
    }

    public static Mode readPreference(Path file) throws IOException {
        Properties values;
        try { values = SafeFiles.read(file); }
        catch (IllegalArgumentException e) { throw new IOException("Impostazioni portabili non valide.", e); }
        if (!"1".equals(values.getProperty("version")))
            throw new IOException("Versione di portable.properties non supportata. Nessun dato è stato spostato.");
        return switch (values.getProperty("storage", "").trim()) {
            case "portable" -> Mode.PORTABLE;
            case "appdata" -> Mode.APPDATA;
            default -> throw new IOException("In portable.properties usare storage=portable oppure storage=appdata.");
        };
    }

    /** Changes only a sidecar preference; the active session and all data files stay untouched. */
    public static void savePreference(Selection selection, Mode next) throws IOException {
        if (selection.preferenceFile() == null || selection.mode() == Mode.CUSTOM)
            throw new IOException("La posizione dei dati è gestita dall'installazione o da un percorso esplicito.");
        if (next != Mode.PORTABLE && next != Mode.APPDATA) throw new IllegalArgumentException("Modalità non valida.");
        readPreference(selection.preferenceFile()); // validate existing version before writing
        Properties values = SafeFiles.read(selection.preferenceFile());
        values.setProperty("storage", next == Mode.PORTABLE ? "portable" : "appdata");
        SafeFiles.write(selection.preferenceFile(), values, "Nexu Port Forwarding - storage preference; restart required");
    }


    /**
     * One-way compatibility copy from the 1.0 layout (<root>/profiles.properties, vault, host keys, window state)
     * into <root>/data/. Originals are deliberately retained so rollback remains possible.
     */
    public static int migrateLegacyUserData(Selection selection) throws IOException {
        if (selection == null || selection.mode() != Mode.APPDATA) return 0;
        Path data = selection.dataDirectory();
        Path root = data.getParent();
        if (root == null || !"data".equals(data.getFileName().toString())) return 0;
        SafeFiles.directory(data);
        int copied = 0;
        for (String name : new String[]{"profiles.properties","credentials.npfvault","host-keys.properties","window.properties"}) {
            Path source = root.resolve(name), target = data.resolve(name);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) continue;
            int limit = name.endsWith(".npfvault") ? 8_000_000 : 2_000_000;
            SafeFiles.writeBytes(target, SafeFiles.readBytes(source, limit));
            copied++;
        }
        return copied;
    }

    private static Path userDataDirectory(Properties system, Map<String,String> environment) throws IOException {
        boolean windows = system.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
        String base = nonBlank(environment.get(windows ? "LOCALAPPDATA" : "XDG_DATA_HOME"));
        if (base != null && Path.of(base).isAbsolute()) return Path.of(base, "nexu-port-forwarding").normalize();
        String home = nonBlank(system.getProperty("user.home"));
        if (home == null) throw new IOException("Cartella utente non disponibile.");
        return (windows ? Path.of(home, "AppData", "Local", "nexu-port-forwarding")
            : Path.of(home, ".local", "share", "nexu-port-forwarding")).toAbsolutePath().normalize();
    }
    private static String nonBlank(String value) { return value == null || value.isBlank() ? null : value; }
}
