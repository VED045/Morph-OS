"""Small, self-hosted theme community. Themes are data; uploads require moderation."""
import hashlib
import hmac
import json
import secrets
import sqlite3
import time
from pathlib import Path
from contextlib import contextmanager
from fastapi import APIRouter, Header, HTTPException
from pydantic import Field
from .design import StrictModel, Theme

class Creator(StrictModel):
    name: str = Field(min_length=2, max_length=40, pattern=r'^[\w .-]+$')

class Upload(StrictModel):
    theme: Theme

def community_router(settings):
    router = APIRouter(prefix='/community')
    @contextmanager
    def db():
        if not settings.community_database:
            raise HTTPException(503, 'Community is not configured on this server')
        path = Path(settings.community_database)
        path.parent.mkdir(parents=True, exist_ok=True)
        connection = sqlite3.connect(path, timeout=10)
        connection.row_factory = sqlite3.Row
        connection.executescript('''
          CREATE TABLE IF NOT EXISTS creators(id TEXT PRIMARY KEY, name TEXT, key_hash TEXT UNIQUE);
          CREATE TABLE IF NOT EXISTS themes(id TEXT PRIMARY KEY, creator TEXT, payload TEXT, created INTEGER, approved INTEGER DEFAULT 0);
          CREATE INDEX IF NOT EXISTS theme_created ON themes(created);
        ''')
        try:
            with connection:
                yield connection
        finally:
            connection.close()
    def admin(authorization):
        key = settings.morphos_client_token.get_secret_value()
        if not key or not hmac.compare_digest(authorization, 'Bearer ' + key):
            raise HTTPException(401, 'Administrator token required')
    def creator(connection, authorization):
        digest = hashlib.sha256(authorization.removeprefix('Bearer ').encode()).hexdigest()
        row = connection.execute('SELECT * FROM creators WHERE key_hash=?', (digest,)).fetchone()
        if not authorization.startswith('Bearer ') or not row:
            raise HTTPException(401, 'Creator key required')
        return row

    @router.post('/creators', status_code=201)
    def register(body: Creator, authorization: str = Header(default='')):
        invite = settings.community_invite.get_secret_value()
        if not invite or not hmac.compare_digest(authorization, 'Bearer ' + invite):
            raise HTTPException(401, 'Community invite required')
        key, identity = secrets.token_urlsafe(32), secrets.token_hex(12)
        with db() as connection:
            connection.execute('BEGIN IMMEDIATE')
            if connection.execute('SELECT count(*) FROM creators').fetchone()[0] >= 500:
                raise HTTPException(429, 'Creator capacity reached')
            connection.execute('INSERT INTO creators VALUES(?,?,?)', (identity, body.name.strip(), hashlib.sha256(key.encode()).hexdigest()))
        return {'id': identity, 'name': body.name.strip(), 'key': key}

    @router.get('/themes')
    def browse(before: int = 9223372036854775807):
        with db() as connection:
            rows = connection.execute('SELECT themes.*, creators.name AS author FROM themes JOIN creators ON creators.id=themes.creator WHERE approved=1 AND created<? ORDER BY created DESC LIMIT 40', (before,)).fetchall()
        return {'items': [{'id': r['id'], 'author': r['author'], 'created': r['created'], 'theme': json.loads(r['payload'])} for r in rows], 'next': rows[-1]['created'] if len(rows) == 40 else None}

    @router.post('/themes', status_code=201)
    def publish(body: Upload, authorization: str = Header(default='')):
        raw = body.theme.model_dump_json(exclude_none=True)
        if len(raw) > 8192:
            raise HTTPException(413, 'Theme exceeds size limit')
        identity, now = secrets.token_hex(12), time.time_ns() // 1000
        with db() as connection:
            connection.execute('BEGIN IMMEDIATE')
            author = creator(connection, authorization)
            if connection.execute('SELECT count(*) FROM themes WHERE creator=? AND created>?', (author['id'], now - 86400_000000)).fetchone()[0] >= 5:
                raise HTTPException(429, 'Five uploads per creator per day')
            if connection.execute('SELECT count(*) FROM themes').fetchone()[0] >= 5000:
                raise HTTPException(429, 'Community storage capacity reached')
            connection.execute('INSERT INTO themes VALUES(?,?,?,?,0)', (identity, author['id'], raw, now))
        return {'id': identity, 'status': 'pending_review'}

    @router.delete('/themes/{identity}')
    def delete(identity: str, authorization: str = Header(default='')):
        with db() as connection:
            author = creator(connection, authorization)
            result = connection.execute('DELETE FROM themes WHERE id=? AND creator=?', (identity, author['id']))
            if not result.rowcount:
                raise HTTPException(404, 'Theme not found for this creator')
        return {'deleted': True}

    @router.get('/review')
    def review(authorization: str = Header(default='')):
        admin(authorization)
        with db() as connection:
            return {'items': [dict(r) for r in connection.execute('SELECT * FROM themes WHERE approved=0 ORDER BY created LIMIT 100')]}

    @router.post('/review/{identity}/{decision}')
    def moderate(identity: str, decision: str, authorization: str = Header(default='')):
        admin(authorization)
        if decision not in ('approve', 'reject'):
            raise HTTPException(422, 'Choose approve or reject')
        with db() as connection:
            result = connection.execute('UPDATE themes SET approved=1 WHERE id=?' if decision == 'approve' else 'DELETE FROM themes WHERE id=?', (identity,))
            if not result.rowcount:
                raise HTTPException(404, 'Theme not found')
        return {'status': decision}
    return router
