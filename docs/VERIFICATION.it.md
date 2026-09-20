# Verifica ed evidenze della release

[English](VERIFICATION.md) | [Italiano](VERIFICATION.it.md)

## Release pubblicata e verificata

**Release:** [v0.2.1-rc.9](https://github.com/p4535992/nexu-port-forwarding/releases/tag/v0.2.1-rc.9), pubblicata il 20 settembre 2026 alle 07:56:25 UTC.

**Commit sorgente dell'applicazione:** `0fae8a332f9f96f5b91a9c281a0b2ee8a330ad15`.

**Workflow riuscito:** [run 35498023726](https://github.com/p4535992/nexu-port-forwarding/actions/runs/35498023726).

Entrambi i job di piattaforma e il job finale di pubblicazione sono terminati con successo. I metadati della release sono stati verificati dopo la pubblicazione: `draft=false`, `prerelease=true` e il commit target corrisponde al sorgente indicato sopra. I successivi commit di sola documentazione non modificano questi binari.

| Controllo automatico | Windows 2022 x64 | Ubuntu 22.04 x64 |
| --- | --- | --- |
| Compilazione applicazione e test con Java 21 | Superato | Superato |
| Test core, storage, stato finestra, policy forwarding e integrazione SSH | Superato | Superato |
| Packaging nativo | ZIP, EXE, MSI generati | TAR.GZ, DEB, RPM generati |
| Avvio e uscita dell'app JavaFX pacchettizzata | Superato | Superato sotto Xvfb |
| Verifica finestra nativa decorata, visibile e non full-screen | Superato | Superato |
| Log diagnostico persistente e marker di readiness | Superato | Superato |
| Upload pacchetti e archivi diagnostici | Superato | Superato |

La release contiene otto distribuzioni applicative: sei pacchetti nativi e due archivi Java specifici per piattaforma contenenti il JAR e `lib/`. I pacchetti nativi includono il runtime. Sono allegati anche entrambi gli archivi diagnostici e `SHA256SUMS.txt`.

## Comportamento finestra verificato in 0.2.1

Lo stage principale usa esplicitamente JavaFX `StageStyle.DECORATED`, non viene avviato in full-screen ed è collocato dentro l'area di lavoro visibile di uno schermo. Le dimensioni predefinite si adattano al monitor corrente invece di assumere un desktop fisso da 1310×850.

I bounds normali e il flag di massimizzazione vengono salvati in `window.properties`. Geometrie non valide o fuori schermo vengono ignorate o ricondotte a un monitor visibile. La smoke test del pacchetto rifiuta la build se la finestra non è decorata, è full-screen oppure è fuori da tutti gli schermi rilevati.

I controlli minimizza e massimizza/ripristina sono quindi forniti da Windows o dal window manager Linux. L'applicazione intercetta solo **X** per offrire riduzione oppure uscita. Con tray disponibile, la riduzione nasconde l'app nell'area di notifica; senza tray ricade sulla normale minimizzazione.

## Copertura e conteggio dei test

Maven esegue **12 metodi JUnit**: due suite aggregate, sei metodi d'integrazione SSH, un metodo per la policy di forwarding e tre metodi per la persistenza dello stato finestra. L'aggregato core contiene **62 casi nominati**. L'aggregato storage contiene **30 casi su POSIX**, oppure **28 quando i permessi POSIX non sono disponibili**, come sul runner Windows.

La copertura core/storage comprende validazione e roundtrip dei profili, quoting dei comandi, pin delle chiavi host, cancellazione/retry/ciclo di vita indipendente delle connessioni, cifratura/decifratura, password errate, archivi alterati, binding all'identità, rotazione della password principale, import append dei backup, persistenza dopo riapertura e log su disco senza segreti.

I test dello stato finestra coprono assenza dello stato, roundtrip della persistenza e rifiuto di geometrie corrotte o non sicure.

I sei test d'integrazione Apache MINA verificano trasferimento TCP locale e remoto, rifiuto password errata, rifiuto della chiave host prima dell'autenticazione password, rifiuto del pin cambiato e conflitto sul bind remoto. La chiusura del listener viene verificata con un controllo bounded eventual-close per non dipendere dal timing non deterministico della chiusura TCP. Le fixture SSH ed echo fanno bind esclusivamente su `127.0.0.1` e non contattano il server SSH o il servizio Maven dell'utente.

Il test della policy di forwarding verifica che i canali remoti possano raggiungere soltanto host e porta configurati, nega canali diretti arbitrari e listener aggiuntivi e nega nuove connessioni dopo la cancellazione. Agent forwarding e X11 sono disabilitati.

La smoke test pacchettizzata avvia l'applicazione JavaFX reale, scrive `UI_READY 0.2.1 DECORATED WINDOWED` e il log diagnostico locale, quindi termina senza aprire tunnel SSH. La tray viene volutamente disattivata in modalità smoke test.

## Riproduzione

Build completa, inclusi i test d'integrazione:

```text
mvn --batch-mode --no-transfer-progress clean verify
```

Verifica core e storage senza dipendenze esterne:

```bash
bash scripts/test-core-offline.sh
```

## Verifiche ancora richieste all'operatore

I pacchetti sono release candidate non firmate. Installazione, aggiornamento e disinstallazione reali EXE/MSI, installazione/rimozione DEB/RPM sulle distribuzioni target, varianti di tray/window manager Linux, comportamento interattivo del dialogo di chiusura, formati di chiave privata contro server reali, sleep/resume e cambi di rete richiedono verifica nell'ambiente di destinazione. La smoke test automatica non è un test desktop end-to-end completo né un audit di sicurezza.

## Modello di errore dello storage e limiti

Ogni singola scrittura di configurazione/vault usa un file temporaneo con permessi limitati al proprietario, flush e sostituzione atomica dove supportata. L'import del backup valida prima e assegna nuovi ID. Le credenziali cifrate vengono salvate prima dei profili: se la seconda scrittura fallisce, i profili precedenti restano invariati ma possono rimanere credenziali cifrate orfane. Non è una transazione multi-file.

L'input dei profili è limitato a 1.000 righe e a un file di configurazione da 2 MB. Gli envelope cifrati sono limitati a 8 MB. Il vault non protegge un processo in esecuzione e sbloccato da malware o amministratori. Le stringhe gestite da JavaFX/librerie non possono essere garantite come cancellate immediatamente.
