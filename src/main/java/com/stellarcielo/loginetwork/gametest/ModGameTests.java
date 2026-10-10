package com.stellarcielo.loginetwork.gametest;

import java.util.function.Consumer;

import com.stellarcielo.loginetwork.LogiNetwork;
import com.stellarcielo.loginetwork.block.CableBlock;
import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Server-side checks for cable connections and item transit. Run with `./gradlew runGameTestServer`.
// Layout (relative): chest A at x=0, cables at x=1..5, chest B at x=6, all at y=1, z=1.
public final class ModGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, LogiNetwork.MOD_ID);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CABLE_CONNECTIONS =
            TEST_FUNCTIONS.register("cable_connections", () -> ModGameTests::cableConnections);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TRANSFER_BETWEEN_CHESTS =
            TEST_FUNCTIONS.register("transfer_between_chests", () -> ModGameTests::transferBetweenChests);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BROKEN_CABLE_KEEPS_ITEMS =
            TEST_FUNCTIONS.register("broken_cable_keeps_items", () -> ModGameTests::brokenCableKeepsItems);

    private static final Identifier STRUCTURE = Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "empty_7x3x3");

    private static final BlockPos CHEST_A = new BlockPos(0, 1, 1);
    private static final BlockPos CHEST_B = new BlockPos(6, 1, 1);
    private static final int FIRST_CABLE_X = 1;
    private static final int LAST_CABLE_X = 5;

    private ModGameTests() {}

    public static void register(IEventBus modEventBus) {
        TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(ModGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "default"));
        registerTest(event, environment, CABLE_CONNECTIONS, 20);
        registerTest(event, environment, TRANSFER_BETWEEN_CHESTS, 200);
        registerTest(event, environment, BROKEN_CABLE_KEEPS_ITEMS, 200);
    }

    private static void registerTest(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment,
            DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function, int maxTicks) {
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, STRUCTURE, maxTicks, 0, true, Rotation.NONE, false, 1, 1, false, 0);
        event.registerTest(function.getId(), new FunctionGameTestInstance(function.getKey(), data));
    }

    private static void cableConnections(GameTestHelper helper) {
        helper.setBlock(CHEST_A, Blocks.CHEST);
        placeCable(helper, new BlockPos(1, 1, 1));
        placeCable(helper, new BlockPos(2, 1, 1));
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.STONE);

        BlockState first = helper.getBlockState(new BlockPos(1, 1, 1));
        helper.assertTrue(first.getValue(CableBlock.WEST), "cable should connect to the chest");
        helper.assertTrue(first.getValue(CableBlock.EAST), "cable should connect to the neighbouring cable");
        helper.assertTrue(!first.getValue(CableBlock.UP), "cable should not connect to stone");
        helper.assertTrue(!first.getValue(CableBlock.SOUTH), "cable should not connect to air");
        helper.succeed();
    }

    private static void transferBetweenChests(GameTestHelper helper) {
        buildLine(helper);
        chest(helper, CHEST_A).setItem(0, new ItemStack(Items.IRON_INGOT, 16));

        helper.succeedWhen(() -> {
            helper.assertValueEqual(chest(helper, CHEST_B).countItem(Items.IRON_INGOT), 16, "iron in chest B");
            helper.assertValueEqual(chest(helper, CHEST_A).countItem(Items.IRON_INGOT), 0, "iron in chest A");
        });
    }

    private static void brokenCableKeepsItems(GameTestHelper helper) {
        buildLine(helper);
        chest(helper, CHEST_A).setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        BlockPos firstCable = helper.absolutePos(new BlockPos(FIRST_CABLE_X, 1, 1));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(inTransit(helper, firstCable) > 0, "waiting for items to enter the cable"))
                .thenExecute(() -> helper.destroyBlock(new BlockPos(4, 1, 1)))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(inTransit(helper, firstCable), 0, "items still in transit");
                    int total = chest(helper, CHEST_A).countItem(Items.IRON_INGOT)
                            + chest(helper, CHEST_B).countItem(Items.IRON_INGOT)
                            + droppedIron(helper);
                    helper.assertValueEqual(total, 8, "iron in chests and on the ground");
                })
                .thenSucceed();
    }

    // Chest A -> five cables -> chest B, with the first cable extracting from chest A.
    private static void buildLine(GameTestHelper helper) {
        helper.setBlock(CHEST_A, Blocks.CHEST);
        helper.setBlock(CHEST_B, Blocks.CHEST);
        for (int x = FIRST_CABLE_X; x <= LAST_CABLE_X; x++) {
            placeCable(helper, new BlockPos(x, 1, 1));
        }
        BlockPos first = new BlockPos(FIRST_CABLE_X, 1, 1);
        helper.setBlock(first, helper.getBlockState(first).setValue(CableBlock.EXTRACT_WEST, true));
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        BlockState state = CableBlock.withConnections(ModBlocks.COPPER_CABLE.get().defaultBlockState(), helper.getLevel(), helper.absolutePos(pos));
        helper.setBlock(pos, state);
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, ChestBlockEntity.class);
    }

    private static int inTransit(GameTestHelper helper, BlockPos absoluteCable) {
        return NetworkManager.get(helper.getLevel()).packets().stream()
                .filter(packet -> packet.nodes().contains(absoluteCable))
                .mapToInt(packet -> packet.stack().getCount())
                .sum();
    }

    private static int droppedIron(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0)).stream()
                .filter(entity -> entity.getItem().is(Items.IRON_INGOT))
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
    }
}
