package com.stellarcielo.loginetwork.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.stellarcielo.loginetwork.block.entity.RouterBlockEntity;
import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// Router: each face is a port into its own segment. Routes between segments using link-state routing.
public class RouterBlock extends BaseEntityBlock {
    public static final MapCodec<RouterBlock> CODEC = simpleCodec(RouterBlock::new);

    public RouterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<RouterBlock> codec() {
        return CODEC;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.ROUTER.get(), RouterBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof RouterBlockEntity blockEntity) {
            blockEntity.openScreen(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RouterBlockEntity(pos, state);
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
