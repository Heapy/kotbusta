# Base image with Liberica OpenJRE and Pandoc
FROM bellsoft/liberica-openjre-debian:25

# Install dependencies
RUN apt-get update && \
    apt-get install -y wget && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install latest pandoc with architecture detection
RUN ARCH=$(dpkg --print-architecture) && \
    if [ "$ARCH" = "amd64" ]; then \
        PANDOC_URL="https://github.com/jgm/pandoc/releases/download/3.8.2.1/pandoc-3.8.2.1-1-amd64.deb"; \
    elif [ "$ARCH" = "arm64" ]; then \
        PANDOC_URL="https://github.com/jgm/pandoc/releases/download/3.8.2.1/pandoc-3.8.2.1-1-arm64.deb"; \
    else \
        echo "Unsupported architecture: $ARCH" && exit 1; \
    fi && \
    wget "$PANDOC_URL" && \
    dpkg -i pandoc-*.deb && \
    rm pandoc-*.deb

# Create application user for security
RUN useradd --create-home --shell /bin/bash kotbusta

# Create app and data directories with proper permissions
RUN mkdir -p /app/data && \
    chown -R kotbusta:kotbusta /app

# Switch to non-root user
USER kotbusta

# Set working directory
WORKDIR /app

# Expose port (for documentation purposes)
EXPOSE 8080
