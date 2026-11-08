package io.github.confidesconch.villagelife.profession.work;

import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Work of the merchant (M15a): stands at the market's cartography table during working hours and
 * looks at players nearby. Trading itself happens on the market page of the village book, which only
 * works while the merchant is at the market ({@code market.MarketService}).
 */
public class MerchantGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:merchant";

    private static final double STAND_DISTANCE = 2.5;
    private static final double LOOK_DISTANCE = 8.0;

    public MerchantGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, ItemTags.SHOVELS, Items.EMERALD); // an emerald in the hand
    }

    @Override
    public boolean canUse() {
        return isWorkTime() && resident.level() instanceof ServerLevel level && table(level) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos table = table(level);
        if (table == null) {
            return;
        }
        if (!table.closerToCenterThan(resident.position(), STAND_DISTANCE)) {
            moveTowards(table, 1);
            return;
        }
        resident.getNavigation().stop();
        Player customer = level.getNearestPlayer(resident, LOOK_DISTANCE);
        if (customer != null) {
            resident.getLookControl().setLookAt(customer);
        }
    }

    private BlockPos table(ServerLevel level) {
        for (BlockPos pos : workplace().workstations()) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.CARTOGRAPHY_TABLE)) {
                return pos;
            }
        }
        return null;
    }
}
