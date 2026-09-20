# Nexu Port Forwarding 1.0.0

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## Italiano

Prima release stabile di Nexu Port Forwarding dal repository pubblico ricreato.

### Funzioni incluse

Forwarding TCP SSH locale (`-L`) e remoto (`-R`); profili indipendenti; griglia ricercabile e ordinabile; comandi avvia/ferma; indicatori di stato; autenticazione con password o chiave privata; verifica esplicita delle chiavi host; keepalive e riconnessione opzionale con tentativi limitati.

Finestra nativa Windows/Linux con minimizza e massimizza/ripristina. Il dialogo di chiusura offre riduzione, uscita o annullamento e avverte che l’uscita ferma i tunnel attivi. Senza tray viene usata la normale minimizzazione. Dimensioni e posizione normali e stato massimizzato sono salvati localmente.

Vault locale AES-256-GCM protetto da password principale, import/export di backup cifrati, esportazione separata dei profili senza credenziali, log locali con rotazione e directory dati indipendente dalla versione. Non ci sono sincronizzazione cloud o avvio automatico dei tunnel.

### Download

Windows x64: ZIP portabile, installer EXE e MSI. Linux x64: TAR.GZ portabile, DEB e RPM. I pacchetti nativi includono Java 21. Gli archivi solo Java includono JAR e directory `lib/` e richiedono Java 21. Estrarre interamente gli archivi portabili.

Sono allegati ZIP sorgenti, checksum SHA-256, diagnostica per piattaforma e inventario dei JAR runtime risolti. MIT copre il codice applicativo; dipendenze e Java mantengono licenze e avvisi propri.

### Verifica e limiti

La pubblicazione richiede il successo di entrambe le build, test Maven, integrazione SSH loopback, avvio/uscita GUI pacchettizzata, log locali, controlli degli endpoint ritirati e completezza dei pacchetti. La documentazione usa il nome completo **Nexu Port Forwarding**, con inglese primario e italiano in parallelo.

I binari non sono firmati digitalmente. L’interfaccia usa attualmente etichette italiane. La tray dipende dal desktop Linux. Upgrade/disinstallazione degli installer, policy dei server reali, sleep/resume e tutti i flussi interattivi non sono testati esaustivamente. Il verde indica un tunnel stabilito, non lo stato del servizio di destinazione. Le password perse di vault/backup non sono recuperabili. Questa release non costituisce un audit di sicurezza.
