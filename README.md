# Orion Downloader

High-performance download manager for Android with C++ engine for maximum speed.

## Features

- 🚀 **High-Speed Downloads**: C++ engine with multi-threaded download support (up to 16 connections)
- 🎨 **Material You Design**: Modern UI with Material Design 3 and Dynamic Colors
- ⚡ **Efficient**: Optimized C++ code with `-O3` and fast-math optimizations
- 📊 **Real-time Progress**: Live download speed and progress tracking
- 🔄 **Pause/Resume**: Full control over your downloads
- 🎯 **Range Requests**: Automatic detection and use of parallel downloads when supported

## Architecture

### C++ Engine (`download_engine.cpp`)
- Multi-threaded download with configurable connections
- Socket-level implementation for maximum performance
- Range request support for parallel chunk downloads
- Optimized with `-O3`, `-ffast-math`, and pthread

### Kotlin Layer
- Modern MVVM architecture with Jetpack Compose
- Clean separation between UI and business logic
- Coroutines for asynchronous operations
- StateFlow for reactive UI updates

### JNI Bridge
- Efficient C++ ↔ Kotlin communication
- Progress callbacks from native code
- Thread-safe operations

## Technical Details

- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Language**: Kotlin 2.1.0 + C++17
- **UI Framework**: Jetpack Compose with Material3 1.3.1
- **Build System**: Gradle 8.9 with CMake 3.22.1
- **NDK**: Android NDK with C++ shared STL

## Building

### Prerequisites
- Android Studio Ladybug or later
- NDK installed
- JDK 17

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

### CI/CD

The project includes GitHub Actions workflow that:
- Builds both debug and release APKs
- Runs on push to main and pull requests
- Uploads artifacts for easy download

## Permissions

- `INTERNET` - For downloading files
- `ACCESS_NETWORK_STATE` - To check network connectivity
- `WRITE_EXTERNAL_STORAGE` - For Android 9 and below
- `READ_MEDIA_*` - For Android 13 and above
- `POST_NOTIFICATIONS` - For download notifications (Android 13+)

## Performance

The C++ engine is optimized for maximum speed:
- Direct socket operations (no high-level HTTP libraries)
- Parallel chunk downloads when server supports range requests
- Buffer size optimized at 64KB
- TCP_NODELAY enabled for reduced latency
- Compiler optimizations: `-O3 -ffast-math`

## Screenshots

*Coming soon*

## License

MIT License - feel free to use and modify

## Contributing

Pull requests are welcome! For major changes, please open an issue first.
