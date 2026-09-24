package it.nexu.forwarding.ui;

import it.nexu.forwarding.importer.MobaXtermImport;
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

/** Read-only import preview for MobaXterm.ini [PortForwarding]. */
public final class MobaXtermImportDialog extends Dialog<List<TunnelProfile>> {
    private final TextField file=new TextField(),installation=new TextField();
    private final TableView<Preview> table=new TableView<>(); private final TextArea details=new TextArea();
    private final Label status=new Label("Scegli MobaXterm.ini e premi Analizza file. Nessuna connessione verrà avviata.");
    private final Set<String> existing=new HashSet<>(); private final List<TunnelProfile> existingProfiles;
    private final ButtonType accept=new ButtonType("Importa selezionati",ButtonBar.ButtonData.OK_DONE);
    private static final class Preview { final MobaXtermImport.Candidate candidate; final BooleanProperty selected=new SimpleBooleanProperty(); final boolean duplicate; final String detail; Preview(MobaXtermImport.Candidate c,boolean d,String detail){candidate=c;duplicate=d;this.detail=detail;selected.set(!d&&!c.requiresReview());} }

    public MobaXtermImportDialog(Window owner,List<TunnelProfile> current){
        initOwner(owner);setTitle("Nexu Port Forwarding · Importa da MobaXterm");setResizable(true);setHeaderText("Importa soltanto i tunnel dalla sezione [PortForwarding]");
        existingProfiles=List.copyOf(current);current.stream().map(TunnelProfile::sourceKey).filter(k->!k.isEmpty()).forEach(existing::add);
        file.setPromptText("Percorso di MobaXterm.ini");MobaXtermImport.suggestedPaths().stream().filter(Files::isRegularFile).findFirst().or(()->MobaXtermImport.suggestedPaths().stream().findFirst()).ifPresent(p->file.setText(p.toString()));
        installation.setPromptText("Nome installazione per queste righe (vuoto = nome tunnel/MobaXterm)");
        Button browse=new Button("Sfoglia…");browse.setOnAction(e->{FileChooser fc=new FileChooser();fc.setTitle("Seleziona MobaXterm.ini");fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Configurazione MobaXterm","*.ini","*.mobaconf"),new FileChooser.ExtensionFilter("Tutti i file","*.*"));try{Path parent=Path.of(file.getText()).toAbsolutePath().getParent();if(Files.isDirectory(parent))fc.setInitialDirectory(parent.toFile());}catch(Exception ignored){}File chosen=fc.showOpenDialog(owner);if(chosen!=null)file.setText(chosen.getAbsolutePath());});
        Button scan=new Button("Analizza file");scan.setOnAction(e->analyze(owner));HBox pathBox=new HBox(8,file,browse,scan);HBox.setHgrow(file,Priority.ALWAYS);
        table.setEditable(true);table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);TableColumn<Preview,Boolean> choose=new TableColumn<>("Importa");choose.setPrefWidth(65);choose.setCellValueFactory(c->c.getValue().selected);choose.setCellFactory(c->new CheckBoxTableCell<>(){@Override public void updateItem(Boolean selected,boolean empty){super.updateItem(selected,empty);Preview p=getTableRow()==null?null:getTableRow().getItem();setDisable(empty||p==null||p.duplicate);}});table.getColumns().add(choose);
        table.getColumns().add(column("Installazione",140,p->p.candidate.profile().installation()));table.getColumns().add(column("Tunnel",220,p->p.candidate.profile().name()));table.getColumns().add(column("Tipo",90,p->p.candidate.profile().mode().name()));table.getColumns().add(column("Server SSH",210,p->p.candidate.profile().endpoint()));table.getColumns().add(column("Ascolto",155,p->p.candidate.profile().listener()));table.getColumns().add(column("Destinazione",210,p->p.candidate.profile().destination()));table.getColumns().add(column("Esito / avvisi",330,p->p.detail));table.setPlaceholder(new Label("L'anteprima apparirà dopo Analizza file."));
        Button all=new Button("Seleziona nuovi (inclusi avvisi)");all.setOnAction(e->table.getItems().forEach(p->p.selected.set(!p.duplicate)));Button none=new Button("Deseleziona tutti");none.setOnAction(e->table.getItems().forEach(p->p.selected.set(false)));
        Label privacy=new Label("Viene letta soltanto [PortForwarding]. Password, sezione Passwords, trust host e altre impostazioni MobaXterm non vengono importati. I tunnel importati vengono salvati localmente da Nexu Port Forwarding e restano fermi.");privacy.setWrapText(true);status.setWrapText(true);details.setEditable(false);details.setWrapText(true);details.setPrefRowCount(4);
        VBox content=new VBox(10,pathBox,installation,privacy,new HBox(8,all,none),table,status,details);content.setPadding(new Insets(10));content.setPrefSize(1060,580);VBox.setVgrow(table,Priority.ALWAYS);getDialogPane().setContent(content);getDialogPane().getButtonTypes().addAll(accept,ButtonType.CANCEL);getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());file.textProperty().addListener((o,a,b)->invalidate());installation.textProperty().addListener((o,a,b)->invalidate());setResultConverter(b->b==accept?table.getItems().stream().filter(p->p.selected.get()&&!p.duplicate).map(p->p.candidate.profile()).toList():null);invalidate();
    }
    private void invalidate(){table.getItems().clear();details.clear();status.setText("Premi Analizza file per aggiornare l'anteprima.");updateButton();}
    private void updateButton(){getDialogPane().lookupButton(accept).setDisable(table.getItems().stream().noneMatch(p->p.selected.get()&&!p.duplicate));}
    private void analyze(Window owner){if(UiWork.busy())return;invalidate();try{Path source=Path.of(file.getText()).toAbsolutePath().normalize();MobaXtermImport.Report report=UiWork.run(owner,"Lettura configurazione MobaXterm…",()->MobaXtermImport.read(source,installation.getText()));Set<String> seen=new HashSet<>(existing);List<Preview> previews=new ArrayList<>();int duplicateCount=0;for(MobaXtermImport.Candidate c:report.candidates()){boolean duplicate=!seen.add(c.profile().sourceKey());if(duplicate)duplicateCount++;String d=duplicate?"Già importato / duplicato: nessuna modifica.":"Nuovo profilo, inizialmente fermo.";if(existingProfiles.stream().anyMatch(p->sameListener(p,c.profile())))d+="\nPossibile conflitto con una porta già configurata.";if(!c.warnings().isEmpty())d+="\n"+String.join("\n",c.warnings());Preview p=new Preview(c,duplicate,d);p.selected.addListener((o,a,b)->updateButton());previews.add(p);}table.setItems(FXCollections.observableArrayList(previews));long reviewCount=previews.stream().filter(p->!p.duplicate&&p.candidate.requiresReview()).count();status.setText("Voci PortForwarding: "+report.entries()+" · Valide: "+previews.size()+" · Duplicati: "+duplicateCount+" · Da rivedere: "+reviewCount+" · Saltate: "+report.skipped()+". Le righe con avvisi non sono preselezionate: spuntale singolarmente o usa «Seleziona nuovi (inclusi avvisi)». Nessun profilo esistente viene sovrascritto.");details.setText(report.warnings().isEmpty()?"Nessuna voce scartata.":String.join("\n",report.warnings()));updateButton();}catch(Exception e){status.setText("Importazione non eseguita. Nessuna modifica ai profili.");details.setText(e.getMessage()==null?"File non valido o non leggibile.":e.getMessage());}}
    private static boolean sameListener(TunnelProfile a,TunnelProfile b){boolean ar=a.mode()==TunnelProfile.Mode.REMOTE,br=b.mode()==TunnelProfile.Mode.REMOTE;if(ar!=br||a.bindPort()!=b.bindPort())return false;return !ar||(a.sshHost().equalsIgnoreCase(b.sshHost())&&a.sshPort()==b.sshPort());}
    private static TableColumn<Preview,String> column(String label,int width,Function<Preview,String> value){TableColumn<Preview,String> c=new TableColumn<>(label);c.setPrefWidth(width);c.setCellValueFactory(v->new ReadOnlyStringWrapper(value.apply(v.getValue())));c.setCellFactory(x->new TableCell<>(){@Override protected void updateItem(String t,boolean empty){super.updateItem(t,empty);setText(empty?null:t);setTooltip(empty?null:new Tooltip(t));}});return c;}
}
