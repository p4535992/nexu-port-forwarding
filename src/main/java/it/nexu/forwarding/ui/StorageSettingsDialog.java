package it.nexu.forwarding.ui;

import it.nexu.forwarding.config.StorageLocations;
import it.nexu.forwarding.i18n.I18n;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import java.util.function.Consumer;

/** Preferences apply after an explicit restart; never copies profiles or secrets. */
public final class StorageSettingsDialog {
    private StorageSettingsDialog() { }
    public static void show(Window owner, StorageLocations.Selection storage, Consumer<String> error) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner); dialog.setTitle(I18n.t("Nexu Port Forwarding — Data folder","Nexu Port Forwarding — Cartella dati"));
        dialog.setHeaderText(I18n.t("Where to store profiles, credentials and settings","Dove salvare profili, credenziali e impostazioni"));
        TextArea current = new TextArea(I18n.t("Current data folder:\n","Cartella dati attuale:\n") + storage.dataDirectory()
            + I18n.t("\n\nCurrent log folder:\n","\n\nCartella log attuale:\n") + storage.logsDirectory());
        current.setEditable(false); current.setWrapText(true); current.setPrefRowCount(5);
        RadioButton portable = new RadioButton(I18n.t("Portable: data/ and logs/ next to the application","Portabile: data/ e logs/ accanto al programma"));
        RadioButton appdata = new RadioButton(I18n.t("User folder: nexu-port-forwarding/data (logs in ../logs)","Cartella utente: nexu-port-forwarding/data (log in ../logs)"));
        ToggleGroup group = new ToggleGroup(); portable.setToggleGroup(group); appdata.setToggleGroup(group);
        boolean configurable = storage.preferenceFile() != null && storage.mode() != StorageLocations.Mode.CUSTOM;
        try {
            StorageLocations.Mode preferred = configurable ? StorageLocations.readPreference(storage.preferenceFile()) : storage.mode();
            if (preferred == StorageLocations.Mode.PORTABLE) portable.setSelected(true); else appdata.setSelected(true);
        } catch (Exception e) { error.accept(e.getMessage()); return; }
        portable.setDisable(!configurable); appdata.setDisable(!configurable);
        Label note = new Label(configurable
            ? I18n.t("The choice applies on the next start. No profile, password or log is copied, deleted or moved. The new folder may be empty or already contain saved profiles. Use encrypted backup export/import to transfer them.","La scelta vale dal prossimo avvio. Nessun profilo, password o log viene copiato, cancellato o spostato. La nuova cartella può essere vuota oppure contenere profili già salvati. Per trasferirli usa l'esportazione/importazione del backup cifrato.")
            : storage.mode() == StorageLocations.Mode.CUSTOM
                ? I18n.t("The path is forced by NEXU_PF_HOME or -Dnexu.home. Remove the external setting to use the portable preference.","Il percorso è imposto da NEXU_PF_HOME o -Dnexu.home. Rimuovi l'impostazione esterna per usare la preferenza del portabile.")
                : I18n.t("This installation uses the user data folder. To store data next to the application, use the portable package.","Questa installazione usa la cartella utente. Per salvare accanto al programma usa il pacchetto portabile."));
        note.setWrapText(true); note.setMaxWidth(580);
        VBox content = new VBox(14, current, portable, appdata, note); content.setPrefWidth(600);
        dialog.getDialogPane().setContent(content);
        ButtonType save = new ButtonType(I18n.t("Save for next start","Salva per il prossimo avvio"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(save).setDisable(!configurable);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) return;
        try {
            StorageLocations.savePreference(storage, portable.isSelected()
                ? StorageLocations.Mode.PORTABLE : StorageLocations.Mode.APPDATA);
            Alert done = new Alert(Alert.AlertType.INFORMATION,
                I18n.t("Preference saved. Restart Nexu Port Forwarding to apply it.\nThe tunnels and data in the current session were not changed.","Preferenza salvata. Riavvia Nexu Port Forwarding per applicarla.\nI tunnel e i dati della sessione attuale non sono stati modificati."), ButtonType.OK);
            done.initOwner(owner); done.setHeaderText(I18n.t("Restart required","Riavvio necessario")); done.showAndWait();
        } catch (Exception e) { error.accept("Impossibile salvare la preferenza.\n" + e.getMessage()); }
    }
}
