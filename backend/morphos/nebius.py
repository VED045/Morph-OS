"""Nebius adapter. Model IDs are configured and checked against the account catalog."""
import asyncio
import json
import httpx
from .config import Settings
from .design import DesignRequest, ImageDesignRequest, Proposal, WidgetRequest, WidgetProposal
from .images import sanitize_image
import uuid

class NebiusDesigner:
    def __init__(self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None):
        self.settings, self.transport = settings, transport

    async def design(self, request: DesignRequest | WidgetRequest) -> dict:
        settings = self.settings
        image_request = isinstance(request, ImageDesignRequest)
        model = (settings.nebius_complex_model if request.route == 'complex' else settings.nebius_fast_model) or settings.nebius_model
        if image_request:
            if not settings.nebius_vision_verified:
                raise ValueError('Verify the configured model supports images before enabling vision')
            model = settings.nebius_vision_model
        if not settings.nebius_api_key.get_secret_value() or not model:
            raise ValueError('Configure backend key and requested model route first')
        if not image_request and 'nemotron' not in model.lower():
            raise ValueError('This track requires a configured Nemotron model')
        ids = await NebiusCatalog(settings, self.transport).model_ids()
        if model not in ids:
            raise ValueError('Configured model unavailable in account catalog')
        widget_request = isinstance(request, WidgetRequest)
        schema = (WidgetProposal if widget_request else Proposal).model_json_schema()
        payload = {'model': model, 'temperature': .2, 'max_tokens': 1600,
            'response_format': {'type': 'json_schema', 'json_schema': {'name': 'morphos_design', 'strict': True, 'schema': schema}},
            'messages': [
                {'role': 'system', 'content': ('Design a registered Android widget. Return explanation and widget. Data access is permission-gated locally. ' if widget_request else 'Design an Android launcher. Return JSON with explanation and a MINIMAL theme patch. Preserve unaffected fields. ') + 'No code, URLs, device commands, private data or permissions. Schema: ' + json.dumps(schema)},
                {'role': 'user', 'content': json.dumps({'instruction': request.prompt, 'current': {} if widget_request else request.current.model_dump(exclude_none=True)})}]}
        if image_request:
            payload['messages'][1]['content'] = [
                {'type': 'text', 'text': json.dumps({'instruction': request.prompt, 'current': request.current.model_dump(exclude_none=True)}) + ' Inspect visual hierarchy, spacing, palette, clock style and icon arrangement. Map to supported schema tokens; preserve fields unrelated to this request. Explain approximations.'},
                {'type': 'image_url', 'image_url': {'url': sanitize_image(request.imageBase64)}}]
        async with httpx.AsyncClient(base_url=settings.nebius_base_url, headers={'Authorization': 'Bearer ' + settings.nebius_api_key.get_secret_value()},
                transport=self.transport, timeout=httpx.Timeout(60, connect=10), follow_redirects=False) as client:
            async with client.stream('POST', 'chat/completions', json=payload) as response:
                response.raise_for_status()
                body = bytearray()
                async for chunk in response.aiter_bytes():
                    body.extend(chunk)
                    if len(body) > 65536:
                        raise ValueError('Provider response exceeds limit')
        result = json.loads(body)
        choice = result['choices'][0]
        if choice.get('finish_reason') != 'stop' or choice['message'].get('refusal'):
            raise ValueError('Model refused or did not finish proposal')
        if widget_request:
            proposal = WidgetProposal.model_validate_json(choice['message']['content'])
            return {'explanation': proposal.explanation, 'widget': {'id': 'w_' + uuid.uuid4().hex[:8], **proposal.widget.model_dump()},
                    'model': model, 'revision': request.revision, 'source': 'nebius', 'route': request.route}
        proposal = Proposal.model_validate_json(choice['message']['content'])
        patch = proposal.patch.model_dump(exclude_none=True)
        if not patch:
            raise ValueError('No supported changes')
        return {'explanation': proposal.explanation, 'patch': patch, 'model': model,
                'revision': request.revision, 'source': 'nebius', 'route': request.route}

class NebiusCatalog:
    def __init__(self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None):
        self.settings = settings
        self.transport = transport

    async def model_ids(self) -> list[str]:
        key = self.settings.nebius_api_key.get_secret_value()
        if not key:
            raise ValueError('Set NEBIUS_API_KEY on the backend')
        async with httpx.AsyncClient(base_url=self.settings.nebius_base_url,
            headers={'Authorization': f'Bearer {key}'}, timeout=20, follow_redirects=False, transport=self.transport) as client:
            response = await client.get('models')
            response.raise_for_status()
            payload = response.json()
            if not isinstance(payload.get('data'), list):
                raise ValueError('Invalid provider model catalog')
            return sorted({item['id'] for item in payload['data'] if isinstance(item, dict) and isinstance(item.get('id'), str)})

async def probe() -> None:
    settings = Settings()
    ids = await NebiusCatalog(settings).model_ids()
    print(json.dumps({'available_model_ids': ids, 'configured_model_available': settings.nebius_model in ids}, indent=2))

if __name__ == '__main__':
    asyncio.run(probe())
