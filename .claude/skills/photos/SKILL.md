---
name: photos
description: Use for Tripinly photo features in the KMP app — picking images, compressing, direct upload to pre-signed URLs, upload progress and retries, gallery, cover selection, deletion and image loading.
---

# Photos

## Upload flow

1. **Pick:** Android Photo Picker (`PickMultipleVisualMedia`), iOS `PHPickerViewController`, behind a shared `PhotoPicker`. No storage permission needed for these pickers.
2. **Prepare (on device):** downscale so the long edge is ≤ 2560 px, re-encode as JPEG (quality ~85; HEIC → JPEG on iOS if needed), **strip EXIF/GPS** before upload. Reject > 15 MB after compression.
3. `POST /markers/{id}/photos/upload-url` with `{ mimeType, bytes }` → `{ photoId, uploadUrl }`.
4. `PUT` the bytes to `uploadUrl` with exactly that `Content-Type` and no auth header (it's a signed URL). Show progress.
5. `POST /photos/{id}/complete`.
6. Show a "processing" placeholder until `photo.ready` arrives via realtime, or poll `GET /markers/{id}/photos` every 3 s for up to 60 s if not connected.

Upload several photos in sequence (at most 2 in parallel). Keep an upload queue in shared code so leaving the screen doesn't cancel uploads. Retry network failures with backoff; if the signed URL expired, request a new one.

## Gallery and cover

- Swipeable gallery in the marker sheet; the cover is marked. Owners/editors can: set cover (`PUT /markers/{id}/cover`), reorder (`PUT /markers/{id}/photo-order`), delete (`DELETE /photos/{id}`, confirm first).
- The first ready photo becomes the cover automatically (server does it); update the pin when `marker.cover_changed` arrives.
- Viewers see like buttons and a report option on each photo.

## Loading

- Use the project's image loader (Coil 3 if none). Photo URLs are **signed and expire** (about 1 hour): cache by photo ID + size, not by the full URL, and refetch the marker/trip if an image returns 403.
- Use `thumbUrl` in pins and lists, `displayUrl` in the gallery.
- Never log photo URLs.

## Tests

Resize/compress sizing math, upload queue state machine (queued → uploading → processing → ready/failed), retry on expired URL.
