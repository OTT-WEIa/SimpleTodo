package com.minimal.todo;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 主界面列表的适配器：支持分组标题行（有日期 / 无日期 / 已完成）。 */
public class TodoAdapter extends BaseAdapter {

    public interface Listener {
        void onToggle(Todo todo);

        void onDelete(Todo todo);

        /** 点了行里的时间/地点，或长按整行：编辑内容、时间、地点。 */
        void onEdit(Todo todo);

        /** 点了行里的颜色圆点，改颜色。 */
        void onEditColor(Todo todo);
    }

    /** 列表里的一行：分组标题，或者一条待办。 */
    public static final class Row {

        static final int TYPE_TODO = 0;
        static final int TYPE_HEADER = 1;

        final int type;
        final String header;
        final Todo todo;

        private Row(int type, String header, Todo todo) {
            this.type = type;
            this.header = header;
            this.todo = todo;
        }

        public static Row header(String text) {
            return new Row(TYPE_HEADER, text, null);
        }

        public static Row todo(Todo todo) {
            return new Row(TYPE_TODO, null, todo);
        }
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();
    /** 长按拖拽的触摸监听，装到每一行上（装在 ListView 上收不到事件）。 */
    private View.OnTouchListener dragTouchListener;

    public TodoAdapter(Context context, Listener listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setRows(List<Row> data) {
        rows.clear();
        if (data != null) {
            rows.addAll(data);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        Row row = rows.get(position);
        return row.type == Row.TYPE_HEADER ? -(position + 1L) : row.todo.id;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row row = rows.get(position);
        if (row.type == Row.TYPE_HEADER) {
            TextView header = convertView instanceof TextView
                    ? (TextView) convertView
                    : (TextView) inflater.inflate(R.layout.item_header, parent, false);
            header.setText(row.header);
            return header;
        }

        ViewHolder holder;
        if (convertView == null || !(convertView.getTag() instanceof ViewHolder)) {
            convertView = inflater.inflate(R.layout.item_todo, parent, false);
            holder = new ViewHolder();
            holder.check = convertView.findViewById(R.id.item_check);
            holder.dot = convertView.findViewById(R.id.item_color_dot);
            holder.title = convertView.findViewById(R.id.item_title);
            holder.time = convertView.findViewById(R.id.item_time);
            holder.place = convertView.findViewById(R.id.item_place);
            holder.edit = convertView.findViewById(R.id.item_edit);
            holder.delete = convertView.findViewById(R.id.item_delete);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Todo item = row.todo;

        // 复用条目时先摘掉监听，避免 setChecked 触发上一条数据的回调
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(item.done);
        holder.check.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked != item.done) {
                listener.onToggle(item);
            }
        });

        holder.title.setText(item.title);
        holder.title.setPaintFlags(item.done
                ? holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : holder.title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        holder.title.setTextColor(context.getColor(item.done ? R.color.text_done : R.color.text_primary));

        holder.dot.setBackground(TodoColors.dotDrawable(context, item.color));

        if (item.hasDue()) {
            holder.time.setVisibility(View.VISIBLE);
            holder.time.setText(TodoTime.formatFull(context, item.due));
            int colorRes;
            if (item.done) {
                colorRes = R.color.text_done;
            } else if (TodoTime.isOverdue(item.due, false)) {
                colorRes = R.color.time_overdue;
            } else {
                colorRes = R.color.text_secondary;
            }
            holder.time.setTextColor(context.getColor(colorRes));
        } else {
            holder.time.setVisibility(View.GONE);
        }

        if (item.hasPlace()) {
            holder.place.setVisibility(View.VISIBLE);
            holder.place.setText(context.getString(R.string.place_with_separator, item.place));
            holder.place.setTextColor(context.getColor(
                    item.done ? R.color.text_done : R.color.text_secondary));
        } else {
            holder.place.setVisibility(View.GONE);
        }

        // 长按整行＝拖拽调整顺序（由 ListDragReorder 接管），所以这里不设长按监听；
        // 拖拽时的落点高亮也由它直接改子视图背景，不走 notifyDataSetChanged
        convertView.setOnTouchListener(dragTouchListener);
        convertView.setOnClickListener(v -> listener.onToggle(item));
        convertView.findViewById(R.id.item_meta).setOnClickListener(v -> listener.onEdit(item));
        convertView.findViewById(R.id.item_color_hit)
                .setOnClickListener(v -> listener.onEditColor(item));
        holder.edit.setOnClickListener(v -> listener.onEdit(item));
        holder.delete.setOnClickListener(v -> listener.onDelete(item));
        return convertView;
    }

    /** 装上长按拖拽用的触摸监听（由 MainActivity 传入）。 */
    public void setDragTouchListener(View.OnTouchListener listener) {
        dragTouchListener = listener;
    }

    private static class ViewHolder {
        CheckBox check;
        View dot;
        TextView title;
        TextView time;
        TextView place;
        ImageButton edit;
        ImageButton delete;
    }
}
