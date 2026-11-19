package ru.samogon.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import ru.samogon.SamogonMod;
import ru.samogon.registry.ModSounds;

/**
 * A birch banya besom: a bundle of leafy switches bound to a short handle.
 *
 * <p>It is a light weapon. A blow lands with the wet slap of leaves and the
 * short hiss of water flashing off hot birch, and a small puff of steam marks
 * the spot. It is too soft to be a real sword, but it is fast, it knocks the
 * target back and it wears out as the leaves are stripped away.</p>
 */
public class BesomItem extends Item {
    /** Bare-fist damage plus one and a half hearts. */
    private static final double ATTACK_DAMAGE = 1.5;
    /** Light and quick: about two and a half blows a second. */
    private static final double ATTACK_SPEED = -1.8;
    /** Leaves shove rather than cut. */
    private static final double ATTACK_KNOCKBACK = 0.4;

    public BesomItem(Properties properties) {
        super(properties);
    }

    /** Damage, speed and knockback of the besom, applied while it is held. */
    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, ATTACK_DAMAGE,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, ATTACK_SPEED,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_KNOCKBACK,
                        new AttributeModifier(
                                ResourceLocation.fromNamespaceAndPath(SamogonMod.MOD_ID, "besom_knockback"),
                                ATTACK_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** A besom slaps; it is never the right tool for breaking a block. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    /**
     * Plays the besom's own sound and a wisp of steam at the point of contact:
     * the wet slap of the switches and the short hiss of water flashing off hot
     * birch. The stack still wears by one point per blow, like any other weapon.
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.level() instanceof ServerLevel server) {
            server.playSound(null, target.getX(), target.getY(0.6), target.getZ(),
                    ModSounds.BESOM_HIT.get(), SoundSource.PLAYERS,
                    0.9F, 0.85F + server.getRandom().nextFloat() * 0.3F);
            server.sendParticles(ParticleTypes.CLOUD,
                    target.getX(), target.getY(0.65), target.getZ(),
                    5, 0.25, 0.3, 0.25, 0.02);
        }
        return true;
    }

    /**
     * Takes the durability point for a landed blow. In 1.21.1 the wear happens
     * in {@code postHurtEnemy}, not in {@code hurtEnemy}: the plain {@link Item}
     * hook is empty and only swords override it, so without this the besom's
     * ninety-six points of durability would never be spent at all.
     */
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.samogon.besom").withStyle(ChatFormatting.GRAY));
    }
}