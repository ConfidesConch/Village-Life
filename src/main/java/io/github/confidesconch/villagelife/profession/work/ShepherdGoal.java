package io.github.confidesconch.villagelife.profession.work;

import java.util.List;

import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;

/**
 * Work of the shepherd (M13c): shears the sheep in its work area, breeds them with wheat from storage
 * up to the village's herd limit (slaughtering: the butcher, M27). Wool goes to the hut or storage.
 * M28b (owner's choice "B"): while the village has a breeder, the breeder looks after the sheep and the
 * shepherd only shears.
 * Basic tool: shears that never wear out.
 */
public class ShepherdGoal extends HerdGoal<Sheep> {
    public static final String PROFESSION = "villagelife:shepherd";

    public ShepherdGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, Tags.Items.TOOLS_SHEAR, Items.SHEARS, Sheep.class);
    }

    /** Only while no resident works as breeder (M28b). */
    @Override
    protected boolean caresForHerd() {
        Village village = resident.brain().village();
        return village == null || village.residents().stream()
                .noneMatch(r -> r.job().filter(job -> BreederGoal.PROFESSION.equals(job.profession())).isPresent());
    }

    @Override
    protected boolean isFeed(ItemStack stack) {
        return stack.is(Items.WHEAT);
    }

    @Override
    protected Sheep specialTarget(List<Sheep> adults) {
        return adults.stream().filter(Sheep::readyForShearing).findFirst().orElse(null);
    }

    @Override
    protected void doSpecial(ServerLevel level, Sheep sheep) {
        if (sheep.readyForShearing()) {
            sheep.shear(level, SoundSource.NEUTRAL, resident.getMainHandItem().copy());
        }
    }
}
