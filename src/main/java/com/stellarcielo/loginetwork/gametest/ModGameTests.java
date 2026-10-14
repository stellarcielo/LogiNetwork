package com.stellarcielo.loginetwork.gametest;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.stellarcielo.loginetwork.LogiNetwork;
import com.stellarcielo.loginetwork.address.Ipv4Address;
import com.stellarcielo.loginetwork.address.Ipv4Interface;
import com.stellarcielo.loginetwork.block.CableBlock;
import com.stellarcielo.loginetwork.block.NetworkInterfaceBlock;
import com.stellarcielo.loginetwork.block.entity.AccessSwitchBlockEntity;
import com.stellarcielo.loginetwork.block.entity.DhcpServerBlockEntity;
import com.stellarcielo.loginetwork.block.entity.DiscardChestBlockEntity;
import com.stellarcielo.loginetwork.block.entity.InterfaceStatus;
import com.stellarcielo.loginetwork.block.entity.NetworkInterfaceBlockEntity;
import com.stellarcielo.loginetwork.block.entity.RouterBlockEntity;
import com.stellarcielo.loginetwork.network.NetworkManager;
import com.stellarcielo.loginetwork.network.TransitPacket;
import com.stellarcielo.loginetwork.network.observe.Observation;
import com.stellarcielo.loginetwork.network.routing.RoutingService;
import com.stellarcielo.loginetwork.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
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

// Server-side checks for cable connections, item transit and addressing. Run with `./gradlew runGameTestServer`.
// Layout (relative): chest A at x=0, cables at x=1..5, chest B at x=6, all at y=1, z=1.
public final class ModGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, LogiNetwork.MOD_ID);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CABLE_CONNECTIONS =
            TEST_FUNCTIONS.register("cable_connections", () -> ModGameTests::cableConnections);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TRANSFER_BETWEEN_CHESTS =
            TEST_FUNCTIONS.register("transfer_between_chests", () -> ModGameTests::transferBetweenChests);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BROKEN_CABLE_KEEPS_ITEMS =
            TEST_FUNCTIONS.register("broken_cable_keeps_items", () -> ModGameTests::brokenCableKeepsItems);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> DHCP_ASSIGNS_UNIQUE_ADDRESSES =
            TEST_FUNCTIONS.register("dhcp_assigns_unique_addresses", () -> ModGameTests::dhcpAssignsUniqueAddresses);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> STATIC_CONFLICT_DETECTED =
            TEST_FUNCTIONS.register("static_conflict_detected", () -> ModGameTests::staticConflictDetected);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> DHCP_LEASE_RELEASED_ON_REMOVAL =
            TEST_FUNCTIONS.register("dhcp_lease_released_on_removal", () -> ModGameTests::dhcpLeaseReleasedOnRemoval);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TRANSFER_VIA_INTERFACES =
            TEST_FUNCTIONS.register("transfer_via_interfaces", () -> ModGameTests::transferViaInterfaces);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> REQUEST_SEQUENCE_DELIVERS =
            TEST_FUNCTIONS.register("request_sequence_delivers", () -> ModGameTests::requestSequenceDelivers);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> REQUEST_SPLITS_BETWEEN_SUPPLIERS =
            TEST_FUNCTIONS.register("request_splits_between_suppliers", () -> ModGameTests::requestSplitsBetweenSuppliers);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> NO_DOUBLE_RESERVATION =
            TEST_FUNCTIONS.register("no_double_reservation", () -> ModGameTests::noDoubleReservation);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TIMEOUT_REREQUESTS =
            TEST_FUNCTIONS.register("timeout_rerequests", () -> ModGameTests::timeoutRerequests);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ROUTED_DELIVERY =
            TEST_FUNCTIONS.register("routed_delivery", () -> ModGameTests::routedDelivery);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> DETOUR_AFTER_LINK_BREAK =
            TEST_FUNCTIONS.register("detour_after_link_break", () -> ModGameTests::detourAfterLinkBreak);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CROSS_SEGMENT_REQUEST =
            TEST_FUNCTIONS.register("cross_segment_request", () -> ModGameTests::crossSegmentRequest);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> WITHDRAW_WHEN_SOLD_OUT =
            TEST_FUNCTIONS.register("withdraw_when_sold_out", () -> ModGameTests::withdrawWhenSoldOut);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> STATIC_ROUTE_PREFERRED =
            TEST_FUNCTIONS.register("static_route_preferred", () -> ModGameTests::staticRoutePreferred);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> DHCP_POOLS_DO_NOT_OVERLAP =
            TEST_FUNCTIONS.register("dhcp_pools_do_not_overlap", () -> ModGameTests::dhcpPoolsDoNotOverlap);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> FIBER_IS_FASTER =
            TEST_FUNCTIONS.register("fiber_is_faster", () -> ModGameTests::fiberIsFaster);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SWITCH_QUEUES_UNDER_LOAD =
            TEST_FUNCTIONS.register("switch_queues_under_load", () -> ModGameTests::switchQueuesUnderLoad);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> OVERFLOW_TO_DISCARD_CHEST =
            TEST_FUNCTIONS.register("overflow_to_discard_chest", () -> ModGameTests::overflowToDiscardChest);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BACKPRESSURE_KEEPS_ITEMS =
            TEST_FUNCTIONS.register("backpressure_keeps_items", () -> ModGameTests::backpressureKeepsItems);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> HIGH_PRIORITY_OVERTAKES =
            TEST_FUNCTIONS.register("high_priority_overtakes", () -> ModGameTests::highPriorityOvertakes);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TRACEROUTE_LISTS_ROUTERS =
            TEST_FUNCTIONS.register("traceroute_lists_routers", () -> ModGameTests::tracerouteListsRouters);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CAPTURE_RECORDS_TRAFFIC =
            TEST_FUNCTIONS.register("capture_records_traffic", () -> ModGameTests::captureRecordsTraffic);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ECMP_SPREADS_PACKETS =
            TEST_FUNCTIONS.register("ecmp_spreads_packets", () -> ModGameTests::ecmpSpreadsPackets);

    private static final Identifier STRUCTURE = Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "empty_7x3x3");

    // Protocol layout (7x3x5): requester chest/interface at x=0/1, cables at x=2 and x=4 with a switch at x=3,
    // supplier 1 interface/chest at x=5/6, all on z=2; supplier 2 hangs off the x=2 cable towards the north.
    private static final Identifier PROTOCOL_STRUCTURE = Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "empty_7x3x5");
    private static final BlockPos REQUESTER_CHEST = new BlockPos(0, 1, 2);
    private static final BlockPos REQUESTER = new BlockPos(1, 1, 2);
    private static final BlockPos SWITCH = new BlockPos(3, 1, 2);
    private static final BlockPos SUPPLIER_1 = new BlockPos(5, 1, 2);
    private static final BlockPos SUPPLIER_1_CHEST = new BlockPos(6, 1, 2);
    private static final BlockPos SUPPLIER_2 = new BlockPos(2, 1, 1);
    private static final BlockPos SUPPLIER_2_CHEST = new BlockPos(2, 1, 0);

    // Routed layout (9x3x5), z=3 unless noted: segment A = requester chest/interface at x=0/1 + cable x=2,
    // router 1 at x=3, short link = cable x=4, router 2 at x=5, segment B = cable x=6 + supplier interface/chest x=7/8.
    // Long link: router 1 north -> cables (3,1,2) (3,1,1) (4,1,1) (5,1,1) (5,1,2) -> router 2 north.
    private static final Identifier ROUTED_STRUCTURE = Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "empty_9x3x5");
    private static final BlockPos ROUTED_REQUESTER_CHEST = new BlockPos(0, 1, 3);
    private static final BlockPos ROUTED_REQUESTER = new BlockPos(1, 1, 3);
    private static final BlockPos ROUTER_1 = new BlockPos(3, 1, 3);
    private static final BlockPos SHORT_LINK = new BlockPos(4, 1, 3);
    private static final BlockPos ROUTER_2 = new BlockPos(5, 1, 3);
    private static final BlockPos ROUTED_SUPPLIER = new BlockPos(7, 1, 3);
    private static final BlockPos ROUTED_SUPPLIER_CHEST = new BlockPos(8, 1, 3);
    private static final BlockPos LONG_LINK_MIDDLE = new BlockPos(4, 1, 1);
    private static final Ipv4Address ROUTED_REQUESTER_ADDRESS = new Ipv4Address(0x0A000101); // 10.0.1.1
    private static final Ipv4Address ROUTED_SUPPLIER_ADDRESS = new Ipv4Address(0x0A000201); // 10.0.2.1

    private static final BlockPos CHEST_A = new BlockPos(0, 1, 1);
    private static final BlockPos CHEST_B = new BlockPos(6, 1, 1);
    private static final int FIRST_CABLE_X = 1;
    private static final int LAST_CABLE_X = 5;
    // Interface layout: interfaces at x=1 and x=5 facing outwards, cables at x=2..4, DHCP server beside the middle cable.
    private static final BlockPos INTERFACE_A = new BlockPos(1, 1, 1);
    private static final BlockPos INTERFACE_B = new BlockPos(5, 1, 1);
    private static final BlockPos DHCP_SERVER = new BlockPos(3, 1, 0);

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
        registerTest(event, environment, DHCP_ASSIGNS_UNIQUE_ADDRESSES, 200);
        registerTest(event, environment, STATIC_CONFLICT_DETECTED, 200);
        registerTest(event, environment, DHCP_LEASE_RELEASED_ON_REMOVAL, 300);
        registerTest(event, environment, TRANSFER_VIA_INTERFACES, 200);
        registerTest(event, environment, REQUEST_SEQUENCE_DELIVERS, 300, PROTOCOL_STRUCTURE);
        registerTest(event, environment, REQUEST_SPLITS_BETWEEN_SUPPLIERS, 300, PROTOCOL_STRUCTURE);
        registerTest(event, environment, NO_DOUBLE_RESERVATION, 400, PROTOCOL_STRUCTURE);
        registerTest(event, environment, TIMEOUT_REREQUESTS, 800, PROTOCOL_STRUCTURE);
        registerTest(event, environment, ROUTED_DELIVERY, 300, ROUTED_STRUCTURE);
        registerTest(event, environment, DETOUR_AFTER_LINK_BREAK, 400, ROUTED_STRUCTURE);
        registerTest(event, environment, CROSS_SEGMENT_REQUEST, 600, ROUTED_STRUCTURE);
        registerTest(event, environment, WITHDRAW_WHEN_SOLD_OUT, 600, ROUTED_STRUCTURE);
        registerTest(event, environment, STATIC_ROUTE_PREFERRED, 400, ROUTED_STRUCTURE);
        registerTest(event, environment, DHCP_POOLS_DO_NOT_OVERLAP, 20);
        registerTest(event, environment, FIBER_IS_FASTER, 200);
        registerTest(event, environment, SWITCH_QUEUES_UNDER_LOAD, 600, PROTOCOL_STRUCTURE);
        registerTest(event, environment, OVERFLOW_TO_DISCARD_CHEST, 1200, PROTOCOL_STRUCTURE);
        registerTest(event, environment, BACKPRESSURE_KEEPS_ITEMS, 1200, PROTOCOL_STRUCTURE);
        registerTest(event, environment, HIGH_PRIORITY_OVERTAKES, 1200, PROTOCOL_STRUCTURE);
        registerTest(event, environment, TRACEROUTE_LISTS_ROUTERS, 300, ROUTED_STRUCTURE);
        registerTest(event, environment, CAPTURE_RECORDS_TRAFFIC, 300, PROTOCOL_STRUCTURE);
        registerTest(event, environment, ECMP_SPREADS_PACKETS, 600, ECMP_STRUCTURE);
    }

    private static void registerTest(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment,
            DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function, int maxTicks) {
        registerTest(event, environment, function, maxTicks, STRUCTURE);
    }

    private static void registerTest(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment,
            DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function, int maxTicks, Identifier structure) {
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, structure, maxTicks, 0, true, Rotation.NONE, false, 1, 1, false, 0);
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

    private static void dhcpAssignsUniqueAddresses(GameTestHelper helper) {
        buildInterfaceLine(helper, true);
        helper.succeedWhen(() -> {
            NetworkInterfaceBlockEntity a = networkInterface(helper, INTERFACE_A);
            NetworkInterfaceBlockEntity b = networkInterface(helper, INTERFACE_B);
            helper.assertValueEqual(a.status(), InterfaceStatus.DHCP_BOUND, "interface A status");
            helper.assertValueEqual(b.status(), InterfaceStatus.DHCP_BOUND, "interface B status");
            helper.assertTrue(!a.assigned().address().equals(b.assigned().address()), "addresses should differ");
            helper.assertValueEqual(a.assigned().prefixLength(), DhcpServerBlockEntity.DEFAULT_PREFIX_LENGTH, "prefix length");
            helper.assertValueEqual(dhcpServer(helper).leases().size(), 2, "lease count");
        });
    }

    private static void staticConflictDetected(GameTestHelper helper) {
        buildInterfaceLine(helper, false);
        Ipv4Interface address = Ipv4Interface.parse("10.0.0.5/24").orElseThrow();
        networkInterface(helper, INTERFACE_A).configure(false, address);
        networkInterface(helper, INTERFACE_B).configure(false, address);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(networkInterface(helper, INTERFACE_A).status(), InterfaceStatus.CONFLICT, "interface A status");
            helper.assertValueEqual(networkInterface(helper, INTERFACE_B).status(), InterfaceStatus.CONFLICT, "interface B status");
        });
    }

    private static void dhcpLeaseReleasedOnRemoval(GameTestHelper helper) {
        buildInterfaceLine(helper, true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(dhcpServer(helper).leases().size(), 2, "lease count"))
                .thenExecute(() -> helper.destroyBlock(INTERFACE_A))
                .thenWaitUntil(() -> helper.assertValueEqual(dhcpServer(helper).leases().size(), 1, "lease count after removal"))
                .thenSucceed();
    }

    private static void transferViaInterfaces(GameTestHelper helper) {
        helper.setBlock(CHEST_A, Blocks.CHEST);
        helper.setBlock(CHEST_B, Blocks.CHEST);
        buildInterfaceLine(helper, false);
        helper.setBlock(INTERFACE_A, helper.getBlockState(INTERFACE_A).setValue(NetworkInterfaceBlock.EXTRACT, true));
        chest(helper, CHEST_A).setItem(0, new ItemStack(Items.IRON_INGOT, 8));

        helper.succeedWhen(() -> {
            helper.assertValueEqual(chest(helper, CHEST_B).countItem(Items.IRON_INGOT), 8, "iron in chest B");
            helper.assertValueEqual(chest(helper, CHEST_A).countItem(Items.IRON_INGOT), 0, "iron in chest A");
        });
    }

    private static void buildInterfaceLine(GameTestHelper helper, boolean withDhcp) {
        BlockState interfaceState = ModBlocks.NETWORK_INTERFACE.get().defaultBlockState();
        helper.setBlock(INTERFACE_A, interfaceState.setValue(NetworkInterfaceBlock.FACING, Direction.WEST));
        helper.setBlock(INTERFACE_B, interfaceState.setValue(NetworkInterfaceBlock.FACING, Direction.EAST));
        if (withDhcp) {
            helper.setBlock(DHCP_SERVER, ModBlocks.DHCP_SERVER.get());
        }
        for (int x = 2; x <= 4; x++) {
            placeCable(helper, new BlockPos(x, 1, 1));
        }
    }

    private static NetworkInterfaceBlockEntity networkInterface(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, NetworkInterfaceBlockEntity.class);
    }

    private static DhcpServerBlockEntity dhcpServer(GameTestHelper helper) {
        return helper.getBlockEntity(DHCP_SERVER, DhcpServerBlockEntity.class);
    }

    private static void requestSequenceDelivers(GameTestHelper helper) {
        buildProtocolLine(helper, false);
        keepStocked(helper, REQUESTER, Items.IRON_INGOT, 16);
        chest(helper, SUPPLIER_1_CHEST).setItem(0, new ItemStack(Items.IRON_INGOT, 64));

        helper.succeedWhen(() -> {
            helper.assertValueEqual(chest(helper, REQUESTER_CHEST).countItem(Items.IRON_INGOT), 16, "iron at requester");
            helper.assertValueEqual(chest(helper, SUPPLIER_1_CHEST).countItem(Items.IRON_INGOT), 48, "iron at supplier");
            helper.assertTrue(networkInterface(helper, REQUESTER).host().requests().isEmpty(), "request should be complete");
            helper.assertValueEqual(networkInterface(helper, SUPPLIER_1).host().shipmentCount(), 0, "shipments awaiting RECEIVED");
            helper.assertValueEqual(accessSwitch(helper).macTableSize(), 2, "MACs learned by the switch");
        });
    }

    private static void requestSplitsBetweenSuppliers(GameTestHelper helper) {
        buildProtocolLine(helper, true);
        keepStocked(helper, REQUESTER, Items.IRON_INGOT, 16);
        chest(helper, SUPPLIER_1_CHEST).setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        chest(helper, SUPPLIER_2_CHEST).setItem(0, new ItemStack(Items.IRON_INGOT, 10));

        helper.succeedWhen(() -> {
            helper.assertValueEqual(chest(helper, REQUESTER_CHEST).countItem(Items.IRON_INGOT), 16, "iron at requester");
            int left = chest(helper, SUPPLIER_1_CHEST).countItem(Items.IRON_INGOT) + chest(helper, SUPPLIER_2_CHEST).countItem(Items.IRON_INGOT);
            helper.assertValueEqual(left, 4, "iron left at suppliers");
        });
    }

    // Two requesters compete for one supplier's 12 items; together they must get exactly 12.
    private static void noDoubleReservation(GameTestHelper helper) {
        buildProtocolLine(helper, true);
        keepStocked(helper, REQUESTER, Items.IRON_INGOT, 10);
        keepStocked(helper, SUPPLIER_1, Items.IRON_INGOT, 10);
        chest(helper, SUPPLIER_2_CHEST).setItem(0, new ItemStack(Items.IRON_INGOT, 12));

        helper.succeedWhen(() -> {
            int first = chest(helper, REQUESTER_CHEST).countItem(Items.IRON_INGOT);
            int second = chest(helper, SUPPLIER_1_CHEST).countItem(Items.IRON_INGOT);
            helper.assertValueEqual(chest(helper, SUPPLIER_2_CHEST).countItem(Items.IRON_INGOT), 0, "iron left at supplier");
            helper.assertValueEqual(first + second, 12, "iron at requesters");
            helper.assertTrue(first <= 10 && second <= 10, "no requester should exceed its target");
        });
    }

    // The chosen supplier disappears after offering; the request times out and is served by the other one.
    private static void timeoutRerequests(GameTestHelper helper) {
        buildProtocolLine(helper, true);
        keepStocked(helper, REQUESTER, Items.IRON_INGOT, 16);
        chest(helper, SUPPLIER_1_CHEST).setItem(0, new ItemStack(Items.IRON_INGOT, 32));
        chest(helper, SUPPLIER_2_CHEST).setItem(0, new ItemStack(Items.IRON_INGOT, 16));
        Ipv4Address supplier1 = Ipv4Address.parse("10.0.0.2").orElseThrow();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(networkInterface(helper, REQUESTER).host().requests().stream()
                        .anyMatch(request -> request.isReservingFrom(supplier1)), "waiting for RESERVE to supplier 1"))
                .thenExecute(() -> helper.destroyBlock(SUPPLIER_1))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(chest(helper, REQUESTER_CHEST).countItem(Items.IRON_INGOT), 16, "iron at requester");
                    helper.assertValueEqual(chest(helper, SUPPLIER_2_CHEST).countItem(Items.IRON_INGOT), 0, "iron at supplier 2");
                    helper.assertValueEqual(chest(helper, SUPPLIER_1_CHEST).countItem(Items.IRON_INGOT), 32, "iron at supplier 1");
                })
                .thenSucceed();
    }

    // Items shipped from segment B reach segment A through both routers once link-state has converged.
    private static void routedDelivery(GameTestHelper helper) {
        buildRoutedNetwork(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(routeFromSupplier(helper) != null, "waiting for routes to converge"))
                .thenExecute(() -> shipToRequester(helper, 16))
                .thenWaitUntil(() -> helper.assertValueEqual(chest(helper, ROUTED_REQUESTER_CHEST).countItem(Items.IRON_INGOT), 16, "iron at requester"))
                .thenSucceed();
    }

    // The short link breaks; routers recompute and traffic takes the long link instead.
    private static void detourAfterLinkBreak(GameTestHelper helper) {
        buildRoutedNetwork(helper);
        BlockPos shortLink = helper.absolutePos(SHORT_LINK);
        BlockPos longLink = helper.absolutePos(LONG_LINK_MIDDLE);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    List<BlockPos> route = routeFromSupplier(helper);
                    helper.assertTrue(route != null && route.contains(shortLink), "waiting for the short link to be preferred");
                })
                .thenExecute(() -> helper.destroyBlock(SHORT_LINK))
                .thenWaitUntil(() -> {
                    List<BlockPos> route = routeFromSupplier(helper);
                    helper.assertTrue(route != null && route.contains(longLink), "waiting for the detour over the long link");
                })
                .thenExecute(() -> shipToRequester(helper, 8))
                .thenWaitUntil(() -> helper.assertValueEqual(chest(helper, ROUTED_REQUESTER_CHEST).countItem(Items.IRON_INGOT), 8, "iron at requester"))
                .thenSucceed();
    }

    // The supplier is behind two routers: its ADVERTISE reaches router 1, which forwards the REQUEST to it.
    private static void crossSegmentRequest(GameTestHelper helper) {
        buildRoutedNetwork(helper);
        chest(helper, ROUTED_SUPPLIER_CHEST).setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        keepStocked(helper, ROUTED_REQUESTER, Items.IRON_INGOT, 16);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(chest(helper, ROUTED_REQUESTER_CHEST).countItem(Items.IRON_INGOT), 16, "iron at requester");
            helper.assertValueEqual(chest(helper, ROUTED_SUPPLIER_CHEST).countItem(Items.IRON_INGOT), 48, "iron at supplier");
            helper.assertTrue(networkInterface(helper, ROUTED_REQUESTER).host().requests().isEmpty(), "request should be complete");
        });
    }

    // Router 1 learns about the supplier's iron, then forgets it once the supplier runs out.
    private static void withdrawWhenSoldOut(GameTestHelper helper) {
        buildRoutedNetwork(helper);
        chest(helper, ROUTED_SUPPLIER_CHEST).setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(router1Knows(helper, Items.IRON_INGOT), "waiting for iron to be advertised"))
                .thenExecute(() -> chest(helper, ROUTED_SUPPLIER_CHEST).setItem(0, ItemStack.EMPTY))
                .thenWaitUntil(() -> helper.assertTrue(!router1Knows(helper, Items.IRON_INGOT), "waiting for iron to be withdrawn"))
                .thenSucceed();
    }

    // Learned routing prefers the short link; a static route on router 2 pins traffic to the long one.
    private static void staticRoutePreferred(GameTestHelper helper) {
        buildRoutedNetwork(helper);
        BlockPos longLink = helper.absolutePos(LONG_LINK_MIDDLE);
        helper.getBlockEntity(ROUTER_1, RouterBlockEntity.class).setPortAddress(Direction.NORTH, Ipv4Interface.parse("10.0.9.1/24").orElseThrow());
        helper.getBlockEntity(ROUTER_2, RouterBlockEntity.class).addStaticRoute(RouterBlockEntity.StaticRoute.parse("10.0.1.0/24 via 10.0.9.1").orElseThrow());
        helper.startSequence()
                .thenWaitUntil(() -> {
                    List<BlockPos> route = routeFromSupplier(helper);
                    helper.assertTrue(route != null && route.contains(longLink), "waiting for the static route over the long link");
                })
                .thenExecute(() -> shipToRequester(helper, 8))
                .thenWaitUntil(() -> helper.assertValueEqual(chest(helper, ROUTED_REQUESTER_CHEST).countItem(Items.IRON_INGOT), 8, "iron at requester"))
                .thenSucceed();
    }

    // Two freshly placed DHCP servers get different default pools.
    private static void dhcpPoolsDoNotOverlap(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.DHCP_SERVER.get());
        helper.setBlock(new BlockPos(5, 1, 1), ModBlocks.DHCP_SERVER.get());
        helper.succeedWhen(() -> {
            Ipv4Address first = helper.getBlockEntity(new BlockPos(1, 1, 1), DhcpServerBlockEntity.class).rangeStart();
            Ipv4Address second = helper.getBlockEntity(new BlockPos(5, 1, 1), DhcpServerBlockEntity.class).rangeStart();
            helper.assertTrue(!first.inSubnet(second, 24), "pools should be in different /24 subnets: " + first + " and " + second);
        });
    }

    private static boolean router1Knows(GameTestHelper helper, Item item) {
        return helper.getBlockEntity(ROUTER_1, RouterBlockEntity.class).suppliersOf(new ItemStack(item)).stream()
                .anyMatch(supplier -> supplier.host().equals(ROUTED_SUPPLIER_ADDRESS));
    }

    private static void buildRoutedNetwork(GameTestHelper helper) {
        BlockState interfaceState = ModBlocks.NETWORK_INTERFACE.get().defaultBlockState();
        helper.setBlock(ROUTED_REQUESTER_CHEST, Blocks.CHEST);
        helper.setBlock(ROUTED_SUPPLIER_CHEST, Blocks.CHEST);
        helper.setBlock(ROUTED_REQUESTER, interfaceState.setValue(NetworkInterfaceBlock.FACING, Direction.WEST));
        helper.setBlock(ROUTED_SUPPLIER, interfaceState.setValue(NetworkInterfaceBlock.FACING, Direction.EAST));
        helper.setBlock(ROUTER_1, ModBlocks.ROUTER.get());
        helper.setBlock(ROUTER_2, ModBlocks.ROUTER.get());
        networkInterface(helper, ROUTED_REQUESTER).configure(false, new Ipv4Interface(ROUTED_REQUESTER_ADDRESS, 24));
        networkInterface(helper, ROUTED_SUPPLIER).configure(false, new Ipv4Interface(ROUTED_SUPPLIER_ADDRESS, 24));
        for (BlockPos cable : List.of(new BlockPos(2, 1, 3), SHORT_LINK, new BlockPos(6, 1, 3),
                new BlockPos(3, 1, 2), new BlockPos(3, 1, 1), LONG_LINK_MIDDLE, new BlockPos(5, 1, 1), new BlockPos(5, 1, 2))) {
            placeCable(helper, cable);
        }
    }

    private static List<BlockPos> routeFromSupplier(GameTestHelper helper) {
        return RoutingService.resolve(helper.getLevel(), helper.absolutePos(ROUTED_SUPPLIER), ROUTED_REQUESTER_ADDRESS);
    }

    private static void shipToRequester(GameTestHelper helper, int count) {
        NetworkManager.get(helper.getLevel()).ship(helper.getLevel(), helper.absolutePos(ROUTED_SUPPLIER), ROUTED_REQUESTER_ADDRESS,
                new ItemStack(Items.IRON_INGOT, count), 1L, ROUTED_SUPPLIER_ADDRESS);
    }

    // On fibre a packet crosses one block per tick.
    private static void fiberIsFaster(GameTestHelper helper) {
        helper.setBlock(CHEST_A, Blocks.CHEST);
        helper.setBlock(CHEST_B, Blocks.CHEST);
        for (int x = FIRST_CABLE_X; x <= LAST_CABLE_X; x++) {
            placeCable(helper, new BlockPos(x, 1, 1), ModBlocks.FIBER_CABLE.get());
        }
        BlockPos first = new BlockPos(FIRST_CABLE_X, 1, 1);
        helper.setBlock(first, helper.getBlockState(first).setValue(CableBlock.EXTRACT_WEST, true));
        chest(helper, CHEST_A).setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        BlockPos middle = helper.absolutePos(new BlockPos(3, 1, 1));
        helper.succeedWhen(() -> helper.assertTrue(NetworkManager.get(helper.getLevel()).packets().stream()
                .anyMatch(packet -> packet.currentNode().equals(middle) && !packet.isWaiting() && packet.hopDuration() == 1),
                "waiting for a packet crossing the fibre at one block per tick"));
    }

    // Fibre into the switch, copper out of it: the switch's output is the bottleneck, so packets queue there.
    private static void switchQueuesUnderLoad(GameTestHelper helper) {
        buildBottleneck(helper, false);
        BlockPos switchPos = helper.absolutePos(SWITCH);
        int[] longestQueue = {0};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(bottleneckRouteReady(helper), "waiting for addresses"))
                .thenExecute(() -> shipBottleneck(helper, 6))
                .thenWaitUntil(() -> {
                    longestQueue[0] = Math.max(longestQueue[0], NetworkManager.get(helper.getLevel()).queueLength(switchPos));
                    helper.assertValueEqual(chest(helper, REQUESTER_CHEST).countItem(Items.IRON_INGOT), 6 * 64, "iron at requester");
                })
                .thenExecute(() -> helper.assertTrue(longestQueue[0] >= 2, "the switch should have queued packets, longest queue " + longestQueue[0]))
                .thenSucceed();
    }

    // More than the switch can hold: the excess goes to the discard chest and nothing is lost.
    private static void overflowToDiscardChest(GameTestHelper helper) {
        buildBottleneck(helper, true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(bottleneckRouteReady(helper), "waiting for addresses"))
                .thenExecute(() -> shipBottleneck(helper, 16))
                .thenWaitUntil(() -> {
                    int discarded = helper.getBlockEntity(DISCARD_CHEST, DiscardChestBlockEntity.class).countItem(Items.IRON_INGOT);
                    int delivered = chest(helper, REQUESTER_CHEST).countItem(Items.IRON_INGOT);
                    helper.assertTrue(discarded > 0, "some iron should overflow into the discard chest");
                    helper.assertValueEqual(delivered + discarded, 16 * 64, "iron delivered or discarded");
                })
                .thenSucceed();
    }

    // Same load without a discard chest: senders wait, everything arrives and nothing is dropped.
    private static void backpressureKeepsItems(GameTestHelper helper) {
        buildBottleneck(helper, false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(bottleneckRouteReady(helper), "waiting for addresses"))
                .thenExecute(() -> shipBottleneck(helper, 16))
                .thenWaitUntil(() -> helper.assertValueEqual(chest(helper, REQUESTER_CHEST).countItem(Items.IRON_INGOT), 16 * 64, "iron at requester"))
                .thenExecute(() -> helper.assertValueEqual(droppedIron(helper), 0, "dropped iron"))
                .thenSucceed();
    }

    // Low-class traffic to a second requester is sent first, then high-class traffic to the first one.
    // Both share the switch's copper output; the high-class load must finish while low-class is still queued.
    private static void highPriorityOvertakes(GameTestHelper helper) {
        buildBottleneck(helper, false);
        BlockState interfaceState = ModBlocks.NETWORK_INTERFACE.get().defaultBlockState();
        helper.setBlock(SUPPLIER_2_CHEST, Blocks.CHEST);
        helper.setBlock(SUPPLIER_2, interfaceState.setValue(NetworkInterfaceBlock.FACING, Direction.NORTH));
        setStatic(helper, SUPPLIER_2, "10.0.0.3/24");
        Ipv4Address lowTarget = Ipv4Address.parse("10.0.0.3").orElseThrow();
        Ipv4Address highTarget = Ipv4Address.parse("10.0.0.1").orElseThrow();
        Ipv4Address supplier = Ipv4Address.parse("10.0.0.2").orElseThrow();
        NetworkManager manager = NetworkManager.get(helper.getLevel());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(bottleneckRouteReady(helper)
                        && RoutingService.resolve(helper.getLevel(), helper.absolutePos(SUPPLIER_1), lowTarget) != null, "waiting for addresses"))
                .thenExecute(() -> manager.ship(helper.getLevel(), helper.absolutePos(SUPPLIER_1), lowTarget, new ItemStack(Items.IRON_INGOT, 8 * 64),
                        1L, supplier, TransitPacket.PRIORITY_LOW))
                .thenIdle(1)
                .thenExecute(() -> manager.ship(helper.getLevel(), helper.absolutePos(SUPPLIER_1), highTarget, new ItemStack(Items.GOLD_INGOT, 4 * 64),
                        2L, supplier, TransitPacket.PRIORITY_HIGH))
                .thenWaitUntil(() -> helper.assertValueEqual(chest(helper, REQUESTER_CHEST).countItem(Items.GOLD_INGOT), 4 * 64, "high-class gold"))
                .thenExecute(() -> helper.assertTrue(chest(helper, SUPPLIER_2_CHEST).countItem(Items.IRON_INGOT) < 8 * 64,
                        "low-class iron should still be on its way when the high-class gold has arrived"))
                .thenWaitUntil(() -> helper.assertValueEqual(chest(helper, SUPPLIER_2_CHEST).countItem(Items.IRON_INGOT), 8 * 64, "low-class iron"))
                .thenSucceed();
    }

    // From segment B to segment A: router 2, router 1, then the requester itself.
    private static void tracerouteListsRouters(GameTestHelper helper) {
        buildRoutedNetwork(helper);
        helper.succeedWhen(() -> {
            Observation.Trace trace = Observation.trace(helper.getLevel(), helper.absolutePos(ROUTED_SUPPLIER), ROUTED_REQUESTER_ADDRESS);
            helper.assertTrue(trace.reached(), "the requester should be reachable");
            helper.assertValueEqual(trace.hops().size(), 3, "hops");
            helper.assertValueEqual(trace.hops().get(0).pos(), helper.absolutePos(ROUTER_2), "first hop");
            helper.assertValueEqual(trace.hops().get(1).pos(), helper.absolutePos(ROUTER_1), "second hop");
            helper.assertValueEqual(trace.hops().get(2).pos(), helper.absolutePos(ROUTED_REQUESTER), "destination");
        });
    }

    // A capture on the switch sees both the control exchange and the delivered items.
    private static void captureRecordsTraffic(GameTestHelper helper) {
        buildProtocolLine(helper, false);
        BlockPos switchPos = helper.absolutePos(SWITCH);
        NetworkManager.get(helper.getLevel()).capture().start(UUID.randomUUID(), switchPos, helper.getLevel().getGameTime(), 600);
        keepStocked(helper, REQUESTER, Items.IRON_INGOT, 16);
        chest(helper, SUPPLIER_1_CHEST).setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        helper.succeedWhen(() -> {
            List<Component> entries = NetworkManager.get(helper.getLevel()).capture().entries(switchPos);
            helper.assertTrue(entries.stream().anyMatch(entry -> isCaptureOf(entry, "observe.loginetwork.capture.control")), "control messages captured");
            helper.assertTrue(entries.stream().anyMatch(entry -> isCaptureOf(entry, "observe.loginetwork.capture.data")), "items captured");
        });
    }

    private static boolean isCaptureOf(Component entry, String key) {
        return entry.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key);
    }

    // Routed layout without the short link, in a 9x3x7 area (everything shifted to z+1), plus a second long link
    // to the south that mirrors the northern one: two equal-cost paths between the routers.
    private static final Identifier ECMP_STRUCTURE = Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "empty_9x3x7");

    private static void ecmpSpreadsPackets(GameTestHelper helper) {
        BlockPos shift = new BlockPos(0, 0, 1);
        BlockState interfaceState = ModBlocks.NETWORK_INTERFACE.get().defaultBlockState();
        helper.setBlock(ROUTED_REQUESTER_CHEST.offset(shift), Blocks.CHEST);
        helper.setBlock(ROUTED_REQUESTER.offset(shift), interfaceState.setValue(NetworkInterfaceBlock.FACING, Direction.WEST));
        helper.setBlock(ROUTED_SUPPLIER.offset(shift), interfaceState.setValue(NetworkInterfaceBlock.FACING, Direction.EAST));
        helper.setBlock(ROUTER_1.offset(shift), ModBlocks.ROUTER.get());
        helper.setBlock(ROUTER_2.offset(shift), ModBlocks.ROUTER.get());
        networkInterface(helper, ROUTED_REQUESTER.offset(shift)).configure(false, new Ipv4Interface(ROUTED_REQUESTER_ADDRESS, 24));
        networkInterface(helper, ROUTED_SUPPLIER.offset(shift)).configure(false, new Ipv4Interface(ROUTED_SUPPLIER_ADDRESS, 24));
        List<BlockPos> cables = List.of(new BlockPos(2, 1, 4), new BlockPos(6, 1, 4),
                new BlockPos(3, 1, 3), new BlockPos(3, 1, 2), new BlockPos(4, 1, 2), new BlockPos(5, 1, 2), new BlockPos(5, 1, 3),
                new BlockPos(3, 1, 5), new BlockPos(3, 1, 6), new BlockPos(4, 1, 6), new BlockPos(5, 1, 6), new BlockPos(5, 1, 5));
        cables.forEach(cable -> placeCable(helper, cable));
        BlockPos supplier = helper.absolutePos(ROUTED_SUPPLIER.offset(shift));
        BlockPos north = helper.absolutePos(new BlockPos(4, 1, 2));
        BlockPos south = helper.absolutePos(new BlockPos(4, 1, 6));
        NetworkManager manager = NetworkManager.get(helper.getLevel());
        helper.startSequence()
                .thenWaitUntil(() -> {
                    RouterBlockEntity router = helper.getBlockEntity(ROUTER_2.offset(shift), RouterBlockEntity.class);
                    helper.assertTrue(router.paths().values().stream().anyMatch(path -> path.firstHops().size() == 2), "waiting for two equal-cost paths");
                    helper.assertTrue(RoutingService.resolve(helper.getLevel(), supplier, ROUTED_REQUESTER_ADDRESS) != null, "waiting for a route");
                })
                .thenExecute(() -> manager.ship(helper.getLevel(), supplier, ROUTED_REQUESTER_ADDRESS, new ItemStack(Items.IRON_INGOT, 8 * 64), 1L,
                        ROUTED_SUPPLIER_ADDRESS))
                .thenExecute(() -> {
                    long viaNorth = manager.packets().stream().filter(packet -> packet.nodes().contains(north)).count();
                    long viaSouth = manager.packets().stream().filter(packet -> packet.nodes().contains(south)).count();
                    helper.assertTrue(viaNorth > 0 && viaSouth > 0, "packets should use both paths (north " + viaNorth + ", south " + viaSouth + ")");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(chest(helper, ROUTED_REQUESTER_CHEST.offset(shift)).countItem(Items.IRON_INGOT), 8 * 64,
                        "iron at requester"))
                .thenSucceed();
    }

    private static final BlockPos DISCARD_CHEST = new BlockPos(4, 1, 1);

    // Protocol layout with fibre between supplier 1 and the switch and copper towards the requester.
    private static void buildBottleneck(GameTestHelper helper, boolean withDiscardChest) {
        BlockState interfaceState = ModBlocks.NETWORK_INTERFACE.get().defaultBlockState();
        helper.setBlock(REQUESTER_CHEST, Blocks.CHEST);
        helper.setBlock(SUPPLIER_1_CHEST, Blocks.CHEST);
        helper.setBlock(REQUESTER, interfaceState.setValue(NetworkInterfaceBlock.FACING, Direction.WEST));
        helper.setBlock(SUPPLIER_1, interfaceState.setValue(NetworkInterfaceBlock.FACING, Direction.EAST));
        helper.setBlock(SWITCH, ModBlocks.ACCESS_SWITCH.get());
        if (withDiscardChest) {
            helper.setBlock(DISCARD_CHEST, ModBlocks.DISCARD_CHEST.get());
        }
        setStatic(helper, REQUESTER, "10.0.0.1/24");
        setStatic(helper, SUPPLIER_1, "10.0.0.2/24");
        placeCable(helper, new BlockPos(2, 1, 2));
        placeCable(helper, new BlockPos(4, 1, 2), ModBlocks.FIBER_CABLE.get());
    }

    private static boolean bottleneckRouteReady(GameTestHelper helper) {
        return RoutingService.resolve(helper.getLevel(), helper.absolutePos(SUPPLIER_1), Ipv4Address.parse("10.0.0.1").orElseThrow()) != null;
    }

    private static void shipBottleneck(GameTestHelper helper, int stacks) {
        NetworkManager.get(helper.getLevel()).ship(helper.getLevel(), helper.absolutePos(SUPPLIER_1), Ipv4Address.parse("10.0.0.1").orElseThrow(),
                new ItemStack(Items.IRON_INGOT, stacks * 64), 1L, Ipv4Address.parse("10.0.0.2").orElseThrow());
    }

    private static void buildProtocolLine(GameTestHelper helper, boolean withSecondSupplier) {
        BlockState interfaceState = ModBlocks.NETWORK_INTERFACE.get().defaultBlockState();
        helper.setBlock(REQUESTER_CHEST, Blocks.CHEST);
        helper.setBlock(SUPPLIER_1_CHEST, Blocks.CHEST);
        helper.setBlock(REQUESTER, interfaceState.setValue(NetworkInterfaceBlock.FACING, Direction.WEST));
        helper.setBlock(SUPPLIER_1, interfaceState.setValue(NetworkInterfaceBlock.FACING, Direction.EAST));
        helper.setBlock(SWITCH, ModBlocks.ACCESS_SWITCH.get());
        setStatic(helper, REQUESTER, "10.0.0.1/24");
        setStatic(helper, SUPPLIER_1, "10.0.0.2/24");
        if (withSecondSupplier) {
            helper.setBlock(SUPPLIER_2_CHEST, Blocks.CHEST);
            helper.setBlock(SUPPLIER_2, interfaceState.setValue(NetworkInterfaceBlock.FACING, Direction.NORTH));
            setStatic(helper, SUPPLIER_2, "10.0.0.3/24");
        }
        placeCable(helper, new BlockPos(2, 1, 2));
        placeCable(helper, new BlockPos(4, 1, 2));
    }

    private static void setStatic(GameTestHelper helper, BlockPos pos, String address) {
        networkInterface(helper, pos).configure(false, Ipv4Interface.parse(address).orElseThrow());
    }

    private static void keepStocked(GameTestHelper helper, BlockPos interfacePos, Item item, int target) {
        networkInterface(helper, interfacePos).host().setFilter(0, new ItemStack(item), target);
    }

    private static AccessSwitchBlockEntity accessSwitch(GameTestHelper helper) {
        return helper.getBlockEntity(SWITCH, AccessSwitchBlockEntity.class);
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
        placeCable(helper, pos, ModBlocks.COPPER_CABLE.get());
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos, CableBlock cable) {
        BlockState state = CableBlock.withConnections(cable.defaultBlockState(), helper.getLevel(), helper.absolutePos(pos));
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
