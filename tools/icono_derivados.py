# Deriva del icono de Play (store_assets/icon_1024.png, de tools/icono_play.py) — 2026-10-01:
#   · app/src/main/res/drawable-nodpi/ic_splash_icon.webp — la splash de arranque (Android 12+ y ≤11).
#     Bitmap PLANO 960×960 transparente con el icono como cuadrado redondeado de 740 px centrado, la
#     misma geometría que el icono viejo (el usuario la validó en el A15). Sombra suave para que el
#     índigo del icono se despegue del azul de app_background.
#   · store_assets/icon_512.png y los ic_launcher_foreground de cada densidad (2026-10-01).
#   · store_assets/feature_graphic_1024x500.png — el gráfico destacado de Play, con el icono nuevo.
#     Sin «FREE»: Play desaconseja precio/promoción en los gráficos de la ficha. Rehecho el 2026-10-01.
#
# Uso: python tools/icono_derivados.py   (después de regenerar icon_1024.png)
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ICON = Image.open(ROOT / "store_assets/icon_1024.png").convert("RGBA")
FONTS = Path("C:/Windows/Fonts")


def rounded_icon(size, radius_frac=0.22, ring=0):
    """El icono a [size] px con esquinas redondeadas y, si [ring] > 0, un aro claro fino."""
    icon = ICON.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size * 4 - 1, size * 4 - 1], radius=int(size * 4 * radius_frac), fill=255)
    mask = mask.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(icon, (0, 0), mask)
    if ring:
        hi = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
        ImageDraw.Draw(hi).rounded_rectangle(
            [ring * 2, ring * 2, size * 4 - 1 - ring * 2, size * 4 - 1 - ring * 2],
            radius=int(size * 4 * radius_frac), outline=(255, 255, 255, 70), width=ring * 4)
        out.alpha_composite(hi.resize((size, size), Image.LANCZOS))
    return out


def with_shadow(canvas, art, x, y, blur, offset, alpha):
    sh = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    a = art.split()[3].point(lambda v: v * alpha // 255)
    sh.paste((0, 0, 0, 255), (x, y + offset), a)
    canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(blur)))
    canvas.alpha_composite(art, (x, y))


# ── Icono de Play (512) y lanzador adaptativo ────────────────────────────────────────────────────
# Lanzador: el arte va a 76 dp dentro del lienzo de 108 (ver mipmap-anydpi/ic_launcher.xml).
ICON.convert("RGB").resize((512, 512), Image.LANCZOS).save(ROOT / "store_assets/icon_512.png", optimize=True)
for dens, px in (("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)):
    art = round(px * 76 / 108)
    fg_icon = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    fg_icon.paste(ICON.resize((art, art), Image.LANCZOS), ((px - art) // 2, (px - art) // 2))
    fg_icon.save(ROOT / f"app/src/main/res/mipmap-{dens}/ic_launcher_foreground.png", optimize=True)

# ── Splash ───────────────────────────────────────────────────────────────────────────────────────
splash = Image.new("RGBA", (960, 960), (0, 0, 0, 0))
art = rounded_icon(740, ring=3)
with_shadow(splash, art, 110, 110, blur=22, offset=14, alpha=110)
splash.save(ROOT / "app/src/main/res/drawable-nodpi/ic_splash_icon.webp", "WEBP", quality=92, method=6)

# ── Gráfico destacado ────────────────────────────────────────────────────────────────────────────
# 2026-10-01: estilo FIEL AL JUEGO (decisión del usuario). Izquierda, el arte del icono a sangre con un
# fundido hacia el centro; derecha, el logo REAL de la pantalla de Inicio (res/raw/teikafusiontitulo.webp)
# y un lema. Sin «gratis» ni precios (política de metadatos de Play).
W, H = 1024, 500
TOP, BOTTOM = (28, 26, 62), (47, 84, 181)
fg = Image.new("RGBA", (W, H))
gd = ImageDraw.Draw(fg)
for x in range(W):
    t = x / (W - 1)
    gd.line([(x, 0), (x, H)], fill=tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)) + (255,))
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse([540, 20, 1080, 480], fill=(120, 100, 230, 110))
fg.alpha_composite(glow.filter(ImageFilter.GaussianBlur(110)))

art = ICON.resize((H, H), Image.LANCZOS)
fade = Image.new("L", (H, H), 255)
fdraw = ImageDraw.Draw(fade)
for x in range(H - 60, H):
    fdraw.line([(x, 0), (x, H)], fill=int(255 * (H - x) / 60))
art.putalpha(fade)
fg.alpha_composite(art, (0, 0))

logo = Image.open(ROOT / "app/src/main/res/raw/teikafusiontitulo.webp").convert("RGBA")
lw = 500
logo = logo.resize((lw, round(logo.height * lw / logo.width)), Image.LANCZOS)
LX = 492
with_shadow(fg, logo, LX, 150, blur=10, offset=8, alpha=120)
d = ImageDraw.Draw(fg)
tag = ImageFont.truetype(str(FONTS / "seguibl.ttf"), 34)
small = ImageFont.truetype(str(FONTS / "segoeuib.ttf"), 25)
line1 = "Rompe líneas. Fusiona Teikas."
tw = d.textlength(line1, font=tag)
d.text((LX + (lw - tw) / 2 + 3, 268 + 3), line1, font=tag, fill=(10, 10, 30))
d.text((LX + (lw - tw) / 2, 268), line1, font=tag, fill=(255, 255, 255))
line2 = "293 niveles · 14 mundos · 14 compañeros"
tw2 = d.textlength(line2, font=small)
d.text((LX + (lw - tw2) / 2, 322), line2, font=small, fill=(255, 214, 92))
fg.convert("RGB").save(ROOT / "store_assets/feature_graphic_1024x500.png", optimize=True)
print("ok")
