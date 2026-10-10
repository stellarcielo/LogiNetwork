package com.stellarcielo.loginetwork.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.stellarcielo.loginetwork.block.entity.DiscardChestBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// Overflow sink: when a device's input queue is full, the excess is sent to a discard chest in the same segment.
// It is never chosen as an ordinary destination.
public class DiscardChestBlock extends BaseEntityBlock {
    public static final MapCodec<DiscardChestBlock> CODEC = simpleCodec(DiscardChestBlock::new);

    public DiscardChestBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<DiscardChestBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DiscardChestBlockEntity blockEntity) {
            player.openMenu(blockEntity);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiscardChestBlockEntity(pos, state);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }
}
