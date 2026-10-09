# Nexu Port Forwarding 1.2.2

[English](RELEASE-NOTES.md) | [Italiano](RELEASE-NOTES.it.md)

## Italiano

Release stabile 1.2.2: ricerca unificata con più parole e filtro combinato LOCAL/REMOTE.

### Modifiche rispetto alla 1.2.1

- Sostituiti i filtri testuali separati per installazione, nome e hostname/IP con un'unica casella di ricerca.
- Ogni parola separata da spazi viene cercata come sottostringa senza distinzione tra maiuscole e minuscole. **Tutte** le parole devono corrispondere, anche in **campi diversi**. Ad esempio, `prato 8687` trova un profilo di Prato con porta di ascolto 8687, indipendentemente dall'ordine.
- La ricerca comprende UUID, nome/titolo, note/descrizione, installazione, origine, tipo di forwarding, hostname SSH, IP SSH risolto, username, hostname e IP di ascolto/destinazione/proxy, relative porte e ID di importazione. Password e percorsi delle chiavi private non vengono indicizzati.
- I risultati si aggiornano quando la risoluzione asincrona dell'hostname SSH fornisce l'indirizzo IP.
- Nel menu dei tipi compare **LOCAL e REMOTE (-L / -R)**, oltre a Tutti i tipi, LOCAL, REMOTE e DYNAMIC.
- Restano combinabili i tab di origine e il filtro per stato della connessione.
- Aggiunti test di regressione per ricerca su campi diversi, sottostringhe, IP, porte, ID, ricerca vuota e combinazioni di tipi.

### Sicurezza

La ricerca e i filtri non avviano connessioni SSH. Le credenziali salvate non vengono indicizzate. I pacchetti non sono firmati: verificare i checksum scaricati e le impostazioni del server SSH prima dell'uso in produzione.
