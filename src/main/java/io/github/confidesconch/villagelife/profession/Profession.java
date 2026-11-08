package io.github.confidesconch.villagelife.profession;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.confidesconch.villagelife.resident.schedule.Schedules;

import net.minecraft.resources.Identifier;

/**
 * A profession loaded from {@code data/<namespace>/profession/<name>.json} (M8). The file name is the
 * profession id. A workplace building whose workstation is one of {@code workstations} employs this
 * profession. {@code work_radius}: how far from the workplace the worker looks for work.
 * <p>
 * The other fields only describe the profession for the "Professions" chapter of the book (M13b), all
 * item ids: {@code tool} (basic tool), {@code materials} (what the worker fetches from storage),
 * {@code products} (what it delivers), {@code nearby} (what must be near the workplace, e.g. a water
 * bucket for water), {@code work_area} (needs an assigned area, M13c), {@code order} (position in the
 * book), {@code essential} (a basic profession a new village needs first: highlighted in the book and at
 * the top of the default job priorities, M19a), {@code defender} (guards: fight monsters, are only knocked out,
 * count as guards for the village stage, M20a).
 */
public record Profession(List<Identifier> workstations, Identifier schedule, int workRadius,
                         Optional<Identifier> tool, List<Identifier> materials, List<Identifier> products,
                         List<Identifier> nearby, boolean workArea, int order, List<Craft> crafts, boolean essential,
                         boolean defender) {

    /** Work problem of professions with {@code work_area} when none is assigned (book: {@code villagelife.problem.work.no_work_area}). */
    public static final String ISSUE_NO_WORK_AREA = "no_work_area";

    public static final Codec<Profession> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().fieldOf("workstations").forGetter(Profession::workstations),
            Identifier.CODEC.optionalFieldOf("schedule", Schedules.DEFAULT_ID).forGetter(Profession::schedule),
            Codec.intRange(4, 64).optionalFieldOf("work_radius", 24).forGetter(Profession::workRadius),
            Identifier.CODEC.optionalFieldOf("tool").forGetter(Profession::tool),
            Identifier.CODEC.listOf().optionalFieldOf("materials", List.of()).forGetter(Profession::materials),
            Identifier.CODEC.listOf().optionalFieldOf("products", List.of()).forGetter(Profession::products),
            Identifier.CODEC.listOf().optionalFieldOf("nearby", List.of()).forGetter(Profession::nearby),
            Codec.BOOL.optionalFieldOf("work_area", false).forGetter(Profession::workArea),
            Codec.INT.optionalFieldOf("order", 100).forGetter(Profession::order),
            Craft.CODEC.listOf().optionalFieldOf("crafts", List.of()).forGetter(Profession::crafts),
            Codec.BOOL.optionalFieldOf("essential", false).forGetter(Profession::essential),
            Codec.BOOL.optionalFieldOf("defender", false).forGetter(Profession::defender)
    ).apply(instance, Profession::new));

    public Profession {
        workstations = List.copyOf(workstations);
        materials = List.copyOf(materials);
        products = List.copyOf(products);
        nearby = List.copyOf(nearby);
        crafts = List.copyOf(crafts);
    }
}
