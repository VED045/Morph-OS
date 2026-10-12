import base64
import json
from io import BytesIO
import httpx
import pytest
from PIL import Image
from fastapi.testclient import TestClient
from morphos.config import Settings
from morphos.main import create_app
from morphos.images import sanitize_image
from test_design import REQUEST, AUTH

def image():
    output = BytesIO(); Image.new('RGB', (40, 40), 'red').save(output, format='PNG')
    return base64.b64encode(output.getvalue()).decode()

def test_images_are_decoded_and_metadata_stripped():
    assert sanitize_image(image()).startswith('data:image/jpeg;base64,')
    with pytest.raises(ValueError): sanitize_image('not base64')

def test_vision_must_be_explicitly_verified():
    c = TestClient(create_app(Settings(_env_file=None, morphos_client_token='separate-client-token', nebius_api_key='test', nebius_vision_model='test/vision')))
    assert c.post('/reference', json={**REQUEST,'imageBase64':image()}, headers=AUTH).status_code == 422
    assert c.get('/capabilities').json()['vision_verified'] is False

def test_verified_vision_contract_returns_editable_patch():
    def handle(request):
        if request.url.path.endswith('/models'): return httpx.Response(200, json={'data':[{'id':'test/vision'}]})
        payload = json.loads(request.content)
        assert payload['model'] == 'test/vision'
        assert payload['messages'][1]['content'][1]['type'] == 'image_url'
        assert payload['messages'][1]['content'][1]['image_url']['url'].startswith('data:image/jpeg;base64,')
        return httpx.Response(200, json={'choices':[{'finish_reason':'stop','message':{'content':json.dumps({'explanation':'Approximate geometric layout','patch':{'clockStyle':'DOT','homeStyle':'GEOMETRIC','columns':4}})}}]})
    c = TestClient(create_app(Settings(_env_file=None, morphos_client_token='separate-client-token', nebius_api_key='test', nebius_vision_model='test/vision', nebius_vision_verified=True), httpx.MockTransport(handle)))
    result = c.post('/reference', json={**REQUEST,'imageBase64':image()}, headers=AUTH)
    assert result.status_code == 200
    assert result.json()['patch']['clockStyle'] == 'DOT'
