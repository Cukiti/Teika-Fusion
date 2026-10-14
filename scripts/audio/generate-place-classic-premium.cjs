// Generate one user-authorized J01 candidate with ElevenLabs Sound Effects v2.
// It does not alter the game resource.
const fs = require('fs');
const cp = require('child_process');
const crypto = require('crypto');
const base = '%USERPROFILE%/.codex/artifacts/elevenlabs/teika-sfx-review-2026-09-13';
fs.mkdirSync(base, { recursive: true });

const receiptPath = base + '/j01-place-classic-generation.json';
const sourcePath = base + '/j01-place-classic-source.pcm';
const previewPath = base + '/j01-place-classic-preview.wav';
const listenPath = base + '/j01-place-classic-listen.wav';

async function main() {
  if (fs.existsSync(receiptPath)) throw new Error('A previous J01 attempt exists; inspect it before retrying.');
  const key = process.env.ELEVENLABS_API_KEY;
  if (!key) throw new Error('ElevenLabs API key is unavailable.');
  const body = {
    model_id: 'eleven_text_to_sound_v2',
    duration_seconds: 0.5,
    prompt_influence: 0.78,
    loop: false,
    text: 'One premium game-piece placement impact for a polished puzzle game. A compact, rounded solid resin-and-wood tok with a precise tactile attack, subtle low-mid body, and one tiny clean electronic harmonic glint. Confident, satisfying, elegant, expensive studio sound. Entire useful sound within 0.12 seconds, then silence. No air, whoosh, hiss, noise layer, scrape, rattle, bounce, echo, reverb tail, voice, music, sub-bass boom or harsh click.'
  };
  const receipt = { startedAt: new Date().toISOString(), body, status: 'started; do not retry automatically' };
  fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2) + '\n');
  let response;
  try {
    response = await fetch('https://api.elevenlabs.io/v1/sound-generation?output_format=pcm_24000', {
      method: 'POST',
      headers: { 'xi-api-key': key, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(90000)
    });
  } catch (error) {
    receipt.status = 'transport outcome uncertain';
    receipt.error = error.name;
    fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2) + '\n');
    throw new Error('Generation outcome is uncertain; inspect the receipt before retrying.');
  }
  receipt.httpStatus = response.status;
  receipt.cost = response.headers.get('character-cost');
  if (!response.ok) {
    receipt.status = 'rejected';
    receipt.error = (await response.text()).slice(0, 2000);
    fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2) + '\n');
    throw new Error(receipt.error);
  }
  const source = Buffer.from(await response.arrayBuffer());
  fs.writeFileSync(sourcePath, source);
  receipt.status = 'generated; candidate not installed';
  receipt.output = sourcePath;
  receipt.completedAt = new Date().toISOString();
  fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2) + '\n');

  // ElevenLabs PCM output is raw signed 16-bit mono at the requested 24 kHz.
  const decoded = cp.execFileSync('ffmpeg', [
    '-v', 'error', '-f', 's16le', '-ar', '24000', '-ac', '1', '-i', sourcePath,
    '-af', 'highpass=f=75,lowpass=f=8500', '-ar', '44100', '-ac', '1', '-f', 'f32le', 'pipe:1'
  ]);
  const samples = new Float32Array(decoded.buffer, decoded.byteOffset, decoded.length / 4);
  let peak = 0;
  for (const value of samples) peak = Math.max(peak, Math.abs(value));
  let onset = 0;
  while (onset < samples.length && Math.abs(samples[onset]) < peak * 0.012) onset++;
  onset = Math.max(0, onset - 44);
  const targetSamples = Math.round(0.125 * 44100);
  const output = new Float32Array(targetSamples);
  peak = 0;
  for (let i = 0; i < targetSamples; i++) {
    const envelope = Math.min(1, i / 44, (targetSamples - 1 - i) / 265);
    output[i] = (samples[onset + i] ?? 0) * envelope;
    peak = Math.max(peak, Math.abs(output[i]));
  }
  if (peak < 0.0001) throw new Error('The J01 candidate is silent after editing.');
  const gain = Math.pow(10, -9 / 20) / peak;
  for (let i = 0; i < output.length; i++) output[i] *= gain;

  const writeWav = (file, bytes) => cp.execFileSync('ffmpeg', [
    '-v', 'error', '-y', '-f', 'f32le', '-ar', '44100', '-ac', '1', '-i', 'pipe:0',
    '-c:a', 'pcm_s16le', file
  ], { input: bytes });
  const bytes = Buffer.from(output.buffer);
  writeWav(previewPath, bytes);
  const silence = Buffer.alloc(Math.round(44100 * 0.55) * 4);
  writeWav(listenPath, Buffer.concat([bytes, silence, bytes, silence, bytes]));

  const result = {
    id: 'J01', resource: 'snd_place_classic', model: body.model_id,
    source: sourcePath, sourceSha256: crypto.createHash('sha256').update(source).digest('hex'),
    preview: previewPath, listen: listenPath, durationSeconds: output.length / 44100,
    peakDb: -9, onsetRemovedSeconds: onset / 44100,
    processing: 'PCM 24 kHz source; mono 44.1 kHz preview; 75 Hz high-pass; 8.5 kHz low-pass; no spectral denoise; 1 ms attack and 6 ms end taper.',
    status: 'Candidate only; current game resource unchanged'
  };
  fs.writeFileSync('docs/audio/j01-colocacion-clasica-candidata.json', JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify({ model: result.model, duration: result.durationSeconds, peakDb: result.peakDb, listen: result.listen, cost: receipt.cost }));
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
