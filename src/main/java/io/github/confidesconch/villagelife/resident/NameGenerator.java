package io.github.confidesconch.villagelife.resident;

import java.util.List;
import java.util.Random;

/**
 * Picks random resident names (Minecraft-free, unit tested).
 * The lists are fixed for now; moving them into data packs is planned (see docs/FEATURES.md).
 */
public final class NameGenerator {
    private NameGenerator() {}

    public record Name(String first, String last) {
        public String full() {
            return first + " " + last;
        }
    }

    /** Women's first names (M21c: the first name decides whether a resident is a woman or a man). */
    static final List<String> FEMALE_NAMES = List.of(
            "Anna", "Clara", "Emma", "Greta", "Ida", "Klara", "Mia", "Olga", "Rosa", "Tilda",
            "Vera", "Yara", "Zoe", "Bea", "Dana", "Elif", "Hanna", "Jana", "Lena", "Nora", "Pia");

    /** Men's first names. */
    static final List<String> MALE_NAMES = List.of(
            "Ben", "David", "Felix", "Hannes", "Jonas", "Lukas", "Noah", "Paul", "Samuel", "Udo",
            "Willi", "Amir", "Cem", "Finn", "Ilias", "Kai", "Malik", "Oskar", "Rafael", "Theo", "Moritz");

    static final List<String> FIRST_NAMES = java.util.stream.Stream.concat(FEMALE_NAMES.stream(), MALE_NAMES.stream()).toList();

    static final List<String> LAST_NAMES = List.of(
            "Bauer", "Becker", "Brunner", "Fischer", "Förster", "Gärtner", "Hofmann", "Jäger", "Keller", "Koch",
            "Krämer", "Lehmann", "Maurer", "Müller", "Neumann", "Richter", "Schäfer", "Schmidt", "Schneider", "Schuster",
            "Seiler", "Steiner", "Vogel", "Wagner", "Weber", "Winkler", "Wolf", "Zimmermann", "Berg", "Brandt");

    /**
     * Hidden names (wish of the owner, 07.11.2026): the owner and a good friend live as residents in the
     * world. Each turns up at most once per village, with a chance of {@value #SPECIAL_CHANCE} per newcomer.
     */
    static final List<Name> SPECIAL_NAMES = List.of(new Name("Jeremy", "Tripp"), new Name("Lukas", "Weigelt"));
    static final double SPECIAL_CHANCE = 0.05;

    /** Like {@link #random(Random)}, but now and then one of the {@link #SPECIAL_NAMES} not yet in {@code taken}. */
    public static Name newcomer(Random random, java.util.Collection<String> taken) {
        if (random.nextDouble() < SPECIAL_CHANCE) {
            List<Name> free = SPECIAL_NAMES.stream().filter(n -> !taken.contains(n.full())).toList();
            if (!free.isEmpty()) {
                return free.get(random.nextInt(free.size()));
            }
        }
        return random(random);
    }

    /** A woman or a man (half and half) with a fitting first name. */
    public static Name random(Random random) {
        return random(random, random.nextBoolean());
    }

    public static Name random(Random random, boolean female) {
        List<String> first = female ? FEMALE_NAMES : MALE_NAMES;
        return new Name(first.get(random.nextInt(first.size())), LAST_NAMES.get(random.nextInt(LAST_NAMES.size())));
    }

    /** True for women's first names (M21c); names the lists do not know count as men's. */
    public static boolean isFemale(String firstName) {
        return FEMALE_NAMES.contains(firstName);
    }
}
