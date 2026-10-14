// Candidate only: remove measured silent outro, process repeated PCM with warm context.
const fs=require('fs'),cp=require('child_process');
const base='%USERPROFILE%/.codex/artifacts/elevenlabs/gentle-selection-2026-09-14',rate=48000;
const source='%USERPROFILE%/.codex/artifacts/elevenlabs/teika-original-references/bgm_gameplay-music-v2_5.mp3';
const ff=a=>cp.execFileSync('ffmpeg',['-v','error','-y',...a],{maxBuffer:256e6});
const raw=ff(['-i',source,'-t','174','-ar','48000','-ac','2','-f','f32le','pipe:1']);
const x=new Float32Array(raw.buffer,raw.byteOffset,raw.length/4),n=173*rate,fade=rate,out=new Float32Array(n*2);
// Start after the portion used by the crossfade, so no opening samples are replayed.
out.set(x.subarray(fade*2,173*rate*2));
for(let i=0;i<fade;i++){const t=i/(fade-1),w=t*t*(3-2*t);for(let c=0;c<2;c++)out[(n-fade+i)*2+c]=x[(173*rate+i)*2+c]*(1-w)+x[i*2+c]*w;}
const bytes=Buffer.from(out.buffer),wav=base+'/menu-sin-cola.wav';
fs.writeFileSync(base+'/three-cycles.pcm',Buffer.concat([bytes,bytes,bytes]));
cp.execFileSync('ffmpeg',['-v','error','-y','-f','f32le','-ar','48000','-ac','2','-i',base+'/three-cycles.pcm','-af','atempo=0.9611111111111111,atrim=start=180:end=360,asetpts=PTS-STARTPTS,volume=-2dB','-c:a','pcm_s24le',wav],{maxBuffer:256e6});
const candidate=base+'/menu-sin-cola.ogg';ff(['-i',wav,'-c:a','libvorbis','-q:a','5',candidate]);
for(const [name,file] of [['antes','app/src/main/res/raw/bgm_gameplay.ogg'],['despues',candidate]])ff(['-sseof','-10','-i',file,'-i',file,'-filter_complex','[0:a]atrim=0:10[a];[1:a]atrim=0:10[b];[a][b]concat=n=2:v=0:a=1[o]','-map','[o]','-c:a','pcm_s16le',base+'/menu-'+name+'.wav']);
fs.writeFileSync('docs/audio/menu-bucle-auditoria.json',JSON.stringify({source,observedSourceSilence:[177.001625,180.024],observedInstalledSilence:[176.958479,178.010896],candidate,processing:'Source 0..174 seconds, 1s circular overlap; 173s stretched to 180 using three-cycle context and middle-cycle extraction.',status:'Candidate only; musical listening and device loop validation pending; no device connected'},null,2));

