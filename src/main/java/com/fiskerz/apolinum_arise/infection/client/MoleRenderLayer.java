package com.fiskerz.apolinum_arise.infection.client;

import java.util.List;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.infection.MoleData;
import com.fiskerz.apolinum_arise.infection.MoleSlot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws each of a player's moles as a small cube riding on the relevant body part. Added to BOTH the
 * wide and slim player renderers (see the AddLayers hook), so it renders for either model variant; the
 * only per-variant difference, the 1px-thinner arm, is corrected per mole via the skin model. Because
 * the mole list comes from {@link MoleClientState} keyed by entity id, a player sees every tracked
 * player's moles, not just their own.
 */
public class MoleRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation MOLE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "textures/entity/mole.png");

    // A 1px unit cube centered on the origin; scaled per mole at render time.
    private final ModelPart moleCube;

    public MoleRenderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("mole",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F),
                PartPose.ZERO);
        this.moleCube = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("mole");
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        List<MoleData> moles = MoleClientState.get(player.getId());
        if (moles.isEmpty()) {
            return;
        }

        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(MOLE_TEXTURE));

        for (MoleData mole : moles) {
            MoleSlot slot = MoleSlot.byIndex(mole.slot());
            ModelPart part = partFor(model, slot.part());
            if (part == null) {
                continue;
            }
            // Slim arms are 1px thinner, so their outer face sits 1px closer to the body.
            float offsetX = slot.x();
            if (slot.armOuter() && slim) {
                offsetX += offsetX > 0 ? -1.0F : 1.0F;
            }

            poseStack.pushPose();
            part.translateAndRotate(poseStack);
            poseStack.translate(offsetX / 16.0F, slot.y() / 16.0F, slot.z() / 16.0F);
            float size = Math.max(0.01F, mole.size());
            poseStack.scale(size, size, size);
            moleCube.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }

    private static ModelPart partFor(PlayerModel<AbstractClientPlayer> model, MoleSlot.Part part) {
        return switch (part) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_LEG -> model.rightLeg;
            case LEFT_LEG -> model.leftLeg;
        };
    }
}
