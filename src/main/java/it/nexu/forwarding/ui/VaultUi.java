package it.nexu.forwarding.ui;

import it.nexu.forwarding.config.*;
import it.nexu.forwarding.model.TunnelProfile;
import it.nexu.forwarding.ssh.TunnelEngine;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

public final class VaultUi {
    private final Window owner; private final VaultStore vault; private final SecretStore memory;
    private final TunnelEngine engine; private final Path profilesFile;
    private final Supplier<List<TunnelProfile>> profiles; private final Consumer<List<TunnelProfile>> replaceRows;
    private final Consumer<String> error;
    private final Runnable clearAdditionalMemory;
    private final MenuButton menu=new MenuButton();
    public VaultUi(Window owner,VaultStore vault,SecretStore memory,TunnelEngine engine,Path profilesFile,
                   Supplier<List<TunnelProfile>> profiles,Consumer<List<TunnelProfile>> replaceRows,Consumer<String> error,
                   Runnable clearAdditionalMemory) {
        this.owner=owner;this.vault=vault;this.memory=memory;this.engine=engine;this.profilesFile=profilesFile;
        this.profiles=profiles;this.replaceRows=replaceRows;this.error=error;
        this.clearAdditionalMemory=clearAdditionalMemory==null?()->{}:clearAdditionalMemory;
        item("Crea / sblocca archivio",()->ensureOpen());
        item("Blocca e dimentica segreti in memoria",()->{
            if(engine.runningCount()!=0) { error.accept("Ferma prima tutti i tunnel: anche le riconnessioni possono usare credenziali in memoria."); return; }
            vault.close(); memory.close(); clearAdditionalMemory.run(); refresh();
        });
        item("Cambia password principale…",this::changePassword);
        menu.getItems().add(new SeparatorMenuItem());
        item("Esporta backup cifrato…",this::exportBackup); item("Importa backup cifrato…",this::importBackup); refresh();
    }
    private void item(String name,Runnable action) { MenuItem item=new MenuItem(name); item.setOnAction(e->action.run());menu.getItems().add(item); }
    public MenuButton menu() { return menu; }
    private void refresh() { menu.setText(vault.unlocked()?"Password · sbloccate":"Password · bloccate"); }
    public boolean ensureOpen() {
        if(vault.unlocked()) return true;
        boolean creating=!vault.exists();
        char[] password=askPassword(creating?"Crea archivio locale":"Sblocca archivio locale",creating);
        if(password==null) return false;
        try { UiWork.run(owner,"Sblocco archivio cifrato…",()->{vault.unlock(password);return null;}); refresh(); return true; }
        catch(Exception e) { error.accept(e.getMessage()); return false; }
        finally { Arrays.fill(password,'\0'); }
    }
    public boolean remember(TunnelProfile profile,char[] secret) {
        if(!ensureOpen()) return false;
        try { UiWork.run(owner,"Salvataggio credenziale locale…",()->{vault.put(profile,secret);return null;}); return true; }
        catch(Exception e) { error.accept("Profilo salvato, ma credenziale NON salvata su disco.\n"+e.getMessage());return false; }
    }
    public boolean forget(UUID id) {
        if(!vault.exists()) return true;
        if(!ensureOpen()) return false;
        try { UiWork.run(owner,"Rimozione credenziale locale…",()->{vault.forget(id);return null;}); return true; }
        catch(Exception e) { error.accept(e.getMessage());return false; }
    }
    private char[] askPassword(String title,boolean creating) {
        Dialog<char[]> dialog=new Dialog<>(); dialog.initOwner(owner); dialog.setTitle(title); dialog.setHeaderText(title);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        PasswordRevealField first=new PasswordRevealField(),second=new PasswordRevealField();
        first.setPromptText("Password principale"); second.setPromptText("Ripeti la password");
        Label help=new Label(creating?"Almeno 12 caratteri. Non viene salvata: se la perdi, non è recuperabile. Nessun dato viene inviato a servizi cloud.":"La password sblocca soltanto l'archivio locale selezionato.");
        help.setWrapText(true); help.setMaxWidth(500);
        Label validation=new Label();
        validation.getStyleClass().add("error-text"); validation.setWrapText(true);
        validation.setMinHeight(42); validation.setMaxWidth(Double.MAX_VALUE);
        VBox content=new VBox(12,help,first); content.setPrefWidth(520);
        if(creating) content.getChildren().add(second);
        content.getChildren().add(validation);
        dialog.getDialogPane().setContent(content);
        ButtonType ok=new ButtonType("Conferma",ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok,ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ok).addEventFilter(ActionEvent.ACTION,e->{
            String problem=PasswordRules.validate(first.getText(),second.getText(),creating);
            if(problem!=null) {
                validation.setText(problem);
                if(creating && problem.contains("secondo campo")) second.requestInputFocus();
                else if(creating && problem.contains("non coincidono")) second.requestInputFocus();
                else first.requestInputFocus();
                e.consume();
            } else validation.setText("");
        });
        dialog.setResultConverter(b->b==ok?first.getText().toCharArray():null);
        dialog.setOnShown(e->first.requestInputFocus());
        Optional<char[]> result=dialog.showAndWait();
        first.clear(); second.clear();
        return result.orElse(null);
    }
    private void changePassword() {
        if(!ensureOpen())return; char[] password=askPassword("Nuova password principale",true);if(password==null)return;
        try { UiWork.run(owner,"Aggiornamento cifratura…",()->{vault.changePassword(password);return null;}); }
        catch(Exception e){error.accept(e.getMessage());}finally{Arrays.fill(password,'\0');}
    }
    private FileChooser chooser(String title) {
        FileChooser c=new FileChooser();c.setTitle(title);c.getExtensionFilters().add(new FileChooser.ExtensionFilter("Backup cifrato NexU (*.npfbackup)","*.npfbackup"));return c;
    }
    private boolean insideData(Path file) { return file.toAbsolutePath().normalize().startsWith(profilesFile.toAbsolutePath().getParent()); }
    private void exportBackup() {
        if(!ensureOpen())return;
        FileChooser c=chooser("Esporta profili e password salvate");c.setInitialFileName("nexu-port-forwarding.npfbackup");
        File selected=c.showSaveDialog(owner);if(selected==null)return;
        // Never overwrite an active application file, even when the chooser allows arbitrary extensions.
        if(insideData(selected.toPath())) {error.accept("Scegli una cartella esterna alla cartella dati dell'applicazione per il backup.");return;}
        char[] password=askPassword("Password del backup (può essere diversa)",true);if(password==null)return;
        List<TunnelProfile> snapshot=profiles.get();
        try { UiWork.run(owner,"Creazione backup cifrato…",()->{BackupService.exportTo(selected.toPath(),snapshot,vault,password);return null;}); }
        catch(Exception e){error.accept(e.getMessage());}finally{Arrays.fill(password,'\0');}
    }
    private void importBackup() {
        if(engine.runningCount()!=0){error.accept("Ferma tutti i tunnel prima di importare un backup.");return;}
        File selected=chooser("Importa backup cifrato").showOpenDialog(owner);if(selected==null)return;
        char[] password=askPassword("Password del backup",false);if(password==null)return;
        BackupService.Backup backup;
        try { backup=UiWork.run(owner,"Verifica integrità del backup…",()->BackupService.read(selected.toPath(),password)); }
        catch(Exception e){error.accept(e.getMessage());return;}finally{Arrays.fill(password,'\0');}
        try(backup) {
            Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Aggiungere "+backup.profiles().size()+" profili e le relative password salvate?\nI profili esistenti non saranno sovrascritti. Le chiavi private non sono incluse e le chiavi host dovranno essere verificate. Nessun tunnel sarà avviato.",ButtonType.YES,ButtonType.NO);
            confirm.initOwner(owner);if(confirm.showAndWait().orElse(ButtonType.NO)!=ButtonType.YES||!ensureOpen())return;
            List<TunnelProfile> snapshot=profiles.get();
            List<TunnelProfile> next=UiWork.run(owner,"Importazione del backup…",()->BackupService.append(backup,snapshot,profilesFile,vault));
            replaceRows.accept(next);
        }catch(Exception e){error.accept("Importazione non completata; i profili precedenti restano invariati.\n"+e.getMessage());}
    }
}
