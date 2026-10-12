import asyncio
import httpx
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError, SecretStr
from morphos.config import Settings
from morphos.main import create_app
from morphos.nebius import NebiusCatalog

def test_offline_health_and_explicit_capabilities():
    client = TestClient(create_app(Settings(_env_file=None)))
    assert client.get('/health').json() == {'status':'ok','version':'0.4.0'}
    result = client.get('/capabilities').json()
    assert result['inference_enabled'] is False
    assert result['vision_verified'] is False
    assert 'api_key' not in str(result)

@pytest.mark.parametrize('url', ['http://example.com/v1', 'https://user:pass@example.com/v1', 'file:///tmp/model', 'https://example.com/v1?key=secret'])
def test_provider_url_rejects_insecure_configuration(url):
    with pytest.raises(ValidationError):
        Settings(_env_file=None, nebius_base_url=url)

def test_catalog_uses_configured_account_without_guessing_model_ids():
    def handle(request):
        assert request.url.path == '/v1/models'
        assert request.headers['authorization'] == 'Bearer test-secret'
        return httpx.Response(200, json={'data':[{'id':'account-model-2'},{'id':'account-model-1'},{'id':'account-model-1'}]})
    settings = Settings(_env_file=None, nebius_api_key=SecretStr('test-secret'))
    assert asyncio.run(NebiusCatalog(settings, httpx.MockTransport(handle)).model_ids()) == ['account-model-1','account-model-2']

def test_catalog_requires_key():
    with pytest.raises(ValueError, match='NEBIUS_API_KEY'):
        asyncio.run(NebiusCatalog(Settings(_env_file=None)).model_ids())

