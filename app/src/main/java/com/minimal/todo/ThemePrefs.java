package com.minimal.todo;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** 深浅模式：跟随系统 / 浅色 / 深色。 */
final class ThemePrefs {

    static final int MODE_SYSTEM = 0;
    static final int MODE_LIGHT = 1;
    static final int MODE_DARK = 2;

    private static final String FILE = "theme_prefs";
    private static final String KEY_MODE = "night_mode";

    private ThemePrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int get(Context context) {
        return prefs(context).getInt(KEY_MODE, MODE_SYSTEM);
    }

    static void set(Context context, int mode) {
        prefs(context).edit().putInt(KEY_MODE, mode).apply();
    }

    /**
     * 按设置改一下上下文的 configuration（“跟随系统”就原样返回）。
     * Activity 在 attachBaseContext 里调用，这样整个界面（含 values-night 资源）都跟着变。
     */
    static Context wrap(Context base) {
        int mode = get(base);
        if (mode == MODE_SYSTEM) {
            return base;
        }
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        int night = mode == MODE_DARK
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(configuration);
    }
}
