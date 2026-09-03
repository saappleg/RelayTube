package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Build.VERSION;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.material.MaterialYouColors;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class SettingsCardPresenter extends Presenter {
    private int mDefaultBackgroundColor;
    private int mDefaultTextColor;
    private int mSelectedBackgroundColor;
    private int mSelectedTextColor;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();

        mDefaultBackgroundColor = MaterialYouColors.surfaceContainerHigh(context);
        mDefaultTextColor = MaterialYouColors.onSurface(context);
        mSelectedBackgroundColor = MaterialYouColors.focusedCardSurface(context);
        // Focused cards use the dark scheme-specific focus surface. Keep their text on the
        // scheme's light surface token so pale accents (grey, blue, and monochrome) do not
        // render dark text on a dark focused card.
        mSelectedTextColor = MaterialYouColors.onSurface(context);

        @SuppressLint("InflateParams")
        View container = LayoutInflater.from(context).inflate(R.layout.settings_card, null);
        container.setBackground(MaterialYouColors.roundedSurface(
                context, mDefaultBackgroundColor, 20));

        TextView textView = container.findViewById(R.id.settings_title);
        textView.setBackgroundColor(Color.TRANSPARENT);
        textView.setTextColor(mDefaultTextColor);

        ViewUtil.setTextScrollSpeed(textView, getCardTextScrollSpeed(context));

        container.setOnFocusChangeListener((v, hasFocus) -> {
            int backgroundColor = hasFocus ? mSelectedBackgroundColor : mDefaultBackgroundColor;
            int textColor = hasFocus ? mSelectedTextColor : mDefaultTextColor;
            v.setBackground(MaterialYouColors.roundedSurface(
                    context, backgroundColor, 20));
            if (VERSION.SDK_INT >= 23) {
                v.setForeground(MaterialYouColors.outlinedSurface(
                        context,
                        Color.TRANSPARENT,
                        20,
                        hasFocus ? MaterialYouColors.focusedCardOutline(context) : Color.TRANSPARENT,
                        hasFocus ? 2.0f : 0.0f));
            }
            if (VERSION.SDK_INT >= 21) {
                v.setElevation(hasFocus ? dp(context, 10) : dp(context, 2));
            }

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

        TextView textView = viewHolder.view.findViewById(R.id.settings_title);

        textView.setText(settingsItem.title);
        viewHolder.view.setContentDescription(settingsItem.title);

        if (settingsItem.imageResId > 0) {
            Context context = viewHolder.view.getContext();
            ImageView imageView = viewHolder.view.findViewById(R.id.settings_image);
            imageView.setImageDrawable(ContextCompat.getDrawable(context, settingsItem.imageResId));
            imageView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }

    protected boolean isCardTextAutoScrollEnabled(Context context) {
        return MainUIData.instance(context).isCardTextAutoScrollEnabled();
    }

    protected float getCardTextScrollSpeed(Context context) {
        return MainUIData.instance(context).getCardTextScrollSpeed();
    }

    private static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }
}
