#!/bin/bash
# Heronix-Hub Build and Run Script
# © 2025 Heronix Education Systems LLC

echo "========================================"
echo "HERONIX-HUB LAUNCHER"
echo "========================================"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    echo "Please install Java 17 or higher"
    exit 1
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed or not in PATH"
    echo "Please install Maven 3.8 or higher"
    exit 1
fi

echo "[1/3] Cleaning previous build..."
mvn clean

echo ""
echo "[2/3] Building application..."
mvn package -DskipTests

if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Build failed!"
    exit 1
fi

echo ""
echo "[3/3] Starting Heronix-Hub..."
echo ""
echo "========================================"
echo "Application is running..."
echo "Default login: admin / admin123"
echo "Press Ctrl+C to stop"
echo "========================================"
echo ""

java -jar target/heronix-hub-1.0.0.jar
