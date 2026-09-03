package com.liskovsoft.smartyoutubetv2.common.integration.relay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives Relay Home profile requests and selections. */
public class RelayProfileReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        switch (intent.getAction()) {
            case RelayHomeBridge.ACTION_SELECT_PROFILE:
                RelayHomeBridge.selectProfile(context, intent.getStringExtra(RelayHomeBridge.EXTRA_PROFILE_ID));
                break;
            case RelayHomeBridge.ACTION_REQUEST_PROFILES:
                RelayHomeBridge.initialize(context);
                RelayHomeBridge.publishProfiles(context);
                RelayHomeBridge.refreshFeeds(context);
                break;
            default:
                break;
        }
    }
}
