package com.minimal.todo;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/**
 * 给桌面小组件的列表提供数据（三种尺寸共用）。
 *
 * 分组：有日期的排前面，没有日期的单独一组（两组都有时才插标题），
 * 已完成再单独一组；每条待办前面是它分类颜色的实心圆点。
 */
public class TodoWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int appWidgetId = intent == null
                ? AppWidgetManager.INVALID_APPWIDGET_ID
                : intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        return new TodoViewsFactory(getApplicationContext(), appWidgetId);
    }

    private static class TodoViewsFactory implements RemoteViewsService.RemoteViewsFactory {

        /** 小组件里每条待办最多显示几个字，超出的部分用省略号。 */
        private static final int MAX_CHARS = 6;
        /** 小组件里地点最多显示几个字，超出的部分用省略号。 */
        private static final int MAX_PLACE_CHARS = 4;

        private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;


        private final Context context;
        private final int appWidgetId;
        /** 与 {@link #items} 一一对应：待办行存待办，标题行存 null。 */
        private final List<Todo> items = new ArrayList<>();
        private final List<String> headers = new ArrayList<>();

        /** 当前小组件尺寸对应的行布局，只在数据刷新时算一次。 */
        private int rowLayout = R.layout.widget_todo_item;
        /** 文字配色（黑字/白字跟着壁纸明暗）。 */
        private WidgetPalette palette = null;

        TodoViewsFactory(Context context, int appWidgetId) {
            this.context = context;
            this.appWidgetId = appWidgetId;
        }

        @Override
        public void onCreate() {
        }

        @Override
        public void onDestroy() {
            items.clear();
            headers.clear();
        }

        @Override
        public void onDataSetChanged() {
            int bucket = WidgetStyle.bucketOf(context, appWidgetId, WidgetStyle.MEDIUM);
            rowLayout = WidgetStyle.rowLayout(bucket);
            palette = WidgetPalette.of(context);

            items.clear();
            headers.clear();

            TodoStore store = TodoStore.get(context);
            // 有时间的待办只在「它所在的那一天」出现，其它日期不显示
            long todayStart = TodoTime.startOfDay(System.currentTimeMillis());
            List<Todo> dated = new ArrayList<>();
            for (Todo todo : store.listDatedBetween(todayStart, todayStart + DAY_MILLIS)) {
                if (!todo.done) {
                    dated.add(todo);
                }
            }
            List<Todo> undated = new ArrayList<>(store.listUndated(TodoStore.FILTER_ACTIVE));
            for (Todo todo : dated) {
                addRow(todo);
            }
            if (!undated.isEmpty()) {
                // 两组都有的时候才需要标题，只有一组时不占地方
                if (!dated.isEmpty()) {
                    addHeader(context.getString(R.string.group_undated));
                }
                for (Todo todo : undated) {
                    addRow(todo);
                }
            }
            if (WidgetPrefs.showDone(context)) {
                List<Todo> done = store.list(TodoStore.FILTER_DONE);
                if (!done.isEmpty()) {
                    addHeader(context.getString(R.string.group_done));
                    for (Todo todo : done) {
                        addRow(todo);
                    }
                }
            }
        }

        private void addRow(Todo todo) {
            items.add(todo);
            headers.add(null);
        }

        private void addHeader(String text) {
            items.add(null);
            headers.add(text);
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public int getViewTypeCount() {
            // 只能有一种布局：RemoteViewsFactory 没有 getItemViewType，
            // 分组标题是内嵌在行布局里、用可见性切换出来的
            return 1;
        }

        @Override
        public long getItemId(int position) {
            Todo todo = items.get(position);
            return todo == null ? -(position + 1L) : todo.id;
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= items.size()) {
                return null;
            }
            WidgetPalette colors = palette != null ? palette : WidgetPalette.of(context);

            RemoteViews views = new RemoteViews(context.getPackageName(), rowLayout);

            // 分组标题行：只显示那行小标题，下面的待办内容整体隐藏
            if (items.get(position) == null) {
                views.setViewVisibility(R.id.item_header_text, View.VISIBLE);
                views.setViewVisibility(R.id.item_row_body, View.GONE);
                views.setTextViewText(R.id.item_header_text, headers.get(position));
                views.setTextColor(R.id.item_header_text, colors.secondary);
                // 空意图：点了也不会误触到上一条待办（列表条目会被复用）
                views.setOnClickFillInIntent(R.id.item_row, new Intent());
                return views;
            }

            Todo item = items.get(position);
            views.setViewVisibility(R.id.item_header_text, View.GONE);
            views.setViewVisibility(R.id.item_row_body, View.VISIBLE);
            views.setTextViewText(R.id.item_title, shorten(item.title, MAX_CHARS));
            views.setTextColor(R.id.item_title, item.done ? colors.done : colors.primary);

            // 前面的圆点：未完成是分类颜色的实心圆，已完成是打勾
            if (item.done) {
                views.setImageViewBitmap(R.id.item_check, TodoColors.checkBitmap(Accent.get(context)));
            } else {
                views.setImageViewBitmap(R.id.item_check,
                        TodoColors.dotBitmap(item.color));
            }
            views.setContentDescription(R.id.item_row, context.getString(
                    item.done ? R.string.widget_item_done : R.string.widget_item_undone, item.title));

            // 右边的小字明细：上一行时间（今天 18:00 / 明18:00 / 9/25，过期标红），
            // 下一行地点（最多显示 4 个字）
            String place = item.place == null ? "" : item.place.trim();
            boolean hasPlace = !place.isEmpty();
            views.setViewVisibility(R.id.item_meta,
                    item.hasDue() || hasPlace ? View.VISIBLE : View.GONE);
            if (item.hasDue()) {
                views.setViewVisibility(R.id.item_time, View.VISIBLE);
                views.setTextViewText(R.id.item_time, TodoTime.formatCompact(context, item.due));
                int color;
                if (item.done) {
                    color = colors.done;
                } else if (TodoTime.isOverdue(item.due, false)) {
                    color = colors.overdue;
                } else {
                    color = colors.secondary;
                }
                views.setTextColor(R.id.item_time, color);
            } else {
                views.setViewVisibility(R.id.item_time, View.GONE);
            }
            if (hasPlace) {
                views.setViewVisibility(R.id.item_place, View.VISIBLE);
                views.setTextViewText(R.id.item_place, shorten(place, MAX_PLACE_CHARS));
                views.setTextColor(R.id.item_place, item.done ? colors.done : colors.secondary);
            } else {
                views.setViewVisibility(R.id.item_place, View.GONE);
            }

            // 整行都是“打勾/取消打勾”，不带删除动作
            Intent toggle = new Intent();
            toggle.putExtra(BaseTodoWidgetProvider.EXTRA_TODO_ID, item.id);
            toggle.putExtra(BaseTodoWidgetProvider.EXTRA_CMD, BaseTodoWidgetProvider.CMD_TOGGLE);
            views.setOnClickFillInIntent(R.id.item_row, toggle);

            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        /** 截断到 MAX_CHARS 个字，超出加省略号；按字符（代码点）计，中文和 emoji 都算一个字。 */
        private static String shorten(String text, int max) {
            if (text == null) {
                return "";
            }
            String value = text.trim().replace('\n', ' ').replace('\r', ' ');
            if (value.codePointCount(0, value.length()) <= max) {
                return value;
            }
            return value.substring(0, value.offsetByCodePoints(0, max)) + "…";
        }
    }
}
