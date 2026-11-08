package io.github.confidesconch.villagelife.client;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws residents in the player shape (M4). Since M21c every resident has an own skin painted from
 * building blocks ({@link ResidentSkins}) with work clothes; women use the slim model (like Alex). The
 * slim model is a second renderer this one hands women over to.
 *
 * <p>The render state is deliberately no {@code AvatarRenderState}: Minecraft draws every such state with
 * its own player renderer and the default skin (Steve), whatever renderer created it.
 */
public class ResidentRenderer extends HumanoidMobRenderer<ResidentEntity, ResidentRenderer.ResidentRenderState, ResidentModel> {

    /** Render state with what the skin needs. */
    public static class ResidentRenderState extends HumanoidRenderState {
        public int skinSeed;
        public boolean female;
        public String profession = "";
    }

    private final Slim slim;

    public ResidentRenderer(EntityRendererProvider.Context context) {
        super(context, new ResidentModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        // M20a: guards wear armor from the village storage – drawn like a player's armor
        addLayer(new HumanoidArmorLayer<>(this,
                ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel<ResidentRenderState>::new),
                context.getEquipmentRenderer()));
        slim = new Slim(context);
    }

    /** The same resident with slim arms (women). */
    private static final class Slim extends HumanoidMobRenderer<ResidentEntity, ResidentRenderState, ResidentModel> {
        Slim(EntityRendererProvider.Context context) {
            super(context, new ResidentModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.5F);
            addLayer(new HumanoidArmorLayer<>(this,
                    ArmorModelSet.bake(ModelLayers.PLAYER_SLIM_ARMOR, context.getModelSet(), HumanoidModel<ResidentRenderState>::new),
                    context.getEquipmentRenderer()));
        }

        @Override
        public ResidentRenderState createRenderState() {
            return new ResidentRenderState();
        }

        @Override
        public Identifier getTextureLocation(ResidentRenderState state) {
            return ResidentSkins.texture(state.skinSeed, state.female, state.profession);
        }
    }

    @Override
    public ResidentRenderState createRenderState() {
        return new ResidentRenderState();
    }

    @Override
    public void extractRenderState(ResidentEntity resident, ResidentRenderState state, float partialTicks) {
        super.extractRenderState(resident, state, partialTicks);
        state.skinSeed = resident.getSkin();
        state.female = resident.isFemale();
        state.profession = resident.getProfessionId();
    }

    @Override
    public void submit(ResidentRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.female) {
            slim.submit(state, poseStack, collector, camera);
        } else {
            super.submit(state, poseStack, collector, camera);
        }
    }

    @Override
    public Identifier getTextureLocation(ResidentRenderState state) {
        return ResidentSkins.texture(state.skinSeed, state.female, state.profession);
    }
}
