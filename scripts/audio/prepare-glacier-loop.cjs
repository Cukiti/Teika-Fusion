const fs=require('fs'),cp=require('child_process');const base='%USERPROFILE%/.codex/artifacts/elevenlabs/glacier-loop-2026-09-15';
const b=cp.execFileSync('ffmpeg',['-v','error','-i',base+'/bridge.mp3','-ac','2','-ar','44100','-f','f32le','pipe:1'],{maxBuffer:40e6});const x=new Float32Array(b.buffer,b.byteOffset,b.length/4),rate=44100,start=5*rate;
let best={mse:Infinity};for(let shift=-2205;shift<=2205;shift++){const end=35*rate+shift;let err=0,power=0;for(let j=0;j<4096;j+=16){const a=x[(start+j)*2],v=x[(end+j)*2];err+=(a-v)**2;power+=a*a;}const mse=err/power;if(mse<best.mse)best={mse,shift,end};}
// Lossy generation does not reproduce references sample-for-sample. Select a quiet
// waveform crossing at corresponding positions, keeping the intended 30-second cycle.
const referenceComparison=best;let seam={score:Infinity};
for(let frame=start-2205;frame<=start+2205;frame++){
 const end=frame+30*rate;let score=0,step=0;
 for(let ch=0;ch<2;ch++){
  const a=x[frame*2+ch],z=x[(end-1)*2+ch];
  const delta=a-z;step=Math.max(step,Math.abs(delta));
  score+=delta*delta+0.1*(a*a+z*z);
 }
 if(score<seam.score)seam={score,start:frame,end,step};
}
// Record boundary error rather than claim perceptual continuity from a numeric threshold.
best={...seam,referenceComparison};
const out=x.slice(best.start*2,best.end*2);let peak=0;for(const v of out){if(!Number.isFinite(v))throw Error('Invalid PCM');peak=Math.max(peak,Math.abs(v));}if(peak>=1)throw Error('Clipping');
const preview=base+'/glaciar-musica.wav';cp.execFileSync('ffmpeg',['-v','error','-y','-f','f32le','-ar','44100','-ac','2','-i','pipe:0','-c:a','pcm_s16le',preview],{input:Buffer.from(out.buffer),maxBuffer:40e6});
const d={preview,source:base+'/bridge.mp3',duration:out.length/88200,alignment:best,peakDb:20*Math.log10(peak),processing:'One 30-second cycle between corresponding positions inside the repeated references; boundary selected near a quiet waveform crossing. References are not sample-identical; musical continuity awaits listening. No crossfade, fade, padding or tempo adjustment.',status:'awaiting musical listening; not installed'};fs.writeFileSync('docs/audio/glaciar-musica.json',JSON.stringify(d,null,2)+'\n');console.log(JSON.stringify(d));

