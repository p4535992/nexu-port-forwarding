# Pipeline di release

[English](RELEASE-PIPELINE.md) | [Italiano](RELEASE-PIPELINE.it.md)

Ogni push dei sorgenti applicativi su `main`, oppure un avvio manuale del workflow, crea una release candidate con numero univoco. Le build vengono eseguite su Windows 2022 e Ubuntu 22.04 x64.

Un job di preparazione crea una **draft release** legata al commit sorgente esatto. Ogni build esegue i test Maven, il packaging nativo e la smoke test dell'app JavaFX pacchettizzata con verifica dei log. La smoke test controlla anche che la finestra principale sia decorata nativamente, non full-screen e visibile su uno schermo rilevato. Solo i pacchetti nativi che superano i test vengono caricati nella draft. Report e log di build sono allegati come archivi diagnostici, anche quando una build fallisce.

Il job di pubblicazione viene eseguito soltanto dopo il successo di **entrambi** i sistemi operativi. Verifica la presenza di tutte le otto distribuzioni previste, aggiunge `SHA256SUMS.txt` e converte la draft in prerelease con note curate. I tentativi falliti rimangono draft non validate e non sono release utilizzabili. Nessun processo elimina automaticamente vecchie release, artefatti o dati utente. Gli asset esistenti non vengono sovrascritti.

Gli asset vengono trasferiti direttamente tramite GitHub Releases invece di `actions/upload-artifact`, perché in precedenza l'account aveva segnalato l'esaurimento della quota di storage degli artifact di Actions. Questo evita che la consegna dipenda da quella quota e non richiede l'eliminazione di artifact di altri repository. Non vengono modificate impostazioni di fatturazione o quota.

La versione nativa predefinita è **0.2.1**; il tag di distribuzione aggiunge `rc.<numero run workflow>`. L'UUID stabile di upgrade Windows è dichiarato in `scripts/package-windows.ps1`. Per la versione applicativa successiva vanno aggiornati insieme `APP_VERSION`, `RELEASE_TAG`, `pom.xml`, le etichette versione di launcher/UI e le note di release.

Non vengono usati release notes generati automaticamente, trigger da pull request, merge automatici, sincronizzazione cloud delle password, self-updater o connessioni SSH esterne reali. La smoke test disabilita volutamente la tray in CI; le varianti reali della tray e il comportamento di upgrade degli installer devono essere verificati dall'operatore.
