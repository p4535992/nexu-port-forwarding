# NexU Port Forwarding 0.2.1 release candidate

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

Gestore desktop di forwarding TCP SSH locale (-L) e remoto (-R) per Windows e Linux.

## Comportamento della finestra

Questa release usa esplicitamente una finestra desktop con decorazioni native. All'avvio, dimensione e posizione vengono adattate all'area di lavoro dello schermo corrente invece di assumere un desktop grande e fisso. Dimensione/posizione normali e stato massimizzato/ripristinato vengono salvati localmente in `window.properties`; geometrie non valide o fuori schermo vengono riportate su un monitor visibile.

I controlli nativi **minimizza** e **massimizza/ripristina** restano gestiti da Windows o dal window manager Linux. Premendo **X** viene chiesto se lasciare l'applicazione in esecuzione ridotta (area di notifica quando disponibile, altrimenti normale minimizzazione) oppure uscire. Se ci sono tunnel attivi, il dialogo specifica che uscire li fermerà mentre ridurre l'app li mantiene in esecuzione.

## Pacchetti

Windows x64: ZIP portabile, installer EXE e installer MSI.

Linux x64: TAR.GZ portabile, DEB e RPM.

Il runtime Java 21 è incluso nei pacchetti nativi. Gli archivi solo Java contengono il JAR specifico per piattaforma e la directory delle dipendenze.

## Dati locali e password

Restano invariati la directory dati indipendente dalla versione, il launcher portabile opzionale, i log diagnostici con rotazione, il vault locale cifrato per password/passphrase e l'import/export dei backup cifrati. Lo stato della finestra non contiene credenziali.

## Gate automatici della release

Entrambe le build devono compilare e superare test Maven, integrazione SSH loopback, packaging e smoke test JavaFX pacchettizzata. La smoke test richiede inoltre una finestra visibile, nativa `DECORATED` e non full-screen. Gli artifact vengono pubblicati solo dopo il successo di entrambe le piattaforme, con checksum SHA-256.

## Limiti

Release candidate non firmata. Il comportamento dell'area di notifica Linux dipende ancora dall'ambiente desktop; se la tray non è disponibile il dialogo di chiusura ricade sulla normale minimizzazione. Il comportamento reale del desktop/window manager e i server SSH reali devono essere validati prima dell'uso in produzione.
