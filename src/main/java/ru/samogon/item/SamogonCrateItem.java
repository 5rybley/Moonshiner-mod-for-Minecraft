package ru.samogon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import ru.samogon.block.SamogonCrateBlockEntity;

import java.util.List;

/** Block item for the open crate: explains the click controls and stored bottle count. */
public final class SamogonCrateItem extends BlockItem {
    public SamogonCrateItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        int stored = data == null ? 0 : data.copyTag().getInt(SamogonCrateBlockEntity.BOTTLE_COUNT_KEY);
        if (stored > 0) {
            lines.add(Component.translatable("tooltip.samogon.crate.contents",
                    stored, SamogonCrateBlockEntity.SLOT_COUNT).withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("tooltip.samogon.crate")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.samogon.crate.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
