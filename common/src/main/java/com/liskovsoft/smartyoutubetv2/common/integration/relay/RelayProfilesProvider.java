package com.liskovsoft.smartyoutubetv2.common.integration.relay;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

/** ContentProvider endpoint used by Relay Home for synchronous profile/feed reads. */
public class RelayProfilesProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        RelayHomeBridge.initialize(getContext());
        return true;
    }

    @Nullable
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (getContext() == null || method == null) {
            return null;
        }

        switch (method) {
            case RelayHomeBridge.METHOD_PROFILES:
                return RelayHomeBridge.getProfiles(getContext());
            case RelayHomeBridge.METHOD_SELECT:
                RelayHomeBridge.selectProfile(getContext(), arg);
                return RelayHomeBridge.getProfiles(getContext());
            case RelayHomeBridge.METHOD_FEEDS:
                return RelayHomeBridge.getFeeds(getContext(), arg);
            default:
                return null;
        }
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
