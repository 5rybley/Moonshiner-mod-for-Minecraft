package ru.samogon.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.samogon.entity.PukeBlockEntity;
import ru.samogon.item.BatchItem;
import ru.samogon.network.BlackoutPayload;
import ru.samogon.process.AromaFamily;
import ru.samogon.process.Simulation.Batch;
import ru.samogon.registry.ModEffects;
import ru.samogon.registry.ModSounds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared drunkenness rules: drink dose, five-level scaling, nausea, vomiting,
 * staggering and the per-tincture affinities.
 */
public final class Intoxication {
    public static final int MAX_LEVEL = 5;

    /** Level V makes the world go black once the drinker has been that drunk this long. */
    private static final int BLACKOUT_TICKS = 30 * 20;
    /** Client-side fade-in; the teleport waits for the screen to be dark. */
    private static final int BLACKOUT_FADE_TICKS = 12;
    private static final int BLACKOUT_HOLD_TICKS = 46;
    /** How far the blackout can fling the drinker, in blocks. */
    private static final double BLACKOUT_RADIUS = 30.0;
    private static final String BLACKOUT_TIMER = "samogon:blackout_timer";
    /** One lump of puke leaves the mouth every this many ticks (1 per 0.15s). */
    private static final int PUKE_INTERVAL_TICKS = 3;
    /** How many lumps of the current volley are still waiting to come out. */
    private static final String PUKE_STREAM_LEFT = "samogon:puke_stream_left";
    /** Player tick at which the next lump of the current volley should come out. */
    private static final String PUKE_STREAM_NEXT = "samogon:puke_stream_next";
    /** Highest intoxication level reached since the drinker was last sober. */
    private static final String PEAK_LEVEL = "samogon:peak_level";
    /** Set by brine so the rest of that session cannot re-arm the hangover. */
    private static final String CURED_SESSION = "samogon:cured_session";
    /**
     * Aura -> level map for the current drink. It survives while the player is
     * intoxicated and is cleared the moment they sober up, so the next drink
     * starts with a clean slate.
     */
    private static final String AURAS = "samogon:auras";

    private static final List<Holder<MobEffect>> TINCTURE_AURAS = List.<Holder<MobEffect>>of(
            ModEffects.BERRY_FURY,
            ModEffects.HONEY_GUARD,
            ModEffects.BOTANICAL_CLARITY,
            ModEffects.OAK_GRIT,
            ModEffects.SPICED_EMBER,
            ModEffects.ORCHARD_BLOOM,
            ModEffects.TIDAL_BREATH);

    private Intoxication() {}

    /** Returns the current intoxication level, from 0 (sober) to 5. */
    public static int level(Player player) {
        MobEffectInstance effect = player.getEffect(ModEffects.INTOXICATION);
        return effect == null ? 0 : Math.min(MAX_LEVEL, effect.getAmplifier() + 1);
    }

    /** Records the highest level of the current session, which scales the hangover. */
    static void trackPeak(ServerPlayer player, int level) {
        CompoundTag data = player.getPersistentData();
        if (level > data.getInt(PEAK_LEVEL)) {
            data.putInt(PEAK_LEVEL, level);
        }
    }

    /** Highest level reached since the last sober moment; 0 when the session is over. */
    static int peakLevel(Player player) {
        return player.getPersistentData().getInt(PEAK_LEVEL);
    }

    static void clearPeak(Player player) {
        player.getPersistentData().putInt(PEAK_LEVEL, 0);
    }

    /** True while a glass of brine is carrying the drinker through the session. */
    static boolean cured(Player player) {
        return player.getPersistentData().getBoolean(CURED_SESSION);
    }

    static void clearCured(Player player) {
        player.getPersistentData().remove(CURED_SESSION);
    }

    /** Drops the level-V blackout countdown when the effect ends outright. */
    static void clearBlackoutTimer(Player player) {
        player.getPersistentData().remove(BLACKOUT_TIMER);
    }

    /**
     * A glass of brine: one intoxication level down, the hangover wiped away and
     * a short spell of clarity. The peak tracker is reset so the sobering that
     * follows is not mistaken for a natural one and does not re-apply a hangover.
     */
    public static void cure(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(ModEffects.INTOXICATION);
        if (current != null) {
            int amplifier = current.getAmplifier();
            int duration = current.getDuration();
            // MobEffectInstance#update only accepts a *higher* amplifier, so a
            // weaker instance would be ignored (or parked in hiddenEffect).
            // Dropping the old effect first is the only way to sober up.
            player.removeEffect(ModEffects.INTOXICATION);
            if (amplifier > 0) {
                player.addEffect(new MobEffectInstance(
                        ModEffects.INTOXICATION, duration, amplifier - 1,
                        false, true, true));
            }
        }

        clearPeak(player);
        // Brine lowers the level by one. A drink that still leaves the player
        // tipsy keeps its auras; the last step to sober clears them at once.
        boolean stillDrunk = player.hasEffect(ModEffects.INTOXICATION);
        if (!stillDrunk) {
            clearAuras(player);
        }
        player.getPersistentData().putBoolean(CURED_SESSION, true);
        player.removeEffect(ModEffects.HANGOVER);
        player.removeEffect(MobEffects.CONFUSION);
        player.removeEffect(MobEffects.HUNGER);
        player.addEffect(new MobEffectInstance(ModEffects.CLEAR_HEAD, 30 * 20, 0, false, true, true));
    }

    /**
     * Applies one drink. A strong or dirty batch can start at level II or III;
     * each further drink raises the level by one, up to V.
     */
    public static void drink(Player player, Batch batch, BatchItem.Tincture tincture) {
        drink(player, batch, tincture, false);
    }

    public static void drink(Player player, Batch batch, BatchItem.Tincture tincture, boolean technical) {
        drink(player, batch, aurasFor(batch, tincture), technical);
    }

    /**
     * Applies one drink with an explicit aura map. The map may hold up to five
     * auras, each at the strength contributed by the blend.
     */
    public static void drink(Player player, Batch batch, Map<String, Integer> auras, boolean technical) {
        if (!(player instanceof ServerPlayer server) || player.level().isClientSide()) {
            return;
        }

        // A fresh drink starts a fresh session: brine no longer shields it.
        clearCured(server);

        MobEffectInstance existing = player.getEffect(ModEffects.INTOXICATION);
        int currentLevel = existing == null ? 0 : Math.min(MAX_LEVEL, existing.getAmplifier() + 1);
        int baseLevel = baseLevel(batch);
        int nextLevel = currentLevel == 0
                ? baseLevel
                : Math.min(MAX_LEVEL, Math.max(currentLevel, baseLevel) + 1);
        int duration = Math.max(durationTicks(batch), existing == null ? 0 : existing.getDuration());

        player.addEffect(new MobEffectInstance(ModEffects.INTOXICATION, duration, nextLevel - 1, false, true, true));

        storeAuras(server, auras);
        int dominant = dominantAuraIndex(auras);
        player.removeEffect(ModEffects.TINCTURE_MARK);
        if (dominant >= 0) {
            // The marker only carries the dominant hue for DrunkVision; the
            // actual aura strengths live in the persistent aura map above.
            player.addEffect(new MobEffectInstance(ModEffects.TINCTURE_MARK, duration, dominant, false, false, false));
        }
        // Apply the auras immediately instead of waiting for the next effect
        // tick, so the first sip visibly grants every effect.
        refreshAura(server, nextLevel);

        server.displayClientMessage(
                Component.translatable("message.samogon.intoxication.level", roman(nextLevel)),
                true);
        server.level().playSound(
                null,
                server.blockPosition(),
                ModSounds.DRINK_SWIG.get(),
                SoundSource.PLAYERS,
                0.8F,
                0.95F + server.getRandom().nextFloat() * 0.12F);

        if (technical) {
            applyTechnicalDebuffs(server, batch);
            server.displayClientMessage(Component.translatable("message.samogon.technical_drink"), false);
        }
    }

    /**
     * Aura strengths a bottle would grant. A blended composition is the source
     * of truth; the fixed classic tinctures fall back to their single flavour.
     */
    public static Map<String, Integer> aurasFor(Batch batch, BatchItem.Tincture tincture) {
        Map<String, Integer> levels = AromaFamily.auraLevels(batch == null ? "" : batch.composition());
        if (levels.isEmpty() && tincture != null && tincture != BatchItem.Tincture.NONE) {
            levels = Map.of(tincture.id(), 1);
        }
        return levels;
    }

    /** Aura id -> level map currently carried by the drinker. */
    public static Map<String, Integer> storedAuras(Player player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag tag = data.getCompound(AURAS);
        if (tag.isEmpty()) return Map.of();
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (String id : AromaFamily.AURAS) {
            int level = tag.getInt(id);
            if (level > 0) {
                levels.put(id, Math.min(AromaFamily.MAX_AURA_LEVEL, level));
            }
        }
        return levels;
    }

    private static void storeAuras(Player player, Map<String, Integer> auras) {
        CompoundTag data = player.getPersistentData();
        if (auras == null || auras.isEmpty()) {
            data.remove(AURAS);
            return;
        }
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : auras.entrySet()) {
            if (auraIndex(entry.getKey()) < 0 || entry.getValue() == null) continue;
            int level = Math.max(1, Math.min(AromaFamily.MAX_AURA_LEVEL, entry.getValue()));
            tag.putInt(entry.getKey(), level);
        }
        if (tag.isEmpty()) {
            data.remove(AURAS);
        } else {
            data.put(AURAS, tag);
        }
    }

    /** Removes every aura effect and forgets the current drink's aura map. */
    static void clearAuras(Player player) {
        for (Holder<MobEffect> aura : TINCTURE_AURAS) {
            player.removeEffect(aura);
        }
        player.getPersistentData().remove(AURAS);
        player.removeEffect(ModEffects.TINCTURE_MARK);
    }

    /** The strongest aura in the map, in canonical order on a tie. */
    public static String dominantAura(Map<String, Integer> auras) {
        if (auras == null || auras.isEmpty()) return null;
        String best = null;
        int bestLevel = 0;
        for (String id : AromaFamily.AURAS) {
            Integer level = auras.get(id);
            if (level == null || level <= bestLevel) continue;
            bestLevel = level;
            best = id;
        }
        return best;
    }

    private static int dominantAuraIndex(Map<String, Integer> auras) {
        return auraIndex(dominantAura(auras));
    }

    /** Called by the visible effect every tick. */
    static void tick(ServerPlayer player, int level) {
        if (player.isSpectator() || player.isDeadOrDying()) {
            return;
        }

        // Botanical clarity works from the first sip now; its strength is the
        // aura level, not the intoxication level.
        boolean botanical = player.hasEffect(ModEffects.BOTANICAL_CLARITY);
        int botanicalLevel = auraAmplifier(player, "botanical") + 1;

        // Keep nausea active even at level I; higher levels refresh it faster and for longer.
        int nauseaInterval = Math.max(20, 120 - level * 20);
        int nauseaDuration = 200 + level * 40;
        if (botanical) {
            nauseaDuration = Math.max(100, nauseaDuration / 2);
        }
        if (player.tickCount % nauseaInterval == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, nauseaDuration, 0, true, true, true));
        }

        if (level >= 2 && player.tickCount % 40 == 0) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    70,
                    level >= 4 ? 1 : 0,
                    true,
                    true,
                    true));
        }

        // Vomiting is reserved for the last two levels: a IV or V drinker may
        // suddenly heave, while the milder stages only sway and blur.
        if (level >= 4 && player.tickCount % 20 == 0) {
            float chance = 0.01F + level * 0.015F;
            if (botanical) {
                chance *= 0.5F;
            }
            if (player.getRandom().nextFloat() < chance) {
                vomit(player, level);
            }
        }

        // Let the rest of a vomit volley trickle out, one lump at a time.
        tickPukeStream(player);

        if (player.tickCount % 8 == 0
                && player.walkDist > player.walkDistO
                && player.onGround()
                && !player.isPassenger()
                && !player.isFallFlying()
                && !player.isInWater()) {
            stumble(player, level);
        }

        refreshAura(player, level);
        tickBlackout(player, level);

        if (botanical && player.tickCount % 100 == 0) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, 100, botanicalLevel >= 3 ? 1 : 0, true, true, true));
        }
    }

    /** Chance to completely ignore one incoming damage event. */
    public static float blockChance(int level) {
        return switch (level) {
            case 3 -> 0.10F;
            case 4 -> 0.15F;
            case 5 -> 0.20F;
            default -> 0.0F;
        };
    }

    /** Chance for the drunken critical strike. */
    public static float attackChance(int level) {
        return switch (level) {
            case 3 -> 0.10F;
            case 4 -> 0.15F;
            case 5 -> 0.20F;
            default -> 0.0F;
        };
    }

    /** At IV+ the drunk swats arrows out of the air; the chance is kept tunable. */
    public static float arrowDeflectChance(int level) {
        return level >= 4 ? 1.0F : 0.0F;
    }

    /** Base intoxication level a fresh glass of this batch would apply. */
    public static int baseLevel(Batch batch) {
        int level = 1;
        if (batch.abv() >= 90.0) {
            level += 3;
        } else if (batch.abv() >= 70.0) {
            level += 2;
        } else if (batch.abv() >= 30.0) {
            level += 1;
        }
        if (batch.impurities() > 45) {
            level += 1;
        }
        if (batch.quality() < 50) {
            level += 1;
        }
        return Math.min(4, level);
    }

    private static void applyTechnicalDebuffs(ServerPlayer player, Batch batch) {
        int abv = (int) Math.round(batch.abv());
        int impurityPenalty = Math.max(0, batch.impurities() - batch.quality() / 2);
        int severity = Math.max(1, Math.min(100, abv + impurityPenalty / 4));

        int poisonTicks = (3 + severity / 10) * 20;
        int blindnessTicks = 40 + severity * 2;
        int weaknessTicks = 100 + severity * 3;

        player.addEffect(new MobEffectInstance(
                MobEffects.POISON, poisonTicks, severity >= 85 ? 1 : 0, true, true, true));
        player.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS, blindnessTicks, 0, true, true, true));
        player.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS, weaknessTicks, severity >= 85 ? 1 : 0, true, true, true));
        player.addEffect(new MobEffectInstance(
                MobEffects.HUNGER, weaknessTicks, severity >= 70 ? 1 : 0, true, true, true));
        player.addEffect(new MobEffectInstance(
                MobEffects.CONFUSION, 200 + severity * 2, 0, true, true, true));
        player.causeFoodExhaustion(2.0F + severity * 0.03F);
    }

    /** How long one glass of this batch keeps the drinker under the effect, in ticks. */
    public static int durationTicks(Batch batch) {
        long seconds = 45L
                + Math.round(batch.alcohol() * 1.4)
                + batch.impurities() / 5;
        return (int) Math.max(45L * 20L, Math.min(360L * 20L, seconds * 20L));
    }

    private static int flavorIndex(BatchItem.Tincture tincture) {
        return switch (tincture) {
            case BERRY -> 0;
            case HONEY -> 1;
            case BOTANICAL -> 2;
            case OAK -> 3;
            case SPICE -> 4;
            case FRUIT -> 5;
            case MARINE -> 6;
            case NONE -> -1;
        };
    }

    /** Aura index (0..6) granted by the tincture, or -1 when it has none. */
    public static int auraIndex(BatchItem.Tincture tincture) {
        return flavorIndex(tincture);
    }

    /** Aura index (0..6) for an aura id, or -1 when the id is unknown. */
    public static int auraIndex(String auraId) {
        return auraId == null ? -1 : AromaFamily.AURAS.indexOf(auraId);
    }

    /** Translation key of the aura effect at the given index, or "" when none. */
    public static String auraKey(int index) {
        return switch (index) {
            case 0 -> "effect.samogon.berry_fury";
            case 1 -> "effect.samogon.honey_guard";
            case 2 -> "effect.samogon.botanical_clarity";
            case 3 -> "effect.samogon.oak_grit";
            case 4 -> "effect.samogon.spiced_ember";
            case 5 -> "effect.samogon.orchard_bloom";
            case 6 -> "effect.samogon.tidal_breath";
            default -> "";
        };
    }

    /** Translation key of the aura effect for an aura id, or "" when unknown. */
    public static String auraKey(String auraId) {
        return auraKey(auraIndex(auraId));
    }

    /**
     * Localized list of every aura in a drink, e.g.
     * "Berry Fury V, Honey Guard I". Used by tooltips and the tasting table.
     */
    public static Component auraSummary(Map<String, Integer> auras) {
        if (auras == null || auras.isEmpty()) {
            return Component.translatable("message.samogon.none");
        }
        MutableComponent result = Component.empty();
        boolean first = true;
        for (String id : AromaFamily.AURAS) {
            Integer level = auras.get(id);
            if (level == null || level <= 0) continue;
            if (!first) result.append(Component.literal(", "));
            result.append(Component.translatable(auraKey(id)))
                    .append(Component.literal(" " + roman(level)));
            first = false;
        }
        return first ? Component.translatable("message.samogon.none") : result;
    }

    /** Amplifier of the drinker's aura, or -1 when the aura is not active. */
    public static int auraAmplifier(Player player, String auraId) {
        int index = auraIndex(auraId);
        if (index < 0) return -1;
        MobEffectInstance effect = player.getEffect(TINCTURE_AURAS.get(index));
        return effect == null ? -1 : effect.getAmplifier();
    }

    /**
     * Grants or refreshes every aura stored for the current drink. Auras apply
     * from the first level of intoxication; their amplifier is the number of
     * matching additives minus one, so five berries show as level V.
     */
    private static void refreshAura(ServerPlayer player, int level) {
        Map<String, Integer> auras = storedAuras(player);
        boolean active = level >= 1 && !auras.isEmpty();

        for (int i = 0; i < TINCTURE_AURAS.size(); i++) {
            if (!active || !auras.containsKey(AromaFamily.AURAS.get(i))) {
                player.removeEffect(TINCTURE_AURAS.get(i));
            }
        }
        if (!active) {
            return;
        }

        for (Map.Entry<String, Integer> entry : auras.entrySet()) {
            int index = auraIndex(entry.getKey());
            if (index < 0) continue;
            int amplifier = Math.min(AromaFamily.MAX_AURA_LEVEL, Math.max(1, entry.getValue())) - 1;
            Holder<MobEffect> desired = TINCTURE_AURAS.get(index);
            MobEffectInstance current = player.getEffect(desired);
            if (current == null || current.getDuration() < 40 || current.getAmplifier() != amplifier) {
                // A MobEffectInstance update cannot lower the amplifier, so a
                // weaker aura has to replace the old instance outright.
                player.removeEffect(desired);
                player.addEffect(new MobEffectInstance(desired, 60, amplifier, false, true, true));
            }
            applyAuraPerk(player, entry.getKey(), amplifier);
        }
    }

    /**
     * Passive perks of the two "slow" affinities. Spiced Ember acts on the
     * attack itself, berry/honey/oak/botanical are used by the damage events.
     */
    private static void applyAuraPerk(ServerPlayer player, String auraId, int amplifier) {
        // Orchard Bloom: fruit sugar slowly refills an empty stomach and keeps
        // a gentle regeneration pulse going.
        if ("fruit".equals(auraId) && player.tickCount % 100 == 0) {
            if (player.getFoodData().getFoodLevel() < 20) {
                player.getFoodData().eat(1, 0.4F);
            }
            player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, 40, amplifier >= 3 ? 1 : 0, true, false, true));
        }

        // Tidal Breath: while in water the drinker simply does not drown.
        // The refresh is throttled, otherwise two effect-update packets would be
        // sent to the client on every single tick spent in water.
        if ("marine".equals(auraId) && player.isInWater()) {
            MobEffectInstance breath = player.getEffect(MobEffects.WATER_BREATHING);
            if (breath == null || breath.getDuration() < 40) {
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, true, false, true));
            }
            MobEffectInstance grace = player.getEffect(MobEffects.DOLPHINS_GRACE);
            if (grace == null || grace.getDuration() < 40) {
                player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 100, 0, true, false, true));
            }
        }
    }

    private static void stumble(ServerPlayer player, int level) {
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        if (forward.lengthSqr() < 1.0E-4) {
            return;
        }
        forward = forward.normalize();

        float chance = 0.08F + level * 0.035F;
        int oakLevel = auraAmplifier(player, "oak");
        if (oakLevel >= 0) {
            chance *= Math.max(0.40F, 0.75F - 0.05F * oakLevel);
        }
        if (player.getRandom().nextFloat() >= chance) {
            return;
        }

        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        double strength = 0.10 + level * 0.035;
        boolean backwards = player.getRandom().nextFloat() < (level >= 4 ? 0.35F : 0.15F);
        Vec3 impulse = backwards
                ? forward.scale(-strength * 1.15)
                : side.scale((player.getRandom().nextBoolean() ? 1.0 : -1.0) * strength);

        if (level >= 5 && player.getRandom().nextFloat() < 0.25F) {
            impulse = impulse.add(forward.scale(-strength * 0.8));
        }

        player.push(impulse.x, 0.025, impulse.z);
        player.hurtMarked = true;
    }

    private static void vomit(ServerPlayer player, int level) {
        ServerLevel server = player.serverLevel();

        // A generous puff of soul-sand crumbs right at the mouth so the effect
        // starts instantly even before the tumbling blocks are in view.
        server.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SOUL_SAND.defaultBlockState()),
                player.getX(),
                player.getY() + 1.1,
                player.getZ(),
                42,
                0.40,
                0.22,
                0.40,
                0.14);
        // Soul sand is nearly black, so a few bright soul wisps keep the puke
        // readable at night or against dark terrain.
        server.sendParticles(
                ParticleTypes.SOUL,
                player.getX(),
                player.getY() + 1.15,
                player.getZ(),
                12,
                0.30,
                0.20,
                0.30,
                0.03);

        // Start the stream at once, then let the effect tick release the rest of
        // the lumps one by one, so they leave the mouth as a visible stream
        // instead of a single clump. Each one flies like a thrown item and then
        // rests on the ground. Ten lumps at level I up to eighteen at level V.
        int count = 8 + level * 2;
        CompoundTag data = player.getPersistentData();
        data.putInt(PUKE_STREAM_LEFT, Math.max(0, count - 1));
        data.putInt(PUKE_STREAM_NEXT, player.tickCount + PUKE_INTERVAL_TICKS);
        spawnPukeBlock(player);

        player.addEffect(new MobEffectInstance(
                MobEffects.HUNGER, 180 + level * 20, level >= 5 ? 1 : 0, true, true, true));
        player.addEffect(new MobEffectInstance(
                MobEffects.CONFUSION, 160 + level * 20, 0, true, true, true));
        player.causeFoodExhaustion(1.0F + level * 0.4F);
        player.displayClientMessage(Component.translatable("message.samogon.intoxication.vomit"), true);
    }

    /**
     * Releases the rest of the current vomit volley, one lump every
     * {@link #PUKE_INTERVAL_TICKS} ticks. Driving it from the effect tick (which
     * already runs every tick) keeps the lumps strictly sequential.
     */
    private static void tickPukeStream(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int left = data.getInt(PUKE_STREAM_LEFT);
        if (left <= 0) {
            return;
        }
        if (player.isDeadOrDying() || player.isSpectator()) {
            data.putInt(PUKE_STREAM_LEFT, 0);
            return;
        }
        if (player.tickCount < data.getInt(PUKE_STREAM_NEXT)) {
            return;
        }

        spawnPukeBlock(player);
        data.putInt(PUKE_STREAM_LEFT, left - 1);
        data.putInt(PUKE_STREAM_NEXT, player.tickCount + PUKE_INTERVAL_TICKS);
    }

    /**
     * Tosses one lump of soul sand out of the drinker's mouth exactly the way the
     * game throws a dropped item: forward along the look direction at {@code look
     * * 0.3} with a small upward kick. The lump is a real block ({@link PukeBlockEntity}),
     * so it can never be picked up, and it comes to rest on the ground before it
     * clears itself away.
     */
    private static void spawnPukeBlock(ServerPlayer player) {
        if (player.isRemoved() || !player.isAlive() || !(player.level() instanceof ServerLevel server)) {
            return;
        }
        RandomSource random = player.getRandom();

        Vec3 look = player.getLookAngle();
        // A little spread so a whole volley does not come out as one straight line.
        Vec3 direction = new Vec3(
                look.x + (random.nextDouble() - 0.5) * 0.25,
                look.y + (random.nextDouble() - 0.5) * 0.15,
                look.z + (random.nextDouble() - 0.5) * 0.25).normalize();

        // Start where a thrown item starts (eye height, nudged forward) so the
        // lump is in view the moment it appears instead of behind the camera.
        Vec3 spawn = player.getEyePosition()
                .add(0.0, -0.30, 0.0)
                .add(direction.scale(0.35));
        // The vanilla thrown-item toss: look * 0.3 plus a small upward kick.
        Vec3 velocity = new Vec3(direction.x * 0.3, direction.y * 0.3 + 0.1, direction.z * 0.3);

        server.addFreshEntity(
                PukeBlockEntity.toss(server, spawn, velocity, Blocks.SOUL_SAND.defaultBlockState()));
    }

    /**
     * At level V the drinker's world goes black after half a minute and they wake
     * up somewhere nearby. The timer resets each time it fires, so staying this
     * drunk keeps costing them.
     */
    private static void tickBlackout(ServerPlayer player, int level) {
        CompoundTag data = player.getPersistentData();
        if (level < MAX_LEVEL) {
            data.remove(BLACKOUT_TIMER);
            return;
        }
        int elapsed = data.getInt(BLACKOUT_TIMER) + 1;
        if (elapsed >= BLACKOUT_TICKS) {
            elapsed = 0;
            blackout(player);
        }
        data.putInt(BLACKOUT_TIMER, elapsed);
    }

    private static void blackout(ServerPlayer player) {
        ServerLevel server = player.serverLevel();
        server.playSound(null, player.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.9F, 0.55F);
        player.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS, BLACKOUT_FADE_TICKS + BLACKOUT_HOLD_TICKS, 0, true, false, false));
        PacketDistributor.sendToPlayer(player, new BlackoutPayload(BLACKOUT_FADE_TICKS, BLACKOUT_HOLD_TICKS));
        player.displayClientMessage(Component.translatable("message.samogon.intoxication.blackout"), false);

        // Teleport once the screen has finished fading to black, so the player
        // never sees the jump itself.
        MinecraftServer minecraftServer = server.getServer();
        minecraftServer.tell(new TickTask(minecraftServer.getTickCount() + BLACKOUT_FADE_TICKS, () -> {
            if (!player.isRemoved() && player.isAlive()) {
                teleportSomewhere(player);
            }
        }));
    }

    /** Moves the drinker to a random standable spot within {@link #BLACKOUT_RADIUS} blocks. */
    private static void teleportSomewhere(ServerPlayer player) {
        ServerLevel server = player.serverLevel();
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 5.0 + random.nextDouble() * (BLACKOUT_RADIUS - 5.0);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos landing = findLandingSpot(server, x, z);
            if (landing == null) {
                continue;
            }
            player.teleportTo(server,
                    landing.getX() + 0.5,
                    landing.getY(),
                    landing.getZ() + 0.5,
                    player.getYRot(),
                    player.getXRot());
            player.resetFallDistance();
            player.hurtMarked = true;
            server.playSound(null, landing, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 0.6F);
            return;
        }
    }

    private static BlockPos findLandingSpot(ServerLevel server, int x, int z) {
        BlockPos top = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        int minY = server.getMinBuildHeight() + 1;
        for (int y = Math.min(top.getY(), server.getMaxBuildHeight() - 2); y >= minY; y--) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (isStandable(server, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isStandable(ServerLevel server, BlockPos pos) {
        if (!server.getFluidState(pos.below()).isEmpty()) {
            return false;
        }
        if (server.getBlockState(pos.below()).getCollisionShape(server, pos.below()).isEmpty()) {
            return false;
        }
        if (!server.getFluidState(pos).isEmpty()) {
            return false;
        }
        BlockState feet = server.getBlockState(pos);
        BlockState head = server.getBlockState(pos.above());
        return feet.getCollisionShape(server, pos).isEmpty() && head.getCollisionShape(server, pos.above()).isEmpty();
    }

    /** Roman numeral for a 1..5 level; "-" for anything else. */
    public static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "-";
        };
    }
}
