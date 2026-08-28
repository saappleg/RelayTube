/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.leanback.R;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.PaddingAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.material.MaterialYouColors;

/**
 * Displays primary and secondary controls for a {@link PlaybackControlsRow}.
 *
 * Binds to items of type {@link Action}.
 */
public class ControlButtonPresenterSelector extends PresenterSelector {
    private final ControlButtonPresenter mPrimaryPresenter =
            new ControlButtonPresenter(R.layout.lb_control_button_primary);
    private final ControlButtonPresenter mSecondaryPresenter =
            new ControlButtonPresenter(R.layout.lb_control_button_secondary);
    private final Presenter[] mPresenters = new Presenter[]{mPrimaryPresenter};

    public ControlButtonPresenterSelector(boolean tooltipsEnabled) {
        mPrimaryPresenter.tooltipsEnabled = mSecondaryPresenter.tooltipsEnabled = tooltipsEnabled;
    }

    /**
     * Returns the presenter for primary controls.
     */
    public Presenter getPrimaryPresenter() {
        return mPrimaryPresenter;
    }

    /**
     * Returns the presenter for secondary controls.
     */
    public Presenter getSecondaryPresenter() {
        return mSecondaryPresenter;
    }

    /**
     * Always returns the presenter for primary controls.
     */
    @Override
    public Presenter getPresenter(Object item) {
        return mPrimaryPresenter;
    }

    @Override
    public Presenter[] getPresenters() {
        return mPresenters;
    }

    static class ActionViewHolder extends Presenter.ViewHolder {
        ImageView mIcon;
        TextView mLabel;
        View mFocusableView;

        public ActionViewHolder(View view) {
            super(view);
            mIcon = (ImageView) view.findViewById(R.id.icon);
            mLabel = (TextView) view.findViewById(R.id.label);
            mFocusableView = view.findViewById(R.id.button);
        }
    }

    public static class ControlButtonPresenter extends Presenter {
        private int mLayoutResourceId;
        private boolean tooltipsEnabled;

        ControlButtonPresenter(int layoutResourceId) {
            mLayoutResourceId = layoutResourceId;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(mLayoutResourceId, parent, false);
            ImageView button = v.findViewById(R.id.button);
            if (button != null) {
                button.setImageDrawable(null);
                button.setBackground(MaterialYouColors.playerControlSurface(parent.getContext()));
                button.setImageAlpha(255);
                button.setAlpha(1f);
            }
            return new ActionViewHolder(v);
        }

        // Used inside: com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.ControlBarPresenter.ViewHolder.bindControlToAction()
        // Restore focus: com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.ControlBar.onRequestFocusInDescendants()
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            Action action = (Action) item;
            ActionViewHolder vh = (ActionViewHolder) viewHolder;

            vh.view.setAlpha(1f);
            vh.mIcon.setImageDrawable(action.getIcon());
            if (isColorArtwork(action.getIcon())) {
                vh.mIcon.clearColorFilter();
            } else {
                vh.mIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            }
            vh.mIcon.setAlpha(1f);
            vh.mIcon.setImageAlpha(255);
            if (action instanceof PaddingAction) {
                int padding = ((PaddingAction) action).getPadding();
                if (padding > 0) {
                    vh.mIcon.setPadding(padding, padding, padding, padding);
                }
            }
            if (vh.mLabel != null) {
                if (action.getIcon() == null) {
                    vh.mLabel.setText(action.getLabel1());
                } else {
                    vh.mLabel.setText(null);
                }
            }
            CharSequence contentDescription = TextUtils.isEmpty(action.getLabel2())
                    ? action.getLabel1() : action.getLabel2();
            if (!TextUtils.equals(vh.mFocusableView.getContentDescription(), contentDescription)) {
                vh.mFocusableView.setContentDescription(contentDescription);
                vh.mFocusableView.sendAccessibilityEvent(
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);

                // MOD: enable control tooltips
                if (tooltipsEnabled) {
                    TooltipCompatHandler.setTooltipText(vh.mFocusableView, action.getLabel1());
                }
            }
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
            ActionViewHolder vh = (ActionViewHolder) viewHolder;
            vh.mIcon.clearColorFilter();
            vh.mIcon.setImageDrawable(null);
            if (vh.mLabel != null) {
                vh.mLabel.setText(null);
            }
            vh.mFocusableView.setContentDescription(null);
        }

        @Override
        public void setOnClickListener(ViewHolder viewHolder,
                                       View.OnClickListener listener) {
            ((ActionViewHolder) viewHolder).mFocusableView.setOnClickListener(listener);
        }

        public void setOnLongClickListener(ViewHolder viewHolder,
                                       View.OnLongClickListener listener) {
            ((ActionViewHolder) viewHolder).mFocusableView.setOnLongClickListener(listener);
        }

        /** Keep channel art and colored state icons intact; lift only monochrome glyphs. */
        private static boolean isColorArtwork(Drawable drawable) {
            if (!(drawable instanceof BitmapDrawable)) {
                return false;
            }
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap == null || bitmap.isRecycled()) {
                return false;
            }
            int stepX = Math.max(1, bitmap.getWidth() / 12);
            int stepY = Math.max(1, bitmap.getHeight() / 12);
            for (int y = 0; y < bitmap.getHeight(); y += stepY) {
                for (int x = 0; x < bitmap.getWidth(); x += stepX) {
                    int pixel = bitmap.getPixel(x, y);
                    if (Color.alpha(pixel) < 48) {
                        continue;
                    }
                    int max = Math.max(Color.red(pixel),
                            Math.max(Color.green(pixel), Color.blue(pixel)));
                    int min = Math.min(Color.red(pixel),
                            Math.min(Color.green(pixel), Color.blue(pixel)));
                    if (max - min >= 28) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
