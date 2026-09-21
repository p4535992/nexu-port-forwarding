package it.nexu.forwarding;

import it.nexu.forwarding.config.*;
import it.nexu.forwarding.importer.TabbyImport;
import it.nexu.forwarding.importer.MobaXtermImport;
import javafx.scene.control.cell.TextFieldTableCell;
import it.nexu.forwarding.model.*;
import it.nexu.forwarding.ssh.*;
import it.nexu.forwarding.ui.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.io.File;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class NexuApplication extends Application {
    private final ObservableList<TunnelRow> rows = FXCollections.observableArrayList(r -> new Observable[]{r.stateProperty(), r.resolvedIpProperty()});
    private final SecretStore secrets = new SecretStore();
    private final TraySupport tray = new TraySupport();
    private final TableView<TunnelRow> table = new TableView<>();
    private final TextField search = new TextField(), ipFilter = new TextField();
    private final TabPane sourceTabs = new TabPane();
    private final Tab activeTab = new Tab("Attivi"), customTab = new Tab("Custom"), tabbyTab = new Tab("Tabby"), mobaTab = new Tab("MobaXterm");
    private final ComboBox<String> statusFilter = new ComboBox<>(), modeFilter = new ComboBox<>();
    private final Label totals = new Label(), active = new Label(), errors = new Label();
    private final Label selectedInfo = new Label("Seleziona una riga per vedere i dettagli."), footer = new Label();
    private final TextArea logs = new TextArea();
    private FilteredList<TunnelRow> filtered;
    private Stage window;
    private BorderPane root;
    private AppLock appLock;
    private Path configFile;
    private HostKeyStore hostKeys;
    private TunnelEngine engine;
    private VaultStore vault;
    private VaultUi vaultUi;
    private AppLog appLog;
    private StorageLocations.Selection storage;
    private WindowStateStore windowStateStore;
    private boolean trayReady, quitting, restoreMaximized;
    private double normalX = Double.NaN, normalY = Double.NaN, normalWidth = Double.NaN, normalHeight = Double.NaN;

    @Override public void start(Stage stage) {
        window = stage;
        stage.initStyle(StageStyle.DECORATED);
        stage.setFullScreen(false);
        try {
            storage = StorageLocations.current();
            int migrated = StorageLocations.migrateLegacyUserData(storage);
            Path home = storage.dataDirectory();
            appLock = new AppLock(home);
            appLog = AppLog.inDirectory(storage.logsDirectory());
            windowStateStore = new WindowStateStore(home.resolve("window.properties"));
            vault = new VaultStore(home.resolve("credentials.npfvault"));
            configFile = home.resolve("profiles.properties");
            hostKeys = new HostKeyStore(home.resolve("host-keys.properties"));
            for (TunnelProfile profile : ProfileStore.load(configFile)) { TunnelRow row=new TunnelRow(profile); rows.add(row); resolveHost(row); }
            engine = new TunnelEngine(new MinaTunnelBackend(hostKeys, this::askHostTrust),
                event -> { appLog.event(event); Platform.runLater(() -> onTunnelEvent(event)); });
            vaultUi = new VaultUi(window,vault,secrets,engine,configFile,this::profiles,
                list -> { rows.setAll(list.stream().map(TunnelRow::new).toList()); rows.forEach(this::resolveHost); updateFilter(); refreshCounters(); },this::error);
            buildWindow();
            restoreWindowState();
            stage.show();
            if (restoreMaximized) Platform.runLater(() -> window.setMaximized(true));
            trayReady = !Boolean.getBoolean("nexu.smokeTest") && tray.install(this::showWindow,
                () -> { showWindow(); startAll(false); }, () -> engine.stopAll(), this::requestExit);
            Platform.setImplicitExit(false);
            footer.setText(trayReady
                ? "La X chiede se ridurre nell’area di notifica oppure uscire. — minimizza; □ massimizza/ripristina."
                : "Tray non disponibile: la X offre minimizzazione normale oppure uscita. — e □ restano i controlli nativi.");
            refreshCounters();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { engine.close(); secrets.close(); vault.close(); }, "nexu-shutdown"));
            appLog.mark("ui-ready");
            if (migrated > 0) appLog.mark("legacy-data-copied=" + migrated);
            if (Boolean.getBoolean("nexu.smokeTest")) {
                if (window.getStyle() != StageStyle.DECORATED || window.isFullScreen()
                    || Screen.getScreensForRectangle(window.getX(), window.getY(), Math.max(1, window.getWidth()), Math.max(1, window.getHeight())).isEmpty())
                    throw new IllegalStateException("Window smoke check failed: native decorated window is not visible.");
                SafeFiles.writeBytes(home.resolve("ui-ready"), "UI_READY 1.1.0 DECORATED WINDOWED".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                javafx.animation.PauseTransition exit = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
                exit.setOnFinished(e -> requestExit()); exit.play();
            }
        } catch (Exception e) {
            if (Boolean.getBoolean("nexu.smokeTest")) { e.printStackTrace(); System.exit(1); }
            if (appLock != null) try { appLock.close(); } catch (Exception ignored) { }
            Alert alert = new Alert(Alert.AlertType.ERROR, "Avvio non riuscito. Nessun tunnel è stato aperto.\n\n" + e.getMessage());
            alert.setHeaderText("Nexu Port Forwarding"); alert.showAndWait();
            Platform.exit();
        }
    }

    private void buildWindow() {
        Label title = new Label("Nexu Port Forwarding"); title.getStyleClass().add("app-title");
        Label subtitle = new Label("Tunnel SSH dal tuo desktop · Windows / Linux"); subtitle.getStyleClass().add("muted");
        VBox branding = new VBox(5, title, subtitle);
        HBox counters = new HBox(10, counter("PROFILI", totals), counter("ATTIVI", active), counter("ERRORI", errors));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(20, branding, spacer, counters); heading.setAlignment(Pos.CENTER_LEFT);
        Button add = new Button("+ Nuovo tunnel"); add.getStyleClass().add("primary"); add.setOnAction(e -> edit(null));
        Button start = new Button("Avvia visibili"); start.setOnAction(e -> startAll(true));
        Button stop = new Button("Ferma tutti"); stop.setOnAction(e -> engine.stopAll());
        MenuButton file = new MenuButton("Profili");
        MenuItem sample = new MenuItem("Aggiungi l'esempio Maven"); sample.setOnAction(e -> addProfile(TunnelProfile.example()));
        MenuItem importItem = new MenuItem("Importa configurazione…"); importItem.setOnAction(e -> importProfiles());
        MenuItem exportItem = new MenuItem("Esporta configurazione…"); exportItem.setOnAction(e -> exportProfiles());
        file.getItems().addAll(sample, new SeparatorMenuItem(), importItem, exportItem);
        Button hosts = new Button("Chiavi host"); hosts.setOnAction(e -> showHostKeys());
        Button quit = new Button("Esci"); quit.setOnAction(e -> requestExit());
        Button openLogs = new Button("Apri log"); openLogs.setOnAction(e -> {
            try { java.awt.Desktop.getDesktop().open(appLog.directory().toFile()); }
            catch (Exception ex) { error("Cartella dei log: " + appLog.directory()); }
        });
        MenuButton settings = new MenuButton("Impostazioni");
        MenuItem dataLocation = new MenuItem("Cartella dati…");
        dataLocation.setOnAction(e -> {
            if (!quitting && !UiWork.busy()) StorageSettingsDialog.show(window, storage, this::error);
        });
        settings.getItems().add(dataLocation);
        Button importTabby = new Button("Importa Tabby…"); importTabby.setOnAction(e -> importTabby());
        Button importMoba = new Button("Importa MobaXterm…"); importMoba.setOnAction(e -> importMobaXterm());
        FlowPane commands = new FlowPane(9, 9, add, importTabby, importMoba, start, stop, file, vaultUi.menu(), hosts, openLogs, settings, quit);
        search.setPromptText("Cerca installazione, nome, hostname, utente, porta o destinazione…"); HBox.setHgrow(search, Priority.ALWAYS);
        ipFilter.setPromptText("Filtra indirizzo IP…"); ipFilter.setPrefWidth(190);
        statusFilter.getItems().add("Tutti gli stati");
        for (TunnelEngine.State state : TunnelEngine.State.values()) statusFilter.getItems().add(state.label());
        statusFilter.getSelectionModel().selectFirst(); statusFilter.setPrefWidth(170);
        modeFilter.getItems().addAll("Tutti i tipi", "LOCAL (-L)", "REMOTE (-R)", "DYNAMIC (SOCKS)");
        modeFilter.getSelectionModel().selectFirst(); modeFilter.setPrefWidth(175);
        Button clear = new Button("Azzera ricerca"); clear.setOnAction(e -> { search.clear(); ipFilter.clear(); statusFilter.getSelectionModel().selectFirst(); modeFilter.getSelectionModel().selectFirst(); });
        HBox filters = new HBox(10, search, modeFilter, ipFilter, statusFilter, clear);
        VBox top = new VBox(22, heading, commands, filters); top.setPadding(new Insets(26,26,18,26));
        activeTab.setClosable(false); customTab.setClosable(false); tabbyTab.setClosable(false); mobaTab.setClosable(false);
        sourceTabs.getTabs().setAll(activeTab, customTab, tabbyTab, mobaTab);
        sourceTabs.setId("source-tabs"); activeTab.setId("active-tab"); customTab.setId("custom-tab"); tabbyTab.setId("tabby-tab"); mobaTab.setId("mobaxterm-tab");
        createTable();
        activeTab.setContent(table);
        sourceTabs.getSelectionModel().selectedItemProperty().addListener((o,old,selected) -> {
            table.edit(-1,null);
            if (old != null) old.setContent(null);
            if (selected != null) selected.setContent(table);
            table.getSelectionModel().clearSelection(); updateFilter();
        });
        updateFilter();
        logs.setEditable(false); logs.setWrapText(true); logs.getStyleClass().add("log-area"); logs.setPrefRowCount(5);
        logs.setPromptText("Gli eventi del tunnel selezionato compariranno qui. Lo storico di stato viene salvato anche nella cartella logs locale; gli ultimi 300 dettagli restano in memoria.");
        selectedInfo.setWrapText(true); selectedInfo.getStyleClass().add("muted");
        Label logTitle = new Label("ATTIVITÀ DEL TUNNEL"); logTitle.getStyleClass().add("section-title");
        footer.getStyleClass().add("muted"); footer.setWrapText(true);
        Label meaning = new Label("Verde = SSH + forwarding stabiliti. Non è un controllo di salute dell'applicazione finale.");
        meaning.getStyleClass().add("muted"); meaning.setWrapText(true);
        VBox bottom = new VBox(8, logTitle, selectedInfo, logs, meaning, footer); bottom.setPadding(new Insets(18,26,20,26));
        root = new BorderPane(sourceTabs, top, null, bottom, null); BorderPane.setMargin(sourceTabs, new Insets(0,26,0,26));
        Scene scene = new Scene(root, 1180, 760); scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        window.setScene(scene); window.setTitle("Nexu Port Forwarding 1.1.0");
        window.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/app-icon.png"))));
        window.xProperty().addListener((o,a,b) -> captureNormalBounds());
        window.yProperty().addListener((o,a,b) -> captureNormalBounds());
        window.widthProperty().addListener((o,a,b) -> captureNormalBounds());
        window.heightProperty().addListener((o,a,b) -> captureNormalBounds());
        window.maximizedProperty().addListener((o,a,b) -> { if (!b) Platform.runLater(this::captureNormalBounds); });
        window.setOnCloseRequest(e -> { e.consume(); handleWindowClose(); });
        table.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> showSelection());
    }
    private VBox counter(String label, Label value) {
        Label caption = new Label(label); caption.getStyleClass().add("section-title"); value.getStyleClass().add("counter-number");
        VBox box = new VBox(5, value, caption); box.getStyleClass().add("counter"); box.setMinWidth(100); return box;
    }

    private void createTable() {
        filtered = new FilteredList<>(rows, r -> true);
        SortedList<TunnelRow> sorted = new SortedList<>(filtered); sorted.comparatorProperty().bind(table.comparatorProperty()); table.setItems(sorted);
        search.textProperty().addListener((o,a,b) -> updateFilter()); ipFilter.textProperty().addListener((o,a,b) -> updateFilter()); statusFilter.valueProperty().addListener((o,a,b) -> updateFilter()); modeFilter.valueProperty().addListener((o,a,b) -> updateFilter());
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY); table.setFixedCellSize(62); table.setEditable(true);
        TableColumn<TunnelRow,TunnelEngine.State> state = new TableColumn<>("STATO"); state.setPrefWidth(145);
        state.setCellValueFactory(c -> c.getValue().stateProperty());
        state.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(TunnelEngine.State s, boolean empty) {
                super.updateItem(s,empty); setText(null); setGraphic(null);
                if (!empty && s != null) {
                    Label badge = new Label("●  " + s.label()); badge.getStyleClass().addAll("state-pill", "state-" + s.name().toLowerCase(Locale.ROOT));
                    TunnelRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null) badge.setTooltip(new Tooltip(row.detail())); setGraphic(badge);
                }
            }
        });
        TableColumn<TunnelRow,String> mode = textColumn("TIPO", 90, r -> switch(r.profile().mode()) { case REMOTE -> "R ←"; case LOCAL -> "L →"; case DYNAMIC -> "D SOCKS"; });
        TableColumn<TunnelRow,String> name = textColumn("NOME", 175, r -> r.profile().name());
        TableColumn<TunnelRow,String> installation = textColumn("INSTALLAZIONE", 180, r -> r.profile().installation());
        installation.setEditable(true);
        installation.setCellFactory(TextFieldTableCell.forTableColumn());
        installation.setOnEditCommit(e -> {
            TunnelRow row = e.getRowValue();
            if (UiWork.busy() || engine.isRunning(row.profile().id())) { table.refresh(); error("Ferma il tunnel prima di modificare l'installazione."); return; }
            try {
                TunnelProfile changed = row.profile().withInstallation(e.getNewValue());
                List<TunnelProfile> next = new ArrayList<>(profiles()); next.set(next.indexOf(row.profile()),changed);
                if (persist(next)) { row.setProfile(changed); updateFilter(); showSelection(); }
            } catch (RuntimeException ex) { error(ex.getMessage()); }
            table.refresh();
        });
        TableColumn<TunnelRow,String> hostname = textColumn("HOSTNAME", 190, r -> r.profile().sshHost());
        TableColumn<TunnelRow,String> ip = new TableColumn<>("INDIRIZZO IP"); ip.setPrefWidth(155); ip.setCellValueFactory(c -> c.getValue().resolvedIpProperty());
        ip.setCellFactory(c -> new TableCell<>() { @Override protected void updateItem(String text, boolean empty) { super.updateItem(text,empty); setText(empty ? null : (text == null || text.isBlank() ? "—" : text)); setTooltip(empty || text == null || text.isBlank() ? null : new Tooltip(text)); } });
        TableColumn<TunnelRow,String> sshPort = textColumn("PORTA SSH", 85, r -> Integer.toString(r.profile().sshPort()));
        TableColumn<TunnelRow,String> bind = textColumn("ASCOLTO", 165, r -> r.profile().listener());
        TableColumn<TunnelRow,String> destination = textColumn("DESTINAZIONE", 210, r -> r.profile().destination());
        TableColumn<TunnelRow,TunnelRow> actions = new TableColumn<>("AZIONI"); actions.setPrefWidth(210); actions.setSortable(false);
        actions.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        actions.setCellFactory(c -> new TableCell<>() {
            private Button launch, halt;
            @Override protected void updateItem(TunnelRow row, boolean empty) {
                if (launch != null) launch.disableProperty().unbind(); if (halt != null) halt.disableProperty().unbind();
                super.updateItem(row,empty); setGraphic(null); setText(null);
                if (empty || row == null) return;
                launch = new Button("Avvia"); launch.getStyleClass().add("small-primary"); launch.setOnAction(e -> startOne(row));
                halt = new Button("Ferma"); halt.setOnAction(e -> engine.stop(row.profile().id()));
                launch.disableProperty().bind(Bindings.createBooleanBinding(() -> row.state().busy(), row.stateProperty()));
                halt.disableProperty().bind(Bindings.createBooleanBinding(() -> !row.state().busy() || row.state() == TunnelEngine.State.STOPPING, row.stateProperty()));
                MenuButton menu = new MenuButton("⋯");
                MenuItem edit = new MenuItem("Modifica…"); edit.setOnAction(e -> edit(row));
                MenuItem duplicate = new MenuItem("Duplica (senza password)"); duplicate.setOnAction(e -> {
                    try { addProfile(row.profile().duplicate()); } catch (RuntimeException ex) { error(ex.getMessage()); }
                });
                MenuItem log = new MenuItem("Mostra log"); log.setOnAction(e -> table.getSelectionModel().select(row));
                MenuItem copyPs = new MenuItem("Copia comando PowerShell"); copyPs.setOnAction(e -> copy(OpenSshCommand.powershell(row.profile())));
                MenuItem copySh = new MenuItem("Copia comando Linux"); copySh.setOnAction(e -> copy(OpenSshCommand.posix(row.profile())));
                MenuItem forget = new MenuItem("Dimentica password in memoria"); forget.setOnAction(e -> {
                    if (engine.isRunning(row.profile().id())) { error("Ferma il tunnel prima di dimenticare la password."); return; }
                    secrets.forget(row.profile().id());
                });
                MenuItem forgetSaved = new MenuItem("Rimuovi password salvata…"); forgetSaved.setOnAction(e -> {
                    if (engine.isRunning(row.profile().id())) { error("Ferma il tunnel prima di rimuovere la password."); return; }
                    if (confirm("Rimuovi credenziale", "Rimuovere la password salvata per questo profilo?") && vaultUi.forget(row.profile().id())) secrets.forget(row.profile().id());
                });
                MenuItem delete = new MenuItem("Elimina…"); delete.setOnAction(e -> delete(row));
                menu.getItems().addAll(edit, duplicate, log, new SeparatorMenuItem(), copyPs, copySh, forget, forgetSaved, new SeparatorMenuItem(), delete);
                HBox box = new HBox(6, launch, halt, menu); box.setAlignment(Pos.CENTER_LEFT); setGraphic(box);
            }
        });
        state.setEditable(false); mode.setEditable(false); name.setEditable(false); hostname.setEditable(false); ip.setEditable(false); sshPort.setEditable(false);
        bind.setEditable(false); destination.setEditable(false); actions.setEditable(false);
        table.getColumns().addAll(state, mode, installation, name, hostname, ip, sshPort, bind, destination, actions);
        Label emptyTitle = new Label("Nessun tunnel da mostrare"); emptyTitle.getStyleClass().add("empty-title");
        Label emptyHelp = new Label("Attivi: solo tunnel connessi. Custom: + Nuovo tunnel. Tabby/MobaXterm: usa i pulsanti Importa."); emptyHelp.getStyleClass().add("muted");
        VBox empty = new VBox(12, emptyTitle, emptyHelp); empty.setAlignment(Pos.CENTER); table.setPlaceholder(empty);
        table.setRowFactory(t -> { TableRow<TunnelRow> row = new TableRow<>(); row.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && !row.isEmpty() && table.getEditingCell() == null) {
                javafx.scene.Node node = e.getTarget() instanceof javafx.scene.Node n ? n : null;
                while (node != null && !(node instanceof TableCell<?,?>)) node = node.getParent();
                if (!(node instanceof TableCell<?,?> cell) || !installation.equals(cell.getTableColumn())) edit(row.getItem());
            }
        }); return row; });
    }
    private TableColumn<TunnelRow,String> textColumn(String label, double width, Function<TunnelRow,String> value) {
        TableColumn<TunnelRow,String> column = new TableColumn<>(label); column.setPrefWidth(width);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        column.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String text, boolean empty) {
                super.updateItem(text,empty); setText(empty ? null : text); setTooltip(empty || text == null ? null : new Tooltip(text));
            }
        }); return column;
    }
    private void updateFilter() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        String ipQuery = ipFilter.getText().trim().toLowerCase(Locale.ROOT);
        String status = statusFilter.getValue(), mode = modeFilter.getValue();
        Tab selected = sourceTabs.getSelectionModel().getSelectedItem();
        filtered.setPredicate(row -> {
            boolean tab = selected == activeTab ? row.state() == TunnelEngine.State.ACTIVE
                : selected == tabbyTab ? row.profile().origin() == TunnelProfile.Origin.TABBY
                : selected == mobaTab ? row.profile().origin() == TunnelProfile.Origin.MOBAXTERM
                : row.profile().origin() == TunnelProfile.Origin.CUSTOM;
            boolean type = mode == null || mode.equals("Tutti i tipi")
                || (mode.startsWith("LOCAL") && row.profile().mode() == TunnelProfile.Mode.LOCAL)
                || (mode.startsWith("REMOTE") && row.profile().mode() == TunnelProfile.Mode.REMOTE)
                || (mode.startsWith("DYNAMIC") && row.profile().mode() == TunnelProfile.Mode.DYNAMIC);
            boolean ipMatch = ipQuery.isEmpty() || row.resolvedIp().toLowerCase(Locale.ROOT).contains(ipQuery);
            return tab && type && ipMatch && row.profile().searchable().contains(query)
                && (status == null || status.equals("Tutti gli stati") || row.state().label().equals(status));
        });
    }
    private void refreshCounters() {
        long count = rows.stream().filter(r -> r.state() == TunnelEngine.State.ACTIVE).count();
        totals.setText(""+rows.size()); active.setText(""+count);
        errors.setText(""+rows.stream().filter(r -> r.state() == TunnelEngine.State.ERROR).count()); tray.update(count, rows.size());
    }
    private void onTunnelEvent(TunnelEngine.Event event) {
        for (TunnelRow row : rows) if (row.profile().id().equals(event.id())) { row.accept(event); break; }
        refreshCounters(); if (sourceTabs.getSelectionModel().getSelectedItem() == activeTab) updateFilter(); showSelection();
    }
    private void showSelection() {
        TunnelRow row = table.getSelectionModel().getSelectedItem();
        if (row == null) { selectedInfo.setText("Seleziona una riga per vedere i dettagli."); logs.clear(); return; }
        selectedInfo.setText((row.profile().installation().isBlank() ? "" : row.profile().installation() + " · ") + row.profile().name() + " · " + row.detail()); logs.setText(row.logs()); logs.positionCaret(logs.getLength());
    }
    private List<TunnelProfile> profiles() { return rows.stream().map(TunnelRow::profile).toList(); }
    private boolean persist(List<TunnelProfile> next) {
        try { ProfileStore.save(configFile,next); return true; }
        catch (Exception e) { error("Salvataggio non riuscito. La modifica non è stata applicata.\n" + e.getMessage()); return false; }
    }
    private void addProfile(TunnelProfile profile) {
        List<TunnelProfile> next = new ArrayList<>(profiles()); next.add(profile);
        if (persist(next)) { TunnelRow row = new TunnelRow(profile); rows.add(row); resolveHost(row); sourceTabs.getSelectionModel().select(tabFor(profile.origin())); table.getSelectionModel().select(row); refreshCounters(); }
    }
    private void edit(TunnelRow row) {
        if (row != null && engine.isRunning(row.profile().id())) { error("Ferma il tunnel prima di modificarlo."); return; }
        TunnelProfile old = row == null ? null : row.profile();
        new ProfileDialog(window,old,old != null && (secrets.contains(old.id()) || vault.contains(old))).showAndWait().ifPresent(result -> {
            try {
                TunnelProfile changed = result.profile();
                List<TunnelProfile> next = new ArrayList<>(profiles());
                if (old == null) next.add(changed); else next.set(next.indexOf(old),changed);
                if (!persist(next)) return;
                if (old != null && (!old.hostKeyId().equals(changed.hostKeyId()) || !old.username().equals(changed.username())
                    || old.auth() != changed.auth() || !old.privateKey().equals(changed.privateKey()))) secrets.forget(old.id());
                if (result.newSecret().length > 0) {
                    secrets.put(changed.id(),result.newSecret());
                    if (result.remember()) vaultUi.remember(changed,result.newSecret());
                    else if (vault.exists() && !vaultUi.forget(changed.id())) error("La credenziale precedente resta nell'archivio; la nuova resta soltanto in memoria.");
                }
                if (row == null) { TunnelRow added=new TunnelRow(changed); rows.add(added); resolveHost(added); sourceTabs.getSelectionModel().select(customTab); }
                else { row.setProfile(changed); resolveHost(row); }
                updateFilter(); table.refresh(); refreshCounters(); showSelection();
            } finally { Arrays.fill(result.newSecret(),'\0'); }
        });
    }
    private void delete(TunnelRow row) {
        if (engine.isRunning(row.profile().id())) { error("Ferma il tunnel prima di eliminarlo."); return; }
        if (!confirm("Elimina profilo", "Eliminare «" + row.profile().name() + "»?")) return;
        if (!vaultUi.forget(row.profile().id())) return;
        List<TunnelProfile> next = new ArrayList<>(profiles()); next.remove(row.profile());
        if (persist(next)) { rows.remove(row); secrets.forget(row.profile().id()); refreshCounters(); }
    }
    private void startAll(boolean visibleOnly) {
        List<TunnelRow> chosen = List.copyOf(visibleOnly ? filtered : rows);
        for (TunnelRow row : chosen) if (!engine.isRunning(row.profile().id())) startOne(row);
    }
    private void startOne(TunnelRow row) {
        TunnelProfile p = row.profile();
        if (quitting || UiWork.busy() || engine.isRunning(p.id())) return;
        if (!p.isLoopbackBind() && !confirm("Ascolto non limitato al loopback",
            "Il profilo «"+p.name()+"» richiede l'ascolto su "+p.listener()+".\nPotrebbe rendere accessibile il servizio ad altri dispositivi. Continuare?")) return;
        char[] secret = secrets.copy(p.id());
        try {
            if (secret.length == 0 && vault.exists() && !vault.unlocked() && !vaultUi.ensureOpen()) return;
            if (secret.length == 0 && vault.contains(p)) secret = vault.copy(p);
            if (secret.length == 0 && !vault.contains(p)) {
                char[] entered = askSecret(p);
                if (entered == null) return;
                secret = entered;
                if (p.auth() == TunnelProfile.Auth.PASSWORD && secret.length == 0) { error("La password non può essere vuota."); return; }
                secrets.put(p.id(),secret);
            }
            engine.start(p,secret);
        } finally { Arrays.fill(secret,'\0'); }
    }
    private char[] askSecret(TunnelProfile profile) {
        Dialog<char[]> dialog = new Dialog<>(); dialog.initOwner(window); dialog.setTitle(profile.name());
        dialog.setHeaderText(profile.auth() == TunnelProfile.Auth.PASSWORD ? "Password SSH per " + profile.endpoint() : "Passphrase della chiave (vuota se non cifrata)");
        PasswordField field = new PasswordField(); field.setPromptText("Password SSH / passphrase");
        CheckBox remember = new CheckBox("Salva nell’archivio locale cifrato"); remember.setSelected(true);
        dialog.getDialogPane().setContent(new VBox(12,field,remember));
        ButtonType connect = new ButtonType("Connetti", ButtonBar.ButtonData.OK_DONE); dialog.getDialogPane().getButtonTypes().addAll(connect,ButtonType.CANCEL);
        dialog.setResultConverter(b -> b == connect ? field.getText().toCharArray() : null);
        dialog.setOnShown(e -> field.requestFocus());
        Optional<char[]> result = dialog.showAndWait(); field.clear();
        if (result.isPresent() && remember.isSelected()) vaultUi.remember(profile,result.get());
        return result.orElse(null);
    }
    private HostTrustPrompt.Decision askHostTrust(TunnelProfile profile, String fingerprint, Cancellation cancellation) throws Exception {
        CompletableFuture<HostTrustPrompt.Decision> decision = new CompletableFuture<>();
        AtomicReference<Alert> shown = new AtomicReference<>();
        AutoCloseable hook = cancellation.onCancel(() -> {
            decision.complete(HostTrustPrompt.Decision.REJECT);
            Platform.runLater(() -> { Alert a = shown.get(); if (a != null) a.close(); });
        });
        try {
            Platform.runLater(() -> {
                if (decision.isDone() || quitting) { decision.complete(HostTrustPrompt.Decision.REJECT); return; }
                showWindow();
                ButtonType once = new ButtonType("Fino alla chiusura dell'app", ButtonBar.ButtonData.OTHER);
                ButtonType save = new ButtonType("Verificata: ricorda", ButtonBar.ButtonData.YES);
                ButtonType reject = new ButtonType("Rifiuta", ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION); shown.set(alert); alert.initOwner(window);
                alert.setTitle("Verifica identità del server SSH"); alert.setHeaderText("Server non ancora conosciuto: " + profile.hostKeyId());
                TextArea message = new TextArea("Impronta della chiave host:\n\n" + fingerprint +
                    "\n\nConfrontala con l'impronta comunicata dall'amministratore tramite un canale indipendente. Non confermare soltanto perché il nome del server è corretto.\n\nQuesta finestra scade dopo 90 secondi.");
                message.setEditable(false); message.setWrapText(true); message.setPrefRowCount(9);
                alert.getDialogPane().setContent(message); alert.getButtonTypes().setAll(once,save,reject);
                for (ButtonType type : List.of(once,save,reject)) ((Button)alert.getDialogPane().lookupButton(type)).setDefaultButton(type == reject);
                alert.setOnHidden(e -> decision.complete(alert.getResult() == save ? HostTrustPrompt.Decision.REMEMBER
                    : alert.getResult() == once ? HostTrustPrompt.Decision.THIS_APP_SESSION : HostTrustPrompt.Decision.REJECT));
                alert.show();
            });
            return decision.get(90,TimeUnit.SECONDS);
        } catch (TimeoutException | CancellationException e) { return HostTrustPrompt.Decision.REJECT; }
        finally { hook.close(); Platform.runLater(() -> { Alert a = shown.get(); if (a != null) a.close(); }); }
    }
    private FileChooser fileChooser(String title) {
        FileChooser chooser = new FileChooser(); chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Profili Nexu Port Forwarding (*.properties)","*.properties")); return chooser;
    }
    private void exportProfiles() {
        FileChooser chooser = fileChooser("Esporta i profili senza credenziali"); chooser.setInitialFileName("nexu-tunnels.properties");
        File chosen = chooser.showSaveDialog(window);
        if (chosen != null) try {
            if (chosen.toPath().toAbsolutePath().normalize().startsWith(configFile.getParent().toAbsolutePath()))
                throw new IllegalArgumentException("Esporta fuori dalla cartella dati dell’applicazione.");
            ProfileStore.save(chosen.toPath(),profiles()); }
        catch (Exception e) { error("Esportazione non riuscita: " + e.getMessage()); }
    }
    private void importProfiles() {
        File chosen = fileChooser("Importa profili").showOpenDialog(window); if (chosen == null) return;
        try {
            List<TunnelProfile> imported = ProfileStore.load(chosen.toPath());
            if (imported.isEmpty()) { error("Il file non contiene profili."); return; }
            if (!confirm("Importa configurazione", "Aggiungere " + imported.size() + " profili?\nLe password e le chiavi host non vengono importate. Nessun tunnel verrà avviato.")) return;
            List<TunnelProfile> next = new ArrayList<>(profiles()); Set<UUID> ids = new HashSet<>(); next.forEach(p -> ids.add(p.id()));
            List<TunnelProfile> added = new ArrayList<>();
            for (TunnelProfile p : imported) { if (ids.contains(p.id())) p = p.copyWithNewId(); ids.add(p.id()); added.add(p); next.add(p); }
            if (persist(next)) { added.forEach(p -> { TunnelRow row=new TunnelRow(p); rows.add(row); resolveHost(row); }); updateFilter(); refreshCounters(); }
        } catch (Exception e) { error("Importazione rifiutata. Nessuna modifica applicata.\n" + e.getMessage()); }
    }
    private void importTabby() {
        if (quitting || UiWork.busy()) return;
        sourceTabs.getSelectionModel().select(tabbyTab);
        new TabbyImportDialog(window,profiles()).showAndWait().ifPresent(selected -> {
            if (selected.isEmpty() || quitting) return;
            try {
                TabbyImport.Plan plan = TabbyImport.append(profiles(),selected);
                if (!plan.added().isEmpty() && !persist(plan.profiles())) return;
                plan.added().forEach(p -> { TunnelRow row=new TunnelRow(p); rows.add(row); resolveHost(row); });
                search.clear(); ipFilter.clear(); statusFilter.getSelectionModel().selectFirst(); modeFilter.getSelectionModel().selectFirst(); updateFilter(); refreshCounters();
                appLog.mark("tabby-import-added="+plan.added().size()+" duplicates="+plan.duplicates());
                Alert a = new Alert(Alert.AlertType.INFORMATION,
                    "Importati "+plan.added().size()+" inoltri in Tabby. Duplicati ignorati: "+plan.duplicates()+
                    ".\nNessun tunnel avviato. Inserisci le credenziali e verifica la chiave host prima del collegamento.",ButtonType.OK);
                a.initOwner(window); a.setHeaderText("Importazione Tabby completata"); a.showAndWait();
            } catch (Exception ex) { error("Importazione non salvata: "+ex.getMessage()); }
        });
    }
    private void importMobaXterm() {
        if (quitting || UiWork.busy()) return;
        sourceTabs.getSelectionModel().select(mobaTab);
        new MobaXtermImportDialog(window,profiles()).showAndWait().ifPresent(selected -> {
            if (selected.isEmpty() || quitting) return;
            try {
                MobaXtermImport.Plan plan = MobaXtermImport.append(profiles(),selected);
                if (!plan.added().isEmpty() && !persist(plan.profiles())) return;
                plan.added().forEach(p -> { TunnelRow row=new TunnelRow(p); rows.add(row); resolveHost(row); });
                search.clear(); ipFilter.clear(); statusFilter.getSelectionModel().selectFirst(); modeFilter.getSelectionModel().selectFirst(); updateFilter(); refreshCounters();
                appLog.mark("mobaxterm-import-added="+plan.added().size()+" duplicates="+plan.duplicates());
                Alert a = new Alert(Alert.AlertType.INFORMATION,
                    "Importati "+plan.added().size()+" inoltri in MobaXterm. Duplicati ignorati: "+plan.duplicates()+
                    ".\nI profili sono ora salvati localmente da Nexu Port Forwarding. Nessun tunnel è stato avviato.",ButtonType.OK);
                a.initOwner(window); a.setHeaderText("Importazione MobaXterm completata"); a.showAndWait();
            } catch (Exception ex) { error("Importazione non salvata: "+ex.getMessage()); }
        });
    }

    private Tab tabFor(TunnelProfile.Origin origin) {
        return switch(origin) { case TABBY -> tabbyTab; case MOBAXTERM -> mobaTab; default -> customTab; };
    }

    private void resolveHost(TunnelRow row) {
        TunnelProfile profile=row.profile(); String host=profile.sshHost(); UUID id=profile.id();
        if (HostAddressResolver.isIpLiteral(host)) { row.setResolvedIp(host.replace("[","").replace("]","")); return; }
        row.setResolvedIp("");
        Thread.ofVirtual().name("npf-dns-"+id).start(() -> {
            String resolved=""; try { resolved=HostAddressResolver.resolve(host); } catch (UnknownHostException ignored) { }
            String value=resolved; Platform.runLater(() -> {
                if (row.profile().id().equals(id) && row.profile().sshHost().equals(host)) { row.setResolvedIp(value); if (!ipFilter.getText().isBlank()) updateFilter(); }
            });
        });
    }

    private void showHostKeys() {
        Dialog<Void> dialog = new Dialog<>(); dialog.initOwner(window); dialog.setTitle("Chiavi host verificate"); dialog.setHeaderText("L'identità è associata a hostname + porta SSH");
        ListView<String> list = new ListView<>(); Runnable refresh = () -> list.getItems().setAll(hostKeys.entries().keySet().stream().sorted().toList()); refresh.run();
        Label fingerprint = new Label("Seleziona un server."); fingerprint.setWrapText(true);
        list.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> fingerprint.setText(b == null ? "" : hostKeys.expected(b)));
        Button forget = new Button("Dimentica la chiave selezionata…"); forget.setOnAction(e -> {
            String host = list.getSelectionModel().getSelectedItem(); if (host == null) return;
            if (engine.runningCount() != 0) { error("Ferma tutti i tunnel prima di modificare le chiavi host."); return; }
            if (!confirm("Rimuovi chiave host", "Dimenticare la chiave per "+host+"?\nAl prossimo collegamento sarà necessaria una nuova verifica con l'amministratore.")) return;
            try { hostKeys.forget(host); refresh.run(); } catch (Exception ex) { error(ex.getMessage()); }
        });
        VBox box = new VBox(14,list,fingerprint,forget); box.setPrefSize(630,380); dialog.getDialogPane().setContent(box); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); dialog.showAndWait();
    }
    private void restoreWindowState() {
        Rectangle2D primary = Screen.getPrimary().getVisualBounds();
        WindowStateStore.State saved = windowStateStore.load().orElse(null);
        Rectangle2D bounds = primary;
        boolean savedVisible = false;
        if (saved != null) {
            List<Screen> screens = Screen.getScreensForRectangle(saved.x(), saved.y(), Math.max(1, saved.width()), Math.max(1, saved.height()));
            if (!screens.isEmpty()) { bounds = screens.get(0).getVisualBounds(); savedVisible = true; }
        }

        double minWidth = Math.min(bounds.getWidth() * 0.95, Math.min(900, Math.max(640, bounds.getWidth() * 0.55)));
        double minHeight = Math.min(bounds.getHeight() * 0.92, Math.min(620, Math.max(480, bounds.getHeight() * 0.55)));
        double maxWidth = Math.max(minWidth, bounds.getWidth() * 0.92);
        double maxHeight = Math.max(minHeight, bounds.getHeight() * 0.88);
        double defaultWidth = Math.max(minWidth, Math.min(1180, maxWidth));
        double defaultHeight = Math.max(minHeight, Math.min(760, maxHeight));

        normalWidth = savedVisible ? clamp(saved.width(), minWidth, maxWidth) : defaultWidth;
        normalHeight = savedVisible ? clamp(saved.height(), minHeight, maxHeight) : defaultHeight;
        normalX = savedVisible ? clamp(saved.x(), bounds.getMinX(), bounds.getMaxX() - normalWidth)
            : bounds.getMinX() + (bounds.getWidth() - normalWidth) / 2;
        normalY = savedVisible ? clamp(saved.y(), bounds.getMinY(), bounds.getMaxY() - normalHeight)
            : bounds.getMinY() + (bounds.getHeight() - normalHeight) / 2;

        window.setMinWidth(minWidth);
        window.setMinHeight(minHeight);
        window.setX(normalX); window.setY(normalY); window.setWidth(normalWidth); window.setHeight(normalHeight);
        window.setFullScreen(false);
        restoreMaximized = savedVisible && saved.maximized();
    }
    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
    private void captureNormalBounds() {
        if (window == null || !window.isShowing() || window.isMaximized() || window.isIconified() || window.isFullScreen()) return;
        double x=window.getX(), y=window.getY(), w=window.getWidth(), h=window.getHeight();
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(w) && Double.isFinite(h) && w >= 100 && h >= 100) {
            normalX=x; normalY=y; normalWidth=w; normalHeight=h;
        }
    }
    private void saveWindowState() {
        if (windowStateStore == null || !Double.isFinite(normalX) || !Double.isFinite(normalY)
            || !Double.isFinite(normalWidth) || !Double.isFinite(normalHeight)) return;
        captureNormalBounds();
        try { windowStateStore.save(new WindowStateStore.State(normalX, normalY, normalWidth, normalHeight, window.isMaximized())); }
        catch (Exception e) { if (appLog != null) appLog.mark("window-state-save-failed"); }
    }
    private void handleWindowClose() {
        if (quitting || UiWork.busy()) return;
        int running = engine.runningCount();
        ButtonType minimize = new ButtonType(trayReady ? "Riduci nell’area di notifica" : "Riduci a icona", ButtonBar.ButtonData.OTHER);
        ButtonType exit = new ButtonType("Esci dall’applicazione", ButtonBar.ButtonData.YES);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.initOwner(window); a.setTitle("Chiudi Nexu Port Forwarding"); a.setHeaderText("Cosa vuoi fare?");
        a.setContentText(running > 0
            ? "Sono attivi " + running + " tunnel. Se esci verranno arrestati; se riduci, continueranno a funzionare."
            : (trayReady ? "Puoi lasciare l’applicazione nell’area di notifica oppure chiuderla."
                         : "La tray non è disponibile: puoi minimizzare la finestra oppure chiudere l’applicazione."));
        a.getButtonTypes().setAll(minimize, exit, ButtonType.CANCEL);
        Optional<ButtonType> choice = a.showAndWait();
        if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) return;
        if (choice.get() == minimize) {
            saveWindowState();
            if (trayReady) window.hide(); else window.setIconified(true);
            return;
        }
        beginExit();
    }
    private void showWindow() { if (!quitting) { window.show(); window.setIconified(false); window.setFullScreen(false); window.toFront(); } }
    private void copy(String text) { ClipboardContent content = new ClipboardContent(); content.putString(text); Clipboard.getSystemClipboard().setContent(content); }
    private boolean confirm(String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,message,ButtonType.YES,ButtonType.NO); a.initOwner(window); a.setTitle(title); a.setHeaderText(title);
        return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }
    private void error(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR,message,ButtonType.OK); a.initOwner(window); a.setHeaderText("Operazione non completata"); a.showAndWait();
    }
    private void requestExit() {
        if (quitting || UiWork.busy()) return;
        if (engine.runningCount() > 0 && !confirm("Chiudi Nexu Port Forwarding", "Arrestare tutti i tunnel e uscire?")) return;
        beginExit();
    }
    private void beginExit() {
        if (quitting) return;
        saveWindowState();
        quitting = true; root.setDisable(true); footer.setText("Chiusura dei tunnel in corso…"); engine.close();
        Thread.ofVirtual().start(() -> {
            try { engine.awaitStopped(10_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            secrets.close(); vault.close();
            if (appLock != null) try { appLock.close(); } catch (Exception ignored) { }
            if (appLog != null) appLog.close();
            Platform.runLater(() -> { tray.close(); Platform.exit(); System.exit(0); });
        });
    }
    @Override public void stop() { if (engine != null) engine.close(); secrets.close(); if(vault!=null) vault.close(); tray.close(); if(appLog!=null) appLog.close(); }
}
