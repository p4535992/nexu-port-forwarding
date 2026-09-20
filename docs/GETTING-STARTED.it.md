# Primo avvio e aggiornamenti

[English](GETTING-STARTED.md) | [Italiano](GETTING-STARTED.it.md)

Scarica [v1.0.0](https://github.com/p4535992/nexu-port-forwarding/releases/tag/v1.0.0). È una release stabile con binari non firmati per Windows x64 e Linux x64; i pacchetti nativi includono Java.

## Windows

Scarica `nexu-port-forwarding-1.0.0-windows-x64.zip`, estrai l'intera cartella e apri `NexuPortForwarding.exe`. Per un'installazione normale sono disponibili anche EXE e MSI nella stessa release. L'avvio normale usa `%LOCALAPPDATA%\nexu-port-forwarding` per dati e log, separatamente dall'installazione.

La finestra parte in modalità desktop normale, entro l'area visibile dello schermo. I pulsanti nativi **—**, **massimizza/ripristina** e **X** sono gestiti da Windows. La X chiede se ridurre nell'area di notifica oppure uscire; se ci sono tunnel attivi avverte che l'uscita li fermerà.

## Linux

Scarica `nexu-port-forwarding-1.0.0-linux-x64.tar.gz`, estrailo e avvia `NexuPortForwarding/bin/NexuPortForwarding`. Sono disponibili anche DEB e RPM. L'avvio normale usa `${XDG_DATA_HOME:-$HOME/.local/share}/nexu-port-forwarding`.

La finestra usa le decorazioni native del window manager. Quando la tray è disponibile, la X può ridurre l'applicazione nell'area di notifica; quando la tray non è disponibile, la stessa scelta esegue una normale minimizzazione.

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

Con l'avvio normale puoi sostituire/aggiornare i binari mantenendo la stessa cartella dati: non è necessario esportare e reimportare ogni volta. Profili, vault cifrato, chiavi host, log e stato finestra restano separati dall'installazione. Conserva comunque un backup prima dell'aggiornamento.

Per usare una cartella dati accanto all'applicativo avvia esplicitamente `start-portable.bat` o `start-portable.sh`, presenti negli archivi portabili. Questa modalità usa `data/`, incluso `data/logs/`, accanto al launcher. Mantieni l'intera cartella `data/` quando passi a una nuova release portabile, oppure ripristina un backup cifrato.
