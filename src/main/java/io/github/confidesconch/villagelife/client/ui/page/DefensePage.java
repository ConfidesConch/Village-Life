package io.github.confidesconch.villagelife.client.ui.page;

import java.util.ArrayList;
import java.util.List;

import io.github.confidesconch.villagelife.book.UpdateVillageSettingsPayload;
import io.github.confidesconch.villagelife.client.ui.component.ScrollList;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiPage;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * "Defense" of the village window (M21b9): what the book had spread over several chapters, in one
 * place. Top: attack or alarm (alarm button), bandit attacks on/off (owner and managers), the defenders'
 * strength and what an attack would bring. Left: every guard, archer and captain with its state (a click
 * opens the resident). Right: the last fights and the raids on trade convoys. Data: "defense", "convoy".
 */
public final class DefensePage implements UiPage {
    private static final int ROW = 18;
    private static final int LINE = 11;

    private final ScrollList defenders = new ScrollList();
    private final ScrollList fights = new ScrollList();
    private ListTag decodedFrom;
    private List<Component> decoded = List.of();

    @Override
    public String id() {
        return "defense";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.IRON_SWORD);
    }

    @Override
    public Component title() {
        return Component.translatable("villagelife.ui.page.defense");
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public List<String> sections() {
        return List.of("defense", "convoy");
    }

    @Override
    public void render(Ui ui, UiRect area, CompoundTag data) {
        CompoundTag d = data.getCompoundOrEmpty("defense");
        String villageId = data.getStringOr("village_id", "");
        UiRect top = area.top(64);
        situation(ui, top, d, villageId);
        UiRect body = area.below(68);
        int leftWidth = Math.max(140, body.w() * 45 / 100);
        defenderList(ui, body.left(leftWidth), d.getListOrEmpty("defenders"));
        history(ui, body.rightOf(leftWidth + 6), d.getListOrEmpty("fights"), data.getCompoundOrEmpty("convoy"));
    }

    // ---- situation -----------------------------------------------------------------------------

    private static void situation(Ui ui, UiRect r, CompoundTag d, String villageId) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, r);
        UiRect inner = r.inset(10, 6, 10, 6);
        int half = inner.w() / 2;
        boolean attack = d.getBooleanOr("under_attack", false);
        boolean alarm = d.getBooleanOr("manual_alarm", false);
        boolean manage = d.getBooleanOr("can_manage", false);
        Component state = Component.translatable(attack ? "villagelife.ui.defense.attack" : alarm ? "villagelife.ui.defense.alarm"
                : "villagelife.ui.defense.calm");
        ui.text(state, inner.x(), inner.y(), attack ? UiTheme.BAD_INK : alarm ? 0xFF9A5B12 : UiTheme.GOOD_INK, half - 6);
        int buttonWidth = (half - 10) / 2;
        UiRect alarmButton = new UiRect(inner.x(), inner.y() + 14, buttonWidth, 16);
        ui.button(alarmButton, Component.translatable(attack ? "villagelife.ui.alarm.attack" : alarm ? "villagelife.ui.alarm.stop"
                : "villagelife.ui.alarm.start"), manage && !attack && !villageId.isEmpty(), () -> send(villageId, "alarm", "toggle"));
        ui.tooltip(alarmButton, Component.translatable(!manage ? "villagelife.ui.alarm.no_right"
                : attack ? "villagelife.ui.alarm.attack_tip" : "villagelife.ui.alarm.tip"));
        boolean attacks = d.getBooleanOr("attacks", true);
        UiRect toggle = new UiRect(inner.x() + buttonWidth + 4, inner.y() + 14, buttonWidth, 16);
        ui.button(toggle, Component.translatable(attacks ? "villagelife.ui.defense.attacks_on" : "villagelife.ui.defense.attacks_off"),
                manage && !villageId.isEmpty(), () -> send(villageId, "attacks", String.valueOf(!attacks)));
        ui.tooltip(toggle, Component.translatable(manage ? "villagelife.ui.defense.attacks_tip" : "villagelife.ui.defense.managers_only"));
        if (!d.getBooleanOr("attacks_possible", false)) {
            ui.text(Component.translatable("villagelife.ui.defense.from_stage"), inner.x(), inner.y() + 36, UiTheme.INK_DIM, half - 6);
        }
        int x = inner.x() + half + 6;
        int w = inner.w() - half - 6;
        ui.text(Component.translatable("villagelife.ui.defense.power", String.format("%.1f", d.getDoubleOr("power", 0))),
                x, inner.y(), UiTheme.INK, w);
        int level = d.getIntOr("bandit_level", 0);
        int y = inner.y() + 14;
        y += ui.paragraph(Component.translatable("villagelife.ui.defense.expected", d.getIntOr("bandits", 2),
                Component.translatable("villagelife.ui.defense.level." + level), d.getIntOr("crossbows", 0)), x, y, w, UiTheme.INK_DIM);
        ui.tooltip(new UiRect(x, inner.y(), w, y - inner.y()), Component.translatable("villagelife.ui.defense.power_tip"));
    }

    private static void send(String villageId, String key, String value) {
        if (!villageId.isEmpty()) {
            ClientPacketDistributor.sendToServer(new UpdateVillageSettingsPayload(villageId, key, value));
        }
    }

    // ---- defenders -----------------------------------------------------------------------------

    private void defenderList(Ui ui, UiRect r, ListTag list) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, r);
        UiRect inner = r.inset(5);
        ui.text(Component.translatable("villagelife.ui.defense.defenders", list.size()), inner.x(), inner.y() + 1, UiTheme.GOLD, inner.w());
        UiRect rows = inner.below(14);
        if (list.isEmpty()) {
            ui.paragraph(Component.translatable("villagelife.ui.defense.no_defenders"), rows.x(), rows.y() + 2, rows.w(), UiTheme.TEXT_DIM);
            return;
        }
        defenders.render(ui, rows, list.size(), ROW, (u, index, row) -> {
            CompoundTag d = list.getCompoundOrEmpty(index);
            if (u.hovered(row)) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x25E7C66B);
            }
            String kind = d.getStringOr("kind", "guard");
            u.item(new ItemStack(kind.equals("archer") ? Items.BOW : kind.equals("captain") ? Items.GOLDEN_HELMET : Items.IRON_SWORD),
                    row.x() + 1, row.y() + 1);
            String state = d.getStringOr("state", "unloaded");
            Component stateText = state.equals("knocked_out")
                    ? Component.translatable("villagelife.ui.defense.state.knocked_out", Math.max(1, (d.getLongOr("back_in", 0) + 999) / 1000))
                    : Component.translatable("villagelife.ui.defense.state." + state);
            int color = switch (state) {
                case "knocked_out" -> UiTheme.BAD;
                case "away" -> UiTheme.WARN;
                case "day", "night" -> UiTheme.GOOD;
                default -> UiTheme.TEXT_DIM;
            };
            int stateWidth = Math.min(u.font().width(stateText), row.w() / 2);
            u.text(Component.literal(d.getStringOr("name", "?")), row.inset(20, 0, stateWidth + 6, 0), UiTheme.TEXT, -1);
            u.text(stateText, row.inset(0, 0, 2, 0), color, 1);
            Component job = Component.translatable(d.getStringOr("job", ""));
            Component activity = d.contains("activity") ? Component.translatable(d.getStringOr("activity", "")) : Component.empty();
            u.tooltip(row, job, activity, Component.translatable("villagelife.ui.overview.goto", Component.translatable("villagelife.ui.page.citizens")));
            String id = d.getStringOr("id", "");
            u.clickable(row, () -> u.navigate("citizens:id=" + id));
        });
    }

    // ---- fights and convoys --------------------------------------------------------------------

    private void history(Ui ui, UiRect r, ListTag list, CompoundTag convoy) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, r);
        UiRect inner = r.inset(5);
        int convoyHeight = 58;
        UiRect upper = inner.inset(0, 0, 0, convoyHeight + 4);
        ui.text(Component.translatable("villagelife.ui.defense.fights"), upper.x(), upper.y() + 1, UiTheme.GOLD, upper.w());
        UiRect rows = upper.below(14);
        if (list.isEmpty()) {
            ui.paragraph(Component.translatable("villagelife.ui.defense.no_fights"), rows.x(), rows.y() + 2, rows.w(), UiTheme.TEXT_DIM);
        } else {
            List<Component> texts = decode(list);
            fights.render(ui, rows, list.size(), LINE + 1, (u, index, row) -> {
                Component day = Component.translatable("villagelife.ui.overview.day_short", list.getCompoundOrEmpty(index).getLongOr("day", 0));
                int dayWidth = u.font().width(day);
                u.text(day, row.left(dayWidth + 2), UiTheme.TEXT_DIM, -1);
                u.text(texts.get(index), row.inset(dayWidth + 6, 0, 2, 0), UiTheme.TEXT, -1);
            });
        }
        UiRect lower = inner.bottomPart(convoyHeight);
        ui.graphics().fill(lower.x(), lower.y() - 2, lower.right(), lower.y() - 1, 0x66E7C66B);
        ui.text(Component.translatable("villagelife.book.convoy.history"), lower.x(), lower.y() + 2, UiTheme.GOLD, lower.w());
        int away = convoy.getIntOr("away", 0);
        ListTag raids = convoy.getListOrEmpty("history");
        int lost = 0;
        int defended = 0;
        for (int i = 0; i < raids.size(); i++) {
            if (raids.getCompoundOrEmpty(i).getBooleanOr("defended", false)) {
                defended++;
            } else {
                lost++;
            }
        }
        ui.text(Component.translatable("villagelife.ui.defense.convoys", away, defended, lost), lower.x(), lower.y() + 15,
                lost > 0 ? UiTheme.WARN : UiTheme.TEXT_DIM, lower.w());
        ui.button(new UiRect(lower.x(), lower.bottom() - 18, Math.min(lower.w(), 160), 16),
                Component.translatable("villagelife.ui.defense.to_contracts"), true, () -> ui.navigate("economy:contracts"));
    }

    /** Fight texts arrive as components; decoded once per list. */
    private List<Component> decode(ListTag list) {
        if (list == decodedFrom) {
            return decoded;
        }
        var level = Minecraft.getInstance().level;
        List<Component> texts = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Tag text = list.getCompoundOrEmpty(i).get("text");
            texts.add(text == null || level == null ? Component.literal("?")
                    : ComponentSerialization.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), text)
                            .result().orElse(Component.literal("?")));
        }
        decodedFrom = list;
        decoded = texts;
        return texts;
    }

    // ---- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        return defenders.mouseScrolled(x, y, amount) || fights.mouseScrolled(x, y, amount);
    }

    @Override
    public boolean mouseDragged(double x, double y) {
        return defenders.mouseDragged(x, y) || fights.mouseDragged(x, y);
    }

    @Override
    public void mouseReleased() {
        defenders.mouseReleased();
        fights.mouseReleased();
    }
}
