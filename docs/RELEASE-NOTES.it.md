# Nexu Port Forwarding 1.1.0-rc.10

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## Italiano

Nuova build di test della linea 1.1.0 release-candidate, con le stesse funzionalità applicative della rc.6 e un nuovo set di pacchetti Windows/Linux compilato e verificato indipendentemente.

### Novità

- I controlli **Avvia/Ferma** della riga sono ora un solo pulsante compatto e contestuale; premendolo la riga viene selezionata automaticamente e vengono mostrati i suoi log.
- L'area attività/log è ora un **pannello verticale a destra** della griglia.
- **PORTA SSH** è incorporata nella stringa compatta **FORWARDING** (per esempio `R · SSH:22 · SERVER[...] → PC → destinazione`).
- **HOSTNAME** e **INDIRIZZO IP** risolto sono unificati nella colonna **HOSTNAME / INDIRIZZO IP**; se il profilo usa già un IP viene mostrato solo l'IP.

- Corretto un errore di avvio SSH nel runtime pacchettizzato che compariva come `ClassNotFoundException` prima ancora di tentare la rete.
- Apache MINA SSHD usa ora esplicitamente il backend I/O NIO2 invece di affidarsi alla scoperta dinamica dei provider dentro le immagini jpackage.
- Aggiunto uno **smoke test SSH sul binario pacchettizzato** sia su Windows sia su Linux: la prerelease non viene pubblicata se l'eseguibile nativo non riesce a inizializzare lo stack SSH.
- Le classi runtime mancanti vengono ora segnalate come errore dell'applicazione/runtime, non come falso problema DNS/firewall/proxy.

- Corretto il feedback di validazione del dialogo vault/password principale dopo l'aggiunta dell'occhio: gli input non validi mostrano ora un errore specifico e persistente con focus sul campo da correggere; gli input validi chiudono normalmente il dialogo.

- Rinominati i file di log con rotazione da `nexu-0.log` a `nexu-port-forwarding-0.log`, `nexu-port-forwarding-1.log`, ecc. I vecchi file già esistenti restano invariati.

- Controllo preventivo della disponibilità del listener locale per LOCAL e DYNAMIC/SOCKS prima dell'autenticazione SSH. Le porte già occupate vengono segnalate subito indicando che un'altra applicazione di tunneling (per esempio Tabby/MobaXterm) potrebbe già usarle.
- I log persistenti registrano ora categorie diagnostiche sanificate e contesto sicuro per gli errori riconosciuti, continuando a escludere credenziali e testo grezzo arbitrario delle eccezioni.

- **NOME** modificabile direttamente in griglia con salvataggio automatico su Invio o perdita del focus.
- Rimossa la colonna **INSTALLAZIONE** dalla griglia principale.
- **TIPO**, **ASCOLTO** e **DESTINAZIONE** sono riuniti in una singola colonna **FORWARDING** con frecce che rendono esplicito il lato di ascolto.
- Esportazioni separate per Windows PowerShell, Windows CMD e Linux/POSIX; CMD non usa più quoting PowerShell.

- I campi password/passphrase includono il pulsante occhio per mostrare/nascondere il valore.
- La colonna **AZIONI** è ora la prima colonna della tabella.
- Gli errori SSH distinguono DNS, timeout TCP, connessione rifiutata, assenza di route, handshake interrotto e autenticazione. Nei timeout viene indicato esplicitamente che la connessione è diretta e che il proxy HTTP/SOCKS del sistema operativo non viene usato automaticamente.

- Tab nell'ordine **Attivi → Custom → Tabby → MobaXterm**. Attivi contiene soltanto i tunnel nello stato SSH/forwarding ACTIVE.
- Importazione **Tabby** in sola lettura da `config.yaml`, con anteprima e conversione Local/Remote/Dynamic.
- Importazione **MobaXterm** in sola lettura della sezione `[PortForwarding]` da `MobaXterm.ini`/`.mobaconf`; sezioni password e impostazioni non pertinenti vengono ignorate.
- Dopo l'importazione le righe diventano normali profili locali di Nexu Port Forwarding. Non è necessario mantenere aperto Tabby/MobaXterm e nessun tunnel parte automaticamente.
- Filtri per tipologia di forwarding e IP risolto del server SSH, oltre a testo libero e stato.
- Supporto DYNAMIC (`-D`) SOCKS tramite Apache MINA SSHD.
- Salvataggio: i portabili usano `data/` e `logs/` affiancate; le installazioni usano `nexu-port-forwarding/data/` e `nexu-port-forwarding/logs/` nella cartella utente. I file dati 1.0.0 riconosciuti vengono copiati una sola volta in `data/` senza eliminare gli originali.

### Sicurezza

Gli importatori non copiano password, trust host, script o configurazioni non pertinenti di Tabby/MobaXterm. Proxy/jump-host non supportati vengono saltati invece di trasformarsi silenziosamente in connessioni dirette. Tutti i tunnel importati partono fermi.

È una prerelease non firmata: verificare l'importazione sulla propria configurazione prima dell'uso in produzione.
