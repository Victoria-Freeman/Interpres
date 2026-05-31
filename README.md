# Interpres

On-device translator + OCR. Uses ML Kit directly — no Google Play Services, no GMS, no telemetry. The only network request is downloading translation models from Google's CDN. Your text never leaves the device.

## Build

```bash
git clone --recurse-submodules https://github.com/Victoria-Freeman/Interpres
cd Interpres
./gradlew assembleDebug
```

Single-activity Android app. Java, ViewBinding, arm64-v8a, minSdk 24.
