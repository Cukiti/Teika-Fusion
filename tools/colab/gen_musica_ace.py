"""Genera las músicas de los 14 biomas con ACE-Step 1.5 (licencia MIT) en Colab (2026-09-28).

Se ejecuta DENTRO del repo clonado de ACE-Step-1.5 con su entorno uv (Colab usa Python 3.13 y
ACE-Step 1.5 pide 3.11-3.12; `uv sync` trae su propio 3.12):
    pip -q install uv && git clone --depth 1 https://github.com/ace-step/ACE-Step-1.5 /content/ace
    cd /content/ace && uv sync
    cp gen_musica_ace.py /content/ace/gen.py && cd /content/ace && MPLBACKEND=Agg uv run python gen.py
(MPLBACKEND=Agg es obligatorio: Colab exporta el backend inline de su kernel y el matplotlib del venv revienta.)
Salida: /content/musica3/bgm_<bioma>_v<N>.flac (2 variantes de 100 s por bioma) y /content/musica_teika3.zip.

Tanda 2 (2026-09-28): la primera (orquestal tranquila) sonaba igual en todos los biomas; el usuario pide que
cada bioma tenga un GÉNERO y un INSTRUMENTO PRINCIPAL propios. Géneros suyos: J-Pop, electropop/EDM, jazz de
los años 30, jazz rock, brass rock, blues/R&B, soul y pop psicodélico, blues rock, rock sureño, darkwave.
Completados por Claude: city pop, rock progresivo, trip-hop, soul clásico. Ningún instrumento principal
se repite.

Tanda 3 (2026-09-28): las tandas 1-2 salieron SIN el LM (el script pedía el 0.6B, que no baja solo; el que
trae el repo es el 1.7B) y el usuario oía distorsión y poca fluidez. Ahora LM 0.6B con «thinking» (planifica la
pieza) + DiT turbo. Probado y descartado en la T4 gratis: `sft` (50 pasos + CFG) da NaN en fp16 (la T4 no tiene
bf16) y en fp32 el proceso muere por RAM (12 GB); LM 1.7B + DiT no caben en VRAM y con offload_to_cpu muere
por RAM. El LM 0.6B se baja a mano:
    uv run python -c "from huggingface_hub import snapshot_download as d; d('ACE-Step/acestep-5Hz-lm-0.6B', local_dir='/content/ace/checkpoints/acestep-5Hz-lm-0.6B')"
100 s porque el modelo cierra con un fundido desde ~84 s aunque se pida «no ending»: el bucle se corta antes.
Una pista por llamada (batch 1) para no rozar la VRAM.

API tomada de profile_inference.py del propio repo (AceStepHandler.initialize_service,
LLMHandler.initialize, GenerationParams, GenerationConfig, generate_music). Probada en Colab T4.
"""
import os
import shutil
import traceback

from acestep.handler import AceStepHandler
from acestep.inference import GenerationConfig, GenerationParams, generate_music
from acestep.llm_inference import LLMHandler

COMUN = ("instrumental background loop for a mobile puzzle game, no vocals, medium tempo, steady groove, "
         "not aggressive, clean polished mix, seamless loop, no intro, no ending")

# (bioma, género + instrumento principal que lo distingue)
BIOMAS = [
    ("garden", "southern rock, sunny country garden, lead slide dobro guitar, banjo rolls, "
               "laid-back shuffle drums, warm bass"),
    ("crystal", "electropop EDM, sparkling crystal cave, lead bright plucked synth arpeggios, "
                "sidechained pads, four-on-the-floor kick, shimmering"),
    ("fear", "darkwave, dark alternative pop, lead cold analog synth melody, chorus-drenched bass, "
             "gated drum machine, mysterious and moody"),
    ("sleep", "psychedelic soul pop, dreamy, lead mellotron flutes and sitar drones, "
              "soft wah guitar, slow hazy groove"),
    ("volcano", "blues rock, volcanic heat, lead overdriven electric guitar riffs, heavy groovy drums, "
                "fat bass, hot and gritty"),
    ("glacier", "classic slow soul ballad, icy and elegant, lead rhodes electric piano, "
                "lush string section, gentle backbeat"),
    ("storm", "jazz rock fusion, electric storm, lead fretless bass melody, crunchy electric guitar comping, "
              "busy tight drums, energetic but controlled"),
    ("toxic", "swampy blues and rhythm and blues, toxic swamp, lead blues harmonica, "
              "tremolo guitar, shuffling snare, muddy groove"),
    ("ninja", "j-pop instrumental, moonlit dojo, lead koto hook, slap bass, bright electric piano, "
              "upbeat catchy pop drums"),
    ("ghost", "1930s swing jazz, haunted mansion ballroom, lead clarinet, muted trumpet, upright bass, "
              "brushed drums, old gramophone warmth"),
    ("glutton", "brass rock, busy kitchen, lead punchy trumpets and trombones section, "
                "funky electric guitar, driving drums"),
    ("star", "japanese city pop, night sky, lead smooth alto saxophone, chorus guitar, "
             "funky bass, glossy 80s drums"),
    ("king", "progressive rock, royal throne room, lead majestic church pipe organ, "
             "odd-meter drums, grand and noble"),
    ("galactic", "trip-hop, deep space, lead eerie theremin, vibraphone, dusty slow breakbeat, "
                 "deep sub bass, floating"),
]

DURACION = 100
PASOS = 8
DIT = "acestep-v15-turbo"
LM = "acestep-5Hz-lm-0.6B"
SEMILLAS = [101, 202]
SALIDA = "/content/musica3"


def main():
    os.makedirs(SALIDA, exist_ok=True)
    dit = AceStepHandler()
    print("DiT:", dit.initialize_service(project_root="/content/ace", config_path=DIT,
                                         device="auto", use_flash_attention=False), flush=True)
    llm = LLMHandler()
    print("LM:", llm.initialize(checkpoint_dir="/content/ace/checkpoints", lm_model_path=LM,
                                backend="pt", device="auto"), flush=True)
    for i, (bioma, estilo) in enumerate(BIOMAS, 1):
        if all(os.path.exists(f"{SALIDA}/bgm_{bioma}_v{n}.flac") for n in (1, 2)):
            continue
        print(f"[{i}/{len(BIOMAS)}] {bioma}", flush=True)
        try:
            ok = 0
            for n, semilla in enumerate(SEMILLAS, 1):
                params = GenerationParams(caption=f"{estilo}, {COMUN}", lyrics="[Instrumental]",
                                          duration=DURACION, seed=semilla, inference_steps=PASOS, thinking=True)
                config = GenerationConfig(batch_size=1, seeds=[semilla], use_random_seed=False, audio_format="flac")
                res = generate_music(dit, llm, params, config, save_dir=f"/content/ace_out3/{bioma}")
                if not res.success:
                    print("  FALLO:", res.error, flush=True)
                    continue
                shutil.copy(res.audios[0]["path"], f"{SALIDA}/bgm_{bioma}_v{n}.flac")
                ok += 1
            print(f"  ok ({ok} pistas)", flush=True)
        except Exception:
            traceback.print_exc()
    os.system("cd /content && rm -f musica_teika3.zip && zip -qr musica_teika3.zip musica3")
    print("LISTO: musica_teika3.zip", flush=True)


if __name__ == "__main__":
    main()
