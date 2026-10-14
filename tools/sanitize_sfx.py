# -*- coding: utf-8 -*-
"""
Sanea una muestra de SFX al estandar de `res/raw` de Teika Fusion.

## Por que existe

El 2026-08-29 se rehizo el tic de UI porque las muestras **cortaban en seco** y ese corte ERA el
pop que se oia ("un sonido raro cuando se esta acabando el sonido, como de interferencia"). No era
el mezclador ni SoundPool: un salto brusco a silencio es un pop.

El metodo quedo fijado y es el que aplica este script:

  1. decodificar a PCM (s16le) y medir el PICO;
  2. recortar el silencio de cabeza y cola con un umbral relativo a ese pico;
  3. dejar un poco de aire y aplicar un FUNDIDO a cero al final;
  4. volver a medir **el maximo de los ultimos 5 ms**: si pasa del 1 % del pico, la muestra sigue
     cortando en seco y hara pop.

Salida: OGG mono 44,1 kHz, que es lo que son las 84 muestras del juego.

Uso:
    python tools/sanitize_sfx.py entrada.mp3 app/src/main/res/raw/snd_x.ogg [--gain 1.2]
"""

from __future__ import annotations

import argparse
import array
import math
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

SAMPLE_RATE = 44100
TAIL_MS = 5             # ventana final que se mide
TAIL_LIMIT = 0.010      # 1 % del pico: por encima, corta en seco
HEAD_THRESHOLD = 0.02   # recorte de cabeza, relativo al pico
TAIL_THRESHOLD = 0.010  # recorte de cola, relativo al pico
AIR_MS = 60             # aire que se deja despues del ultimo sonido util
FADE_MS = 60            # fundido final a cero


def decode(path: Path) -> array.array:
    """Decodifica a PCM 16 bits mono 44,1 kHz."""
    out = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(path),
         "-ac", "1", "-ar", str(SAMPLE_RATE), "-f", "s16le", "-"],
        capture_output=True, check=True,
    ).stdout
    pcm = array.array("h")
    pcm.frombytes(out[: len(out) - (len(out) % 2)])
    return pcm


def measure_tail(pcm: array.array) -> tuple[int, int, float]:
    """(pico, maximo de los ultimos TAIL_MS, fraccion). La medida que decide si hay pop."""
    peak = max((abs(v) for v in pcm), default=0)
    n = int(SAMPLE_RATE * TAIL_MS / 1000)
    tail = max((abs(v) for v in pcm[-n:]), default=0) if pcm else 0
    return peak, tail, (tail / peak if peak else 0.0)


def sanitize(pcm: array.array, gain: float, max_ms: int | None = None,
             fade_ms: int = FADE_MS) -> array.array:
    peak = max((abs(v) for v in pcm), default=0)
    if peak == 0:
        raise SystemExit("La muestra esta en silencio.")

    head_lim = peak * HEAD_THRESHOLD
    tail_lim = peak * TAIL_THRESHOLD
    start = 0
    while start < len(pcm) and abs(pcm[start]) < head_lim:
        start += 1
    end = len(pcm) - 1
    while end > start and abs(pcm[end]) < tail_lim:
        end -= 1

    air = int(SAMPLE_RATE * AIR_MS / 1000)
    end = min(len(pcm) - 1, end + air)
    body = array.array("h", pcm[start:end + 1])

    # Tope de duracion. Las 84 muestras del juego viven entre 0,35 y 0,6 s: un cue de un segundo
    # sobre una rotura de linea se emborrona con el golpe del clear. El corte no deja pop porque
    # el fundido de abajo se aplica DESPUES, sobre el nuevo final.
    if max_ms is not None:
        body = array.array("h", body[: int(SAMPLE_RATE * max_ms / 1000)])

    # Fundido coseno al final: termina en CERO EXACTO, que es lo unico que garantiza que no pope.
    fade = min(int(SAMPLE_RATE * fade_ms / 1000), len(body))
    for i in range(fade):
        t = i / max(1, fade - 1)
        w = 0.5 * (1.0 + math.cos(math.pi * t))
        body[len(body) - fade + i] = int(body[len(body) - fade + i] * w)
    body[-1] = 0

    # Normalizacion suave + ganancia pedida, con techo para no recortar.
    cur = max((abs(v) for v in body), default=1)
    scale = min(gain * (0.89 * 32767 / cur), 32767 / cur)
    for i in range(len(body)):
        body[i] = max(-32768, min(32767, int(body[i] * scale)))
    return body


def encode(pcm: array.array, dest: Path) -> None:
    with tempfile.NamedTemporaryFile(suffix=".raw", delete=False) as tmp:
        tmp.write(pcm.tobytes())
        raw = Path(tmp.name)
    try:
        dest.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(SAMPLE_RATE), "-ac", "1",
             "-i", str(raw), "-c:a", "libvorbis", "-q:a", "3", str(dest)],
            check=True,
        )
    finally:
        raw.unlink(missing_ok=True)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("source", type=Path)
    ap.add_argument("dest", type=Path)
    ap.add_argument("--gain", type=float, default=1.0)
    ap.add_argument("--max-ms", type=int, default=None, help="Tope de duracion en ms.")
    ap.add_argument("--fade-ms", type=int, default=FADE_MS, help="Fundido final en ms.")
    args = ap.parse_args()

    original = decode(args.source)
    p0, t0, f0 = measure_tail(original)
    body = sanitize(original, args.gain, args.max_ms, args.fade_ms)
    encode(body, args.dest)

    # Se vuelve a medir sobre el FICHERO YA CODIFICADO: el codec puede reintroducir cola.
    final = decode(args.dest)
    p1, t1, f1 = measure_tail(final)
    dur = len(final) / SAMPLE_RATE
    status = "OK" if f1 <= TAIL_LIMIT else "CORTA EN SECO"
    print(
        f"{args.dest.name}: {dur:.3f}s  {args.dest.stat().st_size} bytes  "
        f"cola antes {f0 * 100:.2f}% -> despues {f1 * 100:.2f}%  [{status}]"
    )
    return 0 if f1 <= TAIL_LIMIT else 1


if __name__ == "__main__":
    sys.exit(main())
