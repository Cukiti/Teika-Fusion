// Install or restore only the 89 inventoried assets. Reject unrelated edits.
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const file='docs/audio/renovacion-resultados.json',report=JSON.parse(fs.readFileSync(file,'utf8'));
const restore=process.argv.includes('--restore'),hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const raw=path.resolve('app/src/main/res/raw')+path.sep;
for(const r of report.records){if(!path.resolve(r.target).startsWith(raw))throw Error('Target outside raw');if(hash(r.target)!==(restore?r.finalSha256:r.backupSha256))throw Error('Modified target; preserve edits: '+r.target);if(hash(restore?r.backup:r.stage)!==(restore?r.backupSha256:r.finalSha256))throw Error('Source hash mismatch '+r.id);}
const completed=[];try{for(const r of report.records){fs.copyFileSync(restore?r.backup:r.stage,r.target);completed.push(r);}}catch(e){for(const r of completed)fs.copyFileSync(restore?r.stage:r.backup,r.target);throw e;}
report.status=restore?'restored original assets':'installed; static signal checks passed; device listening pending';report.installedAt=new Date().toISOString();fs.writeFileSync(file,JSON.stringify(report,null,2)+'\n');console.log((restore?'Restored ':'Installed ')+completed.length+' verified assets');
