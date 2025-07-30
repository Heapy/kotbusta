# Use Ubuntu-based image instead of Alpine to avoid musl libc issues with SQLite JDBC
FROM bellsoft/liberica-openjre-debian:24-37

# Install calibre
RUN apt-get update && \
    apt-get install -y calibre && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Copy the application
COPY /build/install/kotbusta /kotbusta

# Create application user for security
RUN useradd --create-home --shell /bin/bash kotbusta && \
    chown -R kotbusta:kotbusta /kotbusta

# Create data directory with proper permissions
RUN mkdir -p /app/data && chown -R kotbusta:kotbusta /app/data

# Switch to non-root user
USER kotbusta

# Set working directory
WORKDIR /app

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["/kotbusta/bin/kotbusta"]
