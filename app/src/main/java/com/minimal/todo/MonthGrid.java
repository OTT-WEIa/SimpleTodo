package com.minimal.todo;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * 日历网格：7 列 × 6 行（月视图）或 1 行（周视图，课程表那样）。
 *
 * 月视图：那天有安排就把**整格填成淡色** —— 只有一条且设了颜色就填它的淡色，
 *   没设颜色 / 有多条（或多个颜色）就填淡灰；日期数字在格子里上下左右居中。
 *   用格子背景来标记，是因为背景是格子上本来就在正常渲染的东西（选中高亮就是它），最稳。
 * 周视图：日期数字贴顶 + 若干「浅色实心矩形 + 黑字」的待办块，
 *   每块的上下位置按 {@link Item#position}（它在一天里的时间位置，0~1）摆放，
 *   时间靠前的在上面，并且会避免互相压住。
 * 点某格回调那一天的 0 点时间戳。
 */
final class MonthGrid {

    interface Listener {
        void onDayClick(long dayStart);
    }

    /** 格子里要显示的一条待办：截断好的文字、标记颜色（0 = 没设）、以及它在一天里的时间位置。 */
    static final class Item {

        final String text;
        final int color;
        /** 0 = 当天最早，1 = 当天最晚。 */
        final float position;

        Item(String text, int color, float position) {
            this.text = text;
            this.color = color;
            this.position = position;
        }
    }

    private static final int COLUMNS = 7;
    private static final int MAX_ROWS = 6;
    private static final int MAX_ITEMS = 5;

    private static final int COMPACT_CELL_HEIGHT_DP = 42;
    private static final int ZOOMED_CELL_HEIGHT_DP = 152;
    /** 放大模式下待办块的可用高度（格子高 − 内边距 − 日期数字那一行）。 */
    private static final int ZOOMED_ITEMS_HEIGHT_DP = 118;
    private static final int ZOOMED_HEADER_DP = 18;
    /** 放大模式下每个待办块的高度。 */
    private static final int ZOOMED_ITEM_HEIGHT_DP = 19;
    /** 放大模式：块与块之间至少留的空隙。 */
    private static final int ZOOMED_ITEM_GAP_DP = 1;
    /** 放大模式：待办块的颜色浓度（浅色实心矩形）。 */
    private static final float ZOOMED_ITEM_ALPHA = 0.42f;

    /** 月视图：整格填充的浓度。 */
    private static final float DAY_FILL_ALPHA = 0.24f;

    private final Context context;
    private final Listener listener;
    private final LinearLayout[] rowViews = new LinearLayout[MAX_ROWS];
    private final View[] cells = new View[MAX_ROWS * COLUMNS];
    private final TextView[] numbers = new TextView[MAX_ROWS * COLUMNS];
    private final View[] itemContainers = new View[MAX_ROWS * COLUMNS];
    private final TextView[] itemSlots = new TextView[MAX_ROWS * COLUMNS * MAX_ITEMS];
    private final Drawable[] plainBackground = new Drawable[MAX_ROWS * COLUMNS];

    MonthGrid(Context context, LinearLayout rowsContainer, Listener listener) {
        this.context = context;
        this.listener = listener;
        LayoutInflater inflater = LayoutInflater.from(context);

        for (int row = 0; row < MAX_ROWS; row++) {
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(COMPACT_CELL_HEIGHT_DP)));
            for (int column = 0; column < COLUMNS; column++) {
                int index = row * COLUMNS + column;
                View cell = inflater.inflate(R.layout.item_calendar_day, rowLayout, false);
                cell.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                rowLayout.addView(cell);

                cells[index] = cell;
                plainBackground[index] = cell.getBackground();
                numbers[index] = cell.findViewById(R.id.day_number);
                itemContainers[index] = cell.findViewById(R.id.day_items);
                itemSlots[index * MAX_ITEMS] = cell.findViewById(R.id.day_item_0);
                itemSlots[index * MAX_ITEMS + 1] = cell.findViewById(R.id.day_item_1);
                itemSlots[index * MAX_ITEMS + 2] = cell.findViewById(R.id.day_item_2);
                itemSlots[index * MAX_ITEMS + 3] = cell.findViewById(R.id.day_item_3);
                itemSlots[index * MAX_ITEMS + 4] = cell.findViewById(R.id.day_item_4);
            }
            rowViews[row] = rowLayout;
            rowsContainer.addView(rowLayout);
        }
    }

    /** 月：从包含 1 号的那一周的周一开始，铺满 6 行。 */
    void bindMonth(long selectedDayStart, Calendar month, Map<Long, List<Item>> items) {
        Calendar first = (Calendar) month.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        first.add(Calendar.DAY_OF_MONTH, -mondayOffset(first));
        first.setTimeInMillis(TodoTime.startOfDay(first.getTimeInMillis()));
        bind(first.getTimeInMillis(), MAX_ROWS * COLUMNS, month.get(Calendar.MONTH),
                selectedDayStart, items, false);
    }

    /** 周：只铺一行（课程表样式），格子高、按时间摆放待办块。 */
    void bindWeek(long weekStartMillis, long selectedDayStart, Map<Long, List<Item>> items) {
        bind(weekStartMillis, COLUMNS, -1, selectedDayStart, items, true);
    }

    /** 某天对应的周一 0 点。 */
    static long mondayOf(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(TodoTime.startOfDay(timeMillis));
        calendar.add(Calendar.DAY_OF_MONTH, -mondayOffset(calendar));
        return calendar.getTimeInMillis();
    }

    /** 距离本周周一的天数（周一=0 … 周日=6）。 */
    private static int mondayOffset(Calendar calendar) {
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);   // 周日=1, 周一=2 …
        return (dayOfWeek + 5) % 7;
    }

    private void bind(long firstDayStart, int days, int dimMonth, long selectedDayStart,
                      Map<Long, List<Item>> itemsByDay, boolean zoomed) {
        int cellHeightDp = zoomed ? ZOOMED_CELL_HEIGHT_DP : COMPACT_CELL_HEIGHT_DP;
        int visibleRows = zoomed ? 1 : MAX_ROWS;
        for (int row = 0; row < MAX_ROWS; row++) {
            LinearLayout rowLayout = rowViews[row];
            rowLayout.setVisibility(row < visibleRows ? View.VISIBLE : View.GONE);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rowLayout.getLayoutParams();
            if (params.height != dp(cellHeightDp)) {
                params.height = dp(cellHeightDp);
                rowLayout.setLayoutParams(params);
            }
        }

        long todayStart = TodoTime.startOfDay(System.currentTimeMillis());
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(firstDayStart);

        for (int index = 0; index < cells.length; index++) {
            View cell = cells[index];
            if (index >= days) {
                cell.setVisibility(View.INVISIBLE);
                continue;
            }
            cell.setVisibility(View.VISIBLE);

            // 归一化到当天 0 点：数据表里的键是 startOfDay 算出来的
            final long dayStart = TodoTime.startOfDay(day.getTimeInMillis());
            cell.setOnClickListener(v -> listener.onDayClick(dayStart));

            boolean selected = dayStart == selectedDayStart;
            boolean isToday = dayStart == todayStart;
            boolean dim = dimMonth >= 0 && day.get(Calendar.MONTH) != dimMonth;

            List<Item> items = itemsByDay.get(dayStart);
            TextView number = numbers[index];
            number.setText(String.valueOf(day.get(Calendar.DAY_OF_MONTH)));
            number.setTextColor(context.getColor(
                    isToday ? R.color.accent : (dim ? R.color.text_hint : R.color.text_primary)));
            number.setTypeface(null, isToday || selected ? Typeface.BOLD : Typeface.NORMAL);
            applyNumberLayout(number, zoomed);

            if (zoomed) {
                cell.setBackground(selected ? selectedBackground() : plainBackground[index]);
                bindZoomed(index, items);
            } else {
                cell.setBackground(compactBackground(index, items, selected));
                itemContainers[index].setVisibility(View.GONE);
            }

            day.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    /** 月视图：数字撑满整格并居中；周视图：数字贴顶一行，下面留给时间块。 */
    private void applyNumberLayout(TextView number, boolean zoomed) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) number.getLayoutParams();
        int height = zoomed ? dp(ZOOMED_HEADER_DP) : 0;
        int weight = zoomed ? 0 : 1;
        if (params.height != height || params.weight != weight) {
            params.height = height;
            params.weight = weight;
            number.setLayoutParams(params);
        }
        int gravity = zoomed ? (Gravity.TOP | Gravity.CENTER_HORIZONTAL) : Gravity.CENTER;
        if (number.getGravity() != gravity) {
            number.setGravity(gravity);
        }
    }

    /** 月视图格子背景：那天有安排就填淡色（单条带颜色用它，否则淡灰），选中再描一圈。 */
    private Drawable compactBackground(int index, List<Item> items, boolean selected) {
        int fill = 0;
        if (items != null && !items.isEmpty()) {
            if (items.size() == 1 && items.get(0).color != TodoColors.NO_COLOR) {
                fill = TodoColors.withAlpha(items.get(0).color, DAY_FILL_ALPHA);
            } else {
                fill = context.getColor(R.color.field);
            }
        }
        if (fill == 0) {
            return selected ? selectedBackground() : plainBackground[index];
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(9f * context.getResources().getDisplayMetrics().density);
        drawable.setColor(fill);
        if (selected) {
            drawable.setStroke(dp(1), Accent.color(context));
        }
        return drawable;
    }

    /** 周视图：按时间摆放「浅色实心矩形 + 黑字」。 */
    private void bindZoomed(int index, List<Item> items) {
        itemContainers[index].setVisibility(View.VISIBLE);

        int available = dp(ZOOMED_ITEMS_HEIGHT_DP);
        int itemHeight = dp(ZOOMED_ITEM_HEIGHT_DP);
        int gap = dp(ZOOMED_ITEM_GAP_DP);
        int cursor = 0;   // 上一块的底部，用来避免叠在一起

        for (int slot = 0; slot < MAX_ITEMS; slot++) {
            TextView view = itemSlots[index * MAX_ITEMS + slot];
            if (items == null || slot >= items.size()) {
                view.setVisibility(View.GONE);
                continue;
            }
            Item item = items.get(slot);
            view.setVisibility(View.VISIBLE);
            view.setText(item.text);
            view.setBackground(TodoColors.blockDrawable(context, item.color, ZOOMED_ITEM_ALPHA));

            float position = Math.max(0f, Math.min(1f, item.position));
            int top = Math.round(position * (available - itemHeight));
            if (top < cursor) {
                top = cursor;
            }
            if (top + itemHeight > available) {
                top = Math.max(0, available - itemHeight);
            }
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
            if (params.topMargin != top) {
                params.topMargin = top;
                view.setLayoutParams(params);
            }
            cursor = top + itemHeight + gap;
        }
    }

    /** 选中那天的背景：主题色淡填充 + 一圈主题色描边。 */
    private Drawable selectedBackground() {
        float density = context.getResources().getDisplayMetrics().density;
        int accent = Accent.color(context);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(9f * density);
        drawable.setColor(TodoColors.withAlpha(accent, 0.14f));
        drawable.setStroke(Math.max(1, Math.round(density)), accent);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
