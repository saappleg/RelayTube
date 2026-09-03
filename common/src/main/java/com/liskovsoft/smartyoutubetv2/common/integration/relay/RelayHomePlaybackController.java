package com.liskovsoft.smartyoutubetv2.common.integration.relay;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;

/** Publishes the current SmartTube player state to the optional Relay Home companion. */
public class RelayHomePlaybackController extends BasePlayerController {
    public RelayHomePlaybackController(Context context) {
        RelayHomeBridge.initialize(context);
    }

    @Override
    public void onNewVideo(Video item) {
        publish(item, false);
    }

    @Override
    public void onVideoLoaded(Video item) {
        publish(item, getPlayer() != null && getPlayer().isPlaying());
    }

    @Override
    public void onPlay() {
        publish(getVideo(), true);
    }

    @Override
    public void onPause() {
        publish(getVideo(), false);
    }

    @Override
    public void onSeekEnd() {
        publish(getVideo(), getPlayer() != null && getPlayer().isPlaying());
    }

    @Override
    public void onEngineReleased() {
        publish(getVideo(), false);
    }

    @Override
    public void onFinish() {
        publish(getVideo(), false);
    }

    @Override
    public void onTickle() {
        if (getPlayer() != null && getPlayer().isEngineInitialized()) {
            publish(getVideo(), getPlayer().isPlaying());
        }
    }

    private void publish(Video video, boolean playing) {
        if (getContext() == null) {
            return;
        }

        long positionMs = getPlayer() != null ? getPlayer().getPositionMs() : video != null ? video.getPositionMs() : 0L;
        long durationMs = getPlayer() != null ? getPlayer().getDurationMs() : video != null ? video.getDurationMs() : 0L;
        RelayHomeBridge.publishPlayback(getContext(), video, positionMs, durationMs, playing);
    }
}
