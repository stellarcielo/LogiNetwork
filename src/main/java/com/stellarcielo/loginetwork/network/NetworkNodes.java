package com.stellarcielo.loginetwork.network;

import java.util.ArrayList;
import java.util.List;

import com.stellarcielo.loginetwork.block.AccessSwitchBlock;
import com.stellarcielo.loginetwork.block.CableBlock;
import com.stellarcielo.loginetwork.block.DhcpServerBlock;
import com.stellarcielo.loginetwork.block.NetworkInterfaceBlock;
import com.stellarcielo.loginetwork.block.RouterBlock;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

// Which blocks take part in a cable network, and which of their sides extract items.
public final class NetworkNodes {
    private NetworkNodes() {}

    public static boolean isNode(BlockState state) {
        return state.getBlock() instanceof CableBlock
                || state.getBlock() instanceof NetworkInterfaceBlock
                || state.getBlock() instanceof DhcpServerBlock
                || state.getBlock() instanceof AccessSwitchBlock
                || state.getBlock() instanceof RouterBlock;
    }

    public static List<Direction> extractSides(BlockState state) {
        List<Direction> sides = new ArrayList<>();
        if (state.getBlock() instanceof CableBlock) {
            for (Direction direction : Direction.values()) {
                if (state.getValue(CableBlock.EXTRACT_BY_DIRECTION.get(direction))) {
                    sides.add(direction);
                }
            }
        } else if (state.getBlock() instanceof NetworkInterfaceBlock && state.getValue(NetworkInterfaceBlock.EXTRACT)) {
            sides.add(state.getValue(NetworkInterfaceBlock.FACING));
        }
        return sides;
    }
}
