# Thumbnail Handling

This document describes how SBSkip obtains, displays, stores, and embeds thumbnail images.

## Sources

YouTube thumbnails are represented as URLs rather than downloaded into the queue database.

- For a known video ID, `Constants.buildYouTubeThumbnailUrl(videoId)` builds the fallback URL:
  `https://img.youtube.com/vi/<video-id>/mqdefault.jpg`.
- `YouTubeMetadataParser` extracts YouTube page metadata, but the normal thumbnail selected by the app is the constructed `img.youtube.com` URL.
- `MetadataFetcher` uses the constructed video thumbnail URL whenever a video ID is available. The oEmbed thumbnail is used only when no video ID can be extracted.
- Playlist entries receive the same constructed thumbnail URL when `YouTubePlaylistFetcher` parses each video.

## Direct YouTube Download Flow

When a user pastes a YouTube URL and starts a download:

1. The URL parser extracts the video ID.
2. NewPipe is invoked immediately.
3. Metadata and duration requests run afterward, concurrently.
4. The pending download stores the thumbnail URL in `PendingDownload.thumbnailUrl`.
5. The pending-download card displays that URL while the file is being downloaded.
6. After the file is detected and queued, the thumbnail is copied into the queue item data.

The pending-download thumbnail is therefore available before NewPipe finishes, assuming the video ID was accepted and the generated thumbnail URL is reachable.

## Queue Persistence

The persistent queue stores `thumbnailUrl` on `DownloadQueueEntity` in the `download_queue` Room table.

There are two important paths:

- A direct NewPipe download creates a `PendingDownload` with a thumbnail URL. When the downloaded file is later enqueued, that URL is passed into the queue model and can be displayed immediately.
- An existing local file is added through `QueueRepository.enqueue()`. That method currently creates the entity with `thumbnailUrl = null`. The item therefore initially displays the cleaning placeholder until `DownloadWorker` fetches metadata and calls `QueueRepository.updateMetadata()`.

The worker updates the queue thumbnail using the generated video thumbnail URL, with the YouTube metadata/oEmbed result as the fallback source when no video ID is available.

## Queue UI

`QueueItemCard` renders the persisted queue thumbnail with Coil's `AsyncImage`:

- A non-null `thumbnailUrl` is loaded from the network.
- A null URL displays the cleaning-services placeholder icon.
- A failed image request currently has no explicit error state, so the image component's default failure behavior is used.
- Long-pressing a loaded thumbnail attempts to copy the image to the clipboard through `ClipboardHelper`.

`PendingDownloadCard` and `PlaylistDownloadCard` use the same URL-based Coil loading approach. When their thumbnail URL is missing, they show a loading indicator rather than the queue placeholder icon.

## Worker and Media Tagging

During queue processing, `DownloadWorker` obtains YouTube metadata through `MetadataFetcher`. It then:

1. Chooses the metadata thumbnail URL, or generates the `img.youtube.com` fallback from the video ID.
2. Stores that URL through `queueRepository.updateMetadata()`.
3. Passes the URL to `ProcessingContext` and `MediaTagger`.

For audio output, `MediaTagger` first prefers `MusicMetadata.artworkUrl` when available. If no embedded cover image exists, it downloads artwork using `CoverArtManager`. If the artwork download fails, it falls back to the video thumbnail URL.

`CoverArtManager` validates downloaded artwork before embedding it:

- HTTP response must be successful.
- Content type must start with `image/`.
- File size must not exceed 5 MB.
- The temporary artwork file is stored in the app cache and used during FFmpeg tagging.

## Failure and Fallback Behavior

Thumbnail URLs are not downloaded when queue records are created. Coil downloads them when the card is rendered. Consequently:

- No video ID or missing metadata produces a placeholder until a later worker update can provide a URL.
- A network failure while loading a thumbnail leaves the card without a usable image; the queue item itself is not failed solely because its thumbnail failed.
- A network failure while downloading cover art does not stop media processing; tagging falls back to the video thumbnail or proceeds without embedded artwork.
- Thumbnail URLs are not treated as source media and are not copied into the saved video file. They are embedded only when producing audio metadata and artwork is available.

## Known Timing Limitation

For the existing-local-file workflow, the queue is persisted with a null thumbnail before the worker runs. This is why a newly queued media-cleaning item can show the placeholder image. The thumbnail becomes available only after the worker reaches metadata fetching and updates the queue record.

A future improvement would derive and persist the thumbnail URL from the YouTube video ID during `QueueRepository.enqueue()`, before scheduling the worker. That would remove the initial placeholder for valid YouTube URLs while keeping the worker update as a fallback.
