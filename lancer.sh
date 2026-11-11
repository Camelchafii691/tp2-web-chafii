#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null || true)
  export JAVA_HOME
fi
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
  echo 'Installe le JDK 25 et configure JAVA_HOME.' >&2
  exit 1
fi
if [ -z "${MVN_BIN:-}" ]; then
  if command -v mvn >/dev/null 2>&1; then
    MVN_BIN=$(command -v mvn)
  elif [ -x '/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn' ]; then
    MVN_BIN='/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn'
  else
    echo 'Installe Maven ou indique son exécutable dans MVN_BIN.' >&2
    exit 1
  fi
fi
"$MVN_BIN" -B verify
if [ "${1:-}" = '--verifier' ]; then exit 0; fi

TP2_PORT="${TP2_PORT:-18084}"
if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"$TP2_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Le port $TP2_PORT est déjà utilisé. Choisis-en un autre avec TP2_PORT." >&2
  exit 1
fi
if [ -z "${GEMINI_API_KEY:-${GEMINI_KEY:-}}" ]; then
  if [ ! -t 0 ]; then
    echo 'Configure GEMINI_API_KEY ou lance ce script dans un terminal pour saisir la clé.' >&2
    exit 1
  fi
  printf 'Clé Gemini (saisie masquée) : '
  IFS= read -r -s GEMINI_API_KEY
  printf '\n'
  export GEMINI_API_KEY
fi
if [ -z "${GEMINI_API_KEY:-${GEMINI_KEY:-}}" ]; then
  echo 'La clé Gemini est vide.' >&2
  exit 1
fi
PAYARA_MICRO_JAR="${PAYARA_MICRO_JAR:-target/runtime/payara-micro-7.2026.11.jar}"
if [ ! -f "$PAYARA_MICRO_JAR" ]; then
  mkdir -p "$(dirname "$PAYARA_MICRO_JAR")"
  curl --fail --location --retry 2 \
    https://repo.maven.apache.org/maven2/fish/payara/extras/payara-micro/7.2026.11/payara-micro-7.2026.11.jar \
    -o "$PAYARA_MICRO_JAR.part"
  mv "$PAYARA_MICRO_JAR.part" "$PAYARA_MICRO_JAR"
fi
printf 'Après le démarrage du serveur, ouvre http://localhost:%s/tp2-web-chafii/\n' "$TP2_PORT"
printf 'Pour arrêter le serveur : Ctrl+C.\n'
exec "$JAVA_BIN" -Xmx768m -jar "$PAYARA_MICRO_JAR" \
  --deploy target/tp2-web-chafii.war --contextroot tp2-web-chafii \
  --port "$TP2_PORT" --nohazelcast --rootdir "$PWD/target/payara-runtime"
