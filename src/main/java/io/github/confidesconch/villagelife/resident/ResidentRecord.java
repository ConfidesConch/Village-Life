package io.github.confidesconch.villagelife.resident;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * The village's file on a resident. Exists even while the resident's chunk is not loaded.
 * {@code id} equals the entity UUID. {@code home}/{@code bed}: house (building id) and bed head
 * position, empty while homeless (M6). {@code job}: workplace and profession, empty while
 * unemployed (M8). Needs etc. follow in later milestones.
 */
public record ResidentRecord(UUID id, String firstName, String lastName, int skin, long joinedGameTime,
                             Optional<UUID> home, Optional<BlockPos> bed, Optional<Job> job,
                             Optional<UUID> partner, List<UUID> parents, long grownUpGameTime, int education) {

    public static final Codec<ResidentRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ResidentRecord::id),
            Codec.STRING.fieldOf("first_name").forGetter(ResidentRecord::firstName),
            Codec.STRING.fieldOf("last_name").forGetter(ResidentRecord::lastName),
            Codec.INT.optionalFieldOf("skin", 0).forGetter(ResidentRecord::skin),
            Codec.LONG.fieldOf("joined_game_time").forGetter(ResidentRecord::joinedGameTime),
            // optional: residents saved before M6 have no home yet
            UUIDUtil.CODEC.optionalFieldOf("home").forGetter(ResidentRecord::home),
            BlockPos.CODEC.optionalFieldOf("bed").forGetter(ResidentRecord::bed),
            // optional: residents saved before M8 have no job yet
            Job.CODEC.optionalFieldOf("job").forGetter(ResidentRecord::job),
            // optional: residents saved before M18 have no family (and are adults)
            UUIDUtil.CODEC.optionalFieldOf("partner").forGetter(ResidentRecord::partner),
            UUIDUtil.CODEC.listOf().optionalFieldOf("parents", List.of()).forGetter(ResidentRecord::parents),
            Codec.LONG.optionalFieldOf("grown_up_game_time", 0L).forGetter(ResidentRecord::grownUpGameTime),
            Codec.intRange(0, 100).optionalFieldOf("education", 0).forGetter(ResidentRecord::education) // M18c
    ).apply(instance, ResidentRecord::new));

    public ResidentRecord {
        parents = List.copyOf(parents);
    }

    /** A new, homeless adult resident. */
    public static ResidentRecord create(UUID id, String firstName, String lastName, int skin, long joinedGameTime) {
        return new ResidentRecord(id, firstName, lastName, skin, joinedGameTime, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), List.of(), 0L, 0);
    }

    /** A child born in the village (M18): grows up at {@code grownUpGameTime}. */
    public static ResidentRecord child(UUID id, String firstName, String lastName, int skin, long bornGameTime,
                                       List<UUID> parents, long grownUpGameTime) {
        return new ResidentRecord(id, firstName, lastName, skin, bornGameTime, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), parents, grownUpGameTime, 0);
    }

    /** True while the resident is a child (M18). */
    public boolean isChild(long gameTime) {
        return gameTime < grownUpGameTime;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public ResidentRecord withHome(Optional<UUID> newHome, Optional<BlockPos> newBed) {
        return new ResidentRecord(id, firstName, lastName, skin, joinedGameTime, newHome, newBed.map(BlockPos::immutable), job,
                partner, parents, grownUpGameTime, education);
    }

    public ResidentRecord withJob(Optional<Job> newJob) {
        return new ResidentRecord(id, firstName, lastName, skin, joinedGameTime, home, bed, newJob,
                partner, parents, grownUpGameTime, education);
    }

    /** Education 0–100 from school (M18c). */
    public ResidentRecord withEducation(int newEducation) {
        return new ResidentRecord(id, firstName, lastName, skin, joinedGameTime, home, bed, job,
                partner, parents, grownUpGameTime, Math.max(0, Math.min(100, newEducation)));
    }

    /** Partner from M18 on (empty = single). */
    public ResidentRecord withPartner(Optional<UUID> newPartner) {
        return new ResidentRecord(id, firstName, lastName, skin, joinedGameTime, home, bed, job,
                newPartner, parents, grownUpGameTime, education);
    }
}
