package io.github.confidesconch.villagelife.defense;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.village.EventLog;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The alarm (M20b, owner's choice "4a"): while bandits attack, and for two hours after the owner or a
 * manager rings any bell in the village, everybody but the defenders goes home. Ringing again during a
 * manual alarm ends it. Not saved – after a restart there is no alarm.
 */
public final class AlarmService {
    private AlarmService() {}

    /** A rung bell keeps everybody inside for two in-game hours. */
    private static final long MANUAL_ALARM_TICKS = 2000;

    /** Ticks between two strokes of the bell. */
    private static final int RING_INTERVAL = 30;

    private static final Map<UUID, Long> MANUAL_UNTIL = new HashMap<>();
    /** Bells still to ring: level, bell positions (or the village center without bells), strokes left. */
    private static final List<Ringing> RINGING = new ArrayList<>();

    /** Forgets everything kept in memory for the stopped server (bug review 08.11.2026). */
    public static void clear() {
        MANUAL_UNTIL.clear();
        RINGING.clear(); // holds the level of the stopped server
    }

    private static final class Ringing {
        final ServerLevel level;
        final List<BlockPos> bells;
        final BlockPos center;
        int strokesLeft;
        long next;

        Ringing(ServerLevel level, List<BlockPos> bells, BlockPos center, int strokes, long next) {
            this.level = level;
            this.bells = bells;
            this.center = center;
            this.strokesLeft = strokes;
            this.next = next;
        }
    }
    private static volatile long gameTime;

    /** Remembers the game time for {@link #isAlarm}, which the residents ask without a level. */
    static void setGameTime(long now) {
        gameTime = now;
    }

    static long gameTime() {
        return gameTime;
    }

    /**
     * Rings every bell of the village {@code strokes} times, one stroke every 1.5 s – like a church clock
     * (owner's wish). {@code alreadyRung}: a bell the player just rang itself (counts as the first stroke).
     */
    public static void ring(ServerLevel level, Village village, int strokes, BlockPos alreadyRung) {
        List<BlockPos> bells = new ArrayList<>();
        for (Building building : village.buildings()) {
            for (BlockPos pos : building.workstations()) {
                if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.BELL)) {
                    bells.add(pos);
                }
            }
        }
        if (alreadyRung != null && !bells.contains(alreadyRung)) {
            bells.add(alreadyRung);
        }
        long first = level.getGameTime() + (alreadyRung == null ? 0 : RING_INTERVAL);
        RINGING.add(new Ringing(level, bells, village.center(), alreadyRung == null ? strokes : strokes - 1, first));
    }

    /** Every tick: the next strokes that are due. */
    static void tick() {
        Iterator<Ringing> it = RINGING.iterator();
        while (it.hasNext()) {
            Ringing ringing = it.next();
            if (ringing.level.getGameTime() < ringing.next) {
                continue;
            }
            if (ringing.bells.isEmpty()) {
                ringing.level.playSound(null, ringing.center, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 1.0F);
            }
            for (BlockPos bell : ringing.bells) {
                if (ringing.level.isLoaded(bell) && ringing.level.getBlockState(bell).getBlock() instanceof BellBlock block) {
                    block.attemptToRing(ringing.level, bell, null); // swings the bell and makes nearby bandits glow
                }
            }
            ringing.next = ringing.level.getGameTime() + RING_INTERVAL;
            if (--ringing.strokesLeft <= 0) {
                it.remove();
            }
        }
    }

    public static boolean isAlarm(UUID village) {
        return RaidService.isUnderAttack(village) || MANUAL_UNTIL.getOrDefault(village, 0L) > gameTime;
    }

    /** Right click on a bell inside a village by its owner or a manager: alarm on, or off again. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getLevel() instanceof ServerLevel level)
                || !level.getBlockState(event.getPos()).is(Blocks.BELL)) {
            return;
        }
        Village village = VillageManager.get(level).findAt(event.getPos()).orElse(null);
        if (village == null || !village.mayManage(event.getEntity().getUUID())) {
            return;
        }
        toggleManual(level, village, event.getEntity(), event.getPos().immutable());
    }

    /** True while a manual alarm (bell or village window) runs. */
    public static boolean isManual(UUID village) {
        return MANUAL_UNTIL.getOrDefault(village, 0L) > gameTime;
    }

    /**
     * Starts a manual alarm, or ends a running one (bell or the window's alarm button, M21b3). The caller
     * checks that {@code player} may manage the village. {@code rungBell}: the bell the player just rang, or null.
     */
    public static void toggleManual(ServerLevel level, Village village, Player player, BlockPos rungBell) {
        long now = level.getGameTime();
        boolean active = MANUAL_UNTIL.getOrDefault(village.id(), 0L) > now;
        if (active) {
            MANUAL_UNTIL.remove(village.id());
        } else {
            MANUAL_UNTIL.put(village.id(), now + MANUAL_ALARM_TICKS);
            ring(level, village, 5, rungBell); // the player's stroke and four more (or five without a rung bell)
        }
        player.sendOverlayMessage(Component.translatable(active ? "villagelife.defense.alarm_off"
                : "villagelife.defense.alarm_on").withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        EventLog.record(level.getServer(), village, NotificationTopic.DEFENSE.id(), Component.translatable(
                active ? "villagelife.event.alarm_off" : "villagelife.event.alarm_on", player.getName().getString()));
    }
}
