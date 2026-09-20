package it.nexu.forwarding.ui;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import javafx.application.Platform;

/** Dorkbox matches NexU's desktop integration; the main window remains usable without a tray. */
public final class TraySupport implements AutoCloseable {
    private SystemTray tray;
    public boolean install(Runnable show, Runnable startAll, Runnable stopAll, Runnable quit) {
        try {
            tray = SystemTray.get();
            if (tray == null) return false;
            tray.setImage(getClass().getResource("/app-icon.png"));
            tray.setTooltip("NexU Port Forwarding");
            tray.getMenu().add(new MenuItem("Apri NexU Port Forwarding", e -> Platform.runLater(show)));
            tray.getMenu().add(new MenuItem("Avvia tutti i tunnel", e -> Platform.runLater(startAll)));
            tray.getMenu().add(new MenuItem("Ferma tutti i tunnel", e -> Platform.runLater(stopAll)));
            tray.getMenu().add(new MenuItem("Esci e chiudi i tunnel", e -> Platform.runLater(quit)));
            return true;
        } catch (Throwable e) { close(); return false; }
    }
    public void update(long active, long total) {
        if (tray != null) try { tray.setTooltip("NexU Port Forwarding · " + active + "/" + total + " attivi"); } catch (RuntimeException ignored) { }
    }
    @Override public void close() {
        if (tray != null) { try { tray.shutdown(); } catch (Throwable ignored) { } tray = null; }
    }
}
