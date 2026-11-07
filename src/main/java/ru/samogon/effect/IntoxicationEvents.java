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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.samogon.SamogonMod;
import ru.samogon.registry.ModEffects;

/**
 * High-level intoxication bonuses: drunken damage negation, a chance for a
 * critical strike, the passive perks of the tincture affinities, and the
 * hangover that follows once the drinker sobers up.
 */
@EventBusSubscriber(modid = SamogonMod.MOD_ID)
public final class IntoxicationEvents {
    private IntoxicationEvents() {}

    /**
     * Watches the transition from drunk to sober. A session that peaked at
     * level III or higher leaves a hangover scaled to that peak; a session
     * ended by brine has already cleared the tracker and is left alone.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
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
        Intoxication.clearBlackoutTimer(player);
        if (peak >= 3) {
            applyHangover(player, peak);
        }
        Intoxication.clearPeak(player);
    }

    private static void applyHangover(ServerPlayer player, int peak) {
        int duration = (60 + 30 * peak) * 20;
        int amplifier = peak >= 5 ? 1 : 0;
        player.addEffect(new MobEffectInstance(
                ModEffects.HANGOVER, duration, amplifier, false, true, true));
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
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        int level = Intoxication.level(player);
        if (level < 3 || player.isCreative() || player.isSpectator()) {
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
        if (player.hasEffect(ModEffects.HONEY_GUARD)) {
            chance = Math.min(0.45F, chance + 0.10F);
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

        if (player.hasEffect(ModEffects.OAK_GRIT) && event.getAmount() > 0.0F) {
            event.setAmount(event.getAmount() * 0.90F);
        }
    }

    @SubscribeEvent
    public static void onOutgoingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getEntity() == player || event.getEntity().level().isClientSide()) {
            return;
        }

        int level = Intoxication.level(player);
        if (level < 3 || player.isCreative() || player.isSpectator()) {
            return;
        }

        boolean direct = event.getSource().getDirectEntity() == player;
        boolean ember = direct && player.hasEffect(ModEffects.SPICED_EMBER);

        float multiplier = 1.0F;
        if (direct && player.hasEffect(ModEffects.BERRY_FURY)) {
            multiplier += 0.20F + 0.05F * (level - 3);
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
            event.setNewDamage(event.getNewDamage() * multiplier);
        }

        // Spiced Ember: the drink itself burns, so even bare fists carry fire.
        if (ember && victim.getRemainingFireTicks() < 60) {
            victim.setRemainingFireTicks(60);
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
