"""Produce one material group once, preserving whole sources; never install assets."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import json
import numpy as np
from produce_core_sfx_candidates import ROOT, sha, save, snapshot, decode, metrics


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--design', type=Path, required=True)
    p.add_argument('--key-file', type=Path, required=True)
    p.add_argument('--output-directory', type=Path, required=True)
    p.add_argument('--report-file', type=Path, required=True)
    args = p.parse_args()
    design = json.loads(args.design.read_text(encoding='utf-8'))
    tracks = design['tracks']
    assert tracks and len({t['id'] for t in tracks}) == len(tracks)
    for t in tracks:
        assert 1 <= len(t['prompt']) <= 450
        assert .5 <= t['duration'] <= 30
        assert 0 < t['attack_limit'] < t['decay_limit'] <= t['duration']
        assert t['ceiling_db'] <= -6
        assert 0 <= t.get('prompt_influence',1) <= 1
        assert (ROOT/'app/src/main/res/raw'/t['resource']).is_file()
    out = args.output_directory.resolve()
    assert not out.exists() and not args.report_file.exists(), 'Never repeat or overwrite a production'
    raw = ROOT/'app/src/main/res/raw'
    baseline = {f.name: sha(f) for f in raw.iterdir() if f.is_file()}
    key = args.key_file.read_text(encoding='utf-8-sig').strip()
    before = snapshot(key)
    out.mkdir(parents=True)
    report = {'group': design['group'], 'design': str(args.design.resolve()), 'design_sha256': sha(args.design),
              'model': 'eleven_text_to_sound_v2', 'output_format': 'mp3_44100_192',
              'account_before': before, 'processing': design['processing'],
              'commercial_clearance': 'specific student promotion evidence pending',
              'generation_requests': 0, 'installed_resources': 0, 'tracks': []}
    if design.get('review_notes'): report['review_notes'] = design['review_notes']
    if design.get('review_pair'): report['review_pair'] = design['review_pair']
    env = os.environ.copy()
    env['ELEVENLABS_API_KEY'] = key
    for t in tracks:
        ident = t['id']
        source, prompt = out/(ident+'.mp3'), out/(ident+'.prompt.txt')
        prompt.write_text(t['prompt'], encoding='utf-8')
        report['generation_requests'] += 1
        save(out/'candidates.json', report)
        subprocess.run([sys.executable, str(ROOT/'tools/generate_elevenlabs_sfx.py'),
                        '--prompt-file', str(prompt), '--output', str(source), '--duration', str(t['duration']),
                        '--output-format', report['output_format'], '--prompt-influence', str(t.get('prompt_influence',1))], env=env, check=True)
        probe = json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',str(source)]))['streams'][0]
        rate = int(probe['sample_rate'])
        assert rate == 44100
        pcm = decode(source)
        measured = metrics(pcm, rate)
        reference = raw/t['resource']
        ref_rate = int(json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',str(reference)]))['streams'][0]['sample_rate'])
        ref = metrics(decode(reference), ref_rate)
        assert measured['active_rms'] > 0 and ref['active_rms'] > 0, 'Silent candidate/reference'
        gain = min(ref['active_rms']/measured['active_rms'], 10**(t['ceiling_db']/20)/np.max(np.abs(pcm)))
        preview = out/(ident+'_escucha.wav')
        subprocess.run(['ffmpeg','-v','error','-i',str(source),'-af',f'volume={gain:.12g}',
                        '-ac','2','-c:a','pcm_s16le',str(preview)], check=True)
        preview_pcm = decode(preview)
        assert len(preview_pcm) == len(pcm)
        preview_measured = metrics(preview_pcm, rate)
        flags = []
        if measured['active_start_seconds'] > t['attack_limit']: flags.append('late attack relative to design')
        if measured['active_end_seconds'] > t['decay_limit']: flags.append('active content exceeds design decay target')
        if measured['clipped_samples']: flags.append('source clipping')
        if measured['edge_last_sample_dbfs'] > -50: flags.append('terminal sample above -50 dBFS; inspect potential abrupt end')
        if preview_measured['edge_last_sample_dbfs'] > -50: flags.append('preview terminal sample above -50 dBFS; inspect potential abrupt end')
        receipt = Path(str(source)+'.generation.json')
        report['tracks'].append({'id': ident, 'title': t['title'], 'resource': t['resource'],
            'source': str(source), 'source_sha256': sha(source), 'receipt': str(receipt), 'receipt_sha256': sha(receipt),
            'preview': str(preview), 'preview_sha256': sha(preview), 'reference_sha256': sha(reference),
            'sample_rate': rate, 'source_channels': int(probe['channels']), 'frames': len(pcm),
            'requested_duration': t['duration'], 'metrics': measured, 'preview_metrics': preview_measured,
            'preview_gain_db': float(20*np.log10(gain)), 'technical_flags': flags,
            'user_approved': False, 'integrated': False})
        save(out/'candidates.json', report)
        print(ident, 'duration', round(measured['duration_seconds'],3), 'flags', flags, flush=True)
    report['account_after'] = snapshot(key)
    assert baseline == {f.name: sha(f) for f in raw.iterdir() if f.is_file()}, 'Installed resources changed'
    report['unchanged_raw_files'] = len(baseline)
    save(out/'candidates.json', report)
    save(args.report_file, report)
    print(f'{len(report["tracks"])} candidates preserved; installed resources unchanged', flush=True)


if __name__ == '__main__':
    main()
