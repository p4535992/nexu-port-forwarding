# Nexu Port Forwarding 1.2.0-rc.1

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## Italiano

Prima release candidate della linea 1.2.0, dedicata al supporto proxy esplicito per SSH e a una diagnostica più chiara dei forwarding rifiutati.

### Novità

- Aggiunto il tipo di collegamento per profilo: **Diretto**, **SOCKS5** oppure **HTTP CONNECT**.
- Host, porta e utente proxy opzionale vengono salvati nel profilo; la password proxy viene richiesta all'avvio del tunnel e resta soltanto in memoria.
- I file profilo v1/v2 restano compatibili e diventano automaticamente **Diretto**; il nuovo formato profilo è v3.
- Aggiunte categorie diagnostiche specifiche per DNS del proxy, timeout, autenticazione, SOCKS5 e HTTP CONNECT.
- `FORWARDING_REJECTED` registra ora anche il forwarding concreto richiesto e indica le verifiche lato server SSH. Per un forwarding remoto vengono citati esplicitamente `AllowTcpForwarding`, il `PermitListen` necessario per il listener richiesto, `GatewayPorts` quando si richiede un bind non-loopback ed eventuali restrizioni `Match`/per utente.
- La release stabile 1.1.0 resta invariata; questa prerelease serve a validare il nuovo trasporto proxy su Windows e Linux.

### Sicurezza

Le password proxy non vengono scritte nei file profilo né esportate come configurazione in chiaro. Restano solo nella sessione e vengono eliminate con il comando “Blocca e dimentica segreti in memoria” o alla chiusura dell'applicazione.

È una prerelease non firmata: verificare proxy e policy del server SSH nel proprio ambiente prima dell'uso in produzione.
