FROM python:3.12-slim
WORKDIR /app

# curl : healthcheck docker.
# tesseract-ocr (+ langues fra/eng) : repli OCR de l'import de PDF de regles
#   pour les pages sans couche texte (scans). Inutile pour les PDF born-digital
#   mais necessaire pour couvrir tous les cas.
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        tesseract-ocr \
        tesseract-ocr-fra \
        tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

RUN mkdir -p /app/data
VOLUME ["/app/data"]

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
