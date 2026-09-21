# Verifica — 1.1.0-rc.1

[English](VERIFICATION.md) | [Italiano](VERIFICATION.it.md)

Questa prerelease aggiunge importazione Tabby/MobaXterm, viste Attivi/Custom/Tabby/MobaXterm, filtro Local/Remote/Dynamic, risoluzione asincrona hostname→IP per visualizzazione/filtro e layout `nexu-port-forwarding/data` con `logs` sorella.

I controlli locali senza dipendenze esterne coprono motore core, storage cifrato, risoluzione/migrazione percorsi portabili e utente, conversione Tabby e conversione `[PortForwarding]` MobaXterm. Il workflow di release esegue inoltre Maven/JUnit, integrazione SSH loopback Apache MINA, packaging nativo e smoke test JavaFX pacchettizzata su Windows e Linux.

Gli importatori leggono soltanto la configurazione esterna. Password/vault Tabby e sezioni password MobaXterm non vengono importati. Proxy/jump-host non supportati vengono rifiutati invece di essere trasformati silenziosamente. Le righe importate vengono persistite in Nexu Port Forwarding e partono ferme.

La risoluzione hostname usa il resolver DNS del sistema per visualizzazione/filtro e non invia ping ICMP. Gli IP letterali vengono copiati direttamente.

Il layout utente 1.0.0 viene migrato in modo conservativo: i file riconosciuti direttamente sotto la cartella padre `nexu-port-forwarding` vengono copiati in `data/` solo quando la destinazione non esiste; gli originali restano intatti. Lo storage portabile resta locale alla cartella estratta.

La prerelease resta non firmata e non costituisce un audit di sicurezza. Varianti reali MobaXterm, file Tabby reali, tray Linux, upgrade/disinstallazione installer e policy SSH reali richiedono ancora verifica operatore.
