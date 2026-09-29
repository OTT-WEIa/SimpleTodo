package com.minimal.todo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListView;

/**
 * 长按拖拽调整顺序（只用在应用里的列表）。
 *
 * 踩过的两个坑，这里都避开了：
 * 1. 触摸监听要装在**每一行**上 —— 列表行本身可点击，事件会被行吃掉，
 *    装在 ListView 上的话长按永远不触发；
 * 2. 影子行**不能加进 Activity 的视图树** —— 那会让整棵树重新布局、列表重建子视图，
 *    这次手势当场被取消（表现就是"高亮闪一下就没了"）。
 *    所以影子行用 WindowManager 的子窗口来画，Activity 的布局完全不动。
 *
 * 另外拖拽期间不发 notifyDataSetChanged（重建列表同样会打断手势），
 * 落点高亮是直接改那一行子视图的背景，松手时才真正改数据。
 */
@SuppressLint("ClickableViewAccessibility")
final class ListDragReorder implements View.OnTouchListener {

    interface Callback {
        /** 这一行能不能拖（分组标题不能）。 */
        boolean canDrag(int position);

        /** 能不能放到这个位置（要在同一组里）。 */
        boolean canDrop(int from, int to);

        /** 松手了：把 from 行挪到 to 行。 */
        void onDrop(int from, int to);
    }

    private static final int INVALID = -1;

    private final Activity activity;
    private final ListView list;
    private final Callback callback;
    private final int touchSlop;
    private final int[] listLocation = new int[2];
    private final int[] windowLocation = new int[2];

    private final Runnable longPress = this::startDrag;

    private ImageView floating;
    private WindowManager.LayoutParams floatingParams;
    private WindowManager windowManager;
    private int floatingBaseY;
    private float downRawY;

    private View pressedRow;
    private View highlightedRow;
    private int pressedPosition = INVALID;
    private int fromPosition = INVALID;
    private int targetPosition = INVALID;

    ListDragReorder(Activity activity, ListView list, Callback callback) {
        this.activity = activity;
        this.list = list;
        this.callback = callback;
        this.touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
    }

    /** 由 TodoAdapter 把本监听装到每一行的根布局上。 */
    @Override
    public boolean onTouch(View row, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedRow = row;
                pressedPosition = list.getPositionForView(row);
                downRawY = event.getRawY();
                if (pressedPosition != INVALID && callback.canDrag(pressedPosition)) {
                    row.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                if (fromPosition == INVALID) {
                    if (Math.abs(event.getRawY() - downRawY) > touchSlop) {
                        cancelPending();   // 手指动了，说明是在滚列表
                    }
                    return false;
                }
                moveFloating(event.getRawY());
                return true;               // 拖拽中：吃掉事件

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelPending();
                if (fromPosition != INVALID) {
                    finishDrag();
                    return true;
                }
                return false;

            default:
                return false;
        }
    }

    /** 页面被切走时收尾，避免子窗口残留。 */
    void cancelDrag() {
        cancelPending();
        removeFloating();
        clearHighlight();
    }

    private void startDrag() {
        if (pressedPosition == INVALID || fromPosition != INVALID || pressedRow == null
                || pressedRow.getWidth() == 0) {
            return;
        }
        fromPosition = pressedPosition;
        targetPosition = pressedPosition;

        // 让列表别抢这次手势（挂在行上，标志会一路传到 ListView）
        ViewParent parent = pressedRow.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }

        Bitmap snapshot = Bitmap.createBitmap(
                pressedRow.getWidth(), pressedRow.getHeight(), Bitmap.Config.ARGB_8888);
        pressedRow.draw(new Canvas(snapshot));

        floating = new ImageView(activity);
        floating.setImageBitmap(snapshot);
        floating.setAlpha(0.95f);

        list.getLocationInWindow(windowLocation);
        floatingBaseY = windowLocation[1] + pressedRow.getTop();
        windowManager = activity.getWindowManager();
        floatingParams = new WindowManager.LayoutParams(
                pressedRow.getWidth(), pressedRow.getHeight(),
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        floatingParams.token = activity.getWindow().getDecorView().getWindowToken();
        floatingParams.gravity = Gravity.TOP | Gravity.START;
        floatingParams.x = windowLocation[0];
        floatingParams.y = floatingBaseY;
        try {
            windowManager.addView(floating, floatingParams);
        } catch (Throwable ignored) {
            floating = null;   // 加不上子窗口也没关系，靠落点高亮提示
        }

        setHighlight(targetPosition);
    }

    private void moveFloating(float rawY) {
        if (floating != null && windowManager != null) {
            floatingParams.y = floatingBaseY + Math.round(rawY - downRawY);
            try {
                windowManager.updateViewLayout(floating, floatingParams);
            } catch (Throwable ignored) {
                floating = null;
            }
        }

        list.getLocationOnScreen(listLocation);
        int to = list.pointToPosition(0, Math.round(rawY - listLocation[1]));
        if (to != INVALID && to != targetPosition && callback.canDrop(fromPosition, to)) {
            targetPosition = to;
            setHighlight(targetPosition);
        }
    }

    private void finishDrag() {
        int from = fromPosition;
        int to = targetPosition;
        View dragged = pressedRow;
        removeFloating();      // 收影子行 + 清掉按下态
        clearHighlight();
        if (dragged != null) {
            ViewParent parent = dragged.getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(false);
            }
        }
        pressedRow = null;
        if (to != INVALID && to != from && callback.canDrop(from, to)) {
            callback.onDrop(from, to);
        }
    }

    /** 落点高亮：直接改那一行的背景，不发 notifyDataSetChanged。 */
    private void setHighlight(int position) {
        View row = childAt(position);
        if (row == highlightedRow) {
            return;
        }
        clearHighlight();
        if (row != null && position != fromPosition) {
            row.setBackgroundResource(R.drawable.bg_card_target);
            highlightedRow = row;
        }
    }

    private void clearHighlight() {
        if (highlightedRow != null) {
            highlightedRow.setBackgroundResource(R.drawable.bg_card);
            highlightedRow = null;
        }
    }

    private View childAt(int position) {
        if (position < 0) {
            return null;
        }
        return list.getChildAt(position - list.getFirstVisiblePosition());
    }

    private void cancelPending() {
        if (pressedRow != null) {
            pressedRow.removeCallbacks(longPress);
        }
        pressedPosition = INVALID;
    }

    private void removeFloating() {
        if (floating != null && windowManager != null) {
            try {
                windowManager.removeView(floating);
            } catch (Throwable ignored) {
                // 已经没了就算了
            }
        }
        floating = null;
        // 关键：把按下态清掉。拖拽期间手势被我们吃掉了，列表收不到抬起事件，
        // 不清的话那一行的按压高亮会一直亮着（拖几个就亮几个）。
        if (pressedRow != null) {
            pressedRow.setPressed(false);
            pressedRow.removeCallbacks(longPress);
        }
        fromPosition = INVALID;
        targetPosition = INVALID;
    }
}
