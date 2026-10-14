// Integrate user-approved I02 and I03 while preserving previous resources.
const fs=require('fs'),cp=require('child_process'),crypto=require('crypto');
const base='%USERPROFILE%/.codex/artifacts/elevenlabs/teika-sfx-review-2026-09-13',reportPath='docs/audio/renovacion-resultados.json';
const report=JSON.parse(fs.readFileSync(reportPath,'utf8'));
const specs=[['I02','play-launch','snd_play_launch'],['I03','invalid','snd_invalid']];
const hash=f=>crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex'),prepared=[];
for(const [code,slug,resource] of specs){
  const candidatePath=`docs/audio/${code.toLowerCase()}-${slug}-candidata.json`,candidate=JSON.parse(fs.readFileSync(candidatePath,'utf8'));
  const record=report.records.find(x=>x.id===resource); if(!record||candidate.id!==code)throw Error(`${code} manifest mismatch`);
  if(hash(record.target)!==record.finalSha256)throw Error(`${code} current resource changed`);
  const backup=`${base}/${code.toLowerCase()}-${slug}-previous.ogg`,stage=`${base}/${code.toLowerCase()}-${slug}-approved.ogg`;
  if(fs.existsSync(backup))throw Error(`${code} backup exists`); fs.copyFileSync(record.target,backup);
  cp.execFileSync('ffmpeg',['-v','error','-y','-i',candidate.preview,'-ar','44100','-ac','2','-c:a','libvorbis','-q:a','5',stage]);
  const probe=JSON.parse(cp.execFileSync('ffprobe',['-v','error','-show_entries','format=duration','-of','json',stage],{encoding:'utf8'}));
  const raw=cp.execFileSync('ffmpeg',['-v','error','-i',stage,'-f','f32le','pipe:1']),samples=new Float32Array(raw.buffer,raw.byteOffset,raw.length/4);
  let peak=0;for(const v of samples){if(!Number.isFinite(v))throw Error(`${code} invalid PCM`);peak=Math.max(peak,Math.abs(v));}
  if(Math.abs(Number(probe.format.duration)-candidate.durationSeconds)>1/44100||peak>=.99||peak<.0001)throw Error(`${code} quality gate failed`);
  prepared.push({code,slug,candidatePath,candidate,record,previous:{...record},backup,stage,probe,samples,peak});
}
const copied=[];try{for(const x of prepared){if(hash(x.record.target)!==x.record.finalSha256)throw Error(`${x.code} changed during preparation`);fs.copyFileSync(x.stage,x.record.target);copied.push(x);}}catch(e){for(const x of copied)fs.copyFileSync(x.backup,x.record.target);throw e;}
const installedAt=new Date().toISOString(),integrations=[];
for(const x of prepared){const receipt=JSON.parse(fs.readFileSync(`${base}/${x.code.toLowerCase()}-${x.slug}-generation.json`,'utf8'));
  Object.assign(x.record,{source:x.candidate.source,sourceSha256:x.candidate.sourceSha256,sourceModel:x.candidate.model,stage:x.stage,finalSha256:hash(x.stage),bytes:fs.statSync(x.stage).size,prompt:`User-approved premium ${x.code}; see generation receipt.`,generationTimestamp:receipt.completedAt,processing:{rate:44100,channels:2,approvedPreview:x.candidate.preview,description:x.candidate.processing,targetSeconds:x.candidate.durationSeconds},verified:{declaredSeconds:Number(x.probe.format.duration),decodedSeconds:x.samples.length/2/44100,peakDb:20*Math.log10(x.peak),allFinite:true}});
  x.candidate.status='User approved and installed';fs.writeFileSync(x.candidatePath,JSON.stringify(x.candidate,null,2)+'\n');integrations.push({id:x.code,previous:x.previous,previousBackup:x.backup,current:x.record});}
fs.writeFileSync(reportPath,JSON.stringify(report,null,2)+'\n');fs.writeFileSync('docs/audio/interfaz-restante-integracion.json',JSON.stringify({installedAt,approvedByUser:true,integrations},null,2)+'\n');console.log(JSON.stringify(integrations.map(x=>({id:x.id,duration:x.current.verified.declaredSeconds}))));
