package ru.samogon.process;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Reads the local climate and the heat source below a machine. */
public final class Environment {
    public static double temperature(Level level, BlockPos pos) {
        double base = level.dimension() == Level.NETHER ? 40
                : level.dimension() == Level.END ? 10
                : 5 + level.getBiome(pos).value().getBaseTemperature() * 20;
        int heat = 0;
        int cold = 0;
        for (BlockPos cursor : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
            BlockState state = level.getBlockState(cursor);
            if (isHeatSource(state)) heat++;
            if (isColdSource(state)) cold++;
        }
        base += Math.min(30, heat * 6);
        base -= Math.min(18, cold * 3);
        if (level.canSeeSky(pos.above()) && level.isRainingAt(pos.above())) base -= 3;
        if (level.getDayTime() % 24000 > 13000) base -= 2;
        if (!level.canSeeSky(pos.above())) base += 2;
        return Simulation.clamp(base, -15, 55);
    }

    /** Strength of a heat source directly below a pot, mash tun or column. */
    public static double heatStrength(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isLitCampfire(state)) return state.is(Blocks.SOUL_CAMPFIRE) ? 0.95 : 0.85;
        if (state.is(Blocks.FIRE)) return 1.0;
        if (state.is(Blocks.SOUL_FIRE)) return 1.1;
        if (state.is(Blocks.LAVA)) return 1.2;
        if (state.is(Blocks.MAGMA_BLOCK)) return 0.7;
        if (isLitFurnace(state)) return 0.65;
        return 0;
    }

    public static boolean isHeatSource(BlockState state) {
        return isLitCampfire(state) || isLitFurnace(state) || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK);
    }

    private static boolean isLitCampfire(BlockState state) {
        return (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE))
                && state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT);
    }

    private static boolean isLitFurnace(BlockState state) {
        return (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))
                && state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT);
    }

    private static boolean isColdSource(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW);
    }

    private Environment() {
    }
}
