package com.fiskerz.apolinum_arise.bloodmoon.client;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class BloodMoonClientEvents {
    private static final ResourceLocation MOON_TEXTURE = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");

    private BloodMoonClientEvents() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || !BloodMoonClientState.isActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(event.getModelViewMatrix());
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(minecraft.level.getTimeOfDay(partialTick) * 360.0F));

        // Vanilla has already drawn the moon additively in white (renderSky: blendFunc(SRC_ALPHA, ONE),
        // shader color (1, 1, 1, 1 - rain)), so adding red on top could only wash it out. Re-drawing the
        // identical quad with reverse-subtract blending removes most of the green/blue vanilla added,
        // netting the same texture multiplied by roughly (1.0, 0.18, 0.18): a red moon, no new asset.
        float rainFactor = 1.0F - minecraft.level.getRainLevel(partialTick);
        RenderSystem.depthMask(false); // sky pass wrote no depth; a depth-writing quad here would occlude distant terrain
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.blendEquation(GlConst.GL_FUNC_REVERSE_SUBTRACT);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, MOON_TEXTURE);
        RenderSystem.setShaderColor(0.0F, 0.82F, 0.82F, rainFactor);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        int moonPhase = minecraft.level.getMoonPhase();
        int textureColumn = moonPhase % 4;
        int textureRow = moonPhase / 4 % 2;
        float u0 = (float) textureColumn / 4.0F;
        float v0 = (float) textureRow / 2.0F;
        float u1 = (float) (textureColumn + 1) / 4.0F;
        float v1 = (float) (textureRow + 1) / 2.0F;
        float size = 20.0F;
        var matrix = poseStack.last().pose();
        bufferBuilder.addVertex(matrix, -size, -100.0F, size).setUv(u1, v1);
        bufferBuilder.addVertex(matrix, size, -100.0F, size).setUv(u0, v1);
        bufferBuilder.addVertex(matrix, size, -100.0F, -size).setUv(u0, v0);
        bufferBuilder.addVertex(matrix, -size, -100.0F, -size).setUv(u1, v0);
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());

        RenderSystem.blendEquation(GlConst.GL_FUNC_ADD);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        poseStack.popPose();
    }

    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD || !BloodMoonClientState.isActive()) {
            return;
        }

        float red = event.getRed();
        float green = event.getGreen();
        float blue = event.getBlue();
        event.setRed(Mth.clamp(red * 0.85F + 0.22F, 0.0F, 1.0F));
        event.setGreen(Mth.clamp(green * 0.45F + red * 0.12F, 0.0F, 1.0F));
        event.setBlue(Mth.clamp(blue * 0.32F + red * 0.08F, 0.0F, 1.0F));
    }
}