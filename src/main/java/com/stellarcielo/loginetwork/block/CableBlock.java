package com.stellarcielo.loginetwork.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// Pipe-style cable (link). Only the center core for now; connections are added in phase 2.
public class CableBlock extends Block {
    public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

    private static final VoxelShape CORE_SHAPE = Block.cube(6.0);

    public CableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CORE_SHAPE;
    }
}
