package it.nexu.forwarding.ui;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import it.nexu.forwarding.i18n.I18n;
import javafx.application.Platform;

/** Dorkbox provides desktop integration; the main window remains usable without a tray. */
public final class TraySupport implements AutoCloseable {
    private SystemTray tray;
    public boolean install(Runnable show, Runnable startAll, Runnable stopAll, Runnable quit) {
        try {
            tray = SystemTray.get();
            if (tray == null) return false;
            tray.setImage(getClass().getResource("/app-icon.png"));
            tray.setTooltip("Nexu Port Forwarding");
            tray.getMenu().add(new MenuItem(I18n.t("Open Nexu Port Forwarding","Apri Nexu Port Forwarding"), e -> Platform.runLater(show)));
            tray.getMenu().add(new MenuItem(I18n.t("Start all tunnels","Avvia tutti i tunnel"), e -> Platform.runLater(startAll)));
            tray.getMenu().add(new MenuItem(I18n.t("Stop all tunnels","Ferma tutti i tunnel"), e -> Platform.runLater(stopAll)));
            tray.getMenu().add(new MenuItem(I18n.t("Exit and close tunnels","Esci e chiudi i tunnel"), e -> Platform.runLater(quit)));
            return true;
        } catch (Throwable e) { close(); return false; }
    }
    public void update(long active, long total) {
        if (tray != null) try { tray.setTooltip("Nexu Port Forwarding · " + active + "/" + total + I18n.t(" active"," attivi")); } catch (RuntimeException ignored) { }
    }
    @Override public void close() {
        if (tray != null) { try { tray.shutdown(); } catch (Throwable ignored) { } tray = null; }
    }
}
