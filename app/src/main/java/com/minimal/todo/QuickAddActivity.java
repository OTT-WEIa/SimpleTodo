package com.minimal.todo;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 桌面小组件上点圆圈加号弹出来的「快速添加」窗口。
 * 只填标题就能存（不设置时间），想带时间就点一下时间胶囊选。
 */
public class QuickAddActivity extends Activity {

    private TodoStore store;
    private EditText input;
    private TextView timeChip;
    private TextView colorChip;

    /** 这条待办要用的时间，{@link TodoTime#NONE} 表示不设置。 */
    private long due = TodoTime.NONE;
    /** 这条待办的颜色，{@link TodoColors#NO_COLOR} 表示不设颜色。 */
    private int color = TodoColors.NO_COLOR;

    /** 深浅模式：按设置改一下上下文的 configuration（跟随系统时原样返回）。 */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(ThemePrefs.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getTheme().applyStyle(Accent.styleRes(this), true);   // 跟着设置的深浅 / 主题色
        setContentView(R.layout.activity_quick_add);
        setFinishOnTouchOutside(true);
        AppGuard.check(this);   // 万一用户先从小组件进来，声明弹窗也要弹一次

        store = TodoStore.get(this);
        input = findViewById(R.id.quick_input);
        timeChip = findViewById(R.id.quick_time);
        colorChip = findViewById(R.id.quick_color);

        findViewById(R.id.quick_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.quick_confirm).setOnClickListener(v -> save());
        timeChip.setOnClickListener(v -> DuePicker.show(this, due, picked -> {
            due = picked;
            updateTimeChip();
        }));
        colorChip.setOnClickListener(v -> ColorPicker.show(this, color, picked -> {
            color = picked;
            updateColorChip();
        }));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                save();
                return true;
            }
            return false;
        });

        updateTimeChip();
        updateColorChip();

        // 直接从桌面弹出来，输入框自动获得焦点、键盘自动弹出
        input.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    private void updateTimeChip() {
        if (TodoTime.has(due)) {
            timeChip.setText(TodoTime.formatFull(this, due));
            timeChip.setTextColor(getColor(R.color.accent));
        } else {
            timeChip.setText(R.string.time_label_none);
            timeChip.setTextColor(getColor(R.color.text_secondary));
        }
    }

    /** 颜色胶囊：前面的圆点是当前挑的颜色。 */
    private void updateColorChip() {
        float density = getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable dot = TodoColors.dotDrawable(this, color);
        int size = Math.round(12 * density);
        dot.setSize(size, size);
        colorChip.setCompoundDrawablesWithIntrinsicBounds(dot, null, null, null);
        colorChip.setCompoundDrawablePadding(Math.round(6 * density));
        colorChip.setTextColor(getColor(color == TodoColors.NO_COLOR
                ? R.color.text_secondary : R.color.accent));
    }

    private void save() {
        String text = input.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, R.string.empty_input, Toast.LENGTH_SHORT).show();
            return;
        }
        store.add(text, due, color);
        BaseTodoWidgetProvider.refreshAll(this);   // 桌面小组件立刻更新
        finish();
    }
}
