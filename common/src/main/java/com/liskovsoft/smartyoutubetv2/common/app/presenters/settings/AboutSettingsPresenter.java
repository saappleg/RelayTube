package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.locale.LocaleUtility;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.ATVBridgePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AmazonBridgePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AppUpdatePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.update.UpdateChannelManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class AboutSettingsPresenter extends BasePresenter<Void> {
    private final AppUpdateChecker mUpdateChecker;

    public AboutSettingsPresenter(Context context) {
        super(context);

        mUpdateChecker = new AppUpdateChecker(getContext(), null);
    }

    public static AboutSettingsPresenter instance(Context context) {
        return new AboutSettingsPresenter(context);
    }

    public void show() {
        String mainTitle = String.format("%s %s",
                getContext().getString(R.string.app_name),
                AppInfoHelpers.getAppVersionName(getContext()));

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        String country = LocaleUtility.getCurrentLocale(getContext()).getCountry();

        appendUpdateCheckButton(settingsPresenter);

        appendAutoUpdateSwitch(settingsPresenter);

        appendUpdateChangelogButton(settingsPresenter);

        appendUpdateChannel(settingsPresenter);

        appendInstallBridge(settingsPresenter);

        if (!Helpers.equalsAny(country, "RU", "UA")) {
            appendDonation(settingsPresenter);
            appendFeedback(settingsPresenter);
            appendLinks(settingsPresenter);
        }

        settingsPresenter.showDialog(mainTitle);
    }

    private void appendAutoUpdateSwitch(AppDialogPresenter settingsPresenter) {
        GeneralData generalData = GeneralData.instance(getContext());
        List<OptionItem> items = new ArrayList<>();

        items.add(UiOptionItem.from(getContext().getString(R.string.option_disabled),
                optionItem -> mUpdateChecker.setUpdateCheckEnabled(false),
                !mUpdateChecker.isUpdateCheckEnabled()));

        items.add(UiOptionItem.from(getContext().getString(R.string.sidebar_notification), optionItem -> {
            mUpdateChecker.setUpdateCheckEnabled(true);
            generalData.setOldUpdateNotificationsEnabled(false);
        }, mUpdateChecker.isUpdateCheckEnabled() && !generalData.isOldUpdateNotificationsEnabled()));
        
        items.add(UiOptionItem.from(getContext().getString(R.string.dialog_notification), optionItem -> {
            mUpdateChecker.setUpdateCheckEnabled(true);
            generalData.setOldUpdateNotificationsEnabled(true);
        }, mUpdateChecker.isUpdateCheckEnabled() && generalData.isOldUpdateNotificationsEnabled()));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.check_updates_auto), items);
    }

    private void appendUpdateCheckButton(AppDialogPresenter settingsPresenter) {
        OptionItem updateCheckOption = UiOptionItem.from(
                getContext().getString(R.string.check_for_updates),
                option -> AppUpdatePresenter.instance(getContext()).start(true));

        settingsPresenter.appendSingleButton(updateCheckOption);
    }

    private void appendUpdateChangelogButton(AppDialogPresenter settingsPresenter) {
        List<String> changes = GeneralData.instance(getContext()).getChangelog();

        if (changes == null || changes.isEmpty()) {
            return;
        }

        List<OptionItem> changelog = new ArrayList<>();

        for (String change : changes) {
            changelog.add(UiOptionItem.from(change));
        }

        String title = String.format("%s %s",
                getContext().getString(R.string.update_changelog),
                AppInfoHelpers.getAppVersionName(getContext()));

        settingsPresenter.appendStringsCategory(title, changelog);
    }

    private void appendLinks(AppDialogPresenter settingsPresenter) {
        OptionItem releasesOption = UiOptionItem.from(getContext().getString(R.string.releases),
                option -> Utils.openLink(getContext(), Utils.toQrCodeLink(getContext().getString(R.string.releases_url))));

        OptionItem sourcesOption = UiOptionItem.from(getContext().getString(R.string.sources),
                option -> Utils.openLink(getContext(), Utils.toQrCodeLink(getContext().getString(R.string.sources_url))));

        //OptionItem webSiteOption = UiOptionItem.from(getContext().getString(R.string.web_site),
        //        option -> Utils.openLink(getContext(), Utils.toQrCodeLink(getContext().getString(R.string.web_site_url))));

        settingsPresenter.appendSingleButton(releasesOption);
        settingsPresenter.appendSingleButton(sourcesOption);
        //settingsPresenter.appendSingleButton(webSiteOption);
    }

    private void appendDonation(AppDialogPresenter settingsPresenter) {
        List<OptionItem> donateOptions = new ArrayList<>();

        Map<String, String> donations = Helpers.getMap(getContext(), R.array.donations);

        for (Entry<String, String> entry : donations.entrySet()) {
            donateOptions.add(UiOptionItem.from(
                    entry.getKey(),
                    option -> Utils.openLink(getContext(), Utils.toQrCodeLink(entry.getValue()))));
        }

        if (!donateOptions.isEmpty()) {
            settingsPresenter.appendStringsCategory(getContext().getString(R.string.donation), donateOptions);
        }
    }

    private void appendUpdateChannel(AppDialogPresenter settingsPresenter) {
        UpdateChannelManager channelManager = new UpdateChannelManager(getContext());
        List<OptionItem> options = new ArrayList<>();

        if (channelManager.getAvailableChannels().isEmpty()) {
            return;
        }

        String selectedChannel = channelManager.getSelectedChannel();

        for (String channel : channelManager.getAvailableChannels()) {
            options.add(UiOptionItem.from(channelManager.getChannelLabel(channel),
                    optionItem -> channelManager.setSelectedChannel(channel),
                    Helpers.equals(channel, selectedChannel)));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.update_channel), options);
    }

    private void appendFeedback(AppDialogPresenter settingsPresenter) {
        List<OptionItem> feedbackOptions = new ArrayList<>();

        Map<String, String> feedback = Helpers.getMap(getContext(), R.array.feedback);

        for (Entry<String, String> entry : feedback.entrySet()) {
            feedbackOptions.add(UiOptionItem.from(
                    entry.getKey(),
                    option -> Utils.openLink(getContext(), Utils.toQrCodeLink(entry.getValue()))));
        }

        if (!feedbackOptions.isEmpty()) {
            settingsPresenter.appendStringsCategory(getContext().getString(R.string.feedback), feedbackOptions);
        }
    }

    private void appendInstallBridge(AppDialogPresenter settingsPresenter) {
        OptionItem installBridgeOption = UiOptionItem.from(
                getContext().getString(R.string.install_bridge),
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
