# Nexu Port Forwarding 1.2.1-rc.2

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## Italiano

Prerelease focalizzata su una segnalazione più chiara dello stato dei tunnel e su una diagnostica più utile quando il server SSH rifiuta un remote port forwarding.

### Modifiche rispetto alla 1.2.0

- Le righe nello stato **STOPPING / Arresto** passano immediatamente allo sfondo rosso. Le righe **STOPPED / Fermo**, compresi i tunnel appena creati, restano rosse; le righe **ACTIVE / Attivo** restano verdi.
- I colori esistenti dei pulsanti **Avvia/Ferma** non cambiano.
- Quando un forwarding **REMOTE (-R)** viene rifiutato dopo che l'autenticazione SSH è riuscita, il messaggio suggerisce ora esplicitamente di controllare se il listener richiesto sul server SSH è già occupato da un altro remote forwarding/sessione SSH oppure da un altro processo.
- Lo stesso messaggio continua a indicare le verifiche lato server: `AllowTcpForwarding`, `PermitListen`, `GatewayPorts` per listener non-loopback ed eventuali restrizioni `Match`/per utente.
- Aggiunto un test di regressione per il nuovo suggerimento relativo alla porta remota già occupata.
- Versione applicazione/pacchetti aggiornata a **1.2.1** per la prerelease **v1.2.1-rc.2**.
- Aggiunto un fixture di regressione con struttura Tabby v8 reale: gli inoltri sotto `profiles[*].options.forwardedPorts`, anche multipli nello stesso profilo SSH, mantengono host SSH, porta, username e forwarding Local/Remote senza importare password.
- I fixture MobaXterm `[PortForwarding]` mantengono Local/Remote, username SSH e tunnel ripetuti; i trasporti `WEB proxy` / SOCKS5 riconosciuti vengono convertiti nelle impostazioni proxy Nexu senza importare credenziali proxy.
- L'assenza di `auth` in Tabby non blocca più la preselezione: il profilo viene importato senza credenziale salvata e Nexu la chiede all'avvio del tunnel.
- rc.2 corregge la regressione MobaXterm che perdeva il proxy riconosciuto nella costruzione finale del profilo e aggiorna il test Tabby al comportamento previsto senza credenziale importata.

### Nota diagnostica

Un rifiuto generico del remote forwarding SSH non permette sempre di distinguere una porta già occupata da una restrizione di `sshd`. Nexu segnala quindi entrambe le possibilità senza indicarne una come causa certa.

### Sicurezza

Questa è una prerelease non firmata. Verificare il forwarding e le policy del server SSH nel proprio ambiente prima dell'uso in produzione.
