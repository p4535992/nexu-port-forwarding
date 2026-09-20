#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
JAR="$ROOT/target/app/nexu-port-forwarding.jar"
[[ -f "$JAR" ]] || { echo 'Eseguire prima ./build.sh (JDK 21 e Maven 3.9+).' >&2; exit 1; }
JAVA=java
if [[ -n "${JAVA_HOME:-}" ]]; then JAVA="$JAVA_HOME/bin/java"; fi
exec "$JAVA" -jar "$JAR" "$@"
