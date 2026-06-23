package com.playsi.aero_cam_sync.client.utils;

import com.playsi.aero_cam_sync.client.config.Config;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Характеристики сабвела, на котором стоит игрок: масса, число блоков, габариты
 * (длина X / высота Y / ширина Z в блоках).
 *
 * <p>Габариты — O(1) (читаются из границ плота). Масса берётся готовой у Sable
 * ({@link SubLevelMassProvider}) — без ежесекундного пересчёта. Число блоков
 * (если порог включён) перебирается с редким кэшем, чтобы не фризить.</p>
 */
public final class SubLevelMetrics {

    private SubLevelMetrics() {}

    public record Metrics(double mass, int blocks, int length, int height, int width) {}

    private static final long BLOCKS_CACHE_MS = 5000;

    private static UUID blocksId = null;
    private static long blocksAtMs = Long.MIN_VALUE;
    private static int blocksCached = 0;

    public static Metrics get(ClientSubLevel sl) {
        BoundingBox3ic b = sl.getPlot().getBoundingBox();
        int length = b.maxX() - b.minX() + 1;
        int height = b.maxY() - b.minY() + 1;
        int width  = b.maxZ() - b.minZ() + 1;

        double mass = Config.GATE_MASS_ENABLED.get()
                ? SubLevelMassProvider.getMass(sl, b)
                : 0.0;

        int blocks = Config.GATE_BLOCKS_ENABLED.get()
                ? countBlocksCached(sl, b)
                : 0;

        return new Metrics(mass, blocks, length, height, width);
    }

    private static int countBlocksCached(ClientSubLevel sl, BoundingBox3ic b) {
        UUID id = sl.getUniqueId();
        long now = System.currentTimeMillis();
        if (id.equals(blocksId) && now - blocksAtMs < BLOCKS_CACHE_MS) {
            return blocksCached;
        }

        Level level = sl.getLevel();
        int count = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int y = b.minY(); y <= b.maxY(); y++)
                for (int z = b.minZ(); z <= b.maxZ(); z++)
                    if (!level.getBlockState(p.set(x, y, z)).isAir()) count++;

        blocksCached = count;
        blocksId = id;
        blocksAtMs = now;
        return count;
    }
}
