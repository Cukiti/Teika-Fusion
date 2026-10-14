"""Install source-bound approved bell/Flora or iris pairs; preserve other assets."""
import argparse,copy,json,shutil,subprocess
from pathlib import Path
from produce_core_sfx_candidates import ROOT,sha,save,decode,metrics

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--iris-approved',action='store_true',help='Install approved NAV04/NAV05 instead of start bell/Flora')
    iris=parser.parse_args().iris_approved
    raw=ROOT/'app/src/main/res/raw';audio=ROOT/'docs/audio'
    manifest=audio/('efectos-iris-succion-expansion-revision-2026-10-12.json' if iris else 'efectos-campana-brisa-revision-usuario-2026-10-12.json')
    report_path=audio/('efectos-iris-integracion-2026-10-12.json' if iris else 'efectos-campana-flora-integracion-2026-10-12.json')
    out=Path.home()/'.codex/artifacts/elevenlabs'/('integracion-iris-2026-10-12' if iris else 'integracion-campana-flora-2026-10-12')
    before_count=147 if iris else 146
    replaced_id='NAV04' if iris else 'NAV01'
    replaced_name='snd_iris_transition.ogg' if iris else 'snd_play_launch.ogg'
    destinations={'NAV04':'snd_iris_transition.wav','NAV05':'snd_iris_open.wav'} if iris else {'NAV01':'snd_play_launch.wav','NAV06':'snd_flora_launch.wav'}
    assert not out.exists() and not report_path.exists(), 'Never repeat integration or overwrite backups'
    batch=json.loads(manifest.read_text(encoding='utf-8'));registry_path=audio/'licencias/registro.json'
    registry=json.loads(registry_path.read_text(encoding='utf-8'));original=copy.deepcopy(registry)
    baseline={f.name:sha(f) for f in raw.iterdir() if f.suffix in ['.ogg','.wav','.mp3']}
    assert len(baseline)==before_count and set(baseline)=={e['archivo'] for e in registry['entradas']}
    selected=[t for t in batch['tracks'] if t['id'] in destinations];assert len(selected)==2 and {t['id'] for t in selected}==set(destinations)
    assert all(baseline[e['archivo']]==e['sha256'] for e in registry['entradas']), 'Verify complete registry before mutations'
    prepared=[];report={'date':'2026-10-12','generation_requests':0,'backup':str(out/'backup'),
      'pipeline':'Whole approved PCM16 WAV copied byte-identically; no gain, trimming, fades or processing',
      'acceptance_basis':'Explicit source-bound user listening approval; original flags retained',
      'build':'pending','unit_tests':'pending','android_mix_tested':False,'tracks':[]}
    for t in selected:
        for kind in ['source','preview','receipt']:assert sha(t[kind])==t[kind+'_sha256']
        reference=raw/t['resource'];assert sha(reference)==t['reference_sha256']
        new=raw/destinations[t['id']]
        assert reference.resolve().parent==new.resolve().parent==raw.resolve() and not new.exists()
        probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',t['preview']]))['streams'][0]
        assert probe['codec_name']=='pcm_s16le' and int(probe['sample_rate'])==44100 and int(probe['channels'])==2
        pcm=decode(t['preview']);assert len(pcm)==t['frames']==len(decode(t['source'])) and metrics(pcm,44100)['clipped_samples']==0
        replaced=t['id']==replaced_id;old=copy.deepcopy(next(e for e in registry['entradas'] if e['archivo']==reference.name)) if replaced else None
        entry=next(e for e in registry['entradas'] if e['archivo']==reference.name) if replaced else {}
        if not replaced:registry['entradas'].append(entry)
        entry.clear();entry.update(archivo=new.name,sha256=t['preview_sha256'],origen='elevenlabs',
          licencia='ElevenLabs SFX Creator activo; evidencia particular de promoción pendiente',modelo='eleven_text_to_sound_v2',generado='2026-10-12',
          evidencia=report_path.relative_to(ROOT).as_posix(),original=t['source'],original_sha256=t['source_sha256'],recibo=t['receipt'],recibo_sha256=t['receipt_sha256'],
          procesado=report['pipeline'],estado='VERIFICAR',nota='Usuario aprueba campana para partida y fuente mecánica completa para Flora, con separación de productores; promoción pendiente.')
        feedback='Sí, ese es el carácter que quiero' if iris else ('Sí, me gusta y termina naturalmente' if replaced else 'Sí, me gusta para Flora')
        if iris:entry['nota']='Usuario aprueba cierre y apertura de iris completos, con conexión a eventos reales; promoción pendiente.'
        report['tracks'].append({'id':t['id'],'resource':new.name,'old_resource':reference.name if replaced else None,'reference_resource':reference.name,
          'old_registry_entry':old,'installed_sha256':t['preview_sha256'],'preview':t['preview'],'preview_sha256':t['preview_sha256'],
          'source':t['source'],'source_sha256':t['source_sha256'],'receipt':t['receipt'],'receipt_sha256':t['receipt_sha256'],
          'user_approved':True,'integrated':True,'feedback':feedback,'original_technical_flags':t['technical_flags'],
          'decoded_frames':len(pcm),'duration_seconds':len(pcm)/44100,'sample_rate':44100,'channels':2,'metrics':metrics(pcm,44100)})
        prepared.append((t,reference,new,replaced,feedback))
    backup=out/'backup';backup.mkdir(parents=True);shutil.copy2(manifest,backup/manifest.name);shutil.copy2(registry_path,backup/registry_path.name)
    shutil.copy2(raw/replaced_name,backup/replaced_name)
    for t,reference,new,replaced,feedback in prepared:
        shutil.copy2(t['preview'],new);assert sha(new)==t['preview_sha256']
        t.update(user_approved=True,integrated=True,user_feedback=feedback,installed_resource=new.name,installed_sha256=t['preview_sha256'])
        if t['id']=='NAV06':t['proposal']='Existing complete mechanical R5 source explicitly approved for Flora and integrated'
    (raw/replaced_name).unlink()
    for t in batch['tracks']:
        if not iris and t['id'] in ['NAV04','NAV05']:t.update(user_approved=False,integrated=False,user_feedback='Analiza la versión actual; cierre con succión descendente y pop seco, apertura pop y expansión ascendente',listening_status='rejected; user supplied detailed new design')
    others={n:d for n,d in baseline.items() if n!=replaced_name};assert all(sha(raw/n)==d for n,d in others.items())
    excluded={replaced_name};newnames=set(destinations.values())
    assert [e for e in original['entradas'] if e['archivo'] not in excluded]==[e for e in registry['entradas'] if e['archivo'] not in newnames]
    assert len([f for f in raw.iterdir() if f.suffix in ['.ogg','.wav','.mp3']])==len(registry['entradas'])==before_count+1
    report.update(installed_resources=2,other_resources_and_records_preserved=before_count-1,inventory_before=before_count,inventory_after=before_count+1)
    batch.update(installed_resources=sum(t.get('integrated') and t.get('user_approved') for t in batch['tracks']),acceptance_record=report_path.relative_to(ROOT).as_posix())
    registry['actualizado']='2026-10-12';registry_path.write_text(json.dumps(registry,ensure_ascii=False,indent=1)+'\n',encoding='utf-8');save(manifest,batch);save(report_path,report)
    print(f'Two exact approved WAVs installed; {before_count-1} other assets/records preserved, inventory {before_count+1}')

if __name__=='__main__':main()
