import hmac
import time
import httpx
from fastapi import FastAPI, Header, HTTPException, Request
from .config import Settings
from .design import DesignRequest, WidgetRequest, ImageDesignRequest
from .nebius import NebiusDesigner

def create_app(settings: Settings | None = None, transport: httpx.AsyncBaseTransport | None = None) -> FastAPI:
    configuration = settings or Settings()
    app = FastAPI(title='MorphOS design agent', version='0.2.0')
    requests: list[float] = []

    @app.middleware('http')
    async def limit_body(request: Request, call_next):
        if request.method == 'POST':
            size = 0
            chunks = []
            async for chunk in request.stream():
                size += len(chunk)
                if size > (1450000 if request.url.path == '/reference' else 16384):
                    from fastapi.responses import JSONResponse
                    return JSONResponse({'detail': 'Request too large'}, status_code=413)
                chunks.append(chunk)
            request._body = b''.join(chunks)
        return await call_next(request)

    @app.get('/health')
    async def health() -> dict:
        return {'status': 'ok', 'version': '0.2.0'}

    @app.get('/capabilities')
    async def capabilities() -> dict:
        return {'phase': 3, 'schema_version': 2, 'inference_configured': configuration.inference_configured,
                'inference_enabled': configuration.inference_configured and bool(configuration.morphos_client_token.get_secret_value()),
                'vision_verified': configuration.nebius_vision_verified and bool(configuration.nebius_vision_model), 'generation_endpoint': '/design'}

    @app.post('/design')
    async def design(request: DesignRequest, authorization: str = Header(default='')) -> dict:
        return await infer(request, authorization)

    @app.post('/widget')
    async def widget(request: WidgetRequest, authorization: str = Header(default='')) -> dict:
        return await infer(request, authorization)

    @app.post('/reference')
    async def reference(request: ImageDesignRequest, authorization: str = Header(default='')) -> dict:
        return await infer(request, authorization)

    async def infer(request: DesignRequest | WidgetRequest | ImageDesignRequest, authorization: str) -> dict:
        token = configuration.morphos_client_token.get_secret_value()
        if not token or not hmac.compare_digest(authorization, 'Bearer ' + token):
            raise HTTPException(401, 'Valid MorphOS client token required')
        now = time.monotonic()
        requests[:] = [t for t in requests if now - t < 60]
        if len(requests) >= max(1, configuration.requests_per_minute):
            raise HTTPException(429, 'Request quota reached; try again in a minute')
        requests.append(now)
        try:
            return await NebiusDesigner(configuration, transport).design(request)
        except (ValueError, OSError) as error:
            raise HTTPException(422, 'No validated proposal. Check backend model configuration and response schema.') from error
        except (httpx.HTTPError, KeyError, IndexError, TypeError) as error:
            raise HTTPException(502, 'Inference unavailable. Your current layout is unchanged.') from error
    return app

app = create_app()
