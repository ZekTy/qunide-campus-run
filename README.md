# 去你的校园跑

Android mock-location route simulator with an optional NFC Alipay launch helper and Cloudflare Worker based activation flow.

## Notice

This project is published for technical discussion, learning, research, and authorized testing only. Do not use it for illegal activity, platform abuse, violating third-party rights, or disrupting services. You are responsible for your own use and consequences.

## Configuration

Copy `local.properties.example` to `local.properties`:

```properties
sdk.dir=C:\\Android\\Sdk
AMAP_API_KEY=replace-with-your-amap-key
GOOGLE_MAPS_API_KEY=replace-with-your-google-maps-key
```

Set activation configuration in `gradle.properties` or pass Gradle properties at build time:

```properties
activationBaseUrl=https://your-worker.example.workers.dev
telegramBotUsername=your_bot_username
```

Never commit real API keys, signing keys, APKs, or `local.properties`.

## Build

```powershell
.\\gradlew.bat testDebugUnitTest assembleDebug
```

For release builds, create and store your own Android signing key outside the repository. Future app updates must be signed with the same key.

## Worker

The activation backend is published separately as `nfc-activation-worker`. Deploy it first, then configure `activationBaseUrl` and `telegramBotUsername` here.

## License

GPL-3.0. See `LICENSE`.
