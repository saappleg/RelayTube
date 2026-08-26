# Relay Home integration

RelayTube includes a small package-targeted bridge for Relay Home. It publishes only the
currently-playing video to `com.relayhome.launcher`; it does not expose SmartTube's private
watch-history database.

## Payload

Action: `com.relaytube.action.PLAYBACK`

- `video_id`
- `title`
- `channel`
- `artwork_url`
- `position_ms`
- `duration_ms`
- `playing`

RelayTube uses the same media metadata source that powers Android's media session, but supplies
the video ID too, which enables exact Relay-to-SmartTube handoff. The bridge is deliberately
package-targeted and is the foundation for richer Relay cards, App Peek, and a future opt-in
recent-history provider.

## UI direction

RelayTube is moving toward Material Design 3 and Material You. Android 12+ uses the profile's
Monet neutral/accent resources for the transitional Leanback backdrop; older TVs use RelayTube's
violet fallback. The full migration keeps SmartTube's fast remote navigation and playback engine
while replacing the browse surface progressively with a Compose Material 3 shell:

1. Material You token layer and refined legacy card treatment.
2. Compose Material 3 browse shell: dynamic color, hero artwork, rounded rails, and TV focus.
3. Compose search, account, and settings surfaces.
4. The player moves last, retaining SmartTube's stable ExoPlayer controls throughout.

These changes remain separate from the integration bridge so they can be maintained independently
of upstream SmartTube updates.
