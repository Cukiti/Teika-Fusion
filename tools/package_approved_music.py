"""Package the 15 accepted whole music previews. No API, trimming or loop repair.

Requires ffmpeg and numpy. Run from the repository root. Without --apply,
only writes validated candidates and a backup under .codex/artifacts.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
from datetime import datetime, timezone

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / 'app/src/main/res/raw'
ACCEPTANCE = ROOT / 'docs/audio/aceptacion-musical-2026-10-11.json'
REGISTRY = ROOT / 'docs/audio/licencias/registro.json'
REPORT = ROOT / 'docs/audio/integracion-musical-2026-10-11.json'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def write_json(path, data, indent=2):
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=indent) + '\n', encoding='utf-8')


def decode(path):
    result = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path),
                             '-f', 'f32le', '-acodec', 'pcm_f32le', '-'],
                            check=True, capture_output=True)
    return np.frombuffer(result.stdout, dtype='<f4')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    acceptance = json.loads(ACCEPTANCE.read_text(encoding='utf-8'))
    registry = json.loads(REGISTRY.read_text(encoding='utf-8'))
    assert len(acceptance['tracks']) == 15
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    work = Path.home() / '.codex/artifacts/elevenlabs' / ('integracion-musical-' + stamp)
    staged, backup = work / 'candidates', work / 'backup'
    staged.mkdir(parents=True)
    backup.mkdir()
    shutil.copy2(REGISTRY, backup / REGISTRY.name)
    shutil.copy2(ACCEPTANCE, backup / ACCEPTANCE.name)
    report = {'date': '2026-10-11', 'created_at_utc': stamp, 'backup': str(backup),
              'pipeline': 'Approved whole WAV -> libvorbis q5; no trim, fades, assembly, EQ or additional gain',
              'music_ceiling': 0.28, 'android_mix_tested': False, 'tracks': []}
    names = set()
    for track in acceptance['tracks']:
        assert track['user_composition_approved'] and track['user_loop_approved']
        source, preview = Path(track['source']), Path(track['preview'])
        assert sha(source) == track['source_sha256'], source
        assert sha(preview) == track['preview_sha256'], preview
        receipt_path = Path(str(source) + '.generation.json')
        receipt = json.loads(receipt_path.read_text(encoding='utf-8'))
        assert receipt['httpStatus'] == 200 and receipt['model'] == 'music_v2_5'
        name = 'bgm_' + ('gameplay' if track['id'] == 'MENU' else track['id'].lower()) + '.ogg'
        assert name not in names
        names.add(name)
        target = staged / name
        old = RAW / name
        shutil.copy2(old, backup / name)
        subprocess.run(['ffmpeg', '-v', 'error', '-nostdin', '-i', str(preview),
                        '-map_metadata', '-1', '-c:a', 'libvorbis', '-q:a', '5',
                        str(target)], check=True)
        original_pcm, packaged_pcm = decode(preview), decode(target)
        assert original_pcm.size == packaged_pcm.size, 'Decoded sample count changed: ' + name
        peak = float(np.max(np.abs(packaged_pcm)))
        assert peak < 1.0, 'Clipping: ' + name
        probe = json.loads(subprocess.run(['ffprobe', '-v', 'error', '-show_streams',
                                          '-of', 'json', str(target)],
                                         check=True, capture_output=True, text=True).stdout)['streams'][0]
        channels, rate = int(probe['channels']), int(probe['sample_rate'])
        assert channels == 2 and rate == 44100
        info = {'id': track['id'], 'resource': name, 'source': str(source),
                'source_sha256': sha(source), 'preview_sha256': sha(preview),
                'receipt': str(receipt_path), 'receipt_sha256': sha(receipt_path),
                'song_id': receipt.get('songId'), 'request_id': receipt.get('requestId'),
                'old_sha256': sha(old), 'installed_sha256': sha(target),
                'frames': packaged_pcm.size // channels, 'sample_rate': rate, 'channels': channels,
                'duration_seconds': packaged_pcm.size / channels / rate,
                'sample_peak_dbfs': float(20 * np.log10(peak)),
                'rms_dbfs': float(20 * np.log10(np.sqrt(np.mean(packaged_pcm.astype(np.float64) ** 2)))),
                'old_bytes': old.stat().st_size, 'bytes': target.stat().st_size}
        report['tracks'].append(info)
        print(f"{name}: {info['duration_seconds']:.3f}s, peak {info['sample_peak_dbfs']:.2f} dBFS, full frame count preserved")
    assert names == {e['archivo'] for e in registry['entradas'] if e['archivo'].startswith('bgm_')}
    write_json(work / 'packaging.json', report)
    if not args.apply:
        print('Validated candidates only: ' + str(work))
        return
    # All candidates validated before any installed audio is replaced.
    by_name = {info['resource']: info for info in report['tracks']}
    for entry in registry['entradas']:
        info = by_name.get(entry['archivo'])
        if info is None:
            continue
        entry.clear()
        entry.update(archivo=info['resource'], sha256=info['installed_sha256'], origen='elevenlabs',
                     licencia='ElevenLabs Music Creator activo; uso individual en Indie Game; ver evaluación 2026-10-11',
                     modelo='music_v2_5', generado='2026-10-11',
                     evidencia='docs/audio/integracion-musical-2026-10-11.json',
                     licencia_evaluacion='docs/audio/licencias/musica-creator-2026-10-11.md',
                     original=info['source'], original_sha256=info['source_sha256'],
                     recibo=info['receipt'], recibo_sha256=info['receipt_sha256'],
                     song_id=info['song_id'], procesado=report['pipeline'],
                     estado='VERIFICAR', nota='Criterios Indie/uso individual declarados por usuario; contrato particular de promoción no archivado. Mezcla Android pendiente.')
        shutil.copy2(staged / info['resource'], RAW / info['resource'])
    registry['actualizado'] = '2026-10-11'
    write_json(REGISTRY, registry, indent=1)
    for track in acceptance['tracks']:
        name = 'bgm_' + ('gameplay' if track['id'] == 'MENU' else track['id'].lower()) + '.ogg'
        track.update(integrated=True, installed_resource=name,
                     installed_sha256=by_name[name]['installed_sha256'])
    acceptance.update(installed_resources=15,
                      listening_status='All 15 user-approved whole compositions integrated; Android mix and promotion contract evidence pending')
    write_json(ACCEPTANCE, acceptance)
    write_json(REPORT, report)
    print('Integrated 15 whole tracks. Backup: ' + str(backup))


if __name__ == '__main__':
    main()
