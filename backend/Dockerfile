FROM python:3.12-slim
WORKDIR /app
COPY requirements-lock.txt .
RUN pip install --no-cache-dir -r requirements-lock.txt
COPY morphos ./morphos
RUN useradd --uid 10001 --create-home morphos
USER morphos
EXPOSE 8000
CMD ["python", "-m", "uvicorn", "morphos.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
