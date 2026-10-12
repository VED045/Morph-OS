import base64
from io import BytesIO
from PIL import Image, ImageOps

def sanitize_image(encoded: str) -> str:
    raw = base64.b64decode(encoded, validate=True)
    if len(raw) > 1000000:
        raise ValueError('Image too large')
    with Image.open(BytesIO(raw)) as source:
        if source.format not in {'JPEG', 'PNG'} or source.width * source.height > 4000000 or source.width < 8 or source.height < 8:
            raise ValueError('Unsupported image')
        source.load()
        image = ImageOps.exif_transpose(source).convert('RGB')
        image.thumbnail((1280, 1280))
        output = BytesIO()
        image.save(output, format='JPEG', quality=80)
    return 'data:image/jpeg;base64,' + base64.b64encode(output.getvalue()).decode()
