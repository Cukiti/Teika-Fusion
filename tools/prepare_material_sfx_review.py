"""Prepare material candidates for listening; no installed resources are edited."""
import argparse
import html
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import urllib.request
from produce_core_sfx_candidates import ROOT, sha, decode, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--report-file', type=Path, required=True)
    p.add_argument('--previous-report', type=Path, help='Preserve and compare the prior candidate, not an installed resource')
    p.add_argument('--previous-ids', nargs='+', help='Show rejected comparisons only for these IDs')
    p.add_argument('--output-directory', type=Path, help='Separate listening folder for sources from multiple production batches')
    p.add_argument('--serve', action='store_true')
    args = p.parse_args()
    report = json.loads(args.report_file.read_text(encoding='utf-8'))
    assert report['tracks'] and len({t['id'] for t in report['tracks']}) == len(report['tracks'])
    pair = report.get('review_pair')
    if pair:
        assert {pair['place_id'],pair['clear_id']}.issubset({t['id'] for t in report['tracks']})
        assert 0 <= pair['delay_seconds'] <= 5 and 0 <= pair['place_volume'] <= 1 and 0 <= pair['clear_volume'] <= 1
        assert .5 <= pair.get('place_rate',1) <= 2 and .5 <= pair.get('clear_rate',1) <= 2
    previous = {}
    assert not args.previous_ids or args.previous_report, 'Previous IDs require a report'
    if args.previous_report:
        old = json.loads(args.previous_report.read_text(encoding='utf-8'))
        previous = {t['id']: t for t in old['tracks'] if not args.previous_ids or t['id'] in args.previous_ids}
        assert set(previous).issubset({t['id'] for t in report['tracks']})
        assert set(previous) == (set(args.previous_ids) if args.previous_ids else {t['id'] for t in report['tracks']})
    out = args.output_directory.resolve() if args.output_directory else Path(report['tracks'][0]['source']).parent
    out.mkdir(parents=True, exist_ok=True)
    comparisons, cards, profiles = {}, [], {}
    urls = []
    if pair:
        pair = dict(pair)
        pair['extra_cues'] = [dict(cue) for cue in pair.get('extra_cues', [])]
        for index, cue in enumerate(pair['extra_cues']):
            assert Path(cue['resource']).name == cue['resource'], 'Use a raw resource basename'
            assert .5 <= cue['rate'] <= 2 and 0 <= cue['volume'] <= 1 and 0 <= cue['delay_seconds'] <= 5
            source = ROOT/'app/src/main/res/raw'/cue['resource']
            assert source.is_file() and sha(source) == cue['sha256'], 'Context cue changed'
            local = out/('context_'+str(index)+source.suffix)
            shutil.copy2(source, local)
            assert sha(local) == cue['sha256']
            cue['url'] = local.name
            urls.append(local.name)
    for t in report['tracks']:
        for kind in ['source','receipt','preview']:
            assert sha(t[kind]) == t[kind+'_sha256']
        assert len(decode(t['source'])) == len(decode(t['preview'])) == t['frames']
        assert t['preview_metrics']['clipped_samples'] == 0
        local_preview = out/(t['id']+'_escucha.wav')
        if local_preview.resolve() != Path(t['preview']).resolve():
            if local_preview.exists():
                assert sha(local_preview) == t['preview_sha256'], 'Do not overwrite a different preview'
            else:
                shutil.copy2(t['preview'], local_preview)
            assert sha(local_preview) == t['preview_sha256']
        reference = ROOT/'app/src/main/res/raw'/t['resource']
        if t.get('integrated'):
            reference = ROOT/'app/src/main/res/raw'/t['installed_resource']
            assert sha(reference) == t['installed_sha256'] == t['preview_sha256'], 'Installed acceptance changed'
        else:
            assert sha(reference) == t['reference_sha256'], 'Comparison changed'
        comparison = out/(t['id']+'_actual'+reference.suffix)
        shutil.copy2(reference, comparison)
        comparisons[t['id']] = comparison.name
        urls += [local_preview.name, comparison.name]
        m = t['metrics']
        flags = []
        for f in t['technical_flags']:
            flags.append({'late attack relative to design':'Ataque posterior al objetivo orientativo.',
                'active content exceeds design decay target':'Contenido activo más largo que el objetivo orientativo.',
                'source clipping':'La decodificación flotante de la fuente supera 0 dBFS; el WAV con ganancia no satura. Esto por sí solo no demuestra distorsión audible.',
                'terminal sample above -50 dBFS; inspect potential abrupt end':'Señal al final de la fuente: escuchar si hay un corte.',
                'preview terminal sample above -50 dBFS; inspect potential abrupt end':'Señal al final del WAV de escucha: revisar un posible corte.'}.get(f,f))
        status = ' '.join(flags) or 'Sin banderas en estas medidas; falta validar por escucha.'
        if t.get('integrated') and t.get('user_approved'):
            status = 'Aprobado por escucha e integrado íntegro. Medidas originales conservadas: ' + (' '.join(flags) or 'Sin banderas en estas medidas.')
        ident = t['id']
        place = t['resource'].startswith('snd_place_')
        profiles[ident] = {'action': 'place' if place else 'clear',
            'rates': [.8,1,1.4] if t['resource'].startswith('snd_place_crystal.') else [.85,1,1.2] if t['resource'].startswith('snd_place_wood.') else [.8,1,1.2],
            'repeat_rate': .85 if t['resource'].startswith('snd_place_wood.') else 1,
            'low_rates': [.75,.8] if t['resource'].startswith('snd_clear_crystal.') else []}
        if t['resource'].startswith('snd_place_metal.'):
            profiles[ident].update(rates=[.7,.72,1,1.25],repeat_rate=.8)
        if t['resource'].startswith('snd_clear_void.'):
            profiles[ident]['low_rates']=[.8,1.1,1.15,1.25]
        if Path(t['resource']).stem in ['snd_play_launch','snd_invalid','snd_iris_transition','snd_iris_open','snd_flora_launch']:
            profiles[ident]={'action':'ui','volume':.44 if Path(t['resource']).stem=='snd_invalid' else 1}
        reward_profiles={
            'snd_combo':([1,1.085,1.17,1.34,1.68,2],1.34,1),
            'snd_combo_grace':([1],1,.8),
            'snd_pure_line':([1],1,.595),
            'snd_perfect_clear':([1],1,.9),
            'snd_target_collect':([1],1,.68),
            'snd_teika_appear':([.9,1,1.2],1,1),
            'snd_teika_seed_break':([1],1,.9),
            'snd_mega_collapse':([1],1,1),
            'snd_mega':([1],1,1),
            'snd_tray_reward':([1],1,.64),
            'snd_board_shift':([1],1,.9),
            'snd_ability_ready':([1],1,.68),
            'snd_ability_shield':([1],1,.9),
            'snd_ability_dream':([1],1,.9),
            'snd_ability_slide':([1],1,.9),
            'snd_ability_bloom':([1],1,.9),
            'snd_ability_energize':([1],1,.9),
            'snd_ability_fire':([1],1,.9),
            'snd_ability_slash':([1],1,.9),
            'snd_ability_icicle':([1],1,.9),
            'snd_ability_ghost':([1],1,0.9),
            'snd_ability_feast':([1],1,0.9),
            'snd_ability_miasma':([1],1,0.9),
            'snd_ability_wish':([1],1,0.9),
            'snd_ability_birth':([1],1,0.9),
            'snd_ability_eclipse':([1],1,0.9),
            'snd_purchase':([1],1,0.8),
            'snd_teikoin_gain':([1],1,0.8),
            'snd_character_unlock':([1],1,1),
            'snd_game_over':([1],1,1),
            'snd_level_victory':([1],1,1),
            'snd_life_lost':([1],1,1),
            'snd_no_lives':([1],1,1),
            'snd_rank_up':([1],1,1),
            'snd_streak':([1],1,0.9)}
        reward=reward_profiles.get(Path(t['resource']).stem)
        if reward:
            profiles[ident]={'action':'reward','rates':reward[0],'repeat_rate':reward[1],'volume':reward[2]}
        if Path(t['resource']).stem=='snd_teika_appear':
            profiles[ident]['rate_volumes']=[.28,1,.5]
        take_label = 'Toma aprobada:' if t.get('user_approved') and t.get('integrated') else 'Candidato:'
        prior_card = ''
        if ident in previous:
            prior = previous[ident]
            assert sha(prior['preview']) == prior['preview_sha256']
            prior_copy = out/(ident+'_anterior.wav')
            shutil.copy2(prior['preview'], prior_copy)
            assert sha(prior_copy) == prior['preview_sha256']
            urls.append(prior_copy.name)
            prior_label = html.escape(report.get('previous_comparison_label', 'Toma anterior rechazada:'))
            prior_card = f'<p>{prior_label}</p><audio controls preload="none" src="{prior_copy.name}"></audio>'
        cards.append(f'''<section><h2>{ident} · {html.escape(t['title'])}</h2>
        <p>Archivo completo: {m['duration_seconds']:.3f} s. Contenido medido: {m['active_start_seconds']*1000:.1f}–{m['active_end_seconds']*1000:.1f} ms.</p>
        <p class="status">{html.escape(status)}</p><p>{take_label}</p>
        <audio controls preload="none" src="{ident}_escucha.wav"></audio>
        {prior_card}
        <p>{'Referencia de nivel y timbre; este cue aún no está instalado:' if t.get('reference_only') and not t.get('integrated') else 'Versión actualmente instalada:'}</p><audio controls preload="none" src="{comparison.name}"></audio>
        <p><button onclick="pattern('{ident}',false)">Candidato: tonos y repetición/capas</button>
        <button onclick="pattern('{ident}',true)">Comparar actual</button></p></section>''')
    music = 'bgm_gameplay.ogg'
    shutil.copy2(ROOT/'app/src/main/res/raw'/music,out/music)
    urls.append(music)
    page = '''<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
    <title>Cristal y Madera · efectos</title><style>body{font:17px system-ui;background:#111829;color:#e7edf7;max-width:950px;margin:30px auto;padding:18px;line-height:1.5}section{background:#1d2940;border-radius:16px;padding:22px;margin:18px 0}button{padding:12px;background:#dbebff;color:#152138;border:0;border-radius:8px;margin:4px;cursor:pointer}audio{width:100%}.status{color:#ffd188}</style>
    <h1>Cristal y Madera: colocar y limpiar</h1><p>Cuatro candidatos completos según el bloque equipado. Comprueba el material, la comodidad al repetir y el final natural. Aún no están aprobados ni instalados.</p>
    <p>Colocar: tonos compartidos y cinco contactos. Limpiar: aislado y cuatro capas a 0/42/84/126 ms con rates 1/1,26/1,5/2. Cristal incluye tonos bajos. Las medidas estiman señal sobre el pico menos 40 dB, no duración percibida. Las banderas se conservan aunque te guste una toma.</p>
    <button onclick="stopAll()">Detener</button><p>Música de contexto opcional (volumen 0,28):</p>
    <audio id="music" controls loop preload="none" src="bgm_gameplay.ogg"></audio>
    <p>Esta prueba usa Web Audio; no reproduce límites de voces, prioridades ni latencia de SoundPool Android.</p>CARDS
    PAIR_CONTROLS
    <script>let ctx,sources=[];const comparisons=COMPARISONS,profiles=PROFILES,pair=PAIR_DATA,cache=new Map();document.querySelector('#music').volume=.28;
    function stopSounds(){for(const s of sources){try{s.stop()}catch{}}sources=[]}
    function stopAll(){stopSounds();document.querySelectorAll('audio').forEach(a=>a.pause())}
    async function buffer(url){if(!cache.has(url)){const r=await fetch(url);if(!r.ok)throw Error('HTTP '+r.status);cache.set(url,await ctx.decodeAudioData(await r.arrayBuffer()))}return cache.get(url)}
    function play(b,t,r=1,v=1){const s=ctx.createBufferSource(),g=ctx.createGain();s.buffer=b;s.playbackRate.value=r;g.gain.value=v;s.connect(g);g.connect(ctx.destination);sources.push(s);s.onended=()=>{sources=sources.filter(x=>x!==s)};s.start(t)}
    async function pattern(id,current){try{if(!ctx)ctx=new AudioContext();await ctx.resume();stopSounds();const b=await buffer(current?comparisons[id]:id+'_escucha.wav');let t=ctx.currentTime+.08;
    const p=profiles[id];
    if(p.action==='reward'){for(const [i,r] of p.rates.entries()){play(b,t,r,p.rate_volumes?.[i]??p.volume);t+=b.duration/r+.2}t+=.5;for(let i=0;i<5;i++)play(b,t+i*(b.duration/p.repeat_rate+.15),p.repeat_rate,p.volume)}
    else if(p.action==='ui'){play(b,t,1,p.volume);t+=b.duration+.5;for(let i=0;i<5;i++)play(b,t+i*(b.duration+.15),1,p.volume)}
    else if(p.action==='place'){p.rates.forEach((r,i)=>play(b,t+i*(b.duration/.8+.2),r));t+=p.rates.length*(b.duration/.8+.2)+.5;for(let i=0;i<5;i++)play(b,t+i*.22,p.repeat_rate,.2)}
    else{play(b,t);t+=b.duration+.5;if(p.low_rates.length){p.low_rates.forEach((r,i)=>play(b,t+i*(b.duration/.75+.2),r,.3));t+=p.low_rates.length*(b.duration/.75+.2)+.5}[1,1.26,1.5,2].forEach((r,i)=>play(b,t+i*.042,r,[1,.8,.66,.55][i]))}}
    catch(e){alert('No se pudo reproducir: '+e.message)}}
    async function pairPattern(current){try{if(!ctx)ctx=new AudioContext();await ctx.resume();stopSounds();const url=id=>current?comparisons[id]:id+'_escucha.wav';const extra=pair.extra_cues??[];const [a,b,...buffers]=await Promise.all([buffer(url(pair.place_id)),buffer(url(pair.clear_id)),...extra.map(c=>buffer(c.url))]);const t=ctx.currentTime+.08;play(a,t,pair.place_rate??1,pair.place_volume);play(b,t+pair.delay_seconds,pair.clear_rate??1,pair.clear_volume);extra.forEach((c,i)=>play(buffers[i],t+c.delay_seconds,c.rate,c.volume))}catch(e){alert('No se pudo reproducir: '+e.message)}}
    </script></html>'''.replace('CARDS','\n'.join(cards)).replace('COMPARISONS',json.dumps(comparisons)).replace('PROFILES',json.dumps(profiles)).replace('PAIR_DATA',json.dumps(pair))
    page = page.replace('PAIR_CONTROLS', '<section><h2>'+html.escape(pair.get('title','Combinación compartida de Constelación'))+'</h2><button onclick="pairPattern(false)">Probar combinación candidata</button><button onclick="pairPattern(true)">Comparar combinación actual</button></section>' if pair else '')
    page = page.replace('Cristal y Madera · efectos',html.escape(report['group'])+' · efectos').replace('Cristal y Madera: colocar y limpiar',html.escape(report['group']))
    page = page.replace('Cuatro candidatos completos según el bloque equipado.',f"{len(report['tracks'])} tomas completas según el bloque equipado.")
    if all(p['action']=='ui' for p in profiles.values()):
        page=page.replace('según el bloque equipado','de navegación').replace('Comprueba el material,','Comprueba la intención,').replace('Candidato: tonos y repetición/capas','Candidato: aislado y repetición')
    if all(p['action']=='reward' for p in profiles.values()):
        page=page.replace('según el bloque equipado','de señales del juego').replace('Comprueba el material,','Comprueba la intención,').replace('Candidato: tonos y repetición/capas','Candidato: tonos reales y repetición')
    if all(t.get('user_approved') and t.get('integrated') for t in report['tracks']):
        page = page.replace('Aún no están aprobados ni instalados.','Todas las tomas están aprobadas por escucha e integradas.')
    if report.get('review_notes'):
        page = page.replace('Colocar: tonos compartidos y cinco contactos. Limpiar: aislado y cuatro capas a 0/42/84/126 ms con rates 1/1,26/1,5/2. Cristal incluye tonos bajos.',html.escape(report['review_notes']))
    integrated = sum(bool(t.get('integrated') and t.get('user_approved')) for t in report['tracks'])
    if integrated:
        page = page.replace('Aún no están aprobados ni instalados.',
            'Las cuatro tomas están aprobadas por escucha e integradas íntegramente; se conservan las medidas originales.' if integrated == len(report['tracks']) else
            f'{integrated} tomas ya están aprobadas e integradas; las demás siguen pendientes de escucha. Cada tarjeta indica su estado.')
    target = out/'escuchar-efectos.html'
    target.write_text(page,encoding='utf-8')
    js = out/'review.js'
    js.write_text(page.split('<script>')[1].split('</script>')[0],encoding='utf-8')
    subprocess.run(['node','--check',str(js)],check=True)
    if args.serve:
        config = out/'revisor-local.json'
        assert not config.exists(), 'Do not start duplicate server'
        with socket.socket() as sock:
            sock.bind(('127.0.0.1',0)); port = sock.getsockname()[1]
        env=os.environ.copy();env.pop('ELEVENLABS_API_KEY',None)
        with (out/'server.log').open('wb') as log:
            proc=subprocess.Popen([sys.executable,'-m','http.server',str(port),'--bind','127.0.0.1','--directory',str(out)],
                stdout=log,stderr=log,env=env,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        base=f'http://127.0.0.1:{port}/'
        save(config,{'pid':proc.pid,'url':base+target.name,'directory':str(out),'credential_included':False})
        # urlopen naturally waits for server startup; retry only read-only local HTTP.
        import time
        for attempt in range(20):
            try:
                with urllib.request.urlopen(base+target.name,timeout=2) as r: assert r.status == 200
                break
            except OSError:
                if attempt == 19: raise
                time.sleep(.2)
        for url in urls:
            with urllib.request.urlopen(base+url,timeout=10) as r: assert r.status == 200 and r.read()
        save(out/'review-verification.json',{'verified_source_preview_receipt_hashes':3*len(report['tracks']),'whole_frame_matches':len(report['tracks']),
            'prior_preview_hash_matches':len(previous),
            'unchanged_reference_hashes':len(report['tracks']),'javascript_syntax':'passed','http_routes':len(urls)+1,
            'auditory_review':'pending','android_tests':'not run; no installed assets changed'})
        print(base+target.name,flush=True)


if __name__ == '__main__':
    main()
