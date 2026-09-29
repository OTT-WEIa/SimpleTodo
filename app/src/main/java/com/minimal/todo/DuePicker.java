package com.minimal.todo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;

import java.util.Calendar;

/**
 * 「设置待办时间」的选择流程，主界面和小组件的快速添加共用。
 * 先选今天 / 明天 / 自定义，再选具体时刻；已经设过时间的还可以清除。
 */
final class DuePicker {

    interface Callback {
        void onPicked(long due);
    }

    private DuePicker() {
    }

    static void show(final Activity activity, final long currentDue, final Callback callback) {
        final boolean hasDue = TodoTime.has(currentDue);

        final String[] items = hasDue
                ? new String[]{
                    activity.getString(R.string.pick_today),
                    activity.getString(R.string.pick_tomorrow),
                    activity.getString(R.string.pick_custom),
                    activity.getString(R.string.pick_clear)}
                : new String[]{
                    activity.getString(R.string.pick_today),
                    activity.getString(R.string.pick_tomorrow),
                    activity.getString(R.string.pick_custom)};

        new AlertDialog.Builder(activity)
                .setTitle(R.string.pick_time_title)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        pickClock(activity, currentDue, dayOffset(0), callback);
                    } else if (which == 1) {
                        pickClock(activity, currentDue, dayOffset(1), callback);
                    } else if (which == 2) {
                        pickDate(activity, currentDue, callback);
                    } else {
                        callback.onPicked(TodoTime.NONE);
                    }
                })
                .setNegativeButton(R.string.cancel, (DialogInterface d, int w) -> d.dismiss())
                .show();
    }

    /** 选具体时刻，日期用 dayStart（当天 00:00 的时间戳）。 */
    private static void pickClock(final Activity activity, final long currentDue,
                                  final long dayStart, final Callback callback) {
        Calendar base = Calendar.getInstance();
        if (TodoTime.has(currentDue)) {
            base.setTimeInMillis(currentDue);
        } else {
            base.add(Calendar.HOUR_OF_DAY, 1);   // 默认放到一小时后的整点
        }
        new TimePickerDialog(activity,
                (view, hourOfDay, minute) -> {
                    Calendar result = Calendar.getInstance();
                    result.setTimeInMillis(dayStart);
                    result.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    result.set(Calendar.MINUTE, minute);
                    result.set(Calendar.SECOND, 0);
                    result.set(Calendar.MILLISECOND, 0);
                    callback.onPicked(result.getTimeInMillis());
                },
                base.get(Calendar.HOUR_OF_DAY), base.get(Calendar.MINUTE), true).show();
    }

    /** 自定义日期，选完日期再选时刻。 */
    private static void pickDate(final Activity activity, final long currentDue, final Callback callback) {
        Calendar base = Calendar.getInstance();
        if (TodoTime.has(currentDue)) {
            base.setTimeInMillis(currentDue);
        }
        new DatePickerDialog(activity,
                (view, year, month, dayOfMonth) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.set(year, month, dayOfMonth, 0, 0, 0);
                    chosen.set(Calendar.MILLISECOND, 0);
                    pickClock(activity, currentDue, chosen.getTimeInMillis(), callback);
                },
                base.get(Calendar.YEAR), base.get(Calendar.MONTH), base.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static long dayOffset(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(TodoTime.startOfDay(System.currentTimeMillis()));
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTimeInMillis();
    }
}
