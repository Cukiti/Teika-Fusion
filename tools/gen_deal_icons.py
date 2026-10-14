# -*- coding: utf-8 -*-
"""
Genera los seis iconos Lottie de las cartas de EL TRATO (P-74).

Cada icono es una BANDEJA de esa familia: las fichas reales que reparte, dibujadas con
la paleta CLASICA del juego (BlockSkins.classicPalette). Las piezas entran con un pop
escalonado y se quedan quietas — no hay bucle: la animacion responde a que la carta
aparece o se elige, no es ruido ambiente.

Salida: app/src/main/res/raw/deal_*.json
"""
import json
import os

# Paleta CLASICA (BlockSkins.classicPalette): [base, claro, oscuro]
PALETTE = [
    ((0xFF, 0x5A, 0x5A), (0xD6, 0x32, 0x2F)),  # 0 rojo
    ((0xFF, 0xC8, 0x3D), (0xE0, 0xA0, 0x10)),  # 1 amarillo
    ((0x7B, 0xD1, 0x4A), (0x56, 0xA0, 0x30)),  # 2 verde lima
    ((0x4A, 0x9B, 0xF5), (0x2A, 0x6F, 0xD0)),  # 3 azul
    ((0xB0, 0x5A, 0xE0), (0x7E, 0x2E, 0xB8)),  # 4 morado
]

W = H = 64          # lienzo
CELL = 12.0         # lado de la celda
STEP = 14.0         # celda + hueco
COLS, ROWS = 4, 3
OX = (W - (COLS * STEP - 2)) / 2.0 + CELL / 2.0
OY = (H - (ROWS * STEP - 2)) / 2.0 + CELL / 2.0

FPS = 60
OP = 46             # duracion total en frames (~0.77 s)


def cx(i): return OX + STEP * i
def cy(j): return OY + STEP * j


# Cada familia = lista de PIEZAS; cada pieza = (indice de color, [(col, fila), ...]).
# Las piezas salen del catalogo real de `Piece.SHAPES`, para que el icono no prometa
# una forma que la bandeja no puede repartir.
FAMILIES = {
    # MIXED: la variedad de siempre — una L, un domino y una suelta.
    "deal_mixed": [
        (3, [(0, 0), (0, 1), (1, 1)]),
        (1, [(2, 0), (3, 0)]),
        (0, [(3, 2)]),
    ],
    # BARS: solo barras, en las dos orientaciones.
    "deal_bars": [
        (3, [(0, 0), (1, 0), (2, 0), (3, 0)]),
        (1, [(0, 1), (0, 2)]),
        (0, [(1, 2), (2, 2), (3, 2)]),
    ],
    # CHUNKY: bloques macizos que comen tablero de golpe.
    "deal_chunky": [
        (3, [(0, 0), (1, 0), (0, 1), (1, 1)]),
        (0, [(2, 1), (3, 1), (2, 2), (3, 2)]),
    ],
    # NIMBLE: todo pequeno (<=3 celdas), con hueco entre pieza y pieza.
    "deal_nimble": [
        (1, [(0, 0), (0, 1)]),
        (3, [(2, 0), (2, 1), (3, 1)]),
        (0, [(3, 2)]),
    ],
    # JAGGED: recovecos — una S y una T. Ni barras ni macizos.
    "deal_jagged": [
        (3, [(1, 0), (2, 0), (0, 1), (1, 1)]),
        (1, [(2, 1), (1, 2), (2, 2), (3, 2)]),
    ],
    # ONE_BIG: la grande garantizada, y el resto libre.
    "deal_one_big": [
        (3, [(0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (0, 2), (1, 2), (2, 2)]),
        (1, [(3, 2)]),
    ],
}


def rgba(c):
    return [round(c[0] / 255.0, 4), round(c[1] / 255.0, 4), round(c[2] / 255.0, 4), 1]


def cell_group(dx, dy, base, dark):
    """Una celda: cuadrado redondeado con cara de color y borde oscuro (el bisel del juego)."""
    return {
        "ty": "gr",
        "nm": "cell",
        "it": [
            {"ty": "rc", "d": 1,
             "s": {"a": 0, "k": [CELL, CELL]},
             "p": {"a": 0, "k": [dx, dy]},
             "r": {"a": 0, "k": 3}},
            {"ty": "fl", "c": {"a": 0, "k": rgba(base)}, "o": {"a": 0, "k": 100}, "r": 1, "nm": "cara"},
            {"ty": "st", "c": {"a": 0, "k": rgba(dark)}, "o": {"a": 0, "k": 100},
             "w": {"a": 0, "k": 1.6}, "lc": 2, "lj": 2, "nm": "bisel"},
            {"ty": "tr",
             "p": {"a": 0, "k": [0, 0]}, "a": {"a": 0, "k": [0, 0]},
             "s": {"a": 0, "k": [100, 100]}, "r": {"a": 0, "k": 0}, "o": {"a": 0, "k": 100}},
        ],
    }


def ease(x1, y1, x2, y2):
    return {"i": {"x": [x2], "y": [y2]}, "o": {"x": [x1], "y": [y1]}}


def piece_layer(index, color_index, cells, delay):
    base, dark = PALETTE[color_index]
    # El pop sale del CENTRO de la pieza: la capa se ancla en su centroide y las celdas
    # se dibujan relativas a el. Sin esto, escalar hace que la pieza se deslice.
    px = sum(cx(c[0]) for c in cells) / float(len(cells))
    py = sum(cy(c[1]) for c in cells) / float(len(cells))
    shapes = [cell_group(cx(c[0]) - px, cy(c[1]) - py, base, dark) for c in cells]

    # Pop: 62% -> 106% -> 100%, con la opacidad entrando en la primera mitad.
    s0, s1, s2 = delay, delay + 9, delay + 15
    return {
        "ddd": 0, "ind": index, "ty": 4, "nm": "pieza%d" % index, "sr": 1, "ao": 0,
        "ks": {
            "o": {"a": 1, "k": [
                dict(t=s0, s=[0], **ease(0.33, 0, 0.66, 1)),
                {"t": s0 + 6, "s": [100]},
            ]},
            "r": {"a": 0, "k": 0},
            "p": {"a": 0, "k": [px, py, 0]},
            "a": {"a": 0, "k": [0, 0, 0]},
            "s": {"a": 1, "k": [
                dict(t=s0, s=[62, 62, 100], **ease(0.2, 0, 0.3, 1)),
                dict(t=s1, s=[106, 106, 100], **ease(0.4, 0, 0.4, 1)),
                {"t": s2, "s": [100, 100, 100]},
            ]},
        },
        "shapes": shapes,
        "ip": 0, "op": OP, "st": 0, "bm": 0,
    }


def build(name, pieces):
    layers = []
    # Las capas se apilan de arriba a abajo: la primera pieza tiene que ir la ULTIMA
    # para quedar delante, pero como no se solapan da igual el orden de pintado.
    for i, (color_index, cells) in enumerate(pieces):
        layers.append(piece_layer(i + 1, color_index, cells, delay=i * 5))
    return {
        "v": "5.7.4", "fr": FPS, "ip": 0, "op": OP, "w": W, "h": H,
        "nm": name, "ddd": 0, "assets": [], "layers": layers,
    }


def main():
    out_dir = os.path.join("app", "src", "main", "res", "raw")
    if not os.path.isdir(out_dir):
        raise SystemExit("No encuentro %s — ejecuta desde la raiz del repo" % out_dir)
    for name, pieces in FAMILIES.items():
        data = build(name, pieces)
        path = os.path.join(out_dir, name + ".json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, separators=(",", ":"), ensure_ascii=False)
        print("%-18s %2d capas  %5d bytes" % (name, len(data["layers"]), os.path.getsize(path)))


if __name__ == "__main__":
    main()
