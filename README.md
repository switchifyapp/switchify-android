# Switchify

An Android accessibility service that enables device control through adaptive switches, providing cursor-based and item scanning navigation for users with physical disabilities.

## Requirements

- Android 10 (API level 29) and above
- Android Studio (latest version recommended)

## Setup

### 1. Clone the repository
```bash
git clone https://github.com/enaboapps/Switchify.git
cd Switchify
```

### 2. Configure local.properties
Create a `local.properties` file in the project root with the following:

```properties
# Path to your Android SDK
sdk.dir=/path/to/your/Android/Sdk

# RevenueCat public API key (obtain from project owner)
revenuecat.publicKey=<ask_for_key>

# Amplitude API key (obtain from project owner)
amplitude.apiKey=<ask_for_key>

# Supabase configuration (obtain from project owner)
supabase.url=<ask_for_url>
supabase.anonKey=<ask_for_key>
```

### 3. Build and run
```bash
./gradlew build
```

## Contributing

1. Fork this repository
2. Clone your forked repository
3. Create a new branch (`git checkout -b feature/your-feature`)
4. Make your changes
5. Commit and push your changes
6. Create a pull request

### Git hooks

Enable repo-provided hooks to enforce commit message format:

```bash
git config core.hooksPath .githooks
chmod +x .githooks/commit-msg
```

## Links

- [Google Play Store](https://play.google.com/store/apps/details?id=com.enaboapps.switchify)

## Architecture: ServiceBridge

ServiceBridge provides a unified app↔service communication layer inside the main process.

- Scope: In‑process only. The accessibility service runs in the app process; commands/events flow via Kotlin Flows.
- Commands: `serviceCommands` has no replay (transient). UI triggers configuration and control commands through ViewModels.
- Events: `serviceEvents` uses a replay of 1 so late subscribers see the latest state; used by ViewModels to keep UI in sync.
- Rationale: Centralizes enforcement and settings handling in the service, avoids duplicating logic in UI.
- Future IPC: If the service moves out‑of‑process, prefer a bound service/Binder (or Messenger). If broadcasts are considered, protect with a signature‑level permission and strict input validation.
