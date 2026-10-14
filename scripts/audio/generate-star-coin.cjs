// One user-authorized Sound Effects v2 generation. Does not alter game assets.
const fs = require('fs');
const base = '%USERPROFILE%/.codex/artifacts/elevenlabs/teika-sfx-approved-2026-09-13';

async function main() {
  const receipt = base + '/star-coin-generation.json';
  if (fs.existsSync(receipt)) throw new Error('Prior star-coin attempt exists; inspect it before retrying.');
  const key = process.env.ELEVENLABS_API_KEY;
  if (!key) throw new Error('ElevenLabs API key is unavailable.');

  const body = {
    model_id: 'eleven_text_to_sound_v2',
    duration_seconds: 0.5,
    prompt_influence: 0.75,
    loop: false,
    text: 'One bright collectible coin pickup for a polished Japanese-style puzzle game. A tiny clean gold coin ping with a crisp metallic attack, immediately followed by one higher sparkling coin chime. Cheerful, shiny, rewarding and clearly recognizable as collecting a coin. Complete within 0.36 seconds, then silence. No cash register, coin drop, bouncing, multiple loose coins, air, whoosh, hiss, voice, percussion, reverb wash or background music.'
  };

  const record = { startedAt: new Date().toISOString(), body, status: 'started; do not retry automatically' };
  fs.writeFileSync(receipt, JSON.stringify(record, null, 2) + '\n');
  let response;
  try {
    response = await fetch('https://api.elevenlabs.io/v1/sound-generation?output_format=mp3_44100_128', {
      method: 'POST',
      headers: { 'xi-api-key': key, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(90000)
    });
  } catch (error) {
    record.status = 'transport outcome uncertain';
    record.error = error.name;
    fs.writeFileSync(receipt, JSON.stringify(record, null, 2) + '\n');
    throw new Error('Generation outcome is uncertain; inspect the receipt before retrying.');
  }
  record.httpStatus = response.status;
  record.cost = response.headers.get('character-cost');
  if (!response.ok) {
    record.status = 'rejected';
    record.error = (await response.text()).slice(0, 2000);
    fs.writeFileSync(receipt, JSON.stringify(record, null, 2) + '\n');
    throw new Error(record.error);
  }
  const bytes = Buffer.from(await response.arrayBuffer());
  record.output = base + '/star-coin-source.mp3';
  fs.writeFileSync(record.output, bytes);
  record.status = 'generated; not installed';
  record.completedAt = new Date().toISOString();
  fs.writeFileSync(receipt, JSON.stringify(record, null, 2) + '\n');
  console.log(JSON.stringify({ model: body.model_id, output: record.output, cost: record.cost }));
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
