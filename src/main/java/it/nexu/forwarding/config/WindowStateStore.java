package it.nexu.forwarding.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/** Version-independent normal window bounds plus maximized state. */
public final class WindowStateStore {
    private static final String VERSION = "1";
    private final Path file;

    public WindowStateStore(Path file) { this.file = file; }

    public Optional<State> load() {
        try {
            Properties p = SafeFiles.read(file);
            if (p.isEmpty() || !VERSION.equals(p.getProperty("version"))) return Optional.empty();
            State state = new State(
                number(p, "x"), number(p, "y"), number(p, "width"), number(p, "height"),
                "true".equalsIgnoreCase(p.getProperty("maximized", "false")));
            return state.valid() ? Optional.of(state) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void save(State state) throws IOException {
        if (state == null || !state.valid()) throw new IllegalArgumentException("Stato finestra non valido.");
        Properties p = new Properties();
        p.setProperty("version", VERSION);
        p.setProperty("x", Double.toString(state.x()));
        p.setProperty("y", Double.toString(state.y()));
        p.setProperty("width", Double.toString(state.width()));
        p.setProperty("height", Double.toString(state.height()));
        p.setProperty("maximized", Boolean.toString(state.maximized()));
        SafeFiles.write(file, p, "NexU Port Forwarding window state");
    }

    private static double number(Properties p, String key) {
        return Double.parseDouble(p.getProperty(key, "NaN"));
    }

    public record State(double x, double y, double width, double height, boolean maximized) {
        public boolean valid() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(width) && Double.isFinite(height)
                && width >= 100 && height >= 100 && width <= 100_000 && height <= 100_000
                && Math.abs(x) <= 1_000_000 && Math.abs(y) <= 1_000_000;
        }
    }
}
