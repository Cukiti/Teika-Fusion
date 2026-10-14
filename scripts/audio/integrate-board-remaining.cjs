// Integrate the four user-approved remaining board SFX and preserve previous resources.
const fs = require('fs');
const cp = require('child_process');
const crypto = require('crypto');
const base = '%USERPROFILE%/.codex/artifacts/elevenlabs/teika-sfx-review-2026-09-13';
const reportPath = 'docs/audio/renovacion-resultados.json';
const report = JSON.parse(fs.readFileSync(reportPath, 'utf8'));
const specs = [
  { code: 'J10', resource: 'snd_combo', slug: 'combo' },
  { code: 'J11', resource: 'snd_combo_grace', slug: 'combo-grace' },
  { code: 'J13', resource: 'snd_hot_cell', slug: 'hot-cell' },
  { code: 'J14', resource: 'snd_pure_line', slug: 'pure-line' }
];
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const prepared = [];
for (const spec of specs) {
  const candidatePath = `docs/audio/${spec.code.toLowerCase()}-${spec.slug}-candidata.json`;
  const candidate = JSON.parse(fs.readFileSync(candidatePath, 'utf8'));
  const record = report.records.find(item => item.id === spec.resource);
  if (!record || candidate.id !== spec.code || candidate.resource !== spec.resource) throw new Error(`${spec.code} manifests do not match.`);
  if (hash(record.target) !== record.finalSha256) throw new Error(`Current ${spec.code} changed after its manifest.`);
  const backup = `${base}/${spec.code.toLowerCase()}-${spec.slug}-previous.ogg`;
  const stage = `${base}/${spec.code.toLowerCase()}-${spec.slug}-approved.ogg`;
  if (fs.existsSync(backup)) throw new Error(`${spec.code} backup already exists; inspect before rerunning.`);
  fs.copyFileSync(record.target, backup);
  cp.execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', candidate.preview, '-ar', '44100', '-ac', '2', '-c:a', 'libvorbis', '-q:a', '5', stage]);
  const probe = JSON.parse(cp.execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration:stream=codec_name,sample_rate,channels', '-of', 'json', stage], { encoding: 'utf8' }));
  const raw = cp.execFileSync('ffmpeg', ['-v', 'error', '-i', stage, '-f', 'f32le', 'pipe:1']);
  const samples = new Float32Array(raw.buffer, raw.byteOffset, raw.length / 4);
  let peak = 0;
  for (const value of samples) { if (!Number.isFinite(value)) throw new Error(`Invalid ${spec.code} PCM.`); peak = Math.max(peak, Math.abs(value)); }
  if (Math.abs(Number(probe.format.duration) - candidate.durationSeconds) > 1 / 44100 || peak >= 0.99 || peak < 0.0001) throw new Error(`${spec.code} quality gate failed.`);
  prepared.push({ spec, candidatePath, candidate, record, previous: { ...record }, backup, stage, probe, samples, peak });
}

const installedTargets = [];
try {
  for (const item of prepared) {
    if (hash(item.record.target) !== item.record.finalSha256) throw new Error(`${item.spec.code} changed during preparation.`);
    fs.copyFileSync(item.stage, item.record.target);
    installedTargets.push(item);
  }
} catch (error) {
  for (const item of installedTargets) fs.copyFileSync(item.backup, item.record.target);
  throw error;
}

const installedAt = new Date().toISOString();
const integrations = [];
for (const item of prepared) {
  const receipt = JSON.parse(fs.readFileSync(`${base}/${item.spec.code.toLowerCase()}-${item.spec.slug}-generation.json`, 'utf8'));
  Object.assign(item.record, {
    source: item.candidate.source, sourceSha256: item.candidate.sourceSha256, sourceModel: item.candidate.model,
    stage: item.stage, finalSha256: hash(item.stage), bytes: fs.statSync(item.stage).size,
    prompt: `User-approved premium ${item.spec.code}; see ${item.spec.code.toLowerCase()}-${item.spec.slug}-generation.json.`,
    generationTimestamp: receipt.completedAt,
    processing: { rate: 44100, channels: 2, approvedPreview: item.candidate.preview, description: item.candidate.processing, targetSeconds: item.candidate.durationSeconds },
    verified: { declaredSeconds: Number(item.probe.format.duration), decodedSeconds: item.samples.length / 2 / 44100, peakDb: 20 * Math.log10(item.peak), allFinite: true }
  });
  item.candidate.status = 'User approved and installed';
  fs.writeFileSync(item.candidatePath, JSON.stringify(item.candidate, null, 2) + '\n');
  integrations.push({ id: item.spec.code, previous: item.previous, previousBackup: item.backup, current: item.record });
}
fs.writeFileSync(reportPath, JSON.stringify(report, null, 2) + '\n');
fs.writeFileSync('docs/audio/tablero-restantes-integracion.json', JSON.stringify({ installedAt, approvedByUser: true, integrations }, null, 2) + '\n');
console.log(JSON.stringify(integrations.map(item => ({ id: item.id, duration: item.current.verified.declaredSeconds, peakDb: item.current.verified.peakDb }))));
