package com.minimal.todo;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

/** 直接挑一个标记颜色（加待办时用，也可以给已有待办改）。 */
final class ColorPicker {

    interface Callback {
        void onPicked(int color);
    }

    private static final int SWATCHES_PER_ROW = 6;

    private ColorPicker() {
    }

    static void show(final Activity activity, final int currentColor, final Callback callback) {
        final View content = activity.getLayoutInflater().inflate(R.layout.dialog_color_picker, null);
        final LinearLayout rows = content.findViewById(R.id.color_rows);
        final LayoutInflater inflater = activity.getLayoutInflater();
        final View[] swatches = new View[TodoColors.PALETTE.length];

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.color_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .create();

        LinearLayout row = null;
        for (int i = 0; i < TodoColors.PALETTE.length; i++) {
            if (i % SWATCHES_PER_ROW == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_HORIZONTAL);
                rows.addView(row);
            }
            View swatch = inflater.inflate(R.layout.item_color_swatch, row, false);
            final int color = TodoColors.PALETTE[i];
            swatch.findViewById(R.id.swatch_dot).setBackground(TodoColors.dotDrawable(activity, color));
            swatch.setSelected(color == currentColor);
            swatch.setOnClickListener(v -> {
                callback.onPicked(color);
                dialog.dismiss();
            });
            row.addView(swatch);
            swatches[i] = swatch;
        }

        content.findViewById(R.id.btn_no_color).setOnClickListener(v -> {
            callback.onPicked(TodoColors.NO_COLOR);
            dialog.dismiss();
        });

        dialog.show();
    }
}
