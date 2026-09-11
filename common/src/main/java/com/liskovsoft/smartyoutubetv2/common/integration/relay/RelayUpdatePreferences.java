package com.liskovsoft.smartyoutubetv2.common.integration.relay;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.R;

/**
 * RelayTube-only update policy.
 *
 * The beta and stable feeds are intentionally independent of SmartTube's
 * upstream update feed. The selected path is kept per installed package.
 */
public final class RelayUpdatePreferences {
    public enum Channel {
        BETA,
        STABLE
    }

    private static final String RELAY_BETA_PACKAGE = "com.relaytube.beta";
    private static final String RELAY_STABLE_PACKAGE = "com.relaytube.stable";
    private static final String PREFS_NAME = "relaytube.update.preferences";
    private static final String PREF_CHANNEL = "channel";

    private final Context mContext;
    private final SharedPreferences mPreferences;

    private RelayUpdatePreferences(Context context) {
        mContext = context.getApplicationContext();
        mPreferences = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static RelayUpdatePreferences instance(Context context) {
        return new RelayUpdatePreferences(context);
    }

    public static boolean isRelayTube(Context context) {
        if (context == null) {
            return false;
        }

        String packageName = context.getPackageName();
        return RELAY_BETA_PACKAGE.equals(packageName) || RELAY_STABLE_PACKAGE.equals(packageName);
    }

    public Channel getChannel() {
        String value = mPreferences.getString(PREF_CHANNEL, getDefaultChannel().name());

        try {
            Channel channel = Channel.valueOf(value);
            return supportsChannel(channel) ? channel : getDefaultChannel();
        } catch (IllegalArgumentException ignored) {
            return getDefaultChannel();
        }
    }

    public void setChannel(Channel channel) {
        if (channel != null && supportsChannel(channel)) {
            mPreferences.edit().putString(PREF_CHANNEL, channel.name()).apply();
        }
    }

    public String[] getManifestUrls() {
        int arrayId = getChannel() == Channel.BETA
                ? R.array.relay_update_urls_beta
                : R.array.relay_update_urls_stable;
        return mContext.getResources().getStringArray(arrayId);
    }

    public boolean supportsChannel(Channel channel) {
        return channel != null && isRelayTube(mContext);
    }

    private Channel getDefaultChannel() {
        return RELAY_BETA_PACKAGE.equals(mContext.getPackageName()) ? Channel.BETA : Channel.STABLE;
    }
}
