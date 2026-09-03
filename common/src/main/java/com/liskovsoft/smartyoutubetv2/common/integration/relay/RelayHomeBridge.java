package com.liskovsoft.smartyoutubetv2.common.integration.relay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.disposables.Disposable;

/**
 * Relay Home integration for the stable RelayTube package.
 *
 * <p>The bridge is deliberately best-effort. Relay Home can still use the public
 * MediaSession/notification metadata when the account or feed APIs are unavailable.</p>
 */
public final class RelayHomeBridge {
    public static final String RELAY_HOME_PACKAGE = "com.relayhome.launcher";
    public static final String RELAY_TUBE_PACKAGE = "com.relaytube.stable";
    public static final String RELAY_PROFILES_AUTHORITY = "com.relaytube.stable.relayprofiles";

    public static final String ACTION_PLAYBACK = "com.relaytube.action.PLAYBACK";
    public static final String ACTION_SUBSCRIPTIONS = "com.relaytube.action.SUBSCRIPTIONS";
    public static final String ACTION_CONTINUE_WATCHING = "com.relaytube.action.CONTINUE_WATCHING";
    public static final String ACTION_PROFILES = "com.relaytube.action.PROFILES";
    public static final String ACTION_SELECT_PROFILE = "com.relaytube.action.SELECT_PROFILE";
    public static final String ACTION_REQUEST_PROFILES = "com.relaytube.action.REQUEST_PROFILES";

    public static final String EXTRA_VIDEO_ID = "video_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_CHANNEL = "channel";
    public static final String EXTRA_ARTWORK_URL = "artwork_url";
    public static final String EXTRA_DESCRIPTION = "description";
    public static final String EXTRA_METADATA = "metadata";
    public static final String EXTRA_POSITION_MS = "position_ms";
    public static final String EXTRA_DURATION_MS = "duration_ms";
    public static final String EXTRA_PLAYING = "playing";
    public static final String EXTRA_PROFILE_ID = "profile_id";
    public static final String EXTRA_VIDEOS = "videos";
    public static final String EXTRA_PROFILES = "profiles";
    public static final String FEED_SUBSCRIPTIONS = "subscriptions";
    public static final String FEED_CONTINUE_WATCHING = "continue_watching";

    public static final String METHOD_PROFILES = "profiles";
    public static final String METHOD_SELECT = "select";
    public static final String METHOD_FEEDS = "feeds";

    private static final String PREFS_NAME = "relay_home_bridge";
    private static final String PREF_PROFILES = "profiles";
    private static final String PREF_PROFILE_ID = "profile_id";
    private static final String PREF_SUBSCRIPTIONS_PREFIX = "subscriptions_";
    private static final String PREF_CONTINUE_PREFIX = "continue_watching_";
    private static final String GUEST_PROFILE_ID = "guest";
    private static final String EMPTY_JSON = "[]";
    private static final int MAX_FEED_VIDEOS = 24;
    private static final long ACCOUNT_RETRY_DELAY_MS = 1_000L;

    private static final Object LOCK = new Object();
    private static Context sContext;
    private static boolean sInitialized;
    private static Disposable sSubscriptionsAction;
    private static String sSubscriptionsProfileId;

    private static final Runnable ACCOUNT_RETRY = () -> {
        Context context = sContext;
        if (context != null) {
            publishProfiles(context);
            refreshFeeds(context);
        }
    };
    private static final MediaServiceManager.AccountChangeListener ACCOUNT_LISTENER = account -> {
        Context context = sContext;
        if (context != null) {
            publishProfiles(context);
            refreshFeeds(context);
            Utils.postDelayed(ACCOUNT_RETRY, ACCOUNT_RETRY_DELAY_MS);
        }
    };

    private RelayHomeBridge() {
    }

    public static void initialize(@Nullable Context context) {
        if (context == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            return;
        }
        if (!isStablePackage(appContext)) {
            return;
        }

        boolean initializeServices = false;
        synchronized (LOCK) {
            sContext = appContext;
            if (!sInitialized) {
                sInitialized = true;
                initializeServices = true;
            }
        }

        if (initializeServices) {
            try {
                // Account storage is initialized by the media service when GlobalPreferences exists.
                GlobalPreferences.instance(appContext);
                MediaServiceManager.instance().addAccountListener(ACCOUNT_LISTENER);
            } catch (Throwable ignored) {
                // The bridge must never prevent the viewer from starting.
            }
        }

        publishProfiles(appContext);
        refreshFeeds(appContext);
        // Account restoration is asynchronous in SmartTube's media service.
        Utils.postDelayed(ACCOUNT_RETRY, ACCOUNT_RETRY_DELAY_MS);
    }

    public static Bundle getProfiles(@Nullable Context context) {
        if (context == null) {
            return null;
        }

        SharedPreferences prefs = preferences(context);
        String profilesJson = EMPTY_JSON;
        String profileId = GUEST_PROFILE_ID;

        try {
            SignInService signInService = getSignInService();
            List<Account> accounts = signInService.getAccounts();
            if (accounts != null && !accounts.isEmpty()) {
                ProfileSnapshot snapshot = createProfiles(accounts, signInService.getSelectedAccount());
                profilesJson = snapshot.profilesJson;
                profileId = snapshot.selectedId;
                saveProfiles(prefs, profilesJson, profileId);
            } else {
                String cachedProfiles = prefs.getString(PREF_PROFILES, null);
                if (!TextUtils.isEmpty(cachedProfiles)) {
                    profilesJson = cachedProfiles;
                    profileId = prefs.getString(PREF_PROFILE_ID, GUEST_PROFILE_ID);
                } else {
                    profilesJson = createGuestProfiles();
                }
            }
        } catch (Throwable ignored) {
            String cachedProfiles = prefs.getString(PREF_PROFILES, null);
            profilesJson = !TextUtils.isEmpty(cachedProfiles) ? cachedProfiles : createGuestProfiles();
            profileId = prefs.getString(PREF_PROFILE_ID, GUEST_PROFILE_ID);
        }

        Bundle result = new Bundle();
        result.putString(EXTRA_PROFILES, profilesJson);
        result.putString(EXTRA_PROFILE_ID, profileId);
        return result;
    }

    public static Bundle getFeeds(@Nullable Context context, @Nullable String profileId) {
        if (context == null) {
            return null;
        }

        String resolvedProfileId = !TextUtils.isEmpty(profileId) ? profileId : getSelectedProfileId();
        SharedPreferences prefs = preferences(context);

        Bundle result = new Bundle();
        result.putString(FEED_SUBSCRIPTIONS,
                prefs.getString(feedKey(PREF_SUBSCRIPTIONS_PREFIX, resolvedProfileId), EMPTY_JSON));
        result.putString(FEED_CONTINUE_WATCHING,
                prefs.getString(feedKey(PREF_CONTINUE_PREFIX, resolvedProfileId), EMPTY_JSON));
        return result;
    }

    public static void selectProfile(@Nullable Context context, @Nullable String profileId) {
        if (context == null || TextUtils.isEmpty(profileId)) {
            return;
        }

        initialize(context);

        try {
            SignInService signInService = getSignInService();
            Account selectedAccount = null;
            boolean knownProfile = GUEST_PROFILE_ID.equals(profileId);
            List<Account> accounts = signInService.getAccounts();

            if (accounts != null) {
                for (Account account : accounts) {
                    if (account != null && profileId.equals(profileId(account))) {
                        selectedAccount = account;
                        knownProfile = true;
                        break;
                    }
                }
            }

            if (knownProfile) {
                signInService.selectAccount(selectedAccount);
                publishProfiles(context);
                refreshFeeds(context);
            }
        } catch (Throwable ignored) {
            // Invalid or unavailable profiles are ignored rather than changing the active account.
        }

        Utils.postDelayed(ACCOUNT_RETRY, ACCOUNT_RETRY_DELAY_MS);
    }

    public static void publishProfiles(@Nullable Context context) {
        if (!isStablePackage(context)) {
            return;
        }

        Bundle profiles = getProfiles(context);
        if (profiles == null) {
            return;
        }

        Intent intent = new Intent(ACTION_PROFILES)
                .setPackage(RELAY_HOME_PACKAGE)
                .putExtra(EXTRA_PROFILE_ID, profiles.getString(EXTRA_PROFILE_ID, GUEST_PROFILE_ID))
                .putExtra(EXTRA_PROFILES, profiles.getString(EXTRA_PROFILES, EMPTY_JSON));
        sendBroadcast(context, intent);
    }

    public static void publishPlayback(@Nullable Context context, @Nullable Video video,
                                       long positionMs, long durationMs, boolean playing) {
        if (!isStablePackage(context) || video == null || TextUtils.isEmpty(video.videoId)
                || TextUtils.isEmpty(video.getTitle())) {
            return;
        }

        Intent intent = new Intent(ACTION_PLAYBACK)
                .setPackage(RELAY_HOME_PACKAGE)
                .putExtra(EXTRA_VIDEO_ID, video.videoId)
                .putExtra(EXTRA_TITLE, video.getTitle())
                .putExtra(EXTRA_POSITION_MS, Math.max(0L, positionMs))
                .putExtra(EXTRA_DURATION_MS, Math.max(0L, durationMs))
                .putExtra(EXTRA_PLAYING, playing);
        putExtraIfNotEmpty(intent, EXTRA_CHANNEL, video.getAuthor());
        putExtraIfNotEmpty(intent, EXTRA_ARTWORK_URL, firstNonEmpty(video.getCardImageUrl(), video.bgImageUrl));
        putExtraIfNotEmpty(intent, EXTRA_DESCRIPTION, video.description);
        putExtraIfNotEmpty(intent, EXTRA_METADATA, Helpers.toString(video.getSecondTitleFull()));
        sendBroadcast(context, intent);
    }

    public static void refreshFeeds(@Nullable Context context) {
        if (!isStablePackage(context)) {
            return;
        }

        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            return;
        }

        final String profileId = getSelectedProfileId();
        publishContinueWatching(appContext, profileId);

        try {
            SignInService signInService = getSignInService();
            if (!signInService.isSigned()) {
                publishFeed(appContext, ACTION_SUBSCRIPTIONS, profileId, EMPTY_JSON, PREF_SUBSCRIPTIONS_PREFIX);
                return;
            }

            synchronized (LOCK) {
                if (sSubscriptionsAction != null && !sSubscriptionsAction.isDisposed()
                        && Helpers.equals(profileId, sSubscriptionsProfileId)) {
                    return;
                }
                RxHelper.disposeActions(sSubscriptionsAction);
                sSubscriptionsProfileId = profileId;
            }

            ContentService contentService = YouTubeServiceManager.instance().getContentService();
            sSubscriptionsAction = RxHelper.execute(
                    contentService.getSubscriptionsObserve(),
                    group -> {
                        String payload = mediaGroupToJson(group);
                        publishFeed(appContext, ACTION_SUBSCRIPTIONS, profileId, payload, PREF_SUBSCRIPTIONS_PREFIX);
                    },
                    error -> {
                        // Keep the last successful feed cache when the network is unavailable.
                    }
            );
        } catch (Throwable ignored) {
            // Feed availability is optional and must not affect playback or account selection.
        }
    }

    private static void publishContinueWatching(Context context, String profileId) {
        String payload = statesToJson(context);
        publishFeed(context, ACTION_CONTINUE_WATCHING, profileId, payload, PREF_CONTINUE_PREFIX);
    }

    private static void publishFeed(Context context, String action, String profileId,
                                    String payload, String preferencePrefix) {
        if (TextUtils.isEmpty(profileId)) {
            profileId = GUEST_PROFILE_ID;
        }
        if (TextUtils.isEmpty(payload)) {
            payload = EMPTY_JSON;
        }

        preferences(context).edit()
                .putString(feedKey(preferencePrefix, profileId), payload)
                .apply();

        if (!isStablePackage(context)) {
            return;
        }

        Intent intent = new Intent(action)
                .setPackage(RELAY_HOME_PACKAGE)
                .putExtra(EXTRA_PROFILE_ID, profileId)
                .putExtra(EXTRA_VIDEOS, payload);
        sendBroadcast(context, intent);
    }

    private static String statesToJson(Context context) {
        try {
            VideoStateService stateService = VideoStateService.instance(context);
            if (stateService == null || stateService.isEmpty()) {
                return EMPTY_JSON;
            }

            JSONArray result = new JSONArray();
            Set<String> videoIds = new HashSet<>();
            List<VideoStateService.State> states = new ArrayList<>(stateService.getStates());
            Collections.reverse(states);

            for (VideoStateService.State state : states) {
                if (state == null || state.video == null || TextUtils.isEmpty(state.video.videoId)
                        || !videoIds.add(state.video.videoId)) {
                    continue;
                }
                if (state.durationMs > 0 && state.positionMs >= state.durationMs) {
                    continue;
                }
                if (result.length() >= MAX_FEED_VIDEOS) {
                    break;
                }
                result.put(videoToJson(state.video, state.positionMs, state.durationMs));
            }
            return result.toString();
        } catch (Throwable ignored) {
            return EMPTY_JSON;
        }
    }

    private static String mediaGroupToJson(@Nullable MediaGroup group) {
        if (group == null || group.getMediaItems() == null) {
            return EMPTY_JSON;
        }

        JSONArray result = new JSONArray();
        Set<String> videoIds = new HashSet<>();
        try {
            for (MediaItem mediaItem : group.getMediaItems()) {
                if (result.length() >= MAX_FEED_VIDEOS || mediaItem == null) {
                    break;
                }
                Video video = Video.from(mediaItem);
                if (video == null || TextUtils.isEmpty(video.videoId) || !videoIds.add(video.videoId)) {
                    continue;
                }
                VideoStateService.State state = VideoStateService.instance(null) != null
                        ? VideoStateService.instance(null).getByVideoId(video.videoId) : null;
                long positionMs = state != null ? state.positionMs : video.getPositionMs();
                long durationMs = state != null && state.durationMs > 0 ? state.durationMs : video.getDurationMs();
                result.put(videoToJson(video, positionMs, durationMs));
            }
        } catch (Throwable ignored) {
            // Return the successfully serialized prefix when an individual item is malformed.
        }
        return result.toString();
    }

    private static JSONObject videoToJson(Video video, long positionMs, long durationMs) throws JSONException {
        long safeDurationMs = Math.max(0L, durationMs);
        long safePositionMs = Math.max(0L, positionMs);
        if (safeDurationMs > 0) {
            safePositionMs = Math.min(safePositionMs, safeDurationMs);
        }

        float progress = video.percentWatched >= 0 ? video.percentWatched / 100f : 0f;
        if (safeDurationMs > 0) {
            progress = safePositionMs / (float) safeDurationMs;
        }
        progress = Math.max(0f, Math.min(1f, progress));

        JSONObject result = new JSONObject();
        result.put("id", video.videoId);
        result.put("title", firstNonEmpty(video.getTitle(), video.title, ""));
        putJsonIfNotEmpty(result, "channel", video.getAuthor());
        putJsonIfNotEmpty(result, "channel_id", video.channelId);
        putJsonIfNotEmpty(result, "artwork", firstNonEmpty(video.getCardImageUrl(), video.bgImageUrl));
        putJsonIfNotEmpty(result, "description", video.description);
        putJsonIfNotEmpty(result, "metadata", Helpers.toString(video.getSecondTitleFull()));
        result.put("duration_ms", safeDurationMs);
        result.put("progress", progress);
        result.put("position_ms", safePositionMs);
        return result;
    }

    private static ProfileSnapshot createProfiles(List<Account> accounts, @Nullable Account selectedAccount) {
        JSONArray profiles = new JSONArray();
        Set<String> ids = new HashSet<>();
        String selectedId = selectedAccount != null ? profileId(selectedAccount) : GUEST_PROFILE_ID;

        try {
            JSONObject guest = new JSONObject();
            guest.put("id", GUEST_PROFILE_ID);
            guest.put("name", "Guest");
            guest.put("selected", selectedAccount == null);
            profiles.put(guest);
            ids.add(GUEST_PROFILE_ID);

            for (Account account : accounts) {
                if (account == null) {
                    continue;
                }
                String id = profileId(account);
                if (!ids.add(id)) {
                    continue;
                }
                JSONObject profile = new JSONObject();
                profile.put("id", id);
                profile.put("name", firstNonEmpty(account.getName(), account.getEmail(), "YouTube account"));
                putJsonIfNotEmpty(profile, "avatar", account.getAvatarImageUrl());
                profile.put("selected", account == selectedAccount || account.isSelected());
                profiles.put(profile);
            }
        } catch (JSONException ignored) {
            // The fixed fields above are not expected to fail, but return a valid empty payload if they do.
            return new ProfileSnapshot(createGuestProfiles(), GUEST_PROFILE_ID);
        }

        return new ProfileSnapshot(profiles.toString(), selectedId);
    }

    private static String createGuestProfiles() {
        try {
            JSONObject guest = new JSONObject();
            guest.put("id", GUEST_PROFILE_ID);
            guest.put("name", "Guest");
            guest.put("selected", true);
            return new JSONArray().put(guest).toString();
        } catch (JSONException ignored) {
            return EMPTY_JSON;
        }
    }

    private static String profileId(Account account) {
        if (account == null) {
            return GUEST_PROFILE_ID;
        }
        String identity = firstNonEmpty(account.getEmail(), account.getName(), String.valueOf(account.getId()));
        return "account:" + identity;
    }

    private static String getSelectedProfileId() {
        try {
            Account account = getSignInService().getSelectedAccount();
            return account != null ? profileId(account) : GUEST_PROFILE_ID;
        } catch (Throwable ignored) {
            return GUEST_PROFILE_ID;
        }
    }

    private static SignInService getSignInService() {
        return YouTubeServiceManager.instance().getSignInService();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String feedKey(String prefix, String profileId) {
        return prefix + (!TextUtils.isEmpty(profileId) ? profileId : GUEST_PROFILE_ID);
    }

    private static void saveProfiles(SharedPreferences prefs, String profilesJson, String profileId) {
        prefs.edit().putString(PREF_PROFILES, profilesJson).putString(PREF_PROFILE_ID, profileId).apply();
    }

    private static void putExtraIfNotEmpty(Intent intent, String key, @Nullable String value) {
        if (!TextUtils.isEmpty(value)) {
            intent.putExtra(key, value);
        }
    }

    private static void putJsonIfNotEmpty(JSONObject object, String key, @Nullable String value) throws JSONException {
        if (!TextUtils.isEmpty(value)) {
            object.put(key, value);
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isStablePackage(@Nullable Context context) {
        return context != null && RELAY_TUBE_PACKAGE.equals(context.getApplicationContext().getPackageName());
    }

    private static void sendBroadcast(@Nullable Context context, Intent intent) {
        if (context == null) {
            return;
        }
        try {
            context.sendBroadcast(intent);
        } catch (Throwable ignored) {
            // Relay Home is optional and may be absent or not yet authorized.
        }
    }

    private static final class ProfileSnapshot {
        private final String profilesJson;
        private final String selectedId;

        private ProfileSnapshot(String profilesJson, String selectedId) {
            this.profilesJson = profilesJson;
            this.selectedId = selectedId;
        }
    }
}
