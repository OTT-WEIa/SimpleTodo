package com.minimal.todo;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;

/**
 * 小组件按尺寸分三档，2×2 / 3×2 / 4×2 各用一套布局和行高。
 * 用户拉伸小组件后也会按新的宽度切换，所以不止“三种固定尺寸”，中间尺寸也能自适应。
 */
final class WidgetStyle {

    static final int SMALL = 0;
    static final int MEDIUM = 1;
    static final int LARGE = 2;

    private WidgetStyle() {
    }

    /** 三个 provider 各自对应的默认档位（拿不到实际宽度时用）。 */
    static int defaultBucket(Class<?> providerClass) {
        if (providerClass == TodoWidget2x2.class) {
            return SMALL;
        }
        if (providerClass == TodoWidget4x2.class) {
            return LARGE;
        }
        return MEDIUM;
    }

    /**
     * 按小组件当前宽度（dp）决定用哪一档。
     * 标准换算下：2 格 ≈ 130dp，3 格 ≈ 203dp，4 格 ≈ 276dp；
     * 各家桌面的单元格宽度不一样，所以分界值留了余量。
     */
    static int bucketOf(Context context, int appWidgetId, int fallback) {
        if (appWidgetId <= 0) {
            return fallback;
        }
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId);
        if (options == null) {
            return fallback;
        }
        int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        if (width <= 0) {
            return fallback;
        }
        if (width < 180) {
            return SMALL;
        }
        if (width < 255) {
            return MEDIUM;
        }
        return LARGE;
    }

    static int rootLayout(int bucket) {
        switch (bucket) {
            case SMALL:
                return R.layout.widget_todo_2x2;
            case LARGE:
                return R.layout.widget_todo_4x2;
            default:
                return R.layout.widget_todo_3x2;
        }
    }

    static int rowLayout(int bucket) {
        switch (bucket) {
            case SMALL:
                return R.layout.widget_todo_item_small;
            case LARGE:
                return R.layout.widget_todo_item_large;
            default:
                return R.layout.widget_todo_item;
        }
    }
}
