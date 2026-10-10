package com.stellarcielo.loginetwork.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stellarcielo.loginetwork.Config;
import com.stellarcielo.loginetwork.LogiNetwork;
import com.stellarcielo.loginetwork.network.payload.TransitHopPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

// Draws items travelling through cables. Extracts render state per frame, then submits it as item geometry.
public final class TransitItemRenderer {
    // Largest dimension of a drawn item, in blocks. Slightly thicker than the 6px cable so it stays visible.
    private static final float ITEM_SIZE = 0.5f;
    private static final float SPIN_DEGREES_PER_TICK = 4.0f;

    private record TransitRenderState(Vec3 pos, ItemStackRenderState item, int light, float spin) {}

    private static final ContextKey<List<TransitRenderState>> RENDER_DATA =
            new ContextKey<>(Identifier.fromNamespaceAndPath(LogiNetwork.MOD_ID, "transit_items"));

    private TransitItemRenderer() {}

    public static void extract(ExtractLevelRenderStateEvent event) {
        ClientLevel level = event.getLevel();
        double time = level.getGameTime() + event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ClientTransitTracker.pruneStale(level.getGameTime());

        Vec3 camera = event.getCamera().position();
        double maxDistance = Config.ITEM_RENDER_DISTANCE.getAsInt();
        double maxDistanceSqr = maxDistance * maxDistance;
        List<TransitRenderState> states = new ArrayList<>();
        for (TransitHopPayload hop : ClientTransitTracker.hops()) {
            Vec3 pos = ClientTransitTracker.positionAt(hop, time);
            if (pos.distanceToSqr(camera) > maxDistanceSqr || !event.getFrustum().isVisible(AABB.ofSize(pos, ITEM_SIZE, ITEM_SIZE, ITEM_SIZE))) {
                continue;
            }
            ItemStackRenderState item = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(item, hop.stack(), ItemDisplayContext.GROUND, level, null, (int) hop.id());
            if (item.isEmpty()) {
                continue;
            }
            int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(pos));
            states.add(new TransitRenderState(pos, item, light, (float) (time * SPIN_DEGREES_PER_TICK % 360.0)));
        }
        event.getRenderState().setRenderData(RENDER_DATA, states);
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
            float scale = largest > 0 ? (float) (ITEM_SIZE / largest) : 1.0f;
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
