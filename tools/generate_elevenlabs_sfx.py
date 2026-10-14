"""Generate one ElevenLabs sound effect into a local game-assets folder.

The API key is read only from ELEVENLABS_API_KEY. It must never be committed,
placed in Android resources, or embedded in the APK.
"""

from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


API_URL = "https://api.elevenlabs.io/v1/sound-generation"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate an ElevenLabs SFX as MP3.")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--prompt", help="Description of the sound effect.")
    source.add_argument("--prompt-file", type=Path, help="UTF-8 prompt file.")
    parser.add_argument("--output", required=True, type=Path, help="Destination .mp3 path.")
    parser.add_argument("--duration", type=float, help="Duration in seconds, from 0.5 to 30.")
    parser.add_argument("--loop", action="store_true", help="Request a seamless loop.")
    parser.add_argument("--output-format", choices=("mp3_44100_128", "mp3_44100_192"),
                        default="mp3_44100_128")
    parser.add_argument(
        "--prompt-influence",
        type=float,
        default=0.3,
        help="How literally the prompt should be followed, from 0 to 1.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    api_key = os.environ.get("ELEVENLABS_API_KEY", "").strip()
    if not api_key:
        raise SystemExit("Falta la variable de entorno ELEVENLABS_API_KEY.")
    if args.output.suffix.lower() != ".mp3":
        raise SystemExit("--output debe terminar en .mp3.")
    prompt = args.prompt_file.read_text(encoding="utf-8") if args.prompt_file else args.prompt
    if not 1 <= len(prompt) <= 450:
        raise SystemExit("La descripción SFX debe contener entre 1 y 450 caracteres; no se realiza llamada API.")
    receipt_path = Path(str(args.output) + ".generation.json")
    if args.output.exists() or receipt_path.exists():
        raise SystemExit("Ya existe salida o recibo; no repetir automáticamente una solicitud.")
    if args.duration is not None and not 0.5 <= args.duration <= 30:
        raise SystemExit("--duration debe estar entre 0.5 y 30 segundos.")
    if not 0 <= args.prompt_influence <= 1:
        raise SystemExit("--prompt-influence debe estar entre 0 y 1.")

    payload = {
        "text": prompt,
        "model_id": "eleven_text_to_sound_v2",
        "prompt_influence": args.prompt_influence,
    }
    if args.duration is not None:
        payload["duration_seconds"] = args.duration
    if args.loop:
        payload["loop"] = True

    args.output.parent.mkdir(parents=True, exist_ok=True)
    receipt = {"model": payload["model_id"], "outputFormat": args.output_format,
               "request": payload, "startedAt": datetime.now(timezone.utc).isoformat(),
               "status": "request prepared; result uncertain until completion",
               "output": str(args.output)}
    def save_receipt():
        receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    save_receipt()
    request = Request(
        API_URL + "?" + urlencode({"output_format": args.output_format}),
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "xi-api-key": api_key,
            "Content-Type": "application/json",
            "Accept": "audio/mpeg",
        },
        method="POST",
    )

    try:
        with urlopen(request, timeout=120) as response:
            audio = response.read()
            receipt.update(httpStatus=response.status, requestId=response.headers.get("request-id"),
                           characterCost=response.headers.get("character-cost"),
                           contentType=response.headers.get("content-type"))
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace").replace(api_key, "[REDACTED]")
        receipt.update(status="HTTP error; no retry", httpStatus=error.code,
                       error_detail=detail[:2000], requestId=error.headers.get("request-id"))
        save_receipt()
        raise SystemExit(f"ElevenLabs HTTP {error.code}: {detail[:400]}; recibo guardado, sin reintento.") from error
    except URLError as error:
        receipt["status"] = "Connection error; result uncertain; no retry"
        save_receipt()
        raise SystemExit("Error de conexión; revisar recibo antes de otra solicitud.") from error

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(audio)
    receipt.update(status="generated; candidate not installed", bytes=len(audio),
                   completedAt=datetime.now(timezone.utc).isoformat())
    save_receipt()
    print(f"Generado: {args.output} ({len(audio)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
