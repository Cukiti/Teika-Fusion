# -*- coding: utf-8 -*-
"""
⛔ RETIRADO 2026-09-29: el usuario sacó del juego todo Stable Audio Open y `AudioLicenseRegistryTest` ya no
admite el origen. Se conserva como referencia del procesado (recorte, aire, fundido, RMS).

Integra en `res/raw` los sonidos generados con Stable Audio Open en Colab (2026-09-28).

Entrada: `art/sfx-nuevos/colab/sonidos/<nombre>_v<N>.wav` y `art/sfx-nuevos/colab/eleccion.json`
(la variante elegida de cada uno). Para cada sonido:

  1. `sanitize_sfx.sanitize`: recorte de cabeza y cola, aire, fundido coseno a cero (sin pop).
  2. **Mismo volumen que el que sustituye**: se iguala el RMS de la parte útil al del `.ogg` viejo
     (con techo de pico para no recortar). Los sonidos nuevos se igualan a una referencia de bioma.
  3. OGG Vorbis mono 44,1 kHz, como el resto de muestras.
  4. Se registra en `docs/audio/licencias/registro.json` (origen, licencia, prompt, semilla, hash).

Uso:  python tools/integrar_sfx_colab.py
"""
from __future__ import annotations

import array
import hashlib
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import sanitize_sfx as S  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "app/src/main/res/raw"
SRC = ROOT / "art/sfx-nuevos/colab/sonidos"
PICK = ROOT / "art/sfx-nuevos/colab/eleccion.json"
REG = ROOT / "docs/audio/licencias/registro.json"

sys.path.insert(0, str(ROOT / "tools/colab"))
import gen_cuaderno  # noqa: E402  (la lista de prompts y duraciones: una sola fuente)

PROMPTS = {n: (i, d, p) for i, (n, d, p) in enumerate(gen_cuaderno.SONIDOS, 1)}

# Sonidos nuevos (no sustituyen a ninguno): nombre en res/raw.
NUEVOS = {
    "poison_wash": "snd_poison_wash", "poison_splash": "snd_poison_splash",
    "king_piece_land": "snd_king_piece_land", "king_piece_evict": "snd_king_piece_evict",
    "rock_crack": "snd_rock_crack", "lead_crush": "snd_lead_crush",
    "ability_ready": "snd_ability_ready", "tray_reward": "snd_tray_reward",
    "vine_sprout": "snd_vine_sprout", "kintsugi_seal": "snd_kintsugi_seal",
    "stubborn_resist": "snd_stubborn_resist", "stubborn_break": "snd_stubborn_break",
    "mold_stamp": "snd_mold_stamp", "mirror_stamp": "snd_mirror_stamp",
    "plant_rescue": "snd_plant_rescue", "star_retint": "snd_star_retint",
}
# Referencia de volumen para los nuevos: un efecto de bioma de la misma familia.
REFERENCIA_NUEVOS = "snd_toxic_spread"

# Tope de duración (ms): las fichas se colocan en cada jugada, tienen que ser secas.
def tope_ms(nombre: str) -> int:
    if nombre.startswith("snd_place_") or nombre in ("mold_stamp", "stubborn_resist", "ability_ready"):
        return 350
    if nombre.startswith("snd_clear_"):
        return 750
    if nombre == "snd_mega_collapse":
        return 2200
    return 1000


def rms_util(pcm: array.array) -> float:
    """RMS de la parte con sonido (por encima del 5 % del pico): el silencio no cuenta."""
    pk = max((abs(v) for v in pcm), default=0)
    if pk == 0:
        return 0.0
    util = [v for v in pcm if abs(v) > pk * 0.05]
    return math.sqrt(sum(v * v for v in util) / max(1, len(util)))


def igualar(body: array.array, objetivo: float) -> tuple[array.array, float]:
    cur = rms_util(body)
    pk = max((abs(v) for v in body), default=1)
    factor = min(objetivo / cur if cur else 1.0, 0.95 * 32767 / pk)
    return array.array("h", (max(-32768, min(32767, int(v * factor))) for v in body)), factor


def main() -> int:
    eleccion = json.loads(PICK.read_text(encoding="utf-8"))
    reg = json.loads(REG.read_text(encoding="utf-8"))
    por_archivo = {e["archivo"]: e for e in reg["entradas"]}
    ref_nuevos = rms_util(S.decode(RAW / f"{REFERENCIA_NUEVOS}.ogg"))
    fallos = 0
    for base, var in sorted(eleccion.items()):
        base = base.replace("sonidos\\", "").replace("sonidos/", "")
        destino_nombre = NUEVOS.get(base, base)
        fuente = SRC / f"{base}_v{var}.wav"
        destino = RAW / f"{destino_nombre}.ogg"
        viejo = RAW / f"{destino_nombre}.ogg"
        objetivo = rms_util(S.decode(viejo)) if viejo.exists() and base not in NUEVOS else ref_nuevos
        body = S.sanitize(S.decode(fuente), 1.0, tope_ms(base))
        body, factor = igualar(body, objetivo)
        S.encode(body, destino)
        final = S.decode(destino)
        _, _, cola = S.measure_tail(final)
        ok = cola <= S.TAIL_LIMIT
        fallos += 0 if ok else 1
        print(f"{destino.name:30s} {len(final) / S.SAMPLE_RATE:5.2f}s  ×{factor:4.2f}  "
              f"{'OK' if ok else 'CORTA EN SECO'}")
        i, dur, prompt = PROMPTS[base]
        por_archivo[destino.name] = {
            "archivo": destino.name,
            "sha256": hashlib.sha256(destino.read_bytes()).hexdigest(),
            "origen": "stable-audio-open",
            "licencia": "Stability AI Community License (registrada 2026-09-28; < 1 M USD/año)",
            "modelo": "stabilityai/stable-audio-open-1.0 (diffusers, Colab T4)",
            "prompt": prompt,
            "semilla": i,
            "variante": int(var),
            "generado": "2026-09-28",
            "procesado": "tools/integrar_sfx_colab.py: sanitize_sfx (recorte, aire, fundido) + RMS igualado "
                         f"({'al sonido sustituido' if base not in NUEVOS else REFERENCIA_NUEVOS}) → OGG mono 44,1 kHz",
            "estado": "OK",
        }
    reg["entradas"] = sorted(por_archivo.values(), key=lambda e: e["archivo"])
    reg["actualizado"] = "2026-09-28"
    REG.write_text(json.dumps(reg, ensure_ascii=False, indent=1), encoding="utf-8")
    print("Registro actualizado." + (f" {fallos} con corte en seco." if fallos else ""))
    return 1 if fallos else 0


if __name__ == "__main__":
    sys.exit(main())
