import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "res" / "raw"
W = H = 256
FR = 60
OP = 120


def rgba(hex_color: str, alpha: float = 1.0):
    h = hex_color.lstrip("#")
    return [
        int(h[0:2], 16) / 255,
        int(h[2:4], 16) / 255,
        int(h[4:6], 16) / 255,
        alpha,
    ]


def k(value):
    return {"a": 0, "k": value}


def ak(frames):
    return {"a": 1, "k": frames}


def f(t, value):
    return {"t": t, "s": value if isinstance(value, list) else [value]}


def tr():
    return {
        "ty": "tr",
        "p": k([0, 0]),
        "a": k([0, 0]),
        "s": k([100, 100]),
        "r": k(0),
        "o": k(100),
        "sk": k(0),
        "sa": k(0),
    }


def layer(name, shapes, pos=(128, 128), scale=None, rotate=None, opacity=None, anchor=(128, 128), blend=0):
    return {
        "ddd": 0,
        "ind": 0,
        "ty": 4,
        "nm": name,
        "sr": 1,
        "ks": {
            "o": ak(opacity) if opacity else k(100),
            "r": ak(rotate) if rotate else k(0),
            "p": k([pos[0], pos[1], 0]),
            "a": k([anchor[0], anchor[1], 0]),
            "s": ak(scale) if scale else k([100, 100, 100]),
        },
        "ao": 0,
        "shapes": shapes,
        "ip": 0,
        "op": OP,
        "st": 0,
        "bm": blend,
    }


def group(name, items):
    return {"ty": "gr", "nm": name, "it": items + [tr()]}


def ellipse(name, pos, size, fill, opacity=100, stroke=None, stroke_width=0):
    items = [{"ty": "el", "p": k(list(pos)), "s": k(list(size)), "nm": f"{name} shape"}]
    if fill:
        items.append({"ty": "fl", "c": k(rgba(fill)), "o": k(opacity), "r": 1, "nm": f"{name} fill"})
    if stroke:
        items.append({
            "ty": "st",
            "c": k(rgba(stroke)),
            "o": k(opacity),
            "w": k(stroke_width),
            "lc": 2,
            "lj": 2,
            "ml": 4,
            "nm": f"{name} stroke",
        })
    return group(name, items)


def path_shape(name, points, fill, opacity=100, stroke=None, stroke_width=0, closed=True):
    shp = {
        "i": [[0, 0] for _ in points],
        "o": [[0, 0] for _ in points],
        "v": [list(p) for p in points],
        "c": closed,
    }
    items = [{"ty": "sh", "ks": k(shp), "nm": f"{name} path"}]
    if fill:
        items.append({"ty": "fl", "c": k(rgba(fill)), "o": k(opacity), "r": 1, "nm": f"{name} fill"})
    if stroke:
        items.append({
            "ty": "st",
            "c": k(rgba(stroke)),
            "o": k(opacity),
            "w": k(stroke_width),
            "lc": 2,
            "lj": 2,
            "ml": 4,
            "nm": f"{name} stroke",
        })
    return group(name, items)


def stroke_path(name, points, color, width=5, opacity=100):
    return path_shape(name, points, None, opacity, color, width, False)


def star(name, cx, cy, radius, color, opacity=100):
    pts = []
    for i in range(8):
        r = radius if i % 2 == 0 else radius * 0.38
        angle = -1.5708 + i * 0.7854
        pts.append((cx + __import__("math").cos(angle) * r, cy + __import__("math").sin(angle) * r))
    return path_shape(name, pts, color, opacity)


def body_layers(spec):
    body = spec["body"]
    belly = spec.get("belly", "#ffffff")
    outline = spec.get("outline", "#ffffff")
    face = spec.get("face", "happy")
    layers = [
        layer(
            "premium aura",
            [
                ellipse("outer aura", (128, 132), (226, 226), spec["aura"], 24),
                ellipse("rim glow", (128, 132), (188, 188), None, 72, spec["rim"], 5),
            ],
            scale=[f(0, [98, 98, 100]), f(60, [108, 108, 100]), f(120, [98, 98, 100])],
            opacity=[f(0, [72]), f(60, [100]), f(120, [72])],
            blend=1,
        ),
        layer(
            "character body",
            [
                ellipse("body", (128, 134), (132, 142), body, 100, outline, 4),
                ellipse("belly light", (128, 157), (82, 58), belly, 28),
                ellipse("left cheek", (86, 146), (20, 13), spec.get("cheek", "#ffffff"), 38),
                ellipse("right cheek", (170, 146), (20, 13), spec.get("cheek", "#ffffff"), 38),
            ],
            scale=[f(0, [100, 100, 100]), f(36, [103, 98, 100]), f(72, [98, 103, 100]), f(120, [100, 100, 100])],
        ),
    ]
    if face == "angry":
        eye_shapes = [
            stroke_path("left brow", [(84, 110), (110, 122)], "#2b0b08", 6),
            stroke_path("right brow", [(172, 110), (146, 122)], "#2b0b08", 6),
            ellipse("left eye", (99, 132), (16, 20), "#220806"),
            ellipse("right eye", (157, 132), (16, 20), "#220806"),
            stroke_path("mouth", [(111, 169), (128, 160), (146, 169)], "#2b0b08", 5),
        ]
    elif face == "sleepy":
        eye_shapes = [
            stroke_path("left eye", [(88, 132), (111, 128), (124, 135)], "#062036", 5),
            stroke_path("right eye", [(132, 135), (150, 128), (171, 132)], "#062036", 5),
            stroke_path("smile", [(105, 166), (128, 176), (151, 166)], "#062036", 5),
        ]
    elif face == "star":
        eye_shapes = [
            star("left star eye", 100, 130, 13, "#ffffff"),
            star("right star eye", 156, 130, 13, "#ffffff"),
            stroke_path("wow mouth", [(119, 166), (128, 174), (137, 166)], "#283040", 5),
        ]
    elif face == "void":
        eye_shapes = [
            ellipse("left hollow", (100, 130), (26, 26), "#000000", 100, spec["rim"], 3),
            ellipse("right hollow", (156, 130), (26, 26), "#000000", 100, spec["rim"], 3),
            ellipse("small void mouth", (128, 170), (22, 16), "#000000", 100),
        ]
    else:
        eye_shapes = [
            ellipse("left eye", (101, 130), (18, 24), "#172033"),
            ellipse("right eye", (155, 130), (18, 24), "#172033"),
            ellipse("left shine", (96, 123), (6, 7), "#ffffff", 90),
            ellipse("right shine", (150, 123), (6, 7), "#ffffff", 90),
            stroke_path("smile", [(110, 164), (128, 174), (146, 164)], "#172033", 5),
        ]
    layers.append(layer("character face", eye_shapes))
    return layers


def effects(spec):
    kind = spec["kind"]
    c = spec["rim"]
    if kind == "fire":
        return [
            layer("flame crown", [
                path_shape("left flame", [(83, 84), (100, 43), (114, 87)], "#ff6d00", 92),
                path_shape("center flame", [(111, 80), (128, 28), (146, 80)], "#ffd54f", 92),
                path_shape("right flame", [(142, 87), (158, 46), (176, 84)], "#ff3d00", 92),
                stroke_path("lava crack", [(92, 158), (112, 146), (124, 153), (139, 132)], "#ffe082", 4),
                stroke_path("lava crack 2", [(145, 166), (159, 149), (174, 155)], "#ffab40", 4),
            ], opacity=[f(0, [78]), f(45, [100]), f(120, [78])], blend=1)
        ]
    if kind == "water":
        return [
            layer("water fins", [
                path_shape("left fin", [(72, 132), (42, 111), (62, 159)], "#4fc3f7", 82),
                path_shape("right fin", [(184, 132), (214, 111), (194, 159)], "#29b6f6", 82),
                stroke_path("wave", [(72, 176), (96, 163), (120, 176), (144, 163), (184, 176)], "#b3f5ff", 6),
                ellipse("bubble a", (84, 82), (14, 14), "#e1faff", 80),
                ellipse("bubble b", (181, 91), (10, 10), "#e1faff", 72),
            ], opacity=[f(0, [70]), f(60, [100]), f(120, [70])], blend=1)
        ]
    if kind == "nature":
        return [
            layer("leaf ears", [
                path_shape("leaf left", [(91, 83), (69, 45), (111, 60)], "#76ff03", 88, "#d9ffca", 2),
                path_shape("leaf right", [(166, 83), (187, 45), (145, 60)], "#43a047", 88, "#d9ffca", 2),
                stroke_path("vine", [(77, 184), (104, 166), (128, 184), (153, 166), (181, 184)], "#c5ff6a", 5),
            ], blend=1)
        ]
    if kind == "electric":
        return [
            layer("lightning ears", [
                path_shape("left bolt ear", [(92, 87), (70, 47), (105, 65), (94, 31), (130, 84)], "#fff176", 92),
                path_shape("right bolt ear", [(164, 87), (186, 47), (151, 65), (162, 31), (126, 84)], "#40c4ff", 82),
                stroke_path("charge", [(83, 152), (105, 136), (95, 170), (128, 142), (162, 171)], "#ffffff", 5),
            ], opacity=[f(0, [55]), f(16, [100]), f(34, [50]), f(54, [100]), f(120, [55])], blend=1)
        ]
    if kind == "shadow":
        return [
            layer("shadow smoke", [
                path_shape("left smoke", [(75, 160), (50, 112), (82, 74), (100, 110)], "#3f1b68", 72),
                path_shape("right smoke", [(181, 160), (206, 112), (174, 74), (156, 110)], "#210b38", 82),
                ellipse("forehead gem", (128, 91), (22, 16), "#ba68c8", 80),
            ], rotate=[f(0, [-4]), f(60, [4]), f(120, [-4])], blend=1)
        ]
    if kind == "golden":
        return [
            layer("gold crown", [
                path_shape("crown", [(88, 86), (99, 50), (121, 77), (128, 42), (136, 77), (157, 50), (168, 86)], "#ffd700", 100, "#fff8c4", 3),
                star("glint a", 82, 105, 13, "#ffffff", 92),
                star("glint b", 179, 112, 11, "#fff8c4", 90),
            ], rotate=[f(0, [-2]), f(60, [2]), f(120, [-2])], blend=1)
        ]
    if kind == "rainbow":
        return [
            layer("rainbow halo", [
                ellipse("red ring", (128, 132), (184, 184), None, 78, "#ff1744", 5),
                ellipse("yellow ring", (128, 132), (168, 168), None, 74, "#ffee58", 5),
                ellipse("green ring", (128, 132), (152, 152), None, 74, "#00e676", 5),
                ellipse("blue ring", (128, 132), (136, 136), None, 74, "#40c4ff", 5),
            ], rotate=[f(0, [0]), f(120, [360])], opacity=[f(0, [70]), f(60, [100]), f(120, [70])], blend=1)
        ]
    if kind == "steam":
        return [
            layer("steam curls", [
                stroke_path("curl a", [(88, 186), (72, 145), (99, 122), (83, 82), (110, 55)], "#ffffff", 7, 64),
                stroke_path("curl b", [(142, 190), (158, 150), (132, 126), (151, 85), (135, 58)], "#b2ebf2", 7, 72),
                stroke_path("curl c", [(177, 174), (190, 137), (171, 111), (188, 77)], "#e0fbff", 6, 60),
            ], opacity=[f(0, [42]), f(60, [85]), f(120, [42])], blend=1)
        ]
    if kind == "magma":
        return [
            layer("magma armor", [
                path_shape("left rock horn", [(92, 86), (75, 47), (118, 72)], "#2b1710", 100, "#ff6d00", 3),
                path_shape("right rock horn", [(164, 86), (181, 47), (138, 72)], "#2b1710", 100, "#ff6d00", 3),
                stroke_path("hot fissure", [(94, 142), (113, 129), (124, 139), (140, 111)], "#ffd740", 6),
                stroke_path("hot fissure 2", [(145, 171), (158, 146), (176, 154)], "#ff6d00", 6),
            ], opacity=[f(0, [78]), f(48, [100]), f(120, [78])], blend=1)
        ]
    if kind == "storm":
        return [
            layer("storm hair", [
                ellipse("cloud a", (100, 80), (50, 34), "#90a4ae", 92),
                ellipse("cloud b", (132, 69), (62, 40), "#b0bec5", 92),
                ellipse("cloud c", (164, 82), (48, 32), "#78909c", 92),
                path_shape("tiny bolt", [(124, 89), (110, 131), (130, 124), (116, 171), (153, 112), (133, 119)], "#fff59d", 95),
            ], opacity=[f(0, [68]), f(25, [100]), f(44, [64]), f(74, [100]), f(120, [68])], blend=1)
        ]
    if kind == "plasma":
        return [
            layer("plasma orbit", [
                ellipse("wide orbit", (128, 132), (184, 82), None, 74, "#e040fb", 5),
                ellipse("tall orbit", (128, 132), (82, 184), None, 74, "#40c4ff", 5),
                ellipse("node", (203, 132), (16, 16), "#ffffff", 95),
            ], rotate=[f(0, [0]), f(120, [360])], blend=1)
        ]
    if kind == "aurora":
        return [
            layer("aurora hair", [
                path_shape("curtain a", [(88, 58), (111, 43), (116, 114), (92, 119)], "#64ffda", 58),
                path_shape("curtain b", [(116, 48), (140, 61), (134, 124), (111, 112)], "#b388ff", 56),
                path_shape("curtain c", [(143, 48), (171, 55), (159, 122), (134, 118)], "#40c4ff", 56),
                star("soft star", 176, 90, 10, "#ffffff", 88),
            ], opacity=[f(0, [62]), f(60, [96]), f(120, [62])], blend=1)
        ]
    if kind == "forest":
        return [
            layer("forest antlers", [
                stroke_path("left antler", [(101, 88), (82, 58), (70, 48), (84, 61), (80, 40)], "#8d6e63", 7),
                stroke_path("right antler", [(155, 88), (174, 58), (186, 48), (172, 61), (176, 40)], "#8d6e63", 7),
                path_shape("moss leaf", [(128, 76), (112, 52), (145, 55)], "#81c784", 86),
            ], blend=1)
        ]
    return [
        layer("void halo", [
            ellipse("event horizon", (128, 132), (190, 190), None, 88, "#7e57c2", 7),
            ellipse("inner darkness", (128, 132), (88, 88), "#000000", 52),
            ellipse("purple singularity", (128, 132), (48, 48), "#311b92", 70),
        ], rotate=[f(0, [0]), f(120, [-360])], blend=1)
    ]


SPECS = {
    "fire":    {"kind": "fire", "body": "#d92718", "belly": "#ff9e80", "aura": "#ff3d00", "rim": "#ffd54f", "outline": "#5d1408", "cheek": "#ffccbc", "face": "angry"},
    "water":   {"kind": "water", "body": "#039be5", "belly": "#b3e5fc", "aura": "#0288d1", "rim": "#80deea", "outline": "#01579b", "cheek": "#e1f5fe", "face": "sleepy"},
    "nature":  {"kind": "nature", "body": "#43a047", "belly": "#c5e1a5", "aura": "#2e7d32", "rim": "#b2ff59", "outline": "#1b5e20", "cheek": "#dcedc8"},
    "electric":{"kind": "electric", "body": "#fdd835", "belly": "#fff9c4", "aura": "#ffeb3b", "rim": "#ffffff", "outline": "#f57f17", "cheek": "#fffde7", "face": "angry"},
    "shadow":  {"kind": "shadow", "body": "#33104d", "belly": "#7e57c2", "aura": "#190026", "rim": "#ba68c8", "outline": "#0b0013", "cheek": "#9575cd", "face": "sleepy"},
    "golden":  {"kind": "golden", "body": "#f9a825", "belly": "#fff59d", "aura": "#ffd700", "rim": "#fff8c4", "outline": "#8d6e00", "cheek": "#fffde7"},
    "rainbow": {"kind": "rainbow", "body": "#80deea", "belly": "#f8bbd0", "aura": "#00e5ff", "rim": "#ff80ab", "outline": "#7e57c2", "cheek": "#fff59d", "face": "star"},
    "steam":   {"kind": "steam", "body": "#b2ebf2", "belly": "#ffffff", "aura": "#80deea", "rim": "#ffffff", "outline": "#4dd0e1", "cheek": "#ffffff", "face": "sleepy"},
    "magma":   {"kind": "magma", "body": "#4e1a12", "belly": "#ff6d00", "aura": "#bf360c", "rim": "#ffd740", "outline": "#160503", "cheek": "#ffab91", "face": "angry"},
    "storm":   {"kind": "storm", "body": "#263b7a", "belly": "#90caf9", "aura": "#1a237e", "rim": "#80d8ff", "outline": "#0d1645", "cheek": "#bbdefb", "face": "sleepy"},
    "plasma":  {"kind": "plasma", "body": "#ad1457", "belly": "#40c4ff", "aura": "#880e4f", "rim": "#e040fb", "outline": "#3f001f", "cheek": "#f8bbd0", "face": "angry"},
    "aurora":  {"kind": "aurora", "body": "#00bfa5", "belly": "#b388ff", "aura": "#004d40", "rim": "#64ffda", "outline": "#00695c", "cheek": "#e0f7fa"},
    "forest":  {"kind": "forest", "body": "#2e7d32", "belly": "#8d6e63", "aura": "#1b5e20", "rim": "#a5d66a", "outline": "#0d3b12", "cheek": "#c5e1a5", "face": "sleepy"},
    "void":    {"kind": "void", "body": "#05030c", "belly": "#311b92", "aura": "#000000", "rim": "#7e57c2", "outline": "#b39ddb", "cheek": "#512da8", "face": "void"},
}


def animation(name, spec):
    layers = body_layers(spec) + effects(spec)
    for index, item in enumerate(layers, 1):
        item["ind"] = index
    return {
        "v": "5.7.4",
        "fr": FR,
        "ip": 0,
        "op": OP,
        "w": W,
        "h": H,
        "nm": f"Premium Teika {name}",
        "ddd": 0,
        "assets": [],
        "layers": layers,
    }


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, spec in SPECS.items():
        target = OUT / f"character_{name}.json"
        target.write_text(json.dumps(animation(name, spec), separators=(",", ":")), encoding="utf-8")
    print(f"generated {len(SPECS)} elemental character lotties")


if __name__ == "__main__":
    main()
