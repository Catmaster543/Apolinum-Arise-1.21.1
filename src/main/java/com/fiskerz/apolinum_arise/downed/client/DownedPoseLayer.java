package com.fiskerz.apolinum_arise.downed.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Renders a downed player laid on the ground in one of 3 pose variants loaded from resource files
 * ({@link DownedPoses}). The base body is hidden in {@code RenderPlayerEvent.Pre} (see DownedClientEvents),
 * so this layer re-renders the model ONCE with our pose overriding the vanilla one - no mixin, no double
 * image. Works for both wide and slim models.
 *
 * <p>Fixes vs. the first pass: the outer skin layers (jacket/sleeves/pants/hat) are copied from the posed
 * base parts so they no longer float detached; the whole-body transform comes from tunable pose data
 * instead of an arbitrary hardcoded translate; and the body facing is frozen (Patch B6, done in
 * DownedClientEvents) so the corpse no longer drifts/rotates with the camera.
 */
public class DownedPoseLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    public DownedPoseLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // Downed (Phase 7) or passed out from exhaustion (Phase 10a) - the same laid-out body either way.
        boolean incapacitated = IncapacitatedRender.active(player);
        boolean preview = isPreviewedLocalPlayer(player);
        if (!incapacitated && !preview) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        model.setAllVisible(true); // Pre hid the base body; always restore before the invisibility bail-out
        if (player.isInvisible()) {
            return;
        }
        int variant = preview ? DownedPoses.previewVariant() : IncapacitatedRender.poseVariant(player);
        DownedPose pose = DownedPoses.get(variant);

        applyPose(model, pose);

        poseStack.pushPose();
        applyRoot(poseStack, pose);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(player.getSkin().texture()));
        model.renderToBuffer(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();
    }

    private static boolean isPreviewedLocalPlayer(AbstractClientPlayer player) {
        return DownedPoses.previewVariant() >= 0 && Minecraft.getInstance().player == player;
    }

    // Whole-body transform: offset the model onto the ground, then lay it flat. (Balanced with the
    // push/popPose in render; nothing accumulates between frames.)
    private static void applyRoot(PoseStack poseStack, DownedPose pose) {
        poseStack.translate(pose.rootOffset[0], pose.rootOffset[1], pose.rootOffset[2]);
        poseStack.mulPose(Axis.XP.rotationDegrees(pose.rootRotation[0]));
        poseStack.mulPose(Axis.YP.rotationDegrees(pose.rootRotation[1]));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pose.rootRotation[2]));
    }

    // Per-part rotations, THEN copy the outer skin layers so they follow (the key floating-limb fix).
    private static void applyPose(PlayerModel<AbstractClientPlayer> model, DownedPose pose) {
        setPart(model.head, pose.part("head"));
        setPart(model.body, pose.part("body"));
        setPart(model.rightArm, pose.part("rightArm"));
        setPart(model.leftArm, pose.part("leftArm"));
        setPart(model.rightLeg, pose.part("rightLeg"));
        setPart(model.leftLeg, pose.part("leftLeg"));

        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }

    private static void setPart(ModelPart part, float[] degrees) {
        part.xRot = degrees[0] * DEG_TO_RAD;
        part.yRot = degrees[1] * DEG_TO_RAD;
        part.zRot = degrees[2] * DEG_TO_RAD;
    }
}
