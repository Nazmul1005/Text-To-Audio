#!/bin/bash
# Generate a signed release keystore for TextToAudio
# Run this script once to create the keystore for signing APKs
# Keep the keystore file safe - losing it means you can't update the app

set -e

KEYSTORE_FILE="keystore/release.keystore"
PASSWORD="texttoaudio2024"  # Change this to a strong password
ALIAS="release"
KEY_PASSWORD="texttoaudio2024"  # Change this to a strong password

echo "Generating keystore at: $KEYSTORE_FILE"

# Create keystore directory if needed
mkdir -p "$(dirname "$KEYSTORE_FILE")"

# Generate the keystore
keytool -genkey \
    -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=TextToAudio, OU=TextToAudio, O=TextToAudio, L=City, ST=State, C=US" \
    -sigalg SHA256withRSA

echo ""
echo "✅ Keystore generated successfully!"
echo ""
echo "⚠️  IMPORTANT: Keep this keystore file safe!"
echo "   - Anyone with this keystore can sign apps as you"
echo "   - Losing it means you can't update the app"
echo ""
echo "Next steps:"
echo "1. Copy keystore.properties.example to keystore.properties"
echo "2. Update the passwords in keystore.properties"
echo "3. Run: ./gradlew assembleRelease"
echo "4. The signed APK will be at: app/build/outputs/apk/release/app-release.apk"