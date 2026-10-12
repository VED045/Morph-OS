import json
import httpx
import pytest
from fastapi.testclient import TestClient
from morphos.config import Settings
from morphos.main import create_app

PALETTE = dict(background='#080808', surface='#181818', foreground='#FFFFFF', muted='#AAAAAA', accent='#EE3333')
REQUEST = dict(prompt='Use monochrome icons', current=dict(id='nothing', name='Dot', description='Original', light=PALETTE, dark=PALETTE), revision=3)

def client(patch=None, limit=10, finish='stop'):
    def handle(request):
        if request.url.path.endswith('/models'):
            return httpx.Response(200, json={'data':[{'id':'test/nemotron-fast'}, {'id':'test/nemotron-ultra'}]})
        body = json.loads(request.content)
        assert body['model'] in ['test/nemotron-fast','test/nemotron-ultra']
        assert body['response_format']['type'] == 'json_schema'
        assert 'MINIMAL' in body['messages'][0]['content']
        return httpx.Response(200, json={'choices':[{'finish_reason': finish, 'message': {'content': json.dumps({'explanation':'Only icons change', 'patch':patch or {'monochromeIcons':True}})}}]})
    return TestClient(create_app(Settings(_env_file=None, nebius_api_key='provider-secret', nebius_fast_model='test/nemotron-fast', nebius_complex_model='test/nemotron-ultra', morphos_client_token='separate-client-token', requests_per_minute=limit), httpx.MockTransport(handle)))

AUTH = {'Authorization':'Bearer separate-client-token'}

def test_valid_patch_routes_and_preserves_revision():
    c = client()
    for route, model in [('fast','test/nemotron-fast'), ('complex','test/nemotron-ultra')]:
        response = c.post('/design', json={**REQUEST, 'route':route}, headers=AUTH)
        assert response.status_code == 200
        assert response.json()['patch'] == {'monochromeIcons':True}
        assert response.json()['revision'] == 3
        assert response.json()['model'] == model
        assert 'secret' not in response.text

@pytest.mark.parametrize('patch', [{'shell':'rm -rf /'}, {'columns':99}, {'wallpaperStyle':'javascript'}, {'id':'new'}, {'monochromeIcons':'true'}])
def test_untrusted_model_payload_rejected(patch):
    assert client(patch).post('/design', json=REQUEST, headers=AUTH).status_code == 422

def test_auth_rate_limit_and_request_bounds():
    c = client(limit=1)
    assert c.post('/design', json=REQUEST).status_code == 401
    assert c.post('/design', json=REQUEST, headers=AUTH).status_code == 200
    assert c.post('/design', json=REQUEST, headers=AUTH).status_code == 429
    assert c.post('/design', content='x'*16385, headers=AUTH).status_code == 413

def test_incomplete_response_rejected():
    assert client(finish='length').post('/design', json=REQUEST, headers=AUTH).status_code == 422

def test_widget_generation_is_registry_bounded():
    def handle(request):
        if request.url.path.endswith('/models'):
            return httpx.Response(200, json={'data':[{'id':'test/nemotron-fast'}]})
        return httpx.Response(200, json={'choices':[{'finish_reason':'stop','message':{'content':json.dumps({'explanation':'A working study timer','widget':{'title':'Study','kind':'TIMER','shape':'HEXAGON','durationSeconds':1200}})}}]})
    c = TestClient(create_app(Settings(_env_file=None, nebius_api_key='provider-secret', nebius_fast_model='test/nemotron-fast', morphos_client_token='separate-client-token'), httpx.MockTransport(handle)))
    result = c.post('/widget', json={'prompt':'Hexagonal twenty minute timer','revision':4}, headers=AUTH)
    assert result.status_code == 200
    assert result.json()['widget']['durationSeconds'] == 1200
    assert result.json()['widget']['id'].startswith('w_')
    assert result.json()['revision'] == 4
