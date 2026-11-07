package ru.samogon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the vomited soul-sand lump as a <em>small</em> block — about the size of a
 * block that has been dropped on the ground — instead of the full one-block cube
 * the vanilla falling-block renderer would draw.
 */
@OnlyIn(Dist.CLIENT)
public class PukeBlockRenderer extends FallingBlockRenderer {
    /** Roughly the size of a dropped block item. */
    private static final float SCALE = 0.25F;

    public PukeBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(FallingBlockEntity entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // Shrink about the bottom-centre, so the lump still rests on the ground.
        poseStack.scale(SCALE, SCALE, SCALE);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
