# Nexu Port Forwarding: dependencies and provenance

The Nexu Port Forwarding application code and documentation are licensed under the MIT License in [LICENSE](LICENSE). Third-party components are not relicensed by that grant. The application PNG/ICO was created for this project; no signing-engine code, signing assets or font files are supplied as project sources.

Direct runtime dependencies declared in `pom.xml`: OpenJFX Controls 21.0.11; Dorkbox SystemTray 4.2.1; Apache MINA SSHD core 2.19.0; Bouncy Castle bcpkix-jdk18on 1.84; EdDSA 0.3.0; SLF4J API and Simple 2.0.17. JUnit Jupiter 5.12.2 is test-only. Maven resolves additional transitive libraries.

Every distribution keeps its dependency JARs intact, including their embedded license, copyright and NOTICE resources. `dependency-inventory.json` records the actual distributed JAR names, sizes, SHA-256 digests and embedded legal-resource paths. Those legal resources are also copied under `legal/dependencies/` for convenience. An empty embedded-resource list is not a declaration that an artifact has no license.

Native packages retain the included Java runtime's legal directory. The application MIT license does not replace Java/OpenJFX licenses or the terms of native components included by dependency JARs. Consult the exact distributed legal resources and upstream source for applicable redistribution conditions. This inventory is not an exhaustive legal or security audit.

Sources and license information for the main components:

- OpenJFX: https://github.com/openjdk/jfx21u (version-specific source and legal resources).
- OpenJDK: https://openjdk.org/legal/gplv2+ce.html; the bundled runtime's `release` and `legal/` files identify its build and notices.
- Dorkbox SystemTray: https://github.com/dorkbox/SystemTray.
- Apache MINA SSHD: https://mina.apache.org/sshd-project/.
- Bouncy Castle: https://www.bouncycastle.org/about/license/.
- EdDSA: https://github.com/str4d/ed25519-java.
- SLF4J: https://www.slf4j.org/license.html.

## Italiano

Codice applicativo e documentazione di Nexu Port Forwarding sono MIT; dipendenze, componenti nativi e runtime Java mantengono le proprie licenze. I JAR restano intatti, con gli avvisi inclusi, copiati anche in `legal/dependencies/`. `dependency-inventory.json` elenca i JAR effettivamente distribuiti e i relativi checksum. La directory legale del runtime è conservata. Una lista vuota di avvisi incorporati non significa assenza di licenza. Questo inventario non è un audit esaustivo di licenze o sicurezza.
