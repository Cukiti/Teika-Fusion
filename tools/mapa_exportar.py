"""
Convierte las franjas del mapa (art/mapa/NN_bioma.png) a WebP para el juego:
app/src/main/res/drawable-nodpi/mapa_NN_bioma.webp. Se cargan una a una al verse (WorldMap.kt).

Uso: python tools/mapa_exportar.py [--calidad 80] [--ancho 1024]
"""
import argparse, glob, os
from PIL import Image

p = argparse.ArgumentParser()
p.add_argument("--calidad", type=int, default=80)
p.add_argument("--ancho", type=int, default=1024)
a = p.parse_args()
total = 0
for src in sorted(glob.glob("art/mapa/[0-9][0-9]_*.png")):
    name = os.path.splitext(os.path.basename(src))[0]
    im = Image.open(src).convert("RGB")
    if im.width != a.ancho:
        im = im.resize((a.ancho, round(im.height * a.ancho / im.width)), Image.Resampling.LANCZOS)
    dst = f"app/src/main/res/drawable-nodpi/mapa_{name}.webp"
    im.save(dst, "WEBP", quality=a.calidad, method=6)
    kb = os.path.getsize(dst) // 1024
    total += kb
    print(f"{dst}: {im.size} {kb} KB")
print(f"TOTAL {total} KB")
