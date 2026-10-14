"""
REGISTRO DE LICENCIAS DE AUDIO (2026-09-25).

Uso:
    python tools/audio_licencias.py            # comprueba: cada audio de res/raw tiene entrada y su hash cuadra
    python tools/audio_licencias.py --semilla  # SOLO se usó una vez (2026-09-25): NO volver a ejecutarlo,
                                               # machacaría las entradas de build_block_sfx.py

`docs/audio/licencias/registro.json` es la fuente de verdad: UNA entrada por archivo de `res/raw`
(.ogg/.mp3/.wav) con de dónde sale, bajo qué licencia y su SHA-256. Lo defiende además
`AudioLicenseRegistryTest`, así que un sonido nuevo o cambiado sin registrar rompe el build.

Al añadir un sonido de Kenney o Sonniss, se apunta con `origen` = "kenney" / "sonniss", el pack y el
archivo original tal como vienen en `fuentes.json`, y el procesado aplicado.
"""
import hashlib, json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'raw')
REG = os.path.join(ROOT, 'docs', 'audio', 'licencias', 'registro.json')
EVIDENCE = os.path.join(ROOT, 'docs', 'audio', 'evidencia-licencia-elevenlabs-2026-09-15', 'manifiesto-activos.json')
AUDIO_EXT = ('.ogg', '.mp3', '.wav')

# Los sintetizados con ffmpeg en este repo: el sonido es nuestro.
FFMPEG_OWN = {
    'snd_place_crystal': 'P-251 1/7 (2026-09-23)', 'snd_clear_crystal': 'P-251 1/7 (2026-09-23)',
    'snd_place_wood': 'tools/gen_block_sfx.sh', 'snd_clear_wood': 'tools/gen_block_sfx.sh',
    'snd_place_lava': 'tools/gen_block_sfx.sh', 'snd_clear_lava': 'tools/gen_block_sfx.sh',
    'snd_place_ice': 'tools/gen_block_sfx.sh', 'snd_clear_ice': 'tools/gen_block_sfx.sh',
    'snd_place_spectre': 'tools/gen_block_sfx.sh', 'snd_clear_spectre': 'tools/gen_block_sfx.sh',
    'snd_place_constellation': 'tools/gen_block_sfx.sh', 'snd_clear_constellation': 'tools/gen_block_sfx.sh',
    'snd_place_void': 'tools/gen_block_sfx.sh', 'snd_clear_void': 'tools/gen_block_sfx.sh',
    'snd_teika_seed_break': 'tools/gen_block_sfx.sh',
}


def sha(path):
    return hashlib.sha256(open(path, 'rb').read()).hexdigest()


def audio_files():
    return sorted(f for f in os.listdir(RAW) if f.lower().endswith(AUDIO_EXT))


def seed():
    ev = {os.path.basename(a['installedPath']): a for a in json.load(open(EVIDENCE, encoding='utf-8'))['assets']}
    out = []
    for f in audio_files():
        stem = os.path.splitext(f)[0]
        h = sha(os.path.join(RAW, f))
        e = ev.get(f)
        if stem in FFMPEG_OWN:
            rec = dict(origen='propio-ffmpeg', licencia='Propio (síntesis desde cero)',
                       generado=FFMPEG_OWN[stem], estado='OK')
        elif e and e['installedSha256'] == h:
            rec = dict(origen='elevenlabs', licencia='ElevenLabs plan de pago (Starter activo al generar)',
                       modelo=e.get('model'), generado=e.get('generationTimestamp'),
                       evidencia='docs/audio/evidencia-licencia-elevenlabs-2026-09-15', estado='OK')
        elif e:
            rec = dict(origen='elevenlabs-derivado',
                       licencia='ElevenLabs plan de pago (Starter activo al generar); archivo re-procesado después',
                       modelo=e.get('model'), generado=e.get('generationTimestamp'),
                       evidencia='docs/audio/evidencia-licencia-elevenlabs-2026-09-15',
                       nota='El hash instalado ya no es el de la evidencia: se re-procesó (bucle/nivel) en el '
                            'commit 0e4da26 del 2026-09-22. La licencia sigue la de la generación original.',
                       estado='OK')
        else:
            rec = dict(origen='elevenlabs', licencia='DESCONOCIDA hasta confirmar el plan del día de generación',
                       generado='2026-09-24 (P-272)', estado='VERIFICAR',
                       nota='Generado con ElevenLabs después de la evidencia. La cuenta estaba en plan GRATIS el '
                            '2026-09-25: si ese día ya lo estaba, NO tiene licencia comercial. Mirar la '
                            'facturación de ElevenLabs; si no hay Starter, regenerarlo o sustituirlo.')
        out.append(dict(archivo=f, sha256=h, **rec))
    os.makedirs(os.path.dirname(REG), exist_ok=True)
    json.dump(dict(actualizado='2026-09-25', entradas=out), open(REG, 'w', encoding='utf-8'),
              ensure_ascii=False, indent=2)
    print(f'{len(out)} entradas escritas en {REG}')


def check():
    reg = {e['archivo']: e for e in json.load(open(REG, encoding='utf-8'))['entradas']}
    bad = 0
    for f in audio_files():
        e = reg.get(f)
        if not e:
            print(f'SIN REGISTRO: {f}'); bad += 1
        elif e['sha256'] != sha(os.path.join(RAW, f)):
            print(f'HASH DISTINTO (se cambió sin registrar): {f}'); bad += 1
    for f in reg:
        if f not in audio_files():
            print(f'REGISTRADO PERO YA NO ESTÁ: {f}'); bad += 1
    pend = [f for f, e in reg.items() if e.get('estado') != 'OK']
    print(f'{len(reg)} registrados · {bad} problemas · por verificar: {pend}')
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    seed() if '--semilla' in sys.argv else check()
