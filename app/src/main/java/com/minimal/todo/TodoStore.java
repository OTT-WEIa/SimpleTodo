package com.minimal.todo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 待办的本地存储：SQLite + 单例。
 * 主界面和桌面小组件运行在同一个进程里，共用这一份数据。
 *
 * 排序：每条待办带一个 sort_order，列表按它排序（可长按拖拽调整）。
 * · 有时间的待办初始 sort_order = 它的时间，所以默认就是按时间排的；
 * · 没时间的待办初始 sort_order = -id，所以默认是新的在上面；
 * · 用户手动拖过之后，那一组会被重新编号成 1、2、3…，之后新加的就排到末尾
 *   （没时间的新待办仍会顶到最前面，符合「刚加的马上能看到」的直觉）。
 */
public class TodoStore {

    /** 列表过滤条件。 */
    public static final int FILTER_ALL = 0;
    public static final int FILTER_ACTIVE = 1;
    public static final int FILTER_DONE = 2;

    private static final String TABLE = "todo";
    private static final String[] COLUMNS =
            {"id", "title", "done", "created", "due", "color", "place", "sort_order"};

    /** 手动顺序优先，其次新加的在前。 */
    private static final String ORDER_BY_SORT = "sort_order ASC, id DESC";
    /** 按时间排（日历、周视图、小组件里今天的安排用这个）。 */
    private static final String ORDER_BY_DUE = "due ASC, id DESC";

    private static volatile TodoStore sInstance;
    private final DbHelper helper;

    private TodoStore(Context context) {
        helper = new DbHelper(context);
    }

    public static TodoStore get(Context context) {
        TodoStore instance = sInstance;
        if (instance == null) {
            synchronized (TodoStore.class) {
                instance = sInstance;
                if (instance == null) {
                    instance = new TodoStore(context.getApplicationContext());
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    public synchronized List<Todo> list(int filter) {
        String where;
        switch (filter) {
            case FILTER_ACTIVE:
                where = "done = 0";
                break;
            case FILTER_DONE:
                where = "done = 1";
                break;
            default:
                where = null;
                break;
        }
        String orderBy = filter == FILTER_DONE ? "id DESC" : ORDER_BY_SORT;
        return query(where, null, orderBy);
    }

    /** 指定时间范围内有时间的待办（日历和小组件用），按时间排。 */
    public synchronized List<Todo> listDatedBetween(long fromMillis, long toMillis) {
        return query("due >= ? AND due < ?", new String[]{String.valueOf(fromMillis), String.valueOf(toMillis)},
                ORDER_BY_DUE);
    }

    /** 没有设置时间的待办，按手动顺序。 */
    public synchronized List<Todo> listUndated(int filter) {
        String where;
        switch (filter) {
            case FILTER_ACTIVE:
                where = "due = 0 AND done = 0";
                break;
            case FILTER_DONE:
                where = "due = 0 AND done = 1";
                break;
            default:
                where = "due = 0";
                break;
        }
        return query(where, null, WHERE_UNDATED_ORDER);
    }

    /** 没时间的待办也支持手动顺序。 */
    private static final String WHERE_UNDATED_ORDER = "sort_order ASC, id DESC";

    private List<Todo> query(String where, String[] args, String orderBy) {
        List<Todo> result = new ArrayList<>();
        Cursor cursor = helper.getReadableDatabase().query(
                TABLE, COLUMNS, where, args, null, null, orderBy);
        try {
            while (cursor.moveToNext()) {
                result.add(new Todo(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getInt(2) == 1,
                        cursor.getLong(3),
                        cursor.getLong(4),
                        cursor.getInt(5),
                        cursor.getString(6)));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    /** 添加一条只有标题的待办。 */
    public synchronized long add(String title) {
        return add(title, TodoTime.NONE, TodoColors.NO_COLOR, "");
    }

    /** 添加一条带时间、不带颜色和地点的待办。 */
    public synchronized long add(String title, long due) {
        return add(title, due, TodoColors.NO_COLOR, "");
    }

    /** 添加一条带时间和颜色、不带地点的待办。 */
    public synchronized long add(String title, long due, int color) {
        return add(title, due, color, "");
    }

    /** 添加一条带时间、颜色、地点的待办。 */
    public synchronized long add(String title, long due, int color, String place) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("done", 0);
        values.put("created", System.currentTimeMillis());
        values.put("due", due);
        values.put("color", color);
        values.put("place", place == null ? "" : place);
        values.put("sort_order", nextSortOrder(due));
        return helper.getWritableDatabase().insert(TABLE, null, values);
    }

    /**
     * 新待办放在哪：有时间的用它的时间（默认按时间排、插到对应位置）；
     * 没时间的用 -下一号，这样它排在没时间那组的最前面（刚加的马上能看到）。
     */
    private long nextSortOrder(long due) {
        if (due > 0) {
            return due;
        }
        return -(maxId() + 1);
    }

    private long maxId() {
        Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT IFNULL(MAX(id), 0) FROM " + TABLE, null);
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        } finally {
            cursor.close();
        }
    }

    /** 改标题 / 时间 / 地点（编辑待办时一次写进去）。 */
    public synchronized void update(long id, String title, long due, String place) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("due", due);
        values.put("place", place == null ? "" : place);
        helper.getWritableDatabase().update(TABLE, values, "id = ?", new String[]{String.valueOf(id)});
    }

    /** 把一组待办按给定顺序重新编号（长按拖拽调整顺序后调用）。 */
    public synchronized void reorder(List<Todo> ordered) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            for (int i = 0; i < ordered.size(); i++) {
                values.clear();
                values.put("sort_order", i + 1);
                db.update(TABLE, values, "id = ?", new String[]{String.valueOf(ordered.get(i).id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void setDone(long id, boolean done) {
        ContentValues values = new ContentValues();
        values.put("done", done ? 1 : 0);
        helper.getWritableDatabase().update(TABLE, values, "id = ?", new String[]{String.valueOf(id)});
    }

    /** 修改/清除某条待办的时间，传 {@link TodoTime#NONE} 表示清除。 */
    public synchronized void setDue(long id, long due) {
        ContentValues values = new ContentValues();
        values.put("due", due);
        helper.getWritableDatabase().update(TABLE, values, "id = ?", new String[]{String.valueOf(id)});
    }

    /** 修改/清除某条待办的颜色，传 {@link TodoColors#NO_COLOR} 表示不设颜色。 */
    public synchronized void setColor(long id, int color) {
        ContentValues values = new ContentValues();
        values.put("color", color);
        helper.getWritableDatabase().update(TABLE, values, "id = ?", new String[]{String.valueOf(id)});
    }

    /** 取反完成状态，返回取反之后的状态。 */
    public synchronized boolean toggle(long id) {
        boolean done = false;
        Cursor cursor = helper.getReadableDatabase().query(
                TABLE, new String[]{"done"}, "id = ?", new String[]{String.valueOf(id)},
                null, null, null);
        try {
            if (cursor.moveToFirst()) {
                done = cursor.getInt(0) != 1;
            }
        } finally {
            cursor.close();
        }
        setDone(id, done);
        return done;
    }

    public synchronized void delete(long id) {
        helper.getWritableDatabase().delete(TABLE, "id = ?", new String[]{String.valueOf(id)});
    }

    /** 删除所有已完成，返回删除条数。 */
    public synchronized int clearDone() {
        return helper.getWritableDatabase().delete(TABLE, "done = 1", null);
    }

    /** @return {总数, 未完成数} */
    public synchronized int[] stats() {
        int total = 0;
        int active = 0;
        Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*), SUM(done = 0) FROM " + TABLE, null);
        try {
            if (cursor.moveToFirst()) {
                total = cursor.getInt(0);
                active = cursor.isNull(1) ? 0 : cursor.getInt(1);
            }
        } finally {
            cursor.close();
        }
        return new int[]{total, active};
    }

    private static class DbHelper extends SQLiteOpenHelper {

        private static final String DB_NAME = "todo.db";
        /**
         * v1: id/title/done/created；v2: +due；v3: 加过 category 表；
         * v4: 去掉分类表、待办自带 color；v5: +place；v6: +sort_order（手动排序）。
         */
        private static final int DB_VERSION = 6;

        DbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "title TEXT NOT NULL,"
                    + "done INTEGER NOT NULL DEFAULT 0,"
                    + "created INTEGER NOT NULL,"
                    + "due INTEGER NOT NULL DEFAULT 0,"
                    + "color INTEGER NOT NULL DEFAULT 0,"
                    + "place TEXT NOT NULL DEFAULT '',"
                    + "sort_order INTEGER NOT NULL DEFAULT 0)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN due INTEGER NOT NULL DEFAULT 0");
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN category_id INTEGER NOT NULL DEFAULT 0");
                db.execSQL("CREATE TABLE IF NOT EXISTS category ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "name TEXT NOT NULL,"
                        + "color INTEGER NOT NULL)");
            }
            if (oldVersion < 4) {
                // 分类改成「待办自己带颜色」：先把原来的分类颜色搬过来，再删掉分类表
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN color INTEGER NOT NULL DEFAULT 0");
                db.execSQL("UPDATE " + TABLE + " SET color = IFNULL("
                        + "(SELECT color FROM category WHERE category.id = " + TABLE + ".category_id), 0)");
                db.execSQL("DROP TABLE IF EXISTS category");
            }
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN place TEXT NOT NULL DEFAULT ''");
            }
            if (oldVersion < 6) {
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
                // 老数据保持原来的观感：有时间的按时间排、没时间的新的在前
                db.execSQL("UPDATE " + TABLE + " SET sort_order = CASE WHEN due > 0 THEN due ELSE -id END");
            }
        }
    }
}
