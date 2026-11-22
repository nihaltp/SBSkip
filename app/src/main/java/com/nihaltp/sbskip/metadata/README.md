# Metadata Package

Core metadata read/write/extract logic for SBSkip.

## Files

| File | Purpose |
|------|---------|
| `YouTubeMetadataExtractor.kt` | **Single source of truth** for extracting YouTube metadata from ffprobe tags. Priority: SBSKIP_* → legacy YOUTUBE_* → comment JSON → fallback tags (comment, description, purl, source, www). |
| `SbskipMetadataReader.kt` | Reads full `MediaFileMetadata` from files via ffprobe. Used by `AndroidDownloadStorage.queryMetadata()` for manual file pick, history screen, auto-detect. Includes `readAndWriteBackSbskipTags()` which writes SBSKIP_* tags back if fallback extraction was used. |
| `FfmpegMetadataReader.kt` | Lightweight reader returning `SbskipFileMetadata` (just youtubeId, title, author, etc.). Used by `ImportManager` for watchlist/folder scanning — processes hundreds of files, needs minimal allocation. No write-back. |
| `MetadataWriter.kt` | Writes SBSKIP_* tags to files via FFmpeg (`-codec copy`). Two entry points: `writeSbskipTagsToUri()` for SAF/MediaStore URIs (temp-file-then-replace), `writeSbskipTagsToFile()` for local file paths. |

## Extraction Priority (YouTubeMetadataExtractor)

1. **SBSKIP_* tags** — `SBSKIP_YOUTUBE_VIDEO_ID`, `SBSKIP_YOUTUBE_VIDEO_URL`, `SBSKIP_YOUTUBE_TITLE`, `SBSKIP_AUTHOR_NAME`, `SBSKIP_AUTHOR_URL`, `SBSKIP_THUMBNAIL_URL`
2. **Legacy YOUTUBE_* tags** — `YOUTUBE_VIDEO_ID`, `YOUTUBE_VIDEO_URL`, `title`, `author`
3. **Comment JSON** — Parses `comment` tag as JSON (format written by `MediaTagger`)
4. **Fallback tags** — Plain YouTube URL in `comment`, `description`, `purl`, `source`, `www`

## Flow

```
User picks file / folder scan
         │
         ▼
AndroidDownloadStorage.queryMetadata() / ImportManager.scanFolderItem()
         │
         ▼
SbskipMetadataReader.readAndWriteBackSbskipTags() / FfmpegMetadataReader.readMetadata()
         │
         ▼
ffprobe → YouTubeMetadataExtractor.extract(tags)
         │
         ├── SBSKIP_* found → return immediately (fast path)
         ├── Legacy YOUTUBE_* found → fallbackUsed = true
         ├── Comment JSON found → fallbackUsed = true
         └── Fallback tags found → fallbackUsed = true
         │
         ▼ (if fallbackUsed)
MetadataWriter.writeSbskipTagsToUri() — writes SBSKIP_* tags back
         │
         ▼
Returns MediaFileMetadata / SbskipFileMetadata
```

## Tags Written (MetadataWriter)

| Tag | Source |
|-----|--------|
| `SBSKIP_YOUTUBE_VIDEO_ID` | Extracted video ID |
| `SBSKIP_YOUTUBE_VIDEO_URL` | Normalized YouTube URL |
| `SBSKIP_YOUTUBE_TITLE` | Video title |
| `SBSKIP_AUTHOR_NAME` | Channel name |
| `SBSKIP_AUTHOR_URL` | Channel URL (only from SBSKIP_* tags) |
| `SBSKIP_THUMBNAIL_URL` | Thumbnail URL (or derived from ID) |
| `SBSKIP_VERSION` | `BuildConfig.VERSION_NAME` |
| `SBSKIP_PROCESSED` | `"true"` |
| `SBSKIP_PROCESSED_AT` | `System.currentTimeMillis()` |
| `SBSKIP_SEGMENTS` | Preserved from existing |
| `SBSKIP_REMOVED_CATEGORIES` | Preserved from existing |

## Dependencies

- `com.arthenica.ffmpegkit` — FFprobe/FFmpeg
- `kotlinx.serialization` — JSON parsing
- `com.nihaltp.sbskip.util` — `AppLogger`, `Constants`, `YouTubeUrlParser`
- `com.nihaltp.sbskip.model` — `MediaFileMetadata`
