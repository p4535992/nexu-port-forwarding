# Primo avvio e aggiornamenti

[English](GETTING-STARTED.md) | [Italiano](GETTING-STARTED.it.md)

Questa guida riguarda la **prerelease v1.1.0-rc.1**. La release stabile 1.0.0 resta disponibile e invariata.

## Windows

Nei portabili generati da questa copia sorgente, estrai l'intera cartella e apri `NexuPortForwarding.exe`: `data/` e `logs/` vengono create accanto al programma. Il BAT è opzionale e rispetta la stessa preferenza. Gli installer EXE/MSI usano `%LOCALAPPDATA%\nexu-port-forwarding\data\` per profili/impostazioni e la cartella sorella `logs\` per i log.

## Linux

Nel TAR.GZ portabile generato da questi sorgenti, avvia `NexuPortForwarding/bin/NexuPortForwarding` oppure `start-portable.sh`: i dati sono in `NexuPortForwarding/data/` e i log in `NexuPortForwarding/logs/`, non dentro `bin/`. DEB/RPM usano `${XDG_DATA_HOME:-$HOME/.local/share}/nexu-port-forwarding/data/` e la cartella sorella `logs/`.

## Scelta della cartella dati

Apri **Impostazioni → Cartella dati…**, scegli **Portabile** o **Cartella utente** e riavvia. Nessun dato viene spostato automaticamente. Il dialogo mostra i percorsi effettivi; i percorsi esterni `NEXU_PF_HOME` / `-Dnexu.home` hanno precedenza. La preferenza può anche essere modificata in `portable.properties`, con `storage=portable` oppure `storage=appdata` e `version=1`.

## Viste principali e importazione

L'ordine dei tab è **Attivi → Custom → Tabby → MobaXterm**. **Attivi** mostra soltanto i tunnel nello stato ACTIVE. Tabby e MobaXterm sono esclusivamente sorgenti di importazione: dopo aver premuto il relativo pulsante, gli inoltri selezionati vengono copiati nel `profiles.properties` locale di Nexu Port Forwarding; non resta una dipendenza live dall'app esterna e nessun tunnel viene avviato automaticamente.

La barra filtri combina testo libero, tipologia di forwarding (tutti / LOCAL / REMOTE / DYNAMIC), IP risolto del server SSH e stato. `HOSTNAME` mostra l'host SSH configurato e `INDIRIZZO IP` viene risolto in background. Se l'host configurato è già un IP, lo stesso valore appare immediatamente.

## Finestra e monitor

L'applicazione salva in `window.properties` dimensione e posizione della finestra normale e lo stato massimizzato. Al riavvio ripristina questi valori. Se nel frattempo cambia monitor, risoluzione o scaling e la posizione non è più valida, la finestra viene riportata automaticamente dentro un'area visibile.

L’esempio integrato usa soltanto endpoint di documentazione: inserisci gli indirizzi del tuo ambiente prima di collegarti.

## Crea il primo profilo

Apri **Password → Crea / sblocca archivio** e scegli una password principale di almeno 12 caratteri. Non viene salvata e non è recuperabile in caso di smarrimento.

Apri **+ Nuovo tunnel**, inserisci server SSH, porta, utente, autenticazione e parametri dell'inoltro. Con REMOTE (-R) la porta di ascolto si trova sul server SSH e la destinazione viene raggiunta dal PC locale. Con LOCAL (-L) avviene il contrario.

Lascia selezionato **Salva la credenziale nell'archivio locale cifrato** per conservare password o passphrase sul computer. Avvia il profilo dalla sua riga e confronta l'impronta del server con quella comunicata dall'amministratore prima di autorizzarla. I profili non partono automaticamente all'apertura dell'applicazione.

Dal menu **Profili** puoi aggiungere l'esempio Maven già configurato, ma ancora disattivato. Il verde indica SSH e forwarding stabiliti, non un controllo di salute del servizio Maven.

## Log e backup

**Apri log** apre la cartella locale `logs/`. I file registrano avvio, arresto, UUID e stati dei tunnel; i dettagli degli ultimi eventi sono nel pannello del tunnel selezionato.

**Password → Esporta backup cifrato** salva profili e credenziali già memorizzate in un `.npfbackup` protetto da una password scelta per il backup. Può essere diversa dalla password principale. **Importa backup cifrato** aggiunge nuove copie dei profili senza sovrascrivere quelli esistenti e non avvia collegamenti. Ferma i tunnel prima di importare.

Le chiavi private, le impronte host e i log non sono inclusi nel backup. Trasferisci separatamente i file delle chiavi quando cambi PC e verifica nuovamente le impronte host. L'export dal menu **Profili** resta invece una configurazione senza password.

## Nuove release

Conserva `data/`, `logs/` e `portable.properties` quando aggiorni il portabile. I profili in AppData restano separati e non vengono importati automaticamente. Per trasferimenti usa il backup cifrato. I vecchi log in `data/logs/` restano dove sono; i nuovi vengono scritti in `logs/`.
