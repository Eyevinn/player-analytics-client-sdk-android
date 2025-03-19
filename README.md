# Android Video Player SDK with Analytics

This project demonstrates an Android video player implementation using Media3 ExoPlayer with integrated analytics tracking capabilities. It sends various playback events to an analytics endpoint for monitoring and analysis.

## Overview

The project consists of a simple Android application that plays video content using ExoPlayer while tracking and sending detailed playback analytics events to a remote server. Events include playback state changes, buffering, seeking, and quality changes.

## Features

- Video playback using Media3 ExoPlayer
- Jetpack Compose UI integration
- Comprehensive analytics tracking:
    - Playback lifecycle events (init, loading, loaded, playing, paused, stopped)
    - Buffering events (buffering, buffered)
    - Seeking events (seeking, seeked)
    - Quality change events (bitrate, resolution)
    - Error events
    - Regular heartbeat events for session tracking

## Project Structure

The project contains two main components:

1. **MainActivity.kt**: Handles the player initialization, UI setup, and event tracking
2. **AnalyticsEventSender.kt**: Responsible for formatting and sending analytics events to the server

## Setup & Usage

### Prerequisites

- Android Studio
- Android SDK with minimum API level supporting Media3 components

### Installation

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle dependencies
4. Run the application on an emulator or physical device

### Configuration

The player is pre-configured to play a sample HLS stream. To change the media source, modify the `MEDIA_URL` constant in `MainActivity.kt`:

```kotlin
private const val MEDIA_URL = "your-media-url-here"
```

## Analytics Events

The SDK tracks and sends the following events:

| Event Type | Description |
|------------|-------------|
| init | Player initialization |
| metadata | Content metadata including title, live status, device type |
| loading | Content loading started |
| loaded | Content successfully loaded |
| playing | Playback started/resumed |
| paused | Playback paused |
| buffering | Player entered buffering state |
| buffered | Player exited buffering state |
| seeking | User initiated seek operation |
| seeked | Seek operation completed |
| bitrate_changed | Video quality changed |
| stopped | Playback stopped |
| error | Playback error occurred |
| heartbeat | Regular event sent during playback |

## Analytics Integration

Events are sent to the Eyevinn Player Analytics event sink. The endpoint URL can be configured in the `AnalyticsEventSender` class:

```kotlin
private val eventSinkUrl = "your-analytics-endpoint-url"
```

## Development

### Adding Custom Events

To add custom events, extend the `AnalyticsEventSender` class with new methods that call the base `sendEvent` method with appropriate parameters.

### UI Customization

The player uses a basic Jetpack Compose implementation with `AndroidView` to incorporate the ExoPlayer's `PlayerView`. Customize the UI by modifying the `VideoPlayer` composable function.

## Credits

This project was developed by Eyevinn Technology.
