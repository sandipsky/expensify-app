import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/**
 * Launcher icon layers from icon.png (a receipt on a violet gradient inside a rounded square):
 *   mipmap-<density>/ic_launcher_background.png  the violet gradient over the whole 108 dp canvas
 *   mipmap-<density>/ic_launcher_foreground.png  the receipt alone, keyed off the gradient, inside the 66 dp safe zone
 *   mipmap-<density>/ic_launcher_monochrome.png  the receipt's silhouette for themed icons
 *   mipmap-<density>/ic_launcher.png             the artwork itself at 48 dp, for launchers before API 26
 *   mipmap-<density>/ic_launcher_round.png       the composed layers cut round at 48 dp
 * Usage, from the repo root: java tools/IconGen.java icon.png app/src/main/res
 */
public class IconGen {
    static final String[] DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    static final double[] SCALES = {1, 1.5, 2, 3, 4};
    static final int ADAPTIVE_DP = 108;
    static final int LEGACY_DP = 48;
    /** The 512 px source spans this many dp of the 108 dp canvas, so the receipt's corners stay inside a 33 dp circle. */
    static final double ART_DP = 76;

    public static void main(String[] args) throws Exception {
        BufferedImage loaded = ImageIO.read(new File(args[0]));
        Path res = Paths.get(args[1]);
        int n = loaded.getWidth();
        BufferedImage src = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        src.getGraphics().drawImage(loaded, 0, 0, null);

        int[] box = opaqueBox(src);
        System.out.println("opaque box: " + box[0] + "," + box[1] + " - " + box[2] + "," + box[3]);
        double[][] model = fitBackground(src, box);
        BufferedImage keyed = keyOut(src, model);
        BufferedImage legacyArt = cropWithPadding(src, box);

        for (int i = 0; i < DENSITIES.length; i++) {
            double s = SCALES[i];
            int canvas = (int) Math.round(ADAPTIVE_DP * s);
            double artScale = ART_DP * s / n;
            Path dir = res.resolve("mipmap-" + DENSITIES[i]);
            Files.createDirectories(dir);

            BufferedImage background = gradient(model, canvas, n, artScale);
            BufferedImage foreground = place(keyed, canvas, artScale);
            write(background, dir.resolve("ic_launcher_background.png"));
            write(foreground, dir.resolve("ic_launcher_foreground.png"));
            write(monochrome(foreground), dir.resolve("ic_launcher_monochrome.png"));

            int legacy = (int) Math.round(LEGACY_DP * s);
            write(scaled(legacyArt, legacy, legacy), dir.resolve("ic_launcher.png"));
            write(roundCut(compose(background, foreground), legacy), dir.resolve("ic_launcher_round.png"));
            System.out.println(DENSITIES[i] + ": canvas " + canvas + " px, legacy " + legacy + " px");
        }
    }

    /** The bounds of the fully opaque artwork, from the centre row and column: {left, top, right, bottom}, inclusive. */
    static int[] opaqueBox(BufferedImage src) {
        int n = src.getWidth(), mid = n / 2;
        int left = 0, right = n - 1, top = 0, bottom = n - 1;
        while (left < n && (src.getRGB(left, mid) >>> 24) != 255) left++;
        while (right > 0 && (src.getRGB(right, mid) >>> 24) != 255) right--;
        while (top < n && (src.getRGB(mid, top) >>> 24) != 255) top++;
        while (bottom > 0 && (src.getRGB(mid, bottom) >>> 24) != 255) bottom--;
        return new int[] {left, top, right, bottom};
    }

    /** The artwork with a little of its transparent margin kept, for the legacy icon. */
    static BufferedImage cropWithPadding(BufferedImage src, int[] box) {
        int n = src.getWidth();
        int pad = (int) Math.round((box[2] - box[0]) * 0.02);
        int x0 = Math.max(0, box[0] - pad), y0 = Math.max(0, box[1] - pad);
        int x1 = Math.min(n - 1, box[2] + pad), y1 = Math.min(n - 1, box[3] + pad);
        int side = Math.max(x1 - x0, y1 - y0) + 1;
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(src.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1), 0, 0, null);
        return out;
    }

    /** Least-squares fit of c(x, y) = c0 + gx·x + gy·y per channel (x, y in 0..1) on a ring just inside the square's edge. */
    static double[][] fitBackground(BufferedImage src, int[] box) {
        int n = src.getWidth();
        int width = box[2] - box[0];
        int inner = (int) (width * 0.015), outer = (int) (width * 0.095);
        double[][] model = new double[3][];
        for (int ch = 0; ch < 3; ch++) {
            double sxx = 0, sxy = 0, sx = 0, syy = 0, sy = 0, s1 = 0, sxv = 0, syv = 0, sv = 0;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int dl = x - box[0], dr = box[2] - x, dt = y - box[1], db = box[3] - y;
                    if (dl < inner || dr < inner || dt < inner || db < inner) continue;
                    boolean ring = dl < outer || dr < outer || dt < outer || db < outer;
                    if (!ring) continue;
                    int p = src.getRGB(x, y);
                    if ((p >>> 24) != 255) continue;
                    double fx = (double) x / n, fy = (double) y / n, v = channel(p, ch);
                    sxx += fx * fx; sxy += fx * fy; sx += fx; syy += fy * fy; sy += fy; s1 += 1;
                    sxv += fx * v; syv += fy * v; sv += v;
                }
            }
            model[ch] = solve3(new double[][] {{s1, sx, sy}, {sx, sxx, sxy}, {sy, sxy, syy}}, new double[] {sv, sxv, syv});
        }
        return model;
    }

    static double[] solve3(double[][] a, double[] b) {
        for (int i = 0; i < 3; i++) {
            int pivot = i;
            for (int r = i + 1; r < 3; r++) if (Math.abs(a[r][i]) > Math.abs(a[pivot][i])) pivot = r;
            double[] t = a[i]; a[i] = a[pivot]; a[pivot] = t;
            double tb = b[i]; b[i] = b[pivot]; b[pivot] = tb;
            for (int r = i + 1; r < 3; r++) {
                double f = a[r][i] / a[i][i];
                for (int c = i; c < 3; c++) a[r][c] -= f * a[i][c];
                b[r] -= f * b[i];
            }
        }
        double[] x = new double[3];
        for (int i = 2; i >= 0; i--) {
            double s = b[i];
            for (int c = i + 1; c < 3; c++) s -= a[i][c] * x[c];
            x[i] = s / a[i][i];
        }
        return x;
    }

    static double modelAt(double[][] model, int ch, double fx, double fy) {
        double[] m = model[ch];
        return clamp(m[0] + m[1] * fx + m[2] * fy);
    }

    /** Alpha from each pixel's distance to the modelled background, then the foreground colour un-premultiplied. */
    static BufferedImage keyOut(BufferedImage src, double[][] model) {
        int n = src.getWidth();
        double d0 = 10, d1 = 100;
        BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = src.getRGB(x, y);
                if ((p >>> 24) != 255) continue;
                double fx = (double) x / n, fy = (double) y / n;
                double[] bg = {modelAt(model, 0, fx, fy), modelAt(model, 1, fx, fy), modelAt(model, 2, fx, fy)};
                double dist = 0;
                for (int ch = 0; ch < 3; ch++) dist += Math.pow(channel(p, ch) - bg[ch], 2);
                dist = Math.sqrt(dist);
                double alpha = Math.max(0, Math.min(1, (dist - d0) / (d1 - d0)));
                if (alpha <= 0) continue;
                int[] rgb = new int[3];
                for (int ch = 0; ch < 3; ch++) rgb[ch] = (int) Math.round(clamp((channel(p, ch) - (1 - alpha) * bg[ch]) / alpha));
                out.setRGB(x, y, ((int) Math.round(alpha * 255) << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]);
            }
        }
        return out;
    }

    /** The gradient over the whole canvas, extrapolated from where the artwork sits on it. */
    static BufferedImage gradient(double[][] model, int canvas, int n, double artScale) {
        BufferedImage out = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_RGB);
        double offset = (canvas - n * artScale) / 2;
        for (int y = 0; y < canvas; y++) {
            for (int x = 0; x < canvas; x++) {
                double fx = (x - offset) / artScale / n, fy = (y - offset) / artScale / n;
                int r = (int) Math.round(modelAt(model, 0, fx, fy));
                int g = (int) Math.round(modelAt(model, 1, fx, fy));
                int b = (int) Math.round(modelAt(model, 2, fx, fy));
                out.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    /** The keyed artwork scaled onto a transparent canvas, centred. */
    static BufferedImage place(BufferedImage art, int canvas, double artScale) {
        int size = (int) Math.round(art.getWidth() * artScale);
        BufferedImage small = scaled(art, size, size);
        BufferedImage out = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(small, (canvas - size) / 2, (canvas - size) / 2, null);
        g.dispose();
        return out;
    }

    /** White where the foreground is solid, with a soft edge; the shadow and fringes drop out. */
    static BufferedImage monochrome(BufferedImage fg) {
        int n = fg.getWidth();
        BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double a = (fg.getRGB(x, y) >>> 24) / 255.0;
                double m = Math.max(0, Math.min(1, (a - 0.5) / 0.3));
                if (m > 0) out.setRGB(x, y, ((int) Math.round(m * 255) << 24) | 0xFFFFFF);
            }
        }
        return out;
    }

    static BufferedImage compose(BufferedImage background, BufferedImage foreground) {
        BufferedImage out = new BufferedImage(background.getWidth(), background.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(background, 0, 0, null);
        g.drawImage(foreground, 0, 0, null);
        g.dispose();
        return out;
    }

    /** The centre 72 dp of the 108 dp canvas, as a launcher would mask it, cut round at the legacy size. */
    static BufferedImage roundCut(BufferedImage composed, int size) {
        int canvas = composed.getWidth();
        int inset = canvas / 6;
        BufferedImage centre = composed.getSubimage(inset, inset, canvas - 2 * inset, canvas - 2 * inset);
        BufferedImage small = scaled(centre, size, size);
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(java.awt.Color.WHITE);
        g.fill(new Ellipse2D.Double(0, 0, size, size));
        g.setComposite(AlphaComposite.SrcIn);
        g.drawImage(small, 0, 0, null);
        g.dispose();
        return out;
    }

    /** High-quality downscale: halve until within 2× of the target, then one bicubic pass. */
    static BufferedImage scaled(BufferedImage img, int w, int h) {
        BufferedImage current = img;
        while (current.getWidth() > 2 * w && current.getHeight() > 2 * h) {
            current = resample(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return resample(current, w, h);
    }

    static BufferedImage resample(BufferedImage img, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    static double channel(int argb, int ch) {
        return (argb >> (16 - 8 * ch)) & 0xFF;
    }

    static double clamp(double v) {
        return Math.max(0, Math.min(255, v));
    }

    static void write(BufferedImage img, Path file) throws Exception {
        ImageIO.write(img, "png", file.toFile());
    }
}
