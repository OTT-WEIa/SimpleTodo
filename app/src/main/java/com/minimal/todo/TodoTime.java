package com.minimal.todo;

import android.content.Context;

import java.util.Calendar;

/**
 * 待办时间的格式化和“是否已过期”判断。
 * 没设时间的待办用 {@link #NONE}（0）表示。
 */
final class TodoTime {

    /** 没有设置时间。 */
    static final long NONE = 0L;

    private TodoTime() {
    }

    static boolean has(long due) {
        return due > NONE;
    }

    /** 已经过了时间且还没完成。 */
    static boolean isOverdue(long due, boolean done) {
        return due > NONE && !done && due < System.currentTimeMillis();
    }

    /** 应用里显示的完整时间：今天 18:00 / 明天 09:00 / 9月25日 14:30 / 2027年9月25日 14:30 */
    static String formatFull(Context context, long due) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(due);
        String hourMinute = hourMinute(target);

        if (sameDay(now, target)) {
            return context.getString(R.string.time_today, hourMinute);
        }
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        if (sameDay(tomorrow, target)) {
            return context.getString(R.string.time_tomorrow, hourMinute);
        }
        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR)) {
            return context.getString(R.string.time_this_year,
                    target.get(Calendar.MONTH) + 1, target.get(Calendar.DAY_OF_MONTH), hourMinute);
        }
        return context.getString(R.string.time_other_year,
                target.get(Calendar.YEAR), target.get(Calendar.MONTH) + 1,
                target.get(Calendar.DAY_OF_MONTH), hourMinute);
    }

    /**
     * 小组件里的紧凑格式，最多 5 个字符：18:00 / 明18:00 / 9/25
     * 小组件一行还要显示最多 6 个字的标题，时间必须短。
     */
    static String formatCompact(Context context, long due) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(due);

        if (sameDay(now, target)) {
            return hourMinute(target);
        }
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        if (sameDay(tomorrow, target)) {
            return context.getString(R.string.time_compact_tomorrow, hourMinute(target));
        }
        return context.getString(R.string.time_compact_date,
                target.get(Calendar.MONTH) + 1, target.get(Calendar.DAY_OF_MONTH));
    }

    /** 取某个时刻的 00:00（用于“今天/明天”的基准比较）。 */
    static long startOfDay(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static String hourMinute(Calendar calendar) {
        return two(calendar.get(Calendar.HOUR_OF_DAY)) + ":" + two(calendar.get(Calendar.MINUTE));
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
