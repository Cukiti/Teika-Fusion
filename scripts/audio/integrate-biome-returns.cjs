const fs=require('fs'),cp=require('child_process'),crypto=require('crypto');
const catalogPath='docs/audio/retornos-bioma-candidatos.json',reportPath='docs/audio/renovacion-resultados.json';
const catalog=JSON.parse(fs.readFileSync(catalogPath)),report=JSON.parse(fs.readFileSync(reportPath));
const base='%USERPROFILE%/.codex/artifacts/elevenlabs/biome-returns-2026-09-14';
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),prepared=[];
for(const c of catalog.results){
 const old=null,separate=true,id=c.resource;
 const target=`app/src/main/res/raw/${id}.ogg`,backup=null,stage=`${base}/${c.id}-approved.ogg`;
 if(hash(c.preview)!==c.sha256||fs.existsSync(target)||report.records.some(r=>r.id===id))throw Error(c.id+' changed or installed');
 cp.execFileSync('ffmpeg',['-v','error','-y','-i',c.preview,'-af','pan=stereo|c0=c0|c1=c0','-c:a','libvorbis','-q:a','5',stage]);
 const b=cp.execFileSync('ffmpeg',['-v','error','-i',stage,'-f','f32le','pipe:1']),x=new Float32Array(b.buffer,b.byteOffset,b.length/4);let peak=0;for(const v of x){if(!Number.isFinite(v))throw Error(c.id+' PCM');peak=Math.max(peak,Math.abs(v));}const duration=x.length/88200;
 const declared=Number(cp.execFileSync('ffprobe',['-v','error','-show_entries','format=duration','-of','default=noprint_wrappers=1:nokey=1',stage]).toString().trim());
 if(Math.abs(declared-c.duration)>1/44100||Math.abs(duration-declared)>1024/44100||peak>=1||peak<.0001)throw Error(c.id+' verification');
 const receipt=JSON.parse(fs.readFileSync(c.receipt));prepared.push({c,old,previous:structuredClone(old),separate,backup,stage,target,current:{id,target,source:c.source,sourceSha256:hash(c.source),sourceModel:receipt.body.model_id,generationTimestamp:receipt.completedAt,prompt:receipt.body.text,stage,finalSha256:hash(stage),bytes:fs.statSync(stage).size,processing:{rate:44100,channels:2,approvedPreview:c.preview},verified:{declaredSeconds:declared,decodedSeconds:duration,peakDb:20*Math.log10(peak),allFinite:true}}});
}
for(const p of prepared)if(fs.existsSync(p.target))throw Error('Concurrent change');
for(const p of prepared){fs.copyFileSync(p.stage,p.target);if(p.separate)report.records.push(p.current);else Object.assign(p.old,p.current);p.c.status='approved and installed';p.c.installedResource=p.current.id;}
catalog.installed=true;fs.writeFileSync(catalogPath,JSON.stringify(catalog,null,2)+'\n');fs.writeFileSync(reportPath,JSON.stringify(report,null,2)+'\n');
fs.writeFileSync('docs/audio/retornos-bioma-integracion.json',JSON.stringify({installedAt:new Date().toISOString(),approvedByUser:true,integrations:prepared.map(p=>({id:p.c.id,previous:p.previous,backup:p.backup,separateSelectionResource:false,newResource:true,current:p.current}))},null,2)+'\n');console.log('Four new approved effects installed');

