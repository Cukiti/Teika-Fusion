"""Build a complete renewal-v2 checklist from installed audio, registry and accepted hashes."""
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT/'app/src/main/res/raw'
OUT = ROOT/'docs/audio'
GROUPS = ['Interfaz y navegación','Colocación y limpieza por material','Tablero y Teikas',
         'Habilidades y Eco de carga','Economía','Resultados y progreso',
          'Firmas y selección de biomas','Mecánicas y objetos']
MATERIALS = {'classic':'Básico','crystal':'Cristal','wood':'Madera','lava':'Lava','ice':'Hielo',
             'spectre':'Espectro','constellation':'Constelación','void':'Vacío','metal':'Metal',
             'neon':'Neón','gold':'Dorado','ceramic':'Cerámica'}
BIOMES = {'crystal':'Cristal','sleep':'Bosque del Sueño','fear':'Pasaje Encantado','garden':'Jardín',
          'storm':'Tormenta','volcano':'Volcán','ninja':'Dojo en Sombras','glacier':'Glaciar',
          'ghost':'Mansión Espectral','glutton':'Cocina Voraz','toxic':'Pantano Tóxico',
          'star':'Cielo Estelar','king':'Trono Real','galactic':'Espacio Profundo'}
LABELS = {
 'ui_click':'Clic compartido de botones, pestañas, contador y tutorial',
 'play_launch':'Comenzar partida','invalid':'Movimiento rechazado','iris_transition':'Transición iris',
 'flora_launch':'Lanzamiento del proyectil de Flora',
 'iris_open':'Apertura del círculo de transición',
 'intro_sting':'Firma de la intro CUKITI','purchase':'Compra','teikoin_gain':'Cobro de Teikoins',
 'game_over':'Fin de partida','level_victory':'Victoria de nivel','life_lost':'Vida perdida',
 'no_lives':'Sin vidas','perfect_clear':'Tablero completamente limpio','streak':'Racha',
 'rank_up':'Subida de rango','character_unlock':'Desbloqueo de compañero',
 'board_shift':'Desplazamiento del tablero y usos compartidos','combo':'Combo','combo_grace':'Gracia de combo',
 'pure_line':'Línea de un solo color','target_collect':'Recoger estrella objetivo',
 'teika_appear':'Aparición de Teika','teika_seed_break':'Apertura del bloque-semilla de Teika',
 'merge_2':'Fusión de Teikas (una fuente, cuatro tonos)','mega':'Explosión del Mega',
 'mega_collapse':'Carga del colapso Mega','tray_reward':'Ficha de recompensa en bandeja',
 'astro_emerge':'Aparición del astro','barrel_blast':'Explosión de barril',
 'coil_charge':'Carga de bobina','coil_discharge':'Descarga de bobina','coil_short':'Cortocircuito de bobina',
 'crystal_crack':'Grieta de cristal de mecánica','crystal_shatter':'Rotura de cristal de mecánica',
 'egg_hatch':'Eclosión de huevo','fear_scare':'Susto del Pasaje',
 'galactic_cancel':'Disipación del agujero negro','galactic_collapse':'Colapso del agujero negro',
 'galactic_warn':'Aviso del agujero negro','ghost_fog':'Niebla espectral','ghost_hunt':'Caza espectral',
 'glacier_freeze':'Congelación del Glaciar','glacier_thaw':'Deshielo del Glaciar',
 'glutton_bite':'Mordisco de Cocina Voraz','glutton_open':'Apertura/aviso de Cocina Voraz',
 'glutton_retreat':'Retirada de Cocina Voraz','hot_cell':'Celda caliente',
 'king_piece_evict':'Expulsión de pieza por el Rey','king_piece_land':'Aterrizaje de pieza del Rey',
 'king_wall_break':'Derrumbe del muro real','king_wall_rise':'Subida del muro real','king_warning':'Aviso del Rey',
 'kintsugi_seal':'Sellado kintsugi','lava_retreat':'Retirada de lava','lava_surge':'Oleada de lava',
 'lead_crush':'Aplastamiento por plomo','magnet_place':'Colocación del imán',
 'mirror_stamp':'Estampado espejo','mold_stamp':'Estampado de molde','moon_steal':'Robo de la Luna',
 'mural_complete':'Mural completado','ninja_repair':'Reparación del Dojo','ninja_slash':'Corte del Dojo',
 'plant_rescue':'Rescate de planta','poison_splash':'Salpicadura de veneno','poison_wash':'Lavado de veneno',
 'rock_crack':'Rotura de roca','sleep_drowsy':'Somnolencia del Bosque','sleep_wake':'Despertar del Bosque',
 'star_retint':'Cambio de color estelar','stubborn_break':'Rotura del bloque terco',
 'stubborn_resist':'Resistencia del bloque terco','sun_strike':'Golpe del Sol','toxic_spread':'Propagación tóxica',
 'vine_grab':'Agarre de enredadera','vine_hurt':'Daño de enredadera','vine_sprout':'Brote de enredadera'}
ABILITIES = {'birth':'Parto Real','bloom':'Cañón Floral','dream':'Siesta','eclipse':'Eclipse',
 'energize':'Energía Cruzada','feast':'Festín','fire':'Fichas en Llamas','ghost':'Posesión',
 'icicle':'Estalactitas','miasma':'Miasma','ready':'Eco de habilidad · carga conservada',
 'shield':'Escudo','slash':'Tajo','slide':'Puzzle Deslizante','wish':'Deseo'}
UI = {'ui_click','play_launch','invalid','iris_transition','iris_open','intro_sting'}
BOARD = {'board_shift','combo','combo_grace','pure_line','target_collect','teika_appear','teika_seed_break',
         'merge_2','mega','mega_collapse','tray_reward'}
PROGRESS = {'game_over','level_victory','life_lost','no_lives','perfect_clear','streak','rank_up','character_unlock'}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    registry_data=json.loads((OUT/'licencias/registro.json').read_text(encoding='utf-8'))
    registry=registry_data['entradas']
    inventory={p.name:p for p in RAW.iterdir() if p.suffix in ['.ogg','.mp3','.wav']}
    assert set(inventory)=={e['archivo'] for e in registry}
    code=(ROOT/'app/src/main/java/com/cukiti/teikafusion/ui/SoundManager.kt').read_text(encoding='utf-8')
    loaded=set(re.findall(r'R\.raw\.(snd_\w+)',code))
    music=json.loads((OUT/'aceptacion-musical-2026-10-11.json').read_text(encoding='utf-8'))['tracks']
    accepted_music={t['installed_resource']:t['installed_sha256'] for t in music if t['integrated']}
    accepted=json.loads((OUT/'colocar-basico-integracion-2026-10-11.json').read_text(encoding='utf-8'))
    accepted_effects={accepted['resource']:accepted}
    extra=OUT/'efectos-core-r4-integracion-2026-10-11.json'
    if extra.exists():
        for track in json.loads(extra.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    materials=OUT/'efectos-cristal-madera-integracion-2026-10-11.json'
    if materials.exists():
        for track in json.loads(materials.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    crystal=OUT/'efectos-colocar-cristal-r5-integracion-2026-10-11.json'
    if crystal.exists():
        for track in json.loads(crystal.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    pending_ids={'snd_ui_click':'S01','snd_clear_classic':'S03','snd_merge_2':'S04'}
    lava_ice=OUT/'efectos-lava-hielo-integracion-2026-10-11.json'
    if lava_ice.exists():
        for track in json.loads(lava_ice.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
        pending_ids['snd_clear_lava']='LH02'
    clear_lava=OUT/'efectos-limpiar-lava-r2-integracion-2026-10-11.json'
    if clear_lava.exists():
        for track in json.loads(clear_lava.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    spectre=OUT/'efectos-espectro-constelacion-integracion-2026-10-12.json'
    if spectre.exists():
        for track in json.loads(spectre.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    void=OUT/'efectos-vacio-integracion-2026-10-12.json'
    if void.exists():
        for track in json.loads(void.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
        pending_ids.update(snd_place_metal='VM03',snd_clear_metal='VM04')
    metal=OUT/'efectos-metal-integracion-2026-10-12.json'
    if metal.exists():
        for track in json.loads(metal.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    if materials.exists():pending_ids['snd_place_crystal']='CM01'
    gold_ceramic=OUT/'efectos-dorado-limpiar-ceramica-integracion-2026-10-12.json'
    if gold_ceramic.exists():
        for track in json.loads(gold_ceramic.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
        pending_ids.update(snd_clear_neon='NDC01',snd_place_ceramic='NDC04')
    neon_ceramic=OUT/'efectos-neon-colocar-ceramica-integracion-2026-10-12.json'
    if neon_ceramic.exists():
        for track in json.loads(neon_ceramic.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    records=[]
    invalid=OUT/'efectos-invalid-integracion-2026-10-12.json'
    if invalid.exists():
        for track in json.loads(invalid.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
        pending_ids.update(snd_play_launch='NAV01',snd_iris_transition='NAV03')
    launch_flora=OUT/'efectos-campana-flora-integracion-2026-10-12.json'
    if launch_flora.exists():
        for track in json.loads(launch_flora.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
        pending_ids['snd_flora_launch']='NAV06'
    iris=OUT/'efectos-iris-integracion-2026-10-12.json'
    if iris.exists():
        for track in json.loads(iris.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
        pending_ids.update(snd_iris_transition='NAV04',snd_iris_open='NAV05')
    combo_board=OUT/'efectos-combo-tablero-integracion-2026-10-13.json'
    if combo_board.exists():
        for track in json.loads(combo_board.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    star_teika_review=OUT/'efectos-estrellas-teikas-revision-2026-10-13.json'
    if star_teika_review.exists():
        for track in json.loads(star_teika_review.read_text(encoding='utf-8'))['tracks']:
            if track.get('listening_status','').startswith('rejected'):
                pending_ids[Path(track['resource']).stem]=track['id']
    mega_review=OUT/'efectos-mega-bandeja-v2-2026-10-13.json'
    if mega_review.exists():
        for track in json.loads(mega_review.read_text(encoding='utf-8'))['tracks']:
            if track.get('listening_status','').startswith('rejected'):
                pending_ids[Path(track['resource']).stem]=track['id']
    initial_abilities=OUT/'efectos-habilidades-iniciales-revision-2026-10-13.json'
    if initial_abilities.exists():
        for track in json.loads(initial_abilities.read_text(encoding='utf-8'))['tracks']:
            if track.get('listening_status','').startswith('rejected'):
                pending_ids[Path(track['resource']).stem]=track['id']
    next_abilities=OUT/'efectos-rayco-inaki-kenji-nieves-integracion-2026-10-13.json'
    if next_abilities.exists():
        for track in json.loads(next_abilities.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    cristobal_maite=OUT/'efectos-cristobal-maite-integracion-2026-10-13.json'
    if cristobal_maite.exists():
        for track in json.loads(cristobal_maite.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    timoteo_flora=OUT/'efectos-timoteo-flora-integracion-2026-10-13.json'
    if timoteo_flora.exists():
        for track in json.loads(timoteo_flora.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    move_echo=OUT/'efectos-traslado-eco-integracion-2026-10-13.json'
    if move_echo.exists():
        for track in json.loads(move_echo.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    mega_charge=OUT/'efectos-mega-carga-integracion-2026-10-13.json'
    if mega_charge.exists():
        for track in json.loads(mega_charge.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    mega_expansion=OUT/'efectos-mega-expansion-integracion-2026-10-13.json'
    if mega_expansion.exists():
        for track in json.loads(mega_expansion.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    tray_reward=OUT/'efectos-bandeja-integracion-2026-10-13.json'
    if tray_reward.exists():
        for track in json.loads(tray_reward.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    star_teika=OUT/'efectos-estrellas-teikas-integracion-2026-10-13.json'
    if star_teika.exists():
        for track in json.loads(star_teika.read_text(encoding='utf-8'))['tracks']:
            if track['integrated'] and track['user_approved']:accepted_effects[track['resource']]=track
    for entry in registry:
        name=entry['archivo'];base=Path(name).stem;path=inventory[name];assert sha(path)==entry['sha256']
        if name in accepted_music:
            assert accepted_music[name]==entry['sha256']
            label='Menús y tema general' if base=='bgm_gameplay' else BIOMES[base[4:]]
            group,status='Músicas','Integrada v2, composición y bucle aprobados'
        elif base=='snd_keepalive':
            group,label,status='Técnico','Silencio de mantenimiento del canal','No renovar'
        else:
            assert base in loaded, name
            suffix=base[4:]
            if suffix.startswith(('place_','clear_')):
                action,material=suffix.split('_',1);group=GROUPS[1]
                label=('Colocar · ' if action=='place' else 'Limpiar · ')+MATERIALS[material]
            elif suffix.startswith('ability_'):
                group=GROUPS[3];label=ABILITIES[suffix[8:]]
            elif suffix.startswith('biome_'):
                group=GROUPS[6];ident=suffix[6:];selection=ident.endswith('_selection')
                if selection:ident=ident[:-10]
                label=('Selección alternativa · ' if selection else 'Firma/selección · ')+BIOMES[ident]
            else:
                label=LABELS[suffix]
                group=GROUPS[0] if suffix in UI else GROUPS[2] if suffix in BOARD else GROUPS[4] if suffix in {'purchase','teikoin_gain'} else GROUPS[5] if suffix in PROGRESS else GROUPS[7]
            status='Corrección y aceptación final pendientes' if base in pending_ids else 'Pendiente de definición/revisión v2'
            if name in accepted_effects:
                assert accepted_effects[name]['installed_sha256']==entry['sha256'] and accepted_effects[name]['user_approved']
                status='Integrado v2, carácter y final aprobados'
        records.append({'archivo':name,'nombre':label,'categoria':group,'estado_v2':status,
                        'sha256':entry['sha256'],'licencia_estado':entry.get('estado'),
                        'id_piloto':pending_ids.get(base,'S02' if name==accepted['resource'] else None)})
    effects=[r for r in records if r['categoria'] not in ['Músicas','Técnico']]
    integrated=sum(r['estado_v2'].startswith('Integrado') for r in effects)
    correcting=sum(r['estado_v2']=='Corrección y aceptación final pendientes' for r in effects)
    counts={'archivos_totales':len(records),'musicas_integradas_v2':len(accepted_music),
            'efectos_perceptibles':len(effects),'efectos_integrados_v2':integrated,
            'efectos_pendientes_v2':len(effects)-integrated,'efectos_en_correccion':correcting,
            'otros_efectos_pendientes_revision':len(effects)-integrated-correcting,'silencios_tecnicos':1}
    expected_audio=146+int('snd_flora_launch.wav' in accepted_effects)+int('snd_iris_open.wav' in accepted_effects)
    assert counts['archivos_totales']==expected_audio and counts['efectos_perceptibles']==expected_audio-16
    summary=[{'categoria':g,'total':sum(r['categoria']==g for r in effects),
              'integrados':sum(r['categoria']==g and r['estado_v2'].startswith('Integrado') for r in effects)} for g in GROUPS]
    data={'date':'2026-10-11','updated':registry_data.get('actualizado'),'scope':'Installed unique files and renewal v2 status, not missing in-game sound or a count of API attempts',
          'counts':counts,'categories':summary,'audio':records}
    (OUT/'lista-completa-audio-v2-2026-10-11.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    lines=['# Lista completa y pendientes de audio v2 — 2026-10-11','',
        f"Actualizado: {registry_data.get('actualizado', 'no registrado')}.", '',
        'Catálogo completo contrastado con `res/raw`, registro de licencias, cargas de `SoundManager` y hashes de entregas aceptadas.',
        f"**Quedan {counts['efectos_pendientes_v2']} efectos por cerrar en la renovación v2**: {correcting} en corrección y {counts['otros_efectos_pendientes_revision']} por definir/revisar en esta fase. Todos tienen ya un recurso instalado. Una renovación no implica que cada archivo deba regenerarse sin revisar primero sus usos y requisitos.",
        f"{len(accepted_music)} músicas y {integrated} efectos integrados, {len(effects)} efectos perceptibles totales y 1 silencio técnico: **{len(records)} archivos únicos**. No sumar tomas rechazadas, previews, cuatro tonos de fusión o capas de clear como nuevos sonidos.",
        '«Integrado v2» describe aceptación/entrega, no una certificación comercial global. La evidencia particular de promoción sigue pendiente. Los SFX anteriores conservan sus expedientes; no se heredan derechos de la cuenta nueva. Presencia/carga en código no demuestra por sí sola todas las condiciones jugables de cada evento.','',
        '## Resumen de efectos','', '| Categoría | Total | Integrados v2 | Pendientes v2 |','|---|---:|---:|---:|']
    for c in summary:lines.append(f"| {c['categoria']} | {c['total']} | {c['integrados']} | {c['total']-c['integrados']} |")
    next_step='cerrar los efectos en corrección; después variantes' if correcting else 'variantes'
    lines += ['', f'Orden recomendado: {next_step} de colocar/limpiar por material; tablero/Teikas, interfaz, habilidades, mecánicas, resultados/economía y firmas. Antes de producir cada bloque: ficha y consumidores actuales; después escucha, licencia, respaldo y pruebas. No generar el resto automáticamente por crear esta lista.',
              'Neón comparte la colocación de Cristal; tiene limpieza propia. Hay 11 archivos de colocar y 12 de limpiar, no 24. Selección por estilo equipado, no por bioma de la música.','']
    number=0
    for group in GROUPS+['Músicas','Técnico']:
        lines += ['## '+group,'','| Nº | Acción / pista | Archivo instalado | Estado v2 |','|---:|---|---|---|']
        for record in sorted((r for r in records if r['categoria']==group),key=lambda r:r['archivo']):
            number+=1
            lines.append(f"| {number} | {record['nombre']} | [{record['archivo']}]({inventory[record['archivo']].resolve().as_posix()}) | {record['estado_v2']} |")
        lines.append('')
    assert number==expected_audio
    (OUT/'lista-completa-audio-v2-2026-10-11.md').write_text('\n'.join(lines),encoding='utf-8')
    print(json.dumps({'counts':counts,'categories':summary},ensure_ascii=True))


if __name__=='__main__':
    main()
