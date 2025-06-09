# Switchify

An Android accessibility service that enables device control through adaptive switches, providing cursor-based and item scanning navigation for users with physical disabilities.

## Requirements

- Android 12 (API level 31) and above
- Android Studio (latest version recommended)

## Setup

### 1. Clone the repository
```bash
git clone https://github.com/yourusername/Switchify.git
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
```

### 3. Add Firebase configuration
Obtain the `google-services.json` file from the project owner and place it in the `app/` directory.

### 4. Build and run
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

## Links

- [Google Play Store](https://play.google.com/store/apps/details?id=com.enaboapps.switchify)