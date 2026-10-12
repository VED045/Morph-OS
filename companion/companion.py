"""MorphOS paired LAN clipboard. Local UI:8766, certificate-pinned Android API:8767."""
import argparse
import hashlib
import hmac
import io
import json
import mimetypes
import os
import secrets
import socket
import ssl
import threading
import time
import urllib.parse
import webbrowser
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

MAX_FILE = 100 * 1024 * 1024
TYPES = {'image/png':'.png','image/jpeg':'.jpg','image/webp':'.webp','image/gif':'.gif','image/bmp':'.bmp','video/mp4':'.mp4','video/quicktime':'.mov','video/webm':'.webm','video/x-matroska':'.mkv'}

class Store:
    def __init__(self, folder):
        self.folder = Path(folder); self.folder.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock(); self.items = []; self.counter = time.time_ns() // 1000
        self.enabled = False; self.status = 'Automatic clipboard sync is paused.'
        key = self.folder / 'pair.key'
        if not key.exists(): key.write_text(secrets.token_urlsafe(32), encoding='utf-8')
        self.key = key.read_text(encoding='utf-8').strip()
        self.prune()

    def prune(self):
        # Session-only history; old transfer files are removed at startup and after 24h.
        with self.lock:
            self.items = [x for x in self.items if time.time() - x['at'] < 86400][-20:]
            keep = {x['id'] for x in self.items}
            for path in self.folder.glob('transfer-*'):
                if path.name.split('.')[0].removeprefix('transfer-') not in keep: path.unlink(missing_ok=True)

    def add(self, origin, text=None, data=None, mime=None, name=''):
        with self.lock:
            if text is not None:
                if not 0 < len(text) <= 32768: raise ValueError('Text limit is 32,768 characters')
                raw = text.encode('utf-8'); mime = 'text/plain'
            else:
                if mime not in TYPES or not data or len(data) > MAX_FILE: raise ValueError('Choose an image or video up to 100 MB')
                raw = data
                if mime.startswith('image/'):
                    from PIL import Image
                    with Image.open(io.BytesIO(data)) as image:
                        if image.width * image.height > 40_000_000: raise ValueError('Image is too large')
                        image.verify()
            digest = hashlib.sha256(raw).hexdigest()
            if self.items and self.items[-1]['sha256'] == digest and self.items[-1]['mime'] == mime:
                return self.items[-1]
            self.counter += 1; identity = str(self.counter)
            item = dict(id=identity, origin=origin, mime=mime, name=Path(name.replace('\\','/')).name[:120] or 'Shared item', size=len(raw), sha256=digest, at=time.time())
            if text is not None: item['text'] = text
            else:
                path = self.folder / ('transfer-' + identity + TYPES[mime]); path.write_bytes(raw)
            self.items.append(item)
            while sum(x['size'] for x in self.items) > 250*1024*1024: self.items.pop(0)
            self.prune()
            return item

    def path(self, identity):
        item = next((x for x in self.items if x['id'] == identity and x['mime'] in TYPES), None)
        if not item: raise FileNotFoundError()
        return self.folder / ('transfer-' + identity + TYPES[item['mime']]), item

def certificate(folder):
    cert, key = folder/'certificate.pem', folder/'private-key.pem'
    if not cert.exists() or not key.exists():
        private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'MorphOS paired companion')])
        now = datetime.now(timezone.utc)
        value = x509.CertificateBuilder().subject_name(subject).issuer_name(subject).public_key(private.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now - timedelta(minutes=5)).not_valid_after(now + timedelta(days=3650)).sign(private, hashes.SHA256())
        cert.write_bytes(value.public_bytes(serialization.Encoding.PEM))
        key.write_bytes(private.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    value = x509.load_pem_x509_certificate(cert.read_bytes())
    return cert, key, value.fingerprint(hashes.SHA256()).hex()

def handler(store, fingerprint, local=False):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_): pass  # Never log pairing credentials or clipboard data.
        def response(self, code, body, mime='application/json'):
            raw = json.dumps(body).encode() if mime == 'application/json' else body
            self.send_response(code); self.send_header('Content-Type', mime); self.send_header('Content-Length', str(len(raw)))
            self.send_header('Cache-Control', 'no-store'); self.send_header('X-Content-Type-Options','nosniff')
            self.send_header('Content-Security-Policy', "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; object-src 'none'; frame-ancestors 'none'")
            self.end_headers(); self.wfile.write(raw)
        def allowed(self):
            if local and self.headers.get('Host') != '127.0.0.1:8766': return False
            origin = self.headers.get('Origin')
            if origin and (not local or origin != 'http://127.0.0.1:8766'): return False
            return hmac.compare_digest(self.headers.get('Authorization',''), 'Bearer ' + store.key)
        def do_GET(self):
            if local and self.path == '/' and self.headers.get('Host') == '127.0.0.1:8766':
                return self.response(200, (Path(__file__).parent/'index.html').read_bytes(), 'text/html; charset=utf-8')
            if not self.allowed(): return self.response(401, {'error':'Pair this device first'})
            try:
                with store.lock:
                    store.prune()
                    if self.path.startswith('/state'):
                        after = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query).get('after',['0'])[0]
                        result = {'items':[x for x in store.items if int(x['id']) > int(after)], 'enabled':store.enabled, 'status':store.status}
                        if local:
                            try: address = socket.gethostbyname(socket.gethostname())
                            except OSError: address = 'YOUR_LAPTOP_IP'
                            result.update(fingerprint=fingerprint, address='https://' + address + ':8767')
                        return self.response(200, result)
                    if self.path.startswith('/blob/'):
                        path, item = store.path(self.path.removeprefix('/blob/'))
                        return self.response(200, path.read_bytes(), item['mime'])
                self.response(404, {'error':'Not found'})
            except (ValueError, FileNotFoundError): self.response(404, {'error':'Item expired or invalid cursor'})
        def do_POST(self):
            if not self.allowed(): return self.response(401, {'error':'Pair this device first'})
            try:
                length = int(self.headers.get('Content-Length','0'))
                limit = MAX_FILE if self.path == '/blob' else 150000
                if not 0 < length <= limit: return self.response(413, {'error':'Payload exceeds limit'})
                self.connection.settimeout(60)
                raw = self.rfile.read(length)
                if len(raw) != length: raise ValueError('Incomplete transfer')
                origin = 'windows' if local else 'android'
                if self.path == '/text': item = store.add(origin, text=json.loads(raw)['text'])
                elif self.path == '/blob': item = store.add(origin, data=raw, mime=self.headers.get('Content-Type','').split(';')[0], name=urllib.parse.unquote(self.headers.get('X-File-Name','Shared file')))
                elif self.path == '/sync' and local:
                    with store.lock: store.enabled = json.loads(raw).get('enabled') is True
                    return self.response(200, {'enabled':store.enabled})
                elif self.path == '/clear' and local:
                    with store.lock: store.items.clear(); store.prune()
                    return self.response(200, {'cleared':True})
                else: return self.response(404, {'error':'Not found'})
                self.response(201, item)
            except (ValueError, KeyError, TypeError, OSError): self.response(422, {'error':'Invalid or unsupported transfer'})
    return Handler

def monitor(store):
    from windows_clipboard import WindowsClipboard
    clip = WindowsClipboard(); cursor = 0
    while True:
        time.sleep(.7)
        if not store.enabled:
            clip.sequence = clip.u.GetClipboardSequenceNumber()
            continue
        try:
            change = clip.read_change()
            if change:
                kind, value = change
                if kind == 'text': store.add('windows', text=value)
                elif kind == 'image': store.add('windows', data=value, mime='image/png', name='Clipboard.png')
                else:
                    for path in value: store.add('windows', data=path.read_bytes(), mime=mimetypes.guess_type(path.name)[0], name=path.name)
            with store.lock: pending = [x for x in store.items if int(x['id']) > cursor]
            for item in pending:
                if item['origin'] == 'android':
                    if item['mime'] == 'text/plain': clip.write('text', item['text'])
                    else: clip.write('file', store.path(item['id'])[0])
                cursor = int(item['id'])
            store.status = 'Automatic Windows clipboard sync is on.'
        except Exception:
            store.status = 'Clipboard busy or unsupported item; retrying. Manual transfers remain available.'

def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--data', default=str(Path(__file__).parent/'private')); parser.add_argument('--no-browser', action='store_true'); parser.add_argument('--no-clipboard', action='store_true')
    args = parser.parse_args(); store = Store(args.data)
    cert, key, fingerprint = certificate(store.folder)
    remote = ThreadingHTTPServer(('0.0.0.0',8767), handler(store, fingerprint))
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); tls.minimum_version = ssl.TLSVersion.TLSv1_2; tls.load_cert_chain(cert,key)
    remote.socket = tls.wrap_socket(remote.socket, server_side=True)
    local = ThreadingHTTPServer(('127.0.0.1',8766), handler(store,fingerprint,True))
    threading.Thread(target=remote.serve_forever, daemon=True).start()
    if not args.no_clipboard: threading.Thread(target=monitor,args=(store,),daemon=True).start()
    url = 'http://127.0.0.1:8766/#' + store.key
    if not args.no_browser: webbrowser.open(url)
    print('MorphOS companion running. Open the local window for pairing. Close this process to disconnect.', flush=True)
    try: local.serve_forever()
    except KeyboardInterrupt: pass
    finally: remote.shutdown(); local.server_close(); remote.server_close()

if __name__ == '__main__': main()
