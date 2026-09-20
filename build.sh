#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
command -v mvn >/dev/null || { echo 'Installare Maven 3.9+ e JDK 21.' >&2; exit 1; }
mvn -B clean verify
