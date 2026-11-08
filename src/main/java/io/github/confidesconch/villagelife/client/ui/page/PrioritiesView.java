package io.github.confidesconch.villagelife.client.ui.page;

import io.github.confidesconch.villagelife.book.UpdateVillageSettingsPayload;
import io.github.confidesconch.villagelife.client.ui.component.ScrollList;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Tab "Priorities" of "Work &amp; professions" (M21b6, the book's management page since M19a): every
 * profession in the order jobless residents are given work; owner and managers move them with ▲ ▼.
 * Basic professions carry a star.
 */
final class PrioritiesView {
    private static final int ROW = 18;

    private final ScrollList list = new ScrollList();

    void render(Ui ui, UiRect area, CompoundTag management, String villageId) {
        boolean manage = management.getBooleanOr("can_manage", false);
        int infoWidth = Math.min(180, area.w() * 36 / 100);
        UiRect info = area.left(infoWidth);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, info);
        UiRect text = info.inset(10, 8, 10, 8);
        int y = text.y();
        ui.text(Component.translatable("villagelife.book.management.priorities"), text.x(), y, UiTheme.INK, text.w());
        y += 14;
        y += ui.paragraph(Component.translatable(manage ? "villagelife.book.management.priorities_info"
                : "villagelife.book.management.priorities_guest"), text.x(), y, text.w(), UiTheme.INK_DIM) + 6;
        ui.paragraph(Component.translatable("villagelife.ui.jobs.essential_hint"), text.x(), y, text.w(), UiTheme.INK_DIM);

        UiRect panel = area.rightOf(infoWidth + 6);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, panel);
        ListTag priorities = management.getListOrEmpty("priorities");
        list.render(ui, panel.inset(4), priorities.size(), ROW, (u, index, row) -> {
            CompoundTag p = priorities.getCompoundOrEmpty(index);
            String id = p.getStringOr("id", "");
            if (u.hovered(row)) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x25E7C66B);
            }
            Component rank = Component.literal((index + 1) + ".");
            u.text(rank, row.left(20), UiTheme.TEXT_DIM, 1);
            Component name = JobsPage.name(id);
            if (p.getBooleanOr("essential", false)) {
                name = Component.literal("★ ").append(name);
            }
            int places = p.getIntOr("places", 0);
            Component count = Component.literal(p.getIntOr("workers", 0) + "/" + places);
            int countWidth = u.font().width(count);
            int buttons = manage ? 40 : 0;
            u.text(name, row.inset(26, 0, buttons + countWidth + 10, 0), places == 0 ? UiTheme.TEXT_DIM : UiTheme.TEXT, -1);
            u.text(count, row.inset(0, 0, buttons + 4, 0), UiTheme.TEXT_DIM, 1);
            if (manage) {
                UiRect up = new UiRect(row.right() - 38, row.y() + 1, 16, 16);
                UiRect down = new UiRect(row.right() - 18, row.y() + 1, 16, 16);
                u.button(up, Component.literal("▲"), index > 0, () -> send(villageId, id + "|up"));
                u.button(down, Component.literal("▼"), index + 1 < priorities.size(), () -> send(villageId, id + "|down"));
            }
        });
    }

    private static void send(String villageId, String value) {
        if (!villageId.isEmpty()) {
            ClientPacketDistributor.sendToServer(new UpdateVillageSettingsPayload(villageId, "priority", value));
        }
    }

    boolean mouseScrolled(double x, double y, double amount) {
        return list.mouseScrolled(x, y, amount);
    }

    boolean mouseDragged(double x, double y) {
        return list.mouseDragged(x, y);
    }

    void mouseReleased() {
        list.mouseReleased();
    }
}
