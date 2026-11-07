package ru.samogon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import ru.samogon.block.SamogonCrateBlockEntity;

/**
 * Draws the bottles sitting inside an open-topped crate.
 *
 * <p>The four slots map onto a 2x2 grid so each added bottle appears in the
 * crate and each removed one leaves a gap. {@link ItemRenderer} centers an
 * item model on the pose origin, so the offsets below are the bottle centres
 * measured from the block corner. The bottle model is roughly 0.875 blocks
 * tall and is scaled by its own fixed transform (0.65), which the values here
 * account for so the bottles stand on the crate floor instead of sinking into
 * it.</p>
 */
public final class SamogonCrateRenderer implements BlockEntityRenderer<SamogonCrateBlockEntity> {
    /** Extra scale on top of the bottle model's own fixed transform (0.65). */
    private static final float EXTRA_SCALE = 0.9F;
    /** Bottle centres sit on the crate floor top (2/16) plus half the bottle height. */
    private static final float BASE_Y = 0.38F;
    private static final float NEAR = 0.3125F;
    private static final float FAR = 0.6875F;
    private static final float[][] SPOTS = {
            {NEAR, NEAR},
            {FAR, NEAR},
            {NEAR, FAR},
            {FAR, FAR}
    };

    public SamogonCrateRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SamogonCrateBlockEntity crate, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        for (int slot = 0; slot < SamogonCrateBlockEntity.SLOT_COUNT; slot++) {
            ItemStack stack = crate.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            pose.pushPose();
            pose.translate(SPOTS[slot][0], BASE_Y, SPOTS[slot][1]);
            pose.scale(EXTRA_SCALE, EXTRA_SCALE, EXTRA_SCALE);
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay,
                    pose, buffer, crate.getLevel(), (int) crate.getBlockPos().asLong());
            pose.popPose();
        }
    }
}
