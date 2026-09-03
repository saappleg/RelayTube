package com.liskovsoft.smartyoutubetv2.tv.ui.material;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/** Shared Material TV tokens for Leanback views and presenters. */
public final class MaterialYouColors {
    private MaterialYouColors() {}

    @ColorInt
    public static int surface(Context context) {
        return themeColor(context, R.attr.materialSurface, R.color.relay_surface);
    }

    /** Browse rail base; this is the same scheme-specific surface used behind the content. */
    @ColorInt
    public static int railSurface(Context context) {
        return themeColor(context, R.attr.shelfBackground, R.color.relay_background);
    }

    @ColorInt
    public static int surfaceVariant(Context context) {
        return themeColor(context, R.attr.materialSurfaceVariant, R.color.relay_surface_variant);
    }

    @ColorInt
    public static int accent(Context context) {
        return themeColor(context, R.attr.materialPrimary, R.color.relay_primary);
    }

    @ColorInt
    public static int onPrimary(Context context) {
        return themeColor(context, R.attr.materialOnPrimary, R.color.relay_on_primary);
    }

    @ColorInt
    public static int onSurface(Context context) {
        return themeColor(context, R.attr.materialOnSurface, R.color.relay_on_surface);
    }

    @ColorInt
    public static int accentContainer(Context context) {
        return themeColor(context, R.attr.materialPrimaryContainer, R.color.relay_primary_container);
    }

    @ColorInt
    public static int secondary(Context context) {
        return themeColor(context, R.attr.materialSecondary, R.color.relay_secondary);
    }

    @ColorInt
    public static int surfaceContainerHigh(Context context) {
        return themeColor(context, R.attr.materialSurfaceElevated, R.color.relay_surface_elevated);
    }

    @ColorInt
    public static int focusedCardSurface(Context context) {
        return blend(surfaceContainerHigh(context), accent(context), 0.10f);
    }

    @ColorInt
    public static int focusedCardOutline(Context context) {
        return blend(accent(context), Color.WHITE, 0.28f);
    }

    @ColorInt
    public static int outline(Context context) {
        return themeColor(context, R.attr.materialOutline, R.color.relay_outline);
    }

    @ColorInt
    public static int withAlpha(@ColorInt int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    @ColorInt
    public static int blend(@ColorInt int from, @ColorInt int to, float amount) {
        float clamped = Math.max(0f, Math.min(1f, amount));
        float inverse = 1.0f - clamped;
        return Color.argb(
                Math.round(Color.alpha(from) * inverse + Color.alpha(to) * clamped),
                Math.round(Color.red(from) * inverse + Color.red(to) * clamped),
                Math.round(Color.green(from) * inverse + Color.green(to) * clamped),
                Math.round(Color.blue(from) * inverse + Color.blue(to) * clamped));
    }

    private static int themeColor(Context context, int attribute, int fallback) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) {
                return ContextCompat.getColor(context, value.resourceId);
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
        }
        return ContextCompat.getColor(context, fallback);
    }

    public static GradientDrawable roundedSurface(Context context, @ColorInt int color, float cornerDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(cornerDp * context.getResources().getDisplayMetrics().density);
        return drawable;
    }

    public static StateListDrawable playerControlSurface(Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(
                new int[]{android.R.attr.state_pressed},
                ovalSurface(pressedSurface(context)));
        states.addState(
                new int[]{android.R.attr.state_focused},
                ovalSurface(focusSurface(context)));
        states.addState(
                new int[]{},
                ovalSurface(surfaceVariant(context)));
        return states;
    }

    public static GradientDrawable playerControlSurface(Context context, boolean focused) {
        return ovalSurface(focused ? focusSurface(context) : surfaceVariant(context));
    }

    @ColorInt
    public static int focusSurface(Context context) {
        return themeColor(context, R.attr.materialFocusSurface, R.color.relay_primary_container);
    }

    @ColorInt
    public static int pressedSurface(Context context) {
        return themeColor(context, R.attr.materialPressedSurface, R.color.relay_primary);
    }

    private static GradientDrawable ovalSurface(@ColorInt int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    public static GradientDrawable outlinedSurface(
            Context context, @ColorInt int color, float cornerDp,
            @ColorInt int outlineColor, float outlineDp) {
        GradientDrawable drawable = roundedSurface(context, color, cornerDp);
        int outlinePx = Math.max(1, Math.round(
                outlineDp * context.getResources().getDisplayMetrics().density));
        drawable.setStroke(outlinePx, outlineColor);
        return drawable;
    }
}
