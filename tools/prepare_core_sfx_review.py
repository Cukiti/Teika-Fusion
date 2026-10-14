"""Create a local four-effect reviewer with whole previews and browser playback-rate tests."""
import argparse
import hashlib
import html
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT/'docs/audio/efectos-base-candidatos-2026-10-11.json'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serve', action='store_true')
    parser.add_argument('--report-file', type=Path, default=REPORT)
    parser.add_argument('--include-approved-s02', action='store_true',
                        help='Show previously integrated S02 alongside a three-effect correction batch')
    args = parser.parse_args()
    report = json.loads(args.report_file.read_text(encoding='utf-8'))
    out = Path(report['tracks'][0]['source']).parent
    tracks = list(report['tracks'])
    if args.include_approved_s02:
        assert not any(t['id']=='S02' for t in tracks)
        prior = json.loads((ROOT/'docs/audio/efectos-base-candidatos-r3-2026-10-11.json').read_text(encoding='utf-8'))
        approved = next(t for t in prior['tracks'] if t['id']=='S02').copy()
        assert approved['user_approved'] and approved['integrated']
        approved['comparison_resource'] = approved['installed_resource']
        approved['comparison_sha256'] = approved['installed_sha256']
        shutil.copy2(approved['preview'], out/'S02_escucha.wav')
        tracks.append(approved)
    tracks.sort(key=lambda t:t['id'])
    acceptance_path = ROOT/'docs/audio/efectos-base-aceptacion-2026-10-11.json'
    acceptance = json.loads(acceptance_path.read_text(encoding='utf-8')) if acceptance_path.exists() else {}
    integrated_s02 = acceptance.get('s02_integrated',False) and any(
        t['id']=='S02' and t['source_sha256']==acceptance.get('s02_source_sha256') for t in tracks)
    accepted_hashes={t['source_sha256'] for t in acceptance.get('r4_tracks',[]) if t['user_approved'] and t['integrated']}
    if acceptance.get('s02_integrated'):
        accepted_hashes.add(acceptance['s02_source_sha256'])
    integrated_ids={t['id'] for t in tracks if t['source_sha256'] in accepted_hashes}
    assert len(tracks) == 4
    cards = []
    titles = {'S01':'Tocar · interfaz','S02':'Colocar · bloque Básico','S03':'Limpiar · bloque Básico','S04':'Fusionar · Teikas'}
    comparison_urls = {}
    for track in tracks:
        resource = track.get('comparison_resource',track['resource'])
        reference_hash = track.get('comparison_sha256',track['reference_sha256'])
        reference = ROOT/'app/src/main/res/raw'/resource
        comparison = out/(track['id']+'_actual'+Path(resource).suffix)
        comparison_urls[track['id']] = comparison.name
        comparison_label = 'Recurso actual'
        if reference.exists() and hashlib.sha256(reference.read_bytes()).hexdigest() == reference_hash:
            shutil.copy2(reference, comparison)
        else:
            assert comparison.exists() and hashlib.sha256(comparison.read_bytes()).hexdigest() == reference_hash
            comparison_label = 'Versión anterior del juego (copia previa a integración)'
        comparison_button = 'Probar actual' if comparison_label == 'Recurso actual' else 'Probar anterior'
        measured = track['metrics']
        print(track['id'], 'whole', round(measured['duration_seconds'],4),
              'active', round(measured['active_start_seconds'],4), round(measured['active_end_seconds'],4),
              'terminal dBFS', round(measured['edge_last_sample_dbfs'],1),
              'flags', track['technical_flags'], flush=True)
        labels = {'late attack relative to design': 'Ataque posterior al objetivo',
                  'active content exceeds design decay target': 'Cola activa más larga que la ficha',
                  'terminal sample above -50 dBFS; inspect potential abrupt end': 'Señal en el último instante: revisar posible corte',
                  'source clipping': 'Muestras saturadas'}
        status = '; '.join(labels.get(flag,flag) for flag in track['technical_flags']) or 'Sin banderas en este filtro técnico; escucha pendiente'
        if track['id'] in integrated_ids:
            status = 'Escucha aprobada por el usuario; integrado como WAV íntegro. Mediciones originales conservadas.'
        cards.append(f'''<section><h2>{track['id']} · {titles[track['id']]}</h2>
        <p>Fuente completa: {measured['duration_seconds']:.3f} s. Contenido sobre umbral:
        {measured['active_start_seconds']:.3f}–{measured['active_end_seconds']:.3f} s.</p>
        <p class="status">{html.escape(status)}</p><p>Candidato (solo ganancia constante):</p>
        <audio controls preload="none" src="{track['id']}_escucha.wav"></audio>
        <p>{comparison_label}:</p><audio controls preload="none" src="{comparison.name}"></audio>
        <p><button onclick="pattern('{track['id']}',false)">Probar candidato</button>
        <button onclick="pattern('{track['id']}',true)">{comparison_button}</button></p></section>''')
    for ident in ['gameplay','crystal','sleep']:
        shutil.copy2(ROOT/'app/src/main/res/raw'/('bgm_'+ident+'.ogg'),out/('bgm_'+ident+'.ogg'))
    page = '''<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
    <title>Efectos base v2 · revisión</title><style>
    body{font:17px system-ui;background:#111829;color:#e7edf7;max-width:1000px;margin:30px auto;padding:18px;line-height:1.5}
    section{background:#1d2940;border-radius:16px;padding:22px;margin:18px 0}button,select{padding:12px;background:#dbebff;color:#152138;border:0;border-radius:8px;margin:4px;cursor:pointer}audio{width:100%}.status{color:#ffd188}
    </style><h1>Tocar, colocar, limpiar y fusionar</h1>
    <p>Colocar y limpiar aquí corresponden solo al bloque Básico, de piezas sólidas pulidas.
    Cristal, Madera y los demás estilos conservan sus efectos propios según el bloque equipado.</p>
    <p>Cuatro tomas completas para evaluar el carácter. Siguen existiendo diferencias con las fichas;
    no son efectos definitivos ni están instalados. No aprobar la sustitución por esta escucha de timbre.
    Este revisor no certifica ausencia de ruido ni reproduce el mezclador Android.</p>
    <p>Pruebas: tocar = 16 ticks a 48 ms, rates 1–1,9; colocar = rates 1 / 0,8 / 1,2;
    limpiar = una línea y cuatro capas a 0/42/84/126 ms;
    fusionar = rates 1,15 / 1 / 0,87 / 0,76.</p>
    <button onclick="stopAll()">Detener sonidos</button>
    <h2>Música de contexto opcional</h2><select id="musicSelect" onchange="changeMusic()">
    <option value="gameplay">Menús</option><option value="crystal">Cristal</option><option value="sleep">Sueño</option></select>
    <audio id="music" controls loop preload="none" src="bgm_gameplay.ogg"></audio>
    <p>Música al factor 0,28. La simulación usa factores por cue; no incluye volumen guardado, prioridades,
    límite de voces, lifecycle, ducking ni latencia de SoundPool. Los controles aislados son para escuchar timbre.</p>
    CARDS
    <script>
    let ctx, sources=[]; const comparisonUrls=COMPARISON_URLS; const cache=new Map(); const music=document.querySelector('#music');music.volume=.28;
    function changeMusic(){music.pause();music.src='bgm_'+document.querySelector('#musicSelect').value+'.ogg';music.load();}
    function stopAll(){for(const s of sources){try{s.stop()}catch{}}sources=[];document.querySelectorAll('audio').forEach(a=>a.pause());}
    async function getBuffer(url){if(!cache.has(url)){const r=await fetch(url);if(!r.ok)throw Error('HTTP '+r.status);cache.set(url,await ctx.decodeAudioData(await r.arrayBuffer()))}return cache.get(url)}
    function play(buffer,when,rate=1,volume=1){const s=ctx.createBufferSource(),g=ctx.createGain();s.buffer=buffer;s.playbackRate.value=rate;g.gain.value=volume;s.connect(g);g.connect(ctx.destination);sources.push(s);s.onended=()=>{sources=sources.filter(x=>x!==s)};s.start(when)}
    async function pattern(id,current){try{if(!ctx)ctx=new AudioContext();await ctx.resume();for(const s of sources){try{s.stop()}catch{}}sources=[];
    const b=await getBuffer(current?comparisonUrls[id]:id+'_escucha.wav'),t=ctx.currentTime+.08;
    if(id==='S01'){for(let i=0;i<16;i++)play(b,t+i*.048,1+i/15*.9,.35*.8)}
    if(id==='S02'){[1,.8,1.2].forEach((r,i)=>play(b,t+i*(b.duration/.8+.35),r))}
    if(id==='S03'){play(b,t);const later=t+b.duration+.7;[1,1.26,1.5,2].forEach((r,i)=>play(b,later+i*.042,r,[1,.8,.66,.55][i]))}
    if(id==='S04'){[1.15,1,.87,.76].forEach((r,i)=>play(b,t+i*(b.duration/.76+.4),r))}
    }catch(e){alert('No se pudo reproducir: '+e.message)}}
    </script></html>'''.replace('CARDS','\n'.join(cards)).replace('COMPARISON_URLS',json.dumps(comparison_urls))
    if len(integrated_ids)==4:
        page = page.replace('no son efectos definitivos ni están instalados. No aprobar la sustitución por esta escucha de timbre.',
                            'Las cuatro tomas fueron aprobadas por escucha e integradas como WAV íntegros. Las medidas originales se conservan; no se atribuye mezcla audible Android al revisor.')
    elif integrated_s02:
        page = page.replace('no son efectos definitivos ni están instalados. No aprobar la sustitución por esta escucha de timbre.',
                            'Solo S02 fue aprobado e integrado; S01, S03 y S04 siguen con correcciones técnicas pendientes. No aprobar su sustitución por esta escucha de timbre.')
    target=out/'escuchar-efectos.html'
    target.write_text(page,encoding='utf-8')
    if args.serve:
        config=out/'revisor-local.json'
        assert not config.exists(), 'Reviewer config already exists; do not start duplicate servers'
        with socket.socket() as sock:
            sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
        env=os.environ.copy();env.pop('ELEVENLABS_API_KEY',None)
        with (out/'server.log').open('wb') as log:
            process=subprocess.Popen([sys.executable,'-m','http.server',str(port),'--bind','127.0.0.1',
                                      '--directory',str(out)],stdout=log,stderr=log,env=env,
                                     creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        data={'pid':process.pid,'url':f'http://127.0.0.1:{port}/escuchar-efectos.html','directory':str(out),'credential_included':False}
        config.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
        print(data['url'],flush=True)
    else:
        print(target)


if __name__=='__main__':
    main()
