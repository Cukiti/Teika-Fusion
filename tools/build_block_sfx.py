"""
SONIDOS PREMIUM DE LOS BLOQUES (P-283 punto 2, 2026-09-25).

Cada sonido se CONSTRUYE mezclando capas de los packs con licencia (Kenney CC0 y Sonniss GDC 2026):
a cada capa se le quita el silencio inicial, se recorta, se le da ganancia y retraso; luego se mezclan,
se funde la cola y se nivela el pico a -15 dBFS (vorbis 44,1 kHz estéreo, como el resto del juego).

    python tools/build_block_sfx.py            # construye todos y actualiza docs/audio/licencias/registro.json
    python tools/build_block_sfx.py snd_x ...  # solo esos

Las fuentes viven en art/_audio_fuentes/ (fuera de git). El registro guarda, por cada sonido, sus capas
con el pack y el archivo original: es lo que demuestra de dónde sale cada uno.
"""
import hashlib, json, os, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'art', '_audio_fuentes')
RAW = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'raw')
REG = os.path.join(ROOT, 'docs', 'audio', 'licencias', 'registro.json')

K_IMPACT = ('kenney/impact-sounds', 'kenney/kenney_impact-sounds/Audio/')
K_UI = ('kenney/interface-sounds', 'kenney/kenney_interface-sounds/Audio/')
K_SCIFI = ('kenney/sci-fi-sounds', 'kenney/kenney_sci-fi-sounds/Audio/')
K_RPG = ('kenney/rpg-audio', 'kenney/kenney_rpg-audio/Audio/')
SON = ('sonniss/gdc-2026', 'sonniss/pistas/')


def L(pack, name, gain=0.0, delay=0, dur=None, skip=0.0):
    """Una capa: pack, archivo, ganancia (dB), retraso (ms), duración máxima (s), segundos a saltar."""
    return dict(fuente=pack[0], original=name, path=pack[1] + name, gain=gain, delay=delay, dur=dur, skip=skip)


# nombre -> (duración total en s, capas)
RECIPES = {
    # ── Cristal ─────────────────────────────────────────────────────────────────────────────
    'snd_place_crystal': (0.40, [L(K_IMPACT, 'impactGlass_light_001.ogg'), L(K_UI, 'glass_002.ogg', -5, 25)]),
    'snd_clear_crystal': (0.70, [L(K_IMPACT, 'impactGlass_heavy_001.ogg'),
                                 L(K_IMPACT, 'impactGlass_light_002.ogg', -4, 60),
                                 L(K_UI, 'glass_001.ogg', -7, 140)]),
    # ── Madera ──────────────────────────────────────────────────────────────────────────────
    'snd_place_wood': (0.35, [L(K_IMPACT, 'impactWood_light_000.ogg'),
                              L(SON, 'GAMEBoard_Game Play Piece Action Organic Connect Dots Fall Bounce 04_ESM_BG.wav', -7, 10, 0.3)]),
    'snd_clear_wood': (0.75, [L(K_IMPACT, 'impactWood_heavy_001.ogg'),
                              L(K_RPG, 'chop.ogg', -3, 40, 0.5),
                              L(SON, 'GAMEBoard_Event Board Reset Organic Multiple Pieces Wood Small 02_ESM_BG.wav', -8, 70, 0.6)]),
    # ── Lava ────────────────────────────────────────────────────────────────────────────────
    'snd_place_lava': (0.45, [L(K_IMPACT, 'impactMining_000.ogg', 0, 0, 0.4),
                              L(SON, 'FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ESM_SNLS.wav', -9, 40, 0.4)]),
    'snd_clear_lava': (0.85, [L(K_SCIFI, 'explosionCrunch_000.ogg'),
                              L(SON, 'FIREWhsh_Whoosh Fire Deep Growl Monster Saturated Crisp 03_ESM_EMWI.wav', -4, 0, 0.8),
                              L(SON, 'FIREBurn_Loop Elements Fire Crackling Crunchy Flame Burn 03_ESM_SNLS.wav', -9, 150, 0.7)]),
    # ── Hielo ───────────────────────────────────────────────────────────────────────────────
    'snd_place_ice': (0.40, [L(K_IMPACT, 'impactGlass_light_000.ogg'), L(K_UI, 'glass_001.ogg', -6, 30)]),
    'snd_clear_ice': (0.80, [L(SON, 'ice, block of ice crushed, heavy-015.wav', 0, 0, 0.7),
                             L(SON, 'ice, crack, ice block snapping-001.wav', -4, 40, 0.6),
                             L(K_IMPACT, 'impactGlass_heavy_000.ogg', -7, 90)]),
    # ── Espectro ────────────────────────────────────────────────────────────────────────────
    'snd_place_spectre': (0.45, [L(K_SCIFI, 'forceField_000.ogg', -3, 0, 0.4),
                                 L(SON, 'GAMEMisc_Magic Creation 23_CB Sounddesign_APPlicable Sounds.wav', -8, 0, 0.45)]),
    'snd_clear_spectre': (0.90, [L(SON, 'magic, energy flow, astonishment-001.wav', 0, 0, 0.85),
                                 L(SON, 'MAGAngl_Magic Light Spell Enchantment Potion Effect Tonal Bright 03_ESM_FG2.wav', -7, 60, 0.8)]),
    # ── Constelación ────────────────────────────────────────────────────────────────────────
    'snd_place_constellation': (0.50, [L(SON, 'Button Arp Twinkle.wav', 0, 0, 0.5), L(K_UI, 'pluck_001.ogg', -6)]),
    'snd_clear_constellation': (0.90, [L(SON, 'DSGNStngr_Power Up Bright Positive Successful Light Saturation Crash Shimmer 05_ESM_AG.wav', 0, 0, 0.85),
                                       L(SON, 'MAGMisc_Magic Christmas Bells 2_344 Audio_Christmas.wav', -7, 80, 0.8)]),
    # ── Vacío ───────────────────────────────────────────────────────────────────────────────
    'snd_place_void': (0.45, [L(K_SCIFI, 'lowFrequency_explosion_000.ogg', 0, 0, 0.45),
                              L(K_SCIFI, 'forceField_001.ogg', -10, 0, 0.3)]),
    'snd_clear_void': (0.90, [L(SON, 'DSGNBass_Bass Drop & Downer Fast 16_344 Audio_Bass Drops & Downers.wav', 0, 0, 0.85),
                              L(SON, 'DSGNBass_Tone Downer (Reverb)_344 Audio_Bass Drops and Downers Vol 3.wav', -6, 50, 0.8)]),
    # ── Metal (skin de la vitrina, 2026-09-25) ──────────────────────────────────────────────
    'snd_place_metal': (0.40, [L(K_IMPACT, 'impactPlate_light_000.ogg'), L(K_RPG, 'metalClick.ogg', -5, 20, 0.3)]),
    'snd_clear_metal': (0.80, [L(K_IMPACT, 'impactMetal_heavy_000.ogg'),
                               L(K_RPG, 'metalLatch.ogg', -4, 50),
                               L(K_IMPACT, 'impactPlate_heavy_000.ogg', -5, 90)]),
    # ── Bloque-Teika: CÁPSULA GASHAPON (2026-09-25; antes placa metálica, P-284) ──────────────
    'snd_teika_seed_break': (0.55, [L(K_UI, 'open_001.ogg', 0, 0, 0.35),
                                    L(K_UI, 'pluck_002.ogg', -3, 40),
                                    L(K_UI, 'glass_002.ogg', -9, 90)]),
}


def ff(*args):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', *args], check=True)


def peak(path):
    out = subprocess.run(['ffmpeg', '-hide_banner', '-i', path, '-af', 'volumedetect', '-f', 'null', '-'],
                         capture_output=True, text=True).stderr
    return float(out.split('max_volume:')[1].split('dB')[0])


def build(name, total, layers, tmp):
    parts = []
    for i, l in enumerate(layers):
        src = os.path.join(SRC, l['path'])
        if not os.path.exists(src): raise FileNotFoundError(src)
        chain = ['silenceremove=start_periods=1:start_threshold=-45dB']
        if l['skip']: chain.append(f"atrim=start={l['skip']}")
        if l['dur']: chain.append(f"atrim=end={l['dur']}")
        chain += ['asetpts=PTS-STARTPTS', 'aresample=44100', 'aformat=channel_layouts=stereo',
                  f"volume={l['gain']}dB"]
        if l['delay']: chain.append(f"adelay={l['delay']}|{l['delay']}")
        out = os.path.join(tmp, f'{name}_{i}.wav')
        ff('-i', src, '-af', ','.join(chain), out)
        parts.append(out)
    mixed = os.path.join(tmp, f'{name}_mix.wav')
    inputs = sum((['-i', p] for p in parts), [])
    fade = max(total - 0.08, 0.01)
    ff(*inputs, '-filter_complex',
       f"amix=inputs={len(parts)}:duration=longest:normalize=0,atrim=end={total},afade=t=out:st={fade}:d=0.08",
       mixed)
    gain = -15.0 - peak(mixed)
    dest = os.path.join(RAW, name + '.ogg')
    ff('-i', mixed, '-af', f'volume={gain:.2f}dB', '-ar', '44100', '-ac', '2', '-c:a', 'libvorbis', '-q:a', '5', dest)
    return dest, gain


def main(names):
    reg = json.load(open(REG, encoding='utf-8'))
    by_name = {e['archivo']: e for e in reg['entradas']}
    with tempfile.TemporaryDirectory() as tmp:
        for name, (total, layers) in RECIPES.items():
            if names and name not in names: continue
            dest, gain = build(name, total, layers, tmp)
            fuentes = sorted({l['fuente'].split('/')[0] for l in layers})
            entry = dict(
                archivo=name + '.ogg',
                sha256=hashlib.sha256(open(dest, 'rb').read()).hexdigest(),
                origen=fuentes[0] if len(fuentes) == 1 else 'mezcla',
                licencia=' + '.join({'kenney': 'Kenney CC0', 'sonniss': 'Sonniss #GameAudioGDC'}[f] for f in fuentes),
                capas=[{k: l[k] for k in ('fuente', 'original', 'gain', 'delay', 'dur')} for l in layers],
                procesado=f'tools/build_block_sfx.py: capas sin silencio inicial, mezcla de {total}s, '
                          f'fundido 80 ms, pico -15 dBFS (ganancia {gain:+.1f} dB), vorbis q5',
                generado='2026-09-25 (P-283 · bloques premium)',
                estado='OK',
            )
            by_name[entry['archivo']] = entry
            print(f'{name}: {total}s, {len(layers)} capas, ganancia {gain:+.1f} dB')
    reg['entradas'] = [by_name[k] for k in sorted(by_name)]
    reg['actualizado'] = '2026-09-25'
    json.dump(reg, open(REG, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)


if __name__ == '__main__':
    main(set(sys.argv[1:]))
