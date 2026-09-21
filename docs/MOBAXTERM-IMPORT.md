# MobaXterm forwarding import

[English](MOBAXTERM-IMPORT.md) | [Italiano](MOBAXTERM-IMPORT.it.md)

Nexu Port Forwarding imports **only** the `[PortForwarding]` section of a user-selected `MobaXterm.ini` or `.mobaconf` file. MobaXterm documentation states that configuration is stored in `MobaXterm.ini`, normally under Documents/MobaXterm for installed editions, beside the portable executable for portable editions, or under `%APPDATA%\MobaXterm` in some versions. The importer suggests common user paths but always lets the user browse explicitly.

Each supported `Local`, `Remote` or `Dynamic` entry becomes one stopped local Nexu Port Forwarding profile. The SSH user, host, SSH port, listening address/port, target (when applicable) and an explicitly configured private-key path are copied. Password sections are never parsed. Proxy-configured entries are skipped rather than changed into direct connections.

The importer shows a preview. Identical source fingerprints are not imported twice. After import, profiles are persisted in Nexu Port Forwarding's own `data/profiles.properties`; MobaXterm is no longer consulted for those rows.

MobaXterm's INI forwarding format is not a public stable interchange standard, so this prerelease deliberately accepts only the documented/observed semicolon layout used by current `[PortForwarding]` entries and reports unrecognized rows instead of guessing.
