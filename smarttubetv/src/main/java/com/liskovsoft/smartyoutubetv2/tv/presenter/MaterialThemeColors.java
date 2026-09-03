package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

/** Shared theme-token access for presenters that sit outside the Compose tree. */
final class MaterialThemeColors {
    private MaterialThemeColors() {
    }

    @ColorInt
    static int color(Context context, int attr, @ColorInt int fallback) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                return ContextCompat.getColor(context, value.resourceId);
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
        }
        return fallback;
    }

    @ColorInt
    static int withAlpha(@ColorInt int color, int alpha) {
        return (color & 0x00ffffff) | ((alpha & 0xff) << 24);
    }

    @ColorInt
    static int blend(@ColorInt int start, @ColorInt int end, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int a = Math.round(((start >>> 24) & 0xff) + (((end >>> 24) & 0xff) - ((start >>> 24) & 0xff)) * t);
        int r = Math.round(((start >> 16) & 0xff) + (((end >> 16) & 0xff) - ((start >> 16) & 0xff)) * t);
        int g = Math.round(((start >> 8) & 0xff) + (((end >> 8) & 0xff) - ((start >> 8) & 0xff)) * t);
        int b = Math.round((start & 0xff) + ((end & 0xff) - (start & 0xff)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
