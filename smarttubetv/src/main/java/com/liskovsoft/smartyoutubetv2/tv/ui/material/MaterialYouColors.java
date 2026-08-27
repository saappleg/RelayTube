package com.liskovsoft.smartyoutubetv2.tv.ui.material;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Material You tokens for the transitional Leanback surface.
 *
 * Android 12+ supplies Monet resources at runtime. Older Android TV releases retain the active
 * app palette, so this utility is useful independently of any Relay integration.
 */
public final class MaterialYouColors {
    private MaterialYouColors() {}

    public static int surface(Context context) {
        if (usesAutomaticScheme(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getColor(android.R.color.system_neutral1_900);
        }
        return themeColor(context, R.attr.shelfBackground, R.color.shelf_background_dark);
    }

    public static int surfaceVariant(Context context) {
        if (usesAutomaticScheme(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getColor(android.R.color.system_neutral2_800);
        }
        return themeColor(context, R.attr.cardDefaultBackground, R.color.card_default_background_dark);
    }

    public static int accent(Context context) {
        if (usesAutomaticScheme(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getColor(android.R.color.system_accent1_500);
        }
        return themeColor(context, R.attr.brandAccentColor, R.color.fastlane_background);
    }

    private static boolean usesAutomaticScheme(Context context) {
        // The first built-in scheme has no explicit theme resources. RelayTube exposes that
        // existing slot as Automatic, avoiding a preference-index migration for current users.
        return com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.instance(context)
                .getColorScheme().browseThemeResId == 0;
    }

    private static int themeColor(Context context, int attribute, int fallback) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) {
                return ContextCompat.getColor(context, value.resourceId);
            }
            return value.data;
        }
        return ContextCompat.getColor(context, fallback);
    }

    public static GradientDrawable roundedSurface(Context context, int color, float cornerDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(cornerDp * context.getResources().getDisplayMetrics().density);
        return drawable;
    }
}
