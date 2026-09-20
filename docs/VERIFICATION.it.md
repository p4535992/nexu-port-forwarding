# Verifica di Nexu Port Forwarding

[English](VERIFICATION.md) | [Italiano](VERIFICATION.it.md)

## Base del repository ricreato

Il repository pubblico è stato verificato il 20 settembre 2026. Il commit iniziale di `main` era `7dfd1e28368b534847f1673d5c1f89c39e3c3496`, senza genitori. L’unico branch e il tag iniziale puntavano allo stesso commit. L’albero completo `349a7bc1ab28174a914104aadae7a5511e0a16ab` conteneva 62 file e corrispondeva byte per byte alla copia ricostruita localmente, inclusi i permessi Git.

Nessuno dei due valori dell’infrastruttura ritirati è stato trovato nei file, comprese le codifiche UTF-8 e UTF-16. Nessuna corrispondenza per i pattern comuni di chiavi private/token controllati. Le password sintetiche dei test non sono credenziali di produzione. È un controllo mirato, non una garanzia di assenza di qualsiasi altro dato sensibile.

La base ha superato 62 casi core e 30 casi storage POSIX offline. Questi risultati non sostituiscono la build del sorgente finale su entrambe le piattaforme.

## Controlli della release 1.0.0

Il [workflow di release](../.github/workflows/release.yml) pubblica `v1.0.0` soltanto quando Windows 2022 x64 e Ubuntu 22.04 x64 superano compilazione, test Maven, packaging, avvio/uscita JavaFX pacchettizzata e verifica dei log persistenti. Controlla anche coerenza versione, link locali della documentazione, nome completo, inclusione MIT, JAR applicativo e cronologia Git raggiungibile per gli endpoint ritirati. Le regole conservano hash, non i valori ritirati.

Le note della release pubblicata riportano commit sorgente e run esatti. Gli ZIP diagnostici per piattaforma includono report Maven e risultati dei controlli. Nessun risultato del repository cancellato viene usato come prova per la nuova release.

Maven esegue 12 metodi JUnit: due suite aggregate (62 casi core e 30 storage POSIX, oppure 28 storage su Windows), sei metodi d’integrazione SSH, un metodo per la policy forwarding e tre per la persistenza della finestra. I sei test d’integrazione usano soltanto fixture SSH/echo loopback per trasferimento locale/remoto, rifiuto password, chiave host rifiutata, pin modificato e conflitto di bind. La smoke test pacchettizzata disattiva la tray e non apre tunnel SSH.

## Riproduzione

```bash
mvn --batch-mode --no-transfer-progress clean verify
python scripts/prepare-distribution.py
python scripts/verify-release.py --history --app target/app
```

Test core/storage offline: `bash scripts/test-core-offline.sh`. Il verificatore di release richiede Python 3.10+ e Git; `--history` ispeziona HEAD, branch e tag, non riferimenti PR. Non è uno scanner generale di segreti.

## Limiti

I binari non sono firmati. Upgrade/disinstallazione reali degli installer, varianti tray/window manager Linux, flussi interattivi completi, policy SSH reali, formati di chiavi private, sleep/resume e cambi di rete richiedono verifica nell’ambiente di destinazione. I controlli non sono un audit esaustivo di sicurezza o licenze.

Le singole scritture configurazione/vault usano file temporanei riservati al proprietario e sostituzione atomica dove disponibile. L’import backup non è una transazione multi-file: salva le credenziali prima dei profili, quindi un errore nella seconda scrittura può lasciare credenziali cifrate orfane senza sostituire i profili precedenti. Conservare backup. I file di configurazione sono limitati a 2 MB e gli archivi cifrati a 8 MB. La cifratura non protegge un processo sbloccato da malware/amministratori; la cancellazione immediata delle stringhe gestite dalle librerie non è garantita.

La verifica del repository non dimostra la cancellazione di vecchi cloni altrui, binari scaricati o cache del servizio. Non copiare qui la vecchia directory `.git` o i vecchi binari.
