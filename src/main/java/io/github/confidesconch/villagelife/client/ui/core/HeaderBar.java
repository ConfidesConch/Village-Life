package io.github.confidesconch.villagelife.client.ui.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * The header of the village window (M21b1): village name and stage on the left, live values on the
 * right (residents, work, free beds, mood, emeralds) – read from the snapshot, nothing is computed here.
 * Values that do not fit are left out from the right.
 */
final class HeaderBar {
    private HeaderBar() {}

    private record Value(Component label, Component value, int color) {}

    static void render(Ui ui, UiRect header, CompoundTag data, boolean hasVillage) {
        UiTheme.draw(ui.graphics(), UiTheme.HEADER, header);
        UiRect inner = header.inset(8, 0, 6, 3);
        if (!hasVillage) {
            ui.text(Component.translatable("villagelife.book.no_village.title"), inner, UiTheme.TEXT_DIM, -1);
            return;
        }
        Component name = villageName(data);
        Component stage = Component.translatable("villagelife.stage." + data.getCompoundOrEmpty("growth").getIntOr("stage", 0));
        List<Value> values = values(data);
        int x = inner.right();
        List<UiRect> chips = new ArrayList<>();
        int nameSpace = Math.min(ui.font().width(name), 140) + ui.font().width(stage) + 16;
        for (Value v : values) {
            int width = ui.font().width(v.label()) + ui.font().width(v.value()) + 18;
            if (x - width < inner.x() + nameSpace) {
                break;
            }
            x -= width;
            chips.add(new UiRect(x, inner.y() + 4, width, 16));
            x -= 4;
        }
        for (int i = 0; i < chips.size(); i++) {
            Value v = values.get(i);
            ui.chip(chips.get(i), v.label(), v.value(), v.color());
        }
        int nameWidth = ui.text(name, inner.x(), inner.y() + 8, UiTheme.GOLD, Math.min(140, x - inner.x()));
        ui.text(stage, inner.x() + nameWidth + 8, inner.y() + 8, UiTheme.TEXT_DIM, Math.max(0, x - inner.x() - nameWidth - 12));
    }

    /** Right to left: the most important value first, so it stays when space is short. */
    private static List<Value> values(CompoundTag data) {
        CompoundTag overview = data.getCompoundOrEmpty("overview");
        CompoundTag growth = data.getCompoundOrEmpty("growth");
        List<Value> list = new ArrayList<>();
        list.add(new Value(Component.translatable("villagelife.ui.chip.residents"),
                Component.literal(overview.getIntOr("residents", 0) + "/" + growth.getIntOr("max_residents", 0)), UiTheme.TEXT));
        int places = overview.getIntOr("workplaces", 0);
        int taken = overview.getIntOr("workplaces_taken", 0);
        list.add(new Value(Component.translatable("villagelife.ui.chip.work"), Component.literal(taken + "/" + places),
                overview.getIntOr("unemployed", 0) > 0 ? UiTheme.WARN : UiTheme.TEXT));
        int bedsFree = overview.getIntOr("beds_free", 0);
        list.add(new Value(Component.translatable("villagelife.ui.chip.beds"), Component.literal(String.valueOf(bedsFree)),
                overview.getIntOr("homeless", 0) > 0 ? UiTheme.BAD : UiTheme.TEXT));
        int mood = overview.getIntOr("satisfaction", -1);
        list.add(new Value(Component.translatable("villagelife.ui.chip.mood"), Component.literal(mood < 0 ? "–" : mood + " %"),
                mood < 0 ? UiTheme.TEXT_DIM : mood >= 60 ? UiTheme.GOOD : mood >= 40 ? UiTheme.WARN : UiTheme.BAD));
        int emeralds = data.getCompoundOrEmpty("market").getIntOr("village_emeralds", -1);
        list.add(new Value(Component.translatable("villagelife.ui.chip.emeralds"),
                Component.literal(emeralds < 0 ? "–" : String.valueOf(emeralds)), UiTheme.GOOD));
        return list;
    }

    /** Custom name or "Village of <owner>". */
    static Component villageName(CompoundTag data) {
        String name = data.getStringOr("name", "");
        return name.isEmpty()
                ? Component.translatable("villagelife.village.default_name", data.getStringOr("owner_name", "?"))
                : Component.literal(name);
    }
}
