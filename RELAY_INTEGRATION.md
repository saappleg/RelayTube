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

The RelayTube visual pass will keep SmartTube's fast Leanback navigation while improving the
browse surface with rounded cards, clearer hierarchy, quieter chrome, dynamic artwork backdrops,
and a less intrusive player-control layer. These changes remain separate from the integration
bridge so they can be maintained independently of upstream SmartTube updates.
