"""Measure whole Mega charge timing without modifying any audio; not auditory certification."""
import argparse
import json
from pathlib import Path
import numpy as np
from produce_core_sfx_candidates import decode, save, sha


def analyze(track):
    assert sha(track['preview']) == track['preview_sha256']
    pcm = decode(track['preview'])
    rate = track['sample_rate']
    assert len(pcm) == track['frames']
    power = np.mean(pcm * pcm, axis=1)
    step = round(rate * .02)
    windows = [(i / rate, float(np.sqrt(np.mean(power[i:i + step]))))
               for i in range(0, len(power), step)]
    peak = max(windows, key=lambda item: item[1])
    phases = []
    for start, end in [(0, .242), (.242, .55), (.55, .924), (.924, 1.045), (1.045, 1.1)]:
        segment = power[round(start * rate):min(round(end * rate), len(power))]
        assert len(segment), 'Source must cover the analyzed phase'
        rms = float(np.sqrt(np.mean(segment)))
        phases.append({'start_ms': start * 1000, 'end_ms': end * 1000,
                       'rms_dbfs': float(20 * np.log10(max(rms, 1e-12)))})
    flags = []
    if not .900 <= peak[0] <= 1.060:
        flags.append('strongest 20ms RMS window outside late compression vicinity')
    if phases[3]['rms_dbfs'] < max(p['rms_dbfs'] for p in phases[:3]) - 6:
        flags.append('compression energy more than 6dB below strongest earlier phase')
    if not 1.020 <= track['metrics']['active_end_seconds'] <= 1.100:
        flags.append('active content does not resolve near the end of the 1100ms ceremony')
    return {'id': track['id'], 'source_sha256': track['source_sha256'],
            'preview_sha256': track['preview_sha256'], 'frames': len(pcm),
            'strongest_20ms_window_start_ms': peak[0] * 1000, 'phases': phases,
            'active_end_ms': track['metrics']['active_end_seconds'] * 1000,
            'flags': flags,
            'interpretation': 'Energy proxy only; pitch, tension, character and physical sync require listening. No audio was modified.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report-file', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assert not args.output.exists(), 'Preserve existing measurements'
    report = json.loads(args.report_file.read_text(encoding='utf-8'))
    track = next(t for t in report['tracks'] if t['id'] == 'MB01')
    result = analyze(track)
    save(args.output, result)
    print(json.dumps(result, ensure_ascii=False))
