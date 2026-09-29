#!/usr/bin/env bash
# 检查小组件（RemoteViews）布局里有没有用到系统不支持的控件。
# RemoteViews 只支持这几个类，用到别的就会在桌面显示「无法添加小组件」的黑块：
#   FrameLayout / LinearLayout / RelativeLayout / GridLayout
#   AnalogClock / Button / Chronometer / ImageButton / ImageView / ProgressBar /
#   TextView / ViewFlipper / ListView / GridView / StackView / AdapterViewFlipper
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LAYOUT_DIR="$ROOT/app/src/main/res/layout"
ALLOWED="FrameLayout LinearLayout RelativeLayout GridLayout AnalogClock Button Chronometer ImageButton ImageView ProgressBar TextView ViewFlipper ListView GridView StackView AdapterViewFlipper ViewStub"

# 这些布局会作为小组件根布局或列表行布局被桌面进程 inflate
FILES=$(ls "$LAYOUT_DIR"/widget_todo_*.xml 2>/dev/null)
[ -z "$FILES" ] && { echo "没找到小组件布局"; exit 1; }

fail=0
for f in $FILES; do
  # 取出所有元素名（排除 </ 闭合标签和注释）
  for el in $(grep -oE '<[A-Za-z][A-Za-z0-9_.]*' "$f" | sed 's/^<//' | sort -u); do
    case " $ALLOWED " in
      *" $el "*) ;;
      *) echo "❌ $(basename "$f"): 不支持的元素 <$el>"; fail=1 ;;
    esac
  done
done

if [ "$fail" -eq 0 ]; then
  echo "✅ 所有小组件布局只用了 RemoteViews 支持的控件（$(echo "$FILES" | wc -l | tr -d ' ') 个布局）"
else
  echo "上面这些元素会让小组件在桌面变成「无法添加小组件」的黑块，请替换成白名单里的控件。"
fi
exit $fail
