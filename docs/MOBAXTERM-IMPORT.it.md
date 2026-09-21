# Importazione forwarding MobaXterm

[English](MOBAXTERM-IMPORT.md) | [Italiano](MOBAXTERM-IMPORT.it.md)

Nexu Port Forwarding importa **soltanto** la sezione `[PortForwarding]` di un file `MobaXterm.ini` o `.mobaconf` scelto dall'utente. La documentazione MobaXterm indica che la configurazione viene salvata in `MobaXterm.ini`, normalmente in Documents/MobaXterm per l'edizione installata, accanto all'eseguibile per la portabile oppure sotto `%APPDATA%\MobaXterm` in alcune versioni. L'importatore propone i percorsi utente comuni ma permette sempre di scegliere manualmente il file.

Ogni voce supportata `Local`, `Remote` o `Dynamic` diventa un profilo locale e fermo di Nexu Port Forwarding. Vengono copiati utente SSH, host, porta SSH, indirizzo/porta di ascolto, destinazione quando prevista e il percorso di una chiave privata esplicitamente configurata. Le sezioni delle password non vengono mai analizzate. Le righe che richiedono un proxy vengono saltate invece di essere trasformate in connessioni dirette.

L'importazione mostra un'anteprima. I fingerprint sorgente identici non vengono importati due volte. Dopo l'importazione i profili sono persistiti nel `data/profiles.properties` di Nexu Port Forwarding; per quelle righe MobaXterm non viene più consultato.

Il formato INI dei tunnel MobaXterm non è uno standard di interscambio pubblico stabile: questa prerelease accetta volutamente soltanto il layout a campi separati da `;` osservato nelle voci `[PortForwarding]` correnti e segnala le righe sconosciute senza indovinarne il significato.
