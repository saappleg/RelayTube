package com.liskovsoft.smartyoutubetv2.tv.ui.compose

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.liskovsoft.smartyoutubetv2.tv.R

/**
 * Compose-in-View entry points for screens that are still hosted by Leanback.
 *
 * These views deliberately contain no focusable or clickable Compose nodes. The surrounding
 * Leanback presenter remains responsible for D-pad focus, click dispatch, and row transitions.
 */
object RelayComposeViews {
    @JvmStatic
    fun renderVideoDetails(
        view: ComposeView,
        title: CharSequence?,
        subtitle: CharSequence?,
        body: CharSequence?
    ) {
        prepare(view)
        view.setContent {
            RelayTheme(view.context) {
                VideoDetails(
                    title = title.toStringOrEmpty(),
                    subtitle = subtitle.toStringOrEmpty(),
                    body = body.toStringOrEmpty()
                )
            }
        }
    }

    @JvmStatic
    fun renderSettingsCard(
        view: ComposeView,
        title: CharSequence?,
        imageResId: Int,
        focused: Boolean
    ) {
        prepare(view)
        view.setContent {
            RelayTheme(view.context) {
                SettingsCard(
                    title = title.toStringOrEmpty(),
                    imageResId = imageResId,
                    focused = focused,
                    cardWidth = dimension(view.context, "settings_card_width", 128f),
                    cardHeight = dimension(view.context, "settings_card_height", 100f),
                    cornerRadius = dimension(view.context, "lb_rounded_rect_corner_radius", 12f)
                )
            }
        }
    }

    @JvmStatic
    fun renderSettingsTitle(view: ComposeView, title: CharSequence?) {
        prepare(view)
        view.setContent {
            RelayTheme(view.context) {
                SettingsTitle(title = title.toStringOrEmpty())
            }
        }
    }

    /**
     * Replaces the stock preference title text with a non-focusable Compose title, retaining the
     * preference fragment and its native D-pad behavior below it.
     */
    @JvmStatic
    fun installSettingsTitle(root: View, title: CharSequence?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }

        val context = root.context
        val containerId = context.resources.getIdentifier(
            "decor_title_container", "id", context.packageName
        )
        val titleId = context.resources.getIdentifier("decor_title", "id", context.packageName)
        val container = root.findViewById<ViewGroup>(containerId) ?: return
        val nativeTitle = root.findViewById<View>(titleId)
        nativeTitle?.visibility = View.GONE

        val composeTitle = ComposeView(context)
        composeTitle.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val titleHeight = nativeTitle?.layoutParams?.height?.takeIf { it > 0 }
            ?: (64f * context.resources.displayMetrics.density).toInt()
        container.addView(
            composeTitle,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, titleHeight)
        )
        renderSettingsTitle(composeTitle, title)
    }

    private fun prepare(view: ComposeView) {
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.isClickable = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private fun CharSequence?.toStringOrEmpty(): String = this?.toString().orEmpty()

    @Composable
    private fun RelayTheme(context: Context, content: @Composable () -> Unit) {
        val scheme = darkColorScheme(
            primary = themeColor(context, R.attr.materialPrimary, "relay_primary", "relay_dynamic_accent", "material_dynamic_accent", "fastlane_background"),
            onPrimary = themeColor(context, R.attr.materialOnPrimary, "relay_on_primary", "relay_on_background", "card_selected_text_black", "black"),
            primaryContainer = themeColor(context, R.attr.materialPrimaryContainer, "relay_primary_container", "relay_dynamic_accent_soft", "material_dynamic_accent_soft", "fastlane_background_dark"),
            // The title surface is primaryContainer; use its light on-color explicitly so
            // palettes with light primary accents never inherit a dark onPrimary value here.
            onPrimaryContainer = themeColor(context, R.attr.materialOnSurface, "relay_on_surface", "card_default_text", "white"),
            surface = themeColor(context, R.attr.materialSurface, "relay_surface", "relay_dynamic_surface", "material_dynamic_surface", "shelf_background_dark"),
            onSurface = themeColor(context, R.attr.materialOnSurface, "relay_on_surface", "card_default_text", "white"),
            surfaceVariant = themeColor(context, R.attr.materialSurfaceVariant, "relay_surface_variant", "relay_dynamic_surface_variant", "material_dynamic_surface_variant", "card_default_background_dark"),
            onSurfaceVariant = themeColor(context, R.attr.materialOnSurfaceVariant, "relay_on_surface_variant", "card_default_text", "white"),
            outline = themeColor(context, R.attr.materialOutline, "relay_outline", "default_light_grey", "card_default_background_dark")
        )

        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(
                titleLarge = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                ),
                titleMedium = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
            ),
            content = content
        )
    }

    @Composable
    private fun VideoDetails(title: String, subtitle: String, body: String) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (title.isNotEmpty()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (body.isNotEmpty()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    private fun SettingsCard(
        title: String,
        imageResId: Int,
        focused: Boolean,
        cardWidth: Dp,
        cardHeight: Dp,
        cornerRadius: Dp
    ) {
        val containerColor = if (focused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
        val contentColor = if (focused) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        Surface(
            modifier = Modifier
                .width(cardWidth)
                .heightIn(min = cardHeight),
            color = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(cornerRadius),
            tonalElevation = if (focused) 4.dp else 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (imageResId > 0) {
                    Icon(
                        painter = painterResource(imageResId),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    private fun SettingsTitle(title: String) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                // The settings sheet is rounded only at its leading edge. Clip the Compose
                // title before painting its primary-container color so it cannot square off
                // the sheet's top-left corner; the trailing edge remains flush to the screen.
                .clip(RoundedCornerShape(topStart = 28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    private fun dimension(context: Context, name: String, fallbackDp: Float): Dp {
        val id = context.resources.getIdentifier(name, "dimen", context.packageName)
        if (id == 0) {
            return fallbackDp.dp
        }
        return (context.resources.getDimension(id) / context.resources.displayMetrics.density).dp
    }

    private fun color(context: Context, vararg names: String): Color {
        for (name in names) {
            val id = context.resources.getIdentifier(name, "color", context.packageName)
            if (id != 0) {
                return Color(ContextCompat.getColor(context, id))
            }
        }
        return Color.White
    }

    private fun themeColor(context: Context, attr: Int, vararg fallbackNames: String): Color {
        val value = TypedValue()
        if (context.theme.resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                return Color(ContextCompat.getColor(context, value.resourceId))
            }
            if (value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
                return Color(value.data)
            }
        }
        return color(context, *fallbackNames)
    }
}
