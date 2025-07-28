FROM python:3.11-slim

WORKDIR /app

# Install system dependencies for FB2 conversion
RUN apt-get update && apt-get install -y \
    calibre \
    zip \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

EXPOSE 8081

CMD ["python", "main.py"]