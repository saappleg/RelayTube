package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.os.Build.VERSION;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Color;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.compose.RelayComposeViews;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class SettingsCardPresenter extends Presenter {
    private int mDefaultBackgroundColor;
    private int mDefaultTextColor;
    private int mSelectedBackgroundColor;
    private int mSelectedTextColor;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();

        mDefaultBackgroundColor =
                ContextCompat.getColor(context, Helpers.getThemeAttr(context, R.attr.cardDefaultBackground));
        mDefaultTextColor =
                MaterialThemeColors.color(context, R.attr.materialOnSurface, Color.WHITE);
        mSelectedBackgroundColor =
                MaterialThemeColors.color(context, R.attr.materialFocusSurface,
                        ContextCompat.getColor(context, R.color.card_selected_background_white));
        mSelectedTextColor =
                MaterialThemeColors.color(context, R.attr.materialOnPrimary, Color.BLACK);

        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            FrameLayout container = new FrameLayout(context);
            container.setFocusable(true);
            container.setFocusableInTouchMode(true);

            ComposeView composeView = new ComposeView(context);
            int cardWidth = context.getResources().getDimensionPixelSize(R.dimen.settings_card_width);
            int cardHeight = context.getResources().getDimensionPixelSize(R.dimen.settings_card_height);
            container.addView(composeView, new FrameLayout.LayoutParams(cardWidth, cardHeight));

            ComposeSettingsCardViewHolder viewHolder = new ComposeSettingsCardViewHolder(container, composeView);
            container.setOnFocusChangeListener((v, hasFocus) -> viewHolder.setFocused(hasFocus));
            return viewHolder;
        }

        @SuppressLint("InflateParams")
        View container = LayoutInflater.from(context).inflate(R.layout.settings_card, null);
        container.setBackgroundColor(mDefaultBackgroundColor);
        //if (VERSION.SDK_INT >= 23 && MainUIData.instance(context).isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS)) {
        //    container.setForeground(ContextCompat.getDrawable(context, R.drawable.lb_card_outline));
        //}

        TextView textView = container.findViewById(R.id.settings_title);
        textView.setBackgroundColor(mDefaultBackgroundColor);
        textView.setTextColor(mDefaultTextColor);

        ViewUtil.setTextScrollSpeed(textView, getCardTextScrollSpeed(context));

        container.setOnFocusChangeListener((v, hasFocus) -> {
            int backgroundColor = hasFocus ? mSelectedBackgroundColor : mDefaultBackgroundColor;
            int textColor = hasFocus ? mSelectedTextColor : mDefaultTextColor;
            
            textView.setBackgroundColor(backgroundColor);
            textView.setTextColor(textColor);

            if (hasFocus) {
                ViewUtil.enableMarquee(textView);
            } else {
                ViewUtil.disableMarquee(textView);
            }
        });

        return new ViewHolder(container);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        SettingsItem settingsItem = (SettingsItem) item;

        if (viewHolder instanceof ComposeSettingsCardViewHolder) {
            ((ComposeSettingsCardViewHolder) viewHolder).bind(settingsItem);
            viewHolder.view.setContentDescription(settingsItem.title);
            return;
        }

        TextView textView = viewHolder.view.findViewById(R.id.settings_title);

        textView.setText(settingsItem.title);

        if (settingsItem.imageResId > 0) {
            Context context = viewHolder.view.getContext();
            ImageView imageView = viewHolder.view.findViewById(R.id.settings_image);
            imageView.setImageDrawable(ContextCompat.getDrawable(context, settingsItem.imageResId));
            imageView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        if (viewHolder instanceof ComposeSettingsCardViewHolder) {
            ((ComposeSettingsCardViewHolder) viewHolder).clear();
        }
    }

    protected boolean isCardTextAutoScrollEnabled(Context context) {
        return MainUIData.instance(context).isCardTextAutoScrollEnabled();
    }

    protected float getCardTextScrollSpeed(Context context) {
        return MainUIData.instance(context).getCardTextScrollSpeed();
    }

    private static final class ComposeSettingsCardViewHolder extends ViewHolder {
        private final ComposeView mComposeView;
        private SettingsItem mItem;
        private boolean mFocused;

        ComposeSettingsCardViewHolder(View container, ComposeView composeView) {
            super(container);
            mComposeView = composeView;
        }

        void bind(SettingsItem item) {
            mItem = item;
            render();
        }

        void setFocused(boolean focused) {
            mFocused = focused;
            if (mItem != null) {
                render();
            }
        }

        void clear() {
            mItem = null;
            mComposeView.disposeComposition();
        }

        private void render() {
            RelayComposeViews.renderSettingsCard(
                    mComposeView,
                    mItem != null ? mItem.title : null,
                    mItem != null ? mItem.imageResId : 0,
                    mFocused);
        }
    }
}
