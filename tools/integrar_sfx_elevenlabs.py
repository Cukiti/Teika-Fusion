# -*- coding: utf-8 -*-
"""
Integra en `res/raw` los efectos generados con ElevenLabs el 2026-09-29 para los 34 momentos que quedaron
mudos al retirar Stable Audio Open (decisión del usuario: una sola generación por sonido; 5 se regeneraron
una vez por salir casi vacíos, ver `docs/audio/elevenlabs-2026-09-29-mudos.json`).

Procesado de cada muestra (misma receta para todas):
  1. arranque: se recorta el silencio inicial (el sonido empieza 3 ms antes de pasar el 5 % del pico);
  2. cola: termina donde la envolvente cae 60 dB bajo el pico (+40 ms), con fundido final de coseno del 35 %
     (máx. 250 ms) y fundido inicial de 2 ms contra el clic. Hasta el 2026-09-30 era −45 dB y 60 ms, y
     sonaba a corte («no terminan el efecto»). Las muestras en bucle (LOOPS) no se recortan ni se funden;
  3. nivel: RMS «activo» (muestras por encima del 1 % del pico, la misma medida con la que se midieron los
     sonidos del juego) igualado al de su hermano ya aprobado, con techo de pico de −9 dBFS;
  4. OGG Vorbis estéreo 44,1 kHz (formato del resto de `res/raw`).
Después actualiza `docs/audio/licencias/registro.json` (origen `elevenlabs`, hash nuevo).

Uso:  python tools/integrar_sfx_elevenlabs.py [manifiesto.json]
      Sin argumento, los 34 mudos del 29-09. Con un manifiesto, sus `sonidos` que tengan objetivo en TARGET;
      `carpeta` (relativa al repo) dice dónde están los MP3.
"""
import hashlib
import io
import json
import os
import subprocess
import sys
import shutil
import tempfile
import time

import numpy as np
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "art", "sfx-elevenlabs", "2026-09-29")
RAW = os.path.join(ROOT, "app", "src", "main", "res", "raw")
MANIFEST = os.path.join(ROOT, "docs", "audio", "elevenlabs-2026-09-29-mudos.json")
REGISTRY = os.path.join(ROOT, "docs", "audio", "licencias", "registro.json")

PEAK_CEILING_DB = -9.0
TAIL_DB = -60.0
FADE_OUT_S = 0.25

# Arranque: umbral (fracción del pico) a partir del que empieza el sonido. Por defecto el 5 %. (La Madera de
# romper del 29-09 mañana traía 180 ms de crujidos antes del golpe y empezaba a −12 dB; la muestra de la
# renovación de skins de la tarde ya arranca en el golpe y no lo necesita.)
START_THRESHOLD = {}

# RMS activo objetivo (dBFS) = el del hermano aprobado que suena en el mismo contexto.
TARGET = {
    # Skins: colocar ≈ Clásica/Cerámica (−26,0 / −28,5); romper ≈ Clásica/Cerámica (−26,9 / −33,0).
    **{f"snd_place_{s}": -27.0 for s in ("crystal", "ice", "lava", "metal", "spectre", "void", "constellation", "wood")},
    **{f"snd_clear_{s}": -30.0 for s in ("crystal", "ice", "lava", "metal", "spectre", "void", "constellation", "wood")},
    # P-411 (2026-09-30): Neón y Dorado, al nivel del resto de bloques.
    "snd_clear_neon": -30.0, "snd_place_gold": -27.0, "snd_clear_gold": -30.0,
    "snd_teika_seed_break": -33.0,   # snd_teika_appear
    "snd_mega_collapse": -26.0,      # snd_mega
    "snd_intro_sting": -26.0,        # firma de la intro «CUKITI» (2026-09-30), al nivel de snd_mega
    "snd_ability_ready": -31.0,      # snd_combo
    "snd_tray_reward": -26.0,        # snd_target_collect
    "snd_king_piece_land": -30.0,    # snd_king_wall_break (golpe del Trono)
    "snd_king_piece_evict": -30.0,
    "snd_kintsugi_seal": -30.0,      # snd_ninja_repair
    "snd_lead_crush": -30.0,         # snd_king_wall_break
    "snd_mirror_stamp": -29.5,       # snd_ghost_fog
    "snd_mold_stamp": -27.0,         # colocar
    "snd_plant_rescue": -33.0,       # snd_vine_hurt
    "snd_poison_splash": -29.0,      # snd_toxic_spread
    "snd_poison_wash": -29.0,
    "snd_rock_crack": -33.0,         # snd_crystal_crack
    "snd_star_retint": -29.0,        # entre snd_target_collect y los brillos suaves
    "snd_stubborn_break": -30.0,
    "snd_stubborn_resist": -29.0,
    "snd_vine_sprout": -32.0,        # entre snd_vine_grab y snd_vine_hurt
    # Rework 2026-09-29 (docs/audio/elevenlabs-2026-09-29-rework.json)
    "snd_game_over": -25.0,          # jingle de derrota: momento clave, como snd_level_victory/snd_target_collect
    "snd_iris_transition": -30.0,    # transición: presente pero por debajo de la acción
    # Habilidades renovadas 2026-09-29 (docs/audio/elevenlabs-2026-09-29-habilidades.json): el nivel del
    # sonido al que sustituyen, para no mover el balance del juego. Tajo, Miasma y Eclipse no se tocan.
    "snd_ability_shield": -28.4,
    "snd_ability_dream": -30.8,
    "snd_ability_slide": -24.1,
    "snd_ability_bloom": -26.5,
    "snd_ability_energize": -23.0,
    "snd_ability_fire": -24.2,
    "snd_ability_icicle": -27.2,
    "snd_ability_ghost": -26.0,
    "snd_ability_feast": -25.9,
    "snd_ability_wish": -27.6,
    "snd_ability_birth": -30.1,
    # Skins renovadas 2026-09-29 (docs/audio/elevenlabs-2026-09-29-skins.json): las 8 de arriba conservan su
    # objetivo; Básico y Cerámica, el nivel de la muestra a la que sustituyen.
    "snd_place_classic": -26.0,
    "snd_clear_classic": -26.9,
    "snd_place_ceramic": -28.5,
    "snd_clear_ceramic": -33.0,
    # Tablero y Teikas renovados 2026-09-29 (docs/audio/elevenlabs-2026-09-29-tablero.json): el nivel de la
    # muestra a la que sustituyen. Los que ya tenían objetivo arriba (plomo, molde, testarudo, bloque-Teika,
    # derrumbe del MEGA, premio de bandeja) lo conservan.
    "snd_combo": -31.1,
    "snd_combo_grace": -29.9,
    "snd_hot_cell": -30.6,
    "snd_invalid": -21.0,        # antes −25,6: «no se escucha casi» (2026-09-30)
    "snd_mega": -26.0,
    "snd_merge_2": -17.6,
    "snd_perfect_clear": -26.6,
    "snd_pure_line": -33.6,
    "snd_target_collect": -26.1,
    "snd_teika_appear": -33.0,
    # Interfaz y progreso renovados 2026-09-29 (docs/audio/elevenlabs-2026-09-29-interfaz.json): el nivel de la
    # muestra a la que sustituyen (derrota y círculo ya tenían objetivo arriba).
    "snd_character_unlock": -29.5,
    "snd_level_victory": -20.7,
    "snd_life_lost": -27.1,
    "snd_mural_complete": -24.1,
    "snd_no_lives": -23.7,
    "snd_play_launch": -26.0,    # antes −32,9: «no suena casi» (2026-09-30)
    "snd_purchase": -31.7,
    "snd_rank_up": -25.9,
    "snd_streak": -29.0,
    "snd_teikoin_gain": -29.9,
    "snd_ui_click": -28.4,
    # Mecánicas de bioma renovadas 2026-09-29 (docs/audio/elevenlabs-2026-09-29-biomas.json): el nivel de la
    # muestra a la que sustituyen (las que ya tenían objetivo arriba lo conservan).
    "snd_astro_emerge": -29.7,
    "snd_coil_charge": -27.3,
    "snd_coil_discharge": -29.4,
    "snd_coil_short": -26.3,
    "snd_crystal_crack": -32.7,
    "snd_crystal_shatter": -29.2,
    "snd_fear_scare": -27.6,
    "snd_galactic_cancel": -25.1,
    "snd_galactic_collapse": -21.5,
    "snd_galactic_warn": -29.9,
    "snd_ghost_fog": -29.5,
    "snd_ghost_hunt": -26.2,
    "snd_glacier_freeze": -27.8,
    "snd_glacier_thaw": -38.3,
    "snd_glutton_bite": -24.1,
    "snd_glutton_open": -28.0,
    "snd_glutton_retreat": -31.7,
    "snd_king_wall_break": -30.0,
    "snd_king_wall_rise": -27.0,
    "snd_king_warning": -27.1,
    "snd_lava_retreat": -28.4,
    "snd_lava_surge": -26.0,
    "snd_moon_steal": -31.5,
    "snd_ninja_repair": -30.1,
    "snd_ninja_slash": -27.2,
    "snd_sleep_drowsy": -29.0,
    "snd_sleep_wake": -31.1,
    "snd_sun_strike": -31.2,
    "snd_toxic_spread": -29.3,
    "snd_vine_grab": -35.2,
    "snd_vine_hurt": -33.3,
    # Mejoras 2026-09-30 (docs/audio/elevenlabs-2026-09-30-mejoras.json): los que no tenían objetivo, el de su
    # muestra anterior; «Jugada no válida» y «Botón JUGAR» suben porque el usuario casi no los oía.
    "snd_ability_miasma": -28.3,
    "snd_barrel_blast": -24.2,
    "snd_egg_hatch": -30.1,
    "snd_magnet_place": -23.0,
}


# Sonidos muy picudos (cresta ≥ 15 dB) que con el techo de pico quedaban hasta 8 dB por debajo del sonido al
# que sustituyen: en vez de bajar la muestra entera, un limitador (ataque 1 ms, suelta 60 ms) recorta los picos
# hasta LIMIT_MAX_DB para acercarse al objetivo. Solo habilidades, skins, tablero, interfaz y biomas renovados el
# 2026-09-29 (lo que se escuchó ya con limitador al elegir).
TABLERO = {"snd_combo", "snd_combo_grace", "snd_hot_cell", "snd_invalid", "snd_lead_crush", "snd_mold_stamp",
           "snd_perfect_clear", "snd_pure_line", "snd_stubborn_break", "snd_stubborn_resist", "snd_target_collect",
           "snd_tray_reward", "snd_merge_2", "snd_teika_appear", "snd_mega", "snd_mega_collapse",
           "snd_teika_seed_break"}
INTERFAZ = {"snd_character_unlock", "snd_game_over", "snd_iris_transition", "snd_level_victory", "snd_life_lost",
            "snd_mural_complete", "snd_no_lives", "snd_play_launch", "snd_purchase", "snd_rank_up", "snd_streak",
            "snd_teikoin_gain", "snd_ui_click"}
BIOMAS = {"snd_" + k for k in (
    "astro_emerge coil_charge coil_discharge coil_short crystal_crack crystal_shatter fear_scare galactic_cancel "
    "galactic_collapse galactic_warn ghost_fog ghost_hunt glacier_freeze glacier_thaw glutton_bite glutton_open "
    "glutton_retreat king_piece_evict king_piece_land king_wall_break king_wall_rise king_warning kintsugi_seal "
    "lava_retreat lava_surge mirror_stamp moon_steal ninja_repair ninja_slash plant_rescue poison_splash poison_wash "
    "rock_crack sleep_drowsy sleep_wake star_retint sun_strike toxic_spread vine_grab vine_hurt vine_sprout").split()}
LIMIT = {k for k in TARGET if k.startswith(("snd_ability_", "snd_place_", "snd_clear_")) or k in TABLERO or k in INTERFAZ or k in BIOMAS
         or k in {"snd_barrel_blast", "snd_egg_hatch", "snd_magnet_place"}}
LIMIT_MAX_DB = 6.0
# Muestras que suenan en bucle (SoundPool loop = -1): se procesan sin recorte ni fundidos.
LOOPS = {"snd_galactic_warn"}


def limit(y, sr, ceiling):
    """Limitador de picos: ganancia = min(1, techo/|x|), con ataque instantáneo y suelta exponencial."""
    a = np.abs(y).max(1)
    g = np.minimum(1.0, ceiling / np.maximum(a, 1e-12))
    rel = np.exp(-1.0 / (0.060 * sr))
    out = np.empty_like(g)
    cur = 1.0
    for i, gi in enumerate(g):
        cur = gi if gi < cur else gi + (cur - gi) * rel
        out[i] = cur
    # ataque de 1 ms: la reducción empieza un poco antes del pico
    k = max(1, int(0.001 * sr))
    out = np.minimum.accumulate(np.lib.stride_tricks.sliding_window_view(np.pad(out, (0, k - 1), constant_values=1.0), k), axis=1)[:, -1]
    return y * out[:, None]


def decode(path):
    wav = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ar", "44100", "-ac", "2", "-f", "wav", "-"],
                         capture_output=True, check=True).stdout
    x, sr = sf.read(io.BytesIO(wav), dtype="float64")
    return x, sr


def active_rms_db(mono):
    a = np.abs(mono)
    act = mono[a > a.max() * 0.01]
    return 20 * np.log10(np.sqrt((act ** 2).mean()) + 1e-12)


def process(x, sr, target_db, start_threshold=0.05, limited=False, loop=False):
    if loop:
        # Muestra en bucle (la mira del Espacio): ni recorte ni fundidos, que abrirían un hueco en la costura.
        y = x.copy()
        y *= 10 ** ((target_db - active_rms_db(y.mean(1))) / 20)
        pk = 20 * np.log10(np.abs(y).max())
        if pk > PEAK_CEILING_DB:
            y *= 10 ** ((PEAK_CEILING_DB - pk) / 20)
        return y, 0.0, len(y) / sr
    mono = x.mean(1)
    a = np.abs(mono)
    pk = a.max()
    start = max(0, int(np.argmax(a > pk * start_threshold)) - int(0.003 * sr))
    win = max(1, int(0.02 * sr))
    env = np.convolve(a, np.ones(win) / win, "same")
    # Cola hasta −60 dB (+40 ms) y desvanecido de coseno de hasta 250 ms. Antes era −45 dB y 60 ms, y el
    # usuario lo oyó (2026-09-30): «casi todos tienen un corte raro al final, no terminan el efecto».
    alive = np.where(env > pk * 10 ** (TAIL_DB / 20))[0]
    end = min(len(mono), (alive[-1] if len(alive) else len(mono) - 1) + int(0.040 * sr))
    y = x[start:end].copy()
    n = len(y)
    fade_out = min(int(FADE_OUT_S * sr), int(n * 0.35))
    y[n - fade_out:] *= (0.5 + 0.5 * np.cos(np.linspace(0, np.pi, fade_out)))[:, None]
    fade_in = int(0.002 * sr)
    y[:fade_in] *= np.linspace(0, 1, fade_in)[:, None]
    gain = target_db - active_rms_db(y.mean(1))
    y *= 10 ** (gain / 20)
    if limited:
        over = 20 * np.log10(np.abs(y).max()) - PEAK_CEILING_DB
        if over > 0:
            # hasta LIMIT_MAX_DB de reducción; lo que sobre lo quita el techo de abajo
            y = limit(y * 10 ** (-max(0.0, over - LIMIT_MAX_DB) / 20), sr, 10 ** (PEAK_CEILING_DB / 20))
    peak_db = 20 * np.log10(np.abs(y).max())
    if peak_db > PEAK_CEILING_DB:
        y *= 10 ** ((PEAK_CEILING_DB - peak_db) / 20)
    return y, start / sr, n / sr


def main():
    manifest_path = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else MANIFEST
    doc = json.load(open(manifest_path, encoding="utf-8"))
    src = os.path.join(ROOT, doc["carpeta"]) if "carpeta" in doc else SRC
    evidence = os.path.relpath(manifest_path, ROOT).replace(os.sep, "/")
    src_rel = os.path.relpath(src, ROOT).replace(os.sep, "/")
    manifest = {s["archivo"]: s for s in doc["sonidos"] if s["archivo"] in TARGET}
    reg = json.load(open(REGISTRY, encoding="utf-8"))
    entries = {e["archivo"]: e for e in reg["entradas"]}
    print(f"{'sonido':26}{'recorte ini':>12}{'dur final':>10}{'rms':>7}{'pico':>7}")
    for name in sorted(manifest):
        mp3s = [f for f in os.listdir(os.path.join(src, name)) if f.endswith(".mp3")]
        assert len(mp3s) == 1, (name, mp3s)
        x, sr = decode(os.path.join(src, name, mp3s[0]))
        y, cut, dur = process(x, sr, TARGET[name], START_THRESHOLD.get(name, 0.05), limited=name in LIMIT,
                              loop=name in LOOPS)
        out = os.path.join(RAW, name + ".ogg")
        with tempfile.TemporaryDirectory() as tmp:
            wav = os.path.join(tmp, "a.wav")
            sf.write(wav, y, sr, subtype="PCM_24")
            enc = os.path.join(tmp, "a.ogg")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "6", enc], check=True)
            # Windows: el indexador de Android Studio o el antivirus bloquean a veces res/raw un instante y
            # ffmpeg fallaba con «Invalid argument» al abrir la salida. Se codifica fuera y se reintenta la copia.
            for intento in range(20):
                try:
                    shutil.copyfile(enc, out)
                    break
                except OSError:
                    if intento == 19:
                        raise
                    time.sleep(0.5)
        mono = y.mean(1)
        print(f"{name:26}{cut * 1000:10.0f}ms{dur:9.2f}s{active_rms_db(mono):7.1f}{20 * np.log10(np.abs(y).max()):7.1f}")
        m = manifest[name]
        entries[name + ".ogg"] = {
            "archivo": name + ".ogg",
            "sha256": hashlib.sha256(open(out, "rb").read()).hexdigest(),
            "origen": "elevenlabs",
            "licencia": doc.get("licencia", "ElevenLabs plan de pago (Starter anual activo al generar, 2026-09-29)"),
            "modelo": m.get("herramienta", "text_to_sound_effects (modelo por defecto de la API, vía MCP)"),
            "generado": doc.get("fecha", "2026-09-29"),
            "evidencia": evidence,
            "prompt": m["prompt"],
            "original": f"{src_rel}/{name}/{mp3s[0]}",
            "procesado": f"tools/integrar_sfx_elevenlabs.py: arranque, cola {TAIL_DB:g} dB + fundido de coseno ≤ {FADE_OUT_S * 1000:g} ms, RMS activo {TARGET[name]} dBFS, "
                         f"{'limitador ≤ %g dB, ' % LIMIT_MAX_DB if name in LIMIT else ''}techo {PEAK_CEILING_DB} dBFS → OGG Vorbis estéreo 44,1 kHz",
            "estado": "OK",
        }
    reg["entradas"] = sorted(entries.values(), key=lambda e: e["archivo"])
    reg["actualizado"] = doc.get("fecha", "2026-09-29")
    with open(REGISTRY, "w", encoding="utf-8", newline="") as f:
        f.write(json.dumps(reg, ensure_ascii=False, indent=1))
    print(f"{len(manifest)} integrados; registro: {len(reg['entradas'])} entradas")


if __name__ == "__main__":
    main()
