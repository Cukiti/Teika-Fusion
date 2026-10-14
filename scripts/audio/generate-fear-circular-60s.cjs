const fs=require('fs'),crypto=require('crypto');
const base='%USERPROFILE%/.codex/artifacts/elevenlabs/fear-circular-60s-2026-09-15';fs.mkdirSync(base,{recursive:true});
const receipt=base+'/generation.json',output=base+'/bgm_fear-source.mp3';
const positive=['instrumental nocturnal fantasy puzzle music','warm electric piano melody','delicate music box accents','rounded soft bass','very light clean electronic percussion','cozy gentle mystery','clear memorable melody','restrained accompaniment','96 BPM steady 4/4','continuous repeating harmonic pattern'];
const negative=['vocals','choir','speech','horror','loud synth leads','EDM drops','dramatic drums','hiss','vinyl noise','distortion','intro','outro','fade out','final chord','silence'];
const chunks=[{text:[
 '[Instrumental]',
 '{An endless nocturnal forest puzzle cue, warm electric piano melody, tiny music box answers, restrained soft percussion and rounded bass. Gentle mystery, welcoming rather than scary.}',
 '{Exactly 24 bars in 4/4 at 96 BPM: three eight-bar phrases. Keep a repeating four-bar D minor, B-flat major, F major, A minor harmony cycle.}',
 '{Start on the first downbeat with the full established groove. Subtle motif variations in the middle phrase, then return to the opening instrumentation and energy.}',
 '{The last bar is an ordinary flowing bar, leading directly into bar one. Sustain the rhythm and volume through its last beat. No final chord, slowdown, pause, fade or ending flourish.}',
 '{Short controlled room ambience, no exposed long reverb tails. This is one cycle of an ongoing piece, not a complete song with an ending.}'
].join('\n'),duration_ms:60000,positive_styles:positive,negative_styles:negative,context_adherence:'high'}];
const body={model_id:'music_v2_5',composition_plan:{chunks},store_for_inpainting:true};
async function main(){if(fs.existsSync(receipt)||fs.existsSync(output))throw Error('Prior attempt exists; inspect instead of retry');const key=process.env.ELEVENLABS_API_KEY;if(!key)throw Error('Key unavailable');
const rec={id:'bgm_fear',model:'music_v2_5',subscriptionTierAtGeneration:'starter',subscriptionStatusAtGeneration:'active',creditsBefore:11885,requestedSeconds:60,estimatedCredits:750,body,startedAt:new Date().toISOString(),status:'started; never retry automatically'};fs.writeFileSync(receipt,JSON.stringify(rec,null,2));
try{const r=await fetch('https://api.elevenlabs.io/v1/music?output_format=mp3_44100_128',{method:'POST',headers:{'xi-api-key':key,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(240000)});rec.httpStatus=r.status;rec.requestId=r.headers.get('request-id')??r.headers.get('x-request-id');rec.songId=r.headers.get('song-id');if(!r.ok){rec.status='rejected';rec.error=(await r.text()).slice(0,2000);throw Error('Generation HTTP '+r.status+': '+rec.error);}const data=Buffer.from(await r.arrayBuffer());fs.writeFileSync(output,data);Object.assign(rec,{status:'generated; awaiting listening',output,bytes:data.length,sha256:crypto.createHash('sha256').update(data).digest('hex'),completedAt:new Date().toISOString()});console.log('Music generated: '+output);}catch(e){if(rec.status!=='rejected')rec.status='uncertain; do not retry';throw e;}finally{fs.writeFileSync(receipt,JSON.stringify(rec,null,2)+'\n');}}
main().catch(e=>{console.error(e.message);process.exitCode=1});
