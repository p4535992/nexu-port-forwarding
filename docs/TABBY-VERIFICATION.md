# Nexu Port Forwarding — Tabby import verification

[English](#english) | [Italiano](#italiano)

## English

**Verification notes for the v1.1.0-rc.1 prerelease source.** The stable v1.0.0 release remains unchanged. These notes describe the import/storage changes that are validated locally and again by the release CI before publication.

Implemented in source: Attivi/Custom/Tabby/MobaXterm tabs; a selectable import preview; Tabby Local/Remote/Dynamic mappings; dynamic forwarding through the Apache MINA SOCKS API; an editable/searchable installation column; repeat-import deduplication; origin/installation persistence in configuration format 2 and encrypted backups. Existing format-1 profiles map to Custom. Passwords, vaults, scripts and host trust are not imported from Tabby. Unsupported jump-host/proxy and agent/MFA profiles are reported and skipped. Tabby and MobaXterm are import-only sources; imported rows are persisted by Nexu Port Forwarding. Portable data/logs behavior and the installed user data/data+logs layout are included.

### Checks actually executed

- **209 dependency-free Java cases passed:** 62 core, 30 POSIX storage/security, 40 portable storage/migration, 54 Tabby conversion/metadata/deduplication/backup cases, and 23 MobaXterm import cases. The 54 Tabby tests exercise the converter with plain Java maps/lists; they do not substitute for testing the YAML parser.
- **7 existing Python tests passed**, including release-check helpers and the mocked portable-packaging tests.
- The dependency-free suites compile their selected Java 21 sources directly. Full external-library type checking is performed by the release CI with Maven before packaging.
- The final source snapshot is checked for local documentation links, naming, version consistency, and the existing targeted retired-endpoint/token patterns. Patch-application and ZIP integrity checks are recorded in the accompanying evidence files.

### Not executed here

Maven is not installed in this local environment, so **full Maven compilation, JavaFX execution, the actual SnakeYAML reader and the actual dynamic SOCKS backend are release-CI gates rather than local claims.** JUnit coverage for the parser, SOCKS loopback integration, forwarding policy, hostname/IP resolution and MobaXterm import is included. The release workflow must pass on Windows and Linux before v1.1.0-rc.1 is published. No actual user Tabby or MobaXterm configuration was read during development.

**Back up existing profiles before testing.** Configuration format 2 is readable by this update but not by older application binaries. Restore an earlier backup before downgrading. No configuration is silently moved between portable storage and AppData/XDG.

## Italiano

**Note di verifica dei sorgenti della prerelease v1.1.0-rc.1.** La release stabile v1.0.0 resta invariata. Queste note descrivono le modifiche di importazione/storage validate localmente e nuovamente dalla CI prima della pubblicazione.

Implementati nei sorgenti: tab Attivi/Custom/Tabby/MobaXterm, anteprima con selezione, import di Local/Remote/Dynamic, backend SOCKS tramite Apache MINA, colonna Installazione modificabile e ricercabile, controllo duplicati, persistenza di origine/installazione nel formato 2 e nei backup cifrati. I profili formato 1 vengono assegnati a Custom. Password, vault, script e fiducia host non vengono importati da Tabby. Profili con jump host/proxy o agent/MFA non supportati vengono segnalati e saltati. Tabby e MobaXterm sono solo sorgenti di importazione; le righe importate vengono poi salvate da Nexu Port Forwarding. Sono inclusi sia lo storage portabile sia il layout utente installato con data/ e logs/.

### Verifiche realmente eseguite

- **209 casi Java senza dipendenze superati:** 62 core, 30 storage/sicurezza POSIX, 40 storage/migrazione, 54 conversione Tabby/metadati/duplicati/backup e 23 importazione MobaXterm. I 54 test Tabby usano mappe e liste Java, non il parser YAML.
- **7 test Python esistenti superati**, inclusi gli helper di verifica e i test di packaging portabile simulato.
- Le suite senza dipendenze compilano direttamente i sorgenti Java 21 selezionati. La verifica completa dei tipi con librerie esterne viene eseguita dalla CI Maven prima del packaging.
- Controlli del sorgente finale su link locali, nome completo, coerenza versione e pattern mirati già presenti. Applicazione patch e integrità ZIP sono registrate nelle evidenze allegate.

### Verifiche ancora da eseguire

Maven non è installato in questo ambiente locale, quindi **compilazione Maven completa, esecuzione JavaFX, parser SnakeYAML reale e backend SOCKS reale sono gate della CI di release e non dichiarazioni dei test locali.** Sono inclusi test JUnit per parser, SOCKS loopback, policy di forwarding, risoluzione hostname/IP e importazione MobaXterm. La pipeline Windows/Linux deve completarsi prima della pubblicazione di v1.1.0-rc.1. Nessuna configurazione Tabby o MobaXterm reale dell’utente è stata letta durante lo sviluppo.

**Fare un backup prima della prova.** Il formato configurazione 2 non è leggibile dai vecchi binari: per tornare indietro, ripristinare il backup precedente. Non viene eseguito alcuno spostamento silenzioso tra storage portabile e AppData/XDG.
