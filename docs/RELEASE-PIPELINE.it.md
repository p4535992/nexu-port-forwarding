# Pipeline di release Nexu Port Forwarding

[English](RELEASE-PIPELINE.md) | [Italiano](RELEASE-PIPELINE.it.md)

I push dei sorgenti applicativi su `main` o l'avvio manuale del workflow preparano la versione dichiarata in `pom.xml` e `APP_VERSION`. Per questo ciclo la versione è **1.2.2** e il tag è **v1.2.2**.

Una release in bozza è associata al commit sorgente esatto. Windows 2022 x64 e Ubuntu 22.04 x64 compilano e testano indipendentemente, raccolgono l'inventario delle dipendenze e gli avvisi legali e costruiscono i pacchetti nativi. I controlli verificano coerenza delle versioni, link documentazione, endpoint ritirati nella cronologia/JAR, licenza MIT, importazione, forwarding SSH e avvio della GUI pacchettizzata.

Solo se entrambi i job riescono, la pipeline verifica i pacchetti, aggiunge lo ZIP sorgente senza cronologia Git, genera `SHA256SUMS.txt`, unisce le note inglesi/italiane e pubblica **v1.2.2** come release **stabile e Latest**. Le vecchie versioni pubblicate non vengono sovrascritte.

Pacchetti e diagnostica usano GitHub Releases. I tentativi falliti restano bozze non pubblicate. Il workflow non elimina vecchie release, non riscrive la cronologia Git, non accede a risorse PR, non contatta server SSH reali e non cambia la visibilità del repository.
