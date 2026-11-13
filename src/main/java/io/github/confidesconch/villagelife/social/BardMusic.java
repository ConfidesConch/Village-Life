package io.github.confidesconch.villagelife.social;

import java.util.Optional;

import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.block.LevelEvent;

/**
 * One song of the bard (M23): with a music disc the real record plays from where he stands (like a
 * jukebox there, players see "Now playing"); without one he plays a little tune on a horn with flute
 * notes. Music notes rise above him, and every resident within earshot is in a good mood until morning.
 */
final class BardMusic {
    private final ResidentEntity bard;
    /** Where the record plays, so it can be stopped there; null for a tune or while silent. */
    private BlockPos recordAt;
    private int[] tune;
    private int ticksLeft;
    private int ticks;
    /** True while he holds the disc or the horn (bug review 13.11.2026: the disc stayed in his hand). */
    private boolean holding;

    BardMusic(ResidentEntity bard) {
        this.bard = bard;
    }

    boolean isPlaying() {
        return ticksLeft > 0;
    }

    /** Starts the disc (or a tune if {@code disc} is empty or no record). */
    void start(ServerLevel level, ItemStack disc) {
        stop(level);
        ticks = 0;
        Optional<Holder<JukeboxSong>> song = disc.isEmpty() ? Optional.empty() : JukeboxSong.fromStack(disc);
        if (song.isPresent()) {
            recordAt = bard.blockPosition();
            int id = level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG).getId(song.get().value());
            level.levelEvent(null, LevelEvent.SOUND_PLAY_JUKEBOX_SONG, recordAt, id);
            ticksLeft = Math.max(20, song.get().value().lengthInTicks());
            bard.setItemSlot(EquipmentSlot.MAINHAND, disc.copyWithCount(1));
            holding = true;
        } else {
            tune = BardRules.tune(level.getRandom().nextLong(), BardRules.TUNE_TICKS / BardRules.BEAT_TICKS);
            ticksLeft = BardRules.TUNE_TICKS;
            bard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOAT_HORN));
            holding = true;
        }
    }

    void tick(ServerLevel level) {
        if (ticksLeft <= 0) {
            return;
        }
        ticksLeft--;
        ticks++;
        if (tune != null && ticks % BardRules.BEAT_TICKS == 0) {
            int note = tune[(ticks / BardRules.BEAT_TICKS) % tune.length];
            if (note >= 0) {
                level.playSound(null, bard.getX(), bard.getY() + 1.0, bard.getZ(), SoundEvents.NOTE_BLOCK_FLUTE,
                        SoundSource.RECORDS, 1.0F, BardRules.pitch(note));
                noteParticle(level, note / 24.0);
            }
        } else if (tune == null && ticks % 20 == 0) {
            noteParticle(level, level.getRandom().nextInt(4) / 24.0);
        }
        if (ticks % 100 == 1) {
            letListen(level);
        }
        if (ticksLeft == 0) {
            stop(level);
        }
    }

    /** Ends the song; a record is stopped where it played. */
    void stop(ServerLevel level) {
        if (recordAt != null) {
            level.levelEvent(LevelEvent.SOUND_STOP_JUKEBOX_SONG, recordAt, 0);
            recordAt = null;
        }
        if (holding) {
            bard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            holding = false;
        }
        tune = null;
        ticksLeft = 0;
    }

    private void noteParticle(ServerLevel level, double color) {
        level.sendParticles(ParticleTypes.NOTE, bard.getX(), bard.getY() + bard.getBbHeight() + 0.4, bard.getZ(),
                0, color, 0.0, 0.0, 1.0); // count 0: the offset is the note's colour, as at a jukebox
    }

    /** Everyone within earshot (the bard too) enjoys the music until the next morning; counts once. */
    private void letListen(ServerLevel level) {
        long morning = RelationshipRules.nextMorning(level.getGameTime(), level.getOverworldClockTime());
        for (ResidentEntity listener : level.getEntitiesOfClass(ResidentEntity.class,
                bard.getBoundingBox().inflate(BardRules.HEAR_DISTANCE), resident -> !resident.isSleeping())) {
            SocialService.markMusic(listener.getUUID(), morning);
        }
    }
}
