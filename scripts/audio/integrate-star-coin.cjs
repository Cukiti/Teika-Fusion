// Integrate the user-approved star-coin preview transactionally.
const fs = require('fs');
const cp = require('child_process');
const crypto = require('crypto');
const base = '%USERPROFILE%/.codex/artifacts/elevenlabs/teika-sfx-approved-2026-09-13';
const reportPath = 'docs/audio/renovacion-resultados.json';
const report = JSON.parse(fs.readFileSync(reportPath, 'utf8'));
const candidate = JSON.parse(fs.readFileSync('docs/audio/estrella-moneda-candidata.json', 'utf8'));
const record = report.records.find(item => item.id === 'snd_target_collect');
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');

if (!record) throw new Error('snd_target_collect is absent from the audio manifest.');
if (hash(record.target) !== record.finalSha256) throw new Error('The current star asset changed after the manifest was created.');
if (hash(candidate.preview) !== crypto.createHash('sha256').update(fs.readFileSync(candidate.preview)).digest('hex')) throw new Error('Candidate hash check failed.');

const backup = base + '/snd_target_collect-previous.ogg';
const stage = base + '/snd_target_collect.ogg';
if (fs.existsSync(backup)) throw new Error('A previous star backup already exists; inspect it before rerunning.');
fs.copyFileSync(record.target, backup);

cp.execFileSync('ffmpeg', [
  '-v', 'error', '-y', '-i', candidate.preview,
  '-ar', '44100', '-ac', '2', '-c:a', 'libvorbis', '-q:a', '5', stage
]);
const probe = JSON.parse(cp.execFileSync('ffprobe', [
  '-v', 'error', '-show_entries', 'format=duration:stream=codec_name,sample_rate,channels', '-of', 'json', stage
], { encoding: 'utf8' }));
if (Math.abs(Number(probe.format.duration) - candidate.durationSeconds) > 1 / 44100) throw new Error('The staged duration changed.');
const raw = cp.execFileSync('ffmpeg', ['-v', 'error', '-i', stage, '-f', 'f32le', 'pipe:1']);
const samples = new Float32Array(raw.buffer, raw.byteOffset, raw.length / 4);
let peak = 0;
for (const value of samples) {
  if (!Number.isFinite(value)) throw new Error('The staged file contains invalid samples.');
  peak = Math.max(peak, Math.abs(value));
}
if (peak >= 0.99 || peak < 0.0001) throw new Error('The staged signal failed its peak check.');

if (hash(record.target) !== record.finalSha256) throw new Error('The target changed during preparation.');
try {
  fs.copyFileSync(stage, record.target);
} catch (error) {
  fs.copyFileSync(backup, record.target);
  throw error;
}

const previous = { ...record };
Object.assign(record, {
  source: candidate.source,
  sourceSha256: candidate.sourceSha256,
  sourceModel: candidate.model,
  stage,
  finalSha256: hash(stage),
  bytes: fs.statSync(stage).size,
  prompt: 'User-approved coin pickup; see star-coin-generation.json and estrella-moneda-candidata.json.',
  generationTimestamp: JSON.parse(fs.readFileSync(base + '/star-coin-generation.json', 'utf8')).completedAt,
  processing: {
    rate: 44100,
    channels: 2,
    approvedPreview: candidate.preview,
    filters: candidate.filters,
    removedOnsetSeconds: candidate.removedOnsetSeconds,
    timeCompression: candidate.timeCompression,
    targetSeconds: candidate.durationSeconds
  },
  verified: {
    declaredSeconds: Number(probe.format.duration),
    decodedSeconds: samples.length / 2 / 44100,
    peakDb: 20 * Math.log10(peak),
    allFinite: true
  }
});
candidate.status = 'User approved and installed';
fs.writeFileSync(reportPath, JSON.stringify(report, null, 2) + '\n');
fs.writeFileSync('docs/audio/estrella-moneda-candidata.json', JSON.stringify(candidate, null, 2) + '\n');
fs.writeFileSync('docs/audio/estrella-moneda-integracion.json', JSON.stringify({
  installedAt: new Date().toISOString(),
  approvedByUser: true,
  previous,
  previousBackup: backup,
  current: record
}, null, 2) + '\n');
console.log(JSON.stringify({ installed: record.id, duration: record.verified.declaredSeconds, peakDb: record.verified.peakDb }));
