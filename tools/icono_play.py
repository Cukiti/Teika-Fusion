# Genera el icono de Play (store_assets/icon_1024.png) — 2026-09-30.
#
# Teikas FIELES al juego (calcados de una captura de partida): esferas MATE con degradado radial
# suave, SIN contorno ni reflejo de gominola; cara mínima — cejas negras gruesas, ojos óvalos negros
# macizos sin brillo y boca de una línea o una «o» rosa. Bloques biselados grandes con la paleta
# CLÁSICA del juego (BlockSkins.classicPalette), en el lenguaje de los block puzzle más populares.
#
# Uso: python tools/icono_play.py [salida.png]
import math
import random
import sys
from PIL import Image, ImageDraw, ImageFilter

S = 2048                      # se dibuja a 2x y se reduce: bordes limpios
C = S / 5                     # 5 casillas de ancho
img = Image.new("RGB", (S, S), (28, 26, 62))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def paste_blur(draw_fn, radius):
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    layer = layer.filter(ImageFilter.GaussianBlur(radius))
    img.paste(layer, (0, 0), layer)


# ── Tablero: índigo con luz en el centro y la rejilla del juego ─────────────────────────────────
paste_blur(lambda d: d.ellipse([S * 0.05, S * 0.1, S * 0.95, S * 0.95], fill=(78, 68, 170, 200)), 160)
d = ImageDraw.Draw(img)
for i in range(1, 5):
    d.line([(i * C, 0), (i * C, S)], fill=(62, 62, 128), width=5)
    d.line([(0, i * C), (S, i * C)], fill=(62, 62, 128), width=5)

# ── Bloques biselados ────────────────────────────────────────────────────────────────────────────
PAL = {
    "R": ((0xFF, 0x5A, 0x5A), (0xFF, 0x9E, 0x9E), (0xD6, 0x32, 0x2F)),
    "Y": ((0xFF, 0xC8, 0x3D), (0xFF, 0xE0, 0x8A), (0xE0, 0xA0, 0x10)),
    "G": ((0x7B, 0xD1, 0x4A), (0xAE, 0xE8, 0x85), (0x56, 0xA0, 0x30)),
    "B": ((0x4A, 0x9B, 0xF5), (0x97, 0xC5, 0xFA), (0x2A, 0x6F, 0xD0)),
    "P": ((0xB0, 0x5A, 0xE0), (0xD6, 0xA0, 0xF5), (0x7E, 0x2E, 0xB8)),
}


def tile(cx, cy, key):
    base, light, dark = PAL[key]
    x0, y0, x1, y1 = cx * C, cy * C, (cx + 1) * C, (cy + 1) * C
    o, b = C * 0.035, C * 0.17
    d.rectangle([x0, y0, x1, y1], fill=mix(dark, (20, 10, 40), 0.55))
    X0, Y0, X1, Y1 = x0 + o, y0 + o, x1 - o, y1 - o
    ix0, iy0, ix1, iy1 = X0 + b, Y0 + b, X1 - b, Y1 - b
    d.polygon([(X0, Y0), (X1, Y0), (ix1, iy0), (ix0, iy0)], fill=mix(light, (255, 255, 255), 0.25))
    d.polygon([(X0, Y0), (ix0, iy0), (ix0, iy1), (X0, Y1)], fill=mix(base, light, 0.45))
    d.polygon([(X1, Y0), (X1, Y1), (ix1, iy1), (ix1, iy0)], fill=mix(base, dark, 0.55))
    d.polygon([(X0, Y1), (ix0, iy1), (ix1, iy1), (X1, Y1)], fill=mix(dark, (0, 0, 0), 0.12))
    h = int(iy1 - iy0)
    for k in range(h):
        t = k / max(1, h - 1)
        d.line([(ix0, iy0 + k), (ix1, iy0 + k)], fill=mix(mix(base, light, 0.35), mix(base, dark, 0.25), t))
    g = C * 0.07
    d.polygon([(ix0 + g, iy0 + g), (ix0 + g + C * 0.22, iy0 + g), (ix0 + g, iy0 + g + C * 0.22)],
              fill=mix(light, (255, 255, 255), 0.55))


for (cx, cy, k) in [(0, 0, "P"), (1, 0, "P"), (0, 1, "P"),
                    (0, 4, "R"), (1, 4, "R"), (0, 3, "R"),
                    (4, 4, "B"), (3, 4, "B")]:
    tile(cx, cy, k)


# ── Teikas ───────────────────────────────────────────────────────────────────────────────────────
INK = (17, 17, 24)
# Grosor de cejas y bocas y escala de ojos, en «unidades de cara». Subidos el 2026-10-01 (eran 7 y 1):
# en el lanzador (48 px) las caras se borraban.
STROKE = 10
EYE = 1.12
TEIKA = {   # centro claro, borde oscuro (mate, como en partida)
    "verde": ((0xA8, 0xE6, 0x86), (0x52, 0xA8, 0x3E)),
    "amarillo": ((0xFF, 0xDF, 0x6E), (0xD6, 0x9E, 0x1E)),
    "azul": ((0x9C, 0xD2, 0xF5), (0x4C, 0x8F, 0xC8)),
    "morado": ((0xDB, 0xC2, 0xF5), (0x9A, 0x72, 0xCC)),
}


def teika(cx, cy, r, color, face):
    global d
    inner, edge = TEIKA[color]
    # Sombra apoyada en el tablero.
    paste_blur(lambda dd: dd.ellipse([cx - r * 0.85, cy + r * 0.72, cx + r * 0.85, cy + r * 1.06],
                                     fill=(8, 6, 26, 150)), r * 0.12)
    body = Image.new("RGB", (S, S))
    bd = ImageDraw.Draw(body)
    lx, ly = cx - r * 0.28, cy - r * 0.32              # foco de luz arriba a la izquierda
    reach = r * 1.45
    for k in range(int(reach), 0, -3):
        t = (k / reach) ** 1.3
        bd.ellipse([lx - k, ly - k, lx + k, ly + k], fill=mix(inner, edge, t))
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    m = m.filter(ImageFilter.GaussianBlur(1.5))
    img.paste(body, (0, 0), m)
    d = ImageDraw.Draw(img)
    # Borde algo más oscuro del mismo color (no es un contorno negro).
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=mix(edge, (0, 0, 0), 0.18), width=max(4, int(r * 0.025)))
    u = r / 100.0                                       # unidad de cara

    def brow(x0, y0, x1, y1):
        d.line([(cx + x0 * u, cy + y0 * u), (cx + x1 * u, cy + y1 * u)], fill=INK, width=int(STROKE * u))
        for (x, y) in ((x0, y0), (x1, y1)):              # puntas redondeadas
            d.ellipse([cx + x * u - 3.5 * u, cy + y * u - STROKE / 2 * u, cx + x * u + STROKE / 2 * u, cy + y * u + STROKE / 2 * u], fill=INK)

    def eye(x, y, w=11, h=15):
        w, h = w * EYE, h * EYE
        d.ellipse([cx + (x - w) * u, cy + (y - h) * u, cx + (x + w) * u, cy + (y + h) * u], fill=INK)

    if face == "sorpresa":          # el verde grande: cejas rectas, ojos grandes, «o» rosa
        brow(-50, -40, -18, -42); brow(18, -42, 50, -40)
        eye(-33, -6, 12, 18); eye(33, -6, 12, 18)
        d.ellipse([cx - 11 * u, cy + 30 * u, cx + 11 * u, cy + 50 * u], fill=INK)
        d.ellipse([cx - 8 * u, cy + 33 * u, cx + 8 * u, cy + 47 * u], fill=(0xF2, 0x6B, 0x80))
    elif face == "picaro":          # el amarillo: una ceja más alta, sonrisa de lado
        brow(-50, -40, -16, -44); brow(16, -46, 50, -42)
        eye(-30, -4); eye(30, -4)
        d.arc([cx - 28 * u, cy + 6 * u, cx + 28 * u, cy + 44 * u], 20, 160, fill=INK, width=int(STROKE * u))
    elif face == "preocupado":      # el azul pequeño: cejas caídas hacia fuera, boquita triste
        brow(-46, -30, -16, -40); brow(16, -40, 46, -30)
        eye(-28, -2, 11, 14); eye(28, -2, 11, 14)
        d.arc([cx - 18 * u, cy + 30 * u, cx + 18 * u, cy + 56 * u], 205, 335, fill=INK, width=int(STROKE * u))
    elif face == "esceptico":       # el morado: ceño recto, boca plana
        brow(-48, -34, -16, -30); brow(16, -36, 48, -40)
        eye(-30, -2, 11, 13); eye(30, -2, 11, 13)
        d.line([(cx - 18 * u, cy + 34 * u), (cx + 18 * u, cy + 34 * u)], fill=INK, width=int(7 * u))


# Física de verdad: los Teikas se TOCAN (tangentes) y nunca pisan una casilla con bloque. El verde es
# el protagonista; el amarillo descansa contra él arriba a la derecha y el azul pequeño, sobre el
# bloque azul de abajo, apretado contra el verde.
GX, GY, GR = 2.4, 2.6, 1.25
YR = 0.9
ya = math.radians(-50)
YX, YY = GX + (GR + YR) * math.cos(ya), GY + (GR + YR) * math.sin(ya)
BR = 0.62                                               # 0.48 → 0.62: a 48 px era un punto
BY = 4 - BR                                             # apoyado en el bloque (3,4)
BX = GX + math.sqrt((GR + BR) ** 2 - (BY - GY) ** 2)
# Brillo de FUSIÓN donde se tocan el verde y el amarillo: cuenta el verbo del juego sin texto.
FX, FY = GX + GR * math.cos(ya), GY + GR * math.sin(ya)
paste_blur(lambda dd: dd.ellipse([(FX - 0.55) * C, (FY - 0.55) * C, (FX + 0.55) * C, (FY + 0.55) * C],
                                 fill=(255, 236, 160, 150)), C * 0.22)
teika(YX * C, YY * C, YR * C, "amarillo", "picaro")
teika(GX * C, GY * C, GR * C, "verde", "sorpresa")
teika(BX * C, BY * C, BR * C, "azul", "preocupado")


# Destellos de cuatro puntas (la firma del arte de la casa), pocos y pequeños.
def sparkle(x, y, r, col=(255, 240, 170)):
    d.polygon([(x, y - r), (x + r * 0.22, y - r * 0.22), (x + r, y), (x + r * 0.22, y + r * 0.22),
               (x, y + r), (x - r * 0.22, y + r * 0.22), (x - r, y), (x - r * 0.22, y - r * 0.22)], fill=col)


sparkle(FX * C, FY * C, 70, (255, 250, 220))   # el destello de la fusión
sparkle(1.45 * C, 1.05 * C, 34)

out = sys.argv[1] if len(sys.argv) > 1 else "store_assets/icon_1024.png"
img.resize((1024, 1024), Image.LANCZOS).save(out)
print(out)
