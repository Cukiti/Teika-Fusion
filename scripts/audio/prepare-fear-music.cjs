const fs=require('fs'),cp=require('child_process');
const base='%USERPROFILE%/.codex/artifacts/elevenlabs/biome-fear-music-2026-09-15',source=base+'/bgm_fear-source.mp3';
const ff=a=>cp.spawnSync('ffmpeg',['-hide_banner','-y',...a],{encoding:'utf8',maxBuffer:4e6});
const probe=JSON.parse(cp.execFileSync('ffprobe',['-v','error','-show_entries','format=duration','-of','json',source]));
let r=ff(['-i',source,'-af','loudnorm=I=-20:TP=-3:LRA=11:print_format=json','-f','null','NUL']);if(r.status)throw Error(r.stderr);const m=JSON.parse(r.stderr.slice(r.stderr.lastIndexOf('{'),r.stderr.lastIndexOf('}')+1));
const gain=Math.min(-20-Number(m.input_i),-3-Number(m.input_tp));
const preview=base+'/bgm_fear-preview.wav';r=ff(['-i',source,'-t','180','-af','volume='+gain+'dB','-ar','44100','-ac','2','-c:a','pcm_s16le',preview]);if(r.status)throw Error(r.stderr);
const loopPreview=base+'/bgm_fear-loop-check.wav';r=ff(['-i',preview,'-filter_complex','[0:a]asplit=2[a][b];[a]atrim=start=172:end=180,asetpts=PTS-STARTPTS[end];[b]atrim=start=0:end=8,asetpts=PTS-STARTPTS[start];[end][start]concat=n=2:v=0:a=1[out]','-map','[out]',loopPreview]);if(r.status)throw Error(r.stderr);
r=ff(['-i',preview,'-af','silencedetect=n=-45dB:d=0.15','-f','null','NUL']);
const report={id:'bgm_fear',model:'music_v2_5',source,sourceDeclaredSeconds:Number(probe.format.duration),preview,loopPreview,gainDb:gain,sourceMeter:m,silenceReport:r.stderr.split('\n').filter(x=>x.includes('silence_')),status:'candidate; not installed; musical loop listening pending',processing:'Static gain only; maximum 180 seconds. No crossfade, intro repeat or time stretch.'};
fs.writeFileSync('docs/audio/miedo-musica-candidata.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));

