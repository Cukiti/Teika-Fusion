const fs=require('fs'),cp=require('child_process'),crypto=require('crypto');
const catalogPath='docs/audio/seleccion-suave-candidatos.json',reportPath='docs/audio/renovacion-resultados.json';
const catalog=JSON.parse(fs.readFileSync(catalogPath)),report=JSON.parse(fs.readFileSync(reportPath));
const candidate=catalog.results.find(r=>r.id==='I02'),record=report.records.find(r=>r.id==='snd_play_launch');
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const base='%USERPROFILE%/.codex/artifacts/elevenlabs/gentle-selection-2026-09-14',backup=base+'/I02-before-tin.ogg',stage=base+'/I02-approved-tin.ogg';
if(fs.existsSync(backup)||hash(candidate.preview)!==candidate.sha256||hash(record.target)!==record.finalSha256)throw Error('Existing backup or changed audio: inspect before proceeding');
const previous=structuredClone(record);fs.copyFileSync(record.target,backup);
// Explicit duplication preserves the approved mono level in both channels.
cp.execFileSync('ffmpeg',['-v','error','-y','-i',candidate.preview,'-af','pan=stereo|c0=c0|c1=c0','-c:a','libvorbis','-q:a','5',stage]);
const bytes=cp.execFileSync('ffmpeg',['-v','error','-i',stage,'-f','f32le','pipe:1']);
const pcm=new Float32Array(bytes.buffer,bytes.byteOffset,bytes.length/4);let peak=0;
for(const v of pcm){if(!Number.isFinite(v))throw Error('Invalid samples');peak=Math.max(peak,Math.abs(v));}
const duration=pcm.length/2/44100;if(Math.abs(duration-candidate.duration)>1/44100||peak>=1||peak<.0001)throw Error('Audio validation failed');
if(hash(record.target)!==previous.finalSha256)throw Error('Concurrent change');
fs.copyFileSync(stage,record.target);const receipt=JSON.parse(fs.readFileSync(candidate.receipt));
Object.assign(record,{source:candidate.source,sourceSha256:hash(candidate.source),sourceModel:receipt.body.model_id,generationTimestamp:receipt.completedAt,prompt:receipt.body.text,stage,finalSha256:hash(stage),bytes:fs.statSync(stage).size,processing:{rate:44100,channels:2,approvedPreview:candidate.preview,description:'Approved soft TIN; mono duplicated to stereo without attenuation'},verified:{declaredSeconds:duration,decodedSeconds:duration,peakDb:20*Math.log10(peak),allFinite:true}});
candidate.status='approved and installed';candidate.installedAt=new Date().toISOString();
fs.writeFileSync(reportPath,JSON.stringify(report,null,2)+'\n');fs.writeFileSync(catalogPath,JSON.stringify(catalog,null,2)+'\n');
fs.writeFileSync('docs/audio/inicio-tin-integracion.json',JSON.stringify({installedAt:candidate.installedAt,approvedByUser:true,previous,backup,current:record},null,2)+'\n');console.log(JSON.stringify({duration,peakDb:record.verified.peakDb,backup}));
