"""Vista de revisión de la cadena del mapa: los dos últimos biomas apilados y un zoom de la unión.

Uso: python tools/mapa_vista.py 04_garden 05_storm <carpeta_salida>
"""
import sys
from PIL import Image

prev, new, out = sys.argv[1], sys.argv[2], sys.argv[3]
a = Image.open(f"art/mapa/{prev}.png").convert("RGB")
b = Image.open(f"art/mapa/{new}.png").convert("RGB")
c = Image.new("RGB", (a.width, a.height + b.height))
c.paste(b, (0, 0))
c.paste(a, (0, b.height))
c.resize((c.width // 8, c.height // 8)).save(f"{out}/bioma.png")
c.crop((0, b.height - 1500, a.width, b.height + 700)).resize((420, 902)).save(f"{out}/union.png")
