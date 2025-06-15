# Eyevinn Video Analytics SDK

A comprehensive video player SDK for Android that combines ExoPlayer with built-in analytics tracking and SGAI (Server-Guided Ad Insertion) ad tracking capabilities. This SDK simplifies media playback implementation while providing detailed analytics on user viewing behavior and comprehensive ad tracking.

## Features

- **Integrated ExoPlayer**: Built-in video player with no additional setup required
- **Comprehensive Analytics**: Automatic tracking of key video metrics (play, pause, buffering, etc.)
- **SGAI Ad Tracking**: Complete Server-Guided Ad Insertion tracking with automatic ad detection
- **Fragmented MP4 Ad Support**: Optimized for fragmented MP4 ads (ExoPlayer limitation with regular MP4)
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
    implementation 'com.eyevinn:video-analytics-sdk:1.0.0'
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

---

## Section 1: Video Analytics Tracking

Track comprehensive video playback analytics including user engagement, performance metrics, and viewing behavior.

### Quick Start - Analytics

#### Basic Implementation

```kotlin
// Initialize ExoPlayer
val exoPlayer = ExoPlayer.Builder(context).build()

// Initialize Analytics SDK
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

#### Jetpack Compose Implementation

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

### Analytics Events Tracked

- **Playback Events**: Play, pause, stop, seek
- **Performance Metrics**: Buffering events, bitrate changes
- **User Engagement**: Watch time, completion rates
- **Error Tracking**: Playback failures and network issues
- **Quality Metrics**: Video resolution, audio quality changes

---

## Section 2: SGAI Ad Tracking

Track Server-Guided Ad Insertion ads with comprehensive event monitoring and VAST support.

### Quick Start - SGAI Ad Tracking

#### Basic SGAI Implementation

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var adTracker: SGAIAdTracker
    private lateinit var analyticsManager: AnalyticsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the SGAI ad tracker
        adTracker = SGAIAdTracker(this)
        
        // Initialize analytics manager
        analyticsManager = AnalyticsManager(adTracker.player, "https://your-analytics-endpoint.com")
        
        // Set your HLS stream URL with SGAI ads
        val streamUrl = "https://your-hls-stream-with-ads.m3u8"
        adTracker.setMainMediaItem(streamUrl)
        
        // Create and set up PlayerView
        val playerView = PlayerView(this).apply {
            player = adTracker.player
        }
        
        // Optional: Set container for ad overlays
        adTracker.setPlayerViewContainer(playerView as ViewGroup)
    }
    
    override fun onStart() {
        super.onStart()
        analyticsManager.startTracking()
        adTracker.play()
    }
    
    override fun onStop() {
        super.onStop()
        analyticsManager.stopTracking("User left the app")
        adTracker.pause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        analyticsManager.release()
        adTracker.release()
    }
}
```

### SGAI Ad Events Tracked

The SDK automatically tracks these ad events:

| Event | Description | Trigger |
|-------|-------------|---------|
| `impression` | Ad view started | When ad begins playing |
| `start` | Ad playback started | Same as impression |
| `firstQuartile` | 25% of ad played | At 25% progress |
| `midpoint` | 50% of ad played | At 50% progress |
| `thirdQuartile` | 75% of ad played | At 75% progress |
| `complete` | Ad fully played | At 100% progress |

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

adTracker.setTrackingUrlsForAd("ad-session-1_0_0", trackingUrls)
```

#### Retrieve Tracking Data

```kotlin
// Get tracking URLs for a specific ad
val adKey = "ad-session-1_0_0"
val trackingUrls = adTracker.getTrackingUrlsForAd(adKey)
```

### SGAI Stream Requirements

Your HLS stream should include ad markers and use fragmented MP4 ads:

```m3u8
#EXT-X-DATERANGE:ID="ad-break-1",X-ASSET-LIST="https://your-server.com/ads.json"
```

**Important**: The SDK is optimized for fragmented MP4 ads due to ExoPlayer's limitations with regular MP4 files in HLS interstitials. Ensure your ad creatives are encoded as fragmented MP4.

The asset list should return JSON with tracking information:

```json
{
  "ASSETS": [
    {
      "URI": "ad-creative-fragmented.mp4",
      "X-AD-CREATIVE-SIGNALING": {
        "payload": {
          "tracking": [
            {
              "type": "impression",
              "urls": ["https://tracking.example.com/impression"]
            }
          ]
        }
      }
    }
  ]
}
```

### SGAI Configuration

#### Custom Ad Session ID

```kotlin
// Set custom ads session ID (default: "ad-session-1")
adTracker.setAdsId("custom-session-id")
```

#### Network Configuration

The SDK expects your ad server to be accessible. For Android emulator development:
- Use `10.0.2.2` instead of `localhost`
- Ensure your ad server is running and accessible
- **Ad Format Requirements**: Use fragmented MP4 ads only (regular MP4 files are not supported by ExoPlayer in HLS interstitials)

### Error Handling

The SDK includes built-in error handling and logging. Monitor logs with these tags:
- `SGAIAdTrackingUrlsExtractor` - Manifest monitoring and URL extraction
- `TrackingPixel` - Tracking request status
- `AdsLoader` - Ad loading events

## Sample Implementation

See `SGAIPlayerActivity.kt` for a complete implementation example using both analytics and SGAI ad tracking with Jetpack Compose.

## Requirements

- Android API 21+
- ExoPlayer 1.8.0-alpha01+
- Kotlin Coroutines support

## For Complete Documentation

See the [Usage Guide](USAGE.md) for detailed instructions and advanced configuration options.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.