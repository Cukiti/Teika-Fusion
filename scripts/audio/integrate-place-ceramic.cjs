// Integrate the user-approved J03 preview and preserve the previous game resource.
const fs = require('fs');
const cp = require('child_process');
const crypto = require('crypto');
const base = '%USERPROFILE%/.codex/artifacts/elevenlabs/teika-sfx-review-2026-09-13';
const reportPath = 'docs/audio/renovacion-resultados.json';
const candidatePath = 'docs/audio/j03-colocacion-ceramica-candidata.json';
const report = JSON.parse(fs.readFileSync(reportPath, 'utf8'));
const candidate = JSON.parse(fs.readFileSync(candidatePath, 'utf8'));
const record = report.records.find(item => item.id === 'snd_place_ceramic');
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
if (!record || candidate.id !== 'J03') throw new Error('J03 manifests do not match.');
if (hash(record.target) !== record.finalSha256) throw new Error('Current J03 changed after its manifest.');

const backup = base + '/j03-place-ceramic-previous.ogg';
const stage = base + '/j03-place-ceramic-approved.ogg';
if (fs.existsSync(backup)) throw new Error('J03 backup already exists; inspect before rerunning.');
fs.copyFileSync(record.target, backup);
cp.execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', candidate.preview, '-ar', '44100', '-ac', '2', '-c:a', 'libvorbis', '-q:a', '5', stage]);
const probe = JSON.parse(cp.execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration:stream=codec_name,sample_rate,channels', '-of', 'json', stage], { encoding: 'utf8' }));
const raw = cp.execFileSync('ffmpeg', ['-v', 'error', '-i', stage, '-f', 'f32le', 'pipe:1']);
const samples = new Float32Array(raw.buffer, raw.byteOffset, raw.length / 4);
let peak = 0;
for (const value of samples) { if (!Number.isFinite(value)) throw new Error('Invalid J03 PCM.'); peak = Math.max(peak, Math.abs(value)); }
if (Math.abs(Number(probe.format.duration) - candidate.durationSeconds) > 1 / 44100 || peak >= 0.99 || peak < 0.0001) throw new Error('J03 quality gate failed.');
if (hash(record.target) !== record.finalSha256) throw new Error('J03 changed during preparation.');
try { fs.copyFileSync(stage, record.target); } catch (error) { fs.copyFileSync(backup, record.target); throw error; }

const previous = { ...record };
Object.assign(record, {
  source: candidate.source,
  sourceSha256: candidate.sourceSha256,
  sourceModel: candidate.model,
  stage,
  finalSha256: hash(stage),
  bytes: fs.statSync(stage).size,
  prompt: 'User-approved premium J03 ceramic placement; see j03-place-ceramic-generation.json.',
  generationTimestamp: JSON.parse(fs.readFileSync(base + '/j03-place-ceramic-generation.json', 'utf8')).completedAt,
  processing: { rate: 44100, channels: 2, approvedPreview: candidate.preview, description: candidate.processing, targetSeconds: candidate.durationSeconds },
  verified: { declaredSeconds: Number(probe.format.duration), decodedSeconds: samples.length / 2 / 44100, peakDb: 20 * Math.log10(peak), allFinite: true }
});
candidate.status = 'User approved and installed';
fs.writeFileSync(reportPath, JSON.stringify(report, null, 2) + '\n');
fs.writeFileSync(candidatePath, JSON.stringify(candidate, null, 2) + '\n');
fs.writeFileSync('docs/audio/j03-colocacion-ceramica-integracion.json', JSON.stringify({ installedAt: new Date().toISOString(), approvedByUser: true, previous, previousBackup: backup, current: record }, null, 2) + '\n');
console.log(JSON.stringify({ installed: 'J03', duration: record.verified.declaredSeconds, peakDb: record.verified.peakDb }));
