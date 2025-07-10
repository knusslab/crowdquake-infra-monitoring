#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR=$(dirname "$0")

echo "Moving to parent directory for Docker builds..."
# Move up one level from the script's directory
cd "$SCRIPT_DIR/.." || { echo "Failed to change directory. Exiting."; exit 1; }

echo "Building Docker images..."
docker build -t isslab/im-api-backend:latest -f api-backend/Dockerfile .
docker build -t isslab/im-metrics-consumer:latest -f metrics-backend/consumer/Dockerfile .
docker build -t isslab/im-host-metrics-collector:latest -f metrics-backend/machine-data-collector/Dockerfile .
docker build -t isslab/im-container-metrics-collector:latest -f metrics-backend/container-data-collector/Dockerfile .