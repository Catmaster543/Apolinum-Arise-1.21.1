package com.fiskerz.apolinum_arise.mosquito.client;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.mosquito.MosquitoEntity;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

// Explicit resource paths rather than DefaultedEntityGeoModel: GeckoLib 4.9's resource loader
// (GeckoLibCache) only scans "geo/" and "animations/" - these are the paths its reload listener
// actually reads, so the user's Blockbench exports must land exactly here.
public class MosquitoModel extends GeoModel<MosquitoEntity> {
    public static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "geo/mosquito.geo.json");
    public static final ResourceLocation ANIMATIONS =
            ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "animations/mosquito.animation.json");
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Apolinumarise.MODID, "textures/entity/mosquito_texture.png");

    @Override
    public ResourceLocation getModelResource(MosquitoEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(MosquitoEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(MosquitoEntity animatable) {
        return ANIMATIONS;
    }
}
