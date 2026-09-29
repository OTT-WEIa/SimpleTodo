package com.minimal.todo;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.EditText;

/** 填写地点：纯手填文本，不做定位。 */
final class PlaceInput {

    interface Callback {
        void onPicked(String place);
    }

    private PlaceInput() {
    }

    static void show(final Activity activity, final String current, final Callback callback) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_place, null);
        final EditText input = content.findViewById(R.id.place_input);
        if (current != null && !current.isEmpty()) {
            input.setText(current);
            input.setSelection(input.getText().length());
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.place_title)
                .setView(content)
                .setPositiveButton(R.string.save,
                        (dialog, which) -> callback.onPicked(input.getText().toString().trim()))
                .setNeutralButton(R.string.place_clear, (dialog, which) -> callback.onPicked(""))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
