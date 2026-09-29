package com.minimal.todo;

import android.content.Context;
import android.content.SharedPreferences;

/** 小组件自身的显示偏好（是否显示已完成）。 */
public final class WidgetPrefs {

    private static final String FILE = "widget_prefs";
    private static final String KEY_SHOW_DONE = "show_done";

    private WidgetPrefs() {
    }

    public static boolean showDone(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_DONE, false);
    }

    public static void setShowDone(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_SHOW_DONE, show).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
