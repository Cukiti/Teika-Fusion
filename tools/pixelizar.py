"""
Convierte una ilustración «estilo pixel art» (generada por IA) en pixel art REAL para el mapa de
mundos: la reduce a una rejilla de [ancho] píxeles, la deja en una paleta cerrada de [colores] (48 por defecto: con 32 se apagan los acentos cálidos)
tonos sin tramado y guarda el PNG indexado a tamaño nativo (el juego lo escala sin suavizado).

Uso:
    python tools/pixelizar.py art/crystalcavern.jpeg app/src/main/res/drawable-nodpi/mapa_crystal.png
    python tools/pixelizar.py entrada.png salida.png --ancho 180 --colores 48 --vista vista.png

--vista guarda además una copia ampliada ×4 para revisarla a ojo.
"""
import argparse
from PIL import Image, ImageFilter


def pixelizar(src: str, dst: str, ancho: int, colores: int, vista: str | None) -> None:
    im = Image.open(src).convert("RGB")
    alto = round(im.height * ancho / im.width)
    # Un leve filtro de mediana antes de reducir quita el grano de la IA sin comerse los bordes.
    im = im.filter(ImageFilter.MedianFilter(3))
    small = im.resize((ancho, alto), Image.Resampling.BOX)
    indexed = small.quantize(colors=colores, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE, kmeans=4)
    indexed.save(dst, optimize=True)
    if vista:
        indexed.convert("RGB").resize((ancho * 4, alto * 4), Image.Resampling.NEAREST).save(vista)
    print(f"{dst}: {ancho}x{alto}, {len(indexed.getcolors())} colores")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("src"); p.add_argument("dst")
    p.add_argument("--ancho", type=int, default=180)
    p.add_argument("--colores", type=int, default=48)
    p.add_argument("--vista")
    a = p.parse_args()
    pixelizar(a.src, a.dst, a.ancho, a.colores, a.vista)
