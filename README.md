# Orion Downloader

High-performance download manager for Android with hybrid Kotlin/C++ architecture and foreground service support.

## Features

- 🚀 **High-Speed Downloads**: Multi-threaded download engine with up to 16 parallel connections
- 🔐 **HTTP/HTTPS Support**: Full support for secure HTTPS downloads using Android's native SSL
- 🎨 **Material You Design**: Modern UI with Material Design 3 (v1.3.1) and Dynamic Colors
- ⚡ **Efficient**: Optimized Kotlin coroutines with thread-safe operations
- 📊 **Real-time Progress**: Live download speed, progress tracking, and notifications
- 🔔 **Background Downloads**: Foreground service with persistent notifications
- 🔄 **Pause/Resume**: Full control over your downloads
- 🎯 **Range Requests**: Automatic detection and use of parallel downloads when supported

## Architecture

### Hybrid Kotlin/C++ Approach
- **Kotlin** for HTTP/HTTPS connections using `HttpURLConnection` (native Android SSL support)
- **C++ (JNI)** available for future low-level optimizations
- **Coroutines** for asynchronous operations and parallel downloads
- **Foreground Service** for reliable background downloads

### Download Engine (`HttpDownloadEngine.kt`)
- Multi-threaded downloads with configurable connections
- Range request support for parallel chunk downloads
- AtomicLong for thread-safe progress tracking
- Real-time speed calculation
- Pause/Resume/Cancel functionality

### Foreground Service (`DownloadService.kt`)
- Persistent notifications with progress updates
- Survives app backgrounding
- Automatic cleanup when downloads complete
- Multiple simultaneous downloads supported

### UI Layer
- MVVM architecture with StateFlow
- Jetpack Compose with Material3
- Dynamic color theming (Android 12+)
- Reactive UI updates

## Technical Details

- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Language**: Kotlin 2.1.0 + C++17
- **UI Framework**: Jetpack Compose with Material3 1.3.1
- **Build System**: Gradle 8.9 with CMake 3.22.1
- **Concurrency**: Kotlin Coroutines 1.10.1

## Building

### Prerequisites
- Android Studio Ladybug or later
- Android NDK (automatically installed)
- JDK 17

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

### CI/CD

GitHub Actions workflow automatically:
- Builds both debug and release APKs
- Runs on push to main and pull requests
- Uploads artifacts for download

## Permissions

### Required
- `INTERNET` - For downloading files
- `ACCESS_NETWORK_STATE` - Network connectivity detection
- `FOREGROUND_SERVICE` - Background downloads
- `FOREGROUND_SERVICE_DATA_SYNC` - Data sync service type (Android 14+)

### Storage (API level dependent)
- `WRITE_EXTERNAL_STORAGE` - Android 9 and below
- `READ_MEDIA_*` - Android 13 and above

### Optional
- `POST_NOTIFICATIONS` - Download notifications (Android 13+)
- `WAKE_LOCK` - Prevent sleep during downloads

## Features in Detail

### Parallel Downloads
Configure 1-16 parallel connections per download:
- Automatically detects if server supports range requests
- Falls back to single connection if ranges not supported
- Each connection downloads a chunk independently
- Chunks are merged seamlessly into final file

### Background Downloads
- Downloads continue when app is minimized
- Persistent notification shows progress
- Completion notifications with auto-dismiss
- Service automatically stops when all downloads complete

### Progress Tracking
- Real-time bytes downloaded
- Live speed calculation (MB/s)
- Percentage completion
- Active connection count

## Screenshots

*Coming soon*

## Performance

Optimized for maximum speed:
- Parallel chunk downloads using Kotlin coroutines
- 8KB buffer size per connection
- AtomicLong for lock-free progress updates
- Native Android HTTPS (no overhead from custom SSL)
- Automatic connection pooling

## Known Limitations

- Download resume after app restart not yet implemented (coming soon)
- No download queue management (all start immediately)
- No bandwidth limiting options

## Future Enhancements

- [ ] SQLite persistence for download history
- [ ] Resume downloads after app restart
- [ ] Download queue with priority
- [ ] Bandwidth limiting
- [ ] Scheduled downloads
- [ ] Wi-Fi only mode

## License

MIT License - feel free to use and modify

## Contributing

Pull requests are welcome! For major changes, please open an issue first.
