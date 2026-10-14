"""Generate one instrumental music candidate with the ElevenLabs Music API.

The API key is read only from ELEVENLABS_API_KEY. It must never be committed,
placed in Android resources, or embedded in the APK. One invocation makes at
most one generation request and preserves a receipt so uncertain requests are
not repeated automatically.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


API_URL = "https://api.elevenlabs.io/v1/music"
DEFAULT_MODEL = "music_v2_5"
DEFAULT_OUTPUT_FORMAT = "mp3_44100_128"
MODELS = ("music_v2_5", "music_v2", "music_v1")
MIN_DURATION_SECONDS = 3
MAX_DURATION_SECONDS = 600
MAX_PROMPT_CHARACTERS = 4100
TIMEOUT_SECONDS = 600


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Genera un candidato musical MP3 con ElevenLabs Music."
    )
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--prompt", help="Descripción musical, máximo 4100 caracteres.")
    source.add_argument(
        "--composition-plan",
        type=Path,
        help="Archivo JSON con un composition_plan de ElevenLabs.",
    )
    parser.add_argument(
        "--duration-seconds",
        type=int,
        help="Duración para --prompt, entre 3 y 600 segundos.",
    )
    parser.add_argument(
        "--output", required=True, type=Path, help="Ruta nueva de destino .mp3."
    )
    parser.add_argument(
        "--model",
        choices=MODELS,
        default=DEFAULT_MODEL,
        help=f"Modelo (predeterminado: {DEFAULT_MODEL}).",
    )
    parser.add_argument(
        "--with-vocals",
        action="store_true",
        help="Permite voces; por defecto se solicita música instrumental.",
    )
    return parser.parse_args()


def load_request(args: argparse.Namespace) -> dict:
    if args.prompt is not None:
        if not args.prompt.strip():
            raise ValueError("--prompt no puede estar vacío.")
        if len(args.prompt) > MAX_PROMPT_CHARACTERS:
            raise ValueError(f"--prompt supera {MAX_PROMPT_CHARACTERS} caracteres.")
        if args.duration_seconds is None:
            raise ValueError("Indica --duration-seconds para fijar la duración y el coste.")
        if not MIN_DURATION_SECONDS <= args.duration_seconds <= MAX_DURATION_SECONDS:
            raise ValueError("--duration-seconds debe estar entre 3 y 600.")
        body = {
            "prompt": args.prompt,
            "music_length_ms": args.duration_seconds * 1000,
            "force_instrumental": not args.with_vocals,
        }
    else:
        if args.duration_seconds is not None:
            raise ValueError("La duración de --composition-plan se define dentro del JSON.")
        if args.with_vocals:
            raise ValueError("--with-vocals sólo se admite junto con --prompt.")
        try:
            plan = json.loads(args.composition_plan.read_text(encoding="utf-8"))
        except OSError as error:
            raise ValueError(f"No se pudo leer el plan: {error}") from error
        except json.JSONDecodeError as error:
            raise ValueError(f"El plan no es JSON válido: {error}") from error
        if not isinstance(plan, dict) or not plan:
            raise ValueError("El plan debe ser un objeto JSON no vacío.")
        body = {"composition_plan": plan}

    body["model_id"] = args.model
    return body


def write_receipt(path: Path, receipt: dict) -> None:
    path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    args = parse_args()
    api_key = os.environ.get("ELEVENLABS_API_KEY", "").strip()
    if not api_key:
        print("Falta la variable de entorno ELEVENLABS_API_KEY.", file=sys.stderr)
        return 2

    try:
        body = load_request(args)
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 2

    output = args.output.expanduser().resolve()
    if output.suffix.lower() != ".mp3":
        print("--output debe terminar en .mp3.", file=sys.stderr)
        return 2

    receipt_path = output.with_suffix(output.suffix + ".generation.json")
    if output.exists() or receipt_path.exists():
        print(
            f"Ya existe el audio o su recibo ({output} / {receipt_path}); "
            "revisa el intento antes de volver a ejecutar.",
            file=sys.stderr,
        )
        return 2

    output.parent.mkdir(parents=True, exist_ok=True)
    receipt = {
        "model": args.model,
        "outputFormat": DEFAULT_OUTPUT_FORMAT,
        "request": body,
        "startedAt": utc_now(),
        "status": "started; no automatic retries",
    }
    try:
        # Exclusive creation also prevents two local invocations from charging the
        # same output path concurrently.
        with receipt_path.open("x", encoding="utf-8") as receipt_file:
            json.dump(receipt, receipt_file, ensure_ascii=False, indent=2)
            receipt_file.write("\n")
    except FileExistsError:
        print(f"Ya existe el recibo {receipt_path}; revisa ese intento.", file=sys.stderr)
        return 2

    request = Request(
        f"{API_URL}?{urlencode({'output_format': DEFAULT_OUTPUT_FORMAT})}",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "xi-api-key": api_key,
            "Content-Type": "application/json",
            "Accept": "audio/mpeg",
        },
        method="POST",
    )

    try:
        with urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            audio = response.read()
            receipt.update(
                {
                    "httpStatus": response.status,
                    "songId": response.headers.get("song-id"),
                    "requestId": response.headers.get("request-id"),
                    "generationCost": response.headers.get("character-cost"),
                    "bytes": len(audio),
                }
            )
        if not audio:
            receipt.update(status="failed; empty audio response", completedAt=utc_now())
            write_receipt(receipt_path, receipt)
            print("ElevenLabs devolvió una respuesta de audio vacía; revisa el recibo.", file=sys.stderr)
            return 1

        output.write_bytes(audio)
        receipt.update(
            status="generated; candidate not installed",
            output=str(output),
            completedAt=utc_now(),
        )
        write_receipt(receipt_path, receipt)
        print(f"Candidato generado: {output} ({len(audio)} bytes)")
        print(f"Recibo: {receipt_path}")
        return 0
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:2000]
        receipt.update(
            status="rejected",
            httpStatus=error.code,
            error=detail,
            completedAt=utc_now(),
        )
        write_receipt(receipt_path, receipt)
        print(f"ElevenLabs devolvió HTTP {error.code}; revisa {receipt_path}.", file=sys.stderr)
        return 1
    except (TimeoutError, URLError, OSError) as error:
        # A timeout or dropped connection can happen after the provider has
        # completed a paid request. Keep the receipt and never retry implicitly.
        receipt.update(
            status="uncertain; do not retry until reviewed",
            error=type(error).__name__,
            completedAt=utc_now(),
        )
        write_receipt(receipt_path, receipt)
        print(
            f"Resultado incierto; revisa {receipt_path} antes de intentar otra generación.",
            file=sys.stderr,
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
