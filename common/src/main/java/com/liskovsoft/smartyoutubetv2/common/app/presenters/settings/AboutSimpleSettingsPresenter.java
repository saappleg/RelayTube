package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.ATVBridgePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AmazonBridgePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AppUpdatePresenter;
import com.liskovsoft.smartyoutubetv2.common.integration.relay.RelayUpdatePreferences;
import com.liskovsoft.smartyoutubetv2.common.integration.relay.RelayUpdatePreferences.Channel;

import java.util.ArrayList;
import java.util.List;

public class AboutSimpleSettingsPresenter extends BasePresenter<Void> {
    private final AppUpdateChecker mUpdateChecker;

    public AboutSimpleSettingsPresenter(Context context) {
        super(context);

        mUpdateChecker = new AppUpdateChecker(getContext(), null);
    }

    public static AboutSimpleSettingsPresenter instance(Context context) {
        return new AboutSimpleSettingsPresenter(context);
    }

    public void show() {
        String appName = getContext().getString(R.string.app_name);
        String mainTitle = String.format("%s %s",
                RelayUpdatePreferences.isRelayTube(getContext()) ? appName : appName + " MOD",
                AppInfoHelpers.getAppVersionName(getContext()));

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendAutoUpdateSwitch(settingsPresenter);

        appendRelayUpdateChannel(settingsPresenter);

        appendUpdateCheckButton(settingsPresenter);

        appendInstallBridge(settingsPresenter);

        settingsPresenter.showDialog(mainTitle);
    }

    private void appendAutoUpdateSwitch(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.check_updates_auto), optionItem -> {
            mUpdateChecker.setUpdateCheckEnabled(optionItem.isSelected());
        }, mUpdateChecker.isUpdateCheckEnabled()));
    }

    private void appendUpdateCheckButton(AppDialogPresenter settingsPresenter) {
        OptionItem updateCheckOption = UiOptionItem.from(
                getContext().getString(R.string.check_for_updates),
                option -> AppUpdatePresenter.instance(getContext()).start(true));

        settingsPresenter.appendSingleButton(updateCheckOption);
    }

    private void appendRelayUpdateChannel(AppDialogPresenter settingsPresenter) {
        if (!RelayUpdatePreferences.isRelayTube(getContext())) {
            return;
        }

        RelayUpdatePreferences preferences = RelayUpdatePreferences.instance(getContext());
        List<OptionItem> channels = new ArrayList<>();

        if (preferences.supportsChannel(Channel.ALPHA)) {
            channels.add(UiOptionItem.from(getContext().getString(R.string.relay_update_channel_alpha),
                    option -> preferences.setChannel(Channel.ALPHA),
                    preferences.getChannel() == Channel.ALPHA));
        }
        if (preferences.supportsChannel(Channel.STABLE)) {
            channels.add(UiOptionItem.from(getContext().getString(R.string.relay_update_channel_stable),
                    option -> preferences.setChannel(Channel.STABLE),
                    preferences.getChannel() == Channel.STABLE));
        }
        if (preferences.supportsChannel(Channel.BETA)) {
            channels.add(UiOptionItem.from(getContext().getString(R.string.relay_update_channel_beta),
                    option -> preferences.setChannel(Channel.BETA),
                    preferences.getChannel() == Channel.BETA));
        }

        settingsPresenter.appendRadioCategory(
                getContext().getString(R.string.relay_update_channel), channels);
    }

    private void appendInstallBridge(AppDialogPresenter settingsPresenter) {
        OptionItem installBridgeOption = UiOptionItem.from(
                getContext().getString(R.string.enable_voice_search),
                option -> startBridgePresenter());

        settingsPresenter.appendSingleButton(installBridgeOption);
    }

    private void startBridgePresenter() {
        MessageHelpers.showLongMessage(getContext(), R.string.enable_voice_search_desc);

        ATVBridgePresenter atvPresenter = ATVBridgePresenter.instance(getContext());
        atvPresenter.runBridgeInstaller(true);
        atvPresenter.unhold();

        AmazonBridgePresenter amazonPresenter = AmazonBridgePresenter.instance(getContext());
        amazonPresenter.runBridgeInstaller(true);
        amazonPresenter.unhold();
    }
}
