package io.github.confidesconch.villagelife.client.ui.page;

import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Tab "Laws" in "Administration" (M21b10a, the book's law pages since M19b): rest day, curfew and long
 * evenings with their values on the left (changes apply from the next morning), the effects on the right.
 */
final class AdminLaws {
    private AdminLaws() {}

    static void left(AdminPage.Column c, CompoundTag laws, boolean manage) {
        c.heading(Component.translatable("villagelife.book.laws"));
        c.paragraph(Component.translatable(manage ? "villagelife.book.laws.info" : "villagelife.book.laws.guest"), UiTheme.INK_DIM);
        law(c, laws, manage, "rest_day", "rest_interval", 3, 14, "villagelife.book.laws.every_days");
        int restIn = laws.getIntOr("rest_in", -1);
        if (restIn == 0) {
            c.paragraph(Component.translatable("villagelife.book.laws.rest_today"), UiTheme.GOOD_INK);
        } else if (restIn > 0) {
            c.paragraph(Component.translatable("villagelife.book.laws.rest_in", restIn), UiTheme.INK_DIM);
        }
        law(c, laws, manage, "curfew", "curfew_hour", 18, 23, "villagelife.book.laws.from_clock");
        law(c, laws, manage, "long_evenings", "long_evening_hours", 1, 3, "villagelife.book.laws.hours_earlier");
    }

    static void right(AdminPage.Column c, CompoundTag laws) {
        c.heading(Component.translatable("villagelife.book.laws.effects"));
        if (laws.getBooleanOr("pending", false)) {
            c.paragraph(Component.translatable("villagelife.book.laws.pending"), UiTheme.GOOD_INK);
        }
        for (String key : new String[]{"rest_day", "curfew", "long_evenings"}) {
            c.heading(Component.translatable("villagelife.book.laws." + key));
            c.paragraph(Component.translatable("villagelife.book.laws." + key + ".good"), UiTheme.GOOD_INK);
            c.paragraph(Component.translatable("villagelife.book.laws." + key + ".bad"), UiTheme.BAD_INK);
        }
    }

    /** "Rest day ...... [On]" and below it "[◀] every 7 days [▶]". */
    private static void law(AdminPage.Column c, CompoundTag laws, boolean manage, String key, String valueKey, int min, int max,
                            String unitKey) {
        boolean on = laws.getBooleanOr(key, false);
        c.toggleLiteral(Component.translatable("villagelife.book.laws." + key), on, manage, "law", key + ":" + !on);
        int value = laws.getIntOr(valueKey, min);
        c.numberWith(Component.literal("   "), Component.translatable(unitKey, value), manage && value > min, manage && value < max,
                () -> AdminPage.send(c.villageId, "law", valueKey + ":" + (value - 1)),
                () -> AdminPage.send(c.villageId, "law", valueKey + ":" + (value + 1)), on ? UiTheme.INK : UiTheme.INK_DIM);
    }
}
