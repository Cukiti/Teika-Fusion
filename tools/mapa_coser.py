"""
Cose una frontera de la cadena de biomas del mapa: pega la semilla repintada (seed_<nuevo>.png)
sobre los 700 px de arriba de la franja anterior, y guarda una vista de la unión para revisarla.

Uso:
    python tools/mapa_coser.py 02_sleep 03_fear [--vista ruta.png]

Ver las reglas en tools/ps_relleno_generativo.jsx (paso 4 de la cadena).
"""
import argparse
from PIL import Image

MAPA = "art/mapa/"


def coser(prev: str, new: str, vista: str | None) -> None:
    a = Image.open(f"{MAPA}{prev}.png").convert("RGB")
    seed = Image.open(f"{MAPA}seed_{new}.png").convert("RGB")
    b = Image.open(f"{MAPA}{new}.png").convert("RGB")
    a.paste(seed, (0, 0))
    a.save(f"{MAPA}{prev}.png")
    if vista:
        c = Image.new("RGB", (a.width, b.height + a.height))
        c.paste(b, (0, 0))
        c.paste(a, (0, b.height))
        c.crop((0, b.height - 900, a.width, b.height + 900)).resize((512, 900)).save(vista)
    print(f"{prev} <- seed_{new}; {new}: {b.size}")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("prev"); p.add_argument("new")
    p.add_argument("--vista")
    a = p.parse_args()
    coser(a.prev, a.new, a.vista)
