package com.minimal.todo;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 小组件的文字配色：跟着桌面壁纸的明暗自动在「白字 / 黑字」之间切。
 *
 * 取色用系统的 {@link WallpaperManager#getWallpaperColors(int)}（不需要任何权限）：
 * · Android 12+ 优先用它给的 colorHints（HINT_SUPPORTS_DARK_TEXT 就是“这里适合深色文字”）；
 * · 更低版本或系统没给 hints 时，退化成算壁纸主色的亮度；
 * · 再把小组件玻璃底那层浅色叠进去一起算（玻璃会把背景提亮，所以深色文字的适用范围更大）；
 * · 全都拿不到（动态壁纸不给颜色等）时，回退成跟随系统深色模式。
 */
final class WidgetPalette {

    private static final String TAG = "SimpleTodo";

    /** 叠了玻璃之后，背景亮度超过这个值就用深色文字。 */
    private static final float DARK_TEXT_THRESHOLD = 0.55f;

    /** 液态玻璃 / 透明毛玻璃那层底的透明度，跟 widget_glass_fill / widget_frost_fill 的 alpha 对应。 */
    private static final float LIQUID_TINT_ALPHA = 0.40f;
    private static final float FROST_TINT_ALPHA = 0.30f;

    final int primary;
    final int secondary;
    final int done;
    final int overdue;
    final int chip;
    final boolean darkText;

    private WidgetPalette(int primary, int secondary, int done, int overdue, int chip, boolean darkText) {
        this.primary = primary;
        this.secondary = secondary;
        this.done = done;
        this.overdue = overdue;
        this.chip = chip;
        this.darkText = darkText;
    }

    static WidgetPalette of(Context context) {
        Boolean darkText = prefersDarkText(context);
        if (darkText == null) {
            // 拿不到壁纸信息：跟随系统深色模式（深色模式下用白字）
            boolean night = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            darkText = !night;
        }
        return darkText ? darkTextPalette() : lightTextPalette();
    }

    /** 空心的方框在浅色壁纸下要用深色描边，否则看不见。 */
    int uncheckedIcon() {
        return darkText ? R.drawable.ic_check_off_dark : R.drawable.ic_check_off;
    }

    private static WidgetPalette darkTextPalette() {
        return new WidgetPalette(
                0xFF1F1F24,   // 主文字：近黑
                0xFF55555F,   // 次要文字/时间
                0xFF9A9AA6,   // 已完成
                0xFFD03A3F,   // 过期
                0xFF2F6BE0,   // 胶囊上的强调色（浅底上要更深一点）
                true);
    }

    private static WidgetPalette lightTextPalette() {
        return new WidgetPalette(
                0xFFFFFFFF,   // 主文字：白
                0xFFDCDCE4,   // 次要文字/时间
                0xFFB4B4BE,   // 已完成
                0xFFFF8A8A,   // 过期
                0xFF9CBDFF,   // 胶囊上的强调色（深底上要更亮一点）
                false);
    }

    /** true=壁纸偏亮、该用深色文字；false=该用浅色文字；null=问不出来。 */
    private static Boolean prefersDarkText(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return null;   // getWallpaperColors 是 8.1 才有的
        }
        try {
            WallpaperManager manager = WallpaperManager.getInstance(context);
            WallpaperColors colors = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (colors == null) {
                return null;   // 还没算好，或动态壁纸不提供颜色
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                int hints = colors.getColorHints();
                if ((hints & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0) {
                    return Boolean.TRUE;
                }
                if (hints != 0) {
                    return Boolean.FALSE;   // 系统明确表示这里适合浅色文字
                }
            }
            int rgb = colors.getPrimaryColor() == null ? 0 : colors.getPrimaryColor().toArgb();
            float luminance = (0.299f * Color.red(rgb) + 0.587f * Color.green(rgb)
                    + 0.114f * Color.blue(rgb)) / 255f;
            // 玻璃底会往背景叠一层浅色，等效亮度按叠加后算
            float alpha = GlassPrefs.isLiquid(context) ? LIQUID_TINT_ALPHA : FROST_TINT_ALPHA;
            float effective = luminance + (1f - luminance) * alpha;
            return effective > DARK_TEXT_THRESHOLD;
        } catch (Throwable t) {
            // 个别 ROM 会抛异常，退回默认配色就好，不能让小组件挂掉
            Log.w(TAG, "读取壁纸颜色失败，改用系统深色模式判断", t);
            return null;
        }
    }

    private static boolean sListenerRegistered;

    /** 壁纸换了就刷新一次小组件，让文字立刻跟着变。注册失败就忽略（下次刷新还会重新读）。 */
    static void watchWallpaper(Context context) {
        // addOnColorsChangedListener / getWallpaperColors 都是 8.1（API 27）才有的
        if (sListenerRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return;
        }
        try {
            final Context app = context.getApplicationContext();
            WallpaperManager.getInstance(app).addOnColorsChangedListener(
                    (colors, which) -> BaseTodoWidgetProvider.refreshAll(app),
                    new Handler(Looper.getMainLooper()));
            sListenerRegistered = true;
        } catch (Throwable t) {
            Log.w(TAG, "监听壁纸颜色变化失败，改为下次刷新时重新读取", t);
        }
    }
}
