package io.github.n252g2.beyonder.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A soot-brick chimney stack. Smoke rises from it whenever it has open sky above. */
public class ChimneyBlock extends Block {
    public ChimneyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.above()).isAir() || random.nextInt(3) != 0) {
            return;
        }
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
        level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x, pos.getY() + 1.05, z, 0.0, 0.05, 0.0);
    }
}
