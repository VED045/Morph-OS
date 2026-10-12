import io
import json
import threading
import urllib.request
import urllib.error
import pytest
from PIL import Image
from http.server import ThreadingHTTPServer
from companion import Store, certificate, handler

def png():
    data = io.BytesIO(); Image.new('RGB',(8,8),'blue').save(data,'PNG'); return data.getvalue()
def test_queue_dedup_bounds_and_media(tmp_path):
    store = Store(tmp_path)
    first = store.add('windows', text='Hello phone')
    assert store.add('android', text='Hello phone')['id'] == first['id']
    media = store.add('android', data=png(), mime='image/png', name='../../picture.png')
    assert media['name'] == 'picture.png' and store.path(media['id'])[0].read_bytes() == png()
    for i in range(25): store.add('windows', text=str(i))
    assert len(store.items) == 20 and not list(tmp_path.glob('transfer-*'))
    with pytest.raises(ValueError): store.add('windows', text='a'*32769)
    with pytest.raises((ValueError, OSError)): store.add('windows', data=b'fake', mime='image/png')
    with pytest.raises(ValueError): store.add('windows', data=b'code', mime='application/javascript')

def test_persistent_pairing_certificate_and_session_cleanup(tmp_path):
    store = Store(tmp_path); cert, key, pin = certificate(tmp_path)
    assert len(pin) == 64 and cert.exists() and key.exists()
    store.add('android', data=png(), mime='image/png')
    assert Store(tmp_path).key == store.key
    assert certificate(tmp_path)[2] == pin
    assert not list(tmp_path.glob('transfer-*'))

def test_http_auth_upload_receive_and_origin(tmp_path):
    store = Store(tmp_path); server = ThreadingHTTPServer(('127.0.0.1',0),handler(store,'0'*64))
    thread = threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    base = 'http://127.0.0.1:' + str(server.server_port)
    def request(path, body=None, key=store.key, origin=None):
        headers={'Authorization':'Bearer '+key,'Content-Type':'application/json'}
        if origin: headers['Origin']=origin
        return urllib.request.urlopen(urllib.request.Request(base+path,data=json.dumps(body).encode() if body else None,headers=headers))
    try:
        with pytest.raises(urllib.error.HTTPError) as error: request('/state',key='wrong')
        assert error.value.code == 401
        with pytest.raises(urllib.error.HTTPError): request('/state',origin='https://evil.example')
        assert json.load(request('/text',{'text':'Phone to laptop'}))['origin'] == 'android'
        assert json.load(request('/state'))['items'][0]['text'] == 'Phone to laptop'
        assert not store.enabled
    finally: server.shutdown(); server.server_close()
