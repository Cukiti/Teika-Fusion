"""
Genera app/src/main/res/raw/ic_upgrade_arrow.json — la flecha verde de «mejora lista».

Lottie escrito a mano (Creator no estaba conectado, 2026-10-01). Bucle de 96 f a 60 fps (1,6 s):
reposo → anticipación (se agacha) → salto con brillo que la recorre y chispas → asienta → descansa.
El fotograma 0 es la pose de reposo: con «Reducir efectos» la UI se queda en él.

    python tools/lottie/gen_upgrade_arrow.py
"""
import json, os

W = H = 128
FPS = 60
OP = 96

def rgb(h, a=1.0):
    h = h.lstrip('#')
    return [int(h[i:i+2], 16) / 255 for i in (0, 2, 4)] + [a]

def st(v):  # valor estático
    return {"a": 0, "k": v}

def anim(keys, ease=(0.33, 0.0, 0.2, 1.0)):
    """keys = [(frame, valor)]; el último no lleva curva."""
    out = []
    for i, (t, v) in enumerate(keys):
        v = v if isinstance(v, list) else [v]
        k = {"t": t, "s": v}
        if i < len(keys) - 1:
            n = len(v)
            k["o"] = {"x": [ease[0]] * n, "y": [ease[1]] * n}
            k["i"] = {"x": [ease[2]] * n, "y": [ease[3]] * n}
        out.append(k)
    return {"a": 1, "k": out}

def tr(p=(0, 0), s=(100, 100), r=0, o=100):
    return {"ty": "tr", "p": st(list(p)), "a": st([0, 0]), "s": st(list(s)), "r": st(r),
            "o": st(o), "sk": st(0), "sa": st(0)}

def path(pts):
    n = len(pts)
    return {"ty": "sh", "ks": st({"i": [[0, 0]] * n, "o": [[0, 0]] * n,
                                  "v": [list(p) for p in pts], "c": True})}

def fill(c, o=100):
    return {"ty": "fl", "c": st(c), "o": st(o), "r": 1, "bm": 0}

def stroke(c, w):
    return {"ty": "st", "c": st(c), "o": st(100), "w": st(w), "lc": 2, "lj": 2, "ml": 4, "bm": 0}

def grad(kind, stops, s, e, alpha=None):
    k = []
    for off, hx in stops:
        k += [off] + rgb(hx)[:3]
    if alpha:
        for off, a in alpha:
            k += [off, a]
    g = {"ty": "gf", "o": st(100), "r": 1, "bm": 0, "t": kind,
         "g": {"p": len(stops), "k": st(k)}, "s": st(list(s)), "e": st(list(e))}
    if kind == 2:
        g["h"] = st(0); g["a"] = st(0)
    return g

def group(items, name, t=None):
    return {"ty": "gr", "nm": name, "it": items + [t or tr()]}

def layer(ind, name, shapes, p, a=(0, 0), s=None, r=None, o=None, parent=None, **extra):
    ks = {"o": o or st(100), "r": r or st(0), "p": p if isinstance(p, dict) else st(list(p) + [0]),
          "a": st(list(a) + [0]), "s": s or st([100, 100, 100])}
    L = {"ddd": 0, "ind": ind, "ty": 4, "nm": name, "sr": 1, "ks": ks, "ao": 0,
         "shapes": shapes, "ip": 0, "op": OP, "st": 0, "bm": 0}
    if parent is not None:
        L["parent"] = parent
    L.update(extra)
    return L

# ── La flecha (coordenadas locales: base del astil en y=36) ─────────────────────────────────────
ARROW = [(0, -38), (34, -2), (13, -2), (13, 36), (-13, 36), (-13, -2), (-34, -2)]
ARROW_FILL = grad(1, [(0, '#D4FF63'), (0.45, '#46E06A'), (1, '#0C8A3A')], (0, -38), (0, 36))

RIG = 10
# El rig (nulo) lleva el gesto: ancla en la base para que se agache y estire desde el suelo.
rig_p = anim([(0, [64, 112, 0]), (8, [64, 115, 0]), (16, [64, 100, 0]), (24, [64, 114, 0]),
              (32, [64, 111, 0]), (40, [64, 112, 0]), (OP, [64, 112, 0])])
rig_s = anim([(0, [100, 100, 100]), (8, [114, 84, 100]), (16, [90, 114, 100]), (24, [108, 93, 100]),
              (32, [98, 102, 100]), (40, [100, 100, 100]), (OP, [100, 100, 100])])
rig = {"ddd": 0, "ind": RIG, "ty": 3, "nm": "rig", "sr": 1, "ao": 0, "ip": 0, "op": OP, "st": 0, "bm": 0,
       "ks": {"o": st(0), "r": st(0), "p": rig_p, "a": st([0, 0, 0]), "s": rig_s}}

CHILD_P = (0, -36)  # el origen del rig es la base; la flecha se dibuja centrada en su caja

arrow = layer(5, "flecha", [
    group([path(ARROW), ARROW_FILL, stroke(rgb('#03401B'), 9), stroke(rgb('#FFFFFF'), 17)], "cuerpo"),
], CHILD_P, parent=RIG)

gloss = layer(4, "brillo fijo", [
    group([path([(-2, -31), (-25, -6), (-17, -6), (-2, -22)]), fill(rgb('#FFFFFF'), 55)], "punta"),
    group([path([(-9, 1), (-5, 1), (-5, 30), (-9, 30)]), fill(rgb('#FFFFFF'), 30)], "astil"),
], CHILD_P, parent=RIG)

matte = layer(3, "mate flecha", [group([path(ARROW), fill(rgb('#FFFFFF'))], "mate")],
              CHILD_P, parent=RIG, td=1)

shine_p = anim([(0, [0, 50, 0]), (12, [0, 50, 0]), (30, [0, -120, 0]), (OP, [0, -120, 0])],
               ease=(0.45, 0.0, 0.25, 1.0))
shine = layer(2, "destello", [
    group([{"ty": "rc", "d": 1, "s": st([90, 16]), "p": st([0, 0]), "r": st(0)},
           fill(rgb('#FFFFFF'), 85)], "banda", tr(r=-24)),
    group([{"ty": "rc", "d": 1, "s": st([90, 6]), "p": st([0, 14]), "r": st(0)},
           fill(rgb('#FFFFFF'), 45)], "banda fina", tr(r=-24)),
], shine_p, parent=RIG, tt=1)

def sparkle(ind, t0, x0, x1, size, color):
    # x0/x1 son desplazamientos desde el centro del lienzo; y=40 es la punta en reposo.
    x0, x1 = W / 2 + x0, W / 2 + x1
    p = anim([(0, [x0, 40, 0]), (t0, [x0, 40, 0]), (t0 + 26, [x1, 8, 0]), (OP, [x1, 8, 0])],
             ease=(0.2, 0.0, 0.3, 1.0))
    s = anim([(0, [0, 0, 100]), (t0, [0, 0, 100]), (t0 + 7, [115, 115, 100]),
              (t0 + 26, [0, 0, 100]), (OP, [0, 0, 100])])
    r = anim([(0, 0), (t0, 0), (t0 + 26, 140), (OP, 140)])
    star = {"ty": "sr", "sy": 1, "d": 1, "pt": st(4), "p": st([0, 0]), "r": st(0),
            "ir": st(size * 0.3), "is": st(0), "or": st(size), "os": st(0)}
    return layer(ind, f"chispa {ind}", [group([star, fill(color)], "estrella")],
                 p, s=s, r=r)

# Halo detrás: respira con el salto.
halo = layer(20, "halo", [group([
    {"ty": "el", "d": 1, "s": st([112, 112]), "p": st([0, 0])},
    grad(2, [(0, '#5CFF8C'), (1, '#1FD15C')], (0, 0), (56, 0),
         alpha=[(0, 0.75), (0.5, 0.32), (1, 0)]),
], "brillo")], (64, 74),
    s=anim([(0, [88, 88, 100]), (16, [112, 112, 100]), (52, [90, 90, 100]), (OP, [88, 88, 100])]),
    o=anim([(0, 45), (16, 100), (52, 50), (OP, 45)]))

layers = [
    sparkle(30, 15, -20, -30, 7, rgb('#FFF6B0')),
    sparkle(31, 19, 4, 6, 5.5, rgb('#FFFFFF')),
    sparkle(32, 23, 22, 32, 6.5, rgb('#D9FF8A')),
    matte, shine, gloss, arrow, rig, halo,
]
# Las capas se pintan de la primera (arriba) a la última (abajo); el mate va justo antes de su capa.
doc = {"v": "5.7.4", "fr": FPS, "ip": 0, "op": OP, "w": W, "h": H, "nm": "ic_upgrade_arrow",
       "ddd": 0, "assets": [], "layers": layers}

root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
out = os.path.join(root, "app", "src", "main", "res", "raw", "ic_upgrade_arrow.json")
with open(out, "w", encoding="utf-8") as f:
    json.dump(doc, f, separators=(",", ":"))
print(out, os.path.getsize(out), "bytes")
