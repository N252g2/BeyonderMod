package io.github.n252g2.beyonder.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * The city's facing blocks: shop awnings, the wall-bracket gas lamp, gravestones and the clock face.
 * {@code facing} is the direction the block shows its front (for wall-hung blocks: away from the wall).
 */
public class TingenFacingBlock extends HorizontalDirectionalBlock {
    public enum Kind {
        /** Sloping striped canopy hung from the wall above a shop front. */
        AWNING(true, box(0, 6, 0, 16, 16, 16)),
        /** Iron bracket and lantern off a wall. */
        WALL_LAMP(true, box(5.5, 2, 5.5, 10.5, 14.5, 16)),
        /** Rounded headstone on a plinth. */
        GRAVESTONE(false, box(2, 0, 4, 14, 14, 12)),
        /** A clock dial set into stone; a full block. */
        CLOCK(false, Shapes.block());

        final boolean wallHung;
        final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

        Kind(boolean wallHung, VoxelShape north) {
            this.wallHung = wallHung;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                shapes.put(d, rotate(north, d));
            }
        }
    }

    private final Kind kind;
    private final MapCodec<TingenFacingBlock> codec;

    public TingenFacingBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new TingenFacingBlock(p, kind));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = kind.wallHung && face.getAxis().isHorizontal()
                ? face
                : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind.shapes.get(state.getValue(FACING));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (kind != Kind.WALL_LAMP) {
            return;
        }
        // The lantern hangs at the middle of the block: same flame wisp and night moths as the street lamp.
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, cx + (random.nextDouble() - 0.5) * 0.06,
                    pos.getY() + 0.45, cz + (random.nextDouble() - 0.5) * 0.06, 0.0, 0.004, 0.0);
        }
        if (level.isNight() && random.nextInt(7) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 0.4 + random.nextDouble() * 0.3;
            level.addParticle(ParticleTypes.WHITE_ASH, cx + Math.cos(a) * r,
                    pos.getY() + 0.35 + random.nextDouble() * 0.5, cz + Math.sin(a) * r, 0.0, 0.0, 0.0);
        }
    }

    /** Turns a north-facing shape (model space) to face {@code to}, the way the blockstate turns the model. */
    private static VoxelShape rotate(VoxelShape north, Direction to) {
        if (to == Direction.NORTH) {
            return north;
        }
        VoxelShape[] out = {Shapes.empty()};
        int turns = to.get2DDataValue() - Direction.NORTH.get2DDataValue();   // S=0,W=1,N=2,E=3
        int quarter = Math.floorMod(turns, 4);                               // E=1, S=2, W=3 clockwise from N
        north.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            double ax1 = x1, az1 = z1, ax2 = x2, az2 = z2;
            for (int i = 0; i < quarter; i++) {
                // clockwise quarter turn seen from above: (x, z) -> (1 - z, x)
                double nx1 = 1 - az2, nz1 = ax1, nx2 = 1 - az1, nz2 = ax2;
                ax1 = nx1; az1 = nz1; ax2 = nx2; az2 = nz2;
            }
            out[0] = Shapes.or(out[0], Shapes.box(ax1, y1, az1, ax2, y2, az2));
        });
        return out[0];
    }
}
