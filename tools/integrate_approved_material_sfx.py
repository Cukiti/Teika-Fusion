"""Install only explicitly selected approved material groups from source-bound answers."""
import copy
import argparse
import json
from pathlib import Path
import shutil
import subprocess
from produce_core_sfx_candidates import ROOT, sha, save, decode, metrics

FEEDBACK = {'CM02': 'Sí, me gusta y termina naturalmente',
            'CM03': 'Sí, me gusta y la repetición es cómoda',
            'CM04': 'Sí, me gusta y termina naturalmente'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--crystal-r5', action='store_true', help='Install only the explicitly approved CM01 R5')
    parser.add_argument('--lava-ice', action='store_true', help='Install only LH01 R3, LH03 R2 and LH04 R1 explicitly approved')
    parser.add_argument('--clean-lava-r2', action='store_true', help='Install only the explicitly approved LH02 R2')
    parser.add_argument('--spectre-constellation', action='store_true', help='Install approved EC01 R6, EC02 R7 and EC03/EC04 R8')
    parser.add_argument('--void-pair', action='store_true', help='Install only approved VM01 R1 and VM02 R2')
    parser.add_argument('--metal-pair', action='store_true', help='Install only approved VM03 R3 and VM04 R2')
    parser.add_argument('--gold-ceramic-approved', action='store_true', help='Install approved NDC02 R4, NDC03 R3 and NDC05 R1 only')
    parser.add_argument('--neon-ceramic-final', action='store_true', help='Install approved NDC01 R5 and NDC04 R4 only')
    parser.add_argument('--invalid-approved', action='store_true', help='Install approved NAV02 R3 only')
    parser.add_argument('--combo-board-approved', action='store_true', help='Install approved CT01 R1, CT02 R1, CT03 R2 and CT04 R3')
    parser.add_argument('--star-teika-approved', action='store_true', help='Install approved ET01 R6, ET02 R7 and ET03 R3')
    parser.add_argument('--tray-reward-approved', action='store_true', help='Install approved MB03 R1 only; Mega awaits new character')
    parser.add_argument('--mega-expansion-approved', action='store_true', help='Install approved MB02 R2 only; charge awaits suction character')
    parser.add_argument('--mega-charge-approved', action='store_true', help='Install explicitly approved MB01 R14 whole charge only')
    parser.add_argument('--move-echo-approved', action='store_true', help='Install approved TE01 R3 and TE02 R5 exactly')
    parser.add_argument('--timoteo-flora-approved', action='store_true', help='Install approved HI03 R1 and HI04 R2; shield and dream rejected')
    parser.add_argument('--cristobal-maite-approved', action='store_true', help='Install explicitly approved HI01 R5 and HI02 R3')
    parser.add_argument('--rayco-inaki-kenji-nieves-approved', action='store_true', help='Install explicitly approved HI05/HI06/HI07 R1 and HI08 R4')
    args = parser.parse_args()
    assert sum([args.crystal_r5,args.lava_ice,args.clean_lava_r2,args.spectre_constellation,args.void_pair,args.metal_pair,args.gold_ceramic_approved,args.neon_ceramic_final,args.invalid_approved,args.combo_board_approved,args.star_teika_approved,args.tray_reward_approved,args.mega_expansion_approved,args.mega_charge_approved,args.move_echo_approved,args.timoteo_flora_approved,args.cristobal_maite_approved,args.rayco_inaki_kenji_nieves_approved]) <= 1, 'Select one approved batch'
    date = '2026-10-13' if args.combo_board_approved or args.star_teika_approved or args.tray_reward_approved or args.mega_expansion_approved or args.mega_charge_approved or args.move_echo_approved or args.timoteo_flora_approved or args.cristobal_maite_approved or args.rayco_inaki_kenji_nieves_approved else ('2026-10-12' if args.spectre_constellation or args.void_pair or args.metal_pair or args.gold_ceramic_approved or args.neon_ceramic_final or args.invalid_approved else '2026-10-11')
    feedback = {'CM01': 'Sí, me gusta y termina naturalmente'} if args.crystal_r5 else FEEDBACK
    raw = ROOT/'app/src/main/res/raw'
    manifest = ROOT/'docs/audio'/('efectos-colocar-cristal-r5-candidatos-2026-10-11.json' if args.crystal_r5 else 'efectos-cristal-madera-revision-2026-10-11.json')
    report_path = ROOT/'docs/audio'/('efectos-colocar-cristal-r5-integracion-2026-10-11.json' if args.crystal_r5 else 'efectos-cristal-madera-integracion-2026-10-11.json')
    out = Path.home()/'.codex/artifacts/elevenlabs'/('integracion-colocar-cristal-r5-2026-10-11' if args.crystal_r5 else 'integracion-cristal-madera-2026-10-11')
    note = 'Usuario aprueba específicamente CM01 R5 con medidas reveladas; contrato particular pendiente.' if args.crystal_r5 else 'Usuario aprueba específicamente CM02 R2, CM03 R2 y CM04 R3 con medidas reveladas; contrato particular pendiente.'
    if args.lava_ice:
        feedback = {ident:'Sí, me gusta y termina naturalmente' for ident in ['LH01','LH03','LH04']}
        manifest = ROOT/'docs/audio/efectos-lava-hielo-revision-2026-10-11.json'
        report_path = ROOT/'docs/audio/efectos-lava-hielo-integracion-2026-10-11.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-lava-hielo-2026-10-11'
        note = 'Usuario aprueba específicamente LH01 R3, LH03 R2 y LH04 R1; LH02 no aprobado; contrato particular pendiente.'
    elif args.clean_lava_r2:
        feedback = {'LH02':'Sí, me gusta y termina naturalmente'}
        manifest = ROOT/'docs/audio/efectos-limpiar-lava-r2-candidatos-2026-10-11.json'
        report_path = ROOT/'docs/audio/efectos-limpiar-lava-r2-integracion-2026-10-11.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-limpiar-lava-r2-2026-10-11'
        note = 'Usuario aprueba específicamente LH02 R2 tras corrección de ruido/aspereza; contrato particular pendiente.'
    elif args.spectre_constellation:
        feedback = {ident:'Sí, me gusta y termina naturalmente' for ident in ['EC01','EC02','EC04']}
        feedback['EC03']='Sí, me gusta y la combinación es cómoda'
        manifest = ROOT/'docs/audio/efectos-espectro-constelacion-revision-usuario-2026-10-12.json'
        report_path = ROOT/'docs/audio/efectos-espectro-constelacion-integracion-2026-10-12.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-espectro-constelacion-2026-10-12'
        note = 'Usuario aprueba EC01 R6, EC02 R7, EC03/EC04 R8 con medidas reveladas, incluida combinación .45/.65 tras 180 ms; contrato particular pendiente.'
    elif args.void_pair:
        feedback = {ident:'Sí, me gusta y termina naturalmente' for ident in ['VM01','VM02']}
        manifest = ROOT/'docs/audio/efectos-vacio-metal-revision-2026-10-12.json'
        report_path = ROOT/'docs/audio/efectos-vacio-integracion-2026-10-12.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-vacio-2026-10-12'
        note = 'Usuario aprueba VM01 R1 y VM02 R2 con medidas reveladas; Metal no aprobado en esta entrega; contrato particular pendiente.'
    if args.metal_pair:
        feedback = {ident:'Sí, me gusta y termina naturalmente' for ident in ['VM03','VM04']}
        manifest = ROOT/'docs/audio/efectos-vacio-metal-revisor-metal-r3-2026-10-12.json'
        report_path = ROOT/'docs/audio/efectos-metal-integracion-2026-10-12.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-metal-2026-10-12'
        note = 'Usuario aprueba VM03 R3 y VM04 R2 con medidas reveladas y prueba de turbina; contrato particular pendiente.'
    if args.gold_ceramic_approved:
        feedback = {'NDC02':'Sí, me gusta y la repetición es cómoda','NDC03':'Sí, me gusta y termina naturalmente','NDC05':'Sí, me gusta y termina naturalmente'}
        manifest = ROOT/'docs/audio/efectos-neon-dorado-ceramica-revision-2026-10-12.json'
        report_path = ROOT/'docs/audio/efectos-dorado-limpiar-ceramica-integracion-2026-10-12.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-dorado-limpiar-ceramica-2026-10-12'
        note = 'Usuario aprueba NDC02 R4, NDC03 R3 y NDC05 R1 con medidas reveladas; Neón y colocar Cerámica pendientes; contrato particular pendiente.'
    if args.neon_ceramic_final:
        feedback = {'NDC01':'Sí, me gusta y termina naturalmente','NDC04':'Sí, me gusta y la repetición es cómoda'}
        manifest = ROOT/'docs/audio/efectos-neon-ceramica-revision-usuario-2026-10-12.json'
        report_path = ROOT/'docs/audio/efectos-neon-colocar-ceramica-integracion-2026-10-12.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-neon-colocar-ceramica-2026-10-12'
        note = 'Usuario aprueba NDC01 R5 y NDC04 R4 con medidas reveladas; grupo de materiales completo; contrato particular pendiente.'
    if args.invalid_approved:
        feedback = {'NAV02':'Sí, me gusta y la repetición es cómoda'}
        manifest = ROOT/'docs/audio/efectos-navegacion-revision-2026-10-12.json'
        report_path = ROOT/'docs/audio/efectos-invalid-integracion-2026-10-12.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-invalid-2026-10-12'
        note = 'Usuario aprueba NAV02 R3 repetición cómoda con medidas reveladas; comenzar e iris pendientes de nuevo carácter; contrato particular pendiente.'
    if args.combo_board_approved:
        feedback = {'CT01':'Sí, me gusta y la progresión es cómoda',**{ident:'Sí, me gusta y termina naturalmente' for ident in ['CT02','CT03','CT04']}}
        manifest = ROOT/'docs/audio/efectos-combo-tablero-revision-2026-10-13.json'
        report_path = ROOT/'docs/audio/efectos-combo-tablero-integracion-2026-10-13.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-combo-tablero-2026-10-13'
        note = 'Usuario aprueba CT01 R1 progresión cómoda, CT02 R1/CT03 R2/CT04 R3 finales naturales con medidas reveladas; promoción particular pendiente.'
    if args.star_teika_approved:
        feedback = {'ET01':'Sí, me gusta y la repetición es cómoda','ET02':'Sí, me gusta y la repetición es cómoda','ET03':'Sí, me gusta y termina naturalmente'}
        manifest = ROOT/'docs/audio/efectos-estrellas-teikas-revision-usuario-2026-10-13.json'
        report_path = ROOT/'docs/audio/efectos-estrellas-teikas-integracion-2026-10-13.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-estrellas-teikas-2026-10-13'
        note = 'Usuario aprueba ET01 R6/ET02 R7 repetición cómoda y ET03 R3 final natural con medidas reveladas; promoción particular pendiente.'
    if args.tray_reward_approved:
        feedback = {'MB03':'Sí, me gusta y la repetición es cómoda'}
        manifest = ROOT/'docs/audio/efectos-mega-bandeja-v2-2026-10-13.json'
        report_path = ROOT/'docs/audio/efectos-bandeja-integracion-2026-10-13.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-bandeja-2026-10-13'
        note = 'Usuario aprueba MB03 R1 repetición cómoda con medidas reveladas; carga y expansión Mega pendientes de magia luminosa; promoción particular pendiente.'
    if args.mega_expansion_approved:
        feedback = {'MB02':'Sí, me gusta y termina naturalmente'}
        manifest = ROOT/'docs/audio/efectos-mega-magia-revision-usuario-2026-10-13.json'
        report_path = ROOT/'docs/audio/efectos-mega-expansion-integracion-2026-10-13.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-mega-expansion-2026-10-13'
        note = 'Usuario aprueba MB02 R2 expansión luminosa con final natural; carga MB01 R6 rechazada, nueva dirección succión mágica; promoción particular pendiente.'
    if args.mega_charge_approved:
        feedback = {'MB01':'Sí, ese es el carácter y la combinación es natural'}
        manifest = ROOT/'docs/audio/efectos-mega-giro-rayos-revision-usuario-2026-10-13.json'
        report_path = ROOT/'docs/audio/efectos-mega-carga-integracion-2026-10-13.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-mega-carga-2026-10-13'
        note = 'Usuario aprueba MB01 R14 giro/rayos/compresión y combinación natural con expansión/fusión nominales; sincronía física Android pendiente; promoción particular pendiente.'
    if args.move_echo_approved:
        feedback = {ident:'Sí, me gusta y termina naturalmente' for ident in ['TE01','TE02']}
        manifest = ROOT/'docs/audio/efectos-traslado-eco-revision-2026-10-13.json'
        report_path = ROOT/'docs/audio/efectos-traslado-eco-integracion-2026-10-13.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-traslado-eco-2026-10-13'
        note = 'Usuario aprueba TE01 R3 y TE02 R5 con final natural tras revelar colas y repetir a ganancias0,9/0,68; promoción particular pendiente.'
    if args.timoteo_flora_approved:
        feedback = {'HI03':'Sí, me gusta y la repetición es cómoda','HI04':'Sí, me gusta y termina naturalmente'}
        manifest = ROOT/'docs/audio/efectos-habilidades-iniciales-revision-2026-10-13.json'
        report_path = ROOT/'docs/audio/efectos-timoteo-flora-integracion-2026-10-13.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-timoteo-flora-2026-10-13'
        note = 'Usuario aprueba HI03 R1 repetición cómoda y HI04 R2 final natural con duración/correspondencia visual reveladas; sincronía física y promoción particular pendientes.'
    if args.cristobal_maite_approved:
        feedback = {'HI01':'Sí, ese es el carácter y termina naturalmente','HI02':'Sí, me gusta y termina naturalmente'}
        manifest = ROOT/'docs/audio/efectos-cristobal-maite-revision-usuario-2026-10-13.json'
        report_path = ROOT/'docs/audio/efectos-cristobal-maite-integracion-2026-10-13.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-cristobal-maite-2026-10-13'
        note = 'Usuario aprueba HI01 R5 arcade con cola revelada y HI02 R3 celesta, finales naturales; sincronía física y promoción particular pendientes.'
    if args.rayco_inaki_kenji_nieves_approved:
        feedback = {ident:'Sí, me gusta y termina naturalmente' for ident in ['HI05','HI06','HI07','HI08']}
        manifest = ROOT/'docs/audio/efectos-rayco-inaki-kenji-nieves-revision-2026-10-13.json'
        report_path = ROOT/'docs/audio/efectos-rayco-inaki-kenji-nieves-integracion-2026-10-13.json'
        out = Path.home()/'.codex/artifacts/elevenlabs/integracion-rayco-inaki-kenji-nieves-2026-10-13'
        note = 'Usuario aprueba HI05/HI06/HI07 R1 y HI08 R4 con medidas y limitaciones temporales reveladas; sincronía física y promoción particular pendientes.'
    expected_count = 148 if args.combo_board_approved or args.star_teika_approved or args.tray_reward_approved or args.mega_expansion_approved or args.mega_charge_approved or args.move_echo_approved or args.timoteo_flora_approved or args.cristobal_maite_approved or args.rayco_inaki_kenji_nieves_approved else 146
    assert not out.exists() and not report_path.exists(), 'Never overwrite backups or repeat integration'
    batch = json.loads(manifest.read_text(encoding='utf-8'))
    registry_path = ROOT/'docs/audio/licencias/registro.json'
    registry = json.loads(registry_path.read_text(encoding='utf-8'))
    original_registry = copy.deepcopy(registry)
    baseline = {f.name: sha(f) for f in raw.iterdir() if f.suffix in ['.ogg','.mp3','.wav']}
    assert len(baseline) == expected_count and set(baseline) == {e['archivo'] for e in registry['entradas']}, 'Validate audio inventory before any mutation'
    assert all(baseline[e['archivo']]==e['sha256'] for e in registry['entradas']), 'Verify complete registry before mutation'
    tracks = [t for t in batch['tracks'] if t['id'] in feedback]
    assert {t['id'] for t in tracks} == set(feedback)
    prepared = []
    report = {'date': date, 'backup': str(out/'backup'), 'generation_requests': 0,
        'pipeline': 'Whole approved PCM16 WAV copied byte-identically; no gain, trimming, fades or processing',
        'acceptance_basis': 'Explicit user listening after measured deviations disclosed; flags retained',
        'android_mix_tested': False, 'build': 'pending', 'unit_tests': 'pending', 'tracks': []}
    # Validate all selected source identities, current assets and destination formats first.
    for t in tracks:
        for kind in ['source','preview','receipt']: assert sha(t[kind]) == t[kind+'_sha256']
        old = raw/t['resource']; new = old.with_suffix('.wav'); preview = Path(t['preview'])
        assert old.resolve().parent == new.resolve().parent == raw.resolve(), 'Keep mutations inside raw'
        assert old.is_file() and not new.exists() and sha(old) == t['reference_sha256']
        probe = json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',str(preview)]))['streams'][0]
        assert probe['codec_name'] == 'pcm_s16le' and int(probe['sample_rate']) == 44100 and int(probe['channels']) == 2
        pcm = decode(preview)
        assert len(pcm) == t['frames'] == len(decode(t['source'])) and t['frames'] > 0
        assert metrics(pcm,44100)['clipped_samples'] == 0
        entry = next(e for e in registry['entradas'] if e['archivo'] == old.name)
        previous = copy.deepcopy(entry)
        entry.clear(); entry.update(archivo=new.name,sha256=t['preview_sha256'],origen='elevenlabs',
            licencia='ElevenLabs SFX Creator activo; evidencia particular de promoción pendiente',
            modelo='eleven_text_to_sound_v2',generado=date,
            evidencia=report_path.relative_to(ROOT).as_posix(),
            original=t['source'],original_sha256=t['source_sha256'],
            recibo=t['receipt'],recibo_sha256=t['receipt_sha256'],
            procesado=report['pipeline'],estado='VERIFICAR',
            nota=note)
        report['tracks'].append({'id':t['id'],'resource':new.name,'old_resource':old.name,
            'old_sha256':sha(old),'old_registry_entry':previous,'installed_sha256':t['preview_sha256'],
            'source':t['source'],'source_sha256':t['source_sha256'],
            'preview':t['preview'],'preview_sha256':t['preview_sha256'],
            'receipt':t['receipt'],'receipt_sha256':t['receipt_sha256'],
            'feedback':feedback[t['id']],'user_approved':True,'integrated':True,
            'original_technical_flags':t['technical_flags'],'decoded_frames':len(pcm),
            'duration_seconds':len(pcm)/44100,'sample_rate':44100,'channels':2,'metrics':metrics(pcm,44100)})
        prepared.append((t,old,new,preview))
    backup = out/'backup'; backup.mkdir(parents=True)
    shutil.copy2(registry_path,backup/registry_path.name); shutil.copy2(manifest,backup/manifest.name)
    for _,old,_,_ in prepared: shutil.copy2(old,backup/old.name)
    for t,old,new,preview in prepared:
        shutil.copy2(preview,new)
        assert sha(new) == t['preview_sha256']
        assert old.resolve().parent == raw.resolve()
        old.unlink()
        t.update(user_approved=True,integrated=True,user_feedback=feedback[t['id']],
            installed_resource=new.name,installed_sha256=t['preview_sha256'],
            user_listening_approved_despite_flags=True)
    if args.invalid_approved:
        reasons={'NAV01':'Quiero cambiar el carácter','NAV03':'Quiero que el enfoque sea mas un sonido de abrirse y cerrarse un circulo, dame ideas o opciones'}
        for t in batch['tracks']:
            if t['id'] in reasons:t.update(user_approved=False,integrated=False,user_feedback=reasons[t['id']],listening_status='rejected; character decision pending')
    elif args.gold_ceramic_approved:
        reasons={'NDC01':'Quiero cambiar el carácter','NDC04':'Lo quiero más corto y seco'}
        for t in batch['tracks']:
            if t['id'] in reasons:t.update(user_approved=False,integrated=False,user_feedback=reasons[t['id']],listening_status='rejected; regenerate full event')
        batch['neon_character_choice']='Pulso luminoso: destello suave y cierre seco'
    elif args.void_pair:
        reasons={'VM03':'Quiero más carácter metálico','VM04':'Lo quiero más seco y compacto'}
        for t in batch['tracks']:
            if t['id'] in reasons:t.update(user_approved=False,integrated=False,user_feedback=reasons[t['id']],listening_status='rejected; regenerate full event')
    elif args.rayco_inaki_kenji_nieves_approved:
        for t in batch['tracks']:
            t['listening_status']='approved; natural ending accepted with disclosed timing limitations'
    elif args.cristobal_maite_approved:
        for t in batch['tracks']:
            if t['id'] in ['HI01','HI02']:
                t['listening_status']='approved; chosen character and natural ending accepted'
    elif args.timoteo_flora_approved:
        for t in batch['tracks']:
            if t['id'] in ['HI01','HI02']:
                t.update(user_approved=False,integrated=False,user_feedback='Quiero cambiar el carácter',listening_status='rejected; character decision pending')
    elif args.move_echo_approved:
        for t in batch['tracks']:
            t['listening_status']='approved; natural ending accepted despite measured duration deviations'
    elif args.mega_charge_approved:
        next(t for t in batch['tracks'] if t['id']=='MB01')['listening_status']='approved; character and nominal combination accepted'
    elif args.mega_expansion_approved:
        rejected = next(t for t in batch['tracks'] if t['id']=='MB01')
        rejected.update(user_approved=False,integrated=False,user_feedback='Quiero cambiar el carácter',listening_status='rejected; regenerate full event')
        batch['charge_character_choice']='Succión mágica: remolino grave, subida y compresión'
    elif args.tray_reward_approved:
        for t in batch['tracks']:
            if t['id'] in ['MB01','MB02']:
                t.update(user_approved=False,integrated=False,user_feedback='Quiero cambiar el carácter',listening_status='rejected; regenerate full event')
        batch['mega_character_choice']='Magia luminosa: carga tonal creciente y estallido brillante redondo'
    elif not args.crystal_r5 and not args.clean_lava_r2 and not args.spectre_constellation and not args.metal_pair and not args.neon_ceramic_final and not args.combo_board_approved and not args.star_teika_approved:
        rejected = next(t for t in batch['tracks'] if t['id']==('LH02' if args.lava_ice else 'CM01'))
        rejected.update(user_approved=False,integrated=False,user_feedback='Percibo ruido o aspereza' if args.lava_ice else 'Sigue demasiado largo o resonante',
                    listening_status='rejected; regenerate full event')
    excluded = {old.name for _,old,_,_ in prepared}
    others = {name:digest for name,digest in baseline.items() if name not in excluded}
    assert all(sha(raw/name)==digest for name,digest in others.items())
    assert len([f for f in raw.iterdir() if f.suffix in ['.ogg','.mp3','.wav']]) == len(baseline) == expected_count
    old_entries = [e for e in original_registry['entradas'] if e['archivo'] not in excluded]
    new_names = {new.name for _,_,new,_ in prepared}
    assert old_entries == [e for e in registry['entradas'] if e['archivo'] not in new_names]
    report.update(installed_resources=len(tracks),other_resources_and_records_preserved=len(others))
    batch.update(installed_resources=sum(bool(t.get('integrated') and t.get('user_approved')) for t in batch['tracks']),acceptance_record=report_path.relative_to(ROOT).as_posix())
    registry['actualizado']=date
    registry_path.write_text(json.dumps(registry,ensure_ascii=False,indent=1)+'\n',encoding='utf-8')
    save(manifest,batch);save(report_path,report)
    print(f'{len(tracks)} approved WAVs installed exactly; {len(others)} other resources preserved')


if __name__ == '__main__':
    main()
