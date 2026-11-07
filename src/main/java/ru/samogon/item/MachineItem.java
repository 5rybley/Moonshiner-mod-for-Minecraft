package ru.samogon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import ru.samogon.block.ProcessBlock;

import java.util.List;
import java.util.Locale;

/** Block item with a short, kind-specific explanation of the machine's role. */
public final class MachineItem extends BlockItem {
    public MachineItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (getBlock() instanceof ProcessBlock machine) {
            String kind = machine.kind().name().toLowerCase(Locale.ROOT);
            lines.add(Component.translatable("tooltip.samogon.machine." + kind)
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("tooltip.samogon.machine.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
