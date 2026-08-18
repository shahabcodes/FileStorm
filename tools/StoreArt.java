// Renders the two Play Console graphics from the app's own vector icon, so the
// store artwork and the launcher icon are literally the same drawing rather
// than a redrawn lookalike.
//
//   ic_launcher-playstore.png  512 x 512   the Glass icon
//   feature-graphic.png       1024 x 500   the store banner
//
// Java2D is the rasteriser; there is no imaging library on this machine. Paths
// are parsed straight out of the VectorDrawable pathData strings.

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StoreArt {

    // ── The artwork, copied verbatim from the drawable resources ──────────
    static final String FOLDER =
        "M24,34 C24,30.7 26.7,28 30,28 L44,28 C45.7,28 47.3,28.8 48.4,30.1 " +
        "L52,34.5 C53.1,35.8 54.7,36.5 56.4,36.5 L78,36.5 C81.3,36.5 84,39.2 84,42.5 " +
        "L84,72 C84,75.3 81.3,78 78,78 L30,78 C26.7,78 24,75.3 24,72 Z";
    static final String BOLT = "M63,40 L41,68 L54,68 L48,90 L71,60 L58,60 Z";
    static final String BUBBLE_A = "M26,30 m-16,0 a16,16 0 1,1 32,0 a16,16 0 1,1 -32,0";
    static final String BUBBLE_B = "M88,86 m-22,0 a22,22 0 1,1 44,0 a22,22 0 1,1 -44,0";

    static final Color INDIGO = new Color(0x5856D6);
    static final Color ROSE = new Color(0xFF375F);

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : ".");
        out.mkdirs();
        writeIcon(new File(out, "ic_launcher-playstore.png"), 72);
        writeIcon(new File(out, "icon-fullcanvas.png"), 108);
        writeFeature(new File(out, "feature-graphic.png"));
        writeThumbnail(new File(out, "review-video-thumbnail.png"),
            "All files access", "Foreground service demo");
        writeThumbnail(new File(out, "review-video-thumbnail-media.png"),
            "Media playback", "Foreground service demo");
        System.out.println("wrote " + out.getAbsolutePath());
    }

    // ── 512 x 512 store icon ─────────────────────────────────────────────
    //
    // An adaptive icon is drawn on a 108dp canvas of which the launcher only
    // ever shows the central 72dp. Cropping to that same 72dp is what makes
    // the store icon match what people see on their home screen.
    static void writeIcon(File dest, double view) throws Exception {
        int size = 512, ss = 4;              // supersample, then downscale
        BufferedImage big = new BufferedImage(size * ss, size * ss, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = quality(big.createGraphics());

        double scale = (size * ss) / view;
        g.scale(scale, scale);
        double inset = (108 - view) / 2;
        g.translate(-inset, -inset);

        paintGlass(g, view);

        g.dispose();
        ImageIO.write(downscale(big, size, size), "png", dest);
        System.out.println("  icon    " + size + "x" + size);
    }

    /** The Glass icon itself, on the 108-unit viewport. */
    static void paintGlass(Graphics2D g, double view) {
        double a = (108 - view) / 2, b = a + view;
        g.setPaint(new GradientPaint((float) a, (float) a, INDIGO, (float) b, (float) b, ROSE));
        g.fill(new Rectangle2D.Double(0, 0, 108, 108));

        g.setColor(new Color(255, 255, 255, 0x2E));
        g.fill(path(BUBBLE_A));
        g.setColor(new Color(255, 255, 255, 0x1F));
        g.fill(path(BUBBLE_B));

        // foreground: outer group scales 0.62 about (54,56)
        AffineTransform saved = g.getTransform();
        g.transform(group(54, 56, 0.62, 0));

        // the folder sits in a further group: 1.42 about (54,54), shifted down 2
        AffineTransform savedInner = g.getTransform();
        g.transform(group(54, 54, 1.42, 2));
        Shape folder = path(FOLDER);
        g.setColor(new Color(255, 255, 255, 0x42));
        g.fill(folder);
        g.setColor(new Color(255, 255, 255, 0xBF));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(folder);
        g.setTransform(savedInner);

        g.setColor(Color.WHITE);
        g.fill(path(BOLT));
        g.setTransform(saved);
    }

    /** VectorDrawable group transform: scale about a pivot, then translate. */
    static AffineTransform group(double px, double py, double s, double ty) {
        AffineTransform t = new AffineTransform();
        t.translate(px, py + ty);
        t.scale(s, s);
        t.translate(-px, -py);
        return t;
    }

    // ── 1024 x 500 feature graphic ───────────────────────────────────────
    static void writeFeature(File dest) throws Exception {
        int w = 1024, h = 500, ss = 3;
        BufferedImage big = new BufferedImage(w * ss, h * ss, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = quality(big.createGraphics());
        g.scale(ss, ss);

        // Background: the icon's gradient, stretched to the banner.
        g.setPaint(new GradientPaint(0, 0, INDIGO, w, h, ROSE));
        g.fillRect(0, 0, w, h);

        // Frosted bubbles, echoing the icon's own.
        g.setColor(new Color(255, 255, 255, 0x1A));
        g.fill(new Ellipse2D.Double(-120, -160, 460, 460));
        g.setColor(new Color(255, 255, 255, 0x14));
        g.fill(new Ellipse2D.Double(760, 300, 420, 420));

        // The icon tile, drawn from the same vector as the launcher icon.
        int tile = 236, tx = 86, ty = (h - tile) / 2;
        AffineTransform saved = g.getTransform();
        Shape clip = new RoundRectangle2D.Double(tx, ty, tile, tile, 58, 58);

        // The tile is the same gradient as the background it sits on, so
        // without a shadow the two merge. Stacked translucent outlines band
        // visibly at this size, so the shadow is genuinely blurred.
        BufferedImage shadow = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = quality(shadow.createGraphics());
        sg.setColor(new Color(0, 0, 0, 130));
        sg.fill(new RoundRectangle2D.Double(tx, ty + 10, tile, tile, 58, 58));
        sg.dispose();
        blur(shadow, 14);
        g.drawImage(shadow, 0, 0, w, h, null);
        g.setClip(clip);
        g.translate(tx, ty);
        double s = tile / 72.0;
        g.scale(s, s);
        g.translate(-18, -18);
        paintGlass(g, 72);
        g.setTransform(saved);
        g.setClip(null);
        g.setColor(new Color(255, 255, 255, 0x40));
        g.setStroke(new BasicStroke(2f));
        g.draw(clip);

        // Text block, sized to the space actually left over.
        int textX = tx + tile + 62;
        int avail = w - textX - 56;

        Font title = pick(Font.BOLD, 88);
        Font sub = pick(Font.PLAIN, 31);
        String titleText = "File Storm";
        String subText = "Organise, find and encrypt — entirely offline.";

        title = fit(g, title, titleText, avail);
        sub = fit(g, sub, subText, avail);

        FontMetrics tm = g.getFontMetrics(title);
        FontMetrics sm = g.getFontMetrics(sub);
        int block = tm.getAscent() + 22 + sm.getAscent();
        int baseline = (h - block) / 2 + tm.getAscent();

        g.setColor(new Color(0, 0, 0, 0x33));           // a little lift off the gradient
        g.setFont(title);
        g.drawString(titleText, textX + 2, baseline + 3);
        g.setColor(Color.WHITE);
        g.drawString(titleText, textX, baseline);

        g.setFont(sub);
        g.setColor(new Color(255, 255, 255, 0xE0));
        g.drawString(subText, textX, baseline + 22 + sm.getAscent());

        g.dispose();
        ImageIO.write(downscale(big, w, h), "png", dest);
        System.out.println("  feature " + w + "x" + h);
    }

    /**
     * 1280 x 720 thumbnail for the unlisted demo video.
     *
     * Its audience is one Play reviewer, not a browsing viewer, so it says what
     * the video proves and which package it belongs to rather than trying to
     * earn a click.
     */
    static void writeThumbnail(File dest, String lineOne, String lineTwo) throws Exception {
        int w = 1280, h = 720, ss = 2;
        BufferedImage big = new BufferedImage(w * ss, h * ss, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = quality(big.createGraphics());
        g.scale(ss, ss);

        g.setPaint(new GradientPaint(0, 0, INDIGO, w, h, ROSE));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 255, 255, 0x18));
        g.fill(new Ellipse2D.Double(-160, -220, 620, 620));
        g.setColor(new Color(255, 255, 255, 0x12));
        g.fill(new Ellipse2D.Double(950, 420, 540, 540));

        int tile = 300, tx = 96, ty = (h - tile) / 2;
        BufferedImage shadow = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = quality(shadow.createGraphics());
        sg.setColor(new Color(0, 0, 0, 140));
        sg.fill(new RoundRectangle2D.Double(tx, ty + 12, tile, tile, 72, 72));
        sg.dispose();
        blur(shadow, 16);
        g.drawImage(shadow, 0, 0, w, h, null);

        AffineTransform saved = g.getTransform();
        Shape clip = new RoundRectangle2D.Double(tx, ty, tile, tile, 72, 72);
        g.setClip(clip);
        g.translate(tx, ty);
        double s = tile / 72.0;
        g.scale(s, s);
        g.translate(-18, -18);
        paintGlass(g, 72);
        g.setTransform(saved);
        g.setClip(null);
        g.setColor(new Color(255, 255, 255, 0x40));
        g.setStroke(new BasicStroke(2f));
        g.draw(clip);

        int textX = tx + tile + 70;
        int avail = w - textX - 70;

        Font title = fit(g, pick(Font.BOLD, 78), "File Storm", avail);
        Font line = fit(g, pick(Font.PLAIN, 37),
            lineOne.length() > lineTwo.length() ? lineOne : lineTwo, avail);
        Font small = fit(g, pick(Font.PLAIN, 25), "com.shahaabapps.filestorm", avail);

        FontMetrics tm = g.getFontMetrics(title);
        FontMetrics lm = g.getFontMetrics(line);
        FontMetrics sm = g.getFontMetrics(small);

        int block = tm.getAscent() + 26 + lm.getAscent() + 10 + lm.getAscent() + 30 + sm.getAscent();
        int y = (h - block) / 2 + tm.getAscent();

        g.setFont(title);
        g.setColor(new Color(0, 0, 0, 0x38));
        g.drawString("File Storm", textX + 2, y + 3);
        g.setColor(Color.WHITE);
        g.drawString("File Storm", textX, y);

        y += 26 + lm.getAscent();
        g.setFont(line);
        g.setColor(new Color(255, 255, 255, 0xF2));
        g.drawString(lineOne, textX, y);
        y += 10 + lm.getAscent();
        g.drawString(lineTwo, textX, y);

        y += 30 + sm.getAscent();
        g.setFont(small);
        g.setColor(new Color(255, 255, 255, 0xB0));
        g.drawString("com.shahaabapps.filestorm", textX, y);

        g.dispose();
        ImageIO.write(downscale(big, w, h), "png", dest);
        System.out.println("  thumb   " + w + "x" + h);
    }

    /** First font on the machine that actually exists, so this is not Dialog. */
    static Font pick(int style, int size) {
        String[] wanted = {"Segoe UI", "Inter", "Helvetica Neue", "Arial", "SansSerif"};
        List<String> have = List.of(GraphicsEnvironment
            .getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String name : wanted) {
            if (have.contains(name)) return new Font(name, style, size);
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    /** Shrink until it fits; a clipped feature graphic is a rejected one. */
    static Font fit(Graphics2D g, Font f, String text, int width) {
        while (f.getSize() > 12 && g.getFontMetrics(f).stringWidth(text) > width) {
            f = f.deriveFont((float) f.getSize() - 1);
        }
        return f;
    }

    static Graphics2D quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
            RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }

    /** Three box passes, which is close enough to a Gaussian for a shadow. */
    static void blur(BufferedImage img, int radius) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        for (int pass = 0; pass < 3; pass++) {
            px = boxPass(px, w, h, radius);      // horizontal
            px = boxPass(px, h, w, radius);      // transposed back = vertical
        }
        img.setRGB(0, 0, w, h, px, 0, w);
    }

    /** One horizontal box blur, returning the transposed result. */
    static int[] boxPass(int[] src, int w, int h, int r) {
        int[] dst = new int[src.length];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            long a = 0, rr = 0, gg = 0, bb = 0;
            int count = 0;
            for (int x = -r; x <= r; x++) {
                int xi = Math.min(w - 1, Math.max(0, x));
                int p = src[row + xi];
                a += (p >>> 24); rr += (p >> 16) & 0xFF; gg += (p >> 8) & 0xFF; bb += p & 0xFF;
                count++;
            }
            for (int x = 0; x < w; x++) {
                dst[x * h + y] = ((int) (a / count) << 24) | ((int) (rr / count) << 16)
                    | ((int) (gg / count) << 8) | (int) (bb / count);
                int add = src[row + Math.min(w - 1, x + r + 1)];
                int sub = src[row + Math.max(0, x - r)];
                a += (add >>> 24) - (sub >>> 24);
                rr += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                gg += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                bb += (add & 0xFF) - (sub & 0xFF);
            }
        }
        return dst;
    }

    static BufferedImage downscale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, src.getType());
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    // ── Minimal SVG path parser: M m L l H h V v C c A a Z ────────────────
    static Path2D.Double path(String d) {
        Path2D.Double p = new Path2D.Double();
        List<String> tok = tokenize(d);
        double x = 0, y = 0, sx = 0, sy = 0;
        char cmd = 0;
        int i = 0;
        while (i < tok.size()) {
            String t = tok.get(i);
            if (t.length() == 1 && Character.isLetter(t.charAt(0))) {
                cmd = t.charAt(0);
                i++;
                if (cmd == 'Z' || cmd == 'z') {
                    p.closePath();
                    x = sx; y = sy;
                    continue;
                }
            }
            boolean rel = Character.isLowerCase(cmd);
            switch (Character.toUpperCase(cmd)) {
                case 'M': {
                    double nx = num(tok, i++), ny = num(tok, i++);
                    x = rel ? x + nx : nx; y = rel ? y + ny : ny;
                    p.moveTo(x, y); sx = x; sy = y;
                    cmd = rel ? 'l' : 'L';   // extra pairs after M are lineto
                    break;
                }
                case 'L': {
                    double nx = num(tok, i++), ny = num(tok, i++);
                    x = rel ? x + nx : nx; y = rel ? y + ny : ny;
                    p.lineTo(x, y);
                    break;
                }
                case 'H': {
                    double nx = num(tok, i++);
                    x = rel ? x + nx : nx;
                    p.lineTo(x, y);
                    break;
                }
                case 'V': {
                    double ny = num(tok, i++);
                    y = rel ? y + ny : ny;
                    p.lineTo(x, y);
                    break;
                }
                case 'C': {
                    double a = num(tok, i++), b = num(tok, i++);
                    double c = num(tok, i++), e = num(tok, i++);
                    double f = num(tok, i++), gg = num(tok, i++);
                    if (rel) { a += x; b += y; c += x; e += y; f += x; gg += y; }
                    p.curveTo(a, b, c, e, f, gg);
                    x = f; y = gg;
                    break;
                }
                case 'A': {
                    double rx = num(tok, i++), ry = num(tok, i++), rot = num(tok, i++);
                    boolean large = num(tok, i++) != 0, sweep = num(tok, i++) != 0;
                    double ex = num(tok, i++), ey = num(tok, i++);
                    if (rel) { ex += x; ey += y; }
                    arc(p, x, y, rx, ry, rot, large, sweep, ex, ey);
                    x = ex; y = ey;
                    break;
                }
                default:
                    throw new IllegalArgumentException("unsupported command " + cmd);
            }
        }
        return p;
    }

    static double num(List<String> tok, int i) { return Double.parseDouble(tok.get(i)); }

    static List<String> tokenize(String d) {
        List<String> out = new ArrayList<>();
        int i = 0, n = d.length();
        while (i < n) {
            char c = d.charAt(i);
            if (Character.isWhitespace(c) || c == ',') { i++; continue; }
            if (Character.isLetter(c)) { out.add(String.valueOf(c)); i++; continue; }
            int j = i;
            if (d.charAt(j) == '-' || d.charAt(j) == '+') j++;
            while (j < n && (Character.isDigit(d.charAt(j)) || d.charAt(j) == '.')) j++;
            if (j < n && (d.charAt(j) == 'e' || d.charAt(j) == 'E')) {
                j++;
                if (j < n && (d.charAt(j) == '-' || d.charAt(j) == '+')) j++;
                while (j < n && Character.isDigit(d.charAt(j))) j++;
            }
            out.add(d.substring(i, j));
            i = j;
        }
        return out;
    }

    /** Endpoint-parameterised elliptical arc, appended as cubic segments. */
    static void arc(Path2D.Double p, double x0, double y0, double rx, double ry,
                    double rotDeg, boolean large, boolean sweep, double x1, double y1) {
        if (rx == 0 || ry == 0) { p.lineTo(x1, y1); return; }
        double phi = Math.toRadians(rotDeg);
        double cos = Math.cos(phi), sin = Math.sin(phi);
        double dx2 = (x0 - x1) / 2, dy2 = (y0 - y1) / 2;
        double x1p = cos * dx2 + sin * dy2, y1p = -sin * dx2 + cos * dy2;
        rx = Math.abs(rx); ry = Math.abs(ry);

        double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
        if (lambda > 1) { double s = Math.sqrt(lambda); rx *= s; ry *= s; }

        double sign = (large != sweep) ? 1 : -1;
        double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
        double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
        double co = sign * Math.sqrt(Math.max(0, num / den));
        double cxp = co * rx * y1p / ry, cyp = -co * ry * x1p / rx;
        double cx = cos * cxp - sin * cyp + (x0 + x1) / 2;
        double cy = sin * cxp + cos * cyp + (y0 + y1) / 2;

        double t1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
        double dt = angle((x1p - cxp) / rx, (y1p - cyp) / ry,
                          (-x1p - cxp) / rx, (-y1p - cyp) / ry);
        if (!sweep && dt > 0) dt -= 2 * Math.PI;
        else if (sweep && dt < 0) dt += 2 * Math.PI;

        int segs = (int) Math.ceil(Math.abs(dt) / (Math.PI / 2));
        double step = dt / segs;
        double k = 4.0 / 3 * Math.tan(step / 4);
        for (int s = 0; s < segs; s++) {
            double a = t1 + s * step, b = a + step;
            double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
            double p1x = cx + rx * cos * ca - ry * sin * sa;
            double p1y = cy + rx * sin * ca + ry * cos * sa;
            double p2x = cx + rx * cos * cb - ry * sin * sb;
            double p2y = cy + rx * sin * cb + ry * cos * sb;
            double d1x = -rx * cos * sa - ry * sin * ca, d1y = -rx * sin * sa + ry * cos * ca;
            double d2x = -rx * cos * sb - ry * sin * cb, d2y = -rx * sin * sb + ry * cos * cb;
            p.curveTo(p1x + k * d1x, p1y + k * d1y, p2x - k * d2x, p2y - k * d2y, p2x, p2y);
        }
    }

    static double angle(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double len = Math.hypot(ux, uy) * Math.hypot(vx, vy);
        double a = Math.acos(Math.max(-1, Math.min(1, dot / len)));
        return (ux * vy - uy * vx < 0) ? -a : a;
    }
}
