package com.minimal.todo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/** 编辑一条待办：内容、时间、地点都能改，保存后同步刷新桌面小组件。 */
final class TodoEditDialog {

    private TodoEditDialog() {
    }

    static void show(final Activity activity, final Todo todo, final Runnable onSaved) {
        final View content = activity.getLayoutInflater().inflate(R.layout.dialog_edit_todo, null);
        final EditText titleInput = content.findViewById(R.id.edit_title);
        final TextView timeChip = content.findViewById(R.id.edit_time);
        final TextView placeChip = content.findViewById(R.id.edit_place);

        titleInput.setText(todo.title);
        titleInput.setSelection(titleInput.getText().length());

        final long[] due = {todo.due};
        final String[] place = {todo.place};
        showTime(activity, timeChip, due[0]);
        showPlace(activity, placeChip, place[0]);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.edit_title)
                .setView(content)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        timeChip.setOnClickListener(v -> DuePicker.show(activity, due[0], picked -> {
            due[0] = picked;
            showTime(activity, timeChip, due[0]);
        }));
        placeChip.setOnClickListener(v -> PlaceInput.show(activity, place[0], picked -> {
            place[0] = picked;
            showPlace(activity, placeChip, place[0]);
        }));

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String text = titleInput.getText().toString().trim();
                    if (TextUtils.isEmpty(text)) {
                        Toast.makeText(activity, R.string.empty_input, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    TodoStore.get(activity).update(todo.id, text, due[0], place[0]);
                    BaseTodoWidgetProvider.refreshAll(activity);   // 小组件同步
                    if (onSaved != null) {
                        onSaved.run();
                    }
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private static void showTime(Activity activity, TextView chip, long due) {
        if (TodoTime.has(due)) {
            chip.setText(TodoTime.formatFull(activity, due));
            chip.setTextColor(activity.getColor(R.color.accent));
        } else {
            chip.setText(R.string.time_label_none);
            chip.setTextColor(activity.getColor(R.color.text_secondary));
        }
    }

    private static void showPlace(Activity activity, TextView chip, String place) {
        if (place != null && !place.isEmpty()) {
            chip.setText(place);
            chip.setTextColor(activity.getColor(R.color.accent));
        } else {
            chip.setText(R.string.place_label_none);
            chip.setTextColor(activity.getColor(R.color.text_secondary));
        }
    }
}
