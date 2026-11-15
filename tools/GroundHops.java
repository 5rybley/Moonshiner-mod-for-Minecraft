import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plants the hop models on the soil instead of letting them hover a pixel above it.
 *
 * <p>Crops sit on farmland, and farmland is 15/16 tall, so the crop block's
 * origin is 1/16 above the surface the plant actually stands on. Vanilla crops
 * are wide tufts and hide that sliver; a hop is a thin bine, so it reads as
 * floating. This tool:
 * <ol>
 *   <li>drops every element by 1/16 ({@link #FARM_RECESS}) so the plant rests on
 *       the farmland surface;</li>
 *   <li>buries the central stem down to {@link #STEM_ROOT} so the bine visibly
 *       grows out of the soil even if the block underneath is not farmland.</li>
 * </ol>
 *
 * <p>Run from the project root with JDK 21:
 * {@code javac -d tools/classes tools/ReanchorHops.java tools/GroundHops.java}
 * then {@code java -cp tools/classes GroundHops}. Idempotent: an already
 * grounded model is left untouched.
 */
public final class GroundHops {
    static final Path MODELS = Path.of("src/main/resources/assets/samogon/models/block");
    static final String[] STAGES = {"hops_stage0", "hops_stage1", "hops_stage2", "hops_stage3"};
    /** Farmland is 15/16 tall, so the crop block floats this much above the soil. */
    static final double FARM_RECESS = 0.0625;
    /** How deep the bine is buried so it clearly emerges from the ground. */
    static final double STEM_ROOT = -1.0;
    static final double STEM_LO = 7.3;
    static final double STEM_HI = 8.7;

    public static void main(String[] args) throws Exception {
        for (String stage : STAGES) {
            Path path = MODELS.resolve(stage + ".json");
            Map<String, Object> model = ReanchorHops.asMap(
                    ReanchorHops.Json.parse(Files.readString(path, StandardCharsets.UTF_8)));
            List<Object> elements = ReanchorHops.asList(model.get("elements"));
            double before = minY(elements);
            if (before <= -0.5) {
                System.out.println(stage + ": already grounded (minY=" + fmt(before) + ")");
                continue;
            }
            for (Object element : elements) {
                Map<String, Object> el = ReanchorHops.asMap(element);
                drop(el, "from");
                drop(el, "to");
            }
            int rooted = 0;
            for (Object element : elements) {
                Map<String, Object> el = ReanchorHops.asMap(element);
                double[] from = ReanchorHops.vec(el.get("from"));
                double[] to = ReanchorHops.vec(el.get("to"));
                if (from[0] == STEM_LO && to[0] == STEM_HI && from[2] == STEM_LO && to[2] == STEM_HI) {
                    from[1] = STEM_ROOT;
                    el.put("from", ReanchorHops.arr(from));
                    rooted++;
                }
            }
            Files.writeString(path, ReanchorHops.Json.write(model), StandardCharsets.UTF_8);
            System.out.println(stage + ": " + elements.size() + " elements, rooted stems=" + rooted
                    + ", minY " + fmt(before) + " -> " + fmt(minY(elements)));
        }
    }

    /** Lowers one coordinate vector by the farmland recess. */
    static void drop(Map<String, Object> element, String key) {
        List<Object> vector = ReanchorHops.asList(element.get(key));
        vector.set(1, ReanchorHops.num(vector.get(1)) - FARM_RECESS);
    }

    static double minY(List<Object> elements) {
        double min = Double.MAX_VALUE;
        for (Object element : elements) {
            min = Math.min(min, ReanchorHops.vec(ReanchorHops.asMap(element).get("from"))[1]);
        }
        return min;
    }

    static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private GroundHops() {}
}
