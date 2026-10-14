"""Install only user-approved S02 R3, whole; preserve all other audio and a backup."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

import numpy as np

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def save(path, data, indent=2):
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=indent)+'\n', encoding='utf-8')


def pcm(path):
    return np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(path),
                         '-f','f32le','-']),dtype='<f4')


def main():
    manifest = ROOT/'docs/audio/efectos-base-candidatos-r3-2026-10-11.json'
    data = json.loads(manifest.read_text(encoding='utf-8'))
    chosen = next(t for t in data['tracks'] if t['id']=='S02')
    assert not chosen['technical_flags']
    for field in ['source','preview','receipt']:
        assert sha(chosen[field]) == chosen[field+'_sha256']
    old_target = ROOT/'app/src/main/res/raw'/chosen['resource']
    target = old_target.with_suffix('.wav')
    assert old_target.exists() and not target.exists(), 'Old resource required; do not repeat an installed integration'
    assert sha(old_target)==chosen['reference_sha256']
    out = Path.home()/'.codex/artifacts/elevenlabs/integracion-colocar-r3-2026-10-11'
    out.mkdir(parents=True,exist_ok=True)
    backup = out/'backup'
    backup.mkdir(exist_ok=True)
    registry_path = ROOT/'docs/audio/licencias/registro.json'
    if not (backup/'registro.json').exists():
        shutil.copy2(registry_path,backup/'registro.json')
    if not (backup/old_target.name).exists():
        shutil.copy2(old_target,backup/old_target.name)
    assert sha(backup/old_target.name)==sha(old_target), 'Do not overwrite or use unrelated backups'
    staged = out/target.name
    assert not staged.exists(), 'Integration already staged; do not repeat'
    shutil.copy2(chosen['preview'],staged)
    wave, result = pcm(chosen['preview']), pcm(staged)
    assert wave.size==result.size
    peak = float(np.max(np.abs(result)));assert peak<1
    registry=json.loads(registry_path.read_text(encoding='utf-8'))
    entry=next(e for e in registry['entradas'] if e['archivo']==old_target.name)
    old_entry=entry.copy()
    entry.clear()
    entry.update(archivo=target.name,sha256=sha(staged),origen='elevenlabs',
                 licencia='ElevenLabs SFX Creator activo; evidencia particular de promoción pendiente',
                 modelo='eleven_text_to_sound_v2',generado='2026-10-11',
                 evidencia='docs/audio/colocar-basico-integracion-2026-10-11.json',
                 original=chosen['source'],original_sha256=chosen['source_sha256'],
                 recibo=chosen['receipt'],recibo_sha256=chosen['receipt_sha256'],
                 procesado='WAV PCM16 estéreo 44,1 kHz byte-idéntico a la vista previa aprobada; MP3 completo decodificado con ganancia constante, sin recortes, fundidos, compresión o denoise',
                 estado='VERIFICAR',nota='Usuario aprueba carácter y final natural S02 R3. Contrato particular de promoción estudiantil pendiente; no certificación comercial incondicional.')
    registry['actualizado']='2026-10-11'
    feedback={'date':'2026-10-11','candidate_manifest':str(manifest),
              'general_feedback':'Me gustan las nuevas que generaste',
              'interpretation':'Keep new ElevenLabs takes; general liking of character, not proof of unresolved technical criteria',
              'new_synthesis_authorized':False,'s02_feedback':'Me gusta y el final suena natural',
              's02_source_sha256':chosen['source_sha256'],'s02_preview_sha256':chosen['preview_sha256'],
              's02_user_approved':True,'s02_integrated':True,
              'other_ids':['S01','S03','S04'],'others_character_liked':True,
              'others_technical_corrections_pending':True}
    report={'date':'2026-10-11','id':'S02','skin':'Básico','skin_id':'blockblast',
            'resource':target.name,'source':chosen['source'],'source_sha256':chosen['source_sha256'],
            'preview':chosen['preview'],'preview_sha256':chosen['preview_sha256'],
            'receipt':chosen['receipt'],'receipt_sha256':chosen['receipt_sha256'],
            'installed_sha256':sha(staged),'old_sha256':sha(old_target),'old_registry_entry':old_entry,
            'backup':str(backup),'pipeline':entry['procesado'],'decoded_frames':result.size//2,
            'channels':2,'sample_rate':44100,'duration_seconds':result.size/2/44100,
            'sample_peak_dbfs':float(20*np.log10(peak)),
            'installed_resources':1,'generation_requests':0,
            'feedback':feedback['s02_feedback'],'user_approved':True,
            'commercial_clearance':'Specific student promotion evidence pending',
            'android_device_mix_tested':False,'unit_tests':'pending','build':'pending'}
    vorbis = out/old_target.name
    if vorbis.exists():
        report['rejected_vorbis_attempt']={'file':str(vorbis),'source_frames':wave.size//2,
            'decoded_frames':pcm(vorbis).size//2,'reason':'Decoded frame count differs from approved preview; not installed'}
    shutil.copy2(staged,target)
    # Explicit single-file removal after verified backup; no recursive or computed external deletion.
    assert old_target.resolve().parent == (ROOT/'app/src/main/res/raw').resolve()
    old_target.unlink()
    save(registry_path,registry,1)
    save(ROOT/'docs/audio/efectos-base-aceptacion-2026-10-11.json',feedback)
    save(ROOT/'docs/audio/colocar-basico-integracion-2026-10-11.json',report)
    print(f"Installed only {target.name}, {report['duration_seconds']:.3f}s, peak {report['sample_peak_dbfs']:.2f} dBFS; full frames preserved")


if __name__=='__main__':
    main()
