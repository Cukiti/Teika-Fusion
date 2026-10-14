"""
Descarga SELECTIVA del #GameAudioGDC Bundle de Sonniss (2026-09-25).

Los cinco ZIP pesan ~1,3 GB cada uno. Un ZIP guarda su índice al final, así que con peticiones HTTP
parciales (Range) se lee el índice y se bajan solo las pistas elegidas, sin descargar el bundle.

    python tools/sonniss_pick.py --listar            # índice de los 5 ZIP -> art/_audio_fuentes/sonniss/indice.tsv
    python tools/sonniss_pick.py "patrón" [...]      # descarga las pistas cuyo nombre contenga algún patrón

Licencia: #GameAudioGDC (https://sonniss.com/gdc-bundle-license/). Uso comercial sin atribución dentro
del juego; prohibido redistribuir los archivos sueltos y usarlos para entrenar IA. Las pistas quedan en
art/_audio_fuentes/sonniss/ (fuera de git).
"""
import io, os, sys, urllib.request, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'art', '_audio_fuentes', 'sonniss')
URL = 'https://downloads.sonniss.com/Sonniss.com-GDC2026-GameAudioBundle{}of5.zip'
HEADERS = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Referer': 'https://gdc.sonniss.com/'}


class HttpRangeFile(io.RawIOBase):
    """Fichero de solo lectura sobre HTTP: cada read() es una petición Range."""

    def __init__(self, url):
        self.url, self.pos = url, 0
        req = urllib.request.Request(url, method='HEAD', headers=HEADERS)
        self.size = int(urllib.request.urlopen(req).headers['Content-Length'])

    def seekable(self): return True
    def readable(self): return True
    def tell(self): return self.pos

    def seek(self, offset, whence=0):
        self.pos = {0: offset, 1: self.pos + offset, 2: self.size + offset}[whence]
        return self.pos

    def read(self, n=-1):
        if n is None or n < 0: n = self.size - self.pos
        if n == 0 or self.pos >= self.size: return b''
        end = min(self.pos + n, self.size)
        chunks = []
        # El servidor a veces devuelve MENOS de lo pedido: se repite hasta completar el tramo.
        while self.pos < end:
            for attempt in range(5):
                try:
                    req = urllib.request.Request(self.url, headers={**HEADERS, 'Range': f'bytes={self.pos}-{end - 1}'})
                    data = urllib.request.urlopen(req, timeout=60).read()
                    break
                except Exception:
                    if attempt == 4: raise
            if not data: break
            chunks.append(data)
            self.pos += len(data)
        return b''.join(chunks)

    def readinto(self, b):
        mv = memoryview(b).cast('B')
        data = self.read(len(mv)); mv[:len(data)] = data; return len(data)


PARTS = [int(x) for x in os.environ.get('PARTES', '1,2,3,4,5').split(',')]


def archives():
    for i in PARTS:
        # Una parte que no se deja leer no para las demás: se avisa y se sigue.
        for attempt in range(3):
            try:
                f = io.BufferedReader(HttpRangeFile(URL.format(i)), buffer_size=1 << 20)
                z = zipfile.ZipFile(f)
                break
            except zipfile.BadZipFile as e:
                print(f'parte {i}: índice ilegible ({e}), intento {attempt + 1}/3')
                z = None
        if z is not None:
            yield i, z


def main(args):
    os.makedirs(OUT, exist_ok=True)
    if '--listar' in args:
        with open(os.path.join(OUT, 'indice.tsv'), 'a', encoding='utf-8') as idx:
            for i, z in archives():
                for info in z.infolist():
                    if not info.is_dir():
                        idx.write(f'{i}\t{info.file_size}\t{info.filename}\n')
                print(f'parte {i}: {len(z.infolist())} entradas')
        return
    pats = [p.lower() for p in args]
    for i, z in archives():
        for info in z.infolist():
            if info.is_dir() or not any(p in info.filename.lower() for p in pats): continue
            dest = os.path.join(OUT, 'pistas', os.path.basename(info.filename))
            if os.path.exists(dest) and os.path.getsize(dest) == info.file_size: continue
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with z.open(info) as src, open(dest, 'wb') as dst:
                dst.write(src.read())
            print(f'parte {i}: {os.path.basename(info.filename)} ({info.file_size // 1024} KB)')


if __name__ == '__main__':
    main(sys.argv[1:])
