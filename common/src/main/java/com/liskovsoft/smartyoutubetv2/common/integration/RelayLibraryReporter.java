package com.liskovsoft.smartyoutubetv2.common.integration;

import android.content.Context;
import android.content.Intent;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.googlecommon.service.oauth.YouTubeAccount;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Small, removable RelayTube integration that publishes the current subscription and Continue
 * Watching feeds to Relay. It sends only public card metadata for videos YouTube returns to the signed-in viewer;
 * playback history, cookies, credentials, and account details are never shared.
 */
public final class RelayLibraryReporter {
    private static final String RELAY_PACKAGE = "com.relayhome.launcher";
    private static final String ACTION_SUBSCRIPTIONS = "com.relaytube.action.SUBSCRIPTIONS";
    private static final String ACTION_CONTINUE_WATCHING = "com.relaytube.action.CONTINUE_WATCHING";
    private static final String ACTION_PROFILES = "com.relaytube.action.PROFILES";
    private static final String EXTRA_VIDEOS = "videos";
    private static final String EXTRA_PROFILE_ID = "profile_id";
    private static final String EXTRA_PROFILES = "profiles";
    private static final int MAX_VIDEOS = 24;
    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static SignInService.OnAccountChange accountChangeListener;
    private static final Map<String, String> latestPayloads = new ConcurrentHashMap<>();

    private RelayLibraryReporter() {}

    public static void start(Context context) {
        if (context == null || !started.compareAndSet(false, true)) {
            return;
        }

        final Context appContext = context.getApplicationContext();
        accountChangeListener = account -> {
            publishProfiles(appContext);
            refresh(appContext);
        };
        YouTubeServiceManager.instance().getSignInService().addOnAccountChange(accountChangeListener);
        publishProfiles(appContext);
        refresh(appContext);
    }

    public static void refresh(Context appContext) {
        YouTubeServiceManager.instance().getContentService().getSubscriptionsObserve()
                .subscribe(group -> publish(appContext, group, ACTION_SUBSCRIPTIONS), error -> { /* Relay is optional. */ });
        YouTubeServiceManager.instance().getContentService().getHistoryObserve()
                .subscribe(group -> publish(appContext, group, ACTION_CONTINUE_WATCHING), error -> { /* Relay is optional. */ });
    }

    private static void publish(Context context, MediaGroup group, String action) {
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
                item.put("channel_id", video.channelId);
                item.put("artwork", video.getCardImageUrl());
                item.put("description", video.description);
                item.put("metadata", video.getSecondTitleFull() != null ? video.getSecondTitleFull().toString() : null);
                item.put("progress", Math.max(0f, Math.min(100f, video.percentWatched)) / 100f);
                item.put("position_ms", video.getPositionMs());
                item.put("duration_ms", video.getDurationMs());
                payload.put(item);
            } catch (JSONException ignored) {
                continue;
            }
            if (payload.length() >= MAX_VIDEOS) {
                break;
            }
        }

        Account selected = YouTubeServiceManager.instance().getSignInService().getSelectedAccount();
        latestPayloads.put(action + ':' + profileId(selected), payload.toString());
        context.sendBroadcast(new Intent(action)
                .setPackage(RELAY_PACKAGE)
                .putExtra(EXTRA_PROFILE_ID, profileId(selected))
                .putExtra(EXTRA_VIDEOS, payload.toString()));
    }

    public static void publishProfiles(Context context) {
        JSONArray payload = new JSONArray();
        List<Account> accounts = YouTubeServiceManager.instance().getSignInService().getAccounts();
        if (accounts != null) {
            for (Account account : accounts) {
                if (account == null || account.isEmpty()) continue;
                try {
                    payload.put(new JSONObject()
                            .put("id", profileId(account))
                            .put("name", account.getName())
                            .put("avatar", account.getAvatarImageUrl())
                            .put("selected", account.isSelected()));
                } catch (JSONException ignored) {
                    // A malformed optional display field must not break the bridge.
                }
            }
        }
        Account selected = YouTubeServiceManager.instance().getSignInService().getSelectedAccount();
        context.sendBroadcast(new Intent(ACTION_PROFILES)
                .setPackage(RELAY_PACKAGE)
                .putExtra(EXTRA_PROFILE_ID, profileId(selected))
                .putExtra(EXTRA_PROFILES, payload.toString()));
    }

    public static String profileId(Account account) {
        if (account == null) return "guest";
        String pageId = account instanceof YouTubeAccount ? ((YouTubeAccount) account).getPageIdToken() : null;
        String identity = pageId != null ? pageId : String.valueOf(account.getEmail()) + '\n' + String.valueOf(account.getName());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("rt_");
            for (int i = 0; i < 12; i++) result.append(String.format("%02x", digest[i]));
            return result.toString();
        } catch (Exception ignored) {
            return "rt_" + Integer.toHexString(identity.hashCode());
        }
    }

    public static String latestPayload(String action, String profileId) {
        String payload = latestPayloads.get(action + ':' + profileId);
        return payload != null ? payload : "[]";
    }

    public static String profilesPayload() {
        JSONArray payload = new JSONArray();
        List<Account> accounts = YouTubeServiceManager.instance().getSignInService().getAccounts();
        if (accounts != null) {
            for (Account account : accounts) {
                if (account == null || account.isEmpty()) continue;
                try {
                    payload.put(new JSONObject()
                            .put("id", profileId(account))
                            .put("name", account.getName())
                            .put("avatar", account.getAvatarImageUrl())
                            .put("selected", account.isSelected()));
                } catch (JSONException ignored) {
                    // Optional display metadata only.
                }
            }
        }
        return payload.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
