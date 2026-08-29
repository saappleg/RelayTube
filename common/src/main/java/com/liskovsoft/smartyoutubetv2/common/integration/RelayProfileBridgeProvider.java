package com.liskovsoft.smartyoutubetv2.common.integration;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

/** Package-verified IPC for Relay Home. Tokens and cookies never cross this boundary. */
public final class RelayProfileBridgeProvider extends ContentProvider {
    private static final String RELAY_PACKAGE = "com.relayhome.launcher";
    private static final String METHOD_PROFILES = "profiles";
    private static final String METHOD_SELECT = "select";
    private static final String METHOD_FEEDS = "feeds";
    private static final String ACTION_SUBSCRIPTIONS = "com.relaytube.action.SUBSCRIPTIONS";
    private static final String ACTION_CONTINUE = "com.relaytube.action.CONTINUE_WATCHING";

    @Override
    public boolean onCreate() {
        if (getContext() != null) RelayLibraryReporter.start(getContext());
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (!isRelayCaller()) throw new SecurityException("Relay profile bridge caller rejected");
        if (METHOD_SELECT.equals(method) && arg != null) select(arg);

        SignInService signIn = YouTubeServiceManager.instance().getSignInService();
        String selectedId = RelayLibraryReporter.profileId(signIn.getSelectedAccount());
        Bundle result = new Bundle();
        result.putString("profile_id", selectedId);
        result.putString("profiles", RelayLibraryReporter.profilesPayload());
        if (METHOD_FEEDS.equals(method)) {
            String requestedId = arg != null ? arg : selectedId;
            result.putString("subscriptions", RelayLibraryReporter.latestPayload(ACTION_SUBSCRIPTIONS, requestedId));
            result.putString("continue_watching", RelayLibraryReporter.latestPayload(ACTION_CONTINUE, requestedId));
        }
        return result;
    }

    private void select(String requestedId) {
        SignInService signIn = YouTubeServiceManager.instance().getSignInService();
        for (Account account : signIn.getAccounts()) {
            if (requestedId.equals(RelayLibraryReporter.profileId(account))) {
                signIn.selectAccount(account);
                RelayLibraryReporter.refresh(appContext());
                return;
            }
        }
    }

    private boolean isRelayCaller() {
        if (getContext() == null) return false;
        PackageManager manager = getContext().getPackageManager();
        String[] packages = manager.getPackagesForUid(Binder.getCallingUid());
        if (packages == null) return false;
        for (String packageName : packages) if (RELAY_PACKAGE.equals(packageName)) return true;
        return false;
    }

    private android.content.Context appContext() {
        if (getContext() == null) throw new IllegalStateException("Provider context unavailable");
        return getContext().getApplicationContext();
    }

    @Nullable @Override public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) { return null; }
    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
}
