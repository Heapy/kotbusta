# Use the base image with pre-installed dependencies
# Build manually with GitHub Actions: ghcr.io/heapy/kotbusta-base:latest
FROM ghcr.io/heapy/kotbusta-base:latest

# Copy the application with its final ownership in one layer.
COPY --chown=kotbusta:kotbusta /build/install/kotbusta /kotbusta

USER kotbusta

# Run the application
ENTRYPOINT ["/kotbusta/bin/kotbusta"]
