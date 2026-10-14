# Monta las capturas de la ficha de Play (2026-10-01).
#
# Entrada: store_assets/screenshots/originales_2026-10-01/*.png — capturas reales del Galaxy A15
# (1080×2340, bloque Cerámica). Salida: store_assets/screenshots/play_N.png a 1080×1920 (9:16, lo que
# pide Play), con titular arriba y la pantalla dentro de un marco de móvil.
#
# El banner de AdMob (anuncio de prueba en debug) se RECORTA: va pegado al borde inferior, debajo de
# los botones, y en la ficha no se ve profesional (pedido del usuario). Por eso la pantalla se corta
# en CROP_BOTTOM.
#
# Uso: python tools/ficha_capturas.py
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "store_assets/screenshots/originales_2026-10-01"
OUT = ROOT / "store_assets/screenshots"
FONTS = Path("C:/Windows/Fonts")
W, H = 1080, 1920
CROP_BOTTOM = 2180          # el banner empieza en y≈2200 en el A15

# (archivo, titular, subtítulo, color del titular)
SHOTS = [
    ("1_rompe_lineas.png", "ROMPE LÍNEAS", "Completa filas y columnas y gana estrellas", (255, 214, 92)),
    ("2_nacen_teikas.png", "NACEN LOS TEIKAS", "Ruedan, chocan y se fusionan hasta el MEGA", (140, 220, 255)),
    ("3_mapa_volcan.png", "293 NIVELES", "14 mundos, cada uno con su amenaza", (255, 150, 80)),
    ("4_companeros.png", "ELIGE 3 COMPAÑEROS", "14 poderes que cambian el tablero", (200, 160, 255)),
    ("5_piensa_ficha.png", "PIENSA CADA FICHA", "Sin cronómetro: ves lo que se rompe antes de soltar", (130, 230, 140)),
    ("6_bloques.png", "12 ASPECTOS DE BLOQUE", "Del Básico al Dorado", (255, 120, 150)),
]

TOP, BOTTOM = (28, 26, 62), (47, 84, 181)     # índigo del icono → azul de la app
big = ImageFont.truetype(str(FONTS / "seguibl.ttf"), 92)
sub = ImageFont.truetype(str(FONTS / "segoeuib.ttf"), 40)


def background():
    bg = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(bg)
    for y in range(H):
        t = y / (H - 1)
        d.line([(0, y), (W, y)], fill=tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([-200, 500, W + 200, H + 300], fill=(110, 90, 220, 90))
    bg.paste(glow.filter(ImageFilter.GaussianBlur(160)), (0, 0), glow.filter(ImageFilter.GaussianBlur(160)))
    return bg


def fit_text(d, text, font, max_w):
    size = font.size
    while d.textlength(text, font=font) > max_w and size > 40:
        size -= 4
        font = ImageFont.truetype(font.path, size)
    return font


def shot(src, title, subtitle, color):
    img = background()
    d = ImageDraw.Draw(img)
    f = fit_text(d, title, big, W - 120)
    tw = d.textlength(title, font=f)
    d.text(((W - tw) / 2 + 5, 72 + 6), title, font=f, fill=(10, 10, 30))
    d.text(((W - tw) / 2, 72), title, font=f, fill=color)
    sw = d.textlength(subtitle, font=sub)
    d.text(((W - sw) / 2, 190), subtitle, font=sub, fill=(225, 230, 250))

    # Pantalla: recortada sobre el banner y escalada para caber bajo el titular.
    screen = Image.open(SRC / src).convert("RGB").crop((0, 0, 1080, CROP_BOTTOM))
    sh = H - 290 + 40                          # se sale un poco por abajo: el móvil «asoma»
    sw_ = round(screen.width * sh / screen.height)
    screen = screen.resize((sw_, sh), Image.LANCZOS)
    bezel = 18
    fx, fy = (W - sw_) // 2 - bezel, 280
    frame = Image.new("RGBA", (sw_ + bezel * 2, sh + bezel * 2), (0, 0, 0, 0))
    fd = ImageDraw.Draw(frame)
    fd.rounded_rectangle([0, 0, frame.width - 1, frame.height - 1], radius=64, fill=(14, 14, 24, 255))
    mask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw_ - 1, sh - 1], radius=48, fill=255)
    frame.paste(screen, (bezel, bezel), mask)
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([fx + 10, fy + 24, fx + frame.width + 10, fy + frame.height + 24],
                                             radius=64, fill=(0, 0, 20, 140))
    shadow = shadow.filter(ImageFilter.GaussianBlur(28))
    img.paste(shadow, (0, 0), shadow)
    img.paste(frame, (fx, fy), frame)
    return img


for i, (src, title, subtitle, color) in enumerate(SHOTS, 1):
    shot(src, title, subtitle, color).save(OUT / f"play_{i}.png", optimize=True)
    print(f"play_{i}.png")
