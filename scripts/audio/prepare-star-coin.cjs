// Prepare a 366 ms review WAV. Does not alter game assets.
const fs = require('fs');
const cp = require('child_process');
const crypto = require('crypto');
const base = '%USERPROFILE%/.codex/artifacts/elevenlabs/teika-sfx-approved-2026-09-13';
const source = base + '/star-coin-source.mp3';
const targetSamples = 16141;

const decoded = cp.execFileSync('ffmpeg', [
  '-v', 'error', '-i', source,
  '-af', 'highpass=f=700,lowpass=f=11000',
  '-ac', '1', '-ar', '44100', '-f', 'f32le', 'pipe:1'
]);
let samples = new Float32Array(decoded.buffer, decoded.byteOffset, decoded.length / 4);
let peak = 0;
for (const value of samples) peak = Math.max(peak, Math.abs(value));
let first = 0;
while (first < samples.length && Math.abs(samples[first]) < peak * 0.012) first++;
first = Math.max(0, first - 88);
samples = samples.subarray(first);

const speed = samples.length / targetSamples;
if (speed > 1.03) {
  const compressed = cp.execFileSync('ffmpeg', [
    '-v', 'error', '-f', 'f32le', '-ar', '44100', '-ac', '1', '-i', 'pipe:0',
    '-af', 'atempo=' + speed, '-f', 'f32le', 'pipe:1'
  ], { input: Buffer.from(samples.buffer, samples.byteOffset, samples.byteLength) });
  samples = new Float32Array(compressed.buffer, compressed.byteOffset, compressed.length / 4);
}

const output = new Float32Array(targetSamples);
peak = 0;
for (let i = 0; i < targetSamples; i++) {
  const envelope = Math.min(1, i / 44, (targetSamples - 1 - i) / 529);
  output[i] = (samples[i] ?? 0) * envelope;
  peak = Math.max(peak, Math.abs(output[i]));
}
if (peak < 0.0001) throw new Error('Generated star-coin candidate is silent.');
const gain = Math.pow(10, -9 / 20) / peak;
for (let i = 0; i < output.length; i++) output[i] *= gain;

function writeWav(file, pcm) {
  cp.execFileSync('ffmpeg', [
    '-v', 'error', '-y', '-f', 'f32le', '-ar', '44100', '-ac', '1', '-i', 'pipe:0',
    '-c:a', 'pcm_s16le', file
  ], { input: pcm });
}
const bytes = Buffer.from(output.buffer);
const preview = base + '/star-coin-preview.wav';
writeWav(preview, bytes);
const silence = Buffer.alloc(Math.round(44100 * 0.65) * 4);
writeWav(base + '/star-coin-listen.wav', Buffer.concat([bytes, silence, bytes, silence, bytes]));

const manifest = {
  source,
  sourceSha256: crypto.createHash('sha256').update(fs.readFileSync(source)).digest('hex'),
  model: 'eleven_text_to_sound_v2',
  preview,
  durationSeconds: targetSamples / 44100,
  peakDb: -9,
  removedOnsetSeconds: first / 44100,
  timeCompression: speed > 1.03 ? speed : 1,
  filters: 'highpass 700 Hz; lowpass 11000 Hz; no spectral denoise',
  status: 'Candidate only; current game star remains unchanged'
};
fs.writeFileSync('docs/audio/estrella-moneda-candidata.json', JSON.stringify(manifest, null, 2) + '\n');
console.log(JSON.stringify(manifest));
