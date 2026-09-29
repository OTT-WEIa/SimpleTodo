import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 生成应用图标（各密度 PNG）和三种尺寸的小组件预览图。
 * 预览图画成“玻璃卡片浮在壁纸上”的样子，方便在桌面的小组件列表里看出透明效果。
 * 用法: java GenIcons.java <res 目录>
 */
public class GenIcons {

    private static final Color ACCENT = new Color(0x3D, 0x7E, 0xFF);
    private static final Color ACCENT_DARK = new Color(0x2C, 0x63, 0xE8);
    private static final Color TEXT_PRIMARY = new Color(0x1F, 0x1F, 0x24);
    private static final Color TEXT_SECONDARY = new Color(0x55, 0x55, 0x63);
    private static final Color TEXT_DONE = new Color(0x8A, 0x8A, 0x96);
    private static final Color SEPARATOR = new Color(255, 255, 255, 70);
    /** 未完成待办前面的分类圆点颜色（预览图用）。 */
    private static final Color[] CATEGORY_COLORS = {
            new Color(0x3D, 0x7E, 0xFF), new Color(0x22, 0xA0, 0x6B),
            new Color(0x8B, 0x5C, 0xF6), new Color(0xF0, 0xA8, 0x30),
    };

    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        int[] sizes = {48, 72, 96, 144, 192};
        String[] buckets = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};

        for (int i = 0; i < sizes.length; i++) {
            File dir = new File(res, "mipmap-" + buckets[i]);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("无法创建目录: " + dir);
            }
            ImageIO.write(drawIcon(sizes[i], false), "png", new File(dir, "ic_launcher.png"));
            ImageIO.write(drawIcon(sizes[i], true), "png", new File(dir, "ic_launcher_round.png"));
            System.out.println("生成 " + dir.getName() + "/ic_launcher.png (" + sizes[i] + "px)");
        }

        File nodpi = new File(res, "drawable-nodpi");
        if (!nodpi.exists() && !nodpi.mkdirs()) {
            throw new IllegalStateException("无法创建目录: " + nodpi);
        }
        // 三种尺寸各一张预览图（宽高比分别接近 2×2 / 3×2 / 4×2）
        writePreview(nodpi, "widget_preview_small.png", 320, 300, 2, false, 24, 24, 24);
        writePreview(nodpi, "widget_preview_medium.png", 470, 300, 3, true, 28, 28, 27);
        writePreview(nodpi, "widget_preview_large.png", 620, 300, 3, true, 32, 32, 30);
    }

    private static void writePreview(File dir, String name, int w, int h, int rows,
                                     boolean chips, int titleSize, int rowTextSize, int checkSize)
            throws Exception {
        ImageIO.write(drawPreview(w, h, rows, chips, titleSize, rowTextSize, checkSize), "png",
                new File(dir, name));
        System.out.println("生成 drawable-nodpi/" + name + " (" + w + "x" + h + ")");
    }

    /** 圆角方形（或圆形）底 + 白色对勾。 */
    private static BufferedImage drawIcon(int size, boolean round) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = quality(img);

        Shape shape = round
                ? new Ellipse2D.Float(0, 0, size, size)
                : new RoundRectangle2D.Float(0, 0, size, size, size * 0.22f, size * 0.22f);
        g.setPaint(new GradientPaint(0, 0, ACCENT, size, size, ACCENT_DARK));
        g.fill(shape);

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(size * 0.09f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D check = new Path2D.Float();
        check.moveTo(size * 0.27, size * 0.52);
        check.lineTo(size * 0.43, size * 0.68);
        check.lineTo(size * 0.74, size * 0.34);
        g.draw(check);

        g.dispose();
        return img;
    }

    /** 预览图：壁纸 + 半透明玻璃卡片 + 待办行（方框 / 标题 / 时间）+ 右上角圆圈加号。 */
    private static BufferedImage drawPreview(int w, int h, int rows, boolean chips,
                                            int titleSize, int rowTextSize, int checkSize) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = quality(img);

        int inset = Math.round(Math.min(w, h) * 0.035f);
        float radius = Math.min(w, h) * 0.14f;

        // 1) 背后的“壁纸”
        g.setPaint(new GradientPaint(0, 0, new Color(0xA8, 0xC4, 0xF0), w, h, new Color(0xD3, 0xB8, 0xEC)));
        g.fill(new RoundRectangle2D.Float(inset * 0.4f, inset * 0.4f,
                w - inset * 0.8f, h - inset * 0.8f, radius, radius));

        // 2) 玻璃卡片：半透明填充 + 白色描边 + 顶部渐亮
        Shape card = new RoundRectangle2D.Float(inset * 0.9f, inset * 0.9f,
                w - inset * 1.8f, h - inset * 1.8f, radius, radius);
        g.setColor(new Color(255, 255, 255, 120));
        g.fill(card);
        Shape highlight = new RoundRectangle2D.Float(inset * 0.9f, inset * 0.9f,
                w - inset * 1.8f, (h - inset * 1.8f) * 0.5f, radius, radius);
        g.setPaint(new GradientPaint(0, inset, new Color(255, 255, 255, 110),
                0, inset + (h - inset * 1.8f) * 0.5f, new Color(255, 255, 255, 0)));
        g.fill(highlight);
        g.setColor(new Color(255, 255, 255, 150));
        g.setStroke(new BasicStroke(2f));
        g.draw(card);

        int left = Math.round(inset * 0.9f) + Math.round(h * 0.055f);
        int right = w - left;
        int top = Math.round(inset * 0.9f) + Math.round(h * 0.05f);

        // 3) 标题
        drawText(g, "待办 2/5", left, top + titleSize, titleSize, Font.BOLD, TEXT_PRIMARY);

        // 4) 右上角：胶囊按钮（可选）+ 圆圈加号
        int plus = Math.round(checkSize * 1.35f);
        int plusRight = right - 2;
        drawPlus(g, plusRight - plus / 2f, top + titleSize * 0.62f, plus / 2f);
        int chipRight = plusRight - plus - 8;
        if (chips) {
            int chipH = Math.round(titleSize * 1.4f);
            int chipText = Math.round(titleSize * 0.72f);
            int doneW = chipWidth(g, "显示已办", chipText, chipH);
            chip(g, chipRight - doneW, Math.round(top + titleSize * 0.62f - chipH / 2f), doneW, chipH, "显示已办", chipText);
        }

        // 5) 待办行：第一行已完成（灰 + 打勾），最后一行没有时间
        int rowTop = top + Math.round(titleSize * 2.35f);
        int rowH = Math.max(checkSize + 12, (h - inset * 2 - rowTop) / rows);
        String[] titles = {"买菜", "写周报", "给妈妈打电话", "取快递"};
        String[] times = {"18:00", "9/25", "", ""};
        String[] places = {"超市", "公司", "", "家附"};
        boolean[] done = {true, false, false, false};
        for (int i = 0; i < rows; i++) {
            int cy = rowTop + i * rowH + rowH / 2;
            int r = Math.round(checkSize * 0.5f);
            int cx = left + r;
            if (done[i]) {
                g.setColor(ACCENT);
                g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f));
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(Math.max(2f, checkSize * 0.15f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D check = new Path2D.Float();
                check.moveTo(cx - r * 0.45f, cy + r * 0.02f);
                check.lineTo(cx - r * 0.08f, cy + r * 0.4f);
                check.lineTo(cx + r * 0.48f, cy - r * 0.42f);
                g.draw(check);
            } else {
                // 未完成：分类颜色的实心圆点（和新的分类标注一致）
                g.setColor(CATEGORY_COLORS[i % CATEGORY_COLORS.length]);
                g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f));
            }
            drawText(g, titles[i % titles.length], cx + r + 9, cy - rowTextSize * 0.42f,
                    rowTextSize, Font.PLAIN, done[i] ? TEXT_DONE : TEXT_PRIMARY);
            String time = times[i % times.length];
            String place = places[i % places.length];
            if (!time.isEmpty()) {
                int timeSize = Math.max(10, Math.round(rowTextSize * 0.82f));
                int width = textWidth(g, time, timeSize);
                drawText(g, time, right - width, cy - timeSize * 0.42f, timeSize, Font.PLAIN,
                        done[i] ? TEXT_DONE : TEXT_SECONDARY);
            }
            if (!place.isEmpty()) {
                int placeSize = Math.max(10, Math.round(rowTextSize * 0.82f));
                int width = textWidth(g, place, placeSize);
                drawText(g, place, right - width, cy + placeSize * 0.95f, placeSize, Font.PLAIN,
                        done[i] ? TEXT_DONE : TEXT_SECONDARY);
            }
            if (i < rows - 1) {
                g.setColor(SEPARATOR);
                g.setStroke(new BasicStroke(1.5f));
                g.draw(new Line2D.Float(left, cy + rowH / 2f, right, cy + rowH / 2f));
            }
        }

        g.dispose();
        return img;
    }

    /** 画一圈强调色 + 白色加号（小组件右上角的快速添加入口）。 */
    private static void drawPlus(Graphics2D g, float cx, float cy, float radius) {
        g.setColor(ACCENT);
        g.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f));
        g.setColor(Color.WHITE);
        float arm = radius * 0.5f;
        float thick = Math.max(1.8f, radius * 0.24f);
        g.setStroke(new BasicStroke(thick, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(new Line2D.Float(cx - arm, cy, cx + arm, cy));
        g.draw(new Line2D.Float(cx, cy - arm, cx, cy + arm));
    }

    private static int chipWidth(Graphics2D g, String text, int textSize, int chipH) {
        return textWidth(g, text, textSize) + chipH;
    }

    private static int textWidth(Graphics2D g, String text, int textSize) {
        g.setFont(pickCjkFont(textSize).deriveFont((float) textSize));
        return g.getFontMetrics().stringWidth(text);
    }

    private static void chip(Graphics2D g, int x, int y, int w, int h, String text, int textSize) {
        g.setColor(new Color(255, 255, 255, 170));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, h, h));
        Font f = pickCjkFont(textSize).deriveFont((float) textSize);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(ACCENT);
        g.drawString(text, x + (w - fm.stringWidth(text)) / 2f,
                y + (h - fm.getHeight()) / 2f + fm.getAscent());
    }

    /** 带一点阴影的白字底衬，保证玻璃上的文字看得清。 */
    private static void drawText(Graphics2D g, String text, float x, float baseline,
                                 int textSize, int style, Color color) {
        Font f = pickCjkFont(textSize).deriveFont(style, (float) textSize);
        g.setFont(f);
        g.setColor(new Color(255, 255, 255, 120));
        g.drawString(text, x, baseline + 1.5f);
        g.setColor(color);
        g.drawString(text, x, baseline);
    }

    /** 找一个能显示中文的字体，找不到就退回默认字体。 */
    private static Font pickCjkFont(float size) {
        String[] candidates = {"Microsoft YaHei", "微软雅黑", "SimHei", "Noto Sans CJK SC", "SansSerif"};
        for (String name : candidates) {
            Font f = new Font(name, Font.PLAIN, (int) size);
            if (f.canDisplay('待') && f.canDisplay('办')) {
                return f;
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, (int) size);
    }

    private static Graphics2D quality(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }
}
