const fs=require('fs'),path=require('path'),crypto=require('crypto');
const root=process.cwd().replaceAll('\\','/'),report=JSON.parse(fs.readFileSync('docs/audio/renovacion-resultados.json','utf8'));
const groups=[];const add=(prefix,title,text)=>groups.push({prefix,title,items:text.trim().split('\n').map(line=>{const [id,name,use]=line.split('|');return {id,name,use};})});
add('M','Música de fondo',`bgm_gameplay|Música general|Menús y partida sin tema propio; también los 12 biomas que reutilizan la pista general.
bgm_crystal|Caverna de Cristal|Música durante los niveles del bioma Cristal.
bgm_sleep|Bosque del Sueño|Música durante los niveles del bioma Sueño.`);
add('I','Interfaz y navegación',`snd_ui_click|Clic compartido|Botones, cambio de pestaña, conteo animado de puntuación y pasos del tutorial; cambia el tono o volumen según el uso.
snd_play_launch|Comenzar partida|Confirmación sonora al lanzar la partida.
snd_invalid|Movimiento rechazado|Aviso cuando una ficha no cabe o se rechaza su colocación.`);
add('J','Tablero, colocación y fusiones',`snd_place_classic|Colocar: Clásico|Colocación de una ficha con apariencia Clásico.
snd_place_candy|Colocar: Caramelo|Colocación con apariencia Caramelo.
snd_place_ceramic|Colocar: Cerámica|Colocación con apariencia Cerámica (ID interno de skin fall3d).
snd_clear_classic|Limpiar: Clásico|Limpiar líneas con apariencia Clásico; varias líneas superponen versiones de distinto tono.
snd_clear_candy|Limpiar: Caramelo|La misma limpieza de líneas con material Caramelo.
snd_clear_ceramic|Limpiar: Cerámica|La misma limpieza de líneas con material Cerámica.
snd_teika_appear|Aparición de Teika|Nacimiento de un Teika en el tablero al completar una línea.
snd_merge_2|Fusión de Teikas|Todas las fusiones usan esta misma muestra, independientemente del nivel.
snd_mega|Mega Teika|Expansión o evento Mega; la música baja temporalmente para destacarlo.
snd_combo|Combo|Confirmación de combo; el tono aumenta con la racha.
snd_combo_grace|Combo salvado|La gracia conserva la racha de combo; aviso breve de que se salvó.
snd_target_collect|Estrella objetivo recogida|Recogida de una estrella objetivo; no es el rating de estrellas del nivel.
snd_hot_cell|Celda caliente|Acento de la mecánica de celda caliente, superpuesto a la jugada.
snd_pure_line|Línea de un solo color|Acento al completar una línea pura, que produce un Teika de nivel superior.`);
add('E','Economía e inventario',`snd_teikoin_gain|Ganar Teikoins|Confirmación de ganancia de monedas.
snd_purchase|Compra|Confirmación de una compra.
snd_crystal_defuse_tap|Desfusionar: pulsación|Cada toque del proceso de desfusión de cristales del inventario.
snd_crystal_defuse_done|Desfusionar: terminado|Confirmación de la desfusión completada.
snd_crystal_fuse|Fusionar cristales|Confirmación de fusión de cristales del inventario; distinto de la fusión de Teikas del tablero.`);
add('R','Resultados, vidas y progreso',`snd_life_lost|Vida perdida|Aviso de pérdida de una vida.
snd_no_lives|Sin vidas|Aviso cuando no quedan vidas disponibles.
snd_game_over|Derrota|Final de partida perdido; la música baja temporalmente.
snd_level_victory|Victoria de nivel|Celebración al completar un nivel; la música baja temporalmente.
snd_perfect_clear|Tablero limpio|Celebración de limpieza completa del tablero.
snd_character_unlock|Compañero desbloqueado|Celebración de un nuevo compañero desbloqueado.
snd_rank_up|Subida de rango|Celebración del ascenso de rango del perfil.
snd_mural_complete|Mural completado|Celebración de final de mural/capítulo.
snd_streak|Racha diaria|Acento de la racha de progreso diario; distinto del combo dentro de la partida.`);
add('H','Habilidades de compañeros',`snd_ability_miasma|Toño: Miasma Venenoso|Activación del efecto de miasma.
snd_ability_birth|Reynaldo: Parto Real|Activación del nacimiento del Teika hijo.
snd_ability_dream|Maite: Siesta|Activación de la habilidad de sueño y caída de fila.
snd_ability_slide|Timoteo: Tembleque|Arranque del temblor del tablero; el nombre del archivo conserva slide.
snd_ability_energize|Rayco: Energía Cruzada|Energización de Teikas por la habilidad.
snd_ability_fire|Iñaki: Fichas en Llamas|Efecto de fuego de la habilidad al colocar la ficha encendida.
snd_ability_bloom|Flora: Cañón Floral|Floración/carga del Teika por la habilidad.
snd_ability_ghost|Morti: Manos Fantasma|Activación de las manos fantasma.
snd_ability_icicle|Nieves: Estalactitas|Formación, caída e impacto de hielo; asociado al VFX de aproximadamente 1,5 s.
snd_ability_shield|Habilidad de escudo|Activación del cue de escudo (playAbilityShield).
snd_ability_slash|Habilidad de corte|Activación del cue de corte (playAbilitySlash).
snd_ability_feast|Habilidad de festín|Activación del cue de festín (playAbilityFeast).
snd_ability_wish|Habilidad de deseo|Activación del cue de deseo (playAbilityWish).
snd_ability_eclipse|Habilidad de eclipse|Activación del cue de eclipse (playAbilityEclipse).`);
const biomes=[['crystal','Cristal'],['sleep','Sueño'],['fear','Miedo'],['garden','Jardín'],['storm','Tormenta'],['volcano','Volcán'],['ninja','Ninja'],['glacier','Glaciar'],['ghost','Fantasma'],['glutton','Glotón'],['toxic','Tóxico'],['star','Estrella'],['king','Rey'],['galactic','Galáctico']];
add('B','Firmas breves de bioma',biomes.map(([id,name])=>'snd_biome_'+id+'|Firma de '+name+'|Al tocar un nivel de ese bioma en el mapa.'+(id==='glutton'||id==='star'?' También se reutiliza en eventos del bioma dentro de la partida.':'')+' Es un efecto breve, no la música de fondo.').join('\n'));
add('A','Mecánicas y avisos de biomas',`snd_crystal_crack|Cristal: agrietar|Primera rotura de la protección de cristal.
snd_crystal_shatter|Cristal: romper|Destrucción del cristal.
snd_sleep_drowsy|Sueño: somnolencia|Aviso/efecto de la somnolencia del bioma.
snd_fear_scare|Miedo: susto|Efecto de miedo o huida de bloques.
snd_vine_grab|Jardín: atrapar|La raíz o enredadera captura al Teika.
snd_vine_hurt|Jardín: dañar raíz|Daño o avance de liberación de la raíz.
snd_coil_charge|Tormenta: cargar bobina|Carga de bobinas; también avisa de habilidad disponible con tono más agudo (playAbilityReady).
snd_coil_discharge|Tormenta: descargar|Descarga correcta del circuito de bobinas.
snd_coil_short|Tormenta: cortocircuito|Fallo/cortocircuito de la secuencia de bobinas.
snd_lava_surge|Volcán: avance de lava|Avance o irrupción de lava en el tablero.
snd_ninja_slash|Ninja: corte del tablero|La katana divide el tablero; distinto del corte de habilidad H11.
snd_ninja_repair|Ninja: reparación|Reparación de la división del tablero.
snd_glacier_freeze|Glaciar: congelar|Congelación de un Teika por la mecánica ambiental.
snd_ghost_fog|Fantasma: niebla|Aparición del efecto de niebla/oscuridad.
snd_ghost_hunt|Fantasma: captura|Caza o captura de la mecánica espectral.
snd_glutton_open|Glotón: abrir boca|La boca aparece o se abre antes de morder.
snd_glutton_bite|Glotón: mordida|La boca ejecuta la mordida sobre el tablero.
snd_toxic_spread|Tóxico: propagación|Expansión/recarga del veneno en el tablero.
snd_astro_emerge|Estrella: aparece astro|Aparición del Sol o la Luna; cambia el tono según el astro.
snd_sun_strike|Estrella: ataque solar|El Sol ejecuta su ataque sobre un Teika.
snd_moon_steal|Estrella: robo lunar|La Luna roba los bloques con estrella seleccionados.
snd_king_warning|Rey: aviso de muro|Aviso de asedio; también se usa más grave cuando el cierre se pospone (playKingStall).
snd_king_wall_rise|Rey: levantar muro|Formación o reposición de la muralla.
snd_king_wall_break|Rey: romper muro|Derribo de bloques de muralla.
snd_galactic_warn|Galáctico: advertencia|Anuncio del agujero negro antes del colapso.
snd_galactic_collapse|Galáctico: colapso|Ejecución del colapso del agujero negro.
snd_galactic_cancel|Galáctico: cancelar|La jugada cancela el agujero negro a tiempo.`);
const known=new Map(report.records.map(r=>[r.id,r])),seen=new Set();let changed=0;
for(const g of groups)g.items.forEach((x,i)=>{if(seen.has(x.id)||!known.has(x.id))throw Error('Duplicate or unknown '+x.id);seen.add(x.id);const r=known.get(x.id);x.code=g.prefix+String(i+1).padStart(2,'0');x.file=root+'/'+r.target;x.duration=r.verified.declaredSeconds;const h=crypto.createHash('sha256').update(fs.readFileSync(x.file)).digest('hex');if(h!==r.finalSha256)changed++;});
if(seen.size!==89||report.records.some(r=>!seen.has(r.id)))throw Error('Incomplete census');const current=fs.readdirSync('app/src/main/res/raw').filter(p=>/\.(ogg|wav|mp3|m4a|flac|aac|opus)$/i.test(p));if(current.length!==seen.size)throw Error('Audio inventory changed');
let md='# Censo de revisión de audio — Teika Fusion\n\n89 archivos actuales: 3 músicas y 86 efectos. Identificadores de revisión exclusivos de este documento; no cambian los IDs del juego. Verificación: '+(changed===0?'los 89 archivos coinciden con la renovación integrada.':changed+' archivos difieren de la renovación; revisar metadatos.')+'\n\nCada enlace abre el recurso actual. La descripción explica su función, no una valoración de cómo suena. La repetición, tono y volumen pueden cambiar dentro del juego. No se generan ni sustituyen audios al crear este censo.\n\n## Cómo pedir cambios\n\nEjemplo: **J07: más suave; J08: cambiar por completo; M02: conservar; A07: mejorar solo el aviso de habilidad disponible.**\n\nEstados sugeridos: conservar / mejorar / reemplazar. Puedes precisar volumen, brillo, cuerpo, duración, carácter, repetición o sincronización. Si el archivo se reutiliza, indica si quieres cambiar todos sus usos o solo uno. Separar un uso podría requerir un recurso y una llamada adicionales.\n\n## Resumen\n\n| IDs | Categoría | Archivos |\n|---|---|---:|\n';
for(const g of groups)md+='| '+g.items[0].code+'–'+g.items.at(-1).code+' | '+g.title+' | '+g.items.length+' |\n';
md+='\n**Música pendiente de tema propio:** Miedo, Jardín, Tormenta, Volcán, Ninja, Glaciar, Fantasma, Glotón, Tóxico, Estrella, Rey y Galáctico comparten M01. No hay archivos de voz/narración identificados.\n\n**Archivos compartidos importantes:** I01 (botones, pestañas, contador y tutorial), J08 (todas las fusiones), A07 (bobina y habilidad disponible), A22 (aviso de muro y amago). J04–J06 generan varias capas al limpiar varias líneas. Los B01–B14 son firmas breves, no canciones.\n';
for(const g of groups){md+='\n## '+g.title+'\n\n| ID | Audio / escuchar | Duración | Uso en el juego |\n|---|---|---:|---|\n';for(const x of g.items)md+='| **'+x.code+'** | ['+x.name+']('+x.file+')<br>'+path.basename(x.file)+' | '+x.duration.toFixed(3)+' s | '+x.use+' |\n';}
md+='\n## Notas de revisión\n\n| ID | Decisión | Cambio solicitado |\n|---|---|---|\n| — | — | — |\n\nLas asociaciones se basan en SoundManager y sus llamadas. El archivo [censo técnico original](censo-2026-09-13.json) conserva referencias históricas a llamadores; sus líneas pueden haberse desplazado. Las duraciones de este documento corresponden a los recursos renovados.\n';
fs.writeFileSync('docs/audio/censo-revision.md',md);fs.writeFileSync('docs/audio/censo-revision.json',JSON.stringify({total:seen.size,groups},null,2)+'\n');console.log(JSON.stringify({total:seen.size,changed,groups:groups.map(g=>({ids:g.items[0].code+'–'+g.items.at(-1).code,title:g.title,count:g.items.length}))}));
