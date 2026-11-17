package io.github.confidesconch.villagelife.profession.work;

/**
 * Extra ore luck of the miner (hotfix 1.5.1, owner's wish "he digs too little – a chance per block,
 * better the deeper he digs", amount "a lot" = setting {@code minerOreLuckPercent} 200): for every stone
 * block he digs, at most one extra ore, picked by height. Free of Minecraft so it can be unit tested; the
 * names are item ids without "minecraft:".
 *
 * <p>Normal chances (100 %) per stone block: coal 2 % (above Y 0), copper 1 % (Y −16 to 96), iron 3 %
 * (below Y 72), gold 0.8 % and lapis 0.8 % (below Y 32), redstone 1 % (below Y 16), diamond 0.3 %
 * (below Y 0) – deep down about one extra ore in 14 blocks, twice that with the default 200 %.
 */
public final class OreLuck {
    private OreLuck() {}

    private record Ore(String item, double chance, int belowY, int fromY) {}

    private static final Ore[] ORES = {
            new Ore("diamond", 0.003, 0, Integer.MIN_VALUE),
            new Ore("redstone", 0.01, 16, Integer.MIN_VALUE),
            new Ore("raw_gold", 0.008, 32, Integer.MIN_VALUE),
            new Ore("lapis_lazuli", 0.008, 32, Integer.MIN_VALUE),
            new Ore("raw_iron", 0.03, 72, Integer.MIN_VALUE),
            new Ore("raw_copper", 0.01, 96, -16),
            new Ore("coal", 0.02, Integer.MAX_VALUE, 0),
    };

    /**
     * The extra ore for a stone block dug at height {@code y}, or null.
     *
     * @param roll    a random number in [0, 1)
     * @param percent the setting (100 = normal chances)
     */
    public static String roll(int y, double roll, int percent) {
        if (percent <= 0) {
            return null;
        }
        double factor = percent / 100.0;
        double sum = 0;
        for (Ore ore : ORES) {
            if (y < ore.belowY() && y >= ore.fromY()) {
                sum += ore.chance() * factor;
                if (roll < sum) {
                    return ore.item();
                }
            }
        }
        return null;
    }
}
