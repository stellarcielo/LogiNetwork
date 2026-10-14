package com.stellarcielo.loginetwork.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.stellarcielo.loginetwork.block.entity.DnsServerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// Name server. Like any machine it joins the network through a network interface placed against it;
// queries reach it at that interface's address.
public class DnsServerBlock extends BaseEntityBlock {
    public static final MapCodec<DnsServerBlock> CODEC = simpleCodec(DnsServerBlock::new);

    public DnsServerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<DnsServerBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof DnsServerBlockEntity blockEntity) {
            blockEntity.openScreen(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DnsServerBlockEntity(pos, state);
    }
}
