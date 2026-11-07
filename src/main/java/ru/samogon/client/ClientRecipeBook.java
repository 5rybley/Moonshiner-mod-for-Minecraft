package ru.samogon.client;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Client-side mirror of the spirit types the current player has already made.
 * The server pushes the full set whenever it changes, so the recipes tab of the
 * guide can reveal entries without asking the server on every frame.
 */
public final class ClientRecipeBook {
    private static volatile Set<String> discovered = Set.of();

    private ClientRecipeBook() {}

    /** Replaces the cached set from a comma-separated payload. */
    public static void set(String ids) {
        Set<String> next = new LinkedHashSet<>();
        if (ids != null && !ids.isBlank()) {
            for (String id : ids.split(",")) {
                String trimmed = id.strip();
                if (!trimmed.isEmpty()) next.add(trimmed);
            }
        }
        discovered = Collections.unmodifiableSet(next);
    }

    public static boolean has(String typeId) {
        return typeId != null && discovered.contains(typeId);
    }
}