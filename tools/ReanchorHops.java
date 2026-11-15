import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Re-anchors the hand-made hop models so every leaf and cone is attached to the
 * bine, without changing the author's overall silhouette.
 *
 * <p>Run from the project root with JDK 21:
 * {@code java tools/ReanchorHops.java}
 *
 * <p>For each model it:
 * <ol>
 *   <li>normalises the texture keys {@code 3} / {@code 4} to their material names;</li>
 *   <li>compares every element against the stem column (x/z 7.3..8.7);</li>
 *   <li>groups the elements that float away from the stem into clusters and
 *       slides each whole cluster until it touches the stem, keeping the shape;</li>
 *   <li>bakes any rotation Minecraft cannot represent (the legacy Bedrock
 *       {@code {x,y,z,origin}} form and angles outside -45/-22.5/0/22.5/45) into
 *       an axis-aligned box, so the geometry loads instead of turning into
 *       {@code missingno}.</li>
 * </ol>
 *
 * <p>Reads and rewrites the four {@code models/block/hops_stage*.json} files and
 * only owns those files.
 */
public final class ReanchorHops {
    static final Path MODELS = Path.of("src/main/resources/assets/samogon/models/block");
    static final String[] STAGES = {"hops_stage0", "hops_stage1", "hops_stage2", "hops_stage3"};
    /** The stem column every leaf and cone has to touch. */
    static final double STEM_LO = 7.3;
    static final double STEM_HI = 8.7;
    /** Two boxes belong to the same cluster within this gap. */
    static final double JOIN_GAP = 0.2;
    static final double JOIN_Y = 0.5;
    static final double[] ALLOWED = {-45, -22.5, 0, 22.5, 45};

    public static void main(String[] args) throws Exception {
        for (String stage : STAGES) {
            Path path = MODELS.resolve(stage + ".json");
            Map<String, Object> model = asMap(Json.parse(Files.readString(path, StandardCharsets.UTF_8)));
            renameTextureKeys(model);
            List<Object> elements = asList(model.get("elements"));
            List<El> els = new ArrayList<>();
            for (Object element : elements) els.add(new El(asMap(element)));
            int before = detachedClusters(els);
            attachFloatingClusters(els);
            int after = detachedClusters(els);
            List<Object> rebuilt = new ArrayList<>();
            for (El el : els) rebuilt.add(el.toJson());
            model.put("elements", rebuilt);
            Files.writeString(path, Json.write(model), StandardCharsets.UTF_8);
            System.out.println(stage + ": " + els.size() + " elements, detached leaf clusters " + before + " -> " + after);
        }
    }

    /** Clusters of geometry that do not reach the stem, i.e. leaves floating in the air. */
    static int detachedClusters(List<El> els) {
        int n = els.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (neighbour(els.get(i), els.get(j))) union(parent, i, j);
            }
        }
        Map<Integer, Boolean> attached = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            attached.merge(root, els.get(i).touchesStem(), Boolean::logicalOr);
        }
        int detached = 0;
        for (boolean value : attached.values()) if (!value) detached++;
        return detached;
    }

    static void attachFloatingClusters(List<El> els) {
        int n = els.size();
        // Only already-attached geometry is a valid anchor; floaters are grouped
        // among themselves so no wide rotated leaf bridges the clusters together.
        boolean[] floating = new boolean[n];
        for (int i = 0; i < n; i++) floating[i] = !els.get(i).touchesStem();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            if (!floating[i]) continue;
            for (int j = i + 1; j < n; j++) {
                if (floating[j] && neighbour(els.get(i), els.get(j))) union(parent, i, j);
            }
        }
        Map<Integer, List<Integer>> components = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) if (floating[i]) components.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        if (System.getenv("REANCHOR_DEBUG") != null) {
            System.out.println("    floating components: " + components.size());
            for (List<Integer> component : components.values()) {
                double bx1 = Double.MAX_VALUE, bx2 = -Double.MAX_VALUE, bz1 = Double.MAX_VALUE, bz2 = -Double.MAX_VALUE;
                for (int i : component) {
                    bx1 = Math.min(bx1, els.get(i).x1);
                    bx2 = Math.max(bx2, els.get(i).x2);
                    bz1 = Math.min(bz1, els.get(i).z1);
                    bz2 = Math.max(bz2, els.get(i).z2);
                }
                System.out.printf(Locale.ROOT, "      size=%-4d x[%7.2f..%7.2f] z[%7.2f..%7.2f]%n", component.size(), bx1, bx2, bz1, bz2);
            }
        }
        for (List<Integer> component : components.values()) {
            double x1 = Double.MAX_VALUE, x2 = -Double.MAX_VALUE;
            double z1 = Double.MAX_VALUE, z2 = -Double.MAX_VALUE;
            for (int i : component) {
                El el = els.get(i);
                x1 = Math.min(x1, el.x1);
                x2 = Math.max(x2, el.x2);
                z1 = Math.min(z1, el.z1);
                z2 = Math.max(z2, el.z2);
            }
            double dx = x2 < STEM_LO ? STEM_LO - x2 : (x1 > STEM_HI ? STEM_HI - x1 : 0);
            double dz = z2 < STEM_LO ? STEM_LO - z2 : (z1 > STEM_HI ? STEM_HI - z1 : 0);
            for (int i : component) els.get(i).translate(dx, dz);
        }
    }

    static boolean neighbour(El a, El b) {
        boolean x = a.x1 - JOIN_GAP <= b.x2 && b.x1 - JOIN_GAP <= a.x2;
        boolean z = a.z1 - JOIN_GAP <= b.z2 && b.z1 - JOIN_GAP <= a.z2;
        boolean y = a.y1 - JOIN_Y <= b.y2 && b.y1 - JOIN_Y <= a.y2;
        return x && z && y;
    }

    static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[rb] = ra;
    }

    static void renameTextureKeys(Map<String, Object> model) {
        Map<String, String> rename = new LinkedHashMap<>();
        Map<String, Object> textures = asMap(model.get("textures"));
        for (Map.Entry<String, Object> entry : textures.entrySet()) {
            String value = String.valueOf(entry.getValue());
            if (value.endsWith("/green")) rename.put(entry.getKey(), "green");
            else if (value.endsWith("/dark_wood")) rename.put(entry.getKey(), "dark_wood");
        }
        for (Map.Entry<String, String> entry : rename.entrySet()) {
            if (entry.getKey().equals(entry.getValue())) continue;
            Object value = textures.remove(entry.getKey());
            textures.put(entry.getValue(), value);
        }
        for (Object element : asList(model.get("elements"))) {
            Map<String, Object> faces = asMap(asMap(element).get("faces"));
            for (Object face : faces.values()) {
                Map<String, Object> map = asMap(face);
                String texture = String.valueOf(map.get("texture"));
                if (texture.startsWith("#")) {
                    String key = texture.substring(1);
                    if (rename.containsKey(key) && !rename.get(key).equals(key)) {
                        map.put("texture", "#" + rename.get(key));
                    }
                }
            }
        }
    }

    /** One block element with its rotation normalised and its world-space footprint tracked. */
    static final class El {
        final Map<String, Object> source;
        final double[] from;
        final double[] to;
        Double angle;
        double[] origin;
        String axis = "y";
        boolean baked;
        double x1, y1, z1, x2, y2, z2;

        El(Map<String, Object> source) {
            this.source = source;
            from = vec(source.get("from"));
            to = vec(source.get("to"));
            Object rotation = source.get("rotation");
            if (rotation == null) {
                angle = null;
            } else {
                Map<String, Object> rot = asMap(rotation);
                if (rot.containsKey("angle")) {
                    angle = num(rot.get("angle"));
                    origin = rot.containsKey("origin") ? vec(rot.get("origin")) : new double[3];
                    if (rot.containsKey("axis")) axis = String.valueOf(rot.get("axis"));
                    baked = !allowed(angle);
                } else {
                    angle = rot.containsKey("y") ? num(rot.get("y")) : 0;
                    origin = rot.containsKey("origin") ? vec(rot.get("origin")) : new double[3];
                    baked = true;
                }
            }
            computeFootprint();
        }

        void computeFootprint() {
            double a = angle == null ? 0 : angle;
            double[] o = origin == null ? new double[3] : origin;
            double rad = Math.toRadians(a);
            double c = Math.cos(rad), s = Math.sin(rad);
            x1 = y1 = z1 = Double.MAX_VALUE;
            x2 = y2 = z2 = -Double.MAX_VALUE;
            for (int xi = 0; xi < 2; xi++) {
                for (int zi = 0; zi < 2; zi++) {
                    double x = xi == 0 ? from[0] : to[0];
                    double z = zi == 0 ? from[2] : to[2];
                    double rx = o[0] + (x - o[0]) * c + (z - o[2]) * s;
                    double rz = o[2] - (x - o[0]) * s + (z - o[2]) * c;
                    x1 = Math.min(x1, rx);
                    x2 = Math.max(x2, rx);
                    z1 = Math.min(z1, rz);
                    z2 = Math.max(z2, rz);
                }
            }
            y1 = Math.min(from[1], to[1]);
            y2 = Math.max(from[1], to[1]);
        }

        boolean touchesStem() {
            return x1 <= STEM_HI && x2 >= STEM_LO && z1 <= STEM_HI && z2 >= STEM_LO;
        }

        void translate(double dx, double dz) {
            from[0] += dx;
            from[2] += dz;
            to[0] += dx;
            to[2] += dz;
            if (origin != null) {
                origin[0] += dx;
                origin[2] += dz;
            }
            computeFootprint();
        }

        Map<String, Object> toJson() {
            Map<String, Object> out = new LinkedHashMap<>();
            if (baked) {
                out.put("from", arr(x1, y1, z1));
                out.put("to", arr(x2, y2, z2));
            } else {
                out.put("from", arr(from[0], from[1], from[2]));
                out.put("to", arr(to[0], to[1], to[2]));
                if (angle != null) {
                    Map<String, Object> rot = new LinkedHashMap<>();
                    rot.put("angle", angle);
                    rot.put("axis", axis);
                    rot.put("origin", arr(origin[0], origin[1], origin[2]));
                    out.put("rotation", rot);
                }
            }
            out.put("faces", source.get("faces"));
            return out;
        }
    }

    static boolean allowed(double angle) {
        for (double candidate : ALLOWED) if (Math.abs(candidate - angle) < 1e-6) return true;
        return false;
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

    /** Minimal JSON reader/writer that keeps key order and prints tab-indented, compact-where-sensible output. */
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

    private ReanchorHops() {}
}
