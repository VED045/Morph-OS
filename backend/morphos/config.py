from pydantic import SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict
from urllib.parse import urlsplit

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file='.env', extra='ignore')
    nebius_api_key: SecretStr = SecretStr('')
    nebius_base_url: str = 'https://api.tokenfactory.nebius.com/v1/'
    nebius_model: str = ''
    nebius_fast_model: str = ''
    nebius_complex_model: str = ''
    nebius_vision_model: str = ''
    nebius_vision_verified: bool = False
    morphos_client_token: SecretStr = SecretStr('')
    requests_per_minute: int = 10

    @field_validator('nebius_base_url')
    @classmethod
    def secure_endpoint(cls, value: str) -> str:
        url = urlsplit(value)
        if url.scheme != 'https' or not url.hostname or url.username or url.password or url.query or url.fragment:
            raise ValueError('Nebius endpoint must be an HTTPS URL without credentials, query or fragment')
        return value.rstrip('/') + '/'

    @property
    def inference_configured(self) -> bool:
        return bool(self.nebius_api_key.get_secret_value() and (self.nebius_fast_model or self.nebius_model))
