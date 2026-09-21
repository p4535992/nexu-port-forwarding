package it.nexu.forwarding.ui;

import it.nexu.forwarding.model.TunnelProfile;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.io.File;
import java.util.UUID;

public final class ProfileDialog extends Dialog<ProfileDialog.Result> {
    public record Result(TunnelProfile profile, char[] newSecret, boolean remember) { }
    private final TextField name = new TextField(), host = new TextField(), port = new TextField(), user = new TextField();
    private final ComboBox<TunnelProfile.Auth> auth = new ComboBox<>();
    private final PasswordField secret = new PasswordField();
    private final CheckBox remember = new CheckBox("Salva la credenziale nell’archivio locale cifrato");
    private final TextField key = new TextField(), bind = new TextField(), bindPort = new TextField();
    private final TextField target = new TextField(), targetPort = new TextField();
    private final ComboBox<TunnelProfile.Mode> mode = new ComboBox<>();
    private final TextField timeout = new TextField(), interval = new TextField(), misses = new TextField();
    private final CheckBox reconnect = new CheckBox("Riconnetti in caso di interruzione della rete");
    private final TextField attempts = new TextField(), delay = new TextField(), notes = new TextField();
    private final Label validation = new Label();
    private final UUID id;
    private final TextField installation = new TextField();
    private final TunnelProfile.Origin origin;
    private final String sourceKey;
    private TunnelProfile valid;

    public ProfileDialog(Window owner, TunnelProfile existing, boolean hasSecret) {
        initOwner(owner);
        setTitle(existing == null ? "Nuovo tunnel SSH" : "Modifica tunnel");
        setHeaderText("Una riga, una connessione indipendente");
        setResizable(true);
        TunnelProfile p = existing == null ? TunnelProfile.example() : existing;
        id = existing == null ? UUID.randomUUID() : existing.id();
        origin = existing == null ? TunnelProfile.Origin.CUSTOM : existing.origin();
        sourceKey = existing == null ? "" : existing.sourceKey();
        installation.setText(p.installation());
        installation.setPromptText("Nome libero: cliente, sede, ambiente… (ricercabile)");
        name.setText(existing == null ? "" : p.name()); host.setText(existing == null ? "" : p.sshHost());
        port.setText(""+p.sshPort()); user.setText(existing == null ? "" : p.username());
        auth.getItems().setAll(TunnelProfile.Auth.values()); auth.setValue(p.auth());
        key.setText(p.privateKey()); bind.setText(p.bindHost()); bindPort.setText(""+p.bindPort());
        target.setText(existing == null ? "" : p.targetHost()); targetPort.setText(""+p.targetPort());
        mode.getItems().setAll(TunnelProfile.Mode.values()); mode.setValue(p.mode());
        timeout.setText(""+p.connectTimeoutSeconds()); interval.setText(""+p.keepAliveSeconds()); misses.setText(""+p.keepAliveMisses());
        reconnect.setSelected(p.reconnect()); attempts.setText(""+p.reconnectAttempts()); delay.setText(""+p.reconnectDelaySeconds());
        notes.setText(existing == null ? "" : p.notes());
        secret.setPromptText(hasSecret ? "Lascia vuoto per mantenere la credenziale esistente" : "Password SSH / passphrase della chiave");
        key.setPromptText("Percorso locale della chiave privata");
        host.setPromptText("server.example.org oppure indirizzo IP"); user.setPromptText("utente");
        target.setPromptText("Host raggiungibile dal lato di destinazione");
        mode.setConverter(new javafx.util.StringConverter<>() {
            public String toString(TunnelProfile.Mode m) {
                if (m == null) return "";
                return switch (m) {
                    case REMOTE -> "REMOTE · -R · ascolto sul server SSH";
                    case LOCAL -> "LOCAL · -L · ascolto su questo PC";
                    case DYNAMIC -> "DYNAMIC · -D · proxy SOCKS su questo PC";
                };
            }
            public TunnelProfile.Mode fromString(String s) { throw new UnsupportedOperationException(); }
        });
        auth.setConverter(new javafx.util.StringConverter<>() {
            public String toString(TunnelProfile.Auth a) { return a == TunnelProfile.Auth.PASSWORD ? "Password SSH" : "File chiave privata"; }
            public TunnelProfile.Auth fromString(String s) { throw new UnsupportedOperationException(); }
        });
        Button browse = new Button("Sfoglia…");
        browse.setOnAction(e -> { FileChooser fc = new FileChooser(); fc.setTitle("Seleziona una chiave privata"); File f = fc.showOpenDialog(owner); if (f != null) key.setText(f.getAbsolutePath()); });
        key.disableProperty().bind(auth.valueProperty().isNotEqualTo(TunnelProfile.Auth.PRIVATE_KEY));
        browse.disableProperty().bind(key.disableProperty());
        HBox keyBox = new HBox(8, key, browse); HBox.setHgrow(key, Priority.ALWAYS);
        GridPane connection = grid();
        row(connection, 0, "Nome", name); row(connection, 1, "Server SSH", host); row(connection, 2, "Porta SSH", port);
        row(connection, 3, "Utente", user); row(connection, 4, "Autenticazione", auth);
        row(connection, 5, "Password / passphrase", secret); row(connection, 6, "Chiave privata", keyBox);
        remember.setSelected(true);
        connection.add(remember,0,7,2,1);
        Label privacy = help("Se selezionato, il salvataggio richiede la password principale. Altrimenti la nuova credenziale resta solo in memoria. Un campo vuoto non modifica la credenziale esistente. L’export profili non include password.");
        connection.add(privacy, 0, 8, 2, 1);
        row(connection,9,"Installazione",installation);
        GridPane forwarding = grid();
        row(forwarding, 0, "Tipo di inoltro", mode); row(forwarding, 1, "Indirizzo di ascolto", bind);
        row(forwarding, 2, "Porta di ascolto", bindPort); row(forwarding, 3, "Host destinazione", target);
        row(forwarding, 4, "Porta destinazione", targetPort);
        target.disableProperty().bind(mode.valueProperty().isEqualTo(TunnelProfile.Mode.DYNAMIC));
        targetPort.disableProperty().bind(target.disableProperty());
        Label direction = help("");
        Runnable directionText = () -> direction.setText(mode.getValue() == TunnelProfile.Mode.DYNAMIC
            ? "DYNAMIC (-D): proxy SOCKS locale. Il client sceglie host e porta; il server SSH raggiunge la destinazione. Nessuna destinazione fissa. SOCKS non autenticato: lascia il bind su 127.0.0.1."
            : mode.getValue() == TunnelProfile.Mode.REMOTE
            ? "REMOTE (-R): la porta di ascolto viene aperta sul server SSH. Il tuo PC risolve e raggiunge l'host destinazione."
            : "LOCAL (-L): la porta di ascolto viene aperta sul tuo PC. Il server SSH risolve e raggiunge l'host destinazione.");
        mode.valueProperty().addListener((o,a,b) -> directionText.run()); directionText.run();
        forwarding.add(direction, 0, 5, 2, 1);
        forwarding.add(help("127.0.0.1 limita l'ascolto al loopback. Altri indirizzi possono esporre il servizio alla rete. Per -R la policy effettiva dipende anche da GatewayPorts sul server."), 0, 6, 2, 1);
        GridPane advanced = grid();
        row(advanced, 0, "Timeout connessione (s)", timeout); row(advanced, 1, "Intervallo keepalive (s)", interval);
        row(advanced, 2, "Timeout risposta (× intervallo)", misses); advanced.add(reconnect, 0, 3, 2, 1);
        row(advanced, 4, "Massimo riconnessioni", attempts); row(advanced, 5, "Attesa iniziale (s)", delay); row(advanced, 6, "Note", notes);
        attempts.disableProperty().bind(reconnect.selectedProperty().not()); delay.disableProperty().bind(reconnect.selectedProperty().not());
        advanced.add(help("La soglia keepalive imposta un timeout di risposta: con 15 × 3 il timeout è 45 secondi. Non è identica al contatore di messaggi OpenSSH. Le riconnessioni hanno attesa crescente, fino a 60 secondi, e non ritentano errori di credenziali, chiave host o bind."), 0, 7, 2, 1);
        TabPane tabs = new TabPane(tab("Connessione", connection), tab("Inoltro", forwarding), tab("Avanzate", advanced));
        validation.getStyleClass().add("error-text"); validation.setWrapText(true);
        VBox content = new VBox(12, tabs, validation); content.setPrefSize(730, 480); VBox.setVgrow(tabs, Priority.ALWAYS);
        getDialogPane().setContent(content);
        getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        ButtonType save = new ButtonType("Salva profilo", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, e -> {
            try { valid = collect(); validation.setText(""); }
            catch (RuntimeException ex) { validation.setText(ex.getMessage()); e.consume(); }
        });
        setResultConverter(button -> button == save ? new Result(valid, secret.getText().toCharArray(), remember.isSelected()) : null);
        setOnHidden(e -> secret.clear());
    }
    private TunnelProfile collect() {
        return new TunnelProfile(id, name.getText(), mode.getValue(), host.getText(), num(port,"Porta SSH"), user.getText(),
            bind.getText(), num(bindPort,"Porta ascolto"), target.getText(), mode.getValue() == TunnelProfile.Mode.DYNAMIC ? 0 : num(targetPort,"Porta destinazione"), auth.getValue(), key.getText(),
            num(timeout,"Timeout"), num(interval,"Keepalive"), num(misses,"Soglia keepalive"), reconnect.isSelected(), num(attempts,"Riconnessioni"),
            num(delay,"Attesa"), notes.getText(), installation.getText(), origin, sourceKey);
    }
    private static int num(TextField field, String name) {
        try { return Integer.parseInt(field.getText().trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(name + ": inserire un numero intero."); }
    }
    private static GridPane grid() {
        GridPane g = new GridPane(); g.setPadding(new Insets(20)); g.setHgap(16); g.setVgap(14);
        ColumnConstraints a = new ColumnConstraints(215), b = new ColumnConstraints(); b.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(a,b); return g;
    }
    private static void row(GridPane g, int i, String title, javafx.scene.Node field) {
        g.add(new Label(title),0,i); g.add(field,1,i); if (field instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
    }
    private static Label help(String text) { Label l = new Label(text); l.setWrapText(true); l.setMaxWidth(660); l.getStyleClass().add("muted"); return l; }
    private static Tab tab(String title, Region child) { ScrollPane s = new ScrollPane(child); s.setFitToWidth(true); Tab t = new Tab(title,s); t.setClosable(false); return t; }
}
