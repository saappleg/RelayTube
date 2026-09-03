package com.liskovsoft.smartyoutubetv2.common.app.update;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns the RelayTube update-channel choice and maps it to the GitHub manifest
 * published by the release workflow.
 *
 * Alpha and beta use the beta application id, so they can be selected without
 * ever offering a package-incompatible stable APK. Stable builds only expose
 * stable updates for the same reason.
 */
public final class UpdateChannelManager {
    public static final String ALPHA = "alpha";
    public static final String BETA = "beta";
    public static final String STABLE = "stable";

    private static final String PREFS_NAME = "relaytube.update.channels";
    private static final String PREF_SELECTED_CHANNEL = "selected_channel";
    private static final String PACKAGE_BETA = "com.relaytube.beta";
    private static final String PACKAGE_STABLE = "com.relaytube.stable";

    private final Context mContext;
    private final SharedPreferences mPreferences;

    public UpdateChannelManager(Context context) {
        mContext = context.getApplicationContext();
        mPreferences = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getSelectedChannel() {
        List<String> availableChannels = getAvailableChannels();

        if (availableChannels.isEmpty()) {
            return null;
        }

        String storedChannel = mPreferences.getString(PREF_SELECTED_CHANNEL, null);

        if (isAvailable(storedChannel)) {
            return storedChannel;
        }

        String defaultChannel = getDefaultChannel();
        return isAvailable(defaultChannel) ? defaultChannel : availableChannels.get(0);
    }

    public void setSelectedChannel(String channel) {
        if (isAvailable(channel)) {
            mPreferences.edit().putString(PREF_SELECTED_CHANNEL, channel).apply();
        }
    }

    public String[] getSelectedManifestUrls() {
        return getManifestUrls(getSelectedChannel());
    }

    public String[] getManifestUrls(String channel) {
        int resourceId = getManifestResourceId(channel);
        return resourceId == 0 ? new String[0] : mContext.getResources().getStringArray(resourceId);
    }

    public List<String> getAvailableChannels() {
        String packageName = mContext.getPackageName();
        List<String> channels = new ArrayList<>();

        if (PACKAGE_BETA.equals(packageName)) {
            channels.add(ALPHA);
            channels.add(BETA);
        } else if (PACKAGE_STABLE.equals(packageName)) {
            channels.add(STABLE);
        }

        return Collections.unmodifiableList(channels);
    }

    public boolean isAvailable(String channel) {
        return channel != null && getAvailableChannels().contains(channel);
    }

    public String getChannelLabel(String channel) {
        int resourceId;

        if (ALPHA.equals(channel)) {
            resourceId = R.string.update_channel_alpha;
        } else if (BETA.equals(channel)) {
            resourceId = R.string.update_channel_beta;
        } else if (STABLE.equals(channel)) {
            resourceId = R.string.update_channel_stable;
        } else {
            return channel;
        }

        return mContext.getString(resourceId);
    }

    private String getDefaultChannel() {
        return mContext.getString(R.string.update_default_channel);
    }

    private int getManifestResourceId(String channel) {
        if (ALPHA.equals(channel)) {
            return R.array.update_urls_alpha;
        } else if (BETA.equals(channel)) {
            return R.array.update_urls_beta;
        } else if (STABLE.equals(channel)) {
            return R.array.update_urls_stable;
        }

        return 0;
    }
}
