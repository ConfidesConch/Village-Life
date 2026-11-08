package io.github.confidesconch.villagelife.resident;

import java.util.Random;

/**
 * What a resident looks like (M21c, owner's choice "own building blocks"): skin tone, face, hair, top,
 * bottom and shoes, each with a colour – all derived from the resident's look number ("skin", saved since
 * M4) and whether she or he is a woman. The same number always gives the same look, so nothing new has to
 * be saved; residents from older saves keep their number and get a look from it. Pure logic, unit tested;
 * the client paints the skin from these values ({@code ResidentSkins}).
 */
public record ResidentLook(boolean female, int skinTone, int face, int hair, int hairColor, int top, int topColor,
                           int bottom, int bottomColor, int shoeColor) {

    public static final int[] SKIN_TONES = {0xF6D7B8, 0xEBC29A, 0xD9A877, 0xBD8657, 0x8D5A36, 0x5E3B24};
    public static final int[] HAIR_COLORS = {0x2B1D14, 0x4A3020, 0x6E4628, 0x9A6A3A, 0xD2B06A, 0xA2482A, 0x8A8A8A, 0xE6D6A8};
    public static final int[] CLOTH_COLORS = {0x7A4B2A, 0x4E6B3A, 0x3B5A7A, 0x8A3B2E, 0xC9B48A, 0x5E5E5E, 0x2F4F4F, 0xA0703C,
            0x7A2E3B, 0x456B6B, 0xE6E0D0, 0x3E3A5A};
    public static final int[] PANTS_COLORS = {0x5A4632, 0x3B3B3B, 0x4A5A3A, 0x2E3E5A, 0x6E5A40};
    public static final int[] SHOE_COLORS = {0x3A2A1E, 0x2A2A2A, 0x5A3A22};
    public static final int FACES = 3;
    public static final int HAIR_STYLES = 5;
    public static final int TOPS = 3;
    public static final int BOTTOMS = 2;
    /** Grey hair only for this share of residents (elders are not modelled yet). */
    private static final int GREY_HAIR = 6;

    public static ResidentLook of(int seed, boolean female) {
        Random random = new Random(seed * 31L + (female ? 7 : 3));
        int skinTone = random.nextInt(SKIN_TONES.length);
        int face = random.nextInt(FACES);
        int hair = random.nextInt(HAIR_STYLES);
        int hairColor = random.nextInt(HAIR_COLORS.length);
        if (hairColor == GREY_HAIR && random.nextInt(3) != 0) {
            hairColor = random.nextInt(GREY_HAIR); // grey stays rare
        }
        int top = random.nextInt(TOPS);
        int topColor = random.nextInt(CLOTH_COLORS.length);
        int bottom = random.nextInt(BOTTOMS);
        int bottomColor = random.nextInt(PANTS_COLORS.length);
        int shoeColor = random.nextInt(SHOE_COLORS.length);
        return new ResidentLook(female, skinTone, face, hair, hairColor, top, topColor, bottom, bottomColor, shoeColor);
    }

    /** "m" or "f": the gender part of the building block file names. */
    public String gender() {
        return female ? "f" : "m";
    }

    /** A short key, the same for the same look (with the profession appended by the client). */
    public String key() {
        return gender() + skinTone + face + hair + hairColor + "_" + top + "_" + topColor + "_" + bottom + bottomColor + shoeColor;
    }
}
