```markdown
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
// Initialize ExoPlayer
val exoPlayer = ExoPlayer.Builder(context).build()

// Initialize SDK
val videoAnalyticsTracker = VideoAnalyticsTracker.Builder(exoPlayer)
    .setContentTitle("My Video")
    .setEventSinkUrl("https://your-analytics-endpoint.com")
    .build()

// Add player view to your layout
val playerView = PlayerView(context).apply {
    player = exoPlayer
}
layout.addView(playerView)

// Load and play media
exoPlayer.setMediaItem(MediaItem.fromUri("https://example.com/video.m3u8"))
exoPlayer.prepare()
exoPlayer.playWhenReady = true

// Start tracking analytics
videoAnalyticsTracker.startTracking()

// Remember to release resources when done
override fun onDestroy() {
    super.onDestroy()
    videoAnalyticsTracker.release()
    exoPlayer.release()
}
```

### Jetpack Compose Implementation

```kotlin
@Composable
fun VideoPlayerScreen(exoPlayer: ExoPlayer, videoAnalyticsTracker: VideoAnalyticsTracker) {
    val playerView = remember {
        PlayerView(LocalContext.current).apply {
            player = exoPlayer
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            videoAnalyticsTracker.release()
            exoPlayer.release()
        }
    }
}
```

## For Complete Documentation

See the [Usage Guide](USAGE.md) for detailed instructions and advanced configuration options.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
```