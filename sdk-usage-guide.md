```markdown
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

- `VideoAnalyticsTracker`: The main class that encapsulates analytics functionality
- `AnalyticsEventSender`: Responsible for sending analytics events to your configured endpoint
- `AnalyticsEventType`: Enumeration of all possible video analytics event types

## Basic Implementation

### Initialization

The SDK can be initialized using the Builder pattern:

```kotlin
val exoPlayer = ExoPlayer.Builder(context).build()
val videoAnalyticsTracker = VideoAnalyticsTracker.Builder(exoPlayer)
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
exoPlayer.setMediaItem(MediaItem.fromUri("https://example.com/video.m3u8"))
exoPlayer.prepare()
exoPlayer.playWhenReady = true

// Or load without automatic playback
exoPlayer.setMediaItem(MediaItem.fromUri("https://example.com/video.m3u8"))
exoPlayer.prepare()
exoPlayer.playWhenReady = false
```

### Adding Player to Layout

```kotlin
// XML-based layouts
val playerView = PlayerView(context).apply {
    player = exoPlayer
}
container.addView(playerView)
```

### Jetpack Compose

```kotlin
@Composable
fun VideoPlayerScreen(playerView: PlayerView) {
    AndroidView(
        factory = { playerView },
        modifier = Modifier.fillMaxSize()
    )
}
```

### Complete Activity Example

```kotlin
class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var videoAnalyticsTracker: VideoAnalyticsTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        // Initialize ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build()

        // Initialize SDK
        videoAnalyticsTracker = VideoAnalyticsTracker.Builder(exoPlayer)
            .setContentTitle("My Video")
            .setIsLive(false)
            .setEventSinkUrl("https://your-analytics-endpoint.com")
            .build()

        // Add player view to layout
        val playerView = PlayerView(this).apply {
            player = exoPlayer
        }
        val playerContainer = findViewById<FrameLayout>(R.id.player_container)
        playerContainer.addView(playerView)

        // Load media
        val mediaUrl = "https://example.com/video.m3u8"
        exoPlayer.setMediaItem(MediaItem.fromUri(mediaUrl))
        exoPlayer.prepare()
    }

    override fun onStart() {
        super.onStart()
        videoAnalyticsTracker.startTracking()
        exoPlayer.play()
    }

    override fun onStop() {
        super.onStop()
        videoAnalyticsTracker.stopTracking("User left the app")
        exoPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        videoAnalyticsTracker.release()
        exoPlayer.release()
    }
}
```

## Configuration Options

The `VideoAnalyticsTracker.Builder` class provides several configuration options:

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
exoPlayer.play()

// Pause
exoPlayer.pause()

// Seek to position (in milliseconds)
exoPlayer.seekTo(60000) // Seek to 1 minute
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
    videoAnalyticsTracker.startTracking()
    exoPlayer.play() // Optional, if you want to auto-resume
}

// Stop tracking when your activity/fragment is no longer visible
override fun onStop() {
    exoPlayer.pause() // Optional, if you want to auto-pause
    videoAnalyticsTracker.stopTracking("User left the app")
    super.onStop()
}

// Release resources when your activity/fragment is destroyed
override fun onDestroy() {
    videoAnalyticsTracker.release()
    exoPlayer.release()
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
val playerView = PlayerView(context).apply {
    player = exoPlayer
}
val playerContainer = findViewById<FrameLayout>(R.id.player_container)
playerContainer.addView(playerView)
```

### Jetpack Compose

```kotlin
@Composable
fun VideoPlayerScreen(playerView: PlayerView) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { playerView },
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
videoAnalyticsTracker.sendCustomEvent(
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
exoPlayer.addListener(object : Player.Listener {
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
val exoPlayer = videoAnalyticsTracker.player

// Apply custom configuration
exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
exoPlayer.volume = 0.5f
```

### Custom PlayerView Styling

```kotlin
// Access the PlayerView
val playerView = PlayerView(context).apply {
    player = exoPlayer
}

// Apply custom styling
playerView.useController = false  // Hide the default controls
playerView.controllerShowTimeoutMs = 3000  // Show controls for 3 seconds
playerView.setShutterBackgroundColor(Color.BLACK)
```

## Sample Applications

### Basic Player

```kotlin
class BasicPlayerActivity : AppCompatActivity() {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var videoAnalyticsTracker: VideoAnalyticsTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_basic_player)

        // Initialize ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build()

        // Initialize SDK
        videoAnalyticsTracker = VideoAnalyticsTracker.Builder(exoPlayer)
            .setContentTitle("Sample Video")
            .setEventSinkUrl("https://analytics.example.com/events")
            .build()

        // Add player view to layout
        val playerView = PlayerView(this).apply {
            player = exoPlayer
        }
        findViewById<FrameLayout>(R.id.player_container).addView(playerView)

        // Load media
        exoPlayer.setMediaItem(MediaItem.fromUri("https://example.com/video.m3u8"))
        exoPlayer.prepare()
    }

    override fun onStart() {
        super.onStart()
        videoAnalyticsTracker.startTracking()
        exoPlayer.play()
    }

    override fun onStop() {
        super.onStop()
        videoAnalyticsTracker.stopTracking("User navigated away")
        exoPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        videoAnalyticsTracker.release()
        exoPlayer.release()
    }
}
```

### Compose Player

```kotlin
class ComposePlayerActivity : ComponentActivity() {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var videoAnalyticsTracker: VideoAnalyticsTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build()

        // Initialize SDK
        videoAnalyticsTracker = VideoAnalyticsTracker.Builder(exoPlayer)
            .setContentTitle("Compose Video")
            .setEventSinkUrl("https://analytics.example.com/events")
            .build()

        // Load media
        exoPlayer.setMediaItem(MediaItem.fromUri("https://example.com/video.m3u8"))
        exoPlayer.prepare()

        setContent {
            MaterialTheme {
                VideoPlayerScreen(PlayerView(this).apply {
                    player = exoPlayer
                })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        videoAnalyticsTracker.startTracking()
        exoPlayer.play()
    }

    override fun onStop() {
        super.onStop()
        videoAnalyticsTracker.stopTracking("User navigated away")
        exoPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        videoAnalyticsTracker.release()
        exoPlayer.release()
    }
}

@Composable
fun VideoPlayerScreen(playerView: PlayerView) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { playerView },
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
```