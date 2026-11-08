package io.github.confidesconch.villagelife.client.ui.page;

import java.util.List;

import io.github.confidesconch.villagelife.book.UpdateVillageSettingsPayload;
import io.github.confidesconch.villagelife.client.book.RenameVillageScreen;
import io.github.confidesconch.villagelife.client.ui.component.ScrollPanel;
import io.github.confidesconch.villagelife.client.ui.component.TabBar;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiPage;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * "Administration" of the village window (M21b10a) – the book's settings and management chapters:
 * tabs "Settings" (name, switches, work hours, storage, animals), "Laws" (M19b), "Managers" (M11g2) and
 * "Messages" (the reader's own notification topics, M19a). Owner and managers change; others only read.
 * The job priorities live in "Work &amp; professions".
 */
public final class AdminPage implements UiPage {
    private static final int LINE = 18;
    private static final String[] TOPICS = {"family", "growth", "trade", "caravan", "buildings", "jobs", "defense"};

    private final ScrollPanel left = new ScrollPanel();
    private final ScrollPanel right = new ScrollPanel();
    private int tab;

    @Override
    public String id() {
        return "admin";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.COMPARATOR);
    }

    @Override
    public Component title() {
        return Component.translatable("villagelife.ui.page.admin");
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    public List<String> sections() {
        return List.of("settings", "management");
    }

    /** "admin:laws", "admin:managers", "admin:messages". */
    @Override
    public void focus(String hint) {
        tab = switch (hint) {
            case "laws" -> 1;
            case "managers" -> 2;
            case "messages" -> 3;
            default -> 0;
        };
    }

    @Override
    public void render(Ui ui, UiRect area, CompoundTag data) {
        UiRect body = TabBar.render(ui, area, List.of(Component.translatable("villagelife.ui.admin.tab.settings"),
                Component.translatable("villagelife.book.laws"), Component.translatable("villagelife.book.settings.managers"),
                Component.translatable("villagelife.ui.admin.tab.messages")), tab, i -> {
                    tab = i;
                    left.reset();
                    right.reset();
                });
        CompoundTag s = data.getCompoundOrEmpty("settings");
        CompoundTag m = data.getCompoundOrEmpty("management");
        String villageId = data.getStringOr("village_id", "");
        int half = (body.w() - 6) / 2;
        UiRect l = body.left(half);
        UiRect r = body.rightOf(half + 6);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, l);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, r);
        boolean manage = m.getBooleanOr("can_manage", false);
        CompoundTag laws = m.getCompoundOrEmpty("laws");
        // one column after the other: each one cuts off its own content (scissor), they must not overlap in time
        column(ui, left, l, villageId, a -> {
            switch (tab) {
                case 1 -> AdminLaws.left(a, laws, manage);
                case 2 -> managersLeft(a, s);
                case 3 -> messagesLeft(a, m);
                default -> settingsLeft(a, s, data);
            }
        });
        column(ui, right, r, villageId, b -> {
            switch (tab) {
                case 1 -> AdminLaws.right(b, laws);
                case 2 -> managersRight(b, s);
                case 3 -> messagesRight(b);
                default -> settingsRight(b, s);
            }
        });
    }

    private static void column(Ui ui, ScrollPanel scroll, UiRect panel, String villageId, java.util.function.Consumer<Column> content) {
        Column column = new Column(ui, scroll, panel.inset(10, 8, 6, 8), villageId);
        content.accept(column);
        column.end();
    }

    private static void settingsLeft(Column a, CompoundTag s, CompoundTag data) {
        boolean isOwner = s.getBooleanOr("is_owner", false);
        boolean manage = s.getBooleanOr("can_manage", false);
        a.paragraph(Component.translatable(isOwner ? "villagelife.book.settings.intro_owner"
                : manage ? "villagelife.book.settings.intro_manager" : "villagelife.book.settings.intro_guest"), UiTheme.INK_DIM);
        a.heading(Component.translatable("villagelife.book.settings.name"));
        String name = s.getStringOr("name", "");
        Component shown = name.isEmpty() ? Component.translatable("villagelife.village.default_name", data.getStringOr("owner_name", "?"))
                : Component.literal(name);
        a.button(shown, Component.translatable("villagelife.book.settings.change"), isOwner, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gui.setScreen(new RenameVillageScreen(minecraft.gui.screen(), a.villageId, name));
        });
        a.heading(Component.translatable("villagelife.book.settings.village"));
        for (String key : List.of("immigration", "auto_jobs", "tools_from_storage", "strangers_may_read", "attacks")) {
            a.toggle("villagelife.book.settings." + key, s.getBooleanOr(key, true), manage, key);
        }
        a.heading(Component.translatable("villagelife.book.settings.mining"));
        a.number("villagelife.book.settings.mine_depth", s, "mine_depth", 4, 64, 4, "villagelife.book.settings.blocks", manage);
        a.heading(Component.translatable("villagelife.book.settings.trade"));
        a.number("villagelife.book.settings.caravan_days", s, "caravan_days", 1, 14, 1, "villagelife.book.settings.days", manage);
    }

    private static void settingsRight(Column b, CompoundTag s) {
        boolean manage = s.getBooleanOr("can_manage", false);
        b.heading(Component.translatable("villagelife.book.settings.hours"));
        b.paragraph(Component.translatable("villagelife.book.settings.hours_info"), UiTheme.INK_DIM);
        b.number("villagelife.book.settings.work_start", s, "work_start", 4, 10, 1, "villagelife.book.settings.clock", manage);
        b.number("villagelife.book.settings.work_end", s, "work_end", 14, 20, 1, "villagelife.book.settings.clock", manage);
        b.heading(Component.translatable("villagelife.book.settings.storage"));
        b.paragraph(Component.translatable("villagelife.book.settings.food_reserve_info"), UiTheme.INK_DIM);
        b.number("villagelife.book.settings.food_reserve", s, "food_reserve", 0, 64, 8, "villagelife.book.settings.pieces", manage);
        b.heading(Component.translatable("villagelife.book.settings.animals"));
        b.number("villagelife.book.settings.herd_limit", s, "herd_limit", 2, 32, 2, "villagelife.book.settings.pieces", manage);
    }

    /** Left: who manages the village. Right (owner only): his friends with on/off each. */
    private static void managersLeft(Column a, CompoundTag s) {
        a.heading(Component.translatable("villagelife.book.settings.managers"));
        a.paragraph(Component.translatable("villagelife.book.settings.managers_info"), UiTheme.INK_DIM);
        ListTag managers = s.getListOrEmpty("managers");
        if (managers.isEmpty()) {
            a.paragraph(Component.translatable("villagelife.book.settings.no_managers"), UiTheme.INK_DIM);
        }
        for (int i = 0; i < managers.size(); i++) {
            a.paragraph(Component.literal("• " + managers.getCompoundOrEmpty(i).getStringOr("name", "?")), UiTheme.INK);
        }
    }

    private static void managersRight(Column b, CompoundTag s) {
        b.heading(Component.translatable("villagelife.book.settings.choose_managers"));
        if (!s.getBooleanOr("is_owner", false)) {
            b.paragraph(Component.translatable("villagelife.ui.admin.owner_only"), UiTheme.INK_DIM);
            return;
        }
        ListTag friends = s.getListOrEmpty("friends");
        if (friends.isEmpty()) {
            b.paragraph(Component.translatable("villagelife.book.settings.no_friends"), UiTheme.INK_DIM);
        }
        for (int i = 0; i < friends.size(); i++) {
            CompoundTag f = friends.getCompoundOrEmpty(i);
            boolean on = f.getBooleanOr("manager", false);
            String id = f.getStringOr("id", "");
            b.toggleLiteral(Component.literal(f.getStringOr("name", "?")), on, true, "manager", id + ":" + !on);
        }
    }

    /** The reader's own message topics (everybody can change his own). */
    private static void messagesLeft(Column a, CompoundTag m) {
        a.heading(Component.translatable("villagelife.book.management.notifications"));
        boolean manage = m.getBooleanOr("can_manage", false);
        a.paragraph(Component.translatable(manage ? "villagelife.book.management.notifications_info"
                : "villagelife.book.management.notifications_guest"), UiTheme.INK_DIM);
        CompoundTag notify = m.getCompoundOrEmpty("notify");
        for (String topic : TOPICS) {
            boolean on = notify.getBooleanOr(topic, true);
            a.toggleLiteral(Component.translatable("villagelife.notify." + topic), on, manage, "notify", topic + ":" + !on);
        }
    }

    private static void messagesRight(Column b) {
        b.heading(Component.translatable("villagelife.book.management.topics"));
        for (String topic : TOPICS) {
            b.paragraph(Component.translatable("villagelife.notify." + topic).copy().append(": ")
                    .append(Component.translatable("villagelife.notify." + topic + ".info")), UiTheme.INK_DIM);
        }
    }

    /** One scrolling column of rows, laid out top to bottom. */
    static final class Column {
        final Ui ui;
        final ScrollPanel scroll;
        final UiRect area;
        final String villageId;
        final int x;
        final int w;
        final int top;
        int y;

        Column(Ui ui, ScrollPanel scroll, UiRect area, String villageId) {
            this.ui = ui;
            this.scroll = scroll;
            this.area = area;
            this.villageId = villageId;
            this.x = area.x();
            this.w = ScrollPanel.contentWidth(area);
            this.top = scroll.begin(ui, area);
            this.y = top;
        }

        void end() {
            scroll.end(ui, y - top);
        }

        void heading(Component title) {
            if (y > top) {
                y += 4;
            }
            ui.text(title, x, y, UiTheme.INK, w);
            ui.graphics().fill(x, y + 10, x + w, y + 11, 0x33705A3A);
            y += 15;
        }

        void paragraph(Component text, int color) {
            y += ui.paragraph(text, x, y, w, color) + 3;
        }

        /** "Label ....... [On]" – the button switches (owner and managers). */
        void toggle(String labelKey, boolean on, boolean enabled, String key) {
            toggleLiteral(Component.translatable(labelKey), on, enabled, key, String.valueOf(!on));
        }

        void toggleLiteral(Component label, boolean on, boolean enabled, String key, String value) {
            int buttonWidth = 44;
            ui.text(label, x, y + 4, UiTheme.INK, w - buttonWidth - 6);
            UiRect r = new UiRect(x + w - buttonWidth, y, buttonWidth, 16);
            ui.button(r, Component.translatable(on ? "villagelife.book.settings.on" : "villagelife.book.settings.off"), enabled,
                    () -> send(villageId, key, value));
            ui.graphics().fill(r.x() + 2, r.bottom() - 2, r.right() - 2, r.bottom() - 1, on ? UiTheme.GOOD : UiTheme.BAD);
            y += LINE;
        }

        /** "Label ....... [◀] value [▶]". */
        void number(String labelKey, CompoundTag s, String key, int min, int max, int step, String unitKey, boolean enabled) {
            int value = s.getIntOr(key, min);
            numberWith(Component.translatable(labelKey), Component.translatable(unitKey, value), enabled && value > min,
                    enabled && value < max, () -> send(villageId, key, String.valueOf(Math.max(min, value - step))),
                    () -> send(villageId, key, String.valueOf(Math.min(max, value + step))), UiTheme.INK);
        }

        void numberWith(Component label, Component shown, boolean canLess, boolean canMore, Runnable less, Runnable more, int color) {
            int control = 76;
            ui.text(label, x, y + 4, UiTheme.INK, w - control - 6);
            int cx = x + w - control;
            ui.button(new UiRect(cx, y, 16, 16), Component.literal("◀"), canLess, less);
            ui.text(shown, new UiRect(cx + 18, y, control - 36, 16), color, 0);
            ui.button(new UiRect(cx + control - 16, y, 16, 16), Component.literal("▶"), canMore, more);
            y += LINE;
        }

        /** "Label ....... [button]". */
        void button(Component label, Component button, boolean enabled, Runnable action) {
            int buttonWidth = Math.min(80, w / 3);
            ui.text(label, x, y + 4, UiTheme.INK, w - buttonWidth - 6);
            ui.button(new UiRect(x + w - buttonWidth, y, buttonWidth, 16), button, enabled, action);
            y += LINE;
        }
    }

    static void send(String villageId, String key, String value) {
        if (!villageId.isEmpty()) {
            ClientPacketDistributor.sendToServer(new UpdateVillageSettingsPayload(villageId, key, value));
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        return left.mouseScrolled(x, y, amount) || right.mouseScrolled(x, y, amount);
    }

    @Override
    public boolean mouseDragged(double x, double y) {
        return left.mouseDragged(x, y) || right.mouseDragged(x, y);
    }

    @Override
    public void mouseReleased() {
        left.mouseReleased();
        right.mouseReleased();
    }
}
