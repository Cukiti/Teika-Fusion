"""Produce the four approved SFX designs once; preserve sources and never install assets.

Credential is loaded from the user-supplied file only into the child process.
Requires ffmpeg, ffprobe and numpy. Re-running in the same output directory is refused.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import html
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.request import Request, urlopen

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
DESIGN = ROOT / 'docs/audio/efectos-base-v2-2026-10-11.md'
EXPECTED_ACCOUNT = '6afedca35c3cc14b9c53280492faac223141e82b44754199f9981c3e9bf714f9'
NAMES = ['snd_ui_click.ogg', 'snd_place_classic.ogg', 'snd_clear_classic.ogg', 'snd_merge_2.ogg']
LIMITS = [(.010, .060), (.010, .180), (.010, .350), (.020, .400)]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def save(path, data):
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def snapshot(key):
    def get(endpoint):
        with urlopen(Request('https://api.elevenlabs.io/v1/' + endpoint,
                             headers={'xi-api-key': key}), timeout=30) as response:
            return json.load(response)
    user, subscription = get('user'), get('user/subscription')
    account = hashlib.sha256(user['user_id'].encode()).hexdigest()
    assert account == EXPECTED_ACCOUNT, 'Different account; generation refused'
    assert subscription['tier'] == 'creator' and subscription['status'] == 'active'
    return {'captured_at_utc': datetime.now(timezone.utc).isoformat(),
            'account_id_sha256': account, 'tier': subscription['tier'], 'status': subscription['status'],
            'quota_used': subscription.get('character_count'), 'quota_limit': subscription.get('character_limit'),
            'promotion_contract_verified': False, 'credential_included': False}


def decode(path):
    result = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-ac', '2',
                             '-f', 'f32le', '-'], check=True, capture_output=True)
    return np.frombuffer(result.stdout, dtype='<f4').reshape(-1, 2).astype(np.float64)


def metrics(pcm, rate):
    peak = float(np.max(np.abs(pcm)))
    active = np.max(np.abs(pcm), axis=1) > peak * .01  # -40 dB relative to peak
    indices = np.flatnonzero(active)
    return {'duration_seconds': len(pcm) / rate, 'sample_peak_dbfs': float(20*np.log10(max(peak,1e-12))),
            'clipped_samples': int(np.sum(np.abs(pcm) >= 1)),
            'active_start_seconds': int(indices[0]) / rate if len(indices) else None,
            'active_end_seconds': (int(indices[-1])+1) / rate if len(indices) else None,
            'active_rms': float(np.sqrt(np.mean(pcm[active]**2))) if len(indices) else 0,
            'edge_last_sample_dbfs': float(20*np.log10(max(np.max(np.abs(pcm[-1])),1e-12))),
            'threshold': 'sample envelope above peak minus 40 dB; technical estimate, not auditory duration'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--key-file', type=Path, required=True)
    parser.add_argument('--output-directory', type=Path, required=True)
    parser.add_argument('--duration', type=float, help='Explicit API duration, 0.5-30 seconds')
    parser.add_argument('--ids', nargs='+', choices=['S01','S02','S03','S04'],
                        default=['S01','S02','S03','S04'], help='Only generate selected effects')
    parser.add_argument('--prompt-influence', type=float, default=0.7)
    parser.add_argument('--report-file', type=Path,
                        default=ROOT/'docs/audio/efectos-base-candidatos-2026-10-11.json')
    parser.add_argument('--resume-rejected-request', action='store_true',
                        help='Explicit recovery after correcting a rejected 400 request; uncertain requests are never retried')
    args = parser.parse_args()
    assert args.duration is None or .5 <= args.duration <= 30
    assert 0 <= args.prompt_influence <= 1 and len(set(args.ids)) == len(args.ids)
    assert not args.report_file.exists(), 'Do not overwrite a generation report'
    out = args.output_directory.resolve()
    if out.exists():
        assert args.resume_rejected_request, 'Output directory exists; no automatic repeated requests'
    prompts = re.findall(r'\*\*(S0[1-4])\*\*\s+\n> ([^\n]+)', DESIGN.read_text(encoding='utf-8'))
    assert len(prompts) == 4
    assert all(1 <= len(prompt) <= 450 for _, prompt in prompts), 'Check SFX prompt limit before API calls'
    prompts = [(ident,prompt) for ident,prompt in prompts if ident in args.ids]
    references = {}
    for ident,_ in prompts:
        reference = ROOT/'app/src/main/res/raw'/NAMES[int(ident[-1])-1]
        if not reference.exists():
            reference = reference.with_suffix('.wav')
        assert reference.exists(), 'Reference missing: ' + ident
        references[ident] = reference
    key = args.key_file.read_text(encoding='utf-8-sig').strip()
    before = snapshot(key)
    out.mkdir(parents=True, exist_ok=True)
    report = json.loads((out/'candidates.json').read_text(encoding='utf-8')) if args.resume_rejected_request else {'date': '2026-10-11', 'design': str(DESIGN), 'design_sha256': sha(DESIGN),
              'model': 'eleven_text_to_sound_v2', 'output_format': 'mp3_44100_192',
              'account_before': before, 'generation_requests': 0, 'installed_resources': 0,
              'duration_mode': 'automatic; explicit API duration minimum is 0.5s' if args.duration is None else f'explicit {args.duration}s; whole regenerated events',
              'processing': 'Whole decode plus constant gain only; no trimming, fades, EQ or compression',
              'license': {'commercial_clearance': 'specific student promotion evidence pending',
                          'references': ['https://elevenlabs.io/terms-of-use',
                            'https://help.elevenlabs.io/hc/en-us/articles/13313564601361-Can-I-publish-the-content-I-generate-on-the-platform',
                            'https://elevenlabs.io/docs/api-reference/text-to-sound-effects/convert']},
              'tracks': []}
    if args.resume_rejected_request:
        assert not report['tracks'], 'Recovery supported only before any successful candidate'
        report['account_recovery'] = before
        report['output_format'] = 'mp3_44100_192'
        report['design_sha256'] = sha(DESIGN)
    child_env = os.environ.copy()
    child_env['ELEVENLABS_API_KEY'] = key
    for ident, prompt in prompts:
        i = int(ident[-1])-1
        source, prompt_file = out / (ident+'.mp3'), out / (ident+'.prompt.txt')
        prior = Path(str(source)+'.generation.json')
        if prior.exists():
            failure = json.loads(prior.read_text(encoding='utf-8'))
            assert args.resume_rejected_request and failure['httpStatus'] == 400 and not source.exists()
            assert failure['request']['text'] != prompt, 'The rejected prompt must be corrected'
            rejected = out/(ident+'.rejected-'+failure['outputFormat']+'.generation.json')
            assert not rejected.exists()
            prior.rename(rejected)
            report.setdefault('rejected_requests',[]).append({'id':ident,'receipt':str(rejected),'sha256':sha(rejected),
                'http_status':400,'reason':failure.get('error_detail','Initial response detail was not retained')})
        prompt_file.write_text(prompt, encoding='utf-8')
        report['generation_requests'] += 1
        save(out/'candidates.json', report)
        command = [sys.executable, str(ROOT/'tools/generate_elevenlabs_sfx.py'),
                        '--prompt-file', str(prompt_file), '--output', str(source),
                        '--output-format', report['output_format'], '--prompt-influence', str(args.prompt_influence)]
        if args.duration is not None:
            command += ['--duration', str(args.duration)]
        subprocess.run(command, env=child_env, check=True)
        probe = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_streams',
                                                    '-of', 'json', str(source)]))['streams'][0]
        rate = int(probe['sample_rate'])
        assert rate == 44100
        pcm = decode(source)
        measured = metrics(pcm, rate)
        reference = references[ident]
        ref = metrics(decode(reference), rate)
        ceiling_db = -12 if ident == 'S03' else -6
        gain = min(ref['active_rms']/max(measured['active_rms'], 1e-12),
                   10**(ceiling_db/20)/max(np.max(np.abs(pcm)),1e-12))
        preview = out/(ident+'_escucha.wav')
        subprocess.run(['ffmpeg', '-v', 'error', '-i', str(source), '-af', f'volume={gain:.12g}',
                        '-ac', '2', '-c:a', 'pcm_s16le', str(preview)], check=True)
        assert len(decode(preview)) == len(pcm)
        start_limit, end_limit = LIMITS[i]
        flags = []
        if measured['active_start_seconds'] is None or measured['active_start_seconds'] > start_limit:
            flags.append('late attack relative to design')
        if measured['active_end_seconds'] is None or measured['active_end_seconds'] > end_limit:
            flags.append('active content exceeds design decay target')
        if measured['clipped_samples']: flags.append('source clipping')
        if measured['edge_last_sample_dbfs'] > -50: flags.append('terminal sample above -50 dBFS; inspect potential abrupt end')
        receipt = Path(str(source)+'.generation.json')
        track = {'id':ident,'resource':reference.name,'source':str(source),'source_sha256':sha(source),
                 'receipt':str(receipt),'receipt_sha256':sha(receipt),'preview':str(preview),
                 'preview_sha256':sha(preview),'reference_sha256':sha(reference),
                 'source_channels':int(probe['channels']),'sample_rate':rate,'metrics':measured,
                 'preview_gain_db':float(20*np.log10(gain)),'preview_metrics':metrics(decode(preview),rate),
                 'technical_flags':flags,'user_approved':False,'integrated':False}
        report['tracks'].append(track)
        save(out/'candidates.json',report)
        print(ident, 'duration', round(measured['duration_seconds'],3), 'flags', flags, flush=True)
    report['account_after'] = snapshot(key)
    save(out/'candidates.json',report)
    save(args.report_file,report)
    print(f'{len(report["tracks"])} candidates recorded; no installed resources changed', flush=True)


if __name__ == '__main__':
    main()
