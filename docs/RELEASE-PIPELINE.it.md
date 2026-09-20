# Pipeline di release di Nexu Port Forwarding

[English](RELEASE-PIPELINE.md) | [Italiano](RELEASE-PIPELINE.it.md)

I push dei sorgenti applicativi su `main` o l’avvio manuale del workflow preparano la versione dichiarata in `pom.xml` e `APP_VERSION`. La versione 1.0.0 viene pubblicata come **v1.0.0**, non come release candidate.

Una draft per run prepara il commit sorgente esatto. Windows 2022 x64 e Ubuntu 22.04 x64 compilano e testano indipendentemente, raccolgono l’inventario dei JAR runtime e gli avvisi legali inclusi, e generano i pacchetti nativi. I controlli verificano versione, link della documentazione, nome completo, endpoint ritirati nella cronologia raggiungibile e nel JAR applicativo, e inclusione MIT. L’applicazione JavaFX pacchettizzata viene avviata e arrestata con controlli su finestra e log locali.

Solo dopo il successo di entrambi i job la pubblicazione verifica tutti i pacchetti previsti, aggiunge uno ZIP sorgente senza cronologia Git, calcola `SHA256SUMS.txt`, unisce le note con **inglese prima e italiano dopo** e pubblica la draft con tag stabile come Latest. Le note riportano commit sorgente e run. Le versioni già pubblicate non vengono sovrascritte: incrementare la versione prima di una nuova pubblicazione.

Pacchetti e diagnostica usano GitHub Releases, non lo storage degli artifact Actions. I tentativi falliti restano draft non pubblicate. Il workflow non elimina release precedenti, non riscrive la cronologia, non accede a risorse PR, non contatta server SSH reali e non cambia la visibilità del repository. I log diagnostici contengono dati sintetici dei test; vanno comunque controllati prima della pubblicazione.

Per la versione successiva aggiornare insieme `pom.xml`, `APP_VERSION`, `RELEASE_TAG`, stringhe versione di launcher/UI/log, valori predefiniti degli script e documentazione bilingue. Conservare UUID di upgrade Windows e directory dati indipendente dalla versione. Lo stato stabile non implica firma digitale: i binari non sono firmati.
