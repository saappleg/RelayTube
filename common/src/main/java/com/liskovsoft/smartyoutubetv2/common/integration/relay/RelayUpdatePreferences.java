package com.liskovsoft.smartyoutubetv2.common.integration.relay;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import com.liskovsoft.smartyoutubetv2.common.R;

/**
 * RelayTube-only update policy.
 *
 * Keep this class, its resources, and its settings hook out of any SmartTube upstream patch.
 */
public final class RelayUpdatePreferences {
    public enum Channel {
        STABLE,
        BETA
    }

    private static final String RELAY_PACKAGE = "com.relaytube.stable";
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
        return context != null && RELAY_PACKAGE.equals(context.getPackageName());
    }

    public Channel getChannel() {
        String value = mPreferences.getString(PREF_CHANNEL, getDefaultChannel().name());

        try {
            return Channel.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return Channel.STABLE;
        }
    }

    public void setChannel(Channel channel) {
        mPreferences.edit().putString(PREF_CHANNEL, channel.name()).apply();
    }

    public String[] getManifestUrls() {
        int arrayId = getChannel() == Channel.BETA ?
                R.array.relay_update_urls_beta : R.array.relay_update_urls_stable;
        return mContext.getResources().getStringArray(arrayId);
    }

    private Channel getDefaultChannel() {
        try {
            String versionName = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0).versionName;
            if (versionName != null) {
                String normalized = versionName.toLowerCase(java.util.Locale.US);
                if (normalized.contains("beta") || normalized.contains("alpha") || normalized.contains("rc")) {
                    return Channel.BETA;
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // The package always exists here; Stable is the safest fallback.
        }

        return Channel.STABLE;
    }
}
