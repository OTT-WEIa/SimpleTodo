package com.minimal.todo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待办清单主界面：列表 / 日历两种视图（日历可以放大成课程表样式），
 * 加待办时可以直接挑一个标记颜色。
 *
 * 应用界面固定是不透明白底（深色模式近黑）；
 * 「透明毛玻璃 / 液态玻璃」只作用在桌面小组件上，入口在右上角的「小组件外观」。
 */
public class MainActivity extends Activity implements TodoAdapter.Listener, MonthGrid.Listener {

    private static final String PREF_FILE = "app";
    private static final String PREF_CALENDAR_ZOOMED = "calendar_zoomed";

    private static final int MODE_LIST = 0;
    private static final int MODE_CALENDAR = 1;

    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;
    /** 日历格子里每条待办只显示这么多字（再长就省略）。 */
    private static final int CELL_CHARS = 3;
    /** 放大后的周视图里，一天按 6:00 ~ 24:00 映射到格子高度上。 */
    private static final int DAY_START_MINUTES = 6 * 60;
    private static final int DAY_SPAN_MINUTES = 18 * 60;

    private TodoStore store;
    private TodoAdapter adapter;
    /** 当前列表里的行（含分组标题），拖拽排序和分组判断要用。 */
    private List<TodoAdapter.Row> currentRows = new ArrayList<>();
    private ListDragReorder dragReorder;
    private MonthGrid grid;

    private EditText input;
    private ListView list;
    private TextView summary;
    private TextView clearDone;
    private TextView timeChip;
    private TextView colorChip;
    private TextView placeChip;
    private TextView empty;
    private TextView calendarTitle;
    private TextView zoomButton;
    private TextView calendarHint;
    private View filterRow;
    private View calendarArea;
    private View bottomBar;
    private TextView[] filters;
    private TextView[] viewTabs;

    private int mode = MODE_LIST;
    /** 日历是否放大成课程表样式（放大后就相当于周视图）。 */
    private boolean calendarZoomed;
    private int filter = TodoStore.FILTER_ALL;
    /** 下一条待办要用的时间、颜色和地点。 */
    private long pendingDue = TodoTime.NONE;
    private int pendingColor = TodoColors.NO_COLOR;
    private String pendingPlace = "";
    /** 日历里选中的那一天。 */
    private long selectedDayStart = TodoTime.startOfDay(System.currentTimeMillis());
    /** 日历当前显示的那一月 / 那一周（毫秒时间戳，随便取其中一刻）。 */
    private long anchorMillis = System.currentTimeMillis();

    /** 深浅模式：按设置改一下上下文的 configuration（跟随系统时原样返回）。 */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(ThemePrefs.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getTheme().applyStyle(Accent.styleRes(this), true);   // 叠上主题色（要在 setContentView 之前）
        setContentView(R.layout.activity_main);
        setupSystemBars();
        AppGuard.check(this);   // 首次打开的声明弹窗 + 基础完整性检查

        store = TodoStore.get(this);
        calendarZoomed = getSharedPreferences(PREF_FILE, MODE_PRIVATE)
                .getBoolean(PREF_CALENDAR_ZOOMED, false);

        input = findViewById(R.id.input);
        list = findViewById(R.id.list);
        summary = findViewById(R.id.summary);
        clearDone = findViewById(R.id.btn_clear_done);
        timeChip = findViewById(R.id.btn_time);
        colorChip = findViewById(R.id.btn_color);
        placeChip = findViewById(R.id.btn_place);
        empty = findViewById(R.id.empty);
        calendarTitle = findViewById(R.id.calendar_title);
        zoomButton = findViewById(R.id.btn_zoom);
        calendarHint = findViewById(R.id.calendar_hint);
        filterRow = findViewById(R.id.filter_row);
        calendarArea = findViewById(R.id.calendar_area);
        bottomBar = findViewById(R.id.bottom_bar);
        viewTabs = new TextView[]{findViewById(R.id.tab_list), findViewById(R.id.tab_calendar)};
        filters = new TextView[]{findViewById(R.id.tab_all), findViewById(R.id.tab_active), findViewById(R.id.tab_done)};

        ImageButton addButton = findViewById(R.id.btn_add);
        addButton.setOnClickListener(v -> addTodo());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                addTodo();
                return true;
            }
            return false;
        });

        // 点击空白区域收起键盘
        findViewById(R.id.root).setOnClickListener(v -> hideKeyboard());
        findViewById(R.id.btn_settings).setOnClickListener(v -> showSettings());
        timeChip.setOnClickListener(v -> pickPendingDue());
        colorChip.setOnClickListener(v -> pickPendingColor());
        findViewById(R.id.btn_reset).setOnClickListener(v -> resetPending());
        placeChip.setOnClickListener(v -> pickPendingPlace());
        zoomButton.setOnClickListener(v -> toggleCalendarZoom());

        for (int i = 0; i < viewTabs.length; i++) {
            final int index = i;
            viewTabs[i].setOnClickListener(v -> setMode(index));
        }
        for (int i = 0; i < filters.length; i++) {
            final int index = i;
            filters[i].setOnClickListener(v -> setFilter(index));
        }
        findViewById(R.id.btn_prev).setOnClickListener(v -> shiftCalendar(-1));
        findViewById(R.id.btn_next).setOnClickListener(v -> shiftCalendar(1));

        grid = new MonthGrid(this, findViewById(R.id.calendar_rows), this);
        adapter = new TodoAdapter(this, this);
        list.setAdapter(adapter);
        list.setEmptyView(empty);
        // 长按整行拖拽调整顺序（只影响应用里的列表，小组件不动）
        dragReorder = new ListDragReorder(this, list, new ListDragReorder.Callback() {
            @Override
            public boolean canDrag(int position) {
                return rowAt(position) != null;
            }

            @Override
            public boolean canDrop(int from, int to) {
                if (rowAt(from) == null || rowAt(to) == null) {
                    return false;
                }
                // 只允许在同一组里（有日期 / 无日期 / 已完成）挪动
                return groupStart(currentRows, from) == groupStart(currentRows, to);
            }

            @Override
            public void onDrop(int from, int to) {
                moveRow(from, to);
            }
        });
        adapter.setDragTouchListener(dragReorder);

        clearDone.setOnClickListener(v -> confirmClearDone());

        updateTimeChip();
        updateColorChip();
        updatePlaceChip();
        setMode(MODE_LIST);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (dragReorder != null) {
            dragReorder.cancelDrag();   // 收掉拖拽用的子窗口
        }
    }

    // ---------------- 系统栏 ----------------

    /** 内容铺到状态栏、导航栏下面，窗口底是不透明的 @color/bg，所以整屏是统一的白底。 */
    private void setupSystemBars() {
        Window window = getWindow();
        View root = findViewById(R.id.root);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SystemBars.applyEdgeToEdge(this, window, root);
        } else {
            int barColor = getColor(R.color.bg);
            window.setStatusBarColor(barColor);
            window.setNavigationBarColor(barColor);
        }
    }

    /** 把 API 30 才有的调用关在这个类里，老系统上不会去校验这些类型。 */
    @TargetApi(Build.VERSION_CODES.R)
    private static final class SystemBars {

        static void applyEdgeToEdge(Activity activity, Window window, View root) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);

            boolean night = (activity.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null && !night) {
                int lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightBars, lightBars);
            }

            final int left = root.getPaddingLeft();
            final int top = root.getPaddingTop();
            final int right = root.getPaddingRight();
            final int bottom = root.getPaddingBottom();
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(left, top + bars.top, right, bottom + bars.bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
    }

    // ---------------- 视图切换 ----------------

    private void setMode(int newMode) {
        mode = newMode;
        for (int i = 0; i < viewTabs.length; i++) {
            viewTabs[i].setSelected(i == newMode);
        }
        filterRow.setVisibility(mode == MODE_LIST ? View.VISIBLE : View.GONE);
        calendarArea.setVisibility(mode == MODE_LIST ? View.GONE : View.VISIBLE);
        bottomBar.setVisibility(mode == MODE_LIST ? View.VISIBLE : View.GONE);
        empty.setText(mode == MODE_LIST ? R.string.empty_hint : R.string.empty_day);
        updateZoomButton();
        reload();
    }

    private void setFilter(int index) {
        filter = index;
        for (int i = 0; i < filters.length; i++) {
            filters[i].setSelected(i == index);
        }
        reload();
    }

    /**
     * 放大 / 缩小日历：放大后格子变高，里面能看到两三个字的内容（课程表那样），
     * 一屏正好一周 —— 所以不需要再单独放一个「月 / 周」切换。
     */
    private void toggleCalendarZoom() {
        calendarZoomed = !calendarZoomed;
        getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit()
                .putBoolean(PREF_CALENDAR_ZOOMED, calendarZoomed).apply();
        updateZoomButton();
        reload();
    }

    private void updateZoomButton() {
        zoomButton.setText(calendarZoomed ? R.string.calendar_zoom_out : R.string.calendar_zoom_in);
    }

    /** 上一页 / 下一页：缩小状态翻月，放大状态（相当于周视图）翻周。 */
    private void shiftCalendar(int direction) {
        Calendar anchor = Calendar.getInstance();
        anchor.setTimeInMillis(anchorMillis);
        if (calendarZoomed) {
            anchor.add(Calendar.DAY_OF_MONTH, direction * 7);
            anchorMillis = anchor.getTimeInMillis();
            selectedDayStart = MonthGrid.mondayOf(anchorMillis);
        } else {
            anchor.set(Calendar.DAY_OF_MONTH, 1);
            anchor.add(Calendar.MONTH, direction);
            anchorMillis = anchor.getTimeInMillis();
            selectedDayStart = TodoTime.startOfDay(anchorMillis);
        }
        reload();
    }

    @Override
    public void onDayClick(long dayStart) {
        selectedDayStart = dayStart;
        Calendar clicked = Calendar.getInstance();
        clicked.setTimeInMillis(dayStart);
        Calendar anchor = Calendar.getInstance();
        anchor.setTimeInMillis(anchorMillis);
        boolean sameMonth = clicked.get(Calendar.MONTH) == anchor.get(Calendar.MONTH)
                && clicked.get(Calendar.YEAR) == anchor.get(Calendar.YEAR);
        if (!calendarZoomed && !sameMonth) {
            anchorMillis = dayStart;   // 缩小状态点到了相邻月份的日子，日历跟着翻过去
        }
        reload();
    }

    /** 重新读取数据并刷新界面，同时把桌面小组件一起刷新。 */
    private void reload() {
        List<TodoAdapter.Row> rows;
        if (mode == MODE_LIST) {
            rows = buildListRows();
        } else {
            bindCalendar();
            rows = buildDayRows(selectedDayStart);
        }
        currentRows = rows;
        adapter.setRows(rows);

        int[] stats = store.stats();
        summary.setText(getString(R.string.summary, stats[0], stats[1]));
        clearDone.setEnabled(stats[0] - stats[1] > 0);
        clearDone.setAlpha(stats[0] - stats[1] > 0 ? 1f : 0.4f);
        BaseTodoWidgetProvider.refreshAll(this);
    }

    // ---------------- 列表分组 ----------------

    /** 列表视图：有日期 / 无日期 / 已完成，分开显示。 */
    private List<TodoAdapter.Row> buildListRows() {
        List<Todo> dated = new ArrayList<>();
        List<Todo> undated = new ArrayList<>();
        if (filter != TodoStore.FILTER_DONE) {
            for (Todo todo : store.list(TodoStore.FILTER_ACTIVE)) {
                if (todo.hasDue()) {
                    dated.add(todo);
                } else {
                    undated.add(todo);
                }
            }
        }
        List<Todo> done = filter == TodoStore.FILTER_ACTIVE
                ? new ArrayList<>()
                : store.list(TodoStore.FILTER_DONE);

        List<String> titles = new ArrayList<>();
        List<List<Todo>> groups = new ArrayList<>();
        titles.add(getString(R.string.group_dated));
        groups.add(dated);
        titles.add(getString(R.string.group_undated));
        groups.add(undated);
        titles.add(getString(R.string.group_done));
        groups.add(done);

        List<TodoAdapter.Row> rows = new ArrayList<>();
        addGroups(rows, titles, groups);
        return rows;
    }

    /** 日历视图下面：选中那一天的待办 + 无日期的一组。 */
    private List<TodoAdapter.Row> buildDayRows(long dayStart) {
        List<TodoAdapter.Row> rows = new ArrayList<>(toRows(
                store.listDatedBetween(dayStart, dayStart + DAY_MILLIS)));
        List<Todo> undated = store.listUndated(TodoStore.FILTER_ACTIVE);
        if (!undated.isEmpty()) {
            rows.add(TodoAdapter.Row.header(getString(R.string.group_undated)));
            rows.addAll(toRows(undated));
        }
        return rows;
    }

    /** 多个分组时给每组加个标题，只有一个分组就不加（省地方也更清爽）。 */
    private void addGroups(List<TodoAdapter.Row> rows, List<String> titles, List<List<Todo>> groups) {
        int nonEmpty = 0;
        for (List<Todo> group : groups) {
            if (!group.isEmpty()) {
                nonEmpty++;
            }
        }
        for (int i = 0; i < groups.size(); i++) {
            List<Todo> group = groups.get(i);
            if (group.isEmpty()) {
                continue;
            }
            if (nonEmpty > 1) {
                rows.add(TodoAdapter.Row.header(titles.get(i)));
            }
            rows.addAll(toRows(group));
        }
    }

    private static List<TodoAdapter.Row> toRows(List<Todo> todos) {
        List<TodoAdapter.Row> rows = new ArrayList<>(todos.size());
        for (Todo todo : todos) {
            rows.add(TodoAdapter.Row.todo(todo));
        }
        return rows;
    }

    // ---------------- 长按拖拽调整顺序 ----------------

    /** 这个位置是不是一条待办（分组标题返回 null）。 */
    private Todo rowAt(int position) {
        if (position < 0 || position >= currentRows.size()) {
            return null;
        }
        TodoAdapter.Row row = currentRows.get(position);
        return row.type == TodoAdapter.Row.TYPE_TODO ? row.todo : null;
    }

    /** 一行所在分组的起始行（它前面最近的那个分组标题的下一行）。 */
    private static int groupStart(List<TodoAdapter.Row> rows, int position) {
        for (int i = position - 1; i >= 0; i--) {
            if (rows.get(i).type == TodoAdapter.Row.TYPE_HEADER) {
                return i + 1;
            }
        }
        return 0;
    }

    /** 拖拽落点确定后：把这一行挪过去，落点所在那一组重新编号并保存。 */
    private void moveRow(int from, int to) {
        if (from == to || from < 0 || to < 0
                || from >= currentRows.size() || to >= currentRows.size()) {
            return;
        }
        currentRows.add(to, currentRows.remove(from));

        int start = groupStart(currentRows, to);
        List<Todo> group = new ArrayList<>();
        for (int i = start; i < currentRows.size(); i++) {
            TodoAdapter.Row row = currentRows.get(i);
            if (row.type == TodoAdapter.Row.TYPE_HEADER) {
                break;
            }
            group.add(row.todo);
        }
        store.reorder(group);
        reload();
    }

    // ---------------- 日历 ----------------

    private void bindCalendar() {
        if (calendarZoomed) {
            // 放大：一周一行，格子里有内容
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(MonthGrid.mondayOf(anchorMillis));
            Calendar end = (Calendar) start.clone();
            end.add(Calendar.DAY_OF_MONTH, 6);
            calendarTitle.setText(getString(R.string.calendar_week_title,
                    start.get(Calendar.MONTH) + 1, start.get(Calendar.DAY_OF_MONTH),
                    end.get(Calendar.MONTH) + 1, end.get(Calendar.DAY_OF_MONTH)));
            java.util.Map<Long, List<MonthGrid.Item>> items =
                    calendarItems(start.getTimeInMillis(), 7);
            grid.bindWeek(start.getTimeInMillis(), selectedDayStart, items);
            showCalendarHint(items.isEmpty(), true);
        } else {
            // 缩小：整月 6 行，格子里是颜色圆点
            Calendar month = Calendar.getInstance();
            month.setTimeInMillis(anchorMillis);
            month.set(Calendar.DAY_OF_MONTH, 1);
            calendarTitle.setText(getString(R.string.calendar_month_title,
                    month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1));
            java.util.Map<Long, List<MonthGrid.Item>> items =
                    calendarItems(MonthGrid.mondayOf(month.getTimeInMillis()), 42);
            grid.bindMonth(selectedDayStart, month, items);
            showCalendarHint(items.isEmpty(), false);
        }
    }

    /** 这块时间范围里一条「设了时间」的待办都没有时，提示一下为什么日历是空的。 */
    private void showCalendarHint(boolean empty, boolean week) {
        calendarHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            calendarHint.setText(week ? R.string.calendar_hint_week : R.string.calendar_hint_month);
        }
    }

    /** 这段时间里每天的待办，按时间顺序放进格子里；文字截到两三个字。 */
    private Map<Long, List<MonthGrid.Item>> calendarItems(long firstDayStart, int days) {
        Map<Long, List<MonthGrid.Item>> result = new LinkedHashMap<>();
        for (Todo todo : store.listDatedBetween(firstDayStart, firstDayStart + days * DAY_MILLIS)) {
            long day = TodoTime.startOfDay(todo.due);
            List<MonthGrid.Item> items = result.get(day);
            if (items == null) {
                items = new ArrayList<>();
                result.put(day, items);
            }
            // 传原始颜色（0 = 没设颜色），月视图据此决定整格填淡色还是淡灰
            items.add(new MonthGrid.Item(shorten(todo.title, CELL_CHARS), todo.color,
                    timePosition(todo.due)));
        }
        return result;
    }

    /** 一条待办在一天里的时间位置（0~1），用来在周视图的格子里按时间段摆放。 */
    private static float timePosition(long due) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(due);
        int minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        float fraction = (minutes - DAY_START_MINUTES) / (float) DAY_SPAN_MINUTES;
        return Math.max(0f, Math.min(1f, fraction));
    }

    /** 日历格子里只放得下两三个字。 */
    private static String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        String value = text.trim().replace('\n', ' ').replace('\r', ' ');
        if (value.codePointCount(0, value.length()) <= max) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, max)) + "…";
    }

    // ---------------- 数据操作 ----------------

    private void addTodo() {
        String text = input.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, R.string.empty_input, Toast.LENGTH_SHORT).show();
            return;
        }
        store.add(text, pendingDue, pendingColor, pendingPlace);
        input.setText("");
        if (filter == TodoStore.FILTER_DONE) {
            filter = TodoStore.FILTER_ALL;
            for (int i = 0; i < filters.length; i++) {
                filters[i].setSelected(i == filter);
            }
        }
        reload();
        if (adapter.getCount() > 0) {
            list.setSelection(0);
        }
    }

    @Override
    public void onToggle(Todo todo) {
        store.setDone(todo.id, !todo.done);
        reload();
    }

    @Override
    public void onDelete(final Todo todo) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(getString(R.string.confirm_delete_msg, todo.title))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    store.delete(todo.id);
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onEdit(final Todo todo) {
        TodoEditDialog.show(this, todo, this::reload);
    }

    @Override
    public void onEditColor(final Todo todo) {
        ColorPicker.show(this, todo.color, color -> {
            store.setColor(todo.id, color);
            reload();
        });
    }

    private void pickPendingDue() {
        DuePicker.show(this, pendingDue, due -> {
            pendingDue = due;
            updateTimeChip();
        });
    }

    private void pickPendingColor() {
        ColorPicker.show(this, pendingColor, color -> {
            pendingColor = color;
            updateColorChip();
        });
    }

    /** 一键清掉「下一条」还没用到的时间、地点、颜色。 */
    private void resetPending() {
        pendingDue = TodoTime.NONE;
        pendingPlace = "";
        pendingColor = TodoColors.NO_COLOR;
        updateTimeChip();
        updatePlaceChip();
        updateColorChip();
        Toast.makeText(this, R.string.reset_toast, Toast.LENGTH_SHORT).show();
    }

    private void pickPendingPlace() {
        PlaceInput.show(this, pendingPlace, place -> {
            pendingPlace = place;
            updatePlaceChip();
        });
    }

    /** 地点胶囊：填了地点就显示出来，点它可以改。 */
    private void updatePlaceChip() {
        if (pendingPlace == null || pendingPlace.isEmpty()) {
            placeChip.setText(R.string.place_chip);
            placeChip.setTextColor(getColor(R.color.text_secondary));
        } else {
            placeChip.setText(pendingPlace);
            placeChip.setTextColor(Accent.color(this));
        }
    }

    private void updateTimeChip() {
        if (TodoTime.has(pendingDue)) {
            timeChip.setText(getString(R.string.time_chip_set, TodoTime.formatFull(this, pendingDue)));
            timeChip.setTextColor(Accent.color(this));
        } else {
            timeChip.setText(R.string.time_chip_none);
            timeChip.setTextColor(getColor(R.color.text_secondary));
        }
    }

    /** 颜色胶囊：前面那个圆点就是当前选的颜色，点它可以换。 */
    private void updateColorChip() {
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable dot = TodoColors.dotDrawable(this, pendingColor);
        int size = Math.round(12 * density);
        dot.setSize(size, size);
        colorChip.setCompoundDrawablesWithIntrinsicBounds(dot, null, null, null);
        colorChip.setCompoundDrawablePadding(Math.round(6 * density));
        colorChip.setTextColor(getColor(pendingColor == TodoColors.NO_COLOR
                ? R.color.text_secondary : R.color.accent));
    }

    private void confirmClearDone() {
        final int[] stats = store.stats();
        final int doneCount = stats[0] - stats[1];
        if (doneCount <= 0) {
            Toast.makeText(this, R.string.nothing_done, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_clear_title)
                .setMessage(getString(R.string.confirm_clear_msg, doneCount))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    int removed = store.clearDone();
                    reload();
                    Toast.makeText(this, getString(R.string.cleared_toast, removed), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------- 设置 ----------------

    /** 设置：小组件显示、主题色、深浅模式，右上角问号看说明。 */
    private void showSettings() {
        View content = getLayoutInflater().inflate(R.layout.dialog_settings, null);

        final TextView widgetLiquid = content.findViewById(R.id.opt_widget_liquid);
        final TextView widgetFrosted = content.findViewById(R.id.opt_widget_frosted);
        final TextView nightSystem = content.findViewById(R.id.opt_night_system);
        final TextView nightLight = content.findViewById(R.id.opt_night_light);
        final TextView nightDark = content.findViewById(R.id.opt_night_dark);
        final LinearLayout accentRow = content.findViewById(R.id.accent_row);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setNegativeButton(R.string.theme_close, null)
                .create();

        // 小组件外观（只影响桌面小组件）
        boolean liquid = GlassPrefs.isLiquid(this);
        widgetLiquid.setSelected(liquid);
        widgetFrosted.setSelected(!liquid);
        widgetLiquid.setOnClickListener(v -> {
            if (!GlassPrefs.isLiquid(this)) {
                GlassPrefs.set(this, GlassPrefs.STYLE_LIQUID);
                BaseTodoWidgetProvider.refreshAll(this);
                widgetLiquid.setSelected(true);
                widgetFrosted.setSelected(false);
            }
        });
        widgetFrosted.setOnClickListener(v -> {
            if (GlassPrefs.isLiquid(this)) {
                GlassPrefs.set(this, GlassPrefs.STYLE_FROSTED);
                BaseTodoWidgetProvider.refreshAll(this);
                widgetLiquid.setSelected(false);
                widgetFrosted.setSelected(true);
            }
        });

        // 主题色：几个常用的颜色
        final int current = Accent.get(this);
        final int size = Math.round(34 * getResources().getDisplayMetrics().density);
        for (final int color : Accent.PRESETS) {
            View swatch = getLayoutInflater().inflate(R.layout.item_color_swatch, accentRow, false);
            View dot = swatch.findViewById(R.id.swatch_dot);
            dot.setBackground(TodoColors.dotDrawable(this, color));
            swatch.setSelected(color == current);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginEnd(Math.round(10 * getResources().getDisplayMetrics().density));
            swatch.setLayoutParams(params);
            swatch.setOnClickListener(v -> {
                if (Accent.get(this) != color) {
                    Accent.set(this, color);
                    recreate();   // 主题色是通过主题属性引用进来的，重建界面生效
                }
            });
            accentRow.addView(swatch);
        }

        // 深浅模式
        final int night = ThemePrefs.get(this);
        nightSystem.setSelected(night == ThemePrefs.MODE_SYSTEM);
        nightLight.setSelected(night == ThemePrefs.MODE_LIGHT);
        nightDark.setSelected(night == ThemePrefs.MODE_DARK);
        nightSystem.setOnClickListener(v -> applyNightMode(ThemePrefs.MODE_SYSTEM, night));
        nightLight.setOnClickListener(v -> applyNightMode(ThemePrefs.MODE_LIGHT, night));
        nightDark.setOnClickListener(v -> applyNightMode(ThemePrefs.MODE_DARK, night));

        content.findViewById(R.id.btn_help).setOnClickListener(v -> showHelp());
        dialog.show();
    }

    private void applyNightMode(int mode, int current) {
        if (mode == current) {
            return;
        }
        ThemePrefs.set(this, mode);
        recreate();
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_msg)
                .setPositiveButton(R.string.help_ok, null)
                .show();
    }

    // ---------------- 界面辅助 ----------------

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            }
            focused.clearFocus();
        }
    }
}
