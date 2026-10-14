// Offline audio processing. Never accesses credentials or ElevenLabs.
// Stages assets only. Use install-renewal.cjs for transactional installation/restore.
const fs=require('fs'),path=require('path'),cp=require('child_process'),crypto=require('crypto');
const planPath='docs/audio/renovacion-plan.json',plan=JSON.parse(fs.readFileSync(planPath,'utf8')),base=plan.base;
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const run=args=>cp.execFileSync('ffmpeg',['-v','error','-y',...args],{maxBuffer:160e6});
const decode=(p,channels,rate=44100)=>{const b=run(['-i',p,'-f','f32le','-ar',String(rate),'-ac',String(channels),'pipe:1']);return new Float32Array(b.buffer,b.byteOffset,b.byteLength/4);};
const stats=s=>{let peak=0,sum=0;for(const x of s){if(!Number.isFinite(x))throw Error('Nonfinite PCM');peak=Math.max(peak,Math.abs(x));sum+=x*x;}return {peak,rms:Math.sqrt(sum/s.length)};};
const records=[];
for(const r of plan.records){
 let source=r.source,response=null;
 if(!source){const rp=base+'/responses/'+r.id+(r.id.startsWith('snd_')?'-accepted':'')+'.json';response=JSON.parse(fs.readFileSync(rp,'utf8'));if(response.result.isError)throw Error('Generation failed '+r.id);source=fs.readdirSync(r.outputDirectory).filter(f=>f.endsWith('.mp3')).map(f=>r.outputDirectory+'/'+f);if(source.length!==1)throw Error('Ambiguous source '+r.id);source=source[0];}
 const target=base+'/staged/'+path.basename(r.target),rate=r.sampleRate,ch=r.channels;
 if(!fs.existsSync(source))throw Error('Missing source '+source);
 const item={id:r.id,target:r.target,backup:r.backup,backupSha256:r.backupSha256,source,sourceSha256:hash(source),prompt:response?.args?.text??response?.args?.prompt??'See piloto-2026-09-13.json',generationTimestamp:response?.at??'See pilot manifest',sourceModel:r.id.startsWith('bgm_')?'music_v2':'Not exposed by MCP',stage:target};
 if(r.id.startsWith('snd_')){
  const original=decode(r.pilotPreview??source,ch,rate);let pcm=original;let st=stats(pcm),start=0;const n=Math.round(r.durationSeconds*rate);
  if(!r.pilotPreview){while(start<pcm.length/ch&&Math.max(...Array.from(pcm.subarray(start*ch,start*ch+ch),Math.abs))<st.peak*.008)start++;start=Math.max(0,start-Math.round(.001*rate));pcm=pcm.subarray(start*ch);}
  let speed=1;if(pcm.length/ch>n*1.08&&!r.pilotPreview){speed=pcm.length/ch/n;const b=cp.execFileSync('ffmpeg',['-v','error','-f','f32le','-ar',String(rate),'-ac',String(ch),'-i','pipe:0','-af','atempo='+speed,'-f','f32le','pipe:1'],{input:Buffer.from(pcm.buffer,pcm.byteOffset,pcm.byteLength),maxBuffer:16e6});pcm=new Float32Array(b.buffer,b.byteOffset,b.byteLength/4);}
  const out=new Float32Array(n*ch);st=stats(pcm);if(st.peak<.0001)throw Error('Silent SFX '+r.id);
  const peakDb=r.id.startsWith('snd_biome_')?-12:r.id.startsWith('snd_clear_')?-9:r.id.startsWith('snd_place_')?-10:/mega|victory|unlock|rank_up|mural/.test(r.id)?-6:r.id.startsWith('snd_ability_')?-8:/invalid|target|hot_cell|pure_line|grace/.test(r.id)?-14:-10;
  const gain=r.pilotPreview?1:Math.pow(10,peakDb/20)/st.peak;const fade=Math.max(1,Math.round(rate*.005));
  for(let i=0;i<n;i++){const env=r.pilotPreview?1:Math.min(1,i/Math.max(1,Math.round(rate*.0005)),(n-1-i)/fade);for(let c=0;c<ch;c++)out[i*ch+c]=(pcm[i*ch+c]??0)*gain*env;}
  const args=['-v','error','-y','-f','f32le','-ar',String(rate),'-ac',String(ch),'-i','pipe:0'];args.push(...(target.endsWith('.wav')?['-c:a','pcm_s16le']:['-c:a','libvorbis','-q:a','5']),target);cp.execFileSync('ffmpeg',args,{input:Buffer.from(out.buffer),maxBuffer:16e6});
  item.processing={targetSeconds:n/rate,onsetRemovedSeconds:start/rate,timeCompression:speed,gainDb:20*Math.log10(gain),pilotPreviewReused:!!r.pilotPreview,channels:ch,rate};
 }else{
  // Form one periodic cycle by crossfading adjacent copies, retain [2,180),
  // then stretch that 178-second cycle back to 180 seconds without pitch shift.
  const loop=base+'/staged/'+r.id+'-loop.wav';
  run(['-i',source,'-filter_complex','[0:a]aresample='+rate+',atrim=duration=180,asetpts=PTS-STARTPTS,asplit=2[a][b];[a][b]acrossfade=d=2:c1=tri:c2=tri,atrim=start=2:end=180,asetpts=PTS-STARTPTS,atempo=0.9888888888888889[o]','-map','[o]','-c:a','pcm_s24le',loop]);
  // Correct the tempo filter's small sample-count drift by periodic interpolation,
  // not silence padding. The last sample interpolates towards the first sample.
  const timed=decode(loop,ch,rate),frames=timed.length/ch,count=180*rate,exact=new Float32Array(count*ch);
  for(let i=0;i<count;i++){const x=i*frames/count,j=Math.floor(x),f=x-j;for(let c=0;c<ch;c++)exact[i*ch+c]=timed[j*ch+c]*(1-f)+timed[((j+1)%frames)*ch+c]*f;}
  cp.execFileSync('ffmpeg',['-v','error','-y','-f','f32le','-ar',String(rate),'-ac',String(ch),'-i','pipe:0','-c:a','pcm_s24le',loop],{input:Buffer.from(exact.buffer),maxBuffer:160e6});
  const meter=cp.spawnSync('ffmpeg',['-hide_banner','-i',loop,'-af','loudnorm=I=-16:TP=-1.5:LRA=9:print_format=json','-f','null','-'],{encoding:'utf8'});if(meter.status!==0)throw Error(meter.stderr);const m=JSON.parse(meter.stderr.slice(meter.stderr.lastIndexOf('{'),meter.stderr.lastIndexOf('}')+1));const db=Math.min(-16-Number(m.input_i),-1.7-Number(m.input_tp));
  run(['-i',loop,'-af','volume='+db+'dB','-c:a','libvorbis','-q:a','5',target]);item.processing={circularCrossfadeSeconds:2,timeStretch:178/180,gainDb:db,targetSeconds:180,rate,channels:ch,loopMeterInput:{lufs:Number(m.input_i),truePeakDb:Number(m.input_tp),lra:Number(m.input_lra)},perceptualLoopValidation:'Pending Android listening; mathematical boundary continuity is not musical approval'};
 }
 const decoded=decode(target,ch,rate),s=stats(decoded),expected=r.id.startsWith('bgm_')?180:r.durationSeconds;
 const meta=JSON.parse(cp.execFileSync('ffprobe',['-v','error','-show_entries','format=duration','-of','json',target],{encoding:'utf8'}));const declared=Number(meta.format.duration);
 // Some Vorbis decoders expose a final padded block beyond the Ogg end granule.
 // Enforce the resource's duration and report decoder padding separately.
 const tolerance=r.id.startsWith('bgm_')?.002:1024/rate+.00005;
 if(Math.abs(declared-expected)>1/rate+.000001||Math.abs(decoded.length/ch/rate-expected)>tolerance||s.peak>=.99||s.peak<.0001)throw Error('Signal/duration check failed '+r.id+JSON.stringify({declared,decoded:decoded.length/ch/rate,...s}));
 item.finalSha256=hash(target);item.bytes=fs.statSync(target).size;item.verified={declaredSeconds:declared,decodedSeconds:decoded.length/ch/rate,decoderPaddingSeconds:decoded.length/ch/rate-declared,peakDb:20*Math.log10(s.peak),rmsDb:20*Math.log10(s.rms),allFinite:true};
 if(r.id.startsWith('bgm_')){let seam=0,adjacent=0;for(let c=0;c<ch;c++){seam=Math.max(seam,Math.abs(decoded[c]-decoded[decoded.length-ch+c]));for(let i=ch+c;i<Math.min(decoded.length,rate*ch);i+=ch)adjacent=Math.max(adjacent,Math.abs(decoded[i]-decoded[i-ch]));}item.verified.seamStep=seam;item.verified.firstSecondMaxStep=adjacent;}
 records.push(item);console.log(r.id+' OK');
}
fs.writeFileSync('docs/audio/renovacion-resultados.json',JSON.stringify({status:'staged; static signal checks passed; device listening pending',base,records},null,2)+'\n');
