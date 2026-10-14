"""
P-294 (2026-09-25) · Genera los Lottie de los biomas premium: la ESPADA DEL REY (Trono Real) y el
METEORITO (Espacio Profundo); desde P-329 (2026-09-26) también las PIEZAS DEL REY (torre, alfil y
caballo, en blanca y negra). Mismo formato que `astro_sol.json` / `astro_luna.json`: capas de forma
vectoriales, sin imágenes, 300×300 a 60 fps y en bucle.

    python tools/gen_biome_lottie.py      # escribe en app/src/main/res/raw/ rey_espada, meteorito y ajedrez_*

Se genera por código para poder retocar colores y tiempos sin editar JSON a mano.
"""
import json
import os

FR = 60
RAW = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


# ── piezas de Lottie ────────────────────────────────────────────────────────────────────────────
def st(v):
    return {"a": 0, "k": v}


def an(keys, linear=False):
    """keys = [(frame, valor), ...] con ease in-out entre cada par.

    [linear] para lo que gira o avanza SIN PARAR a lo largo del bucle (un giro de 360°): con ease el
    movimiento frena al final de cada vuelta y el bucle se nota (2026-09-25, lo vio el usuario).
    """
    out = []
    for i, (t, v) in enumerate(keys):
        k = {"t": t, "s": v if isinstance(v, list) else [v]}
        if i < len(keys) - 1:
            n = len(k["s"])
            if linear:
                k["o"] = {"x": [0] * n, "y": [0] * n}
                k["i"] = {"x": [1] * n, "y": [1] * n}
            else:
                k["o"] = {"x": [0.42] * n, "y": [0] * n}
                k["i"] = {"x": [0.58] * n, "y": [1] * n}
        out.append(k)
    return {"a": 1, "k": out}


def rgb(h, a=1.0):
    h = h.lstrip("#")
    return [int(h[0:2], 16) / 255, int(h[2:4], 16) / 255, int(h[4:6], 16) / 255, a]


def tr(p=(0, 0), s=(100, 100), r=0, o=100):
    return {"ty": "tr", "p": p if isinstance(p, dict) else st(list(p)), "a": st([0, 0]),
            "s": s if isinstance(s, dict) else st(list(s)), "r": r if isinstance(r, dict) else st(r),
            "o": o if isinstance(o, dict) else st(o)}


def el(w, h, p=(0, 0)):
    return {"ty": "el", "p": st(list(p)), "s": st([w, h])}


def rc(w, h, r=0, p=(0, 0)):
    return {"ty": "rc", "p": st(list(p)), "s": st([w, h]), "r": st(r)}


def path(vs, closed=True):
    z = [[0, 0] for _ in vs]
    return {"ty": "sh", "ks": st({"i": z, "o": z, "v": [list(v) for v in vs], "c": closed})}


def fl(c, o=100):
    return {"ty": "fl", "c": st(c), "o": o if isinstance(o, dict) else st(o), "r": 1}


def sk(c, w, o=100):
    return {"ty": "st", "c": st(c), "o": o if isinstance(o, dict) else st(o), "w": st(w), "lc": 2, "lj": 2}


def gr(name, items, **t):
    return {"ty": "gr", "nm": name, "it": items + [tr(**t)]}


def layer(ind, name, shapes, op, p=(150, 150), r=0, s=(100, 100), o=100):
    ks = {
        "o": o if isinstance(o, dict) else st(o),
        "r": r if isinstance(r, dict) else st(r),
        "p": p if isinstance(p, dict) else st([p[0], p[1], 0]),
        "a": st([0, 0, 0]),
        "s": s if isinstance(s, dict) else st([s[0], s[1], 100]),
    }
    return {"ddd": 0, "ind": ind, "ty": 4, "nm": name, "sr": 1, "ao": 0, "ks": ks,
            "shapes": shapes, "ip": 0, "op": op, "st": 0, "bm": 0}


def comp(name, op, layers):
    # En Lottie la PRIMERA capa se pinta ENCIMA: se pasan de delante a atrás.
    return {"v": "5.7.4", "fr": FR, "ip": 0, "op": op, "w": 300, "h": 300, "nm": name, "ddd": 0,
            "assets": [], "layers": layers}


# ── LA ESPADA DEL REY ───────────────────────────────────────────────────────────────────────────
def rey_espada():
    op = 120
    gold, gold_d, gold_l = rgb("#FFC93C"), rgb("#B7791F"), rgb("#FFF1B8")
    steel, steel_d, steel_l = rgb("#DDE6F2"), rgb("#8A97AD"), rgb("#FFFFFF")
    ruby = rgb("#E5374B")
    # La hoja apunta ARRIBA: punta en y=-118, guarda en y=22.
    blade = gr("blade", [path([(0, -118), (15, -98), (15, 22), (-15, 22), (-15, -98)]),
                         fl(steel), sk(steel_d, 3)])
    fuller = gr("fuller", [path([(0, -92), (0, 14)], closed=False), sk(steel_d, 4, 70)])
    edge = gr("edge", [path([(-9, -96), (-9, 18)], closed=False), sk(steel_l, 2.5, 80)])
    guard = gr("guard", [rc(92, 16, 8, (0, 30)), fl(gold), sk(gold_d, 3)])
    guard_gems = gr("guardGems", [el(9, 9, (-36, 30)), el(9, 9, (36, 30)), fl(ruby)])
    grip = gr("grip", [rc(18, 46, 5, (0, 61)), fl(rgb("#6B3A1E")), sk(rgb("#3B1F0E"), 2.5)])
    wraps = gr("wraps", [path([(-9, 48), (9, 56)], closed=False), path([(-9, 60), (9, 68)], closed=False),
                         path([(-9, 72), (9, 80)], closed=False), sk(gold, 3)])
    pommel = gr("pommel", [el(26, 26, (0, 94)), fl(gold), sk(gold_d, 3)])
    gem_pulse = an([(0, [100, 100]), (60, [118, 118]), (120, [100, 100])])
    gem = gr("gem", [el(12, 12), fl(ruby), sk(rgb("#FFD0D6"), 1.5, 80)], p=(0, 94), s=gem_pulse)
    sword = layer(3, "sword", [gem, pommel, wraps, grip, guard_gems, guard, edge, fuller, blade], op,
                  p=an([(0, [150, 156, 0]), (60, [150, 146, 0]), (120, [150, 156, 0])]),
                  r=an([(0, -4), (60, 4), (120, -4)]))
    # Destello: una franja blanca que sube por la hoja y se apaga.
    gleam = layer(2, "gleam", [gr("shine", [rc(26, 8, 4), fl(steel_l)], r=-30)], op,
                  p=an([(0, [150, 176, 0]), (50, [150, 50, 0]), (120, [150, 50, 0])]),
                  o=an([(0, 0), (12, 90), (46, 90), (58, 0), (120, 0)]))
    # Aura dorada detrás que late.
    aura = layer(4, "aura", [gr("halo", [el(190, 250), fl(gold, 22)]),
                             gr("core", [el(120, 190), fl(gold_l, 18)])], op,
                 s=an([(0, [92, 92]), (60, [108, 108]), (120, [92, 92])]))
    # Rayos cortos alrededor de la punta: «la hoja brilla».
    spark = layer(1, "sparkle", [gr("star", [{"ty": "sr", "sy": 1, "d": 1, "pt": st(4), "p": st([0, 0]),
                                              "r": st(0), "or": st(22), "os": st(0), "ir": st(5), "is": st(0)},
                                             fl(gold_l)])], op,
                  # Estrella de 4 puntas: 90° es un giro completo a la vista; lineal, sin frenar.
                  p=st([150, 30, 0]), r=an([(0, 0), (120, 180)], linear=True),
                  s=an([(0, [60, 60]), (30, [120, 120]), (60, [70, 70]), (90, [120, 120]), (120, [60, 60])]),
                  o=an([(0, 40), (30, 100), (60, 50), (90, 100), (120, 40)]))
    # (2026-09-27, pedido del usuario) Sin el destello que recorría la hoja: se quita de la
    # composición y la definición de arriba se queda solo como referencia del efecto retirado.
    return comp("rey_espada", op, [spark, sword, aura])


# ── EL METEORITO ────────────────────────────────────────────────────────────────────────────────
def meteorito():
    # Bucle de 2 s. (2026-09-25) Antes duraba 1 s, la roca giraba 60° y volvía a 0° de golpe y las
    # chispas saltaban del final al principio: se veía reiniciarse. Ahora cada cosa acaba EXACTAMENTE
    # donde empieza: la roca da una vuelta entera lineal y cada chispa nace y muere transparente.
    op = 120
    rock, rock_d, rock_l = rgb("#6B5E7A"), rgb("#3A3148"), rgb("#9D8FB0")
    hot, fire, fire_l = rgb("#FF6A2B"), rgb("#FFB23C"), rgb("#FFF0B0")
    # Roca: polígono irregular centrado en (0,0), ~96 px.
    body = gr("body", [path([(-44, -12), (-30, -40), (0, -48), (32, -38), (48, -8), (40, 26), (14, 46),
                             (-20, 44), (-44, 22)]), fl(rock), sk(rock_d, 4)])
    craters = gr("craters", [el(22, 18, (-14, -14)), el(14, 12, (18, 10)), el(10, 9, (-6, 22)),
                             fl(rock_d, 85)])
    rim_light = gr("rimLight", [path([(-30, -40), (0, -48), (32, -38)], closed=False), sk(rock_l, 5, 70)])
    glow_edge = gr("hotEdge", [path([(40, 26), (14, 46), (-20, 44), (-44, 22)], closed=False),
                               sk(hot, 7, an([(0, 70), (30, 100), (60, 70), (90, 100), (120, 70)]))])
    rock_layer = layer(2, "rock", [glow_edge, rim_light, craters, body], op, p=st([176, 176, 0]),
                       r=an([(0, 0), (120, 360)], linear=True))

    # Estela: tres lenguas de fuego hacia arriba a la izquierda, que parpadean (primer y último
    # fotograma iguales).
    def tongue(name, w, h, c, o_keys, s_keys):
        return layer(0, name, [gr("flame", [el(w, h), fl(c)], r=-45)], op, p=st([118, 118, 0]),
                     o=an(o_keys), s=an(s_keys))
    t1 = tongue("trailOuter", 90, 250, fire, [(0, 55), (40, 75), (80, 50), (120, 55)],
                [(0, [100, 100]), (60, [108, 112]), (120, [100, 100])])
    t2 = tongue("trailMid", 64, 190, hot, [(0, 80), (30, 60), (90, 90), (120, 80)],
                [(0, [100, 100]), (40, [94, 106]), (120, [100, 100])])
    t3 = tongue("trailCore", 34, 130, fire_l, [(0, 85), (50, 100), (100, 75), (120, 85)],
                [(0, [100, 100]), (70, [110, 104]), (120, [100, 100])])
    t1["ind"], t2["ind"], t3["ind"] = 5, 4, 3

    # Chispas que saltan hacia atrás, escalonadas: cada una vive su tramo y es invisible cuando vuelve
    # a su punto de salida.
    def spark(name, size, color, start, end, t0, t1_, peak):
        return gr(name, [el(size, size), fl(color)],
                  p=an([(0, start), (t0, start), (t1_, end), (120, end)]),
                  o=an([(0, 0), (t0, 0), (t0 + 6, peak), (t1_, 0), (120, 0)]))
    sparks = layer(1, "sparks", [
        spark("s1", 8, fire_l, [0, 0], [-70, -40], 0, 60, 100),
        spark("s2", 6, fire, [10, -10], [-40, -80], 30, 90, 100),
        spark("s3", 5, fire_l, [-8, 12], [-90, -10], 58, 118, 90),
    ], op, p=st([150, 150, 0]))
    return comp("meteorito", op, [sparks, rock_layer, t3, t2, t1])


# ── LAS PIEZAS DEL REY (P-329, 2026-09-26) ──────────────────────────────────────────────────────
# Torre, alfil y caballo, en blanco (aura cian) y negro (aura roja), como el vídeo de referencia
# (`art/escena.mp4`): pieza plana con canto de luz, aura que late y un destello que la recorre. El
# movimiento por el tablero y la cámara lenta los hace el código (KingPieceVFX): aquí solo vive la
# pieza, en bucle y quieta en su sitio. Pie en y=+104, cabeza hacia arriba, ~220 px de alto.
PIECE_COLORS = {
    "blanca": dict(body=rgb("#F4F1EA"), shade=rgb("#C9C3B6"), line=rgb("#4A5060"),
                   rim=rgb("#FFFFFF"), aura=rgb("#6FE3FF"), aura_l=rgb("#D8F8FF")),
    "negra": dict(body=rgb("#2B2E38"), shade=rgb("#1A1C23"), line=rgb("#C9D3E6"),
                  rim=rgb("#8D97AD"), aura=rgb("#FF4D5E"), aura_l=rgb("#FFC2C8")),
}


def pedestal(c):
    """La base común a las tres: peana ancha y collarín."""
    return [
        gr("collar", [rc(118, 16, 6, (0, 78)), fl(c["body"]), sk(c["line"], 4)]),
        gr("base", [rc(152, 26, 10, (0, 100)), fl(c["body"]), sk(c["line"], 4)]),
        gr("baseShade", [rc(140, 8, 4, (0, 108)), fl(c["shade"])]),
    ]


def torre_shapes(c):
    crown = [(-56, -54), (-56, -104), (-34, -104), (-34, -84), (-11, -84), (-11, -104), (11, -104),
             (11, -84), (34, -84), (34, -104), (56, -104), (56, -54)]
    return [
        gr("slit", [rc(10, 26, 4, (0, -8)), fl(c["line"], 70)]),
        gr("crown", [path(crown), fl(c["body"]), sk(c["line"], 4)]),
        gr("rimTop", [rc(118, 14, 5, (0, -50)), fl(c["body"]), sk(c["line"], 4)]),
        gr("tower", [path([(-44, 72), (-38, -44), (38, -44), (44, 72)]), fl(c["body"]), sk(c["line"], 4)]),
        gr("towerShade", [path([(18, 70), (22, -40), (36, -40), (41, 70)]), fl(c["shade"], 80)]),
        gr("rim", [path([(-34, 64), (-30, -38)], closed=False), sk(c["rim"], 4, 70)]),
    ] + pedestal(c)


def alfil_shapes(c):
    mitre = [(-36, 72), (-44, 24), (-36, -24), (-16, -60), (0, -76), (16, -60), (36, -24), (44, 24), (36, 72)]
    return [
        gr("ball", [el(26, 26, (0, -92)), fl(c["body"]), sk(c["line"], 4)]),
        gr("cut", [path([(12, -54), (-14, -18)], closed=False), sk(c["line"], 6)]),
        gr("sash", [rc(96, 14, 6, (0, 36)), fl(c["body"]), sk(c["line"], 4)]),
        gr("mitre", [path(mitre), fl(c["body"]), sk(c["line"], 4)]),
        gr("mitreShade", [path([(16, 70), (30, 24), (26, -18), (38, -20), (44, 24), (36, 70)]), fl(c["shade"], 80)]),
        gr("rim", [path([(-30, 60), (-36, 20), (-28, -22), (-12, -50)], closed=False), sk(c["rim"], 4, 70)]),
    ] + pedestal(c)


def caballo_shapes(c):
    # Cabeza de perfil mirando a la izquierda, crin a la derecha.
    # UNA oreja y hocico largo: con dos orejas y el morro corto se leía como un gato (2026-09-26).
    head = [(-40, 72), (-30, 30), (-20, 10), (-58, -4), (-78, -18), (-80, -34), (-54, -58), (-30, -74),
            (-14, -98), (-2, -74), (20, -66), (42, -40), (54, 0), (50, 72)]
    return [
        gr("eye", [el(12, 12, (-38, -50)), fl(c["line"])]),
        gr("nostril", [el(9, 7, (-70, -26)), fl(c["line"], 80)]),
        gr("mouth", [path([(-78, -16), (-60, -12)], closed=False), sk(c["line"], 3, 70)]),
        gr("mane", [path([(4, -70), (32, -46), (46, -8), (44, 50)], closed=False), sk(c["line"], 5, 80)]),
        gr("head", [path(head), fl(c["body"]), sk(c["line"], 4)]),
        gr("headShade", [path([(30, -52), (42, -40), (54, 0), (50, 70), (32, 70), (38, 0)]), fl(c["shade"], 80)]),
        gr("rim", [path([(-76, -36), (-52, -56), (-30, -72)], closed=False), sk(c["rim"], 4, 70)]),
    ] + pedestal(c)


def pieza(nombre, shapes_fn, color):
    op = 120
    c = PIECE_COLORS[color]
    body = layer(3, "piece", shapes_fn(c), op,
                 p=an([(0, [150, 158, 0]), (60, [150, 150, 0]), (120, [150, 158, 0])]))
    # Destello que cruza la pieza en diagonal y se apaga (el brillo del vídeo).
    gleam = layer(2, "gleam", [gr("shine", [rc(22, 190, 11), fl(c["rim"], 55)], r=24)], op,
                  p=an([(0, [60, 150, 0]), (46, [240, 150, 0]), (120, [240, 150, 0])]),
                  o=an([(0, 0), (8, 80), (38, 80), (46, 0), (120, 0)]))
    aura = layer(4, "aura", [gr("halo", [el(210, 270), fl(c["aura"], 26)]),
                             gr("core", [el(140, 210), fl(c["aura_l"], 20)])], op,
                 s=an([(0, [90, 90]), (60, [110, 110]), (120, [90, 90])]))
    # Anillo de marca bajo el pie, como las casillas iluminadas del vídeo.
    ring = layer(5, "ring", [gr("mark", [el(170, 44), sk(c["aura"], 5, 80)])], op, p=st([150, 262, 0]),
                 s=an([(0, [90, 90]), (60, [104, 104]), (120, [90, 90])]),
                 o=an([(0, 55), (60, 95), (120, 55)]))
    # (2026-09-27, pedido del usuario) Sin el destello en diagonal que cruzaba la pieza.
    return comp(nombre, op, [body, aura, ring])


def main():
    piezas = [(f"ajedrez_{p}_{col}", pieza(f"ajedrez_{p}_{col}", fn, col))
              for p, fn in (("torre", torre_shapes), ("alfil", alfil_shapes), ("caballo", caballo_shapes))
              for col in ("blanca", "negra")]
    for name, data in [("rey_espada", rey_espada()), ("meteorito", meteorito())] + piezas:
        path_out = os.path.normpath(os.path.join(RAW, name + ".json"))
        with open(path_out, "w", encoding="utf-8") as f:
            json.dump(data, f, separators=(",", ":"))
        print(path_out, os.path.getsize(path_out), "bytes")


if __name__ == "__main__":
    main()
