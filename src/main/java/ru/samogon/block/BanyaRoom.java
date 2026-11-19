package ru.samogon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The air volume a banya stove heats.
 *
 * <p>The banya is any closed box of blocks, whatever those blocks are: stone,
 * planks, glass, doors, even leaves. The scan floods the air cells reachable
 * from the cell above the stove and stops at every non-air block, so the stove
 * only needs to sit anywhere on the floor of the box. A box that is not closed
 * (a hole, an open doorway, a broken roof) floods out of the room and is
 * rejected, and so is a box larger than the scan limit.</p>
 *
 * <p>The volume is the number of air cells inside; the stove uses it as the
 * room's heat capacity, so a small washroom warms up quickly and a big steam
 * room takes its time.</p>
 */
public record BanyaRoom(boolean valid, int volume, BlockPos min, BlockPos max) {
    public static final BanyaRoom NONE = new BanyaRoom(false, 0, BlockPos.ZERO, BlockPos.ZERO);
    /** Largest room that is still treated as a banya, in air cells. */
    public static final int DEFAULT_LIMIT = 8192;
    /** No banya may reach further than this from its stove along any axis. */
    private static final int MAX_REACH = 64;

    /** True when the block sits inside the enclosed air of the room. */
    public boolean contains(BlockPos pos) {
        return valid
                && pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /** Floods the air above the stove; {@link #NONE} when the box is open or too big. */
    public static BanyaRoom scan(Level level, BlockPos stove, int limit) {
        BlockPos seed = stove.above();
        if (!isAir(level, seed)) {
            return NONE;
        }
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(seed);
        queue.add(seed);

        int minX = seed.getX();
        int minY = seed.getY();
        int minZ = seed.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;

        while (!queue.isEmpty()) {
            BlockPos cell = queue.poll();
            minX = Math.min(minX, cell.getX());
            minY = Math.min(minY, cell.getY());
            minZ = Math.min(minZ, cell.getZ());
            maxX = Math.max(maxX, cell.getX());
            maxY = Math.max(maxY, cell.getY());
            maxZ = Math.max(maxZ, cell.getZ());

            for (Direction direction : Direction.values()) {
                BlockPos next = cell.relative(direction);
                if (!insideReach(next, stove) || next.getY() < level.getMinBuildHeight()
                        || next.getY() >= level.getMaxBuildHeight() || !level.isLoaded(next)) {
                    return NONE;
                }
                if (seen.contains(next) || !isAir(level, next)) {
                    continue;
                }
                seen.add(next);
                if (seen.size() > limit) {
                    return NONE;
                }
                queue.add(next);
            }
        }

        if (seen.size() < 4) {
            return NONE;
        }
        return new BanyaRoom(true, seen.size(), new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ));
    }

    private static boolean insideReach(BlockPos pos, BlockPos stove) {
        return Math.abs(pos.getX() - stove.getX()) <= MAX_REACH
                && Math.abs(pos.getY() - stove.getY()) <= MAX_REACH
                && Math.abs(pos.getZ() - stove.getZ()) <= MAX_REACH;
    }

    private static boolean isAir(Level level, BlockPos pos) {
        return level.getBlockState(pos).isAir();
    }
}