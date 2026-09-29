package com.minimal.todo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 待办标记颜色的绘制工具：
 * · 可选颜色（加待办时直接挑）；
 * · 应用里画圆点：**设了颜色是实心圆，没设颜色是透明细边框的空心圆**；
 * · 日历格子里的浅色实心块；
 * · 小组件里画圆点 —— RemoteViews 不能给图片动态上色，所以在应用进程里按颜色生成
 *   小圆形位图，再用 setImageViewBitmap 塞进小组件（同色位图缓存复用）。
 */
final class TodoColors {

    /** 没设颜色。 */
    static final int NO_COLOR = 0;

    /** 没设颜色时，日历色块 / 周视图方块用的中性填充色。 */
    private static final int NEUTRAL = 0xFFA0A0AE;

    /** 空心圆的描边色。 */
    private static final int STROKE = 0xFF8E8E9A;

    /** 空心圆描边宽度（dp）。 */
    private static final float STROKE_WIDTH_DP = 1.2f;

    /** 可选的颜色。 */
    static final int[] PALETTE = {
            0xFF3D7EFF, 0xFF5B8DEF, 0xFF8B5CF6, 0xFFB65CE8,
            0xFFE8559B, 0xFFE5484D, 0xFFF2764B, 0xFFF0A830,
            0xFFD6C020, 0xFF7BB93B, 0xFF22A06B, 0xFF2AA8A8,
            0xFF8E8E9A, 0xFF6B6B76,
    };

    /** 小组件圆点位图的边长（px）：纯色圆放大也不会糊，小一点省 RemoteViews 传输量。 */
    private static final int DOT_PX = 36;

    private static final Map<Integer, Bitmap> DOT_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Bitmap> SYMBOL_CACHE = new ConcurrentHashMap<>();
    /** 小组件上「＋」「✓」这类图标位图的边长。 */
    private static final int SYMBOL_PX = 72;

    private TodoColors() {
    }

    /** 应用里的圆点：有颜色＝实心圆，没颜色＝透明细边框的空心圆。 */
    static GradientDrawable dotDrawable(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        if (color == NO_COLOR) {
            float density = context.getResources().getDisplayMetrics().density;
            drawable.setColor(Color.TRANSPARENT);
            drawable.setStroke(Math.max(2, Math.round(STROKE_WIDTH_DP * density)), STROKE);
        } else {
            drawable.setColor(color);
        }
        return drawable;
    }

    /** 日历格子里的浅色实心块 / 周视图的待办块。 */
    static GradientDrawable blockDrawable(Context context, int color, float alpha) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(4f * context.getResources().getDisplayMetrics().density);
        drawable.setColor(withAlpha(color == NO_COLOR ? NEUTRAL : color, alpha));
        return drawable;
    }

    /** 给颜色换一个透明度。 */
    static int withAlpha(int color, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** 小组件的「＋」入口：主题色实心圆 + 白色加号（主题色要在位图里画，RemoteViews 不能改色）。 */
    static Bitmap plusBitmap(int accent) {
        return symbolBitmap(accent, true);
    }

    /** 小组件里已完成的样子：主题色实心圆 + 白色对勾。 */
    static Bitmap checkBitmap(int accent) {
        return symbolBitmap(accent, false);
    }

    private static Bitmap symbolBitmap(int accent, boolean plus) {
        int key = plus ? accent : accent ^ 0x00000100;
        Bitmap cached = SYMBOL_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Bitmap bitmap = Bitmap.createBitmap(SYMBOL_PX, SYMBOL_PX, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float center = SYMBOL_PX / 2f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(accent);
        canvas.drawCircle(center, center, center, paint);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(SYMBOL_PX * 0.11f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float arm = SYMBOL_PX * 0.24f;
        if (plus) {
            canvas.drawLine(center - arm, center, center + arm, center, paint);
            canvas.drawLine(center, center - arm, center, center + arm, paint);
        } else {
            Path path = new Path();
            path.moveTo(center - arm, center);
            path.lineTo(center - arm * 0.2f, center + arm * 0.75f);
            path.lineTo(center + arm, center - arm * 0.65f);
            canvas.drawPath(path, paint);
        }
        SYMBOL_CACHE.put(key, bitmap);
        return bitmap;
    }

    /** 小组件里的圆点位图（同色缓存）：有颜色＝实心，没颜色＝空心细描边。 */
    static Bitmap dotBitmap(int color) {
        Bitmap cached = DOT_CACHE.get(color);
        if (cached != null) {
            return cached;
        }
        Bitmap bitmap = Bitmap.createBitmap(DOT_PX, DOT_PX, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float radius = DOT_PX / 2f;
        if (color == NO_COLOR) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            paint.setColor(STROKE);
            radius -= 2f;
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
        }
        canvas.drawCircle(DOT_PX / 2f, DOT_PX / 2f, radius, paint);
        DOT_CACHE.put(color, bitmap);
        return bitmap;
    }
}
