package com.stellarcielo.loginetwork.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stellarcielo.loginetwork.Config;
import com.stellarcielo.loginetwork.LogiNetwork;
import com.stellarcielo.loginetwork.network.control.ControlMessageType;
import com.stellarcielo.loginetwork.network.payload.ControlPathPayload;
import com.stellarcielo.loginetwork.network.payload.TransitHopPayload;
import com.stellarcielo.loginetwork.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

// Draws what travels through cables: real items, and (when enabled) control messages as glinting
// tinted envelopes. Extracts render state per frame, then submits it as item geometry.
public final class TransitItemRenderer {
    // Largest dimension of a drawn item, in blocks. Slightly thicker than the 6px cable so it stays visible.
    private static final float ITEM_SIZE = 0.5f;
    // Control messages poke out of the cable too; the tint and glint tell them apart from real items.
    private static final float CONTROL_SIZE = 0.6f;
    private static final float SPIN_DEGREES_PER_TICK = 4.0f;

    private record TransitRenderState(Vec3 pos, ItemStackRenderState item, int light, float spin, float size) {}

    private static final ContextKey<List<TransitRenderState>> RENDER_DATA =
            new ContextKey<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "transit_items"));

    private static final Map<ControlMessageType, ItemStack> CONTROL_ICONS = new EnumMap<>(ControlMessageType.class);

    private TransitItemRenderer() {}

    public static void extract(ExtractLevelRenderStateEvent event) {
        ClientLevel level = event.getLevel();
        double time = level.getGameTime() + event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ClientTransitTracker.pruneStale(level.getGameTime());

        Vec3 camera = event.getCamera().position();
        double maxDistance = Config.ITEM_RENDER_DISTANCE.getAsInt();
        double maxDistanceSqr = maxDistance * maxDistance;
        Frustum frustum = event.getFrustum();
        float spin = (float) (time * SPIN_DEGREES_PER_TICK % 360.0);
        List<TransitRenderState> states = new ArrayList<>();

        for (TransitHopPayload hop : ClientTransitTracker.hops()) {
            Vec3 pos = ClientTransitTracker.positionAt(hop, time);
            if (isVisible(pos, ITEM_SIZE, camera, maxDistanceSqr, frustum)) {
                addState(states, level, pos, hop.stack(), (int) hop.id(), spin, ITEM_SIZE);
            }
        }
        if (Config.RENDER_CONTROL_PACKETS.getAsBoolean()) {
            for (ControlPathPayload path : ClientControlTracker.active(time)) {
                Vec3 pos = ClientControlTracker.positionAt(path, time);
                if (isVisible(pos, CONTROL_SIZE, camera, maxDistanceSqr, frustum)) {
                    ControlMessageType type = ControlMessageType.byOrdinal(path.messageType());
                    addState(states, level, pos, controlIcon(type), type.ordinal(), spin, CONTROL_SIZE);
                }
            }
        } else {
            ClientControlTracker.clear();
        }
        event.getRenderState().setRenderData(RENDER_DATA, states);
    }

    private static boolean isVisible(Vec3 pos, float size, Vec3 camera, double maxDistanceSqr, Frustum frustum) {
        return pos.distanceToSqr(camera) <= maxDistanceSqr && frustum.isVisible(AABB.ofSize(pos, size, size, size));
    }

    private static void addState(List<TransitRenderState> states, ClientLevel level, Vec3 pos, ItemStack stack, int seed, float spin, float size) {
        ItemStackRenderState item = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(item, stack, ItemDisplayContext.GROUND, level, null, seed);
        if (!item.isEmpty()) {
            states.add(new TransitRenderState(pos, item, LightCoordsUtil.getLightCoords(level, BlockPos.containing(pos)), spin, size));
        }
    }

    // A paper envelope tinted with the message type's colour, with a glint to set it apart from real items.
    private static ItemStack controlIcon(ControlMessageType type) {
        return CONTROL_ICONS.computeIfAbsent(type, t -> {
            ItemStack stack = new ItemStack(ModItems.CONTROL_PACKET.get());
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(t.color())));
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            return stack;
        });
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        List<TransitRenderState> states = event.getLevelRenderState().getRenderData(RENDER_DATA);
        if (states == null || states.isEmpty()) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        for (TransitRenderState state : states) {
            AABB bounds = state.item().getModelBoundingBox();
            double largest = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
            float scale = largest > 0 ? (float) (state.size() / largest) : 1.0f;
            Vec3 center = bounds.getCenter();

            poseStack.pushPose();
            poseStack.translate(state.pos().x - camera.x, state.pos().y - camera.y, state.pos().z - camera.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.spin()));
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-center.x, -center.y, -center.z);
            state.item().submit(poseStack, event.getSubmitNodeCollector(), state.light(), OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }
}
