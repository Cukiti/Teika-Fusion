"""Integrate only the three R4 effects explicitly approved in user listening, whole WAVs."""
import hashlib
import json
from pathlib import Path
import shutil
import numpy as np
import subprocess

ROOT=Path(__file__).resolve().parents[1]
RAW=ROOT/'app/src/main/res/raw'
FEEDBACK={'S01':'Sí, me gusta y la repetición es cómoda',
          'S03':'Me gusta y el final suena natural',
          'S04':'No percibo corte; me gusta y termina naturalmente'}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def save(path,data,indent=2):
    Path(path).write_text(json.dumps(data,ensure_ascii=False,indent=indent)+'\n',encoding='utf-8')


def main():
    manifest=ROOT/'docs/audio/efectos-base-candidatos-r4-2026-10-11.json'
    batch=json.loads(manifest.read_text(encoding='utf-8'))
    assert {t['id'] for t in batch['tracks']}==set(FEEDBACK)
    out=Path.home()/'.codex/artifacts/elevenlabs/integracion-core-r4-2026-10-11'
    assert not out.exists(), 'Do not repeat or overwrite an integration backup'
    out.mkdir(parents=True);backup=out/'backup';backup.mkdir()
    registry_path=ROOT/'docs/audio/licencias/registro.json'
    acceptance_path=ROOT/'docs/audio/efectos-base-aceptacion-2026-10-11.json'
    registry=json.loads(registry_path.read_text(encoding='utf-8'))
    acceptance=json.loads(acceptance_path.read_text(encoding='utf-8'))
    shutil.copy2(registry_path,backup/registry_path.name)
    shutil.copy2(acceptance_path,backup/acceptance_path.name)
    shutil.copy2(manifest,backup/manifest.name)
    report={'date':'2026-10-11','backup':str(backup),'generation_requests':0,
            'pipeline':'Whole approved PCM16 WAV copied byte-identically; no new gain, trim, fades, compression or denoise',
            'user_acceptance_basis':'Explicit answers after duration/terminal measurements were disclosed; not a claim that numeric flags passed',
            'android_mix_tested':False,'build':'pending','unit_tests':'pending','tracks':[]}
    prepared=[]
    for track in batch['tracks']:
        for kind in ['source','preview','receipt']:assert sha(track[kind])==track[kind+'_sha256']
        old=RAW/track['resource'];new=old.with_suffix('.wav')
        assert old.exists() and not new.exists() and sha(old)==track['reference_sha256']
        preview=Path(track['preview'])
        probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',str(preview)]))['streams'][0]
        assert probe['codec_name']=='pcm_s16le' and int(probe['sample_rate'])==44100 and int(probe['channels'])==2
        samples=np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(preview),'-f','f32le','-']),dtype='<f4')
        assert samples.size//2==21168 and np.max(np.abs(samples))<1
        shutil.copy2(old,backup/old.name);shutil.copy2(preview,out/new.name)
        entry=next(e for e in registry['entradas'] if e['archivo']==old.name)
        previous=entry.copy()
        entry.clear();entry.update(archivo=new.name,sha256=sha(preview),origen='elevenlabs',
            licencia='ElevenLabs SFX Creator activo; evidencia particular de promoción pendiente',
            modelo='eleven_text_to_sound_v2',generado='2026-10-11',
            evidencia='docs/audio/efectos-core-r4-integracion-2026-10-11.json',
            original=track['source'],original_sha256=track['source_sha256'],
            recibo=track['receipt'],recibo_sha256=track['receipt_sha256'],
            procesado=report['pipeline'],estado='VERIFICAR',
            nota='Usuario aprueba explícitamente escucha R4 con medidas reveladas; contrato particular de promoción pendiente.')
        report['tracks'].append({'id':track['id'],'resource':new.name,'old_resource':old.name,
            'old_sha256':sha(old),'old_registry_entry':previous,'installed_sha256':sha(preview),
            'source':track['source'],'source_sha256':track['source_sha256'],
            'preview':track['preview'],'preview_sha256':track['preview_sha256'],
            'receipt':track['receipt'],'receipt_sha256':track['receipt_sha256'],
            'feedback':FEEDBACK[track['id']],'user_approved':True,'integrated':True,
            'original_technical_flags':track['technical_flags'],
            'decoded_frames':21168,'duration_seconds':.480,'sample_rate':44100,'channels':2,
            'sample_peak_dbfs':float(20*np.log10(np.max(np.abs(samples)))),'bytes':preview.stat().st_size})
        prepared.append((old,new,preview))
    # All hashes, formats and backups validated before touching any installed resource.
    for old,new,preview in prepared:
        shutil.copy2(preview,new)
        assert old.resolve().parent==RAW.resolve()
        old.unlink()
    for track in batch['tracks']:
        new_name=Path(track['resource']).with_suffix('.wav').name
        track.update(user_approved=True,integrated=True,user_feedback=FEEDBACK[track['id']],
            installed_resource=new_name,installed_sha256=track['preview_sha256'],
            user_listening_approved_despite_flags=True)
    batch.update(installed_resources=3,acceptance_record='docs/audio/efectos-base-aceptacion-2026-10-11.json')
    acceptance.update(others_technical_corrections_pending=False,
        r4_listening_accepted=True,r4_integrated=True,r4_tracks=report['tracks'],
        acceptance_scope='All four base effects integrated; browser listening approved, numeric flags retained; Android audible mix not yet verified')
    report['installed_resources']=3
    registry['actualizado']='2026-10-11'
    save(registry_path,registry,1);save(acceptance_path,acceptance);save(manifest,batch)
    save(ROOT/'docs/audio/efectos-core-r4-integracion-2026-10-11.json',report)
    print('Integrated S01/S03/S04 whole approved WAVs; S02 and all other audio preserved')


if __name__=='__main__':
    main()
