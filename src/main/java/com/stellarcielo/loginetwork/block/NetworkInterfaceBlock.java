package com.stellarcielo.loginetwork.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.stellarcielo.loginetwork.block.entity.NetworkInterfaceBlockEntity;
import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

// Turns the inventory in front of it into a network host with its own address.
// FACING points at the inventory; cables connect on the other five sides.
public class NetworkInterfaceBlock extends BaseEntityBlock {
    public static final MapCodec<NetworkInterfaceBlock> CODEC = simpleCodec(NetworkInterfaceBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty EXTRACT = BooleanProperty.create("extract");

    public NetworkInterfaceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EXTRACT, false));
    }

    @Override
    protected MapCodec<NetworkInterfaceBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, EXTRACT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face the block that was clicked, so placing against a chest attaches to it.
        return this.defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    // Whether a cable on the given side of this interface may connect (everything but the front).
    public static boolean acceptsCableFrom(BlockState state, Direction sideOfInterface) {
        return state.getValue(FACING) != sideOfInterface;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof NetworkInterfaceBlockEntity blockEntity) {
            blockEntity.openScreen(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkInterfaceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.NETWORK_INTERFACE.get(), NetworkInterfaceBlockEntity::serverTick);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            NetworkManager.get(serverLevel).onNodeChanged(pos, state);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        NetworkManager.get(level).onNodeRemoved(pos);
    }
}
