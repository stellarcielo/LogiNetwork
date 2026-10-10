package com.stellarcielo.loginetwork.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.stellarcielo.loginetwork.block.entity.AccessSwitchBlockEntity;
import com.stellarcielo.loginetwork.network.NetworkManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// Single-block access switch: forwards within the segment on all six sides and learns which MAC sits behind each port.
public class AccessSwitchBlock extends BaseEntityBlock {
    public static final MapCodec<AccessSwitchBlock> CODEC = simpleCodec(AccessSwitchBlock::new);

    public AccessSwitchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<AccessSwitchBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof AccessSwitchBlockEntity blockEntity) {
            blockEntity.openScreen(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AccessSwitchBlockEntity(pos, state);
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
