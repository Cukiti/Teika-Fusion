const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const cp = require('child_process');

const root = process.cwd();
const evidenceDir = path.join(root, 'docs/audio/evidencia-licencia-elevenlabs-2026-09-15');
const artifactRoot = '%USERPROFILE%/.codex/artifacts/elevenlabs';
const archivePath = '%USERPROFILE%/.codex/artifacts/elevenlabs/evidencia-licencia-teika-fusion-2026-09-15.zip';
const registryPath = path.join(root, 'docs/audio/renovacion-resultados.json');
const sha256 = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const iso = new Date().toISOString();

function walk(dir, output = []) {
  if (!fs.existsSync(dir)) return output;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, output);
    else output.push(full);
  }
  return output;
}

function sanitize(value) {
  if (Array.isArray(value)) return value.map(sanitize);
  if (!value || typeof value !== 'object') return value;
  const result = {};
  for (const [key, item] of Object.entries(value)) {
    if (/api.?key|authorization|password|secret|token|email/i.test(key)) continue;
    result[key] = sanitize(item);
  }
  return result;
}

async function main() {
  if (!process.env.ELEVENLABS_API_KEY) throw Error('ELEVENLABS_API_KEY unavailable');
  const response = await fetch('https://api.elevenlabs.io/v1/user/subscription', {
    headers: { 'xi-api-key': process.env.ELEVENLABS_API_KEY },
  });
  if (!response.ok) throw Error(`Subscription HTTP ${response.status}`);
  const subscription = await response.json();
  const subscriptionSnapshot = {
    capturedAt: iso,
    provider: 'ElevenLabs',
    endpoint: '/v1/user/subscription',
    tier: subscription.tier,
    status: subscription.status,
    billingPeriod: subscription.billing_period,
    currency: subscription.currency,
    quotaUsed: subscription.character_count,
    quotaLimit: subscription.character_limit,
    nextQuotaResetUnix: subscription.next_character_count_reset_unix,
    hasOpenInvoices: subscription.has_open_invoices,
    apiCredentialIncluded: false,
  };

  const registry = JSON.parse(fs.readFileSync(registryPath));
  const assets = registry.records.map(record => {
    const target = path.resolve(root, record.target);
    if (!fs.existsSync(target)) throw Error(`Missing installed asset: ${record.target}`);
    const actualHash = sha256(target);
    if (record.finalSha256 && actualHash !== record.finalSha256) {
      throw Error(`Hash mismatch: ${record.id}`);
    }
    const sourceExists = Boolean(record.source && fs.existsSync(record.source));
    return {
      id: record.id,
      installedPath: record.target,
      installedBytes: fs.statSync(target).size,
      installedSha256: actualHash,
      sourcePath: record.source || null,
      sourceExists,
      sourceSha256: record.sourceSha256 || (sourceExists ? sha256(record.source) : null),
      model: record.sourceModel || null,
      generationTimestamp: record.generationTimestamp || null,
      prompt: record.prompt || null,
      processing: record.processing || null,
      verification: record.verified || null,
    };
  });

  const receipts = [];
  for (const file of walk(artifactRoot).filter(file => file.endsWith('.json'))) {
    try {
      const parsed = JSON.parse(fs.readFileSync(file));
      if (!parsed.songId && !parsed.model && !parsed.model_id && !parsed.startedAt && !parsed.completedAt) continue;
      receipts.push({ receiptPath: file, receiptSha256: sha256(file), ...sanitize(parsed) });
    } catch (_) {}
  }

  const legalReferences = {
    capturedAt: iso,
    scope: 'Official ElevenLabs pages consulted; summaries are not substitutes for the provider terms.',
    sources: [
      {
        url: 'https://help.elevenlabs.io/hc/en-us/articles/13313564601361-Can-I-publish-the-content-I-generate-on-the-platform',
        summary: 'ElevenLabs states that paid plans include a commercial license, subject to its terms, rights requirements and Beta-service exclusion; content generated during a paid subscription may be used commercially and indefinitely.',
      },
      {
        url: 'https://help.elevenlabs.io/hc/en-us/articles/15993008593297-What-happens-to-my-content-after-my-subscription-ends',
        summary: 'ElevenLabs states that content generated during a subscription retains its commercial license after the subscription ends.',
      },
      {
        url: 'https://elevenlabs.io/docs/overview/administration/billing',
        summary: 'ElevenLabs billing documentation states that content generated on paid plans receives commercial rights.',
      },
      {
        url: 'https://elevenlabs.io/terms-of-use-eu',
        summary: 'Provider terms governing paid commercial use, subscriptions and permitted use remain controlling and may change.',
      },
    ],
  };

  fs.mkdirSync(evidenceDir, { recursive: true });
  fs.writeFileSync(path.join(evidenceDir, 'suscripcion-sanitizada.json'), `${JSON.stringify(subscriptionSnapshot, null, 2)}\n`);
  fs.writeFileSync(path.join(evidenceDir, 'manifiesto-activos.json'), `${JSON.stringify({ createdAt: iso, count: assets.length, assets }, null, 2)}\n`);
  fs.writeFileSync(path.join(evidenceDir, 'recibos-generacion-sanitizados.json'), `${JSON.stringify({ createdAt: iso, count: receipts.length, receipts }, null, 2)}\n`);
  fs.writeFileSync(path.join(evidenceDir, 'referencias-legales.json'), `${JSON.stringify(legalReferences, null, 2)}\n`);

  const readme = `# Evidencia de generación y licencia — Teika Fusion\n\n` +
    `Creada: ${iso}\n\n` +
    `Este expediente conserva evidencia técnica de que los activos instalados fueron generados y registrados mientras la API reportaba una suscripción ElevenLabs **${subscriptionSnapshot.tier}** con estado **${subscriptionSnapshot.status}**. No contiene la clave API, contraseñas, tokens ni correo de la cuenta.\n\n` +
    `## Contenido\n\n` +
    `- \`suscripcion-sanitizada.json\`: respuesta limitada y sin secretos del estado de suscripción.\n` +
    `- \`manifiesto-activos.json\`: ${assets.length} activos instalados, modelos, fechas disponibles, rutas y hashes SHA-256.\n` +
    `- \`recibos-generacion-sanitizados.json\`: ${receipts.length} recibos técnicos encontrados, incluidos identificadores de canción y marcas de tiempo cuando existen.\n` +
    `- \`referencias-legales.json\`: enlaces oficiales y resumen del fundamento comercial consultado.\n` +
    `- \`SHA256SUMS.txt\`: integridad de los documentos del expediente y de cada activo instalado.\n\n` +
    `## Alcance probatorio\n\n` +
    `Los hashes permiten demostrar que los archivos distribuidos son los mismos que constan en el registro. El estado de API demuestra el plan activo en el momento de captura. Para una defensa contractual completa deben conservarse además las facturas o recibos de pago emitidos por ElevenLabs y una copia descargada de los términos aplicables en la fecha de generación.\n`;
  fs.writeFileSync(path.join(evidenceDir, 'README.md'), readme);

  const evidenceFiles = fs.readdirSync(evidenceDir).map(name => path.join(evidenceDir, name));
  const sums = [
    ...evidenceFiles.map(file => `${sha256(file)}  evidence/${path.basename(file)}`),
    ...assets.map(asset => `${asset.installedSha256}  ${asset.installedPath.replaceAll('\\', '/')}`),
  ].join('\n') + '\n';
  fs.writeFileSync(path.join(evidenceDir, 'SHA256SUMS.txt'), sums);

  if (fs.existsSync(archivePath)) throw Error(`Archive already exists: ${archivePath}`);
  cp.execFileSync('tar.exe', ['-a', '-c', '-f', archivePath, '-C', path.dirname(evidenceDir), path.basename(evidenceDir)]);
  console.log(JSON.stringify({ evidenceDir, archivePath, assets: assets.length, receipts: receipts.length, subscription: subscriptionSnapshot }));
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });
