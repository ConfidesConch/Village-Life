package io.github.confidesconch.villagelife.defense;

import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Monsters cannot damage a village and do not appear in it (M20b2, owner's wishes): creeper (and other monster) explosions do not
 * destroy blocks inside a village, and monsters in a village cannot change blocks (endermen taking
 * blocks, zombies breaking doors …). Players' explosions (TNT) are not affected; entities are still hurt.
 */
public final class VillageProtection {
    private VillageProtection() {}

    /**
     * No monsters appear inside a village, also not at night (owner's wish): natural spawns, patrols and
     * reinforcements are refused. Spawners, spawn eggs and commands still work (e.g. for tests).
     */
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        EntitySpawnReason reason = event.getSpawnType();
        boolean natural = reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.PATROL
                || reason == EntitySpawnReason.REINFORCEMENT;
        if (!natural || !(event.getEntity() instanceof Enemy)) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        if (!level.getServer().isSameThread()) {
            return; // village data only on the server thread
        }
        if (VillageManager.get(level).findAt(BlockPos.containing(event.getX(), event.getY(), event.getZ())).isPresent()) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /** No mob griefing by monsters standing in a village. */
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Enemy && entity.level() instanceof ServerLevel level
                && VillageManager.get(level).findAt(entity.blockPosition()).isPresent()) {
            event.setCanGrief(false);
        }
    }

    /** A monster's explosion leaves every block inside a village alone – also from just outside. */
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        boolean byMonster = event.getExplosion().getIndirectSourceEntity() instanceof Enemy
                || event.getExplosion().getDirectSourceEntity() instanceof Enemy;
        if (byMonster) {
            VillageManager villages = VillageManager.get(level);
            event.getAffectedBlocks().removeIf(pos -> villages.findAt(pos).isPresent());
        }
    }
}
