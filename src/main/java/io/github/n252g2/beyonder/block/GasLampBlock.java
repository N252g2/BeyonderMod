package io.github.n252g2.beyonder.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Tingen street gas lamp: an iron-framed glass lantern with a live flame.
 * Sits on top of a {@link LampPostBlock} or on any surface.
 */
public class GasLampBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 14.0, 12.0);

    public GasLampBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // The flame breathes: an occasional wisp rises from the burner.
        if (random.nextInt(5) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.08;
            double y = pos.getY() + 0.42;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.08;
            level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0.0, 0.004, 0.0);
        }
        // Moths at night, drawn to the light.
        if (level.isNight() && random.nextInt(6) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 0.45 + random.nextDouble() * 0.35;
            level.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + 0.5 + Math.cos(a) * r,
                    pos.getY() + 0.4 + random.nextDouble() * 0.6, pos.getZ() + 0.5 + Math.sin(a) * r, 0.0, 0.0, 0.0);
        }
    }
}
