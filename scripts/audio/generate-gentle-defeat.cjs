// Review candidates only. Encoded source carries its own sample rate; never guess raw PCM layout.
const fs=require('fs'),cp=require('child_process'),crypto=require('crypto');
const base='%USERPROFILE%/.codex/artifacts/elevenlabs/gentle-defeat-2026-09-15';
const rows=[
 ['R03','game_over',1.0,'Exactly two soft descending notes on a warm clean electric piano with a muted marimba-like attack. Gentle tum then lower tin, mildly wistful but comforting. A brief calm end-of-attempt cue. Complete natural release. No bass hit, explosion, buzz, hiss, voice or extra notes.']
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
 fs.writeFileSync('docs/audio/derrota-suave-candidato.json',JSON.stringify({results,installed:false},null,2));
 fs.writeFileSync(base+'/escuchar.md',results.map(r=>`**${r.id} — ${r.resource}**\n\n![${r.id}](${r.preview})\n`).join('\n'));
}main().catch(e=>{console.error(e.message);process.exitCode=1;});
