#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────────────────────────
# LA CAMPAÑA DEL BOT (2026-09-26): juega los 14 capítulos, uno por JVM, y junta un solo informe.
#
#   tools/bot_campana.sh [partidas_por_nivel=2] [carpeta=app/build/reports/level-bot/campana]
#
# - Un capítulo por tanda, con `gradlew --stop` antes y `--no-daemon` (un `--stop` de otra sesión
#   mató la primera campaña, 2026-09-26): una JVM de Robolectric que juega
#   cientos de partidas seguidas acaba sin memoria (la campaña del 2026-09-22 se cortó así).
# - REANUDABLE: un capítulo cuyo `levels.csv` ya existe se salta. Para repetir uno, borra su carpeta.
# - Al final, `LEVEL_BOT_MERGE` junta todos los capítulos en `<carpeta>/resumen.md`.
# - `LEVEL_BOT_BASE=<levels.csv viejo>` (variable de entorno) añade la columna «Antes» al informe.
#
# Ritmo orientativo (2026-09-28): ~1 s por partida; 2 partidas × 286 niveles ≈ 15–20 min.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
set -u
RUNS="${1:-2}"
ROOT="${2:-app/build/reports/level-bot/campana}"
cd "$(dirname "$0")/.." || exit 1
mkdir -p "$ROOT"
# En Windows (Git Bash) el envoltorio es gradlew.bat; en el resto, gradlew.
GRADLE=./gradlew
[ -f ./gradlew.bat ] && case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) GRADLE=./gradlew.bat ;; esac

for c in $(seq 1 14); do
  out="$ROOT/c$(printf %02d "$c")"
  if [ -f "$out/levels.csv" ]; then
    echo "capítulo $c: ya jugado, se salta ($out)"
    continue
  fi
  mkdir -p "$out"
  echo "capítulo $c: jugando ($RUNS partidas por nivel)… $(date +%H:%M:%S)"
  $GRADLE --stop > /dev/null 2>&1
  # La ruta de salida, ABSOLUTA: el test corre con el directorio de trabajo en app/.
  LEVEL_BOT="c$c" LEVEL_BOT_RUNS="$RUNS" LEVEL_BOT_OUT="$(pwd)/$out" \
    $GRADLE --no-daemon :app:testDebugUnitTest --tests "*LevelBotTest.el bot juega*" > "$out/gradle.log" 2>&1
  if [ -f "$out/levels.csv" ]; then
    echo "capítulo $c: hecho — $(($(wc -l < "$out/levels.csv") - 1)) partidas, $(($(wc -l < "$out/anomalias.csv") - 1)) anomalías"
  else
    echo "capítulo $c: FALLÓ (mira $out/gradle.log). Se sigue con el siguiente; vuelve a lanzar el script para repetirlo."
  fi
done

echo "Juntando el informe… $(date +%H:%M:%S)"
$GRADLE --stop > /dev/null 2>&1
LEVEL_BOT_MERGE="$(pwd)/$ROOT" $GRADLE --no-daemon :app:testDebugUnitTest --tests "*LevelBotTest.informe de la campana" > "$ROOT/merge.log" 2>&1
echo "Informe: $ROOT/resumen.md"
