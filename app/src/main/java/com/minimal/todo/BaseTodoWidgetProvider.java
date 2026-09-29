package com.minimal.todo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Arrays;
import java.util.List;

/**
 * 桌面小组件的公共实现，三个尺寸的子类（{@link TodoWidget2x2}、{@link TodoWidget3x2}、
 * {@link TodoWidget4x2}）共用这里的逻辑，只在桌面「小组件」列表里各占一个条目。
 *
 * 交互：
 * · 点某一行（或前面的方框）＝打勾完成 / 取消完成；
 * · 点右上角圆圈加号 ＝ 弹出快速添加（不设时间也能直接存）；
 * · 点标题 ＝ 打开应用。
 * 小组件里不提供删除，删除在应用里做（有二次确认），避免在桌面上误删。
 */
public class BaseTodoWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "SimpleTodo";

    /** 列表项的点击模板 action，具体点的是哪条由每行的填充 Intent 带进来。 */
    public static final String ACTION_ITEM = "com.minimal.todo.action.ITEM";
    public static final String ACTION_SHOW_DONE = "com.minimal.todo.action.SHOW_DONE";

    public static final String EXTRA_TODO_ID = "todo_id";
    public static final String EXTRA_CMD = "cmd";
    /** 目前只有“打勾/取消打勾”一种动作。 */
    public static final String CMD_TOGGLE = "toggle";

    private static final int REQ_ITEM_TEMPLATE = 100;
    private static final int REQ_OPEN_APP = 101;
    private static final int REQ_OPEN_APP_EMPTY = 102;
    private static final int REQ_SHOW_DONE = 103;
    private static final int REQ_QUICK_ADD = 104;

    private static final List<Class<? extends BaseTodoWidgetProvider>> ALL_PROVIDERS = Arrays.asList(
            TodoWidget2x2.class, TodoWidget3x2.class, TodoWidget4x2.class);

    /**
     * 2×2 的玻璃底底部留白。
     *
     * 背景：桌面给「2 格高」的小组件留的行高，比它给 2 行图标（文件夹那种）的行高更高，
     * 于是 2×2 小组件的盒子天生就比 2×2 文件夹高一截（实测约 1/6；如果是整整多了一行
     * 会高约 1/2，所以格子数本身没问题，差的是行高）。盒子多大由桌面决定、我们改不了，
     * 但玻璃底画多大是我们说了算：按桌面给的实际高度内缩 1/5.5，下边缘就和文件夹对齐了。
     * （1/7 时还差一点点、1/5 会缩过头，所以取中间值。）
     */
    private static final float BOTTOM_INSET_DIVISOR = 5.5f;
    private static final int BOTTOM_INSET_MIN_DP = 8;
    private static final int BOTTOM_INSET_MAX_DP = 40;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId, getClass()));
        }
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle newOptions) {
        // 用户拖动边缘改了大小，按新尺寸换布局
        manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId, getClass()));
        manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_ITEM.equals(action)) {
            long todoId = intent.getLongExtra(EXTRA_TODO_ID, -1L);
            if (todoId > 0) {
                TodoStore.get(context).toggle(todoId);
                refreshAll(context);
            }
            return;
        }

        if (ACTION_SHOW_DONE.equals(action)) {
            WidgetPrefs.setShowDone(context, !WidgetPrefs.showDone(context));
            refreshAll(context);
            return;
        }

        // APPWIDGET_UPDATE / APPWIDGET_OPTIONS_CHANGED 等交给父类分发
        super.onReceive(context, intent);
    }

    /** 待办数据变化后刷新桌面上所有尺寸的小组件；主界面、快速添加、小组件自身都会调用。 */
    public static void refreshAll(Context context) {        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (Class<? extends BaseTodoWidgetProvider> providerClass : ALL_PROVIDERS) {
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, providerClass));
            if (ids == null || ids.length == 0) {
                continue;
            }
            for (int appWidgetId : ids) {
                manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId, providerClass));
            }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
        }
    }

    private static RemoteViews buildViews(Context context, int appWidgetId,
                                         Class<? extends BaseTodoWidgetProvider> providerClass) {
        int bucket = WidgetStyle.bucketOf(context, appWidgetId, WidgetStyle.defaultBucket(providerClass));
        RemoteViews views = new RemoteViews(context.getPackageName(), WidgetStyle.rootLayout(bucket));

        // 玻璃背景：两层背景叠着放，用可见性切换「液态玻璃 / 透明毛玻璃」
        boolean liquid = GlassPrefs.isLiquid(context);
        views.setViewVisibility(R.id.widget_glass_liquid, liquid ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_glass_frost, liquid ? View.GONE : View.VISIBLE);

        // 2×2 按桌面给的行高把玻璃底底部内缩，让下边缘和系统文件夹小组件对齐
        int[] box = widgetBox(context, appWidgetId);
        if (bucket == WidgetStyle.SMALL && box[1] > 0) {
            int insetDp = Math.max(BOTTOM_INSET_MIN_DP,
                    Math.min(BOTTOM_INSET_MAX_DP, Math.round(box[1] / (float) BOTTOM_INSET_DIVISOR)));
            views.setViewPadding(R.id.widget_root, 0, 0, 0, toPixels(context, insetDp));
        }

        // 文字配色跟着桌面壁纸明暗走（浅壁纸用黑字、深壁纸用白字），并监听壁纸变化
        WidgetPalette palette = WidgetPalette.of(context);
        WidgetPalette.watchWallpaper(context);

        int[] stats = TodoStore.get(context).stats();
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title, stats[1], stats[0]));
        views.setTextColor(R.id.widget_title, palette.primary);
        views.setTextColor(R.id.widget_empty, palette.secondary);
        views.setOnClickPendingIntent(R.id.widget_title, openApp(context, REQ_OPEN_APP));
        views.setOnClickPendingIntent(R.id.widget_empty, openApp(context, REQ_OPEN_APP_EMPTY));

        // 圆圈加号：快速添加一条待办，不用进应用
        Intent quickAdd = new Intent(context, QuickAddActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        views.setImageViewBitmap(R.id.widget_add, TodoColors.plusBitmap(Accent.get(context)));
        views.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(
                context, REQ_QUICK_ADD, quickAdd,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // 2×2 太窄，放不下「显示已办」，只在标题里显示进度
        if (bucket != WidgetStyle.SMALL) {
            views.setTextViewText(R.id.widget_toggle_done, context.getString(
                    WidgetPrefs.showDone(context) ? R.string.hide_done : R.string.show_done));
            views.setTextColor(R.id.widget_toggle_done, Accent.get(context));
            views.setOnClickPendingIntent(R.id.widget_toggle_done,
                    broadcast(context, providerClass, ACTION_SHOW_DONE, REQ_SHOW_DONE));
        }

        // 列表内容由 RemoteViewsService 提供
        views.setRemoteAdapter(R.id.widget_list, new Intent(context, TodoWidgetService.class));
        views.setEmptyView(R.id.widget_list, R.id.widget_empty);

        // 列表项点击模板：每行用 setOnClickFillInIntent 带上自己的待办 id。
        // 必须是 FLAG_MUTABLE —— 填充 Intent 机制要靠系统往模板里补 extras，
        // 用 FLAG_IMMUTABLE 的话桌面点方框会没反应（Android 12 起才有这个限制）。
        Intent template = new Intent(context, providerClass).setAction(ACTION_ITEM);
        views.setPendingIntentTemplate(R.id.widget_list, PendingIntent.getBroadcast(
                context, REQ_ITEM_TEMPLATE, template,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));

        return views;
    }

    /**
     * 桌面此刻给这个小组件的尺寸（dp）：{最小宽度, 最小高度}，也就是竖屏下的那个盒子。
     * 顺手记进 logcat（`adb logcat -s SimpleTodo` 可以看），方便核对格子对不对。
     */
    private static int[] widgetBox(Context context, int appWidgetId) {
        int width = 0;
        int height = 0;
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId);
        if (options != null) {
            width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            Log.i(TAG, "小组件 " + appWidgetId + " 竖屏 " + width + "×" + height + "dp，横屏 "
                    + options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) + "×"
                    + options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) + "dp");
        }
        return new int[]{width, height};
    }

    private static int toPixels(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    private static PendingIntent openApp(Context context, int requestCode) {        Intent intent = new Intent(context, MainActivity.class);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent broadcast(Context context, Class<?> providerClass,
                                           String action, int requestCode) {
        Intent intent = new Intent(context, providerClass).setAction(action);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
