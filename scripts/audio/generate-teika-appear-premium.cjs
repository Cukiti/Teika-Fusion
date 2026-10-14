// Generate one user-authorized J07 candidate with ElevenLabs Sound Effects v2.
// It does not alter the game resource.
const fs = require('fs');
const cp = require('child_process');
const crypto = require('crypto');
const base = '%USERPROFILE%/.codex/artifacts/elevenlabs/teika-sfx-review-2026-09-13';
const receiptPath = base + '/j07-teika-appear-generation.json';
const sourcePath = base + '/j07-teika-appear-source.pcm';
const previewPath = base + '/j07-teika-appear-preview.wav';
const listenPath = base + '/j07-teika-appear-listen.wav';
async function main() {
  if (fs.existsSync(receiptPath)) throw new Error('A previous J07 attempt exists; inspect before retrying.');
  const key = process.env.ELEVENLABS_API_KEY;
  if (!key) throw new Error('ElevenLabs API key is unavailable.');
  const body = {
    model_id: 'eleven_text_to_sound_v2', duration_seconds: 0.6, prompt_influence: 0.9, loop: false,
    text: 'Single clean premium magical-creature appearance sound for a polished puzzle game. One small rounded soft pop blooms into one pure short rising kawaii electropop synth tone, expressing a cute Teika being born. Warm, elegant, joyful, tactile, studio-isolated and dry; complete within 0.48 seconds. No air, whoosh, hiss, static, crackle, fizz, noise layer, room tone, voice, chirp, melody, echo, reverb tail, distortion or heavy bass.'
  };
  const receipt = { startedAt: new Date().toISOString(), body, status: 'started; do not retry automatically' };
  fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2) + '\n');
  let response;
  try { response = await fetch('https://api.elevenlabs.io/v1/sound-generation?output_format=pcm_24000', { method: 'POST', headers: { 'xi-api-key': key, 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(90000) }); }
  catch (error) { receipt.status = 'transport outcome uncertain'; receipt.error = error.name; fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2) + '\n'); throw new Error('Generation outcome is uncertain; inspect before retrying.'); }
  receipt.httpStatus = response.status; receipt.cost = response.headers.get('character-cost');
  if (!response.ok) { receipt.status = 'rejected'; receipt.error = (await response.text()).slice(0, 2000); fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2) + '\n'); throw new Error(receipt.error); }
  const source = Buffer.from(await response.arrayBuffer()); fs.writeFileSync(sourcePath, source);
  receipt.status = 'generated; candidate not installed'; receipt.output = sourcePath; receipt.completedAt = new Date().toISOString();
  fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2) + '\n');
  const decoded = cp.execFileSync('ffmpeg', ['-v', 'error', '-f', 's16le', '-ar', '24000', '-ac', '1', '-i', sourcePath, '-af', 'highpass=f=180,lowpass=f=6500', '-ar', '44100', '-ac', '1', '-f', 'f32le', 'pipe:1']);
  let samples = new Float32Array(decoded.buffer, decoded.byteOffset, decoded.length / 4);
  let peak = 0; for (const value of samples) peak = Math.max(peak, Math.abs(value));
  let first = 0, last = samples.length - 1;
  while (first < last && Math.abs(samples[first]) < peak * 0.018) first++;
  while (last > first && Math.abs(samples[last]) < peak * 0.012) last--;
  first = Math.max(0, first - 44); last = Math.min(samples.length - 1, last + 132);
  samples = samples.subarray(first, last + 1);
  const targetSamples = Math.round(0.48 * 44100);
  const speed = samples.length / targetSamples;
  if (speed > 1.03) {
    const compressed = cp.execFileSync('ffmpeg', ['-v', 'error', '-f', 'f32le', '-ar', '44100', '-ac', '1', '-i', 'pipe:0', '-af', 'atempo=' + speed, '-f', 'f32le', 'pipe:1'], { input: Buffer.from(samples.buffer, samples.byteOffset, samples.byteLength) });
    samples = new Float32Array(compressed.buffer, compressed.byteOffset, compressed.length / 4);
  }
  const output = new Float32Array(targetSamples); peak = 0;
  for (let i = 0; i < targetSamples; i++) { const envelope = Math.min(1, i / 44, (targetSamples - 1 - i) / 353); output[i] = (samples[i] ?? 0) * envelope; peak = Math.max(peak, Math.abs(output[i])); }
  if (peak < 0.0001) throw new Error('The J07 candidate is silent after editing.');
  const gain = Math.pow(10, -10.5 / 20) / peak; for (let i = 0; i < output.length; i++) output[i] *= gain;
  const writeWav = (file, bytes) => cp.execFileSync('ffmpeg', ['-v', 'error', '-y', '-f', 'f32le', '-ar', '44100', '-ac', '1', '-i', 'pipe:0', '-c:a', 'pcm_s16le', file], { input: bytes });
  const bytes = Buffer.from(output.buffer); writeWav(previewPath, bytes);
  const silence = Buffer.alloc(Math.round(44100 * 0.55) * 4); writeWav(listenPath, Buffer.concat([bytes, silence, bytes, silence, bytes]));
  const result = { id: 'J07', resource: 'snd_teika_appear', model: body.model_id, source: sourcePath, sourceSha256: crypto.createHash('sha256').update(source).digest('hex'), preview: previewPath, listen: listenPath, durationSeconds: output.length / 44100, peakDb: -10.5, onsetRemovedSeconds: first / 44100, timeCompression: speed > 1.03 ? speed : 1, processing: 'PCM 24 kHz source; mono 44.1 kHz preview; 180 Hz high-pass; 6.5 kHz low-pass; no spectral denoise; tight noise threshold and zero-ended envelope.', status: 'Candidate only; current game resource unchanged' };
  fs.writeFileSync('docs/audio/j07-aparicion-teika-candidata.json', JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify({ model: result.model, duration: result.durationSeconds, peakDb: result.peakDb, listen: result.listen, cost: receipt.cost }));
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
