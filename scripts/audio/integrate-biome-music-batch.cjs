const fs = require('fs');
const cp = require('child_process');
const crypto = require('crypto');

const hash = path => crypto.createHash('sha256').update(fs.readFileSync(path)).digest('hex');
const registryPath = 'docs/audio/renovacion-resultados.json';
const registry = JSON.parse(fs.readFileSync(registryPath));
const items = [
  ['bgm_storm', 'docs/audio/tormenta-musica.json'],
  ['bgm_volcano', 'docs/audio/volcan-tribal-musica.json'],
  ['bgm_ninja', 'docs/audio/dojo-musica.json'],
  ['bgm_glacier', 'docs/audio/glaciar-musica.json'],
  ['bgm_ghost', 'docs/audio/mansion-espectral-waltz.json'],
  ['bgm_glutton', 'docs/audio/cocina-voraz-utensilios.json'],
  ['bgm_toxic', 'docs/audio/pantano-toxico-musica.json'],
  ['bgm_star', 'docs/audio/cielo-estelar-musica.json'],
  ['bgm_king', 'docs/audio/trono-real-musica.json'],
  ['bgm_galactic', 'docs/audio/espacio-profundo-musica.json'],
];

for (const [id] of items) {
  const target = `app/src/main/res/raw/${id}.ogg`;
  if (fs.existsSync(target) || registry.records.some(record => record.id === id)) {
    throw Error(`Already installed: ${id}`);
  }
}

const installedAt = new Date().toISOString();
const records = [];
for (const [id, reportPath] of items) {
  const report = JSON.parse(fs.readFileSync(reportPath));
  const target = `app/src/main/res/raw/${id}.ogg`;
  cp.execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', report.preview, '-c:a', 'libvorbis', '-q:a', '5', target]);
  const seconds = Number(cp.execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'default=noprint_wrappers=1:nokey=1', target]).toString());
  if (Math.abs(seconds - 30) > 0.001) throw Error(`Invalid duration: ${id} ${seconds}`);
  const record = {
    id,
    target,
    source: report.source,
    sourceSha256: hash(report.source),
    sourceModel: 'music_v2_5',
    finalSha256: hash(target),
    bytes: fs.statSync(target).size,
    processing: {
      rate: 44100,
      channels: 2,
      approvedPreview: report.preview,
      previewSha256: hash(report.preview),
      description: report.processing,
    },
    verified: { declaredSeconds: seconds, pendingDeviceCheck: true },
  };
  records.push(record);
  report.status = 'approved and installed';
  fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`);
}

registry.records.push(...records);
fs.writeFileSync(registryPath, `${JSON.stringify(registry, null, 2)}\n`);
fs.writeFileSync('docs/audio/musicas-biomas-integracion.json', `${JSON.stringify({
  records,
  approvedByUser: true,
  installedAt,
  previousRouting: 'bgm_gameplay preserved as fallback and generic gameplay track',
}, null, 2)}\n`);
console.log(JSON.stringify(records.map(({ id, bytes }) => ({ id, bytes }))));
