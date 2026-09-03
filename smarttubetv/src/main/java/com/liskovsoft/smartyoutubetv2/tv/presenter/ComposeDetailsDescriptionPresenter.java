package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.compose.ui.platform.ComposeView;
import androidx.leanback.widget.Presenter;

import com.liskovsoft.smartyoutubetv2.tv.ui.compose.RelayComposeViews;

/**
 * Details presenter that keeps the Leanback presenter contract while rendering its content in
 * Compose on API 21+. The small native fallback keeps the app's existing minSdk usable.
 */
public class ComposeDetailsDescriptionPresenter extends Presenter {
    public interface TextProvider {
        CharSequence getTitle(Object item);

        CharSequence getSubtitle(Object item);

        CharSequence getBody(Object item);
    }

    public interface ViewHolderListener {
        void onBound(ViewHolder viewHolder);
    }

    private final TextProvider mTextProvider;
    private final ViewHolderListener mViewHolderListener;

    public ComposeDetailsDescriptionPresenter(TextProvider textProvider) {
        this(textProvider, null);
    }

    public ComposeDetailsDescriptionPresenter(TextProvider textProvider,
                                              ViewHolderListener viewHolderListener) {
        mTextProvider = textProvider;
        mViewHolderListener = viewHolderListener;
    }

    @Override
    public ViewHolder onCreateViewHolder(android.view.ViewGroup parent) {
        Context context = parent.getContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ComposeView composeView = new ComposeView(context);
            composeView.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(composeView);
        }

        LinearLayout nativeView = new LinearLayout(context);
        nativeView.setOrientation(LinearLayout.VERTICAL);
        nativeView.setPadding(18, 12, 18, 12);
        return new ViewHolder(nativeView, createTextView(context, 22),
                createTextView(context, 16), createTextView(context, 14));
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder holder, Object item) {
        ViewHolder viewHolder = (ViewHolder) holder;
        viewHolder.bind(
                mTextProvider != null ? mTextProvider.getTitle(item) : null,
                mTextProvider != null ? mTextProvider.getSubtitle(item) : null,
                mTextProvider != null ? mTextProvider.getBody(item) : null);
        if (mViewHolderListener != null) {
            mViewHolderListener.onBound(viewHolder);
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder holder) {
        ((ViewHolder) holder).clear();
    }

    private static TextView createTextView(Context context, int textSize) {
        TextView textView = new TextView(context);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(textSize);
        return textView;
    }

    public static final class ViewHolder extends Presenter.ViewHolder {
        private final ComposeView mComposeView;
        private final TextView mTitleView;
        private final TextView mSubtitleView;
        private final TextView mBodyView;
        private CharSequence mTitle;
        private CharSequence mSubtitle;
        private CharSequence mBody;

        private ViewHolder(ComposeView view) {
            this(view, null, null, null);
        }

        private ViewHolder(View view, TextView titleView, TextView subtitleView, TextView bodyView) {
            super(view);
            mComposeView = view instanceof ComposeView ? (ComposeView) view : null;
            mTitleView = titleView;
            mSubtitleView = subtitleView;
            mBodyView = bodyView;

            if (mTitleView != null) {
                ((LinearLayout) view).addView(mTitleView);
                ((LinearLayout) view).addView(mSubtitleView);
                ((LinearLayout) view).addView(mBodyView);
            }
        }

        private void bind(CharSequence title, CharSequence subtitle, CharSequence body) {
            mTitle = title;
            mSubtitle = subtitle;
            mBody = body;

            if (mComposeView != null) {
                RelayComposeViews.renderVideoDetails(mComposeView, mTitle, mSubtitle, mBody);
            } else {
                mTitleView.setText(mTitle);
                mSubtitleView.setText(mSubtitle);
                mBodyView.setText(mBody);
            }
        }

        public void setBody(CharSequence body) {
            mBody = body;
            if (mComposeView != null) {
                RelayComposeViews.renderVideoDetails(mComposeView, mTitle, mSubtitle, mBody);
            } else {
                mBodyView.setText(body);
            }
        }

        private void clear() {
            mTitle = null;
            mSubtitle = null;
            mBody = null;
            if (mComposeView != null) {
                mComposeView.disposeComposition();
            } else {
                mTitleView.setText(null);
                mSubtitleView.setText(null);
                mBodyView.setText(null);
            }
        }
    }
}
