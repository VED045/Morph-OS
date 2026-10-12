from fastapi.testclient import TestClient
from morphos.main import create_app
from morphos.config import Settings

PALETTE = dict(background='#111111', surface='#222222', foreground='#FFFFFF', muted='#BBBBBB', accent='#DD4433')
THEME = dict(id='test', name='Test theme', description='Shared design', light=PALETTE, dark=PALETTE)
def setup(tmp_path):
    return TestClient(create_app(Settings(_env_file=None, community_database=str(tmp_path/'community.sqlite'), community_invite='test-invite', morphos_client_token='test-admin')))
def creator(client, name='Artist'):
    response = client.post('/community/creators', json={'name':name}, headers={'Authorization':'Bearer test-invite'})
    assert response.status_code == 201
    return {'Authorization':'Bearer ' + response.json()['key']}

def test_publish_review_download_delete_persists(tmp_path):
    client = setup(tmp_path); auth = creator(client)
    identity = client.post('/community/themes', json={'theme':THEME}, headers=auth).json()['id']
    assert client.get('/community/themes').json()['items'] == []
    assert client.post(f'/community/review/{identity}/approve', headers={'Authorization':'Bearer test-admin'}).status_code == 200
    reopened = setup(tmp_path)
    row = reopened.get('/community/themes').json()['items'][0]
    assert row['theme']['name'] == 'Test theme' and row['author'] == 'Artist'
    assert 'key' not in row
    assert reopened.delete('/community/themes/'+identity, headers=auth).status_code == 200
    assert reopened.get('/community/themes').json()['items'] == []

def test_access_boundaries_and_unknown_code(tmp_path):
    client = setup(tmp_path)
    assert client.post('/community/creators', json={'name':'Test'}).status_code == 401
    assert client.post('/community/themes', json={'theme':THEME}).status_code == 401
    assert client.get('/community/review').status_code == 401
    author = creator(client); stranger = creator(client, 'Someone else')
    identity = client.post('/community/themes', json={'theme':THEME}, headers=author).json()['id']
    assert client.delete('/community/themes/'+identity, headers=stranger).status_code == 404
    assert client.post(f'/community/review/{identity}/approve', headers=author).status_code == 401
    assert client.post('/community/themes', json={'theme':{**THEME,'script':'run code'}}, headers=author).status_code == 422
    assert client.post('/community/themes', json={'theme':{**THEME,'columns':999}}, headers=author).status_code == 422

def test_upload_quota_and_unconfigured_community(tmp_path):
    client = setup(tmp_path); auth = creator(client)
    for _ in range(5): assert client.post('/community/themes', json={'theme':THEME}, headers=auth).status_code == 201
    assert client.post('/community/themes', json={'theme':THEME}, headers=auth).status_code == 429
    assert TestClient(create_app(Settings(_env_file=None))).get('/community/themes').status_code == 503
