# Nexu Port Forwarding

[English](README.md) | [Italiano](README.it.md)

Gestore desktop di tunnel SSH TCP per **Windows e Linux**, sviluppato con Java 21, JavaFX e integrazione Dorkbox SystemTray. Nexu Port Forwarding è un gestore di tunnel indipendente; non include servizi di firma digitale né un server web locale.

L'inglese è la lingua primaria della documentazione; le traduzioni italiane vengono mantenute in parallelo. L'interfaccia grafica attuale usa etichette italiane.

## Sorgenti importati: Tabby e MobaXterm

Le viste principali sono **Attivi → Custom → Tabby → MobaXterm**. **Attivi** contiene soltanto i tunnel nello stato ACTIVE. I profili Custom vengono creati manualmente; Tabby e MobaXterm sono sorgenti di importazione. Dopo l’import le righe sono salvate localmente da Nexu Port Forwarding e non dipendono più in tempo reale dalle applicazioni esterne.

**Importa Tabby…** legge un `config.yaml`; **Importa MobaXterm…** legge soltanto `[PortForwarding]` dal `MobaXterm.ini` selezionato. Entrambi mostrano un’anteprima e importano i forwarding Local, Remote e Dynamic/SOCKS supportati senza avviarli. La colonna **INSTALLAZIONE** è modificabile/ricercabile e resta nei backup cifrati. Le reimportazioni aggiungono nuove definizioni e ignorano i fingerprint identici senza sovrascrivere modifiche locali o credenziali.

Consulta [importazione Tabby](docs/TABBY-IMPORT.it.md) e [importazione MobaXterm](docs/MOBAXTERM-IMPORT.it.md).

## Funzioni

- Tabella dei tunnel ricercabile, ordinabile e scorrevole.
- Profili SSH con hostname/IP, porta SSH personalizzata, utente, password o autenticazione con chiave privata.
- Forwarding TCP **REMOTE (-R)**, **LOCAL (-L)** e **DYNAMIC (-D / SOCKS)**.
- Avvio e arresto indipendenti per ogni riga.
- Duplicazione, modifica, eliminazione, attività per tunnel ed esportazione del comando OpenSSH.
- Verifica esplicita delle chiavi host SSH.
- Keepalive e riconnessione opzionale con tentativi limitati.
- Decorazioni native Windows/Linux con minimizza, massimizza/ripristina e chiudi.
- Vault locale cifrato per le credenziali e import/export di backup cifrati.

Il verde indica che connessione SSH e forwarding sono stati stabiliti; **non** certifica lo stato di salute dell'applicazione di destinazione.

Ogni profilo possiede una connessione SSH indipendente. Nessun tunnel parte automaticamente all'apertura dell'applicazione. Gli errori di autenticazione, chiave host o bind non vengono ritentati automaticamente. Apache MINA SSHD viene usato direttamente, quindi le password non vengono passate tramite BAT, comandi PowerShell o argomenti di processi esterni.

## Release 1.1.0-rc.4

Scarica **[v1.1.0-rc.4](https://github.com/p4535992/nexu-port-forwarding/releases/tag/v1.1.0-rc.4)**.

| Piattaforma | Pacchetti |
| --- | --- |
| Windows x64 | ZIP portabile, installer EXE, installer MSI |
| Linux x64 | TAR.GZ portabile, DEB, RPM |
| Java | Archivio per piattaforma contenente JAR e directory `lib/` |

I pacchetti nativi includono Java 21. I pacchetti portabili devono essere estratti come directory completa.

Avvio:
- Windows: `NexuPortForwarding.exe`
- Linux: `bin/NexuPortForwarding`

Il tag è pubblicato come prerelease, ma i binari non sono firmati digitalmente. Windows SmartScreen o gli strumenti di gestione pacchetti Linux possono quindi mostrare un avviso di autore sconosciuto.

## Diagnostica della connessione SSH

Gli errori distinguono risoluzione DNS, timeout TCP, connessione rifiutata, assenza di route, handshake SSH interrotto, verifica della chiave host e autenticazione. Nei timeout Nexu Port Forwarding specifica che apre una **connessione TCP SSH diretta** e non riutilizza automaticamente il proxy HTTP/SOCKS del sistema operativo. Se la rete aziendale richiede HTTP CONNECT/SOCKS o una VPN serve quindi un percorso compatibile esplicito.

I campi password/passphrase includono un pulsante a forma di occhio per mostrare o nascondere temporaneamente il valore digitato. La colonna **AZIONI** è la prima a sinistra.


La griglia dei tunnel inizia con **AZIONI**. **NOME** è modificabile direttamente nella riga: premendo Invio o uscendo dal campo il profilo viene salvato automaticamente. Tipo, ascolto e destinazione sono riuniti nella colonna **FORWARDING**, ad esempio `R · SSH[127.0.0.1:8687] → PC → maven.example.com:8080`. Per Local compare `L · PC[...] → SSH → ...`; per Dynamic `D · PC[...] → SOCKS → SSH`.

Nel menu della riga ci sono esportazioni distinte **Windows PowerShell**, **Windows CMD** e **Linux/POSIX**. Il comando CMD non contiene l'operatore PowerShell `&` né argomenti tra apici singoli.

## Comportamento della finestra

La finestra principale usa le decorazioni native fornite da Windows o dal window manager Linux.

- **—** minimizza normalmente.
- **massimizza/ripristina** è gestito dal sistema operativo.
- **X** chiede se ridurre l'applicazione oppure uscire.
- Con system tray disponibile, la riduzione dal dialogo di chiusura nasconde l'app nell'area di notifica.
- Senza tray, la stessa scelta esegue una normale minimizzazione.
- Se ci sono tunnel attivi, il dialogo specifica chiaramente che l'uscita li fermerà.

Posizione, dimensione e stato massimizzato vengono salvati in `window.properties`. Se cambiano monitor, scaling o risoluzione e la posizione salvata non è più visibile, l'applicazione riporta automaticamente la finestra dentro un'area disponibile.

## Password e passphrase locali

Il menu **Password** permette di creare/sbloccare il vault locale, bloccarlo, cambiare la password principale e importare/esportare backup cifrati.

Le password SSH e le passphrase delle chiavi salvate vengono conservate localmente in:

`credentials.npfvault`

Il vault usa AES-256-GCM e PBKDF2-HMAC-SHA256 con 600.000 iterazioni, salt casuale e nonce nuovo. La password principale deve contenere almeno 12 caratteri e **non viene mai salvata**. Se viene persa, non è recuperabile.

Le credenziali sono legate all'ID del profilo e all'identità SSH. Cambiare server, porta, utente o metodo di autenticazione non riutilizza silenziosamente una credenziale precedente. Duplicare un profilo non duplica implicitamente il segreto.

La cifratura a riposo non protegge un processo già sbloccato da malware o amministratori con accesso al processo.

## Dati locali, log e aggiornamenti

**Archivi portabili nativi (ZIP / TAR.GZ):** l'avvio normale, incluso il doppio clic sull'eseguibile Windows, crea `data/` e `logs/` accanto al programma. I profili non vengono salvati come file sparsi accanto all'eseguibile.

```text
NexuPortForwarding/
  NexuPortForwarding.exe           Windows; su Linux: bin/NexuPortForwarding
  portable.properties              Preferenza di salvataggio, senza credenziali
  data/
    profiles.properties            Profili, senza password
    credentials.npfvault           Password/passphrase cifrate
    host-keys.properties           Impronte host SSH accettate
    window.properties              Stato della finestra
    app.lock                       Blocco dati per singola istanza
  logs/
    nexu-0.log                     Log diagnostico con rotazione
```

Le due cartelle vengono create all'avvio. I singoli file vengono creati quando servono (per esempio il vault dopo aver scelto la password principale). Un portabile nuovo non legge né importa automaticamente i profili presenti in AppData.

**Impostazioni → Cartella dati…** permette di scegliere il salvataggio locale portabile oppure la cartella dati dell'utente. La preferenza è in `portable.properties`, si applica al riavvio e non copia, elimina o sposta profili, credenziali o log. La cartella scelta può contenere profili già salvati. Per un trasferimento esplicito usa l'esportazione/importazione del backup cifrato.

**Installer e archivi solo Java** usano per default una cartella padre dell’utente con `data/` e `logs/` separate:

```text
Windows:
  %LOCALAPPDATA%\nexu-port-forwarding\data\
  %LOCALAPPDATA%\nexu-port-forwarding\logs\
Linux:
  ${XDG_DATA_HOME:-$HOME/.local/share}/nexu-port-forwarding/data/
  ${XDG_DATA_HOME:-$HOME/.local/share}/nexu-port-forwarding/logs/
```

Aggiornando dalla 1.0.0, i file legacy riconosciuti direttamente nella cartella padre vengono copiati in `data/` solo se la destinazione non esiste; gli originali restano intatti. Un override esplicito `-Dnexu.home` o `NEXU_PF_HOME` continua a usare il proprio percorso esplicito. Il dialogo mostra i percorsi dati e log effettivamente in uso.

Launcher portabili e avvio diretto rispettano la stessa preferenza. Aggiornando il portabile, conserva `data/`, `logs/` e il tuo `portable.properties`. Non unire alla cieca dati esistenti e pacchetti nuovi. I vecchi log in `data/logs/` non vengono modificati; i nuovi log portabili vanno nella cartella `logs/` adiacente.

I log ruotano su cinque file da circa 2 MB e non registrano password, chiavi private o traffico. Il salvataggio portabile richiede una cartella locale scrivibile e controllata dal proprietario. Gli errori vengono segnalati senza passare silenziosamente ad AppData. Dettagli in [salvataggio portabile](docs/PORTABLE-STORAGE.it.md).

## Backup e migrazione

**Profili → Esporta configurazione** crea un file `.properties` privo di credenziali.

**Password → Esporta backup cifrato** crea un file `.npfbackup` protetto da password, contenente profili e credenziali esplicitamente salvate nel vault locale.

L'importazione cifrata aggiunge copie con nuovi ID; non sovrascrive i profili esistenti e non avvia tunnel.

I backup escludono volutamente file delle chiavi private, fiducia nelle chiavi host e log. Spostandosi su un altro computer, le chiavi private vanno copiate separatamente, i percorsi aggiornati e le impronte SSH verificate di nuovo.

## Esempio di remote forwarding

L'esempio integrato usa indirizzi riservati alla documentazione. Sostituiscili con server e destinazione reali prima di collegarti:

```powershell
ssh.exe -N -T -p 22 -o ExitOnForwardFailure=yes -o ServerAliveInterval=15 -o ServerAliveCountMax=3 -R 127.0.0.1:8989:maven.example.com:8081 utente@203.0.113.10
```

Con `-R`, `127.0.0.1:8989` ascolta sul server SSH e la destinazione `maven.example.com:8081` viene raggiunta dal computer locale attraverso il tunnel. Con `-L` i lati si invertono.

Il bind remoto effettivo dipende anche dalle policy del server SSH, ad esempio `GatewayPorts`.

## Sviluppo

Requisiti:
- JDK 21
- Maven 3.9+

```text
mvn --batch-mode clean verify
java -jar target/app/nexu-port-forwarding.jar
```

Sono inclusi `build.bat`, `run.bat`, `build.sh` e `run.sh`.

Il packaging richiede anche Python 3.10+ per preparare documentazione offline e inventario:

```bash
python scripts/prepare-distribution.py
```

Poi crea i pacchetti sul sistema operativo di destinazione:

```powershell
./scripts/package-windows.ps1 -Version 1.1.0
```

```bash
bash scripts/package-linux.sh 1.1.0
```

Il packaging Windows richiede WiX 3.x. Quello Linux richiede gli strumenti DEB/RPM e le librerie desktop installate dalla pipeline di release.

## Verifica e documentazione

I controlli di release verificano anche cronologia Git raggiungibile e JAR applicativo per i due indirizzi ritirati, senza salvarne i valori nelle regole. È un controllo mirato, non un audit di sicurezza completo. L’inventario esatto dei JAR runtime è incluso come `dependency-inventory.json`.

La pipeline di release compila e testa entrambi i sistemi operativi, genera i pacchetti nativi ed esegue una smoke test dell'applicazione JavaFX pacchettizzata. La release viene pubblicata solo dopo il successo di entrambi i job.

Documentazione:

- [Primo avvio](docs/GETTING-STARTED.it.md)
- [Evidenze di verifica](docs/VERIFICATION.it.md)
- [Pipeline di release](docs/RELEASE-PIPELINE.it.md)
- [Note di release](docs/RELEASE-NOTES.it.md)
- [Dati locali e log](docs/DATA-AND-LOGS.it.txt)
- [Indice della documentazione](docs/README.it.md)

Non implementati: SSH agent, ProxyJump, MFA/autenticazione keyboard-interactive, avvio automatico dei tunnel, aggiornamento automatico dell'applicazione o sincronizzazione cloud.

## Licenza

Codice applicativo e documentazione di Nexu Port Forwarding sono distribuiti con [licenza MIT](LICENSE). Dipendenze e runtime Java incluso mantengono le proprie licenze; consulta [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). I pacchetti nativi includono la licenza MIT e gli avvisi di terze parti.
