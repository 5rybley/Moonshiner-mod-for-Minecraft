package ru.samogon.process;

import java.util.Map;

/**
 * Dependency-free checks for the blend aura model. The rules are deliberately
 * plain data, so the grouping that turns five berries into one level-V aura and
 * a mixed pantry into several level-I auras can be verified without Minecraft.
 */
public final class AromaTest {
    public static void main(String[] args) {
        // Five of the same additive stack into one level-V aura.
        expect(Map.of("berry", 5),
                "sweet_berries,sweet_berries,sweet_berries,sweet_berries,sweet_berries",
                "five berries");

        // A botanical, a berry and a honey give three level-I auras, not one
        // priority aura.
        expect(Map.of("berry", 1, "honey", 1, "botanical", 1),
                "botanical_bundle,honey_bottle,sweet_berries", "three families");

        // Different additives that share an aura still stack.
        expect(Map.of("berry", 5),
                "chorus_fruit,glow_berries,sugar,sugar_cane,sweet_berries",
                "five berry-family additives");

        // Mixed strengths.
        expect(Map.of("berry", 3, "honey", 2),
                "honey_bottle,honey_bottle,sweet_berries,sweet_berries,sweet_berries",
                "three berries and two honey");

        // ROOT and GRAIN both feed the oak aura.
        expect(Map.of("oak", 2), "beetroot,wheat", "root and grain share the oak aura");

        // Five distinct families give five auras of level I.
        expect(Map.of("berry", 1, "honey", 1, "botanical", 1, "oak", 1, "fruit", 1),
                "apple,beetroot,botanical_bundle,honey_bottle,sweet_berries",
                "five distinct auras");

        // An oversized or malformed composition never exceeds level V.
        expect(Map.of("berry", 5),
                "sweet_berries,sweet_berries,sweet_berries,sweet_berries,sweet_berries,sweet_berries",
                "capped at five");

        // Empty and unknown compositions are harmless.
        expect(Map.of(), "", "empty composition");
        expect(Map.of(), "unobtainium", "unknown additive");

        // The family-to-aura mapping is stable.
        require(AromaFamily.aura(AromaFamily.Family.BERRY).equals("berry"), "berry aura");
        require(AromaFamily.aura(AromaFamily.Family.SWEET).equals("berry"), "sweet joins berry");
        require(AromaFamily.aura(AromaFamily.Family.EXOTIC).equals("berry"), "exotic joins berry");
        require(AromaFamily.aura(AromaFamily.Family.FRUIT).equals("fruit"), "fruit aura");
        require(AromaFamily.aura(AromaFamily.Family.HONEY).equals("honey"), "honey aura");
        require(AromaFamily.aura(AromaFamily.Family.BOTANICAL).equals("botanical"), "botanical aura");
        require(AromaFamily.aura(AromaFamily.Family.SEED).equals("botanical"), "seed joins botanical");
        require(AromaFamily.aura(AromaFamily.Family.FUNGUS).equals("botanical"), "fungus joins botanical");
        require(AromaFamily.aura(AromaFamily.Family.MARINE).equals("marine"), "marine aura");
        require(AromaFamily.aura(AromaFamily.Family.SPICE).equals("spice"), "spice aura");
        require(AromaFamily.aura(AromaFamily.Family.ROOT).equals("oak"), "root joins oak");
        require(AromaFamily.aura(AromaFamily.Family.GRAIN).equals("oak"), "grain joins oak");

        System.out.println("Aroma aura levels passed");
    }

    private static void expect(Map<String, Integer> expected, String composition, String label) {
        Map<String, Integer> actual = AromaFamily.auraLevels(composition);
        require(actual.equals(expected), label + ": expected " + expected + " but got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private AromaTest() {
    }
}