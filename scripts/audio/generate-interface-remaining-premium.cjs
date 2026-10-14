// Generate the two remaining user-authorized interface SFX with ElevenLabs Sound Effects v2.
// Existing game resources remain unchanged until user approval.
const fs = require('fs');
const cp = require('child_process');
const crypto = require('crypto');
const base = '%USERPROFILE%/.codex/artifacts/elevenlabs/teika-sfx-review-2026-09-13';
const specs = [
  {
    code: 'I02', slug: 'play-launch', resource: 'snd_play_launch', seconds: 1.05, gainDb: -8.5,
    highpass: 90, lowpass: 7600,
    text: 'Single clean premium game-start cue. A quick rounded magical pulse launches into a focused rising Japanese electropop synth and resolves with one confident soft impact, signaling immediate entry into gameplay. Energetic, elegant and polished, studio-isolated and dry; complete within 1.05 seconds. No voice, drums, melody loop, air, noisy whoosh, hiss, static, crackle, room tone, echo, long reverb, distortion, harsh treble or bass boom.'
  },
  {
    code: 'I03', slug: 'invalid', resource: 'snd_invalid', seconds: 0.30, gainDb: -8,
    highpass: 150, lowpass: 4300,
    text: 'Single clean premium invalid-move cue for a puzzle game. One short muted rounded electronic wooden knock with a subtle downward two-tone pitch, clearly communicating that the piece cannot be placed while remaining friendly and unobtrusive. Studio-isolated and dry; complete within 0.30 seconds. No voice, buzzer, alarm, air, whoosh, hiss, static, crackle, noise bed, metallic ring, echo, reverb, distortion or bass boom.'
  }
];
const writeWav = (file, bytes) => cp.execFileSync('ffmpeg', ['-v', 'error', '-y', '-f', 'f32le', '-ar', '44100', '-ac', '1', '-i', 'pipe:0', '-c:a', 'pcm_s16le', file], { input: bytes });
async function generate(spec) {
  const receiptPath = `${base}/${spec.code.toLowerCase()}-${spec.slug}-generation.json`;
  if (fs.existsSync(receiptPath)) {
    const previousReceipt = JSON.parse(fs.readFileSync(receiptPath, 'utf8'));
    const existingCandidate = `docs/audio/${spec.code.toLowerCase()}-${spec.slug}-candidata.json`;
    if (previousReceipt.status === 'generated; candidate not installed' && fs.existsSync(existingCandidate)) {
      const result = JSON.parse(fs.readFileSync(existingCandidate, 'utf8'));
      return { id: spec.code, listen: result.listen, duration: result.durationSeconds, peakDb: result.peakDb, cost: previousReceipt.cost, reused: true };
    }
    throw new Error(`Previous ${spec.code} attempt exists; inspect before retrying.`);
  }
  const key = process.env.ELEVENLABS_API_KEY;
  if (!key) throw new Error('ElevenLabs API key is unavailable.');
  const body = { model_id: 'eleven_text_to_sound_v2', duration_seconds: Math.max(0.5, Number((spec.seconds + 0.15).toFixed(3))), prompt_influence: 0.9, loop: false, text: spec.text };
  const receipt = { startedAt: new Date().toISOString(), body, status: 'started; do not retry automatically' };
  fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2) + '\n');
  let response;
  try { response = await fetch('https://api.elevenlabs.io/v1/sound-generation?output_format=pcm_24000', { method: 'POST', headers: { 'xi-api-key': key, 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(90000) }); }
  catch (error) { receipt.status = 'transport outcome uncertain'; receipt.error = error.name; fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2) + '\n'); throw new Error(`${spec.code} generation outcome uncertain; inspect before retrying.`); }
  receipt.httpStatus = response.status; receipt.cost = response.headers.get('character-cost');
  if (!response.ok) { receipt.status = 'rejected'; receipt.error = (await response.text()).slice(0, 2000); fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2) + '\n'); throw new Error(`${spec.code}: ${receipt.error}`); }
  const source = Buffer.from(await response.arrayBuffer());
  const sourcePath = `${base}/${spec.code.toLowerCase()}-${spec.slug}-source.pcm`;
  const previewPath = `${base}/${spec.code.toLowerCase()}-${spec.slug}-preview.wav`;
  const listenPath = `${base}/${spec.code.toLowerCase()}-${spec.slug}-listen.wav`;
  fs.writeFileSync(sourcePath, source);
  receipt.status = 'generated; candidate not installed'; receipt.output = sourcePath; receipt.completedAt = new Date().toISOString();
  fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2) + '\n');
  const decoded = cp.execFileSync('ffmpeg', ['-v', 'error', '-f', 's16le', '-ar', '24000', '-ac', '1', '-i', sourcePath, '-af', `highpass=f=${spec.highpass},lowpass=f=${spec.lowpass}`, '-ar', '44100', '-ac', '1', '-f', 'f32le', 'pipe:1']);
  let samples = new Float32Array(decoded.buffer, decoded.byteOffset, decoded.length / 4);
  let peak = 0; for (const value of samples) peak = Math.max(peak, Math.abs(value));
  let first = 0, last = samples.length - 1;
  while (first < last && Math.abs(samples[first]) < peak * 0.018) first++;
  while (last > first && Math.abs(samples[last]) < peak * 0.012) last--;
  first = Math.max(0, first - 44); last = Math.min(samples.length - 1, last + 132);
  samples = samples.subarray(first, last + 1);
  const targetSamples = Math.round(spec.seconds * 44100);
  const speed = samples.length / targetSamples;
  if (speed > 1.03) {
    const compressed = cp.execFileSync('ffmpeg', ['-v', 'error', '-f', 'f32le', '-ar', '44100', '-ac', '1', '-i', 'pipe:0', '-af', `atempo=${speed}`, '-f', 'f32le', 'pipe:1'], { input: Buffer.from(samples.buffer, samples.byteOffset, samples.byteLength) });
    samples = new Float32Array(compressed.buffer, compressed.byteOffset, compressed.length / 4);
  }
  const output = new Float32Array(targetSamples); peak = 0;
  const taper = spec.code === 'I02' ? 662 : 265;
  for (let i = 0; i < targetSamples; i++) { const envelope = Math.min(1, i / 44, (targetSamples - 1 - i) / taper); output[i] = (samples[i] ?? 0) * envelope; peak = Math.max(peak, Math.abs(output[i])); }
  if (peak < 0.0001) throw new Error(`${spec.code} candidate is silent after editing.`);
  const gain = Math.pow(10, spec.gainDb / 20) / peak; for (let i = 0; i < output.length; i++) output[i] *= gain;
  const bytes = Buffer.from(output.buffer); writeWav(previewPath, bytes);
  const silence = Buffer.alloc(Math.round(44100 * 0.6) * 4); writeWav(listenPath, Buffer.concat([bytes, silence, bytes, silence, bytes]));
  const result = { id: spec.code, resource: spec.resource, model: body.model_id, source: sourcePath, sourceSha256: crypto.createHash('sha256').update(source).digest('hex'), preview: previewPath, listen: listenPath, durationSeconds: output.length / 44100, peakDb: spec.gainDb, onsetRemovedSeconds: first / 44100, timeCompression: speed > 1.03 ? speed : 1, processing: `PCM 24 kHz source; mono 44.1 kHz preview; ${spec.highpass} Hz high-pass; ${spec.lowpass / 1000} kHz low-pass; no spectral denoise; tight noise threshold and zero-ended envelope.`, status: 'Candidate only; current game resource unchanged' };
  fs.writeFileSync(`docs/audio/${spec.code.toLowerCase()}-${spec.slug}-candidata.json`, JSON.stringify(result, null, 2) + '\n');
  return { id: spec.code, listen: listenPath, duration: result.durationSeconds, peakDb: result.peakDb, cost: receipt.cost };
}
async function main() {
  const results = [];
  for (const spec of specs) results.push(await generate(spec));
  fs.writeFileSync('docs/audio/interfaz-pendientes-candidatos.json', JSON.stringify({ generatedAt: new Date().toISOString(), model: 'eleven_text_to_sound_v2', installed: false, results }, null, 2) + '\n');
  console.log(JSON.stringify(results));
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
