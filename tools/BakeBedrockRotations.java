import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Makes hand-edited block models valid for the Java model format while keeping
 * their geometry.
 *
 * <p>Java elements can only rotate about a single axis, and only by
 * {@code -45/-22.5/0/22.5/45} degrees. An editor that exports the Bedrock form
 * {@code {"x":..,"y":..,"z":..,"origin":..}} (or arbitrary angles such as 67.5
 * and 90) therefore produces models the game cannot load.</p>
 *
 * <p>This tool bakes such rotations instead of deleting them: it rotates the
 * element's eight corners, then cuts the rotated cuboid into thin slices along
 * its longest world axis and writes each slice as an axis-aligned box. The
 * silhouette, position and thickness stay the same; only the smooth tilt
 * becomes a staircase, which is invisible at Minecraft's pixel scale. Exact
 * 90-degree rotations collapse to a single box.</p>
 *
 * <p>Valid {@code angle/axis/origin} rotations are left exactly as they are.
 * Run from the project root with JDK 21: {@code java tools/BakeBedrockRotations.java}.
 * Pass file paths to limit the run.</p>
 */
public final class BakeBedrockRotations {
    static final Path MODELS = Path.of("src/main/resources/assets/samogon/models/block");
    static final double[] ALLOWED = {-45, -22.5, 0, 22.5, 45};
    /** How thick one staircase slice is, in model pixels. */
    static final double SLICE = 0.5;
    static final int MIN_SLICES = 4;
    static final int MAX_SLICES = 16;

    public static void main(String[] args) throws Exception {
        List<Path> files = new ArrayList<>();
        if (args.length > 0) {
            for (String arg : args) files.add(Path.of(arg));
        } else {
            try (var stream = Files.list(MODELS)) {
                stream.filter(path -> path.toString().endsWith(".json")).sorted().forEach(files::add);
            }
        }

        int baked = 0;
        int changedFiles = 0;
        for (Path path : files) {
            Map<String, Object> model = asMap(Json.parse(Files.readString(path, StandardCharsets.UTF_8)));
            List<Object> elements = asList(model.get("elements"));
            if (elements == null) continue;

            List<Object> rebuilt = new ArrayList<>();
            boolean changed = false;
            for (Object value : elements) {
                Map<String, Object> element = asMap(value);
                Map<String, Object> rotation = element.get("rotation") == null
                        ? null : asMap(element.get("rotation"));
                if (rotation == null || isJavaValid(rotation)) {
                    rebuilt.add(element);
                    continue;
                }
                List<Object> slices = bake(element, rotation);
                if (slices == null) {
                    rebuilt.add(element);
                    continue;
                }
                rebuilt.addAll(slices);
                baked++;
                changed = true;
            }

            if (changed) {
                model.put("elements", rebuilt);
                Files.writeString(path, Json.write(model), StandardCharsets.UTF_8);
                changedFiles++;
                System.out.println(path.getFileName() + ": elements " + elements.size()
                        + " -> " + rebuilt.size());
            }
        }
        System.out.println("Baked " + baked + " rotation(s) in " + changedFiles + " model file(s).");
    }

    static boolean isJavaValid(Map<String, Object> rotation) {
        if (!rotation.containsKey("angle")) return false;
        if (!rotation.containsKey("axis")) return false;
        if (!rotation.containsKey("origin")) return false;
        double angle = num(rotation.get("angle"));
        for (double allowed : ALLOWED) {
            if (Math.abs(allowed - angle) < 1e-6) return true;
        }
        return false;
    }

    /**
     * Rotates the element's corners and returns a list of axis-aligned elements
     * that fill the same volume. Returns {@code null} for a zero rotation, where
     * the caller can simply drop the rotation.
     */
    static List<Object> bake(Map<String, Object> element, Map<String, Object> rotation) {
        double rx = 0, ry = 0, rz = 0;
        double[] origin = rotation.containsKey("origin") ? vec(rotation.get("origin")) : new double[3];
        if (rotation.containsKey("angle")) {
            double angle = num(rotation.get("angle"));
            String axis = String.valueOf(rotation.get("axis"));
            switch (axis) {
                case "x" -> rx = angle;
                case "y" -> ry = angle;
                case "z" -> rz = angle;
                default -> { return null; }
            }
        } else {
            rx = rotation.containsKey("x") ? num(rotation.get("x")) : 0;
            ry = rotation.containsKey("y") ? num(rotation.get("y")) : 0;
            rz = rotation.containsKey("z") ? num(rotation.get("z")) : 0;
        }
        if (Math.abs(rx) < 1e-9 && Math.abs(ry) < 1e-9 && Math.abs(rz) < 1e-9) {
            return null;
        }

        double[] from = vec(element.get("from"));
        double[] to = vec(element.get("to"));
        double[][] corners = new double[8][];
        int index = 0;
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    corners[index++] = rotate(rx, ry, rz, origin,
                            new double[]{x == 0 ? from[0] : to[0],
                                    y == 0 ? from[1] : to[1],
                                    z == 0 ? from[2] : to[2]});
                }
            }
        }

        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] corner : corners) {
            for (int axis = 0; axis < 3; axis++) {
                min[axis] = Math.min(min[axis], corner[axis]);
                max[axis] = Math.max(max[axis], corner[axis]);
            }
        }

        // A rotation by multiples of 90 degrees keeps the cuboid axis-aligned.
        if (isMultipleOf90(rx) && isMultipleOf90(ry) && isMultipleOf90(rz)) {
            List<Object> single = new ArrayList<>();
            single.add(box(element, min, max));
            return single;
        }

        int axis = longestAxis(min, max);
        double extent = max[axis] - min[axis];
        int slices = (int) Math.ceil(extent / SLICE);
        slices = Math.max(MIN_SLICES, Math.min(MAX_SLICES, slices));
        double step = extent / slices;

        List<Object> boxes = new ArrayList<>();
        for (int i = 0; i < slices; i++) {
            double lo = min[axis] + i * step;
            double hi = i == slices - 1 ? max[axis] : lo + step;
            double[] section = crossSection(corners, axis, lo, hi);
            if (section == null) continue;
            // Overlap neighbours by a hair so no pixel gap opens between slices.
            section[axis] -= 0.004;
            section[axis + 3] += 0.004;
            boxes.add(box(element, new double[]{section[0], section[1], section[2]},
                    new double[]{section[3], section[4], section[5]}));
        }
        return boxes;
    }

    static Map<String, Object> box(Map<String, Object> source, double[] min, double[] max) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", arr(min[0], min[1], min[2]));
        out.put("to", arr(max[0], max[1], max[2]));
        out.put("faces", source.get("faces"));
        return out;
    }

    /** AABB of the rotated cuboid clipped to the slab {@code [lo, hi]} on {@code axis}. */
    static double[] crossSection(double[][] corners, int axis, double lo, double hi) {
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        boolean any = false;
        for (double[] corner : corners) {
            if (corner[axis] >= lo - 1e-9 && corner[axis] <= hi + 1e-9) {
                any = include(min, max, corner) || any;
            }
        }
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6},
                {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] edge : edges) {
            double[] a = corners[edge[0]];
            double[] b = corners[edge[1]];
            for (double plane : new double[]{lo, hi}) {
                double da = a[axis] - plane;
                double db = b[axis] - plane;
                if (Math.abs(db - da) < 1e-12) continue;
                if ((da <= 0 && db >= 0) || (da >= 0 && db <= 0)) {
                    double t = (plane - a[axis]) / (b[axis] - a[axis]);
                    if (t < -1e-9 || t > 1 + 1e-9) continue;
                    double[] point = new double[3];
                    for (int k = 0; k < 3; k++) point[k] = a[k] + t * (b[k] - a[k]);
                    if (point[axis] >= lo - 1e-9 && point[axis] <= hi + 1e-9) {
                        any = include(min, max, point) || any;
                    }
                }
            }
        }
        if (!any) return null;
        // Never emit a zero-thickness slice.
        for (int k = 0; k < 3; k++) {
            if (max[k] - min[k] < 1e-6) max[k] = min[k] + 1e-6;
        }
        return new double[]{min[0], min[1], min[2], max[0], max[1], max[2]};
    }

    static boolean include(double[] min, double[] max, double[] point) {
        for (int k = 0; k < 3; k++) {
            min[k] = Math.min(min[k], point[k]);
            max[k] = Math.max(max[k], point[k]);
        }
        return true;
    }

    /** Rotation about X, then Y, then Z (the order Bedrock/Blockbench editors use). */
    static double[] rotate(double rx, double ry, double rz, double[] origin, double[] p) {
        double x = p[0] - origin[0], y = p[1] - origin[1], z = p[2] - origin[2];

        double ax = Math.toRadians(rx);
        double cx = Math.cos(ax), sx = Math.sin(ax);
        double y1 = y * cx - z * sx;
        double z1 = y * sx + z * cx;
        y = y1; z = z1;

        double ay = Math.toRadians(ry);
        double cy = Math.cos(ay), sy = Math.sin(ay);
        double x1 = x * cy + z * sy;
        double z2 = -x * sy + z * cy;
        x = x1; z = z2;

        double az = Math.toRadians(rz);
        double cz = Math.cos(az), sz = Math.sin(az);
        double x2 = x * cz - y * sz;
        double y2 = x * sz + y * cz;

        return new double[]{x2 + origin[0], y2 + origin[1], z + origin[2]};
    }

    static boolean isMultipleOf90(double angle) {
        double rest = Math.abs(angle % 90);
        return rest < 1e-6 || Math.abs(rest - 90) < 1e-6;
    }

    static int longestAxis(double[] min, double[] max) {
        int axis = 0;
        double best = -1;
        for (int k = 0; k < 3; k++) {
            double extent = max[k] - min[k];
            if (extent > best) {
                best = extent;
                axis = k;
            }
        }
        return axis;
    }

    static double[] vec(Object value) {
        List<Object> list = asList(value);
        return new double[]{num(list.get(0)), num(list.get(1)), num(list.get(2))};
    }

    static List<Object> arr(double... values) {
        List<Object> list = new ArrayList<>();
        for (double value : values) list.add(value);
        return list;
    }

    static double num(Object value) {
        return ((Number) value).doubleValue();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object value) {
        return (List<Object>) value;
    }

    /** Minimal JSON reader/writer that keeps key order. */
    static final class Json {
        static final int INLINE_LIMIT = 78;
        private final String text;
        private int pos;

        private Json(String text) {
            this.text = text;
        }

        static Object parse(String text) {
            Json json = new Json(text);
            json.skip();
            Object value = json.value();
            json.skip();
            return value;
        }

        Object value() {
            char c = text.charAt(pos);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': pos += 4; return Boolean.TRUE;
                case 'f': pos += 5; return Boolean.FALSE;
                case 'n': pos += 4; return null;
                default: return number();
            }
        }

        Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skip();
            if (text.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skip();
                String key = string();
                skip();
                pos++; // ':'
                skip();
                map.put(key, value());
                skip();
                char c = text.charAt(pos++);
                if (c == '}') return map;
            }
        }

        List<Object> array() {
            List<Object> list = new ArrayList<>();
            pos++;
            skip();
            if (text.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                skip();
                list.add(value());
                skip();
                char c = text.charAt(pos++);
                if (c == ']') return list;
            }
        }

        String string() {
            StringBuilder out = new StringBuilder();
            pos++;
            while (true) {
                char c = text.charAt(pos++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = text.charAt(pos++);
                    switch (e) {
                        case 'n' -> out.append('\n');
                        case 't' -> out.append('\t');
                        case 'r' -> out.append('\r');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'u' -> {
                            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> out.append(e);
                    }
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }

        Double number() {
            int start = pos;
            while (pos < text.length() && "+-.eE0123456789".indexOf(text.charAt(pos)) >= 0) pos++;
            return Double.parseDouble(text.substring(start, pos));
        }

        void skip() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
        }

        static String write(Object value) {
            StringBuilder out = new StringBuilder();
            render(value, 0, out);
            out.append('\n');
            return out.toString();
        }

        static void render(Object value, int indent, StringBuilder out) {
            if (value instanceof Map<?, ?> map) {
                String compact = compact(value);
                if (compact.length() <= INLINE_LIMIT) {
                    out.append(compact);
                    return;
                }
                out.append("{\n");
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    tabs(out, indent + 1);
                    out.append('"').append(entry.getKey()).append("\": ");
                    render(entry.getValue(), indent + 1, out);
                    if (++i < map.size()) out.append(',');
                    out.append('\n');
                }
                tabs(out, indent);
                out.append('}');
            } else if (value instanceof List<?> list) {
                String compact = compact(value);
                if (compact.length() <= INLINE_LIMIT) {
                    out.append(compact);
                    return;
                }
                out.append("[\n");
                for (int i = 0; i < list.size(); i++) {
                    tabs(out, indent + 1);
                    render(list.get(i), indent + 1, out);
                    if (i + 1 < list.size()) out.append(',');
                    out.append('\n');
                }
                tabs(out, indent);
                out.append(']');
            } else {
                out.append(compact(value));
            }
        }

        static String compact(Object value) {
            StringBuilder out = new StringBuilder();
            compactInto(value, out);
            return out.toString();
        }

        static void compactInto(Object value, StringBuilder out) {
            if (value == null) {
                out.append("null");
            } else if (value instanceof Map<?, ?> map) {
                out.append('{');
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (i++ > 0) out.append(", ");
                    out.append('"').append(entry.getKey()).append("\": ");
                    compactInto(entry.getValue(), out);
                }
                out.append('}');
            } else if (value instanceof List<?> list) {
                out.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) out.append(", ");
                    compactInto(list.get(i), out);
                }
                out.append(']');
            } else if (value instanceof Double d) {
                out.append(number(d));
            } else if (value instanceof Boolean b) {
                out.append(b);
            } else if (value instanceof Number number) {
                out.append(number(number.doubleValue()));
            } else {
                out.append('"').append(value).append('"');
            }
        }

        static void tabs(StringBuilder out, int count) {
            for (int i = 0; i < count; i++) out.append('\t');
        }

        static String number(double value) {
            if (value == Math.rint(value) && Math.abs(value) < 1e15) {
                return Long.toString((long) value);
            }
            String text = String.format(Locale.ROOT, "%.3f", value);
            text = text.replaceAll("0+$", "");
            if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
            return text;
        }
    }

    private BakeBedrockRotations() {}
}