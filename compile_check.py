#!/usr/bin/env python3
import subprocess
import os
import sys

os.chdir('/Users/moraneus/AndroidStudioProjects/WiMap')
print("Starting compilation check...")
print("Current directory:", os.getcwd())

try:
    result = subprocess.run(['./gradlew', ':app:compileReleaseKotlin'], 
                          capture_output=True, text=True, timeout=300)
    
    print("Exit code:", result.returncode)
    print("\n--- STDOUT ---")
    print(result.stdout)
    print("\n--- STDERR ---")
    print(result.stderr)
    
    if result.returncode == 0:
        print("\n✅ Compilation successful!")
    else:
        print("\n❌ Compilation failed!")
        
except subprocess.TimeoutExpired:
    print("❌ Compilation timed out!")
except Exception as e:
    print(f"❌ Error running compilation: {e}")