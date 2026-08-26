package com.liskovsoft.smartyoutubetv2.common.integration;

import android.content.Context;
import android.content.Intent;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small, removable RelayTube integration that publishes the current subscription feed to Relay.
 * It sends only public card metadata for videos YouTube already returns to the signed-in viewer;
 * playback history, cookies, credentials, and account details are never shared.
 */
public final class RelayLibraryReporter {
    private static final String RELAY_PACKAGE = "com.relayhome.launcher";
    private static final String ACTION_SUBSCRIPTIONS = "com.relaytube.action.SUBSCRIPTIONS";
    private static final String EXTRA_VIDEOS = "videos";
    private static final int MAX_VIDEOS = 24;
    private static final AtomicBoolean started = new AtomicBoolean(false);

    private RelayLibraryReporter() {}

    public static void start(Context context) {
        if (context == null || !started.compareAndSet(false, true)) {
            return;
        }

        final Context appContext = context.getApplicationContext();
        YouTubeServiceManager.instance().getContentService().getSubscriptionsObserve()
                .subscribe(group -> publish(appContext, group), error -> { /* Relay is optional. */ });
    }

    private static void publish(Context context, MediaGroup group) {
        List<Video> videos = VideoGroup.from(group).getVideos();
        if (videos == null) {
            return;
        }

        JSONArray payload = new JSONArray();
        for (Video video : videos) {
            if (video == null || isBlank(video.videoId) || isBlank(video.getTitleFull())) {
                continue;
            }
            try {
                JSONObject item = new JSONObject();
                item.put("id", video.videoId);
                item.put("title", video.getTitleFull());
                item.put("channel", video.getAuthor());
                item.put("artwork", video.getCardImageUrl());
                payload.put(item);
            } catch (JSONException ignored) {
                continue;
            }
            if (payload.length() >= MAX_VIDEOS) {
                break;
            }
        }

        if (payload.length() == 0) {
            return;
        }
        context.sendBroadcast(new Intent(ACTION_SUBSCRIPTIONS)
                .setPackage(RELAY_PACKAGE)
                .putExtra(EXTRA_VIDEOS, payload.toString()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
