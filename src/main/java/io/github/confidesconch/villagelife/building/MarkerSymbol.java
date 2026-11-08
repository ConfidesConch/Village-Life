package io.github.confidesconch.villagelife.building;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/**
 * The picture in a building marker's frame (M18d, owner's wish "a fitting symbol for every profession"):
 * house, storage, or the profession of a workplace (from its work station). Workplaces of professions
 * added by data packs show the general {@link #WORKPLACE} hammer.
 */
public enum MarkerSymbol implements StringRepresentable {
    HOUSE, STORAGE, WORKPLACE,
    LUMBERJACK, FARMER, FISHERMAN, SHEPHERD, BREEDER, MINER, COOK, SMITH, BAKER, CARPENTER, STOREKEEPER, MERCHANT,
    INNKEEPER, TEACHER, GUARD, ARCHER, CAPTAIN,
    /** M21d: fenced areas; never a profession's symbol. */
    FIELD, PASTURE;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The symbol of a profession id like {@code villagelife:baker}, or the general workplace symbol. */
    public static MarkerSymbol forProfession(String professionId) {
        String path = professionId.substring(professionId.indexOf(':') + 1);
        for (MarkerSymbol symbol : values()) {
            if (symbol.getSerializedName().equals(path) && symbol.ordinal() > WORKPLACE.ordinal() && symbol.ordinal() < FIELD.ordinal()) {
                return symbol;
            }
        }
        return WORKPLACE;
    }
}
