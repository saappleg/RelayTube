package com.liskovsoft.smartyoutubetv2.common.integration.relay;

import android.content.Context;
import android.content.SharedPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;

/**
 * RelayTube-only update policy.
 *
 * Keep this class, its resources, and its settings hook out of any SmartTube upstream patch.
 */
public final class RelayUpdatePreferences {
    public enum Channel {
        ALPHA,
        STABLE,
        BETA
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
        return context != null && (RELAY_BETA_PACKAGE.equals(context.getPackageName())
                || RELAY_STABLE_PACKAGE.equals(context.getPackageName()));
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
        if (supportsChannel(channel)) {
            mPreferences.edit().putString(PREF_CHANNEL, channel.name()).apply();
        }
    }

    public String[] getManifestUrls() {
        int arrayId;
        switch (getChannel()) {
            case ALPHA:
                arrayId = R.array.relay_update_urls_alpha;
                break;
            case BETA:
                arrayId = R.array.relay_update_urls_beta;
                break;
            default:
                arrayId = R.array.relay_update_urls_stable;
                break;
        }

        return mContext.getResources().getStringArray(arrayId);
    }

    public boolean supportsChannel(Channel channel) {
        if (RELAY_BETA_PACKAGE.equals(mContext.getPackageName())) {
            return channel == Channel.ALPHA || channel == Channel.BETA;
        }

        return RELAY_STABLE_PACKAGE.equals(mContext.getPackageName()) && channel == Channel.STABLE;
    }

    private Channel getDefaultChannel() {
        if (!RELAY_BETA_PACKAGE.equals(mContext.getPackageName())) {
            return Channel.STABLE;
        }

        try {
            String versionName = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0).versionName;
            if (versionName != null && versionName.toLowerCase(java.util.Locale.US).contains("alpha")) {
                return Channel.ALPHA;
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            // The package always exists here; beta is the safe fallback.
        }

        return Channel.BETA;
    }
}
