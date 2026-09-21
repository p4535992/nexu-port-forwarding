package it.nexu.forwarding.ui;

import it.nexu.forwarding.config.StorageLocations;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import java.util.function.Consumer;

/** Preferences apply after an explicit restart; never copies profiles or secrets. */
public final class StorageSettingsDialog {
    private StorageSettingsDialog() { }
    public static void show(Window owner, StorageLocations.Selection storage, Consumer<String> error) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner); dialog.setTitle("Nexu Port Forwarding — Cartella dati");
        dialog.setHeaderText("Dove salvare profili, credenziali e impostazioni");
        TextArea current = new TextArea("Cartella dati attuale:\n" + storage.dataDirectory()
            + "\n\nCartella log attuale:\n" + storage.logsDirectory());
        current.setEditable(false); current.setWrapText(true); current.setPrefRowCount(5);
        RadioButton portable = new RadioButton("Portabile: data/ e logs/ accanto al programma");
        RadioButton appdata = new RadioButton("Cartella utente: nexu-port-forwarding/data (log in ../logs)");
        ToggleGroup group = new ToggleGroup(); portable.setToggleGroup(group); appdata.setToggleGroup(group);
        boolean configurable = storage.preferenceFile() != null && storage.mode() != StorageLocations.Mode.CUSTOM;
        try {
            StorageLocations.Mode preferred = configurable ? StorageLocations.readPreference(storage.preferenceFile()) : storage.mode();
            if (preferred == StorageLocations.Mode.PORTABLE) portable.setSelected(true); else appdata.setSelected(true);
        } catch (Exception e) { error.accept(e.getMessage()); return; }
        portable.setDisable(!configurable); appdata.setDisable(!configurable);
        Label note = new Label(configurable
            ? "La scelta vale dal prossimo avvio. Nessun profilo, password o log viene copiato, cancellato o spostato. "
                + "La nuova cartella può essere vuota oppure contenere profili già salvati. "
                + "Per trasferirli usa l'esportazione/importazione del backup cifrato."
            : storage.mode() == StorageLocations.Mode.CUSTOM
                ? "Il percorso è imposto da NEXU_PF_HOME o -Dnexu.home. Rimuovi l'impostazione esterna per usare la preferenza del portabile."
                : "Questa installazione usa la cartella utente. Per salvare accanto al programma usa il pacchetto portabile.");
        note.setWrapText(true); note.setMaxWidth(580);
        VBox content = new VBox(14, current, portable, appdata, note); content.setPrefWidth(600);
        dialog.getDialogPane().setContent(content);
        ButtonType save = new ButtonType("Salva per il prossimo avvio", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(save).setDisable(!configurable);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) return;
        try {
            StorageLocations.savePreference(storage, portable.isSelected()
                ? StorageLocations.Mode.PORTABLE : StorageLocations.Mode.APPDATA);
            Alert done = new Alert(Alert.AlertType.INFORMATION,
                "Preferenza salvata. Riavvia Nexu Port Forwarding per applicarla.\n"
                + "I tunnel e i dati della sessione attuale non sono stati modificati.", ButtonType.OK);
            done.initOwner(owner); done.setHeaderText("Riavvio necessario"); done.showAndWait();
        } catch (Exception e) { error.accept("Impossibile salvare la preferenza.\n" + e.getMessage()); }
    }
}
