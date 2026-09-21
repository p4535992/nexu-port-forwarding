# Pipeline di release Nexu Port Forwarding

[English](RELEASE-PIPELINE.md) | [Italiano](RELEASE-PIPELINE.it.md)

I push dei sorgenti applicativi su `main` o l'avvio manuale del workflow preparano la versione dichiarata in `pom.xml` e `APP_VERSION`. Per questo ciclo la versione applicativa/pacchetto è **1.1.0** e il tag di release è **v1.1.0-rc.1**.

Una draft per ogni run è legata al commit sorgente esatto. Windows 2022 x64 e Ubuntu 22.04 x64 compilano e testano indipendentemente, raccolgono inventario JAR e avvisi legali e costruiscono i pacchetti nativi. I controlli verificano coerenza versione, link documentazione, nome completo del progetto, endpoint ritirati nella cronologia/JAR, inclusione MIT, test importatori, forwarding dinamico e avvio della GUI pacchettizzata.

Solo se entrambi i job passano, la pubblicazione verifica tutti i pacchetti, aggiunge lo ZIP sorgente senza cronologia Git, calcola `SHA256SUMS.txt`, combina le note con inglese prima e italiano dopo e pubblica **v1.1.0-rc.1** con `prerelease=true` e **non Latest**. La release stabile `v1.0.0` non viene modificata. Le versioni già pubblicate non vengono sovrascritte.

Pacchetti e diagnostica usano GitHub Releases. I tentativi falliti restano draft non pubblicate. Il workflow non elimina vecchie release, non riscrive la cronologia Git, non accede a risorse PR, non collega server SSH reali e non cambia la visibilità del repository.
