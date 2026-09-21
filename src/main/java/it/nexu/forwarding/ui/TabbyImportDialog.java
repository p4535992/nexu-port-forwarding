package it.nexu.forwarding.ui;

import it.nexu.forwarding.importer.*;
import it.nexu.forwarding.model.TunnelProfile;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** User-initiated, read-only YAML import with a selectable preview. */
public final class TabbyImportDialog extends Dialog<List<TunnelProfile>> {
    private final TextField file=new TextField(), installation=new TextField();
    private final TableView<Preview> table=new TableView<>();
    private final TextArea details=new TextArea();
    private final Label status=new Label("Scegli config.yaml e premi Analizza file. Nessuna connessione verrà avviata.");
    private final Set<String> existing=new HashSet<>();
    private String reportSummary = "";
    private final List<TunnelProfile> existingProfiles;
    private final ButtonType accept=new ButtonType("Importa selezionati",ButtonBar.ButtonData.OK_DONE);
    private static final class Preview {
        final TabbyImport.Candidate candidate;
        final BooleanProperty selected=new SimpleBooleanProperty();
        final boolean duplicate;
        final String detail;
        Preview(TabbyImport.Candidate c,boolean duplicate,String detail) {
            this.candidate=c; this.duplicate=duplicate; this.detail=detail;
            selected.set(!duplicate&&!c.requiresReview());
        }
    }
    public TabbyImportDialog(Window owner,List<TunnelProfile> current) {
        initOwner(owner); setTitle("Nexu Port Forwarding · Importa da Tabby"); setResizable(true);
        setHeaderText("Importa gli inoltri locali, remoti e dinamici dal config.yaml di Tabby");
        existingProfiles=List.copyOf(current);
        current.stream().map(TunnelProfile::sourceKey).filter(k->!k.isEmpty()).forEach(existing::add);
        file.setPromptText("Percorso di config.yaml");
        TabbyYamlReader.suggestedPaths().stream().filter(Files::isRegularFile).findFirst()
            .or(()->TabbyYamlReader.suggestedPaths().stream().findFirst()).ifPresent(p->file.setText(p.toString()));
        installation.setPromptText("Nome installazione per queste righe (vuoto = gruppo Tabby)");
        Button browse=new Button("Sfoglia…");
        browse.setOnAction(e->{
            FileChooser fc=new FileChooser(); fc.setTitle("Seleziona config.yaml di Tabby");
            fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Configurazione YAML","*.yaml","*.yml"),
                new FileChooser.ExtensionFilter("Tutti i file","*.*"));
            try { Path parent=Path.of(file.getText()).toAbsolutePath().getParent(); if(Files.isDirectory(parent)) fc.setInitialDirectory(parent.toFile()); }
            catch(InvalidPathException ignored) { }
            File chosen=fc.showOpenDialog(owner); if(chosen!=null) file.setText(chosen.getAbsolutePath());
        });
        Button scan=new Button("Analizza file"); scan.setOnAction(e->analyze(owner));
        HBox pathBox=new HBox(8,file,browse,scan); HBox.setHgrow(file,Priority.ALWAYS);
        table.setEditable(true); table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        TableColumn<Preview,Boolean> choose=new TableColumn<>("Importa"); choose.setPrefWidth(65);
        choose.setCellValueFactory(c->c.getValue().selected);
        choose.setCellFactory(c->new CheckBoxTableCell<>() {
            @Override public void updateItem(Boolean selected,boolean empty) {
                super.updateItem(selected,empty);
                Preview p=getTableRow()==null?null:getTableRow().getItem();
                setDisable(empty||p==null||p.duplicate);
            }
        });
        table.getColumns().add(choose);
        table.getColumns().add(column("Installazione",140,p->p.candidate.profile().installation()));
        table.getColumns().add(column("Profilo / inoltro",230,p->p.candidate.profile().name()));
        table.getColumns().add(column("Tipo",90,p->p.candidate.profile().mode().name()));
        table.getColumns().add(column("Server SSH",200,p->p.candidate.profile().endpoint()));
        table.getColumns().add(column("Ascolto",155,p->p.candidate.profile().listener()));
        table.getColumns().add(column("Destinazione",210,p->p.candidate.profile().destination()));
        table.getColumns().add(column("Esito / avvisi",330,p->p.detail));
        table.setPlaceholder(new Label("L'anteprima apparirà dopo Analizza file."));
        Button all=new Button("Seleziona nuovi (inclusi avvisi)");
        all.setOnAction(e->table.getItems().forEach(p->p.selected.set(!p.duplicate)));
        Button none=new Button("Deseleziona tutti"); none.setOnAction(e->table.getItems().forEach(p->p.selected.set(false)));
        Label privacy=new Label("Il file Tabby viene soltanto letto. Password, vault, chiavi private incorporate, script e fiducia host non vengono importati. I nuovi tunnel restano fermi.");
        privacy.setWrapText(true); status.setWrapText(true);
        details.setEditable(false); details.setWrapText(true); details.setPrefRowCount(4);
        details.setPromptText("Le righe saltate o che richiedono revisione sono riportate qui.");
        table.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{
            if(b!=null) details.setText(reportSummary+"\n\nRiga selezionata: "+b.candidate.profile().name()+"\n"+b.detail);
        });
        VBox content=new VBox(10,pathBox,installation,privacy,new HBox(8,all,none),table,status,details);
        content.setPadding(new Insets(10)); content.setPrefSize(1060,580); VBox.setVgrow(table,Priority.ALWAYS);
        getDialogPane().setContent(content); getDialogPane().getButtonTypes().addAll(accept,ButtonType.CANCEL);
        getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        file.textProperty().addListener((o,a,b)->invalidate());
        installation.textProperty().addListener((o,a,b)->invalidate());
        setResultConverter(button->button==accept?table.getItems().stream().filter(p->p.selected.get()&&!p.duplicate)
            .map(p->p.candidate.profile()).toList():null);
        invalidate();
    }
    private void invalidate() {
        table.getItems().clear(); details.clear(); reportSummary="";
        status.setText("Premi Analizza file per aggiornare l'anteprima."); updateButton();
    }
    private void updateButton() {
        getDialogPane().lookupButton(accept).setDisable(table.getItems().stream().noneMatch(p->p.selected.get()&&!p.duplicate));
    }
    private void analyze(Window owner) {
        if(UiWork.busy()) return;
        invalidate();
        try {
            Path source=Path.of(file.getText()).toAbsolutePath().normalize(); String label=installation.getText();
            TabbyImport.Report report=UiWork.run(owner,"Lettura configurazione Tabby…",()->TabbyYamlReader.read(source,label));
            Set<String> seen=new HashSet<>(existing); List<Preview> previews=new ArrayList<>();
            int duplicateCount=0;
            for(TabbyImport.Candidate c:report.candidates()) {
                boolean duplicate=!seen.add(c.profile().sourceKey()); if(duplicate) duplicateCount++;
                String description=duplicate?"Già importato / duplicato: nessuna modifica.":"Nuovo profilo, inizialmente fermo.";
                if(existingProfiles.stream().anyMatch(p->sameListener(p,c.profile())))
                    description+="\nPossibile conflitto con una porta già configurata: verificare prima dell'avvio.";
                if(!c.warnings().isEmpty()) description+="\n"+String.join("\n",c.warnings());
                Preview p=new Preview(c,duplicate,description);
                p.selected.addListener((o,a,b)->updateButton()); previews.add(p);
            }
            table.setItems(FXCollections.observableArrayList(previews));
            status.setText("Profili SSH: "+report.sshProfiles()+" · Inoltri validi: "+previews.size()+" · Duplicati: "+duplicateCount+
                " · Profili saltati: "+report.skippedProfiles()+" · Inoltri saltati: "+report.skippedForwards()+
                ". Le righe da rivedere non sono preselezionate. Nessun profilo esistente viene sovrascritto.");
            reportSummary=report.warnings().isEmpty()?"Nessuna riga scartata. Seleziona una riga per leggerne gli avvisi.":String.join("\n",report.warnings());
            details.setText(reportSummary);
            updateButton();
        } catch(Exception e) {
            // All YAML parser errors have already been stripped of source snippets by TabbyYamlReader.
            status.setText("Importazione non eseguita. Nessuna modifica ai profili.");
            Throwable reason = e instanceof java.io.IOException ? e : e.getCause();
            details.setText(reason instanceof java.io.IOException ? reason.getMessage() : "File non valido o non leggibile. Controllare il percorso.");
        }
    }
    private static boolean sameListener(TunnelProfile a,TunnelProfile b) {
        boolean ar=a.mode()==TunnelProfile.Mode.REMOTE,br=b.mode()==TunnelProfile.Mode.REMOTE;
        if(ar!=br||a.bindPort()!=b.bindPort()) return false;
        return !ar||(a.sshHost().equalsIgnoreCase(b.sshHost())&&a.sshPort()==b.sshPort());
    }
    private static TableColumn<Preview,String> column(String label,int width,Function<Preview,String> value) {
        TableColumn<Preview,String> column=new TableColumn<>(label); column.setPrefWidth(width); column.setEditable(false);
        column.setCellValueFactory(c->new ReadOnlyStringWrapper(value.apply(c.getValue())));
        column.setCellFactory(c->new TableCell<>() {
            @Override protected void updateItem(String text,boolean empty) { super.updateItem(text,empty); setText(empty?null:text); setTooltip(empty?null:new Tooltip(text)); }
        });
        return column;
    }
}
