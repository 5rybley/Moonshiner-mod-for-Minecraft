package ru.samogon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import ru.samogon.block.PlacedDrinkBlockEntity;

/**
 * Draws the drink stored by a placed-drink block at the centre of the block.
 *
 * <p>{@link ItemDisplayContext#NONE} is used on purpose: it skips the item's
 * display transforms, so the voxel model keeps its raw 0..16 model units and
 * its base ends up on the block floor once the pose is translated to the block
 * centre. The stored 16-step rotation turns the mug handle (+X in the model)
 * or the bottle label (north, -Z in the model) toward the placing player.</p>
 *
 * <p>The mug and bottle models are authored with their lowest face at y=1
 * model unit, so the pose is lowered by that one unit to put the base exactly
 * on the floor instead of letting the drink hover a pixel above it.</p>
 */
public final class PlacedDrinkRenderer implements BlockEntityRenderer<PlacedDrinkBlockEntity> {
    private static final float STEP_DEGREES = 360.0F / PlacedDrinkBlockEntity.ROTATION_STEPS;
    /** Model height of the lowest face above the floor, in block units. */
    private static final float BASE_OFFSET = 1.0F / 16.0F;

    public PlacedDrinkRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PlacedDrinkBlockEntity placed, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ItemStack drink = placed.drink();
        if (drink.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5F, 0.5F - BASE_OFFSET, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(placed.rotation() * STEP_DEGREES));
        Minecraft.getInstance().getItemRenderer().renderStatic(drink, ItemDisplayContext.NONE,
                packedLight, packedOverlay, pose, buffer, placed.getLevel(),
                (int) placed.getBlockPos().asLong());
        pose.popPose();
    }
}
