# Base image with Liberica OpenJRE and Pandoc
FROM bellsoft/liberica-openjre-debian:24.0.2

# Install pandoc
RUN apt-get update && \
    apt-get install -y pandoc && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

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