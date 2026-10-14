# Genera store_assets/icono_referencia_1024.png: la imagen de REFERENCIA que se sube a Photoshop/Firefly
# para el icono de Play (2026-09-30). Paleta CLÁSICA real del juego (BlockSkins.classicPalette) y un
# Teika con la cara de la casa. Uso: python tools/icono_referencia.py
from PIL import Image, ImageDraw, ImageFilter
import math, random
S = 2048                      # se dibuja a 2x y se reduce: bordes limpios
C = S / 5                     # 5 casillas de ancho, como los iconos de referencia
img = Image.new("RGB", (S, S), (31, 24, 70))
d = ImageDraw.Draw(img)
# Tablero: degradado radial suave + rejilla tenue
glow = Image.new("L", (S, S), 0)
gd = ImageDraw.Draw(glow)
for r in range(int(S * 0.75), 0, -8):
    gd.ellipse([S/2 - r, S*0.55 - r, S/2 + r, S*0.55 + r], fill=int(70 * (1 - r / (S * 0.75))))
img = Image.composite(Image.new("RGB", (S, S), (70, 50, 150)), img, glow)
d = ImageDraw.Draw(img)
for i in range(1, 5):
    d.line([(i*C, 0), (i*C, S)], fill=(48, 40, 104), width=6)
    d.line([(0, i*C), (S, i*C)], fill=(48, 40, 104), width=6)

PAL = {  # paleta CLÁSICA del juego: base, claro, oscuro
    "R": ((0xFF,0x5A,0x5A), (0xFF,0x9E,0x9E), (0xD6,0x32,0x2F)),
    "Y": ((0xFF,0xC8,0x3D), (0xFF,0xE0,0x8A), (0xE0,0xA0,0x10)),
    "G": ((0x7B,0xD1,0x4A), (0xAE,0xE8,0x85), (0x56,0xA0,0x30)),
    "B": ((0x4A,0x9B,0xF5), (0x97,0xC5,0xFA), (0x2A,0x6F,0xD0)),
    "P": ((0xB0,0x5A,0xE0), (0xD6,0xA0,0xF5), (0x7E,0x2E,0xB8)),
}
def mix(a, b, t): return tuple(int(a[i] + (b[i]-a[i]) * t) for i in range(3))

def tile(cx, cy, key, glow_on=False):
    base, light, dark = PAL[key]
    x0, y0 = cx * C, cy * C
    x1, y1 = x0 + C, y0 + C
    o = C * 0.035          # contorno oscuro
    b = C * 0.17           # ancho del bisel
    outline = mix(dark, (20, 10, 40), 0.55)
    d.rectangle([x0, y0, x1, y1], fill=outline)
    X0, Y0, X1, Y1 = x0+o, y0+o, x1-o, y1-o
    ix0, iy0, ix1, iy1 = X0+b, Y0+b, X1-b, Y1-b
    # Cuatro facetas con luz de arriba-izquierda: arriba la más clara, abajo la más oscura.
    d.polygon([(X0,Y0),(X1,Y0),(ix1,iy0),(ix0,iy0)], fill=mix(light, (255,255,255), 0.25))
    d.polygon([(X0,Y0),(ix0,iy0),(ix0,iy1),(X0,Y1)], fill=mix(base, light, 0.45))
    d.polygon([(X1,Y0),(X1,Y1),(ix1,iy1),(ix1,iy0)], fill=mix(base, dark, 0.55))
    d.polygon([(X0,Y1),(ix0,iy1),(ix1,iy1),(X1,Y1)], fill=mix(dark, (0,0,0), 0.12))
    # Cara: degradado vertical y un reflejo en la esquina superior izquierda.
    h = int(iy1 - iy0)
    for k in range(h):
        t = k / max(1, h - 1)
        d.line([(ix0, iy0 + k), (ix1, iy0 + k)], fill=mix(mix(base, light, 0.35), mix(base, dark, 0.25), t))
    g = C * 0.07
    d.polygon([(ix0+g, iy0+g), (ix0+g+C*0.22, iy0+g), (ix0+g, iy0+g+C*0.22)], fill=mix(light, (255,255,255), 0.55))

layers = []
# Brillo de la pieza protagonista (debajo de ella)
halo = Image.new("RGBA", (S, S), (0,0,0,0))
hd = ImageDraw.Draw(halo)
for (cx, cy) in [(2,1),(3,1),(4,1),(3,2)]:
    hd.rectangle([cx*C - C*0.15, cy*C - C*0.15, (cx+1)*C + C*0.15, (cy+1)*C + C*0.15], fill=(255, 220, 90, 150))
# Estela de luz sobre la pieza que cae
for (cx) in (2, 3, 4):
    for k in range(40):
        a = int(110 * (k / 40))
        hd.rectangle([cx*C + C*0.08, k*C/40, (cx+1)*C - C*0.08, (k+1)*C/40], fill=(255, 210, 120, a // 2))
halo = halo.filter(ImageFilter.GaussianBlur(40))
img.paste(halo, (0, 0), halo)
d = ImageDraw.Draw(img)

for (cx, cy, k) in [(0,0,"R"),(1,0,"R"),(0,1,"R"),
                    (0,3,"G"),(0,4,"G"),
                    (4,3,"B"),(4,4,"B"),
                    (4,2,"P")]:
    tile(cx, cy, k)
for (cx, cy) in [(2,1),(3,1),(4,1),(3,2)]:
    tile(cx, cy, "Y")

# Chispas de la estela (destellos de cuatro puntas)
random.seed(7)
def sparkle(x, y, r, col=(255, 240, 170)):
    d.polygon([(x, y-r), (x+r*0.22, y-r*0.22), (x+r, y), (x+r*0.22, y+r*0.22),
               (x, y+r), (x-r*0.22, y+r*0.22), (x-r, y), (x-r*0.22, y-r*0.22)], fill=col)
for _ in range(14):
    sparkle(random.uniform(2.1*C, 4.9*C), random.uniform(0.05*C, 0.9*C), random.uniform(10, 26))
sparkle(1.55*C, 1.6*C, 46); sparkle(4.55*C, 0.35*C, 40)

# ── EL TEIKA: esfera brillante con contorno marino y cara de la casa ──
tx, ty, tr = 2.05 * C, 3.92 * C, 0.98 * C
sh = Image.new("RGBA", (S, S), (0,0,0,0))
ImageDraw.Draw(sh).ellipse([tx-tr*0.9, ty+tr*0.75, tx+tr*0.9, ty+tr*1.05], fill=(10, 5, 30, 150))
sh = sh.filter(ImageFilter.GaussianBlur(30)); img.paste(sh, (0,0), sh); d = ImageDraw.Draw(img)
navy = (22, 20, 58)
d.ellipse([tx-tr-18, ty-tr-18, tx+tr+18, ty+tr+18], fill=(255, 255, 255))   # filo de pegatina
d.ellipse([tx-tr, ty-tr, tx+tr, ty+tr], fill=navy)
ir = tr - 22
ball = Image.new("RGB", (S, S)); bm = Image.new("L", (S, S), 0)
ImageDraw.Draw(bm).ellipse([tx-ir, ty-ir, tx+ir, ty+ir], fill=255)
bd = ImageDraw.Draw(ball)
lx, ly = tx - ir*0.35, ty - ir*0.4
for k in range(int(ir*1.7), 0, -4):           # degradado radial descentrado
    t = k / (ir*1.7)
    bd.ellipse([lx-k, ly-k, lx+k, ly+k], fill=mix((205, 140, 255), (112, 40, 190), t))
img.paste(ball, (0,0), bm); d = ImageDraw.Draw(img)
hl = Image.new("RGBA", (S, S), (0,0,0,0))
ImageDraw.Draw(hl).ellipse([tx-ir*0.62, ty-ir*0.86, tx+ir*0.05, ty-ir*0.42], fill=(255, 255, 255, 170))
hl = hl.filter(ImageFilter.GaussianBlur(8)); img.paste(hl, (0,0), hl); d = ImageDraw.Draw(img)
# Cara: ojos ovalados con brillo, cejas finas, boca abierta con lengua.
for sx in (-1, 1):
    ex, ey = tx + sx * ir*0.33, ty - ir*0.05
    d.ellipse([ex-ir*0.13, ey-ir*0.2, ex+ir*0.13, ey+ir*0.2], fill=(15, 12, 30))
    d.ellipse([ex-ir*0.06, ey-ir*0.15, ex+ir*0.03, ey-ir*0.03], fill=(255, 255, 255))
    d.line([(ex-ir*0.14, ey-ir*0.33), (ex+ir*0.12, ey-ir*0.37 if sx > 0 else ey-ir*0.29)], fill=navy, width=14)
mx, my = tx, ty + ir*0.36
d.chord([mx-ir*0.26, my-ir*0.2, mx+ir*0.26, my+ir*0.3], 0, 180, fill=(70, 10, 30))
d.chord([mx-ir*0.14, my+ir*0.02, mx+ir*0.14, my+ir*0.3], 0, 180, fill=(255, 90, 110))
for sx in (-1, 1):                              # mejillas
    cx2 = tx + sx*ir*0.6
    ch = Image.new("RGBA", (S, S), (0,0,0,0))
    ImageDraw.Draw(ch).ellipse([cx2-ir*0.12, my-ir*0.18, cx2+ir*0.12, my-ir*0.02], fill=(255, 120, 170, 140))
    ch = ch.filter(ImageFilter.GaussianBlur(6)); img.paste(ch, (0,0), ch)
d = ImageDraw.Draw(img)
sparkle(tx + tr*0.95, ty - tr*0.95, 50); sparkle(tx - tr*1.05, ty - tr*0.35, 30)

out = img.resize((1024, 1024), Image.LANCZOS)
out.save(r"%USERPROFILE%/AndroidStudioProjects/Teikafusion/store_assets/icono_referencia_1024.png")
print("ok")
