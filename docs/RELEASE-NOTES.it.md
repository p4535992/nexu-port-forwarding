# Nexu Port Forwarding 1.2.1

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## Italiano

Release stabile di manutenzione 1.2.1 con indicazione più chiara dello stato dei tunnel, diagnostica migliorata per il remote forwarding e importazione Tabby/MobaXterm molto più robusta.

### Modifiche rispetto alla 1.2.0

- Le righe nello stato **STOPPING / Arresto** passano immediatamente allo sfondo rosso. Le righe **STOPPED / Fermo**, compresi i tunnel appena creati, restano rosse; le righe **ACTIVE / Attivo** restano verdi.
- I colori esistenti dei pulsanti **Avvia/Ferma** non cambiano.
- Quando un forwarding **REMOTE (-R)** viene rifiutato dopo un'autenticazione SSH riuscita, il messaggio suggerisce esplicitamente di verificare se il listener richiesto sul server SSH è già occupato da un altro remote forwarding/sessione SSH o da un altro processo.
- La stessa diagnostica indica anche `AllowTcpForwarding`, `PermitListen`, `GatewayPorts` per listener non-loopback ed eventuali restrizioni `Match`/per utente.

- Supportati gli inoltri Tabby v8 sotto `profiles[*].options.forwardedPorts`, compresi più forwarding Local/Remote nello stesso profilo SSH.
- L'import Tabby conserva hostname SSH, porta SSH, username, listener e destinazione senza importare password.
- L'assenza di `auth` in Tabby non blocca più la normale selezione: il profilo viene importato senza credenziale salvata e Nexu la richiede all'avvio del tunnel.

- L'importazione MobaXterm `[PortForwarding]` conserva definizioni Local/Remote, username SSH e tunnel con nomi ripetuti.
- I trasporti MobaXterm `WEB proxy`/HTTP CONNECT e SOCKS5 riconosciuti vengono mantenuti senza importare credenziali proxy.
- Le entry MobaXterm malformate vengono isolate per singola riga: gli errori di validazione incrementano `skipped` e vengono riportati come warning senza interrompere l'intero file.
- Un test di regressione verifica la sequenza Local valido → host SSH URL-like non valido → Remote valido, assicurando che le entry valide successive restino importabili.
- I profili importati da Tabby e MobaXterm vengono aggiunti ai profili esistenti; le origini Custom, Tabby e MobaXterm restano distinte.

### Nota diagnostica

Un rifiuto generico del remote forwarding SSH non permette sempre di distinguere una porta già occupata da una restrizione di `sshd`. Nexu segnala entrambe le possibilità senza indicarne una come causa certa.

### Sicurezza

Le password non vengono importate dai file di configurazione Tabby o MobaXterm. La release non è firmata: verificare il forwarding e le policy del server SSH nel proprio ambiente prima dell'uso in produzione.
