# NexU Port Forwarding

[English](README.md) | [Italiano](README.it.md)

Gestore desktop di tunnel SSH TCP per **Windows e Linux**, sviluppato con Java 21, JavaFX e integrazione Dorkbox SystemTray. NexU è usato come riferimento per l'integrazione desktop; questa applicazione non include servizi di firma digitale né un server web locale.

## Funzioni

- Tabella dei tunnel ricercabile, ordinabile e scorrevole.
- Profili SSH con hostname/IP, porta SSH personalizzata, utente, password o autenticazione con chiave privata.
- Forwarding TCP **REMOTE (-R)** e **LOCAL (-L)**.
- Avvio e arresto indipendenti per ogni riga.
- Duplicazione, modifica, eliminazione, attività per tunnel ed esportazione del comando OpenSSH.
- Verifica esplicita delle chiavi host SSH.
- Keepalive e riconnessione opzionale con tentativi limitati.
- Decorazioni native Windows/Linux con minimizza, massimizza/ripristina e chiudi.
- Vault locale cifrato per le credenziali e import/export di backup cifrati.

Il verde indica che connessione SSH e forwarding sono stati stabiliti; **non** certifica lo stato di salute dell'applicazione di destinazione.

Ogni profilo possiede una connessione SSH indipendente. Nessun tunnel parte automaticamente all'apertura dell'applicazione. Gli errori di autenticazione, chiave host o bind non vengono ritentati automaticamente. Apache MINA SSHD viene usato direttamente, quindi le password non vengono passate tramite BAT, comandi PowerShell o argomenti di processi esterni.

## Release verificata corrente

La release verificata corrente è **[v0.2.1-rc.9](https://github.com/p4535992/nexu-port-forwarding/releases/tag/v0.2.1-rc.9)**.

| Piattaforma | Pacchetti |
| --- | --- |
| Windows x64 | ZIP portabile, installer EXE, installer MSI |
| Linux x64 | TAR.GZ portabile, DEB, RPM |
| Java | Archivio per piattaforma contenente JAR e directory `lib/` |

I pacchetti nativi includono Java 21. I pacchetti portabili devono essere estratti come directory completa.

Avvio:
- Windows: `NexuPortForwarding.exe`
- Linux: `bin/NexuPortForwarding`

La release non è firmata ed è pubblicata come prerelease. Windows SmartScreen o gli strumenti di gestione pacchetti Linux possono quindi mostrare un avviso di autore sconosciuto.

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

L'avvio normale usa una cartella dati indipendente dalla versione:

```text
Windows: %LOCALAPPDATA%\nexu-port-forwarding\
Linux:   ${XDG_DATA_HOME:-$HOME/.local/share}/nexu-port-forwarding/

profiles.properties      Profili dei tunnel, senza password
credentials.npfvault     Password/passphrase cifrate
host-keys.properties     Impronte host SSH accettate
window.properties        Posizione, dimensione e stato massimizzato
logs/nexu-0.log          Log diagnostico corrente con rotazione
app.lock                 Blocco della directory dati per singola istanza
```

I log ruotano su cinque file da circa 2 MB. Registrano ciclo di vita dell'applicazione e UUID/stato dei tunnel, non password, chiavi private, contenuti del traffico o messaggi SSH arbitrari.

Poiché i dati sono separati dall'installazione, gli aggiornamenti normali riutilizzano profili, vault, chiavi host, log e stato della finestra.

I launcher portabili `start-portable.bat` e `start-portable.sh` usano invece una directory `data/` accanto al launcher. Va conservata interamente quando si sostituisce una release portabile.

`NEXU_PF_HOME` o `-Dnexu.home` permettono di usare una directory dati personalizzata.

## Backup e migrazione

**Profili → Esporta configurazione** crea un file `.properties` privo di credenziali.

**Password → Esporta backup cifrato** crea un file `.npfbackup` protetto da password, contenente profili e credenziali esplicitamente salvate nel vault locale.

L'importazione cifrata aggiunge copie con nuovi ID; non sovrascrive i profili esistenti e non avvia tunnel.

I backup escludono volutamente file delle chiavi private, fiducia nelle chiavi host e log. Spostandosi su un altro computer, le chiavi private vanno copiate separatamente, i percorsi aggiornati e le impronte SSH verificate di nuovo.

## Esempio di remote forwarding

L'esempio integrato equivale a:

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

Packaging:

```powershell
./scripts/package-windows.ps1 -Version 0.2.1
```

```bash
bash scripts/package-linux.sh 0.2.1
```

Il packaging Windows richiede WiX 3.x. Quello Linux richiede gli strumenti DEB/RPM e le librerie desktop installate dalla pipeline di release.

## Verifica e documentazione

La pipeline di release compila e testa entrambi i sistemi operativi, genera i pacchetti nativi ed esegue una smoke test dell'applicazione JavaFX pacchettizzata. La release viene pubblicata solo dopo il successo di entrambi i job.

Documentazione:

- [Primo avvio](docs/GETTING-STARTED.it.md)
- [Evidenze di verifica](docs/VERIFICATION.it.md)
- [Pipeline di release](docs/RELEASE-PIPELINE.it.md)
- [Note di release](docs/RELEASE-NOTES.it.md)
- [Dati locali e log](docs/DATA-AND-LOGS.it.txt)
- [Indice della documentazione](docs/README.it.md)

Non implementati: SSH agent, ProxyJump, MFA/autenticazione keyboard-interactive, avvio automatico dei tunnel, aggiornamento automatico dell'applicazione o sincronizzazione cloud.
