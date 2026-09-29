package com.minimal.todo;

/** 一条待办。due 为 0 表示没有设置时间，color 为 0 表示没有设置颜色，place 为空表示没写地点。 */
public class Todo {

    public final long id;
    public final String title;
    public final boolean done;
    public final long created;
    /** 待办时间（毫秒时间戳），0 表示不设置时间。 */
    public final long due;
    /** 标记颜色（ARGB），0 表示没设颜色。 */
    public final int color;
    /** 地点（自己填的，不做定位），空串表示没填。 */
    public final String place;

    public Todo(long id, String title, boolean done, long created, long due, int color, String place) {
        this.id = id;
        this.title = title;
        this.done = done;
        this.created = created;
        this.due = due;
        this.color = color;
        this.place = place == null ? "" : place;
    }

    public boolean hasDue() {
        return due > 0L;
    }

    public boolean hasPlace() {
        return !place.isEmpty();
    }
}
