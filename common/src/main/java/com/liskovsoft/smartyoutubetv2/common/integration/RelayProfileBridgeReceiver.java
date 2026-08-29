package com.liskovsoft.smartyoutubetv2.common.integration;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

/** Signature-protected account switch used by Relay Home. No credentials leave RelayTube. */
public final class RelayProfileBridgeReceiver extends BroadcastReceiver {
    public static final String ACTION_SELECT_PROFILE = "com.relaytube.action.SELECT_PROFILE";
    public static final String ACTION_REQUEST_PROFILES = "com.relaytube.action.REQUEST_PROFILES";
    public static final String EXTRA_PROFILE_ID = "profile_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (ACTION_REQUEST_PROFILES.equals(intent.getAction())) {
            RelayLibraryReporter.publishProfiles(context.getApplicationContext());
            return;
        }
        if (!ACTION_SELECT_PROFILE.equals(intent.getAction())) return;

        String requestedId = intent.getStringExtra(EXTRA_PROFILE_ID);
        if (requestedId == null || requestedId.trim().isEmpty()) return;
        SignInService signIn = YouTubeServiceManager.instance().getSignInService();
        Account match = null;
        for (Account account : signIn.getAccounts()) {
            if (requestedId.equals(RelayLibraryReporter.profileId(account))) {
                match = account;
                break;
            }
        }
        if (match == null) return;
        signIn.selectAccount(match);
        RelayLibraryReporter.publishProfiles(context.getApplicationContext());
        RelayLibraryReporter.refresh(context.getApplicationContext());
    }
}
