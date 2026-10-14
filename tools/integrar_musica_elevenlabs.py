# -*- coding: utf-8 -*-
"""
Integra en `res/raw` las 12 músicas de bioma generadas con ElevenLabs Music (music_v2, 180 s, instrumental) el
2026-09-29: un género y un instrumento principal por bioma (tabla del usuario del 28-09), energía contenida.
Prompts en `docs/audio/elevenlabs-2026-09-29-musicas.json`.

Receta de bucle (la misma para las 12):
  1. cuerpo: se quitan la intro floja y el fundido final. Con el nivel en ventanas de 0,5 s, el cuerpo va del
     primer instante al último en que el nivel está a menos de 4 dB de la mediana;
  2. nivel: −14,1 LUFS integrados (el de las músicas del juego) y limitador a −2,5 dB (la codificación Vorbis
     sube el pico hasta ~1 dB), con compensación de su retardo. Va ANTES de la costura: aplicado después,
     el retardo desplazaba el audio y rompía la continuidad;
  3. costura: se busca en la segunda mitad del cuerpo el punto b cuyo audio siguiente más se parece al inicio (espectro
     por bandas en ventanas de 8 s, afinado a la muestra) y el bucle es [0, b). Los primeros 80 ms se funden
     (lineal) con lo que seguía a b. (Antes: fundido de 4 s del final sobre el inicio, que sonaba «doble» al arrancar
     porque mezclaba dos pasajes sin alinear.)
  4. OGG Vorbis estéreo 44,1 kHz, ~160 kbps nominales (formato de bgm_crystal/bgm_sleep).
Después actualiza `docs/audio/licencias/registro.json` (origen `elevenlabs`, hash nuevo).

Uso:  python tools/integrar_musica_elevenlabs.py [manifiesto.json]
      Sin argumento, la tanda del 29-09 (`elevenlabs-2026-09-29-musicas.json`). El manifiesto puede traer
      `licencia` y `modelo` (si no, los de la tanda Starter) y
      `carpeta` (origen de los MP3, relativa al repo); si no, `art/musica-elevenlabs/2026-09-29`.
"""
import hashlib
import io
import json
import os
import subprocess
import sys
import tempfile

import numpy as np
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "art", "musica-elevenlabs", "2026-09-29")
RAW = os.path.join(ROOT, "app", "src", "main", "res", "raw")
MANIFEST = os.path.join(ROOT, "docs", "audio", "elevenlabs-2026-09-29-musicas.json")
REGISTRY = os.path.join(ROOT, "docs", "audio", "licencias", "registro.json")

TARGET_LUFS = -14.1
# −2,5 y no −1,5: la codificación Vorbis sube el pico hasta ~1 dB sobre el del limitador (Estelar llegó a −0,4).
TRUE_PEAK = -2.5
XFADE_S = 0.08
# Búsqueda del punto de bucle (ver loop_point).
HOP = 1024
BANDS = 24
MATCH_S = 8.0
# El bucle debe durar al menos MIN_LOOP_FRAC del cuerpo (se busca en toda su segunda mitad).
MIN_LOOP_FRAC = 0.5
# De las candidatas con parecido ≥ LATE_TOLERANCE × el mejor, se queda la más tardía: el mejor parecido
# suele estar en el «Tema B» de mitad de canción y cortar ahí dejaba fuera el drop final.
LATE_TOLERANCE = 0.8
BODY_TOLERANCE_DB = 4.0


def decode(path):
    wav = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ar", "44100", "-ac", "2", "-f", "wav", "-"],
                         capture_output=True, check=True).stdout
    return sf.read(io.BytesIO(wav), dtype="float64")


def body_bounds(x, sr):
    win = int(0.5 * sr)
    m = x.mean(1)
    n = len(m) // win
    lv = 20 * np.log10(np.sqrt((m[:n * win].reshape(n, win) ** 2).mean(1)) + 1e-12)
    ok = np.where(lv >= np.median(lv) - BODY_TOLERANCE_DB)[0]
    return ok[0] * win, (ok[-1] + 1) * win


def _bands(m, sr):
    """Espectrograma logarítmico en BANDS bandas, una columna por HOP muestras, normalizado por banda."""
    n = (len(m) - HOP) // HOP
    fr = np.lib.stride_tricks.sliding_window_view(m, 2 * HOP)[::HOP][:n] * np.hanning(2 * HOP)
    mag = np.abs(np.fft.rfft(fr, axis=1))
    edges = np.unique(np.geomspace(2, mag.shape[1] - 1, BANDS + 1).astype(int))
    s = np.log1p(np.add.reduceat(mag, edges[:-1], axis=1))
    return (s - s.mean(0)) / (s.std(0) + 1e-9)


def loop_point(y, sr, hint=None):
    """Punto de corte `b` del bucle: donde lo que suena desde b más se parece a lo que suena desde 0.

    El fundido de 4 s mezclaba dos pasajes sin alinear y el arranque de cada música sonaba «doble». Aquí se
    compara la ventana de MATCH_S s del inicio con cada candidata de la segunda mitad del cuerpo (espectro por
    bandas, cada HOP) y luego se afina a la muestra con correlación cruzada, para que el compás caiga igual.
    Con `hint` (muestra conocida del bucle) no se busca: solo se afina a la muestra. Lo usan las pistas extendidas
    por inpainting, que terminan con una copia de su propio inicio en una posición conocida (`bucle_s` del
    manifiesto); la búsqueda podía preferir la transición generada si esta imitaba el inicio.
    Devuelve (b, parecido 0–1)."""
    m = y.mean(1)
    f = _bands(m, sr)
    w = int(MATCH_S * sr / HOP)
    head = f[:w].ravel()
    if hint is not None:
        b = _refine(m, sr, hint)
        i = b // HOP
        return b, float(np.dot(head, f[i:i + w].ravel())) / len(head)
    lo = max(w, int(len(f) * MIN_LOOP_FRAC))
    cand = range(lo, len(f) - w)
    score = [float(np.dot(head, f[i:i + w].ravel())) / len(head) for i in cand]
    best = max(score)
    k = max(i for i, v in enumerate(score) if v >= LATE_TOLERANCE * best)
    b = cand[k] * HOP
    return _refine(m, sr, b), score[k]


def _refine(m, sr, b):
    """Afina b a la muestra: correlación cruzada de 1 s del inicio en ±HOP alrededor de b."""
    seg = m[b - HOP:b + HOP + sr]
    xc = np.correlate(seg, m[:sr], mode="valid")
    return b - HOP + int(np.argmax(xc))


def loop_crossfade(y, sr, b):
    """Cuerpo y[0:b] con los primeros XFADE_S s fundidos con lo que seguía a b: al volver de la última muestra
    a la primera, la música continúa como si no hubiera corte. Fundido LINEAL, no de igual potencia: las dos
    señales ya están alineadas (correladas) y el de igual potencia las sumaba hasta +3 dB de pico."""
    c = int(XFADE_S * sr)
    t = np.linspace(0, 1, c)[:, None]
    head = y[:c] * t + y[b:b + c] * (1 - t)
    return np.concatenate([head, y[c:b]])


def loudness(path):
    r = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", path, "-af", "ebur128=peak=true", "-f", "null", "-"],
                       capture_output=True, text=True).stderr
    lines = [l.strip() for l in r.splitlines()]
    i = float([l.split()[1] for l in lines if l.startswith("I:")][-1])
    pk = float([l.split()[1] for l in lines if l.startswith("Peak:")][-1])
    return i, pk


def main():
    manifest_path = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else MANIFEST
    doc = json.load(open(manifest_path, encoding="utf-8"))
    src = os.path.join(ROOT, doc["carpeta"]) if "carpeta" in doc else SRC
    evidence = os.path.relpath(manifest_path, ROOT).replace(os.sep, "/")
    src_rel = os.path.relpath(src, ROOT).replace(os.sep, "/")
    manifest = {m["archivo"]: m for m in doc["musicas"]}
    reg = json.load(open(REGISTRY, encoding="utf-8"))
    entries = {e["archivo"]: e for e in reg["entradas"]}
    print(f"{'música':14}{'cuerpo':>16}{'final':>8}{'parecido':>9}{'LUFS':>7}{'pico':>7}")
    for name in sorted(manifest):
        mp3s = [f for f in os.listdir(os.path.join(src, name)) if f.endswith(".mp3")]
        assert len(mp3s) == 1, (name, mp3s)
        x, sr = decode(os.path.join(src, name, mp3s[0]))
        a, b = body_bounds(x, sr)
        out = os.path.join(RAW, name + ".ogg")
        with tempfile.TemporaryDirectory() as tmp:
            # Nivel y limitador ANTES de la costura: el limitador retrasa la señal unos ms y, aplicado después,
            # desplazaba el audio y rompía la continuidad entre la última y la primera muestra.
            raw_wav, lim_wav, wav = (os.path.join(tmp, f) for f in ("body.wav", "lim.wav", "loop.wav"))
            sf.write(raw_wav, x[a:b], sr, subtype="PCM_24")
            i0, _ = loudness(raw_wav)
            lim = 10 ** (TRUE_PEAK / 20)
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", raw_wav, "-af",
                            f"volume={TARGET_LUFS - i0:.2f}dB,alimiter=limit={lim:.4f}:attack=5:release=50:level=disabled:latency=1",
                            "-c:a", "pcm_f32le", lim_wav], check=True)
            z, _ = sf.read(lim_wav, dtype="float64")
            hint = int(manifest[name]["bucle_s"] * sr) - a if "bucle_s" in manifest[name] else None
            b_loop, parecido = loop_point(z, sr, hint=hint)
            y = loop_crossfade(z, sr, b_loop)
            sf.write(wav, y, sr, subtype="FLOAT")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-b:a", "160k", out], check=True)
        i1, pk1 = loudness(out)
        print(f"{name:14}{a / sr:7.1f}–{b / sr:5.1f} s{len(y) / sr:7.1f}s{parecido:9.2f}{i1:7.1f}{pk1:7.1f}")
        m = manifest[name]
        entries[name + ".ogg"] = {
            "archivo": name + ".ogg",
            "sha256": hashlib.sha256(open(out, "rb").read()).hexdigest(),
            "origen": "elevenlabs",
            "licencia": doc.get("licencia", "ElevenLabs plan de pago (Starter anual activo al generar, 2026-09-29)"),
            "modelo": doc.get("modelo", "music_v2 (instrumental forzado, 180 s)"),
            "generado": "2026-09-29",
            "evidencia": evidence,
            "prompt": m["prompt"],
            "original": f"{src_rel}/{name}/{mp3s[0]}",
            "procesado": f"tools/integrar_musica_elevenlabs.py: cuerpo {a / sr:.1f}–{b / sr:.1f} s, bucle alineado en "
                         f"{b_loop / sr:.2f} s (parecido {parecido:.2f}) con fundido de {XFADE_S * 1000:g} ms, {TARGET_LUFS} LUFS, pico real ≤ {TRUE_PEAK} dBTP → OGG Vorbis 160 kbps",
            "estado": "OK",
        }
    reg["entradas"] = sorted(entries.values(), key=lambda e: e["archivo"])
    reg["actualizado"] = "2026-09-29"
    with open(REGISTRY, "w", encoding="utf-8", newline="") as f:
        f.write(json.dumps(reg, ensure_ascii=False, indent=1))
    print(f"{len(manifest)} músicas integradas; registro: {len(reg['entradas'])} entradas")


if __name__ == "__main__":
    main()
