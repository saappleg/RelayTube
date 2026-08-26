package com.liskovsoft.smartyoutubetv2.tv.integration;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

/**
 * Opt-in-safe handoff for Relay Home.
 *
 * The broadcast is constrained to Relay's package and contains only the video currently being
 * played. SmartTube watch-history storage is intentionally never exposed.
 */
public final class RelayPlaybackReporter {
    public static final String RELAY_PACKAGE = "com.relayhome.launcher";
    public static final String ACTION_PLAYBACK = "com.relaytube.action.PLAYBACK";
    public static final String EXTRA_VIDEO_ID = "video_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_CHANNEL = "channel";
    public static final String EXTRA_ARTWORK_URL = "artwork_url";
    public static final String EXTRA_POSITION_MS = "position_ms";
    public static final String EXTRA_DURATION_MS = "duration_ms";
    public static final String EXTRA_PLAYING = "playing";

    private RelayPlaybackReporter() {}

    public static void publish(
            @Nullable Context context,
            @Nullable Video video,
            long positionMs,
            long durationMs,
            boolean playing) {
        if (context == null || video == null || video.videoId == null || video.videoId.isEmpty()) {
            return;
        }

        Intent update = new Intent(ACTION_PLAYBACK)
                .setPackage(RELAY_PACKAGE)
                .putExtra(EXTRA_VIDEO_ID, video.videoId)
                .putExtra(EXTRA_TITLE, video.getTitleFull())
                .putExtra(EXTRA_CHANNEL, video.getAuthor())
                .putExtra(EXTRA_ARTWORK_URL, video.getCardImageUrl())
                .putExtra(EXTRA_POSITION_MS, positionMs)
                .putExtra(EXTRA_DURATION_MS, durationMs)
                .putExtra(EXTRA_PLAYING, playing);
        context.sendBroadcast(update);
    }
}
