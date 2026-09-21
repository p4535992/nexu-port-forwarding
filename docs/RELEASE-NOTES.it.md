# Nexu Port Forwarding 1.2.0

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## Italiano

Release stabile 1.2.0 con trasporto proxy SOCKS5/HTTP CONNECT configurabile, diagnostica di confronto OpenSSH, tabella adattiva, colorazione più chiara degli stati e selezione persistente della lingua Inglese/Italiano con inglese predefinito.

### Principali novità della 1.2.0

- Aggiunto **Settings / Impostazioni → Language / Lingua → English / Italiano**, con **English come default** quando non esiste ancora una preferenza.
- La lingua scelta viene salvata nella cartella dati locale e applicata al successivo avvio dell'applicazione.
- Finestra principale, filtri, intestazioni della tabella, stati dei tunnel, dialoghi profilo/proxy, password/vault, cartella dati e menu tray seguono ora la lingua selezionata.
- Il cambio lingua richiede intenzionalmente il riavvio, così la UI non viene ricostruita mentre potrebbero essere attivi tunnel SSH.

- Le righe dei tunnel **ACTIVE** hanno ora un vero sfondo verde, anche quando sono selezionate; le righe ferme restano rosse e l'azione contestuale **■ Ferma** resta rossa.
- Rimossa la colonna ridondante **STATO** dalla tabella; il filtro Stato resta disponibile, mentre colore della riga e pulsante **Avvia/Ferma** comunicano direttamente lo stato.
- Corretto il wrapping di **NOME** / **INSTALLAZIONE**: la riga ricalcola davvero l'altezza necessaria e non taglia più il testo multilinea sopra o sotto.
- **FORWARDING** e **HOSTNAME / INDIRIZZO IP** vanno a capo con altezza dinamica e larghezze più adattive, riducendo la pressione orizzontale sugli schermi più piccoli.

- A ogni **Avvia** vengono ora scritti nel pannello attività del tunnel i comandi OpenSSH equivalenti e copiabili per **Windows PowerShell** e **Linux/POSIX**.
- I comandi diagnostici usano esplicitamente **`-F NUL`** su Windows e **`-F /dev/null`** su Linux, così non leggono la configurazione OpenSSH dell'utente e riproducono più fedelmente il comportamento autosufficiente di Nexu.
- Il log chiarisce che Nexu usa **Apache MINA SSHD** e non esegue realmente `ssh.exe`: il comando stampato serve per confrontare parametri SSH e forwarding.
- Se il profilo Nexu usa un proxy SOCKS5/HTTP CONNECT, il pannello registra separatamente quel trasporto perché è implementato internamente e non compare come `ProxyCommand` esterno.
- Inclusa la diagnostica opzionale **configurazione OpenSSH locale** aggiunta dopo rc.1: confronta `ssh -G` con `ssh -F NUL/-F /dev/null -G` e segnala differenze in ProxyJump, ProxyCommand, IdentityFile, HostName, User e Port.

- Aggiunto il tipo di collegamento per profilo: **Diretto**, **SOCKS5** oppure **HTTP CONNECT**.
- Host, porta e utente proxy opzionale vengono salvati nel profilo; la password proxy viene richiesta all'avvio del tunnel e resta soltanto in memoria.
- I file profilo v1/v2 restano compatibili e diventano automaticamente **Diretto**; il nuovo formato profilo è v3.
- Aggiunte categorie diagnostiche specifiche per DNS del proxy, timeout, autenticazione, SOCKS5 e HTTP CONNECT.
- `FORWARDING_REJECTED` registra ora anche il forwarding concreto richiesto e indica le verifiche lato server SSH. Per un forwarding remoto vengono citati esplicitamente `AllowTcpForwarding`, il `PermitListen` necessario per il listener richiesto, `GatewayPorts` quando si richiede un bind non-loopback ed eventuali restrizioni `Match`/per utente.
- La 1.2.0 porta in stabile il trasporto proxy, la diagnostica OpenSSH, i miglioramenti adattivi della tabella e l'interfaccia bilingue validati durante il ciclo delle release candidate.

### Sicurezza

Le password proxy non vengono scritte nei file profilo né esportate come configurazione in chiaro. Restano solo nella sessione e vengono eliminate con il comando “Blocca e dimentica segreti in memoria” o alla chiusura dell'applicazione.

La release non è firmata: verificare proxy e policy del server SSH nel proprio ambiente prima dell'uso in produzione.
