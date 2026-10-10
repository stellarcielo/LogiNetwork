package com.stellarcielo.loginetwork.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;

// Pipe-style cable (link). Connects to other cables and to any block exposing an item handler.
public class CableBlock extends PipeBlock {
    public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

    private static final float CABLE_SIZE = 6.0f;

    public CableBlock(Properties properties) {
        super(CABLE_SIZE, properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    protected MapCodec<CableBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = this.defaultBlockState();
        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = pos.relative(direction);
            boolean connect = canConnectTo(level, neighbourPos, level.getBlockState(neighbourPos), direction);
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connect);
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        boolean connect = canConnectTo(level, neighbourPos, neighbourState, directionToNeighbour);
        return state.setValue(PROPERTY_BY_DIRECTION.get(directionToNeighbour), connect);
    }

    private static boolean canConnectTo(LevelReader level, BlockPos neighbourPos, BlockState neighbourState, Direction directionToNeighbour) {
        if (neighbourState.getBlock() instanceof CableBlock) {
            return true;
        }
        // Capability lookup needs a full Level; other readers (e.g. worldgen regions) never connect to inventories.
        if (neighbourState.isAir() || !(level instanceof Level fullLevel)) {
            return false;
        }
        return fullLevel.getCapability(Capabilities.Item.BLOCK, neighbourPos, neighbourState, null, directionToNeighbour.getOpposite()) != null;
    }
}
