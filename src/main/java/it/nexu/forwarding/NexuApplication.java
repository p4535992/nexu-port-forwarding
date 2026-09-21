package it.nexu.forwarding;

import it.nexu.forwarding.config.*;
import it.nexu.forwarding.importer.TabbyImport;
import it.nexu.forwarding.importer.MobaXtermImport;
import it.nexu.forwarding.i18n.I18n;
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
    private final SecretStore secrets = new SecretStore(), proxySecrets = new SecretStore();
    private final TraySupport tray = new TraySupport();
    private final TableView<TunnelRow> table = new TableView<>();
    private final TextField installationFilter = new TextField(), nameFilter = new TextField(), hostFilter = new TextField();
    private final TabPane sourceTabs = new TabPane();
    private final Tab activeTab = new Tab("Attivi"), customTab = new Tab("Custom"), tabbyTab = new Tab("Tabby"), mobaTab = new Tab("MobaXterm");
    private final ComboBox<String> statusFilter = new ComboBox<>(), modeFilter = new ComboBox<>();
    private final CheckMenuItem openSshDiagnostics = new CheckMenuItem("Diagnostica configurazione OpenSSH locale all'avvio");
    private final Label totals = new Label(), active = new Label(), errors = new Label();
    private final Label selectedInfo = new Label("Seleziona una riga per vedere i dettagli."), footer = new Label();
    private final TextArea logs = new TextArea();
    private FilteredList<TunnelRow> filtered;
    private Stage window;
    private BorderPane root;
    private AppLock appLock;
    private Path configFile, languageFile;
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
            languageFile = home.resolve("language.properties");
            I18n.setLanguage(LanguageSettings.load(languageFile));
            appLog = AppLog.inDirectory(storage.logsDirectory());
            windowStateStore = new WindowStateStore(home.resolve("window.properties"));
            vault = new VaultStore(home.resolve("credentials.npfvault"));
            configFile = home.resolve("profiles.properties");
            hostKeys = new HostKeyStore(home.resolve("host-keys.properties"));
            for (TunnelProfile profile : ProfileStore.load(configFile)) { TunnelRow row=new TunnelRow(profile); rows.add(row); resolveHost(row); }
            engine = new TunnelEngine(new MinaTunnelBackend(hostKeys, this::askHostTrust, p -> proxySecrets.copy(p.id())),
                event -> { appLog.event(event); Platform.runLater(() -> onTunnelEvent(event)); });
            vaultUi = new VaultUi(window,vault,secrets,engine,configFile,this::profiles,
                list -> { rows.setAll(list.stream().map(TunnelRow::new).toList()); rows.forEach(this::resolveHost); updateFilter(); refreshCounters(); },this::error,
                proxySecrets::close);
            buildWindow();
            restoreWindowState();
            stage.show();
            if (restoreMaximized) Platform.runLater(() -> window.setMaximized(true));
            trayReady = !Boolean.getBoolean("nexu.smokeTest") && tray.install(this::showWindow,
                () -> { showWindow(); startAll(false); }, () -> engine.stopAll(), this::requestExit);
            Platform.setImplicitExit(false);
            footer.setText(trayReady
                ? I18n.t("The X asks whether to minimize to the notification area or exit. — minimizes; □ maximizes/restores.","La X chiede se ridurre nell’area di notifica oppure uscire. — minimizza; □ massimizza/ripristina.")
                : I18n.t("Tray unavailable: the X offers normal minimization or exit. — and □ remain native controls.","Tray non disponibile: la X offre minimizzazione normale oppure uscita. — e □ restano i controlli nativi."));
            refreshCounters();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { engine.close(); secrets.close(); proxySecrets.close(); vault.close(); }, "nexu-shutdown"));
            appLog.mark("ui-ready");
            if (migrated > 0) appLog.mark("legacy-data-copied=" + migrated);
            if (Boolean.getBoolean("nexu.smokeTest")) {
                if (window.getStyle() != StageStyle.DECORATED || window.isFullScreen()
                    || Screen.getScreensForRectangle(window.getX(), window.getY(), Math.max(1, window.getWidth()), Math.max(1, window.getHeight())).isEmpty())
                    throw new IllegalStateException("Window smoke check failed: native decorated window is not visible.");
                SafeFiles.writeBytes(home.resolve("ui-ready"), "UI_READY 1.2.0 DECORATED WINDOWED".getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
        activeTab.setText(I18n.t("Active","Attivi"));
        customTab.setText("Custom"); tabbyTab.setText("Tabby"); mobaTab.setText("MobaXterm");
        openSshDiagnostics.setText(I18n.t("Diagnose local OpenSSH configuration on Start","Diagnostica configurazione OpenSSH locale all'avvio"));
        selectedInfo.setText(I18n.t("Select a row to view details.","Seleziona una riga per vedere i dettagli."));
        Label title = new Label("Nexu Port Forwarding"); title.getStyleClass().add("app-title");
        Label subtitle = new Label(I18n.t("SSH tunnels from your desktop · Windows / Linux","Tunnel SSH dal tuo desktop · Windows / Linux")); subtitle.getStyleClass().add("muted");
        VBox branding = new VBox(5, title, subtitle);
        HBox counters = new HBox(10, counter(I18n.t("PROFILES","PROFILI"), totals), counter(I18n.t("ACTIVE","ATTIVI"), active), counter(I18n.t("ERRORS","ERRORI"), errors));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(20, branding, spacer, counters); heading.setAlignment(Pos.CENTER_LEFT);
        Button add = new Button(I18n.t("+ New tunnel","+ Nuovo tunnel")); add.getStyleClass().add("primary"); add.setOnAction(e -> edit(null));
        Button stop = new Button(I18n.t("Stop all","Ferma tutti")); stop.setOnAction(e -> engine.stopAll());
        MenuButton file = new MenuButton(I18n.t("Profiles","Profili"));
        MenuItem sample = new MenuItem(I18n.t("Add Maven example","Aggiungi l'esempio Maven")); sample.setOnAction(e -> addProfile(TunnelProfile.example()));
        MenuItem importTabbyItem = new MenuItem(I18n.t("Import Tabby…","Importa Tabby…")); importTabbyItem.setOnAction(e -> importTabby());
        MenuItem importMobaItem = new MenuItem(I18n.t("Import MobaXterm…","Importa MobaXterm…")); importMobaItem.setOnAction(e -> importMobaXterm());
        MenuItem importItem = new MenuItem(I18n.t("Import configuration…","Importa configurazione…")); importItem.setOnAction(e -> importProfiles());
        MenuItem exportItem = new MenuItem(I18n.t("Export configuration…","Esporta configurazione…")); exportItem.setOnAction(e -> exportProfiles());
        file.getItems().addAll(sample, new SeparatorMenuItem(), importTabbyItem, importMobaItem,
            new SeparatorMenuItem(), importItem, exportItem);
        Button hosts = new Button(I18n.t("Host keys","Chiavi host")); hosts.setOnAction(e -> showHostKeys());
        Button quit = new Button(I18n.t("Exit","Esci")); quit.setOnAction(e -> requestExit());
        Button openLogs = new Button(I18n.t("Open logs","Apri log")); openLogs.setOnAction(e -> {
            try { java.awt.Desktop.getDesktop().open(appLog.directory().toFile()); }
            catch (Exception ex) { error(I18n.t("Log folder: ","Cartella dei log: ") + appLog.directory()); }
        });
        MenuButton settings = new MenuButton(I18n.t("Settings","Impostazioni"));
        MenuItem dataLocation = new MenuItem(I18n.t("Data folder…","Cartella dati…"));
        dataLocation.setOnAction(e -> {
            if (!quitting && !UiWork.busy()) StorageSettingsDialog.show(window, storage, this::error);
        });
        Menu languageMenu = new Menu(I18n.t("Language","Lingua"));
        ToggleGroup languageGroup = new ToggleGroup();
        RadioMenuItem englishLanguage = new RadioMenuItem("English");
        RadioMenuItem italianLanguage = new RadioMenuItem("Italiano");
        englishLanguage.setToggleGroup(languageGroup); italianLanguage.setToggleGroup(languageGroup);
        I18n.Language savedLanguage = LanguageSettings.load(languageFile);
        englishLanguage.setSelected(savedLanguage == I18n.Language.ENGLISH);
        italianLanguage.setSelected(savedLanguage == I18n.Language.ITALIAN);
        englishLanguage.setOnAction(e -> saveLanguagePreference(I18n.Language.ENGLISH,englishLanguage,italianLanguage));
        italianLanguage.setOnAction(e -> saveLanguagePreference(I18n.Language.ITALIAN,englishLanguage,italianLanguage));
        languageMenu.getItems().addAll(englishLanguage,italianLanguage);
        openSshDiagnostics.setSelected(false);
        openSshDiagnostics.setOnAction(e -> {
            if (openSshDiagnostics.isSelected() && !confirm(I18n.t("Local OpenSSH diagnostics","Diagnostica OpenSSH locale"),
                I18n.t("When enabled, before starting a tunnel Nexu runs «ssh -G» and compares it with «ssh -F NUL/-F /dev/null -G».\n\n"
                    + "It does not open an SSH connection, but OpenSSH may evaluate local Match exec rules in the configuration.\n\n"
                    + "Enable diagnostics for this session?",
                    "Quando è attiva, prima di avviare un tunnel Nexu esegue «ssh -G» e lo confronta con «ssh -F NUL/-F /dev/null -G».\n\n"
                    + "Non apre una connessione SSH, ma OpenSSH può valutare eventuali regole locali Match exec presenti nella configurazione.\n\n"
                    + "Abilitare la diagnostica per questa sessione?"))) {
                openSshDiagnostics.setSelected(false);
            }
        });
        settings.getItems().addAll(dataLocation, languageMenu, new SeparatorMenuItem(), openSshDiagnostics);
        FlowPane commands = new FlowPane(9, 9, add, stop, file, vaultUi.menu(), hosts, openLogs, settings, quit);
        installationFilter.setPromptText(I18n.t("Filter installation…","Filtra installazione…")); installationFilter.setPrefWidth(180);
        nameFilter.setPromptText(I18n.t("Filter name…","Filtra nome…")); HBox.setHgrow(nameFilter, Priority.ALWAYS);
        hostFilter.setPromptText(I18n.t("Filter hostname / IP address…","Filtra hostname / indirizzo IP…")); hostFilter.setPrefWidth(240);
        statusFilter.getItems().add(I18n.t("All states","Tutti gli stati"));
        for (TunnelEngine.State state : TunnelEngine.State.values()) statusFilter.getItems().add(state.label());
        statusFilter.getSelectionModel().selectFirst(); statusFilter.setPrefWidth(170);
        modeFilter.getItems().addAll(I18n.t("All types","Tutti i tipi"), "LOCAL (-L)", "REMOTE (-R)", "DYNAMIC (SOCKS)");
        modeFilter.getSelectionModel().selectFirst(); modeFilter.setPrefWidth(175);
        Button clear = new Button(I18n.t("Clear filters","Azzera filtri")); clear.setOnAction(e -> {
            installationFilter.clear(); nameFilter.clear(); hostFilter.clear();
            statusFilter.getSelectionModel().selectFirst(); modeFilter.getSelectionModel().selectFirst();
        });
        HBox filters = new HBox(10, installationFilter, nameFilter, hostFilter, modeFilter, statusFilter, clear);
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
        logs.setEditable(false); logs.setWrapText(true); logs.getStyleClass().add("log-area");
        logs.setPromptText(I18n.t("Events for the selected tunnel appear here. State history is also saved in the local logs folder; the last 300 details stay in memory.","Gli eventi del tunnel selezionato compariranno qui. Lo storico di stato viene salvato anche nella cartella logs locale; gli ultimi 300 dettagli restano in memoria."));
        logs.setMinHeight(0); logs.setMaxHeight(Double.MAX_VALUE); VBox.setVgrow(logs, Priority.ALWAYS);
        selectedInfo.setWrapText(true); selectedInfo.getStyleClass().add("muted");
        Label logTitle = new Label(I18n.t("TUNNEL ACTIVITY","ATTIVITÀ DEL TUNNEL")); logTitle.getStyleClass().add("section-title");
        footer.getStyleClass().add("muted"); footer.setWrapText(true);
        Label meaning = new Label(I18n.t("Green = SSH + forwarding established. This is not a health check of the final application.","Verde = SSH + forwarding stabiliti. Non è un controllo di salute dell'applicazione finale."));
        meaning.getStyleClass().add("muted"); meaning.setWrapText(true);
        VBox logPanel = new VBox(10, logTitle, selectedInfo, logs, meaning, footer);
        logPanel.getStyleClass().add("log-side-panel"); logPanel.setPadding(new Insets(16));
        logPanel.setMinWidth(300); logPanel.setPrefWidth(380);
        SplitPane workspace = new SplitPane(sourceTabs, logPanel);
        workspace.setOrientation(Orientation.HORIZONTAL); workspace.setDividerPositions(0.72);

        double[] logDivider = {0.72};
        Button logToggle = new Button("›");
        logToggle.getStyleClass().add("log-toggle");
        logToggle.setTooltip(new Tooltip(I18n.t("Hide log panel","Nascondi pannello log")));
        logToggle.setOnAction(e -> {
            if (workspace.getItems().contains(logPanel)) {
                if (!workspace.getDividers().isEmpty()) logDivider[0] = workspace.getDividers().getFirst().getPosition();
                workspace.getItems().remove(logPanel);
                logToggle.setText("‹");
                logToggle.setTooltip(new Tooltip(I18n.t("Show log panel","Mostra pannello log")));
            } else {
                workspace.getItems().add(logPanel);
                logToggle.setText("›");
                logToggle.setTooltip(new Tooltip(I18n.t("Hide log panel","Nascondi pannello log")));
                Platform.runLater(() -> workspace.setDividerPositions(logDivider[0]));
            }
        });

        StackPane workspaceShell = new StackPane(workspace, logToggle);
        StackPane.setAlignment(logToggle, Pos.CENTER_RIGHT);
        StackPane.setMargin(logToggle, new Insets(0, 6, 0, 0));
        root = new BorderPane(workspaceShell, top, null, null, null); BorderPane.setMargin(workspaceShell, new Insets(0,26,20,26));
        Scene scene = new Scene(root, 1320, 760); scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        window.setScene(scene); window.setTitle("Nexu Port Forwarding 1.2.0");
        window.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/app-icon.png"))));
        window.xProperty().addListener((o,a,b) -> captureNormalBounds());
        window.yProperty().addListener((o,a,b) -> captureNormalBounds());
        window.widthProperty().addListener((o,a,b) -> captureNormalBounds());
        window.heightProperty().addListener((o,a,b) -> captureNormalBounds());
        window.maximizedProperty().addListener((o,a,b) -> { if (!b) Platform.runLater(this::captureNormalBounds); });
        window.setOnCloseRequest(e -> { e.consume(); handleWindowClose(); });
        table.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> showSelection());
    }
    private void saveLanguagePreference(I18n.Language language,RadioMenuItem english,RadioMenuItem italian) {
        try {
            LanguageSettings.save(languageFile,language);
            english.setSelected(language==I18n.Language.ENGLISH); italian.setSelected(language==I18n.Language.ITALIAN);
            Alert done=new Alert(Alert.AlertType.INFORMATION,
                I18n.t("Language saved. Restart Nexu Port Forwarding to apply it.","Lingua salvata. Riavvia Nexu Port Forwarding per applicarla."),
                ButtonType.OK);
            done.initOwner(window); done.setHeaderText(I18n.t("Restart required","Riavvio necessario")); done.showAndWait();
        } catch(Exception ex) {
            I18n.Language saved=LanguageSettings.load(languageFile);
            english.setSelected(saved==I18n.Language.ENGLISH); italian.setSelected(saved==I18n.Language.ITALIAN);
            error(I18n.t("Could not save the language preference.\n","Impossibile salvare la preferenza della lingua.\n")+ex.getMessage());
        }
    }

    private VBox counter(String label, Label value) {
        Label caption = new Label(label); caption.getStyleClass().add("section-title"); value.getStyleClass().add("counter-number");
        VBox box = new VBox(5, value, caption); box.getStyleClass().add("counter"); box.setMinWidth(100); return box;
    }

    private void createTable() {
        filtered = new FilteredList<>(rows, r -> true);
        SortedList<TunnelRow> sorted = new SortedList<>(filtered); sorted.comparatorProperty().bind(table.comparatorProperty()); table.setItems(sorted);
        installationFilter.textProperty().addListener((o,a,b) -> updateFilter());
        nameFilter.textProperty().addListener((o,a,b) -> updateFilter());
        hostFilter.textProperty().addListener((o,a,b) -> updateFilter());
        statusFilter.valueProperty().addListener((o,a,b) -> updateFilter()); modeFilter.valueProperty().addListener((o,a,b) -> updateFilter());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); table.setFixedCellSize(-1); table.setEditable(true);
        TableColumn<TunnelRow,String> installation = textColumn(I18n.t("INSTALLATION","INSTALLAZIONE"), 155, r -> r.profile().installation());
        installation.setMinWidth(110);
        installation.setEditable(true);
        installation.setCellFactory(c -> new AutoSaveTextCell("—"));
        installation.setOnEditCommit(e -> {
            TunnelRow row = e.getRowValue();
            if (UiWork.busy() || engine.isRunning(row.profile().id())) { table.refresh(); error("Ferma il tunnel prima di modificare l’installazione."); return; }
            try {
                TunnelProfile changed = row.profile().withInstallation(e.getNewValue());
                List<TunnelProfile> next = new ArrayList<>(profiles());
                int index = next.indexOf(row.profile());
                if (index < 0) throw new IllegalStateException("Profilo non trovato.");
                next.set(index, changed);
                if (persist(next)) { row.setProfile(changed); updateFilter(); showSelection(); }
            } catch (RuntimeException ex) { error(ex.getMessage()); }
            table.refresh();
        });
        TableColumn<TunnelRow,String> name = textColumn(I18n.t("NAME","NOME"), 230, r -> r.profile().name());
        name.setMinWidth(150);
        name.setEditable(true);
        name.setCellFactory(c -> new AutoSaveTextCell());
        name.setOnEditCommit(e -> {
            TunnelRow row = e.getRowValue();
            if (UiWork.busy() || engine.isRunning(row.profile().id())) { table.refresh(); error("Ferma il tunnel prima di rinominarlo."); return; }
            try {
                TunnelProfile changed = row.profile().withName(e.getNewValue());
                List<TunnelProfile> next = new ArrayList<>(profiles());
                int index = next.indexOf(row.profile());
                if (index < 0) throw new IllegalStateException("Profilo non trovato.");
                next.set(index, changed);
                if (persist(next)) { row.setProfile(changed); updateFilter(); showSelection(); }
            } catch (RuntimeException ex) { error(ex.getMessage()); }
            table.refresh();
        });
        TableColumn<TunnelRow,String> forwarding = textColumn("FORWARDING", 320, r -> r.profile().forwardingSummary());
        forwarding.setMinWidth(220);
        forwarding.setCellFactory(c -> new WrappingTextCell());
        TableColumn<TunnelRow,String> hostAddress = new TableColumn<>(I18n.t("HOSTNAME / IP ADDRESS","HOSTNAME / INDIRIZZO IP")); hostAddress.setPrefWidth(250); hostAddress.setMinWidth(180);
        hostAddress.setCellValueFactory(c -> Bindings.createStringBinding(c.getValue()::hostAddressDisplay, c.getValue().resolvedIpProperty()));
        hostAddress.setCellFactory(c -> new WrappingTextCell());
        TableColumn<TunnelRow,TunnelRow> actions = new TableColumn<>(I18n.t("ACTIONS","AZIONI")); actions.setPrefWidth(185); actions.setMinWidth(165); actions.setSortable(false);
        actions.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        actions.setCellFactory(c -> new TableCell<>() {
            private Button runStop;
            @Override protected void updateItem(TunnelRow row, boolean empty) {
                if (runStop != null) {
                    runStop.textProperty().unbind();
                    runStop.disableProperty().unbind();
                    runStop.styleProperty().unbind();
                }
                super.updateItem(row,empty); setGraphic(null); setText(null);
                if (empty || row == null) return;
                runStop = new Button(); runStop.getStyleClass().add("small-primary");
                runStop.textProperty().bind(Bindings.createStringBinding(
                    () -> row.state().busy() ? I18n.t("■ Stop","■ Ferma") : I18n.t("▶ Start","▶ Avvia"), row.stateProperty()));
                runStop.styleProperty().bind(Bindings.createStringBinding(
                    () -> row.state().busy()
                        ? "-fx-text-fill: #ff9da6; -fx-background-color: #4c2631;"
                        : "-fx-text-fill: #7eead9; -fx-background-color: #16433f;",
                    row.stateProperty()));
                runStop.disableProperty().bind(Bindings.createBooleanBinding(
                    () -> row.state() == TunnelEngine.State.STOPPING, row.stateProperty()));
                runStop.setOnAction(e -> {
                    showLogsFor(row);
                    if (row.state().busy()) engine.stop(row.profile().id());
                    else startOne(row);
                });
                MenuButton menu = new MenuButton("⋯");
                MenuItem edit = new MenuItem(I18n.t("Edit…","Modifica…")); edit.setOnAction(e -> edit(row));
                MenuItem duplicate = new MenuItem(I18n.t("Duplicate (without password)","Duplica (senza password)")); duplicate.setOnAction(e -> {
                    try { addProfile(row.profile().duplicate()); } catch (RuntimeException ex) { error(ex.getMessage()); }
                });
                MenuItem log = new MenuItem(I18n.t("Show log","Mostra log")); log.setOnAction(e -> showLogsFor(row));
                MenuItem copyPs = new MenuItem(I18n.t("Copy Windows PowerShell command","Copia comando Windows PowerShell")); copyPs.setOnAction(e -> copy(OpenSshCommand.powershell(row.profile())));
                MenuItem copyCmd = new MenuItem(I18n.t("Copy Windows CMD command","Copia comando Windows CMD")); copyCmd.setOnAction(e -> copy(OpenSshCommand.cmd(row.profile())));
                MenuItem copySh = new MenuItem(I18n.t("Copy Linux / POSIX command","Copia comando Linux / POSIX")); copySh.setOnAction(e -> copy(OpenSshCommand.posix(row.profile())));
                MenuItem forget = new MenuItem(I18n.t("Forget in-memory password","Dimentica password in memoria")); forget.setOnAction(e -> {
                    if (engine.isRunning(row.profile().id())) { error("Ferma il tunnel prima di dimenticare la password."); return; }
                    secrets.forget(row.profile().id());
                });
                MenuItem forgetSaved = new MenuItem(I18n.t("Remove saved password…","Rimuovi password salvata…")); forgetSaved.setOnAction(e -> {
                    if (engine.isRunning(row.profile().id())) { error("Ferma il tunnel prima di rimuovere la password."); return; }
                    if (confirm("Rimuovi credenziale", "Rimuovere la password salvata per questo profilo?") && vaultUi.forget(row.profile().id())) secrets.forget(row.profile().id());
                });
                MenuItem delete = new MenuItem(I18n.t("Delete…","Elimina…")); delete.setOnAction(e -> delete(row));
                menu.getItems().addAll(edit, duplicate, log, new SeparatorMenuItem(), copyPs, copyCmd, copySh, forget, forgetSaved, new SeparatorMenuItem(), delete);
                HBox box = new HBox(6, runStop, menu); box.setAlignment(Pos.CENTER_LEFT); setGraphic(box);
            }
        });
        forwarding.setEditable(false); hostAddress.setEditable(false);
        actions.setEditable(false);
        table.getColumns().addAll(actions, installation, name, forwarding, hostAddress);
        Label emptyTitle = new Label(I18n.t("No tunnels to display","Nessun tunnel da mostrare")); emptyTitle.getStyleClass().add("empty-title");
        Label emptyHelp = new Label(I18n.t("Active: connected tunnels only. Custom: + New tunnel. Tabby/MobaXterm: use Import.","Attivi: solo tunnel connessi. Custom: + Nuovo tunnel. Tabby/MobaXterm: usa i pulsanti Importa.")); emptyHelp.getStyleClass().add("muted");
        VBox empty = new VBox(12, emptyTitle, emptyHelp); empty.setAlignment(Pos.CENTER); table.setPlaceholder(empty);
        table.setRowFactory(t -> {
            TableRow<TunnelRow> row = new TableRow<>() {
                private final javafx.beans.value.ChangeListener<TunnelEngine.State> stateListener =
                    (o, oldState, newState) -> refreshStateStyle();

                {
                    itemProperty().addListener((o, oldItem, newItem) -> {
                        if (oldItem != null) oldItem.stateProperty().removeListener(stateListener);
                        if (newItem != null) newItem.stateProperty().addListener(stateListener);
                        refreshStateStyle();
                    });
                }

                private void refreshStateStyle() {
                    getStyleClass().removeAll("row-stopped", "row-active");
                    TunnelRow item = getItem();
                    if (item == null) return;
                    if (item.state() == TunnelEngine.State.STOPPED) {
                        getStyleClass().add("row-stopped");
                    } else if (item.state() == TunnelEngine.State.ACTIVE) {
                        getStyleClass().add("row-active");
                    }
                }
            };
            row.setMinHeight(62);
            row.setPrefHeight(Region.USE_COMPUTED_SIZE);
            row.setMaxHeight(Double.MAX_VALUE);
            row.setPadding(new Insets(2,0,2,0));
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && table.getEditingCell() == null) {
                    javafx.scene.Node node = e.getTarget() instanceof javafx.scene.Node n ? n : null;
                    while (node != null && !(node instanceof TableCell<?,?>)) node = node.getParent();
                    if (node instanceof TableCell<?,?> cell && installation.equals(cell.getTableColumn())) table.edit(row.getIndex(), installation);
                    else if (node instanceof TableCell<?,?> cell && name.equals(cell.getTableColumn())) table.edit(row.getIndex(), name);
                    else edit(row.getItem());
                }
            });
            return row;
        });
    }

    private static final class AutoSaveTextCell extends TableCell<TunnelRow,String> {
        private final String emptyPlaceholder;
        private final Label display = new Label();
        private TextField editor;
        private AutoSaveTextCell() { this(""); }
        private AutoSaveTextCell(String emptyPlaceholder) {
            this.emptyPlaceholder = emptyPlaceholder == null ? "" : emptyPlaceholder;
            display.setWrapText(true);
            display.setTextOverrun(OverrunStyle.CLIP);
            display.setMinHeight(Region.USE_PREF_SIZE);
            display.setMaxWidth(Double.MAX_VALUE);
            display.prefWidthProperty().bind(Bindings.max(40, widthProperty().subtract(24)));
            widthProperty().addListener((o,a,b) -> requestRowLayout());
            display.textProperty().addListener((o,a,b) -> requestRowLayout());
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }
        private void requestRowLayout() {
            requestLayout();
            TableRow<TunnelRow> row = getTableRow();
            if (row != null) row.requestLayout();
            TableView<TunnelRow> view = getTableView();
            if (view != null) view.requestLayout();
        }
        @Override protected double computePrefHeight(double width) {
            if (isEditing()) return super.computePrefHeight(width);
            double available = Math.max(40, getWidth() > 0 ? getWidth() - 24 : width - 24);
            double content = display.prefHeight(available);
            return Math.max(super.computePrefHeight(width), content + 22);
        }
        @Override public void startEdit() {
            if (!isEditable() || !getTableView().isEditable() || !getTableColumn().isEditable()) return;
            super.startEdit();
            if (editor == null) {
                editor = new TextField();
                editor.setOnAction(e -> commitEditor());
                editor.focusedProperty().addListener((o,was,focused) -> { if (!focused && isEditing()) commitEditor(); });
            }
            editor.setText(getItem() == null ? "" : getItem());
            setText(null); setGraphic(editor);
            editor.selectAll(); editor.requestFocus();
        }
        private void commitEditor() {
            if (editor != null && isEditing()) commitEdit(editor.getText());
        }
        private void showDisplay(String value) {
            String shown = value == null || value.isBlank() ? emptyPlaceholder : value;
            display.setText(shown);
            setText(null);
            setGraphic(display);
            setTooltip(value == null || value.isBlank() ? null : new Tooltip(value));
            requestRowLayout();
        }
        @Override public void cancelEdit() {
            super.cancelEdit();
            showDisplay(getItem());
        }
        @Override protected void updateItem(String value, boolean empty) {
            super.updateItem(value,empty);
            if (empty) { setText(null); setGraphic(null); setTooltip(null); }
            else if (!isEditing()) showDisplay(value);
        }
    }

    private static final class WrappingTextCell extends TableCell<TunnelRow,String> {
        private final Label display = new Label();
        private WrappingTextCell() {
            display.setWrapText(true);
            display.setTextOverrun(OverrunStyle.CLIP);
            display.setMinHeight(Region.USE_PREF_SIZE);
            display.setMaxWidth(Double.MAX_VALUE);
            display.prefWidthProperty().bind(Bindings.max(40, widthProperty().subtract(24)));
            widthProperty().addListener((o,a,b) -> requestRowLayout());
            display.textProperty().addListener((o,a,b) -> requestRowLayout());
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }
        private void requestRowLayout() {
            requestLayout();
            TableRow<TunnelRow> row = getTableRow();
            if (row != null) row.requestLayout();
            TableView<TunnelRow> view = getTableView();
            if (view != null) view.requestLayout();
        }
        @Override protected double computePrefHeight(double width) {
            double available = Math.max(40, getWidth() > 0 ? getWidth() - 24 : width - 24);
            double content = display.prefHeight(available);
            return Math.max(super.computePrefHeight(width), content + 22);
        }
        @Override protected void updateItem(String value, boolean empty) {
            super.updateItem(value,empty);
            if (empty) {
                display.setText("");
                setText(null); setGraphic(null); setTooltip(null);
            } else {
                String shown = value == null ? "" : value;
                display.setText(shown);
                setText(null); setGraphic(display);
                setTooltip(shown.isBlank() ? null : new Tooltip(shown.replace("\n"," · ")));
                requestRowLayout();
            }
        }
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
        String installationQuery = installationFilter.getText().trim().toLowerCase(Locale.ROOT);
        String nameQuery = nameFilter.getText().trim().toLowerCase(Locale.ROOT);
        String hostQuery = hostFilter.getText().trim().toLowerCase(Locale.ROOT);
        String status = statusFilter.getValue(), mode = modeFilter.getValue();
        Tab selected = sourceTabs.getSelectionModel().getSelectedItem();
        filtered.setPredicate(row -> {
            boolean tab = selected == activeTab ? row.state() == TunnelEngine.State.ACTIVE
                : selected == tabbyTab ? row.profile().origin() == TunnelProfile.Origin.TABBY
                : selected == mobaTab ? row.profile().origin() == TunnelProfile.Origin.MOBAXTERM
                : row.profile().origin() == TunnelProfile.Origin.CUSTOM;
            boolean type = mode == null || mode.equals(I18n.t("All types","Tutti i tipi"))
                || (mode.startsWith("LOCAL") && row.profile().mode() == TunnelProfile.Mode.LOCAL)
                || (mode.startsWith("REMOTE") && row.profile().mode() == TunnelProfile.Mode.REMOTE)
                || (mode.startsWith("DYNAMIC") && row.profile().mode() == TunnelProfile.Mode.DYNAMIC);
            boolean installationMatch = installationQuery.isEmpty()
                || row.profile().installation().toLowerCase(Locale.ROOT).contains(installationQuery);
            boolean nameMatch = nameQuery.isEmpty()
                || row.profile().name().toLowerCase(Locale.ROOT).contains(nameQuery);
            boolean hostMatch = hostQuery.isEmpty()
                || row.hostAddressDisplay().toLowerCase(Locale.ROOT).contains(hostQuery);
            return tab && type && installationMatch && nameMatch && hostMatch
                && (status == null || status.equals(I18n.t("All states","Tutti gli stati")) || row.state().label().equals(status));
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
        if (row == null) { selectedInfo.setText(I18n.t("Select a row to view details.","Seleziona una riga per vedere i dettagli.")); logs.clear(); return; }
        selectedInfo.setText((row.profile().installation().isBlank() ? "" : row.profile().installation() + " · ") + row.profile().name() + " · " + row.detail());
        logs.setText(row.logs()); logs.positionCaret(logs.getLength());
    }
    private void showLogsFor(TunnelRow row) {
        if (row == null) return;
        table.getSelectionModel().select(row);
        table.scrollTo(row);
        showSelection();
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
                if (old != null && (old.proxyType()!=changed.proxyType() || !old.proxyHost().equals(changed.proxyHost())
                    || old.proxyPort()!=changed.proxyPort() || !old.proxyUsername().equals(changed.proxyUsername()))) proxySecrets.forget(old.id());
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
        if (persist(next)) { rows.remove(row); secrets.forget(row.profile().id()); proxySecrets.forget(row.profile().id()); refreshCounters(); }
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
        appendEquivalentOpenSshCommands(row,p);
        if (openSshDiagnostics.isSelected()) {
            try {
                OpenSshConfigDiagnostic.Result diagnostic = UiWork.run(window, "Analisi configurazione OpenSSH…",
                    () -> OpenSshConfigDiagnostic.inspect(p));
                appLog.openSshDiagnostic(p.id(), diagnostic.summary());
                row.appendDiagnostic("OpenSSH", diagnostic.summary());
                showSelection();
            } catch (Exception diagnosticError) {
                String detail = "Diagnostica OpenSSH non completata: " + safeDiagnosticText(diagnosticError.getMessage());
                appLog.openSshDiagnostic(p.id(), detail);
                row.appendDiagnostic("OpenSSH", detail);
                showSelection();
            }
        }
        try {
            MinaTunnelBackend.preflightLocalListener(p);
        } catch (TunnelBackend.Failure preflight) {
            TunnelEngine.Event event = new TunnelEngine.Event(p.id(), TunnelEngine.State.ERROR, preflight.getMessage(), java.time.Instant.now());
            appLog.event(event); row.accept(event); table.refresh(); refreshCounters(); showSelection();
            return;
        }
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
            if (p.proxyNeedsPassword() && !proxySecrets.contains(p.id())) {
                char[] proxySecret = askProxySecret(p);
                if (proxySecret == null) return;
                try {
                    if (proxySecret.length == 0) { error("La password del proxy non può essere vuota quando è specificato un utente proxy."); return; }
                    proxySecrets.put(p.id(),proxySecret);
                } finally { Arrays.fill(proxySecret,'\0'); }
            }
            engine.start(p,secret);
        } finally { Arrays.fill(secret,'\0'); }
    }
    private void appendEquivalentOpenSshCommands(TunnelRow row,TunnelProfile profile) {
        row.appendDiagnostic("OpenSSH equivalente",
            "Nexu NON esegue ssh.exe: usa Apache MINA SSHD. I comandi seguenti riproducono i parametri SSH/forwarding del profilo senza leggere ~/.ssh/config.");
        row.appendDiagnostic("PowerShell", OpenSshCommand.diagnosticPowershell(profile));
        row.appendDiagnostic("Linux/POSIX", OpenSshCommand.diagnosticPosix(profile));
        if (profile.usesProxy()) {
            row.appendDiagnostic("Trasporto proxy Nexu",
                profile.proxyLabel()+" "+profile.proxyEndpoint()
                    + (profile.proxyUsername().isBlank() ? "" : " · utente="+profile.proxyUsername())
                    + ". Il proxy è gestito internamente da Nexu e non è rappresentato dai comandi OpenSSH sopra.");
        } else {
            row.appendDiagnostic("Trasporto Nexu","Diretto · nessun proxy configurato nel profilo.");
        }
        showSelection();
    }
    private static String safeDiagnosticText(String value) {
        String text = value == null || value.isBlank() ? "errore non specificato" : value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return text.length() > 300 ? text.substring(0,300) : text;
    }
    private char[] askProxySecret(TunnelProfile profile) {
        Dialog<char[]> dialog = new Dialog<>(); dialog.initOwner(window); dialog.setTitle(profile.name());
        dialog.setHeaderText("Password proxy " + profile.proxyLabel() + " per " + profile.proxyEndpoint());
        PasswordRevealField field = new PasswordRevealField(); field.setPromptText("Password proxy per " + profile.proxyUsername());
        Label help = new Label("La password proxy resta soltanto in memoria fino alla chiusura dell'app o al comando «Blocca e dimentica segreti in memoria».");
        help.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(12,help,field));
        ButtonType connect = new ButtonType("Continua", ButtonBar.ButtonData.OK_DONE); dialog.getDialogPane().getButtonTypes().addAll(connect,ButtonType.CANCEL);
        dialog.setResultConverter(b -> b == connect ? field.getText().toCharArray() : null);
        dialog.setOnShown(e -> field.requestInputFocus());
        Optional<char[]> result = dialog.showAndWait(); field.clear();
        return result.orElse(null);
    }
    private char[] askSecret(TunnelProfile profile) {
        Dialog<char[]> dialog = new Dialog<>(); dialog.initOwner(window); dialog.setTitle(profile.name());
        dialog.setHeaderText(profile.auth() == TunnelProfile.Auth.PASSWORD ? "Password SSH per " + profile.endpoint() : "Passphrase della chiave (vuota se non cifrata)");
        PasswordRevealField field = new PasswordRevealField(); field.setPromptText("Password SSH / passphrase");
        CheckBox remember = new CheckBox("Salva nell’archivio locale cifrato"); remember.setSelected(true);
        dialog.getDialogPane().setContent(new VBox(12,field,remember));
        ButtonType connect = new ButtonType("Connetti", ButtonBar.ButtonData.OK_DONE); dialog.getDialogPane().getButtonTypes().addAll(connect,ButtonType.CANCEL);
        dialog.setResultConverter(b -> b == connect ? field.getText().toCharArray() : null);
        dialog.setOnShown(e -> field.requestInputFocus());
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
                installationFilter.clear(); nameFilter.clear(); hostFilter.clear(); statusFilter.getSelectionModel().selectFirst(); modeFilter.getSelectionModel().selectFirst(); updateFilter(); refreshCounters();
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
                installationFilter.clear(); nameFilter.clear(); hostFilter.clear(); statusFilter.getSelectionModel().selectFirst(); modeFilter.getSelectionModel().selectFirst(); updateFilter(); refreshCounters();
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
                if (row.profile().id().equals(id) && row.profile().sshHost().equals(host)) { row.setResolvedIp(value); if (!hostFilter.getText().isBlank()) updateFilter(); }
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
        double defaultWidth = Math.max(minWidth, Math.min(1320, maxWidth));
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
    @Override public void stop() { if (engine != null) engine.close(); secrets.close(); proxySecrets.close(); if(vault!=null) vault.close(); tray.close(); if(appLog!=null) appLog.close(); }
}
