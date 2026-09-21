# Nexu Port Forwarding — Importazione Tabby

[English](TABBY-IMPORT.md) | [Italiano](TABBY-IMPORT.it.md)

Questa funzione è inclusa nella **prerelease v1.1.0-rc.1**. I binari stabili 1.0.0 restano invariati. L'interfaccia grafica mantiene le etichette italiane.

## Tab Custom e Tabby

**Custom** contiene i profili esistenti e quelli creati con **+ Nuovo tunnel**. Le vecchie configurazioni vengono lette come Custom, con installazione inizialmente vuota.

**Tabby** contiene i profili importati con **Importa Tabby…**. Un profilo SSH di Tabby può generare più righe indipendenti: una per ogni inoltro locale, remoto o dinamico. Le righe importate sono modificabili localmente; le modifiche non vengono riscritte in Tabby. Duplicare una riga importata crea una copia Custom senza copiarne le credenziali.

Ricerca e **Avvia visibili** operano sul tab selezionato. Contatori, **Ferma tutti** e i comandi globali della tray riguardano entrambi i tab. L'importazione non avvia connessioni.

## Procedura di importazione

Premi **Importa Tabby…**, scegli `config.yaml`, inserisci eventualmente un nome **Installazione** e premi **Analizza file**. Controlla anteprima e riepilogo dei profili saltati, seleziona le righe e premi **Importa selezionati**.

I percorsi suggeriti sono `%APPDATA%\tabby\config.yaml` su Windows e `${XDG_CONFIG_HOME:-$HOME/.config}/tabby/config.yaml` su Linux. Se `TABBY_CONFIG_DIRECTORY` contiene un percorso assoluto, viene suggerito per primo. Un file Tabby personalizzato o portabile può essere selezionato con **Sfoglia…**. Il file non viene letto all'avvio dell'applicazione: la lettura avviene soltanto premendo il pulsante di analisi.

Il file viene soltanto letto. Non viene conservata una copia completa dello YAML. Campi password, vault cifrato Tabby, contenuti delle chiavi, fiducia host e script di accesso **non vengono importati**. Il file selezionato viene analizzato nella memoria del processo; non è garantita la cancellazione immediata delle stringhe gestite dalle librerie. Non pubblicare una configurazione reale per chiedere assistenza.

Prima di avviare una riga importata, inserisci le credenziali in Nexu Port Forwarding e verifica autonomamente l'impronta del server. La credenziale può essere salvata nel normale vault locale cifrato di Nexu Port Forwarding.

## Corrispondenza dei campi

| Campo Tabby | Campo Nexu Port Forwarding |
| --- | --- |
| `profiles[].name` e `description` dell'inoltro | Nome della riga |
| Nome del gruppo oppure nome impostato nell'importazione | **Installazione** modificabile e ricercabile |
| `options.host`, `options.port`, `options.user` | Host, porta e utente SSH |
| `options.forwardedPorts[].type` | `Local` → `LOCAL (-L)`; `Remote` → `REMOTE (-R)`; `Dynamic` → `DYNAMIC (-D)` |
| `host`, `port` dell'inoltro | Indirizzo e porta di ascolto |
| `targetAddress`, `targetPort` | Destinazione fissa solo per Local/Remote |
| `options.privateKeys[0]` | Primo percorso di chiave locale esplicito, non il contenuto |
| `keepaliveInterval`, `keepaliveCountMax`, `readyTimeout` | Keepalive e timeout convertiti e limitati; leggere gli avvisi |

Vengono applicati, nell'ordine, `profileDefaults.ssh.options`, `defaults.ssh.options` del gruppo diretto e `options` del profilo. Una lista esplicita di inoltri sostituisce quella ereditata; una lista vuota la disabilita. I default dei gruppi superiori non vengono ereditati ricorsivamente. I profili integrati caricati esternamente da Tabby, gli include della configurazione SSH e i template non vengono importati.

L'autenticazione nulla/automatica viene convertita in chiave esplicita, quando presente, oppure password e richiede revisione. Profili con agent o keyboard-interactive/MFA vengono saltati con una spiegazione. Lo stesso vale per profili con jump host o proxy di connessione: non diventano mai collegamenti diretti alternativi. URL di provider di chiavi, riferimenti al vault, percorsi UNC/di rete, percorsi relativi e variabili non risolte vengono rifiutati. `~/`, `%h` e `%r` nei percorsi espliciti vengono espansi. La presenza di più chiavi genera un avviso: viene scelto solo il primo percorso.

## Forwarding dinamico SOCKS

L'inoltro dinamico crea un **proxy SOCKS TCP locale** tramite l'API di forwarding dinamico del backend SSH. Non ha una destinazione fissa: il client SOCKS sceglie host e porta che verranno raggiunti dal server SSH. Il comando OpenSSH esportato usa `-D`, non `-L` o `-R`. Sono inclusi test TCP SOCKS4 e SOCKS5 nella suite d'integrazione; l'applicazione non implementa inoltro UDP.

Il proxy SOCKS locale non ha un'autenticazione password separata. Mantieni il bind su `127.0.0.1` o `::1`. Le righe non loopback richiedono selezione esplicita nell'anteprima; prima dell'avvio rimane la conferma sull'esposizione alla rete. L'aggiunta di SOCKS non allenta il rifiuto dei canali non richiesti avviati dal server.

## Nome installazione e importazioni ripetute

La colonna **INSTALLAZIONE** è modificabile facendo doppio clic sulla cella quando il tunnel è fermo, oppure tramite **Modifica… → Connessione → Installazione**. È ricercabile, ordinabile e salvata nel profilo. Durante l'importazione, un nome inserito viene applicato alle righe selezionate; lasciandolo vuoto si usa il nome del gruppo diretto Tabby, se presente.

Ogni riga importata ha una chiave locale SHA-256 derivata dall'identità del profilo Tabby e dalle impostazioni SSH/forwarding. Reimportare la stessa riga non crea duplicati, anche dopo aver spostato il file, modificato il nome installazione o riordinato gli inoltri. Credenziali, etichette e righe modificate non vengono sovrascritte. Impostazioni di connessione o inoltro cambiate creano una **nuova** riga; quelle precedenti non vengono eliminate. Se manca l'ID Tabby, il nome del profilo sorgente partecipa all'identità. È un'importazione, non una sincronizzazione.

L'anteprima segnala possibili conflitti sulle porte. Prima di avviare una riga, ferma l'eventuale tunnel Tabby/altro programma che usa la stessa porta, oppure cambia porta. Le due applicazioni non condividono le sessioni SSH.

## Salvataggio e limiti di sicurezza

Il formato configurazione **2** aggiunge origine, installazione e identità di importazione. Il formato 1 resta leggibile e viene assegnato a Custom. I backup cifrati mantengono i nuovi campi e DYNAMIC; il ripristino continua a creare nuovi UUID. Le scritture verificano prima sia il limite di 1.000 righe sia quello di 2 MB serializzati.

**Crea un backup prima di provare la modifica. I binari precedenti non leggono il formato 2.** Per tornare indietro occorre ripristinare una configurazione/backup precedente, non aprire i nuovi file con il vecchio eseguibile. Non viene effettuata alcuna migrazione o cancellazione automatica fuori dalla directory dati selezionata di Nexu Port Forwarding.

Lo YAML è limitato a 4 MB, 40 livelli e 100.000 nodi valore convertiti. Un'importazione è limitata a 1.000 profili SSH e 1.000 definizioni di inoltro. Il lettore usa la composizione di SnakeYAML seguita da conversione in dati semplici, non costruzione di oggetti Java arbitrari. Chiavi duplicate, tag personalizzati, alias, merge YAML e documenti multipli vengono rifiutati. Gli errori non mostrano estratti dello YAML. Le righe non valide/non supportate vengono segnalate; quelle valide restano selezionabili.

## Stato della verifica

I test di conversione e salvataggio senza dipendenze vengono eseguiti con `bash scripts/test-core-offline.sh`. `TabbyYamlReaderTest` verifica il lettore SnakeYAML reale. `MinaIntegrationTest` include tre nuovi test SOCKS con fixture SSH/echo solo loopback, comprese destinazioni scelte dal client e chiusura del listener. I test con dipendenze vanno eseguiti con `mvn clean verify` prima del packaging o della pubblicazione. Il rapporto locale allegato distingue ciò che è stato realmente eseguito. Durante lo sviluppo non è stata fornita o aperta alcuna configurazione Tabby reale dell'utente.

## Riferimenti al formato

L'importatore è stato scritto indipendentemente sul formato pubblico Tabby, consultato il 20 settembre 2026. Non viene incluso codice dell'applicazione Tabby.

- [Opzioni SSH e campi degli inoltri](https://github.com/Eugeny/tabby/blob/master/tabby-ssh/src/api/interfaces.ts)
- [Default SSH integrati](https://github.com/Eugeny/tabby/blob/master/tabby-ssh/src/profiles.ts)
- [Default di profilo e gruppo diretto](https://github.com/Eugeny/tabby/blob/master/tabby-core/src/services/profiles.service.ts)
- [Nome del file configurazione](https://github.com/Eugeny/tabby/blob/master/app/lib/config.ts)
- [API Apache MINA per il forwarding dinamico](https://github.com/apache/mina-sshd/blob/sshd-2.19.0/sshd-core/src/main/java/org/apache/sshd/common/forward/PortForwardingManager.java)
- [Esempio YAML sintetico](../src/test/resources/tabby/config.example.yaml)
