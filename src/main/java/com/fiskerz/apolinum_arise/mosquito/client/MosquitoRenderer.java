package com.fiskerz.apolinum_arise.mosquito.client;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.mosquito.MosquitoEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class MosquitoRenderer extends GeoEntityRenderer<MosquitoEntity> {
    private static boolean warnedMissingAssets;

    public MosquitoRenderer(EntityRendererProvider.Context context) {
        super(context, new MosquitoModel());
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(MosquitoEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // Until the user's real Blockbench exports are in place, GeckoLib would throw on a missing
        // geo/animation file every frame. Match the mod's missing-asset precedent instead:
        // warn once, render nothing, no crash. (A missing texture just renders checkered.)
        if (!GeckoLibCache.getBakedModels().containsKey(MosquitoModel.MODEL)
                || !GeckoLibCache.getBakedAnimations().containsKey(MosquitoModel.ANIMATIONS)) {
            if (!warnedMissingAssets) {
                warnedMissingAssets = true;
                Apolinumarise.LOGGER.warn("Mosquito assets missing (expected {} and {}); mosquitoes will be invisible until they are added",
                        MosquitoModel.MODEL, MosquitoModel.ANIMATIONS);
            }
            return;
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
