# Eyevinn Player Analytics Client SDK for Android

A comprehensive video player SDK for Android that combines ExoPlayer with built-in analytics tracking and SGAI (Server-Guided Ad Insertion) ad tracking capabilities. This SDK simplifies media playback implementation while providing detailed analytics on user viewing behavior and comprehensive ad tracking.

## Features

- **Unified Video Analytics Tracker**: Single class handles both video analytics and SGAI ad tracking
- **Integrated ExoPlayer**: Built-in video player with no additional setup required
- **Comprehensive Analytics**: Automatic tracking of key video metrics (play, pause, buffering, etc.)
- **SGAI Ad Tracking**: Complete Server-Guided Ad Insertion tracking with automatic ad detection
- **Live and VOD SGAI Support**: Works with streams using `X-ASSET-LIST` or `X-ASSET-URI` for ad signaling, as per the HLS SGAI standard.
- **Easy Integration**: Simple API for quick implementation
- **Performance Monitoring**: Track buffering, bitrate changes, and errors
- **VAST Support**: Parse VAST XML for comprehensive ad tracking
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
    implementation 'com.github.Eyevinn:player-analytics-client-sdk-android:1.0.0'
    implementation 'androidx.media3:media3-exoplayer:1.8.0-alpha01'
    implementation 'androidx.media3:media3-ui:1.8.0-alpha01'
    implementation 'androidx.media3:media3-exoplayer-hls:1.8.0-alpha01'
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.0'
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
    <groupId>com.github.Eyevinn</groupId>
    <artifactId>player-analytics-client-sdk-android</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Permissions

Add the following permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---
## Section 1: Video Analytics Tracking (No Ads)

Track comprehensive video playback analytics including user engagement, performance metrics, and viewing behavior.

### Quick Start - Video Analytics Only

#### Basic Implementation

```kotlin
// Initialize ExoPlayer
val exoPlayer = ExoPlayer.Builder(context).build()

// Initialize Video Analytics Tracker (no ads)
val videoAnalyticsTracker = VideoAnalyticsTracker.Builder(context, exoPlayer)
    .setEventSinkUrl("https://your-analytics-endpoint.com")
    .setContentTitle("My Video")
    .setDeviceType("Android Sample App")
    .setHeartbeatInterval(30_000L)
    // Note: SGAI tracking is disabled by default
    .build()

// Set media item
videoAnalyticsTracker.setMainMediaItem("https://example.com/video.m3u8")

// Add player view to your layout
val playerView = PlayerView(context).apply {
    player = exoPlayer
}
layout.addView(playerView)

// Start tracking analytics
videoAnalyticsTracker.startTracking()

// Start playback
exoPlayer.play()

// Remember to release resources when done
override fun onDestroy() {
    super.onDestroy()
    videoAnalyticsTracker.release()
    exoPlayer.release()
}
```

#### Jetpack Compose Implementation

```kotlin
@Composable
fun VideoPlayerScreen() {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    val videoAnalyticsTracker = remember {
        VideoAnalyticsTracker.Builder(context, exoPlayer)
            .setEventSinkUrl("https://your-analytics-endpoint.com")
            .setContentTitle("Sample Video")
            .build()
    }
    
    val playerView = remember {
        PlayerView(context).apply {
            player = exoPlayer
        }
    }

    LaunchedEffect(Unit) {
        videoAnalyticsTracker.setMainMediaItem("https://example.com/video.m3u8")
        videoAnalyticsTracker.startTracking()
        exoPlayer.play()
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

### Analytics Events Tracked

- **Playback Events**: Play, pause, stop, seek
- **Performance Metrics**: Buffering events, bitrate changes
- **User Engagement**: Watch time, completion rates
- **Error Tracking**: Playback failures and network issues
- **Quality Metrics**: Video resolution, audio quality changes
- **Heartbeat**: Periodic tracking events (configurable interval)

---

## Section 2: SGAI Ad Tracking + Video Analytics

Track both Server-Guided Ad Insertion ads and comprehensive video analytics using the unified VideoAnalyticsTracker.

### Quick Start - SGAI + Video Analytics

#### Basic SGAI Implementation

```kotlin
class SGAIPlayerActivity : ComponentActivity() {
    private lateinit var videoAnalyticsTracker: VideoAnalyticsTracker
    private lateinit var playerView: PlayerView
    
    private val sgaiStreamUrl = "http://10.0.2.2:3333/x36xhzz/x36xhzz.m3u8"
    private val eventSinkUrl = "https://your-analytics-endpoint.com"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create ExoPlayer instance
        val player = ExoPlayer.Builder(this).build()
        
        // Create unified VideoAnalyticsTracker with SGAI ad tracking enabled
        videoAnalyticsTracker = VideoAnalyticsTracker.Builder(this, player)
            .setEventSinkUrl(eventSinkUrl)
            .setContentTitle("SGAI Live Stream")
            .setDeviceType("Android SGAI Player")
            .setHeartbeatInterval(30_000L)
            .enableSGAIAdTracking(true)  // Enable SGAI ad tracking
            .build()
        
        // Create PlayerView and set the player
        playerView = PlayerView(this).apply {
            this.player = player
        }
        
        // Set the player view container for ad rendering (required for SGAI)
        videoAnalyticsTracker.setPlayerViewContainer(playerView)
        
        // Set the main media item with SGAI support
        videoAnalyticsTracker.setMainMediaItem(sgaiStreamUrl)
        
        setContent {
            MaterialTheme {
                SGAIVideoPlayerScreen(playerView)
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        // Start unified tracking (handles both video analytics and SGAI ad tracking)
        videoAnalyticsTracker.startTracking()
        // Start playback
        playerView.player?.let { player ->
            player.playWhenReady = true
            player.play()
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Pause playback
        playerView.player?.pause()
        // Stop tracking with reason
        videoAnalyticsTracker.stopTracking("User left the app")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Release all resources
        videoAnalyticsTracker.release()
        playerView.player?.release()
    }
}
```

#### Simple Video Analytics (No SGAI)

```kotlin
class SimplePlayerActivity : ComponentActivity() {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var videoAnalyticsTracker: VideoAnalyticsTracker
    private lateinit var playerView: PlayerView

    private val assetUrl = "https://example.com/video.m3u8"
    private val eventSinkUrl = "https://your-analytics-endpoint.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create ExoPlayer instance
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            playWhenReady = false
        }

        // Create VideoAnalyticsTracker with SGAI tracking disabled (default)
        videoAnalyticsTracker = VideoAnalyticsTracker.Builder(this, exoPlayer)
            .setEventSinkUrl(eventSinkUrl)
            .setContentTitle("Sample Video")
            .setDeviceType("Android Sample App")
            .setHeartbeatInterval(30_000L)
            // Note: enableSGAIAdTracking(false) is the default
            .build()

        // Set the main media item (simple video, no ads)
        videoAnalyticsTracker.setMainMediaItem(assetUrl)

        // Create PlayerView
        playerView = PlayerView(this).apply {
            player = exoPlayer
        }

        setContent {
            MaterialTheme {
                VideoPlayerScreen(playerView)
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
        exoPlayer.pause()
        videoAnalyticsTracker.stopTracking("User left the app")
    }

    override fun onDestroy() {
        super.onDestroy()
        videoAnalyticsTracker.release()
        exoPlayer.release()
    }
}
```

### SGAI Ad Events Tracked

The SDK automatically tracks these ad events when SGAI tracking is enabled:

| Event | Description | Trigger |
|-------|-------------|---------|
| `impression` | Ad view started | When ad begins playing (onStart) |
| `start` | Ad playback started | Same as impression (onStart) |
| `firstQuartile` | 25% of ad played | At 25% progress |
| `midpoint` | 50% of ad played | At 50% progress |
| `thirdQuartile` | 75% of ad played | At 75% progress |
| `complete` | Ad fully played | When ad completes (onAssetListLoadCompleted) |
| `pause` | Ad playback paused | When user pauses during ad |
| `resume` | Ad playback resumed | When user resumes after pause |
| `podStart` | Ad pod started | First ad in a group |
| `podEnd` | Ad pod ended | Last ad in a group |

### Advanced SGAI Usage

#### Custom Tracking URLs

```kotlin
// Set custom tracking URLs for specific ads
val trackingUrls = mapOf(
    "impression" to listOf("https://tracking.example.com/impression"),
    "firstQuartile" to listOf("https://tracking.example.com/q1"),
    "midpoint" to listOf("https://tracking.example.com/mid"),
    "thirdQuartile" to listOf("https://tracking.example.com/q3"),
    "complete" to listOf("https://tracking.example.com/complete")
)

videoAnalyticsTracker.setAdTrackingUrls("ad-session-1_0_0", trackingUrls)
```

#### Retrieve Tracking Data

```kotlin
// Get tracking URLs for a specific ad
val adKey = "ad-session-1_0_0"
val trackingUrls = videoAnalyticsTracker.getTrackingUrlsForAd(adKey)
```

#### Send Custom Events

```kotlin
// Send custom analytics event
videoAnalyticsTracker.sendCustomEvent("CUSTOM_EVENT_TYPE")

```

#### SGAI Ad Event Support

```kotlin

Live and VOD streams are handled using #EXT-X-DATERANGE with either X-ASSET-LIST or X-ASSET-URI.

The SDK automatically detects and fetches asset lists for either type, extracting tracking URLs for events such as impression, start, quartiles, complete, pause/resume, pod events, and more.
``` 

### SGAI Stream Requirements
```

**HLS stream should use standard SGAI cues for ad breaks and reference asset lists via X-ASSET-LIST:**

```m3u8
#EXTINF:6.0,
segment1.ts
#EXT-X-DATERANGE:ID="ad-break-1",X-ASSET-LIST="https://your-server.com/ads.json"
#EXTINF:6.0,
segment2.ts
```

**Alternative format with X-ASSET-URI (VoD SGAI):**
```m3u8
#EXT-X-DATERANGE:ID="ad-break-1",X-ASSET-URI="https://your-server.com/ads.json"
```

The asset list should return JSON with tracking information:

```json
{
  "ASSETS": [
    {
      "URI": "ad-creative.mp4",
      "X-AD-CREATIVE-SIGNALING": {
        "payload": {
          "tracking": [
            {
              "type": "impression",
              "urls": ["https://tracking.example.com/impression"]
            },
            {
              "type": "start", 
              "urls": ["https://tracking.example.com/start"]
            },
            {
              "type": "firstQuartile",
              "urls": ["https://tracking.example.com/firstQuartile"]
            },
            {
              "type": "midpoint",
              "urls": ["https://tracking.example.com/midpoint"]
            },
            {
              "type": "thirdQuartile", 
              "urls": ["https://tracking.example.com/thirdQuartile"]
            },
            {
              "type": "complete",
              "urls": ["https://tracking.example.com/complete"]
            },
            {
              "type": "pause",
              "urls": ["https://tracking.example.com/pause"]
            },
            {
              "type": "resume",
              "urls": ["https://tracking.example.com/resume"]
            }
          ]
        }
      }
    }
  ],
  "X-AD-CREATIVE-SIGNALING": {
    "payload": {
      "tracking": [
        {
          "type": "podStart",
          "urls": ["https://tracking.example.com/podStart"]
        },
        {
          "type": "podEnd", 
          "urls": ["https://tracking.example.com/podEnd"]
        }
      ]
    }
  }
}
```

### Configuration Options

The `VideoAnalyticsTracker.Builder` supports these configuration options:

```kotlin
VideoAnalyticsTracker.Builder(context, player)
    .setEventSinkUrl("https://analytics.example.com")     // Analytics endpoint
    .setContentTitle("My Content")                        // Content title
    .setDeviceType("Android TV")                         // Device identifier
    .setHeartbeatInterval(30_000L)                       // Heartbeat interval in ms
    .enableSGAIAdTracking(true)                          // Enable/disable SGAI
    .build()
```

### Network Configuration

The SDK expects your ad server to be accessible. For Android emulator development:
- Use `10.0.2.2` instead of `localhost`
- Ensure your ad server is running and accessible
- **Ad Format Requirements**: Use fragmented MP4 ads only (regular MP4 files will cause `FragmentedMp4Extractor` NullPointerException)

### Error Handling & Debugging

The SDK includes built-in error handling and logging. Monitor logs with these tags:
- `VideoAnalyticsTracker` - Main tracker events
- `SGAIAdTrackingUrlsExtractor` - Manifest monitoring and URL extraction
- `SGAIAdImpressionSender` - Tracking request status
- `TrackingEvent` - Ad tracking events
- `PodTracking` - Ad pod events

#### Debug Methods

```kotlin
// Debug current ad tracking state
private fun debugAdState() {
    Log.d("DEBUG", "Is playing ad: ${player.isPlayingAd}")
    Log.d("DEBUG", "Ad group index: ${player.currentAdGroupIndex}")
    Log.d("DEBUG", "Ad index in group: ${player.currentAdIndexInAdGroup}")
}

// Debug tracking URLs for specific ad
private fun debugAdTracking(adKey: String) {
    val trackingUrls = videoAnalyticsTracker.getTrackingUrlsForAd(adKey)
    if (trackingUrls != null) {
        Log.d("DEBUG", "Tracking URLs for ad $adKey: $trackingUrls")
    } else {
        Log.d("DEBUG", "No tracking URLs found for ad $adKey")
    }
}
```

## API Reference

### VideoAnalyticsTracker

#### Builder Methods
- `setEventSinkUrl(url: String)` - Set analytics endpoint
- `setContentTitle(title: String?)` - Set content title
- `setDeviceType(deviceType: String)` - Set device identifier
- `setHeartbeatInterval(intervalMs: Long)` - Set heartbeat interval
- `enableSGAIAdTracking(enable: Boolean)` - Enable/disable SGAI tracking

#### Main Methods
- `setMainMediaItem(streamUrl: String)` - Set media source
- `setPlayerViewContainer(container: ViewGroup)` - Set ad container (SGAI only)
- `startTracking()` - Start analytics tracking
- `stopTracking(reason: String)` - Stop tracking with reason
- `release()` - Release all resources
- `sendCustomEvent(eventType: String, payload: JSONObject?)` - Send custom event

#### SGAI Methods
- `setAdTrackingUrls(adKey: String, trackingMap: Map<String, List<String>>)` - Set tracking URLs
- `getTrackingUrlsForAd(adKey: String): Map<String, List<String>>?` - Get tracking URLs

## Migration Guide

### From Separate SGAIAdTracker to Unified VideoAnalyticsTracker

**Old approach (separate classes):**
```kotlin
val adTracker = SGAIAdTracker(context)
val analyticsManager = AnalyticsManager(adTracker.player, eventSinkUrl)
```

**New approach (unified class):**
```kotlin
val player = ExoPlayer.Builder(context).build()
val videoAnalyticsTracker = VideoAnalyticsTracker.Builder(context, player)
    .setEventSinkUrl(eventSinkUrl)
    .enableSGAIAdTracking(true)
    .build()
```

## Sample Implementation

See `SGAIPlayerActivity.kt` and `SimplePlayerActivity.kt` for complete implementation examples using both analytics and SGAI ad tracking with Jetpack Compose.

## Requirements

- Android API 21+
- ExoPlayer 1.8.0-alpha01+ (required for SGAI functionality)
- Kotlin Coroutines support


### Debug Logs to Monitor

**Success indicators:**
```
"Ad started (onStart): ad-session-1_0_0"
"Ad completed (onAssetListLoadCompleted): ad-session-1_0_0"
"Sending impression event for ad ad-session-1_0_0 with 1 URLs"
"TrackingDebug: Parsed adTrackingUrlsMap"
```

**Error indicators:**
```
"Failed to extract tracking URLs"
"No tracking URLs found for event"
```


##  Current Limitations & Requirements

### **ExoPlayer Version Compatibility**

**This SDK is designed for ExoPlayer version 1.8.0-alpha01 and later versions.**


**SGAI Ad Tracking Implementation Details**

The SDK supports both live and VOD SGAI streams.

Current status: All ad tracking URLs are fetched and parsed by the SDK, since ExoPlayer does not provide them directly.

Future status: Once ExoPlayer supports ad tracking events, the SDK will use ExoPlayer’s built-in features.

---


**Important Note for Live Streams**

Live Stream Monitoring: The current implementation processes the manifest only once when processManifest() or processManifestAsync() is called.
For Live Streams, you need to implement periodic monitoring of the manifest to detect new ad breaks as they appear in real-time. Consider:

Setting up a timer or scheduled task to call processManifest() periodically (e.g., every 15-30 seconds)
Monitoring manifest updates based on your streaming protocol requirements
Implementing manifest change detection to avoid unnecessary processing

For VoD (Video on Demand), the single manifest processing is sufficient as ad breaks are pre-defined.

**Important: onPositionDiscontinuity in ExoPlayer**

️ExoPlayer Integration Requirement: When integrating SGAI ad tracking with ExoPlayer, the onPositionDiscontinuity listener is ESSENTIAL and cannot be removed.
Why onPositionDiscontinuity is important:

Ad Transition Detection: ExoPlayer uses position discontinuities to signal transitions between content and ads. This is the primary mechanism to detect when an ad starts or ends.
Tracking URL Synchronization: The tracking URLs extracted from the manifest are only synchronized to the main tracker when ad events are triggered, which happens through onPositionDiscontinuity.
Event Chain Trigger:
onPositionDiscontinuity → sendTrackingEvent() → syncTrackingUrls() → URLs available for tracking

Ad State Management: It manages ad keys, pod tracking, and quartile progression that are essential for proper SGAI ad analytics.

Without onPositionDiscontinuity:

- Tracking URLs remain in the extractor but are never synchronized to the main tracker
- No ad impression, start, or complete events are sent
- Pod start/end events are not triggered
- SGAI ad tracking completely fails

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For issues and questions, please visit our [GitHub repository](https://github.com/Eyevinn/player-analytics-client-sdk-android) or join our [community on Slack](https://slack.osaas.io).
