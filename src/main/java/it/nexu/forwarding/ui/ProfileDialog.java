package it.nexu.forwarding.ui;

import it.nexu.forwarding.i18n.I18n;
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
    private final ComboBox<TunnelProfile.ProxyType> proxyType = new ComboBox<>();
    private final TextField proxyHost = new TextField(), proxyPort = new TextField(), proxyUser = new TextField();
    private final PasswordRevealField secret = new PasswordRevealField();
    private final CheckBox remember = new CheckBox(I18n.t("Save credential in the encrypted local vault","Salva la credenziale nell’archivio locale cifrato"));
    private final TextField key = new TextField(), bind = new TextField(), bindPort = new TextField();
    private final TextField target = new TextField(), targetPort = new TextField();
    private final ComboBox<TunnelProfile.Mode> mode = new ComboBox<>();
    private final TextField timeout = new TextField(), interval = new TextField(), misses = new TextField();
    private final CheckBox reconnect = new CheckBox(I18n.t("Reconnect after a network interruption","Riconnetti in caso di interruzione della rete"));
    private final TextField attempts = new TextField(), delay = new TextField(), notes = new TextField();
    private final Label validation = new Label();
    private final UUID id;
    private final TextField installation = new TextField();
    private final TunnelProfile.Origin origin;
    private final String sourceKey;
    private TunnelProfile valid;

    public ProfileDialog(Window owner, TunnelProfile existing, boolean hasSecret) {
        initOwner(owner);
        setTitle(existing == null ? I18n.t("New SSH tunnel","Nuovo tunnel SSH") : I18n.t("Edit tunnel","Modifica tunnel"));
        setHeaderText(I18n.t("One row, one independent connection","Una riga, una connessione indipendente"));
        setResizable(true);
        TunnelProfile p = existing == null ? TunnelProfile.example() : existing;
        id = existing == null ? UUID.randomUUID() : existing.id();
        origin = existing == null ? TunnelProfile.Origin.CUSTOM : existing.origin();
        sourceKey = existing == null ? "" : existing.sourceKey();
        installation.setText(p.installation());
        installation.setPromptText(I18n.t("Free label: customer, site, environment… (searchable)","Nome libero: cliente, sede, ambiente… (ricercabile)"));
        name.setText(existing == null ? "" : p.name()); host.setText(existing == null ? "" : p.sshHost());
        port.setText(""+p.sshPort()); user.setText(existing == null ? "" : p.username());
        auth.getItems().setAll(TunnelProfile.Auth.values()); auth.setValue(p.auth());
        proxyType.getItems().setAll(TunnelProfile.ProxyType.values()); proxyType.setValue(p.proxyType());
        proxyHost.setText(p.proxyHost()); proxyPort.setText(p.usesProxy() ? Integer.toString(p.proxyPort()) : "");
        proxyUser.setText(p.proxyUsername());
        key.setText(p.privateKey()); bind.setText(p.bindHost()); bindPort.setText(""+p.bindPort());
        target.setText(existing == null ? "" : p.targetHost()); targetPort.setText(""+p.targetPort());
        mode.getItems().setAll(TunnelProfile.Mode.values()); mode.setValue(p.mode());
        timeout.setText(""+p.connectTimeoutSeconds()); interval.setText(""+p.keepAliveSeconds()); misses.setText(""+p.keepAliveMisses());
        reconnect.setSelected(p.reconnect()); attempts.setText(""+p.reconnectAttempts()); delay.setText(""+p.reconnectDelaySeconds());
        notes.setText(existing == null ? "" : p.notes());
        secret.setPromptText(hasSecret ? I18n.t("Leave empty to keep the existing credential","Lascia vuoto per mantenere la credenziale esistente") : I18n.t("SSH password / key passphrase","Password SSH / passphrase della chiave"));
        key.setPromptText(I18n.t("Local path to the private key","Percorso locale della chiave privata"));
        host.setPromptText(I18n.t("server.example.org or IP address","server.example.org oppure indirizzo IP")); user.setPromptText(I18n.t("username","utente"));
        target.setPromptText(I18n.t("Host reachable from the destination side","Host raggiungibile dal lato di destinazione"));
        mode.setConverter(new javafx.util.StringConverter<>() {
            public String toString(TunnelProfile.Mode m) {
                if (m == null) return "";
                return switch (m) {
                    case REMOTE -> I18n.t("REMOTE · -R · listen on SSH server","REMOTE · -R · ascolto sul server SSH");
                    case LOCAL -> I18n.t("LOCAL · -L · listen on this PC","LOCAL · -L · ascolto su questo PC");
                    case DYNAMIC -> I18n.t("DYNAMIC · -D · SOCKS proxy on this PC","DYNAMIC · -D · proxy SOCKS su questo PC");
                };
            }
            public TunnelProfile.Mode fromString(String s) { throw new UnsupportedOperationException(); }
        });
        auth.setConverter(new javafx.util.StringConverter<>() {
            public String toString(TunnelProfile.Auth a) { return a == TunnelProfile.Auth.PASSWORD ? I18n.t("SSH password","Password SSH") : I18n.t("Private key file","File chiave privata"); }
            public TunnelProfile.Auth fromString(String s) { throw new UnsupportedOperationException(); }
        });
        proxyType.setConverter(new javafx.util.StringConverter<>() {
            public String toString(TunnelProfile.ProxyType p) {
                if (p == null) return "";
                return switch(p) { case DIRECT -> I18n.t("Direct (no proxy)","Diretto (nessun proxy)"); case SOCKS5 -> "SOCKS5"; case HTTP_CONNECT -> "HTTP CONNECT"; };
            }
            public TunnelProfile.ProxyType fromString(String s) { throw new UnsupportedOperationException(); }
        });
        Button browse = new Button(I18n.t("Browse…","Sfoglia…"));
        browse.setOnAction(e -> { FileChooser fc = new FileChooser(); fc.setTitle(I18n.t("Select a private key","Seleziona una chiave privata")); File f = fc.showOpenDialog(owner); if (f != null) key.setText(f.getAbsolutePath()); });
        key.disableProperty().bind(auth.valueProperty().isNotEqualTo(TunnelProfile.Auth.PRIVATE_KEY));
        browse.disableProperty().bind(key.disableProperty());
        HBox keyBox = new HBox(8, key, browse); HBox.setHgrow(key, Priority.ALWAYS);
        GridPane connection = grid();
        row(connection, 0, I18n.t("Name","Nome"), name); row(connection, 1, "Server SSH", host); row(connection, 2, I18n.t("SSH port","Porta SSH"), port);
        row(connection, 3, I18n.t("Username","Utente"), user); row(connection, 4, I18n.t("Authentication","Autenticazione"), auth);
        row(connection, 5, "Password / passphrase", secret); row(connection, 6, I18n.t("Private key","Chiave privata"), keyBox);
        remember.setSelected(true);
        connection.add(remember,0,7,2,1);
        Label privacy = help(I18n.t("When selected, saving requires the master password. Otherwise the new credential stays in memory only. An empty field keeps the existing credential. Profile export never includes passwords.","Se selezionato, il salvataggio richiede la password principale. Altrimenti la nuova credenziale resta solo in memoria. Un campo vuoto non modifica la credenziale esistente. L’export profili non include password."));
        connection.add(privacy, 0, 8, 2, 1);
        row(connection,9,I18n.t("Installation","Installazione"),installation);
        GridPane proxy = grid();
        row(proxy,0,I18n.t("Proxy type","Tipo proxy"),proxyType); row(proxy,1,I18n.t("Proxy host","Host proxy"),proxyHost);
        row(proxy,2,I18n.t("Proxy port","Porta proxy"),proxyPort); row(proxy,3,I18n.t("Proxy username (optional)","Utente proxy (opzionale)"),proxyUser);
        proxyHost.setPromptText(I18n.t("proxy.example.org or IP address","proxy.example.org oppure indirizzo IP"));
        proxyPort.setPromptText(I18n.t("e.g. 8080 / 1080","es. 8080 / 1080"));
        proxyUser.setPromptText(I18n.t("If set, the password is requested when the tunnel starts","Se valorizzato, la password viene chiesta all'avvio del tunnel"));
        proxyHost.disableProperty().bind(proxyType.valueProperty().isEqualTo(TunnelProfile.ProxyType.DIRECT));
        proxyPort.disableProperty().bind(proxyHost.disableProperty());
        proxyUser.disableProperty().bind(proxyHost.disableProperty());
        proxy.add(help(I18n.t("SOCKS5 and HTTP CONNECT route the SSH connection through the configured proxy. The SSH destination is requested from the proxy using hostname + port. If a proxy username is specified, the password is requested at tunnel start and remains in memory only until the application closes or passwords are locked.","SOCKS5 e HTTP CONNECT instradano la connessione SSH tramite il proxy indicato. La destinazione SSH viene richiesta al proxy usando hostname + porta. Se specifichi un utente proxy, la password viene richiesta all'avvio e resta solo in memoria fino alla chiusura/blocco delle password.")),0,4,2,1);
        GridPane forwarding = grid();
        row(forwarding, 0, I18n.t("Forwarding type","Tipo di inoltro"), mode); row(forwarding, 1, I18n.t("Listen address","Indirizzo di ascolto"), bind);
        row(forwarding, 2, I18n.t("Listen port","Porta di ascolto"), bindPort); row(forwarding, 3, I18n.t("Destination host","Host destinazione"), target);
        row(forwarding, 4, I18n.t("Destination port","Porta destinazione"), targetPort);
        target.disableProperty().bind(mode.valueProperty().isEqualTo(TunnelProfile.Mode.DYNAMIC));
        targetPort.disableProperty().bind(target.disableProperty());
        Label direction = help("");
        Runnable directionText = () -> direction.setText(mode.getValue() == TunnelProfile.Mode.DYNAMIC
            ? I18n.t("DYNAMIC (-D): local SOCKS proxy. The client chooses host and port; the SSH server reaches the destination. No fixed destination. Unauthenticated SOCKS: keep bind on 127.0.0.1.","DYNAMIC (-D): proxy SOCKS locale. Il client sceglie host e porta; il server SSH raggiunge la destinazione. Nessuna destinazione fissa. SOCKS non autenticato: lascia il bind su 127.0.0.1.")
            : mode.getValue() == TunnelProfile.Mode.REMOTE
            ? I18n.t("REMOTE (-R): the listening port is opened on the SSH server. This PC resolves and reaches the destination host.","REMOTE (-R): la porta di ascolto viene aperta sul server SSH. Il tuo PC risolve e raggiunge l'host destinazione.")
            : I18n.t("LOCAL (-L): the listening port is opened on this PC. The SSH server resolves and reaches the destination host.","LOCAL (-L): la porta di ascolto viene aperta sul tuo PC. Il server SSH risolve e raggiunge l'host destinazione."));
        mode.valueProperty().addListener((o,a,b) -> directionText.run()); directionText.run();
        forwarding.add(direction, 0, 5, 2, 1);
        forwarding.add(help(I18n.t("127.0.0.1 limits listening to loopback. Other addresses may expose the service to the network. For -R, the effective policy also depends on GatewayPorts on the server.","127.0.0.1 limita l'ascolto al loopback. Altri indirizzi possono esporre il servizio alla rete. Per -R la policy effettiva dipende anche da GatewayPorts sul server.")), 0, 6, 2, 1);
        GridPane advanced = grid();
        row(advanced, 0, I18n.t("Connection timeout (s)","Timeout connessione (s)"), timeout); row(advanced, 1, I18n.t("Keepalive interval (s)","Intervallo keepalive (s)"), interval);
        row(advanced, 2, I18n.t("Reply timeout (× interval)","Timeout risposta (× intervallo)"), misses); advanced.add(reconnect, 0, 3, 2, 1);
        row(advanced, 4, I18n.t("Maximum reconnects","Massimo riconnessioni"), attempts); row(advanced, 5, I18n.t("Initial delay (s)","Attesa iniziale (s)"), delay); row(advanced, 6, I18n.t("Notes","Note"), notes);
        attempts.disableProperty().bind(reconnect.selectedProperty().not()); delay.disableProperty().bind(reconnect.selectedProperty().not());
        advanced.add(help("La soglia keepalive imposta un timeout di risposta: con 15 × 3 il timeout è 45 secondi. Non è identica al contatore di messaggi OpenSSH. Le riconnessioni hanno attesa crescente, fino a 60 secondi, e non ritentano errori di credenziali, chiave host o bind. Con Proxy = Diretto non viene usato automaticamente il proxy HTTP/SOCKS del sistema."), 0, 7, 2, 1);
        TabPane tabs = new TabPane(tab(I18n.t("Connection","Connessione"), connection), tab("Proxy", proxy), tab(I18n.t("Forwarding","Inoltro"), forwarding), tab(I18n.t("Advanced","Avanzate"), advanced));
        validation.getStyleClass().add("error-text"); validation.setWrapText(true);
        VBox content = new VBox(12, tabs, validation); content.setPrefSize(730, 480); VBox.setVgrow(tabs, Priority.ALWAYS);
        getDialogPane().setContent(content);
        getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        ButtonType save = new ButtonType(I18n.t("Save profile","Salva profilo"), ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, e -> {
            try { valid = collect(); validation.setText(""); }
            catch (RuntimeException ex) { validation.setText(ex.getMessage()); e.consume(); }
        });
        setResultConverter(button -> button == save ? new Result(valid, secret.getText().toCharArray(), remember.isSelected()) : null);
        setOnHidden(e -> secret.clear());
    }
    private TunnelProfile collect() {
        TunnelProfile.ProxyType selectedProxy = proxyType.getValue() == null ? TunnelProfile.ProxyType.DIRECT : proxyType.getValue();
        return new TunnelProfile(id, name.getText(), mode.getValue(), host.getText(), num(port,I18n.t("SSH port","Porta SSH")), user.getText(),
            bind.getText(), num(bindPort,"Porta ascolto"), target.getText(), mode.getValue() == TunnelProfile.Mode.DYNAMIC ? 0 : num(targetPort,I18n.t("Destination port","Porta destinazione")), auth.getValue(), key.getText(),
            num(timeout,"Timeout"), num(interval,"Keepalive"), num(misses,"Soglia keepalive"), reconnect.isSelected(), num(attempts,"Riconnessioni"),
            num(delay,"Attesa"), notes.getText(), installation.getText(), origin, sourceKey,
            selectedProxy, proxyHost.getText(), selectedProxy == TunnelProfile.ProxyType.DIRECT ? 0 : num(proxyPort,I18n.t("Proxy port","Porta proxy")), proxyUser.getText());
    }
    private static int num(TextField field, String name) {
        try { return Integer.parseInt(field.getText().trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(name + I18n.t(": enter an integer.",": inserire un numero intero.")); }
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
