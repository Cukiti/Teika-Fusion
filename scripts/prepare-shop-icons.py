"""Convert approved raw artwork losslessly; retain originals outside Android resources."""
from pathlib import Path
from PIL import Image
import hashlib
import json
import shutil

root = Path(__file__).resolve().parents[1]
raw = root / 'app/src/main/res/raw'
backup = Path.home() / '.codex/artifacts/shop-icons-2026-09-14'
backup.mkdir(parents=True, exist_ok=True)
mapping = {
    'store_coins_250.png': 'store_teikoins_3000.webp',
    'store_coins_1200.png': 'store_teikoins_5000.webp',
    'store_coins_5000.png': 'store_teikoins_10000.webp',
    'store_starter_pack.png': 'store_starter_pack.webp',
    'store_remove_ads.png': 'store_remove_ads.webp',
    'settings.png': 'settings.webp',
}
duplicates = ['ic_crystal.png', 'ic_block_skin.png', 'ic_no_ads.png']
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
records = []
for name in [*mapping, *duplicates]:
    source = raw / name
    saved = backup / name
    if not source.exists():
        raise RuntimeError(f'Missing source: {source}; inspect before rerun')
    if saved.exists() and digest(saved) != digest(source):
        raise RuntimeError(f'Backup conflict: {saved}')
    shutil.copy2(source, saved)
    target = raw / mapping.get(name, name.replace('.png', '.webp'))
    if name in mapping:
        if target.exists():
            raise RuntimeError(f'Target already exists: {target}')
        im = Image.open(source).convert('RGBA')
        im.save(target, format='WEBP', lossless=True, exact=True, method=6)
        decoded = Image.open(target).convert('RGBA')
        assert decoded.size == im.size and decoded.tobytes() == im.tobytes()
    else:
        # Existing approved WebP stays untouched; PNG is an extra copy with the same Android ID.
        Image.open(target).verify()
    records.append(dict(source=name, resource=target.name, backup=str(saved),
                        originalBytes=source.stat().st_size, webpBytes=target.stat().st_size,
                        sourceSha256=digest(source), webpSha256=digest(target)))

for record in records:
    source = raw / record['source']
    assert source.parent.resolve() == raw.resolve()
    assert digest(source) == record['sourceSha256'] == digest(Path(record['backup']))
    source.unlink()
# The product illustration replaces the generic no-ads icon's last caller.
retired = raw / 'ic_no_ads.webp'
if retired.exists():
    shutil.copy2(retired, backup / retired.name)
    assert digest(retired) == digest(backup / retired.name)
    retired.unlink()
report = root / 'docs/shop-icons-2026-09-14.json' 
report.write_text(json.dumps(records, indent=2) + '\n', encoding='utf-8')
print(f'{len(mapping)} lossless WebP conversions; {len(duplicates)} duplicate PNGs backed up and removed')
