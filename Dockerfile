# Use the base image with pre-installed dependencies
# Build manually with GitHub Actions: ghcr.io/heapy/kotbusta-base:latest
FROM ghcr.io/heapy/kotbusta-base:latest

# Switch back to root temporarily to copy and set permissions
USER root

# Copy the application
COPY /build/install/kotbusta /kotbusta

# Set ownership
RUN chown -R kotbusta:kotbusta /kotbusta

# Switch back to non-root user
USER kotbusta

# Run the application
ENTRYPOINT ["/kotbusta/bin/kotbusta"]
