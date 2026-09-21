package it.nexu.forwarding.ui;

import it.nexu.forwarding.model.TunnelProfile;
import it.nexu.forwarding.ssh.TunnelEngine;
import javafx.beans.property.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;

public final class TunnelRow {
    private TunnelProfile profile;
    private final ObjectProperty<TunnelEngine.State> state = new SimpleObjectProperty<>(TunnelEngine.State.STOPPED);
    private final StringProperty detail = new SimpleStringProperty("Pronto. Nessuna connessione avviata.");
    private final StringProperty resolvedIp = new SimpleStringProperty("");
    private final ArrayDeque<String> logs = new ArrayDeque<>();
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    public TunnelRow(TunnelProfile profile) { this.profile = profile; }
    public TunnelProfile profile() { return profile; }
    public void setProfile(TunnelProfile profile) { this.profile = profile; resolvedIp.set(""); }
    public ObjectProperty<TunnelEngine.State> stateProperty() { return state; }
    public TunnelEngine.State state() { return state.get(); }
    public StringProperty detailProperty() { return detail; }
    public StringProperty resolvedIpProperty() { return resolvedIp; }
    public String resolvedIp() { return resolvedIp.get(); }
    public String hostAddressDisplay() {
        String host = profile.sshHost();
        String ip = resolvedIp();
        if (ip == null || ip.isBlank() || host.equalsIgnoreCase(ip)) return host;
        return host + "\n" + ip;
    }
    public void setResolvedIp(String value) { resolvedIp.set(value == null ? "" : value); }
    public String detail() { return detail.get(); }
    public void accept(TunnelEngine.Event event) {
        state.set(event.state()); detail.set(event.detail());
        logs.addLast(CLOCK.format(event.time()) + "  " + event.state().label() + "  " + event.detail());
        while (logs.size() > 300) logs.removeFirst();
    }
    public void appendDiagnostic(String category,String message) {
        String safeCategory = category == null ? "Diagnostica" : category.replaceAll("[\\p{Cntrl}]", " ").trim();
        String safeMessage = message == null ? "" : message.replaceAll("[\\p{Cntrl}]", " ").trim();
        logs.addLast(CLOCK.format(Instant.now()) + "  " + safeCategory + "  " + safeMessage);
        while (logs.size() > 300) logs.removeFirst();
    }
    public String logs() { return String.join("\n", logs); }
}
