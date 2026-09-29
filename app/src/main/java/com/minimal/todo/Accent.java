package com.minimal.todo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;

/**
 * 主题色：只给常用的几种。做法是给 Activity 的主题叠一层只改 colorAccent 的 style，
 * 界面里的强调色都引用 ?android:attr/colorAccent（XML 里）或解析主题属性（代码里），
 * 所以换色后 recreate() 一下，整个界面和弹窗都会跟着变。
 */
final class Accent {

    /** 蓝、绿、紫、橙、红、青。 */
    static final int[] PRESETS = {
            0xFF3D7EFF, 0xFF22A06B, 0xFF8B5CF6, 0xFFF0A830, 0xFFE5484D, 0xFF2AA8A8,
    };

    private static final int[] STYLES = {
            R.style.AccentBlue, R.style.AccentGreen, R.style.AccentPurple,
            R.style.AccentOrange, R.style.AccentRed, R.style.AccentTeal,
    };

    private static final String FILE = "accent_prefs";
    private static final String KEY_COLOR = "accent";

    private Accent() {
    }

    static int get(Context context) {
        return prefs(context).getInt(KEY_COLOR, PRESETS[0]);
    }

    static void set(Context context, int color) {
        prefs(context).edit().putInt(KEY_COLOR, color).apply();
    }

    /** 当前主题色对应的那层 style（在 setContentView 之前叠加到主题上）。 */
    static int styleRes(Context context) {
        int color = get(context);
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i] == color) {
                return STYLES[i];
            }
        }
        return STYLES[0];
    }

    /**
     * 代码里要用的主题色：优先从主题属性里解析（这样和 XML 里引用的是同一个颜色），
     * 解析不到（比如用 Application context）就退回存的那个值。
     */
    static int color(Context context) {
        if (context != null && context.getTheme() != null) {
            TypedValue value = new TypedValue();
            if (context.getTheme().resolveAttribute(android.R.attr.colorAccent, value, true)
                    && value.data != 0) {
                return value.data;
            }
        }
        return get(context);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
