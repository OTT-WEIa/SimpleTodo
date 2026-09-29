package com.minimal.todo;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 外观主题：透明毛玻璃 / 液态玻璃。应用界面和桌面小组件都跟着这个设置走。
 */
final class GlassPrefs {

    /** 透明毛玻璃：几乎全透，主要靠系统模糊保证可读性。 */
    static final int STYLE_FROSTED = 0;
    /** 液态玻璃：带渐变高光和描边的玻璃质感。 */
    static final int STYLE_LIQUID = 1;

    private static final String FILE = "glass_prefs";
    private static final String KEY_STYLE = "style";

    private GlassPrefs() {
    }

    static int get(Context context) {
        return prefs(context).getInt(KEY_STYLE, STYLE_LIQUID);
    }

    static void set(Context context, int style) {
        prefs(context).edit().putInt(KEY_STYLE, style).apply();
    }

    static boolean isLiquid(Context context) {
        return get(context) == STYLE_LIQUID;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
