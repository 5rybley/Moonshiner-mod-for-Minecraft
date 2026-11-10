package ru.samogon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Block item for the brewery multiblock, with a short explanation of its chain. */
public final class BreweryItem extends BlockItem {
    public BreweryItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.samogon.brewery").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.samogon.machine.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
