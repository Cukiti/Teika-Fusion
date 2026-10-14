"""Genera tools/colab/generador_sonidos_teika.ipynb (cuaderno de Google Colab).

Se genera desde aquí para que el JSON del cuaderno sea siempre válido. Para cambiar la lista de sonidos,
edita SONIDOS y vuelve a ejecutar:  python tools/colab/gen_cuaderno.py
"""
import json
from pathlib import Path

# (nombre del archivo, duración en segundos, prompt en inglés)
SONIDOS = [
    # Skins de bloque: colocar (muy corto y suave) y romper línea.
    ("snd_place_wood", 0.6, "single soft wooden block knock on a wooden board, short dry click, casual puzzle game sound effect"),
    ("snd_clear_wood", 1.2, "wooden blocks breaking apart, satisfying crunchy wood smash, cartoon puzzle game sound effect"),
    ("snd_place_crystal", 0.6, "single glass gem placed, light bright clink, casual puzzle game sound effect"),
    ("snd_clear_crystal", 1.2, "crystal shattering with magical sparkle, glass break, puzzle game sound effect"),
    ("snd_place_ice", 0.6, "single ice cube tap, crisp cold click, puzzle game sound effect"),
    ("snd_clear_ice", 1.2, "ice block cracking and crushing, crystalline shatter, puzzle game sound effect"),
    ("snd_place_lava", 0.6, "hot stone placed with a short sizzle, puzzle game sound effect"),
    ("snd_clear_lava", 1.2, "molten rock bursting, fiery sizzling crack, puzzle game sound effect"),
    ("snd_place_metal", 0.6, "small metal plate placed, light clean clink, puzzle game sound effect"),
    ("snd_clear_metal", 1.2, "metal blocks smashing, heavy clang with ring, puzzle game sound effect"),
    ("snd_place_spectre", 0.6, "short soft ghostly whoosh, ethereal airy, puzzle game sound effect"),
    ("snd_clear_spectre", 1.2, "spectral magic energy burst, eerie shimmer, puzzle game sound effect"),
    ("snd_place_void", 0.6, "deep soft sub thump, low short impact, space puzzle game sound effect"),
    ("snd_clear_void", 1.2, "void implosion with bass drop, cosmic whoosh, puzzle game sound effect"),
    ("snd_place_constellation", 0.6, "single soft twinkle star chime, bright and short, puzzle game sound effect"),
    ("snd_clear_constellation", 1.2, "bright stellar sparkle burst, ascending chime arpeggio, power up sound effect"),
    ("snd_teika_seed_break", 0.8, "cute magical egg cracking open with a happy pop, game sound effect"),
    # Momentos nuevos, prestados y mudos.
    ("poison_wash", 1.0, "green slime draining away, liquid drip off, relief, game sound effect"),
    ("poison_splash", 1.0, "toxic slime splash, gooey splat, game sound effect"),
    ("king_piece_land", 1.0, "heavy stone chess piece landing on a board, deep thud, game sound effect"),
    ("king_piece_evict", 0.8, "chess piece knocked away, wooden clack and slide, game sound effect"),
    ("rock_crack", 0.8, "stone cracking, gritty rock crack, game sound effect"),
    ("lead_crush", 0.8, "heavy lead weight crushing a block, dull heavy impact, game sound effect"),
    ("ability_ready", 0.8, "short positive ability ready chime, mobile game notification sound"),
    ("tray_reward", 1.0, "short golden reward shimmer, sparkling coins, mobile game sound effect"),
    ("vine_sprout", 1.5, "vines sprouting fast, leafy rustle and wood creak, game sound effect"),
    ("kintsugi_seal", 1.2, "liquid gold sealing a crack, soft metallic shimmer, game sound effect"),
    ("stubborn_resist", 0.6, "stubborn block resists a hit, dull muted thunk, game sound effect"),
    ("stubborn_break", 1.0, "stone finally cracks and crumbles, satisfying, game sound effect"),
    ("mold_stamp", 0.5, "perfect fit snap click, mold locking in place, game sound effect"),
    ("mirror_stamp", 0.8, "mirror reflection shimmer, glassy whoosh, game sound effect"),
    ("plant_rescue", 0.8, "roots snapping free, twig snap with a relief pop, game sound effect"),
    ("star_retint", 0.8, "magical color shift chime, soft sparkle, game sound effect"),
    ("snd_mega_collapse", 2.5, "giant magical creature collapsing into light, big implosion, epic game sound effect"),
]


def md(text):
    return {"cell_type": "markdown", "metadata": {}, "source": text.strip("\n").splitlines(keepends=True)}


def code(text):
    return {"cell_type": "code", "metadata": {}, "execution_count": None, "outputs": [],
            "source": text.strip("\n").splitlines(keepends=True)}


lista = ",\n".join(f"    ({n!r}, {d}, {p!r})" for n, d, p in SONIDOS)

cells = [
    md("""
# Generador de sonidos de Teika Fusion

Dos partes: **efectos** con *Stable Audio Open* y **música** con *ACE-Step* (opcional).

**Antes de empezar (una sola vez):**
1. Menú **Entorno de ejecución → Cambiar tipo de entorno de ejecución → GPU T4** y Guardar.
2. Crea una cuenta gratis en [huggingface.co](https://huggingface.co) y entra en
   [stable-audio-open-1.0](https://huggingface.co/stabilityai/stable-audio-open-1.0): acepta las condiciones del modelo.
3. Crea un token de lectura en [huggingface.co/settings/tokens](https://huggingface.co/settings/tokens).
   Lo pegarás tú en el paso 2 (no se guarda en el cuaderno).
4. **Licencia comercial:** regístrate gratis en la licencia *Community* de Stability AI
   ([stability.ai/license](https://stability.ai/license)). Es gratis mientras facturas menos de 1 millón de USD al año.

Ejecuta las celdas **en orden** con ▶ (o Mayús + Intro).
"""),
    md("## 1. Comprobar la GPU"),
    code("!nvidia-smi --query-gpu=name,memory.total --format=csv || echo 'SIN GPU: cambia el entorno a GPU T4 (ver arriba)'"),
    md("## 2. Instalar y entrar en Hugging Face\nTarda 1–2 minutos. Al final aparece una caja: pega tu token de Hugging Face."),
    code("""
!pip -q install -U diffusers transformers accelerate soundfile torchsde
from huggingface_hub import notebook_login
notebook_login()
"""),
    md("## 3. Cargar el modelo de efectos\nLa primera vez descarga ~5 GB (unos minutos)."),
    code("""
import torch, soundfile as sf, os
from diffusers import StableAudioPipeline
pipe = StableAudioPipeline.from_pretrained("stabilityai/stable-audio-open-1.0", torch_dtype=torch.float16).to("cuda")
SR = pipe.vae.sampling_rate
print("Modelo listo. Frecuencia:", SR, "Hz")
"""),
    md("""
## 4. Probar un solo sonido
Cambia el texto (en inglés) y la duración, y ejecuta. Escucha las 3 variantes debajo.
"""),
    code("""
from IPython.display import Audio, display
PROMPT = "single soft wooden block knock on a wooden board, short dry click, casual puzzle game sound effect"
SEGUNDOS = 0.6
NEGATIVO = "music, melody, voice, speech, noise, low quality"
g = torch.Generator("cuda").manual_seed(1)
audios = pipe(PROMPT, negative_prompt=NEGATIVO, num_inference_steps=100, audio_end_in_s=max(SEGUNDOS, 1.0),
              num_waveforms_per_prompt=3, generator=g).audios
for i, a in enumerate(audios):
    a = a.T.float().cpu().numpy()[: int(SR * SEGUNDOS)]
    print("Variante", i + 1); display(Audio(a.T, rate=SR))
"""),
    md("""
## 5. Generar TODA la lista de sonidos pendientes
3 variantes de cada uno (`nombre_v1.wav`, `_v2`, `_v3`). Unos 20–40 min en una T4. Al acabar se descarga un zip:
elige la mejor variante de cada sonido, quítale el `_vN` y déjala en `art/sfx-nuevos/`.
Si Colab se corta, vuelve a ejecutar las celdas 2, 3 y esta: salta los que ya estén hechos.
"""),
    code(f"""
SONIDOS = [
{lista}
]
NEGATIVO = "music, melody, voice, speech, noise, low quality"
os.makedirs("sonidos", exist_ok=True)
for i, (nombre, seg, prompt) in enumerate(SONIDOS, 1):
    if os.path.exists(f"sonidos/{{nombre}}_v3.wav"):
        continue
    print(f"[{{i}}/{{len(SONIDOS)}}] {{nombre}}")
    g = torch.Generator("cuda").manual_seed(i)
    audios = pipe(prompt, negative_prompt=NEGATIVO, num_inference_steps=100, audio_end_in_s=max(seg, 1.0),
                  num_waveforms_per_prompt=3, generator=g).audios
    for v, a in enumerate(audios, 1):
        sf.write(f"sonidos/{{nombre}}_v{{v}}.wav", a.T.float().cpu().numpy()[: int(SR * seg * 1.3)], SR)
!zip -qr sonidos_teika.zip sonidos
from google.colab import files
files.download("sonidos_teika.zip")
"""),
    md("""
---
# Música con ACE-Step (opcional)

Licencia Apache 2.0: uso comercial libre. Abre una interfaz web con un enlace público `gradio.live`:
escribe el estilo (por ejemplo *calm fantasy puzzle game music, harp and flute, loopable, instrumental*),
deja la letra en `[instrumental]`, elige la duración y descarga el resultado.

**Libera memoria antes:** menú *Entorno de ejecución → Reiniciar sesión* y ejecuta solo la celda 1 y estas dos.
La primera vez descarga ~8 GB. Si la interfaz no arranca, revisa las instrucciones actuales en
[github.com/ace-step/ACE-Step](https://github.com/ace-step/ACE-Step) (el proyecto cambia a menudo).
"""),
    code("!pip -q install git+https://github.com/ace-step/ACE-Step.git"),
    code("# La T4 no soporta bf16: se desactiva.\n!acestep --share true --bf16 false --port 7865"),
]

nb = {
    "nbformat": 4, "nbformat_minor": 5,
    "metadata": {"accelerator": "GPU", "colab": {"provenance": [], "gpuType": "T4"},
                 "kernelspec": {"name": "python3", "display_name": "Python 3"},
                 "language_info": {"name": "python"}},
    "cells": cells,
}
out = Path(__file__).with_name("generador_sonidos_teika.ipynb")
out.write_text(json.dumps(nb, ensure_ascii=False, indent=1), encoding="utf-8")
print("Escrito:", out, "·", len(SONIDOS), "sonidos")
