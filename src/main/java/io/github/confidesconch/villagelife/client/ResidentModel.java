package io.github.confidesconch.villagelife.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The player shape (with the second skin layer: jacket, sleeves, trousers, hat) for residents. Not the
 * vanilla {@code PlayerModel}: its {@code AvatarRenderState} makes Minecraft draw the entity with its own
 * player renderer and the default skin, so the resident's skin would never be used.
 */
public class ResidentModel extends HumanoidModel<ResidentRenderer.ResidentRenderState> {
    private final boolean slim;

    /** {@code root} baked from the player layer (wide) or the slim player layer. */
    public ResidentModel(ModelPart root, boolean slim) {
        super(root, RenderTypes::entityTranslucent);
        this.slim = slim;
    }

    @Override
    public void translateToHand(HumanoidRenderState state, HumanoidArm arm, PoseStack poseStack) {
        if (!slim) {
            super.translateToHand(state, arm, poseStack);
            return;
        }
        // slim arms are half a pixel narrower – hold items in the middle of the hand (like PlayerModel)
        root().translateAndRotate(poseStack);
        ModelPart part = getArm(arm);
        float offset = 0.5F * (arm == HumanoidArm.RIGHT ? 1 : -1);
        part.x += offset;
        part.translateAndRotate(poseStack);
        part.x -= offset;
    }
}
