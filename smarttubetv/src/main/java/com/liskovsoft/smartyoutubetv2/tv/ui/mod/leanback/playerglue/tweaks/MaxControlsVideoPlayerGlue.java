package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerView;
import com.liskovsoft.smartyoutubetv2.tv.presenter.ComposeDetailsDescriptionPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.framedrops.PlaybackBaseControlGlue;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.framedrops.PlaybackTransportControlGlue;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.PlaybackTransportRowPresenter.TopEdgeFocusListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.PlaybackTransportRowPresenter.ViewHolder;

import java.lang.ref.WeakReference;
import java.util.List;

public abstract class MaxControlsVideoPlayerGlue<T extends PlayerAdapter>
        extends PlaybackTransportControlGlue<T> implements TopEdgeFocusListener, PlayerView {
    private String mQualityInfo;
    private Video mVideo;
    private WeakReference<PlaybackTransportRowPresenter.ViewHolder> mTransportViewHolder;
    private WeakReference<ComposeDetailsDescriptionPresenter.ViewHolder> mDescriptionViewHolder;

    /**
     * Constructor for the glue.
     *
     * @param context context
     * @param impl    Implementation to underlying media player.
     */
    public MaxControlsVideoPlayerGlue(Context context, T impl) {
        super(context, impl);
    }

    @Override
    protected PlaybackRowPresenter onCreateRowPresenter() {
        final ComposeDetailsDescriptionPresenter detailsPresenter =
                new ComposeDetailsDescriptionPresenter(new ComposeDetailsDescriptionPresenter.TextProvider() {
                    @Override
                    public CharSequence getTitle(Object item) {
                        return item instanceof PlaybackBaseControlGlue
                                ? ((PlaybackBaseControlGlue<?>) item).getTitle() : null;
                    }

                    @Override
                    public CharSequence getSubtitle(Object item) {
                        return item instanceof PlaybackBaseControlGlue
                                ? ((PlaybackBaseControlGlue<?>) item).getSubtitle() : null;
                    }

                    @Override
                    public CharSequence getBody(Object item) {
                        return null;
                    }
                }, new ComposeDetailsDescriptionPresenter.ViewHolderListener() {
                    @Override
                    public void onBound(ComposeDetailsDescriptionPresenter.ViewHolder viewHolder) {
                        mDescriptionViewHolder = new WeakReference<>(viewHolder);
                    }
                });

        PlaybackTransportRowPresenter rowPresenter = new PlaybackTransportRowPresenter() {
            @Override
            protected void onBindRowViewHolder(RowPresenter.ViewHolder vh, Object item) {
                super.onBindRowViewHolder(vh, item);
                vh.setOnKeyListener(MaxControlsVideoPlayerGlue.this);

                ViewHolder viewHolder = (ViewHolder) vh;
                mTransportViewHolder = new WeakReference<>(viewHolder);

                viewHolder.setTopEdgeFocusListener(MaxControlsVideoPlayerGlue.this);
                viewHolder.setQualityInfo(mQualityInfo);
                viewHolder.setDateVisibility(isControlsVisible());
                // Don't uncomment
                // Reset to defaults
                //viewHolder.setSeekPreviewTitle(null);
                // Don't uncomment
                //viewHolder.setSeekBarSegments(null);
            }
            @Override
            protected void onUnbindRowViewHolder(RowPresenter.ViewHolder vh) {
                super.onUnbindRowViewHolder(vh);
                vh.setOnKeyListener(null);
            }
        };
        rowPresenter.setDescriptionPresenter(detailsPresenter);
        return rowPresenter;
    }

    @Override
    public void setControlsVisibility(boolean show) {
        super.setControlsVisibility(show);

        if (getTransportViewHolder() != null) {
            getTransportViewHolder().setDateVisibility(show);
        }
    }

    @Override
    public void setQualityInfo(String info) {
        mQualityInfo = info;

        if (getTransportViewHolder() != null) {
            getTransportViewHolder().setQualityInfo(info);
        }
    }

    @Override
    public void setVideo(Video video) {
        mVideo = video;
    }

    @Override
    public void play() {
        super.play();

        if (getTransportViewHolder() != null) {
            getTransportViewHolder().setPlay(true);
        }
    }

    @Override
    public void pause() {
        super.pause();

        if (getTransportViewHolder() != null) {
            getTransportViewHolder().setPlay(false);
        }
    }

    @Override
    protected void onUpdateControlsVisibility() {
        super.onUpdateControlsVisibility();

        if (isControlsVisible()) {
            updateLiveEndingTime();
        }
    }

    @Override
    protected void onUpdateProgress() {
        super.onUpdateProgress();

        if (isControlsVisible()) {
            updateLiveEndingTime();
        }
    }

    public void setSeekPreviewTitle(String title) {
        if (getTransportViewHolder() != null) { // the chapter title when show seeking ui
            getTransportViewHolder().setSeekPreviewTitle(title);
        }
        if (getDescriptionViewHolder() != null) { // the chapter title when show full ui
            getDescriptionViewHolder().setBody(title);
        }
    }

    public void setSeekBarSegments(List<SeekBarSegment> segments) {
        if (getTransportViewHolder() != null) {
            getTransportViewHolder().setSeekBarSegments(segments);
        }
    }

    private void updateLiveEndingTime() {
        if (mVideo == null) {
            return;
        }

        long liveDurationMs = mVideo.getLiveDurationMs();

        if (liveDurationMs == 0) {
            return;
        }

        PlaybackControlsRow controlsRow = getControlsRow();
        PlayerAdapter playerAdapter = getPlayerAdapter();

        if (controlsRow == null || playerAdapter == null) {
            return;
        }

        // Apply duration on videos with uncommon length.
        if (playerAdapter.getDuration() > Video.MAX_LIVE_DURATION_MS) {
            controlsRow.setDuration(
                    playerAdapter.isPrepared() ? liveDurationMs : -1);
        }
    }

    private ViewHolder getTransportViewHolder() {
        return mTransportViewHolder != null ? mTransportViewHolder.get() : null;
    }

    private ComposeDetailsDescriptionPresenter.ViewHolder getDescriptionViewHolder() {
        return mDescriptionViewHolder != null ? mDescriptionViewHolder.get() : null;
    }

    public abstract void onTopEdgeFocused();
}
