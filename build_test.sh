#!/bin/bash
cd /Users/moraneus/AndroidStudioProjects/WiMap
echo "Starting build test..."
./gradlew :app:compileReleaseKotlin
echo "Build completed with exit code: $?"