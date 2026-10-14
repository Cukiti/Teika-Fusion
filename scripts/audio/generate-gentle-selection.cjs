// Review candidates only. Encoded source carries its own sample rate; never guess raw PCM layout.
const fs=require('fs'),cp=require('child_process'),crypto=require('crypto');
const base='%USERPROFILE%/.codex/artifacts/elevenlabs/gentle-selection-2026-09-14';
const rows=[
 ['I02','play_launch',1.2,'One soft small silver bell struck once: TIN. Warm rounded attack, clear sweet pitch, delicate natural decay. Satisfying calm start-game confirmation. No melody, second strike or spoken word.'],
 ['B01','biome_crystal',.9,'A tiny crystal gently breaking into three delicate glass fragments. Soft sparkling fracture, intimate and reassuring, not a window crash.'],
 ['B02','biome_sleep',1.1,'One peaceful sleeping exhale only. Soft rounded breath out from a cute sleeping creature. No inhale, snore, speech or repeated breathing.'],
 ['B03','biome_fear',1.2,'One gentle mysterious enchanted breeze, a soft velvety airy swirl with a subtle hollow magical tone. Calm curiosity, not frightening.'],
 ['B04','biome_garden',1.1,'One small magical root growing and curling, supple woody creak and soft fibrous stretch ending in a rounded tiny root snap.'],
 ['B05','biome_storm',.8,'A very gentle short-circuit cue: two tiny electrical ticks descending into a muted rounded error tone. Soft coil malfunction, no loud buzz or thunder.'],
 ['B06','biome_volcano',1.2,'Two slow thick molten magma bubbles softly opening and settling. Warm viscous lava movement, small scale, no eruption or explosion.'],
 ['B07','biome_ninja',.7,'One elegant anime katana swish and delicate metallic ting. Fast controlled blade movement with a soft smooth tail, no battle impact or shouting.'],
 ['B08','biome_glacier',1,'A small object gently crystallizing into ice, delicate ascending icy crackles ending with a soft glassy freeze lock. No blizzard.'],
 ['B09','biome_ghost',1.2,'A cute mischievous cartoon ghost makes one soft hollow rounded woo sound and floats away. Playful haunted mansion, original character, no scream or speech.'],
 ['B10','biome_glutton',.7,'Three small rounded bubbles in a gently simmering cooking pot. Cozy kitchen, separated plops, no continuous boiling hiss or utensils.'],
 ['B11','biome_toxic',1.2,'A little thick magical potion pours into a glass vial and finishes with two soft viscous poison bubbles. Intimate, strange and gentle, no splash blast.'],
 ['B12','biome_star',.8,'A luminous magical star sparkles with a tiny ascending cluster of sweet bell harmonics. Original short arcade reward, radiant and gentle, no song or shrill tones.'],
 ['B13','biome_king',1.1,'A soft warm muted royal trumpet plays a tiny noble two-note welcome fanfare. Intimate, restrained breath and smooth release, no loud brass blast or drums.'],
 ['B14','biome_galactic',1.3,'A miniature black hole contracts with a smooth low-mid gravity bend and a soft deep rounded closing pulse. Mysterious space, audible on phone speakers, no explosion.']
];
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const ff=a=>cp.execFileSync('ffmpeg',['-v','error','-y',...a],{maxBuffer:32e6});
async function main(){fs.mkdirSync(base,{recursive:true});const results=[];
 for(const [id,slug,duration,description] of rows){const prefix=`${base}/${id}-${slug}`,receiptPath=prefix+'.json';let receipt;
  if(fs.existsSync(receiptPath)){receipt=JSON.parse(fs.readFileSync(receiptPath));if(receipt.status!=='downloaded')throw Error(`${id}: inspect existing receipt before retry`);}
  else{const key=process.env.ELEVENLABS_API_KEY;if(!key)throw Error('API key unavailable');const body={model_id:'eleven_text_to_sound_v2',text:description+' Quiet premium mental puzzle UI. Isolated close sound, smooth complete decay. No background noise, music, harsh treble or long reverb.',duration_seconds:duration,prompt_influence:.8,loop:false};if(body.text.length>450)throw Error(id+' prompt too long');receipt={body,startedAt:new Date().toISOString(),status:'pending; never automatically retry'};fs.writeFileSync(receiptPath,JSON.stringify(receipt,null,2));const response=await fetch('https://api.elevenlabs.io/v1/sound-generation?output_format=mp3_44100_128',{method:'POST',headers:{'xi-api-key':key,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(90000)});if(!response.ok){receipt.status='rejected';receipt.http=response.status;fs.writeFileSync(receiptPath,JSON.stringify(receipt,null,2));throw Error(id+' HTTP '+response.status);}fs.writeFileSync(prefix+'.mp3',Buffer.from(await response.arrayBuffer()));Object.assign(receipt,{status:'downloaded',completedAt:new Date().toISOString(),cost:response.headers.get('character-cost'),sourceSha256:hash(prefix+'.mp3')});fs.writeFileSync(receiptPath,JSON.stringify(receipt,null,2));}
  const bytes=ff(['-i',prefix+'.mp3','-ac','1','-ar','44100','-af','highpass=f=65,lowpass=f=10000','-f','f32le','pipe:1']);const samples=new Float32Array(bytes.buffer,bytes.byteOffset,bytes.length/4);let peak=0;for(const v of samples){if(!Number.isFinite(v))throw Error(id+' invalid');peak=Math.max(peak,Math.abs(v));}if(peak<1e-5)throw Error(id+' silent');const gain=10**((id==='I02'?-15:-17)/20)/peak;for(let i=0;i<samples.length;i++)samples[i]*=gain*Math.min(1,i/88,(samples.length-1-i)/2205);
  const preview=prefix+'.wav';cp.execFileSync('ffmpeg',['-v','error','-y','-f','f32le','-ar','44100','-ac','1','-i','pipe:0','-c:a','pcm_s16le',preview],{input:Buffer.from(samples.buffer,samples.byteOffset,samples.byteLength)});
  results.push({id,resource:'snd_'+slug,preview,source:prefix+'.mp3',receipt:receiptPath,sha256:hash(preview),duration:samples.length/44100,peakTargetDb:id==='I02'?-15:-17,status:'pending listening; not installed'});console.log(id+' ready');
 }
 fs.writeFileSync('docs/audio/seleccion-suave-candidatos.json',JSON.stringify({results,installed:false},null,2));
 fs.writeFileSync(base+'/escuchar.md',results.map(r=>`**${r.id} — ${r.resource}**\n\n![${r.id}](${r.preview})\n`).join('\n'));
}main().catch(e=>{console.error(e.message);process.exitCode=1;});
