package ru.samogon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import ru.samogon.block.SamogonCrateBlockEntity;
import ru.samogon.registry.ModBlocks;

/**
 * Renders the crate item as the real crate with the bottles it carries.
 *
 * <p>A plain model cannot show an arbitrary set of stored bottles, so the item
 * uses a custom renderer: it draws the block model and then places each stored
 * bottle on the same 2x2 grid the block entity renderer uses in the world. The
 * stored stacks live in the item's {@code BlockEntityData} component, which is
 * written when a filled crate is broken.</p>
 */
public final class SamogonCrateItemRenderer extends BlockEntityWithoutLevelRenderer {
    /** Kept in sync with {@link SamogonCrateRenderer} so the item matches the world. */
    private static final float EXTRA_SCALE = 0.9F;
    /**
     * Bottles are lifted for the icon: at the world height their tops stay below
     * the rim, and the small GUI view is too shallow to show the crate interior,
     * so the necks would be invisible. Here the shoulders sit at the rim and the
     * necks and corks peek just above it.
     */
    private static final float ICON_BASE_Y = 0.68F;
    private static final float NEAR = 0.3125F;
    private static final float FAR = 0.6875F;
    private static final float[][] SPOTS = {
            {NEAR, NEAR},
            {FAR, NEAR},
            {NEAR, FAR},
            {FAR, FAR}
    };

    private static SamogonCrateItemRenderer instance;

    private SamogonCrateItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    /** Lazily created: the renderer may only be built once the client is up. */
    public static SamogonCrateItemRenderer get() {
        if (instance == null) {
            instance = new SamogonCrateItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockState state = ModBlocks.SAMOGON_CRATE.get().defaultBlockState();
        minecraft.getBlockRenderer().renderSingleBlock(state, pose, buffer, packedLight, packedOverlay,
                ModelData.EMPTY, null);

        NonNullList<ItemStack> bottles = readContents(stack, minecraft);
        if (bottles == null) {
            return;
        }
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        for (int slot = 0; slot < SPOTS.length; slot++) {
            ItemStack bottle = bottles.get(slot);
            // Only real bottles are drawn; this also keeps a forged crate inside
            // a crate from recursing into this renderer.
            if (!SamogonCrateBlockEntity.isBottle(bottle)) {
                continue;
            }
            pose.pushPose();
            // Block-space coordinates: the crate is drawn at 0..1 here just like
            // in the world, and renderStatic centres the bottle model itself.
            pose.translate(SPOTS[slot][0], ICON_BASE_Y, SPOTS[slot][1]);
            pose.scale(EXTRA_SCALE, EXTRA_SCALE, EXTRA_SCALE);
            itemRenderer.renderStatic(bottle, ItemDisplayContext.FIXED, packedLight, packedOverlay,
                    pose, buffer, minecraft.level, slot);
            pose.popPose();
        }
    }

    /** Reads the bottles stored on a dropped crate item, or null when it is empty. */
    private static NonNullList<ItemStack> readContents(ItemStack stack, Minecraft minecraft) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) {
            return null;
        }
        HolderLookup.Provider registries = registries(minecraft);
        if (registries == null) {
            return null;
        }
        NonNullList<ItemStack> bottles =
                NonNullList.withSize(SamogonCrateBlockEntity.SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(data.copyTag(), bottles, registries);
        return bottles;
    }

    private static HolderLookup.Provider registries(Minecraft minecraft) {
        Level level = minecraft.level;
        if (level != null) {
            return level.registryAccess();
        }
        return minecraft.getConnection() != null ? minecraft.getConnection().registryAccess() : null;
    }
}
