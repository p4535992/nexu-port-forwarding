# Nexu Port Forwarding 1.1.0-rc.1

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## Italiano

Prerelease che aggiunge importazione da applicazioni esterne e nuovi filtri operativi, lasciando invariata la release stabile 1.0.0.

### Novità

- Tab nell'ordine **Attivi → Custom → Tabby → MobaXterm**. Attivi contiene soltanto i tunnel nello stato SSH/forwarding ACTIVE.
- Importazione **Tabby** in sola lettura da `config.yaml`, con anteprima e conversione Local/Remote/Dynamic.
- Importazione **MobaXterm** in sola lettura della sezione `[PortForwarding]` da `MobaXterm.ini`/`.mobaconf`; sezioni password e impostazioni non pertinenti vengono ignorate.
- Dopo l'importazione le righe diventano normali profili locali di Nexu Port Forwarding. Non è necessario mantenere aperto Tabby/MobaXterm e nessun tunnel parte automaticamente.
- Filtri per tipologia di forwarding e IP risolto del server SSH, oltre a testo libero e stato.
- Colonne separate **HOSTNAME** e **INDIRIZZO IP**. Se l'host è già un IP viene copiato subito; gli hostname vengono risolti in background tramite il resolver DNS del sistema (senza ping ICMP).
- Colonna **INSTALLAZIONE** modificabile e ricercabile per cliente/sede/ambiente.
- Supporto DYNAMIC (`-D`) SOCKS tramite Apache MINA SSHD.
- Salvataggio: i portabili usano `data/` e `logs/` affiancate; le installazioni usano `nexu-port-forwarding/data/` e `nexu-port-forwarding/logs/` nella cartella utente. I file dati 1.0.0 riconosciuti vengono copiati una sola volta in `data/` senza eliminare gli originali.

### Sicurezza

Gli importatori non copiano password, trust host, script o configurazioni non pertinenti di Tabby/MobaXterm. Proxy/jump-host non supportati vengono saltati invece di trasformarsi silenziosamente in connessioni dirette. Tutti i tunnel importati partono fermi.

È una prerelease non firmata: verificare l'importazione sulla propria configurazione prima dell'uso in produzione.
