# Eyevinn Video Analytics SDK

A comprehensive video player SDK for Android that combines ExoPlayer with built-in analytics tracking. This SDK simplifies media playback implementation while providing detailed analytics on user viewing behavior.

## Features

- **Integrated ExoPlayer**: Built-in video player with no additional setup required
- **Comprehensive Analytics**: Automatic tracking of key video metrics (play, pause, buffering, etc.)
- **Easy Integration**: Simple API for quick implementation
- **Performance Monitoring**: Track buffering, bitrate changes, and errors
- **Customizable Configuration**: Adjust settings to match your specific needs
- **Ready-to-use UI**: Includes configured PlayerView for easy integration into layouts

## Installation

### Gradle

Add the following to your project level `build.gradle`:

```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency to your app level `build.gradle`:

```gradle
dependencies {
    implementation 'com.eyevinn:video-analytics-sdk:1.0.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.eyevinn</groupId>
    <artifactId>video-analytics-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Permissions

Add the following permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Quick Start

### Basic Implementation

```kotlin
// Initialize SDK
val videoAnalyticsSDK = VideoAnalyticsSDK.Builder(context)
    .setContentTitle("My Video")
    .setEventSinkUrl("https://your-analytics-endpoint.com")
    .build()

// Add player view to your layout
layout.addView(videoAnalyticsSDK.playerView)

// Load and play media
videoAnalyticsSDK.loadMedia("https://example.com/video.m3u8")

// Start tracking analytics
videoAnalyticsSDK.startTracking()

// Remember to release resources when done
override fun onDestroy() {
    super.onDestroy()
    videoAnalyticsSDK.release()
}
```

### Jetpack Compose Implementation

```kotlin
@Composable
fun VideoPlayerScreen(videoAnalyticsSDK: VideoAnalyticsSDK) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { _ -> videoAnalyticsSDK.playerView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
```

## For Complete Documentation

See the [Usage Guide](USAGE.md) for detailed instructions and advanced configuration options.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
