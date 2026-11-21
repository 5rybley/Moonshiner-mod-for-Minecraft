package ru.samogon.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.samogon.SamogonMod;
import ru.samogon.config.SamogonConfig;
import ru.samogon.registry.ModEffects;

/**
 * High-level intoxication bonuses: drunken damage negation, a chance for a
 * critical strike, the passive perks of the tincture affinities, and the
 * hangover that follows once the drinker sobers up.
 */
@Mod.EventBusSubscriber(modid = SamogonMod.MOD_ID)
public final class IntoxicationEvents {
    private IntoxicationEvents() {}

    /**
     * Watches the transition from drunk to sober. A session that peaked at
     * level III or higher leaves a hangover scaled to that peak; a session
     * ended by brine has already cleared the tracker and is left alone.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        int level = Intoxication.level(player);
        if (level > 0) {
            if (!Intoxication.cured(player)) {
                Intoxication.trackPeak(player, level);
            }
            return;
        }

        int peak = Intoxication.peakLevel(player);
        Intoxication.clearCured(player);
        // Sober again: drop the drink's auras, the hue marker and any vomit
        // volley still queued, so the next bottle starts clean and no stale
        // beneficial effect (or leftover lump) lingers.
        Intoxication.clearAuras(player);
        Intoxication.clearPukeStream(player);
        // A session that reached level V ends with the blackout: the screen
        // fades to black, "you drank too much" is shown and the drinker comes to
        // somewhere nearby. It fires once, here, instead of on a timer while
        // drunk.
        if (peak >= Intoxication.MAX_LEVEL
                && SamogonConfig.blackoutEnabled()
                && player.isAlive() && !player.isSpectator()) {
            Intoxication.blackout(player);
        }
        if (peak >= 3 && SamogonConfig.hangoverEnabled()
                && SamogonConfig.hangoverDurationMultiplier() > 0.0D) {
            applyHangover(player, peak);
        }
        Intoxication.clearPeak(player);
    }

    /**
     * Death is the only cure for the fusel fog. Vanilla usually drops every
     * effect on respawn, but a world that keeps the inventory would carry it
     * over, so it is lifted here the moment the drinker dies.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.removeEffect(ModEffects.holder(ModEffects.FUSEL_BLINDNESS));
        }
    }

    private static void applyHangover(ServerPlayer player, int peak) {
        int duration = (int) Math.round((60 + 30 * peak) * 20 * SamogonConfig.hangoverDurationMultiplier());
        int amplifier = peak >= 5 ? 1 : 0;
        player.addEffect(new MobEffectInstance(
                ModEffects.holder(ModEffects.HANGOVER), duration, amplifier, false, true, true));
        player.displayClientMessage(
                Component.translatable(amplifier > 0
                        ? "message.samogon.hangover.severe" : "message.samogon.hangover"),
                false);

        if (player.level() instanceof ServerLevel server) {
            server.sendParticles(
                    ParticleTypes.ITEM_SLIME,
                    player.getX(),
                    player.getY() + 1.2,
                    player.getZ(),
                    14,
                    0.35,
                    0.4,
                    0.35,
                    0.02);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        int level = Intoxication.level(player);
        if (level < 1 || player.isCreative() || player.isSpectator()) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        if (level >= 4
                && event.getSource().getDirectEntity() instanceof AbstractArrow
                && player.getRandom().nextFloat() < Intoxication.arrowDeflectChance(level)) {
            // Cancelling the hurt makes the arrow use its normal rebound trajectory.
            event.setCanceled(true);
            if (player.level() instanceof ServerLevel server) {
                server.sendParticles(
                        ParticleTypes.ENCHANTED_HIT,
                        player.getX(),
                        player.getY() + 1.0,
                        player.getZ(),
                        8,
                        0.25,
                        0.35,
                        0.25,
                        0.08);
                server.playSound(
                        null,
                        player.blockPosition(),
                        SoundEvents.SHIELD_BLOCK,
                        SoundSource.PLAYERS,
                        0.8F,
                        1.25F);
            }
            player.displayClientMessage(
                    Component.translatable("message.samogon.intoxication.deflect"),
                    true);
            return;
        }

        float chance = Intoxication.blockChance(level);
        int honeyLevel = Intoxication.auraAmplifier(player, "honey");
        if (honeyLevel >= 0) {
            chance = Math.min(0.55F, chance + 0.10F + 0.02F * honeyLevel);
        }

        if (player.getRandom().nextFloat() < chance) {
            event.setCanceled(true);
            if (player.level() instanceof ServerLevel server) {
                server.sendParticles(
                        ParticleTypes.ENCHANTED_HIT,
                        player.getX(),
                        player.getY() + 1.0,
                        player.getZ(),
                        12,
                        0.35,
                        0.5,
                        0.35,
                        0.06);
                server.playSound(
                        null,
                        player.blockPosition(),
                        SoundEvents.SHIELD_BLOCK,
                        SoundSource.PLAYERS,
                        0.8F,
                        0.85F + player.getRandom().nextFloat() * 0.2F);
            }
            player.displayClientMessage(
                    Component.translatable("message.samogon.intoxication.blocked"),
                    true);
            return;
        }

        int oakLevel = Intoxication.auraAmplifier(player, "oak");
        if (oakLevel >= 0 && event.getAmount() > 0.0F) {
            float keep = Math.max(0.70F, 0.90F - 0.02F * oakLevel);
            event.setAmount(event.getAmount() * keep);
        }
    }

    @SubscribeEvent
    public static void onOutgoingDamage(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getEntity() == player || event.getEntity().level().isClientSide()) {
            return;
        }

        int level = Intoxication.level(player);
        if (level < 1 || player.isCreative() || player.isSpectator()) {
            return;
        }

        boolean direct = event.getSource().getDirectEntity() == player;
        int spiceLevel = Intoxication.auraAmplifier(player, "spice");
        boolean ember = direct && spiceLevel >= 0;

        float multiplier = 1.0F;
        int berryLevel = Intoxication.auraAmplifier(player, "berry");
        if (direct && berryLevel >= 0) {
            multiplier += 0.20F + 0.05F * berryLevel;
        }

        boolean critical = player.getRandom().nextFloat() < Intoxication.attackChance(level);
        if (critical) {
            multiplier += 0.50F + 0.25F * (level - 3);
        }
        if (multiplier <= 1.001F && !ember) {
            return;
        }

        LivingEntity victim = event.getEntity();
        if (multiplier > 1.001F) {
            event.setAmount(event.getAmount() * multiplier);
        }

        // Spiced Ember: the drink itself burns, so even bare fists carry fire.
        if (ember && victim.getRemainingFireTicks() < 60) {
            victim.setRemainingFireTicks(60 + 20 * spiceLevel);
            if (victim.level() instanceof ServerLevel server) {
                server.sendParticles(
                        ParticleTypes.FLAME,
                        victim.getX(),
                        victim.getY() + victim.getBbHeight() * 0.6,
                        victim.getZ(),
                        8,
                        0.25,
                        0.3,
                        0.25,
                        0.02);
            }
        }

        if (critical) {
            if (victim.level() instanceof ServerLevel server) {
                server.sendParticles(
                        ParticleTypes.CRIT,
                        victim.getX(),
                        victim.getY() + victim.getBbHeight() * 0.5,
                        victim.getZ(),
                        10,
                        0.3,
                        0.3,
                        0.3,
                        0.15);
                server.playSound(
                        null,
                        victim.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_CRIT,
                        SoundSource.PLAYERS,
                        0.75F,
                        1.0F);
            }
            player.displayClientMessage(
                    Component.translatable("message.samogon.intoxication.smash"),
                    true);
        }
    }
}
