# Eyevinn Video Analytics SDK - Usage Guide

This comprehensive guide covers everything you need to know about implementing and using the Eyevinn Video Analytics SDK in your Android applications.

## Table of Contents

1. [SDK Overview](#sdk-overview)
2. [Basic Implementation](#basic-implementation)
3. [Configuration Options](#configuration-options)
4. [Playback Controls](#playback-controls)
5. [Analytics Events](#analytics-events)
6. [Lifecycle Management](#lifecycle-management)
7. [UI Integration](#ui-integration)
8. [Custom Events](#custom-events)
9. [Error Handling](#error-handling)
10. [Advanced Configuration](#advanced-configuration)
11. [Sample Applications](#sample-applications)
12. [Troubleshooting](#troubleshooting)

## SDK Overview

The Eyevinn Video Analytics SDK is designed to simplify media playback implementation in Android applications while providing detailed analytics on user viewing behavior. The SDK integrates ExoPlayer for playback and includes a comprehensive analytics tracking system, allowing developers to monitor how users interact with video content.

### Key Components

- `VideoAnalyticsSDK`: The main class that encapsulates ExoPlayer and analytics functionality
- `AnalyticsEventSender`: Responsible for sending analytics events to your configured endpoint
- `AnalyticsEventType`: Enumeration of all possible video analytics event types

## Basic Implementation

### Initialization

The SDK can be initialized using the Builder pattern:

```kotlin
val videoAnalyticsSDK = VideoAnalyticsSDK.Builder(context)
    .setContentTitle("My Video")
    .setIsLive(false)
    .setEventSinkUrl("https://your-analytics-endpoint.com")
    .setDeviceType("Android App")
    .setHeartbeatInterval(30_000L)
    .build()
```

### Playing Media

```kotlin
// Load and automatically play media
videoAnalyticsSDK.loadMedia("https://example.com/video.m3u8")

// Or load without automatic playback
videoAnalyticsSDK.loadMedia("https://example.com/video.m3u8", autoPlay = false)
```

### Adding Player to Layout

```kotlin
// XML-based layouts
container.addView(videoAnalyticsSDK.playerView)
```

### Jetpack Compose

```kotlin
@Composable
fun VideoPlayerScreen(videoAnalyticsSDK: VideoAnalyticsSDK) {
    AndroidView(
        factory = { _ -> videoAnalyticsSDK.playerView },
        modifier = Modifier.fillMaxSize()
    )
}
```

### Complete Activity Example

```kotlin
class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var videoAnalyticsSDK: VideoAnalyticsSDK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        // Initialize SDK
        videoAnalyticsSDK = VideoAnalyticsSDK.Builder(this)
            .setContentTitle("My Video")
            .setIsLive(false)
            .setEventSinkUrl("https://your-analytics-endpoint.com")
            .build()

        // Add player view to layout
        val playerContainer = findViewById<FrameLayout>(R.id.player_container)
        playerContainer.addView(videoAnalyticsSDK.playerView)

        // Load media
        val mediaUrl = "https://example.com/video.m3u8"
        videoAnalyticsSDK.loadMedia(mediaUrl)
    }

    override fun onStart() {
        super.onStart()
        videoAnalyticsSDK.startTracking()
    }

    override fun onStop() {
        super.onStop()
        videoAnalyticsSDK.stopTracking("User left the app")
    }

    override fun onDestroy() {
        super.onDestroy()
        videoAnalyticsSDK.release()
    }
}
```

## Configuration Options

The `VideoAnalyticsSDK.Builder` class provides several configuration options:

| Method | Description | Default Value |
|--------|-------------|---------------|
| `setEventSinkUrl(url)` | Sets the URL to which analytics events will be sent | `https://default.url` |
| `setContentTitle(title)` | Sets the title of the content being played | `null` |
| `setIsLive(isLive)` | Indicates whether the content is a live stream | `false` |
| `setDeviceType(deviceType)` | Sets the device type identifier | `"Android player"` |
| `setHeartbeatInterval(intervalMs)` | Sets the interval for heartbeat events in milliseconds | `30000` (30 seconds) |

## Playback Controls

The SDK provides simple methods to control media playback:

```kotlin
// Play
videoAnalyticsSDK.play()

// Pause
videoAnalyticsSDK.pause()

// Seek to position (in milliseconds)
videoAnalyticsSDK.seekTo(60000) // Seek to 1 minute
```

## Analytics Events

The SDK automatically tracks and sends the following events:

| Event | Description | When Triggered |
|-------|-------------|----------------|
| `init` | Initial event when SDK is initialized | On initialization |
| `metadata` | Content metadata | On initialization |
| `loading` | Content begins loading | When media starts loading |
| `loaded` | Content has loaded | When media is ready to play |
| `playing` | Content begins playing | When playback starts |
| `paused` | Content is paused | When playback is paused |
| `buffering` | Content begins buffering | During buffering |
| `buffered` | Content finishes buffering | After buffering completes |
| `seeking` | User begins seeking | When seek operation starts |
| `seeked` | Seeking is completed | When seek operation ends |
| `bitrate_changed` | Video bitrate changes | When video quality changes |
| `stopped` | Playback stops | When playback ends or is stopped |
| `error` | An error occurs | When playback encounters an error |
| `heartbeat` | Regular heartbeat | At configured interval during playback |

## Lifecycle Management

To ensure proper resource management, follow these lifecycle patterns:

```kotlin
// Start tracking when your activity/fragment becomes visible
override fun onStart() {
    super.onStart()
    videoAnalyticsSDK.startTracking()
    videoAnalyticsSDK.play() // Optional, if you want to auto-resume
}

// Stop tracking when your activity/fragment is no longer visible
override fun onStop() {
    videoAnalyticsSDK.pause() // Optional, if you want to auto-pause
    videoAnalyticsSDK.stopTracking("User left the app")
    super.onStop()
}

// Release resources when your activity/fragment is destroyed
override fun onDestroy() {
    videoAnalyticsSDK.release()
    super.onDestroy()
}
```

## UI Integration

### XML Layouts

```xml
<FrameLayout
    android:id="@+id/player_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

```kotlin
val playerContainer = findViewById<FrameLayout>(R.id.player_container)
playerContainer.addView(videoAnalyticsSDK.playerView)
```

### Jetpack Compose

```kotlin
@Composable
fun VideoPlayerScreen(videoAnalyticsSDK: VideoAnalyticsSDK) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { _ -> videoAnalyticsSDK.playerView },
            modifier = Modifier.fillMaxSize()
        )
        
        // Additional UI elements can be added here
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            // Custom controls, info, etc.
        }
    }
}
```

## Custom Events

You can send custom events when needed:

```kotlin
// Send a custom event
videoAnalyticsSDK.sendCustomEvent(
    eventType = "custom_event_name",
    payload = JSONObject().apply {
        put("key1", "value1")
        put("key2", "value2")
    }
)
```

## Error Handling

The SDK automatically tracks playback errors, but you can also listen for them:

```kotlin
videoAnalyticsSDK.player.addListener(object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
        // Handle error
        Log.e("PlayerError", "An error occurred: ${error.message}")
    }
})
```

## Advanced Configuration

### Custom ExoPlayer Configuration

While the SDK creates and manages the ExoPlayer instance, you can still access it for advanced configuration:

```kotlin
// Access the ExoPlayer instance
val exoPlayer = videoAnalyticsSDK.player

// Apply custom configuration
exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
exoPlayer.volume = 0.5f
```

### Custom PlayerView Styling

```kotlin
// Access the PlayerView
val playerView = videoAnalyticsSDK.playerView

// Apply custom styling
playerView.useController = false  // Hide the default controls
playerView.controllerShowTimeoutMs = 3000  // Show controls for 3 seconds
playerView.setShutterBackgroundColor(Color.BLACK)
```

## Sample Applications

### Basic Player

```kotlin
class BasicPlayerActivity : AppCompatActivity() {
    private lateinit var videoAnalyticsSDK: VideoAnalyticsSDK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_basic_player)

        // Initialize SDK
        videoAnalyticsSDK = VideoAnalyticsSDK.Builder(this)
            .setContentTitle("Sample Video")
            .setEventSinkUrl("https://analytics.example.com/events")
            .build()

        // Add player view to layout
        findViewById<FrameLayout>(R.id.player_container).addView(videoAnalyticsSDK.playerView)

        // Load media
        videoAnalyticsSDK.loadMedia("https://example.com/video.m3u8")
    }

    override fun onStart() {
        super.onStart()
        videoAnalyticsSDK.startTracking()
    }

    override fun onStop() {
        super.onStop()
        videoAnalyticsSDK.stopTracking("User navigated away")
    }

    override fun onDestroy() {
        super.onDestroy()
        videoAnalyticsSDK.release()
    }
}
```

### Compose Player

```kotlin
class ComposePlayerActivity : ComponentActivity() {
    private lateinit var videoAnalyticsSDK: VideoAnalyticsSDK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SDK
        videoAnalyticsSDK = VideoAnalyticsSDK.Builder(this)
            .setContentTitle("Compose Video")
            .setEventSinkUrl("https://analytics.example.com/events")
            .build()

        // Load media
        videoAnalyticsSDK.loadMedia("https://example.com/video.m3u8")

        setContent {
            MaterialTheme {
                VideoPlayerScreen(videoAnalyticsSDK)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        videoAnalyticsSDK.startTracking()
    }

    override fun onStop() {
        super.onStop()
        videoAnalyticsSDK.stopTracking("User navigated away")
    }

    override fun onDestroy() {
        super.onDestroy()
        videoAnalyticsSDK.release()
    }
}

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

## Troubleshooting

### Common Issues

1. **No video playback**: Ensure the media URL is correct and the device has internet permissions.

   ```kotlin
   // Check if URL is valid
   try {
       URL(mediaUrl)
   } catch (e: MalformedURLException) {
       Log.e("VideoSDK", "Invalid URL: $mediaUrl")
   }
   ```

2. **Analytics events not sending**: Verify the event sink URL is correct and accessible.

   ```kotlin
   // Test event sink connectivity
   Thread {
       try {
           val connection = URL(eventSinkUrl).openConnection() as HttpURLConnection
           connection.requestMethod = "HEAD"
           val responseCode = connection.responseCode
           Log.d("VideoSDK", "Event sink response code: $responseCode")
           connection.disconnect()
       } catch (e: Exception) {
           Log.e("VideoSDK", "Cannot connect to event sink: ${e.message}")
       }
   }.start()
   ```

3. **Memory leaks**: Ensure proper lifecycle management by calling `release()` when done.

### Logging

Enable detailed logging for troubleshooting:

```kotlin
// Enable verbose logging (typically in Application class)
if (BuildConfig.DEBUG) {
    Logger.setLogLevel(Logger.LOG_LEVEL_VERBOSE)
}
```

For more assistance, please contact Eyevinn support or submit an issue on the GitHub repository.
