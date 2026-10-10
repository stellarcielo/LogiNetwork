package com.stellarcielo.loginetwork.block;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import com.stellarcielo.loginetwork.network.NetworkManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;

// Pipe-style cable (link). Connects to other cables and to any block exposing an item handler.
// A side facing an inventory can be switched to extract mode (phase 3 stand-in until network interfaces exist).
public class CableBlock extends PipeBlock {
    public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

    public static final BooleanProperty EXTRACT_NORTH = BooleanProperty.create("extract_north");
    public static final BooleanProperty EXTRACT_EAST = BooleanProperty.create("extract_east");
    public static final BooleanProperty EXTRACT_SOUTH = BooleanProperty.create("extract_south");
    public static final BooleanProperty EXTRACT_WEST = BooleanProperty.create("extract_west");
    public static final BooleanProperty EXTRACT_UP = BooleanProperty.create("extract_up");
    public static final BooleanProperty EXTRACT_DOWN = BooleanProperty.create("extract_down");
    public static final Map<Direction, BooleanProperty> EXTRACT_BY_DIRECTION = ImmutableMap.of(
            Direction.NORTH, EXTRACT_NORTH,
            Direction.EAST, EXTRACT_EAST,
            Direction.SOUTH, EXTRACT_SOUTH,
            Direction.WEST, EXTRACT_WEST,
            Direction.UP, EXTRACT_UP,
            Direction.DOWN, EXTRACT_DOWN);

    private static final float CABLE_SIZE = 6.0f;
    private static final double CORE_HALF_SIZE = CABLE_SIZE / 32.0;

    public CableBlock(Properties properties) {
        super(CABLE_SIZE, properties);
        BlockState state = this.stateDefinition.any();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), false).setValue(EXTRACT_BY_DIRECTION.get(direction), false);
        }
        this.registerDefaultState(state);
    }

    @Override
    protected MapCodec<CableBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
        builder.add(EXTRACT_NORTH, EXTRACT_EAST, EXTRACT_SOUTH, EXTRACT_WEST, EXTRACT_UP, EXTRACT_DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    // Recomputes all six connections, keeping extract flags only on sides that still face an inventory.
    public static BlockState withConnections(BlockState state, Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = pos.relative(direction);
            BlockState neighbourState = level.getBlockState(neighbourPos);
            state = applyConnection(state, direction, canConnectTo(level, neighbourPos, neighbourState, direction), isCable(neighbourState));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        boolean connect = canConnectTo(level, neighbourPos, neighbourState, directionToNeighbour);
        return applyConnection(state, directionToNeighbour, connect, isCable(neighbourState));
    }

    private static BlockState applyConnection(BlockState state, Direction direction, boolean connect, boolean neighbourIsCable) {
        boolean extract = state.getValue(EXTRACT_BY_DIRECTION.get(direction)) && connect && !neighbourIsCable;
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), connect).setValue(EXTRACT_BY_DIRECTION.get(direction), extract);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Direction side = clickedSide(pos, hitResult);
        if (!state.getValue(PROPERTY_BY_DIRECTION.get(side)) || isCable(level.getBlockState(pos.relative(side)))) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            BooleanProperty property = EXTRACT_BY_DIRECTION.get(side);
            boolean extract = !state.getValue(property);
            level.setBlock(pos, state.setValue(property, extract), Block.UPDATE_ALL);
            player.sendOverlayMessage(Component.translatable(extract ? "message.loginetwork.cable.extract_on" : "message.loginetwork.cable.extract_off"));
        }
        return InteractionResult.SUCCESS;
    }

    // The arm under the cursor, or the clicked face when the cursor is on the core.
    private static Direction clickedSide(BlockPos pos, BlockHitResult hitResult) {
        Vec3 local = hitResult.getLocation().subtract(Vec3.atCenterOf(pos));
        Direction dominant = Direction.getApproximateNearest(local.x, local.y, local.z);
        double reach = Math.abs(local.get(dominant.getAxis()));
        return reach > CORE_HALF_SIZE + 1.0e-3 ? dominant : hitResult.getDirection();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            NetworkManager.get(serverLevel).onCableChanged(pos, state);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        NetworkManager.get(level).onCableRemoved(pos);
    }

    public static boolean isCable(BlockState state) {
        return state.getBlock() instanceof CableBlock;
    }

    public static boolean hasAnyExtract(BlockState state) {
        for (BooleanProperty property : EXTRACT_BY_DIRECTION.values()) {
            if (state.getValue(property)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canConnectTo(LevelReader level, BlockPos neighbourPos, BlockState neighbourState, Direction directionToNeighbour) {
        if (isCable(neighbourState)) {
            return true;
        }
        // Capability lookup needs a full Level; other readers (e.g. worldgen regions) never connect to inventories.
        if (neighbourState.isAir() || !(level instanceof Level fullLevel)) {
            return false;
        }
        return fullLevel.getCapability(Capabilities.Item.BLOCK, neighbourPos, neighbourState, null, directionToNeighbour.getOpposite()) != null;
    }
}
