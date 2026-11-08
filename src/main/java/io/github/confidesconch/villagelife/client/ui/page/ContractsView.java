package io.github.confidesconch.villagelife.client.ui.page;

import io.github.confidesconch.villagelife.client.ui.component.ScrollList;
import io.github.confidesconch.villagelife.client.ui.component.ScrollPanel;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;
import io.github.confidesconch.villagelife.convoy.ConvoyEscortPayload;
import io.github.confidesconch.villagelife.market.contract.ContractRules;
import io.github.confidesconch.villagelife.market.contract.ContractTerms;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Tab "Contracts" (M21b7, the book's contracts chapter since M15d1; owner and managers only): on the left
 * the contracts (red = waiting for our answer), "new offer" and the trade roads (guards away, last raids);
 * on the right the chosen contract with terms, deliveries, road safety, escorts ◀ n ▶ and its buttons –
 * or the offer form ({@link ContractForm}).
 */
final class ContractsView {
    private static final ContractTerms NO_TERMS = new ContractTerms("", 0, 0, 0, "", 0);
    private static final int ROW = 14;
    private static final int LINE = 11;

    private final ScrollList list = new ScrollList();
    private final ScrollList raids = new ScrollList();
    private final ScrollPanel detail = new ScrollPanel();
    private final ContractForm form = new ContractForm();
    private int selected = -1;

    void render(Ui ui, UiRect area, CompoundTag section, CompoundTag convoy, String villageId) {
        if (!section.getBooleanOr("can_manage", false)) {
            UiTheme.draw(ui.graphics(), UiTheme.PANEL, area);
            UiRect inner = area.inset(12);
            ui.paragraph(Component.translatable("villagelife.book.contracts.managers_only"), inner.x(), inner.y(), inner.w(), UiTheme.INK_DIM);
            return;
        }
        ListTag contracts = section.getListOrEmpty("contracts");
        CompoundTag shown = null;
        for (int i = 0; i < contracts.size(); i++) {
            if (contracts.getCompoundOrEmpty(i).getIntOr("contract", -2) == selected) {
                shown = contracts.getCompoundOrEmpty(i);
            }
        }
        if (shown == null && !contracts.isEmpty()) {
            shown = contracts.getCompoundOrEmpty(0);
            selected = shown.getIntOr("contract", -1);
        }
        int leftWidth = Math.max(130, area.w() * 38 / 100);
        UiRect left = area.left(leftWidth);
        renderLeft(ui, left, section, convoy, contracts);
        UiRect right = area.rightOf(leftWidth + 6);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, right);
        UiRect inner = right.inset(10, 8, 6, 8);
        int w = ScrollPanel.contentWidth(inner);
        int top = detail.begin(ui, inner);
        int y = top;
        if (form.isOpen()) {
            y = form.render(ui, inner.x(), y, w, section, villageId);
        } else if (shown != null) {
            y = details(ui, inner.x(), y, w, shown, section, convoy, villageId);
        } else {
            y += ui.paragraph(Component.translatable("villagelife.book.contracts.none"), inner.x(), y, w, UiTheme.INK_DIM);
        }
        detail.end(ui, y - top);
    }

    private void renderLeft(Ui ui, UiRect left, CompoundTag section, CompoundTag convoy, ListTag contracts) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, left);
        UiRect inner = left.inset(5);
        int max = section.getIntOr("max_contracts", ContractRules.CONTRACTS_PER_VILLAGE);
        ui.text(Component.translatable("villagelife.ui.economy.contracts", contracts.size(), max), inner.x(), inner.y() + 1, UiTheme.GOLD, inner.w());
        int y = inner.y() + 14;
        boolean market = section.getBooleanOr("has_market", false);
        if (!market) {
            y += ui.paragraph(Component.translatable("villagelife.book.contracts.need_market"), inner.x(), y, inner.w(), UiTheme.BAD) + 2;
        }
        boolean partners = !section.getListOrEmpty("partners").isEmpty();
        boolean canOffer = market && partners && contracts.size() < max;
        UiRect offer = new UiRect(inner.x(), y, inner.w(), 16);
        ui.button(offer, Component.translatable("villagelife.book.contracts.offer"), canOffer, () -> {
            form.startNew();
            detail.reset();
        });
        if (!canOffer && market) {
            ui.tooltip(offer, Component.translatable(partners ? "villagelife.ui.economy.contracts_full" : "villagelife.book.contracts.no_partners"));
        }
        y += 20;
        int historyHeight = Math.min(90, Math.max(40, (inner.bottom() - y) / 3));
        UiRect rows = new UiRect(inner.x(), y, inner.w(), Math.max(0, inner.bottom() - y - historyHeight - 4));
        list.render(ui, rows, contracts.size(), ROW, (u, index, row) -> {
            CompoundTag c = contracts.getCompoundOrEmpty(index);
            int id = c.getIntOr("contract", -1);
            boolean isSelected = id == selected && !form.isOpen();
            if (isSelected) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x50E7C66B);
            } else if (u.hovered(row)) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x25E7C66B);
            }
            u.graphics().fill(row.x() + 2, row.y() + 5, row.x() + 6, row.y() + 9, color(c));
            u.text(EconomyPage.villageName(c), row.inset(10, 0, 2, 0), isSelected ? UiTheme.GOLD : UiTheme.TEXT, -1);
            u.clickable(row, () -> {
                selected = id;
                form.close();
                detail.reset();
            });
        });
        history(ui, inner.bottomPart(historyHeight), convoy);
    }

    private static int color(CompoundTag c) {
        if (c.getBooleanOr("my_turn", false)) {
            return UiTheme.BAD;
        }
        return "active".equals(c.getStringOr("status", "")) ? UiTheme.GOOD : UiTheme.TEXT_DIM;
    }

    /** Trade roads (M20c): guards away right now and the last raids. */
    private void history(Ui ui, UiRect r, CompoundTag convoy) {
        ui.graphics().fill(r.x(), r.y(), r.right(), r.y() + 1, 0x66E7C66B);
        ui.text(Component.translatable("villagelife.book.convoy.history"), r.x(), r.y() + 4, UiTheme.GOLD, r.w());
        int away = convoy.getIntOr("away", 0);
        int y = r.y() + 16;
        if (away > 0) {
            ui.text(Component.translatable("villagelife.book.convoy.away", away), r.x(), y, UiTheme.TEXT_DIM, r.w());
            y += LINE;
        }
        ListTag history = convoy.getListOrEmpty("history");
        if (history.isEmpty()) {
            ui.text(Component.translatable("villagelife.ui.economy.no_raids"), r.x(), y, UiTheme.TEXT_DIM, r.w());
            return;
        }
        raids.render(ui, new UiRect(r.x(), y, r.w(), Math.max(0, r.bottom() - y)), history.size(), LINE, (u, index, row) -> {
            CompoundTag h = history.getCompoundOrEmpty(index);
            boolean defended = h.getBooleanOr("defended", false);
            Component ago = ago(h.getLongOr("ago", 0));
            Component line = defended ? Component.translatable("villagelife.book.convoy.raid_defended", EconomyPage.villageName(h), ago)
                    : Component.translatable("villagelife.book.convoy.raid_lost", EconomyPage.villageName(h), h.getIntOr("lost", 0),
                            h.getIntOr("total", 0), EconomyPage.itemName(h.getStringOr("item", "")), ago);
            u.text(line, row, defended ? UiTheme.GOOD : UiTheme.BAD, -1);
        });
    }

    /** The chosen contract: partner, state, terms from our side, deliveries, escorts and the buttons. */
    private int details(Ui ui, int x, int y, int w, CompoundTag c, CompoundTag section, CompoundTag convoy, String villageId) {
        ui.text(EconomyPage.villageName(c), x, y, UiTheme.INK, w);
        y += LINE + 1;
        String status = c.getStringOr("status", "offer");
        boolean myTurn = c.getBooleanOr("my_turn", false);
        Component state = switch (status) {
            case "active" -> Component.translatable("villagelife.book.contracts.status.active");
            case "ending" -> Component.translatable("villagelife.book.contracts.status.ending", Math.max(1, (c.getLongOr("ends_in", 0) + 999) / 1000));
            default -> Component.translatable(myTurn ? "villagelife.book.contracts.status.your_turn" : "villagelife.book.contracts.status.waiting");
        };
        int stateColor = myTurn ? UiTheme.BAD_INK : "active".equals(status) ? UiTheme.GOOD_INK : UiTheme.INK_DIM;
        ui.text(state, x, y, stateColor, w);
        y += LINE + 4;
        boolean weGive = c.getBooleanOr("we_give", true);
        y = heading(ui, x, y, w, Component.translatable(weGive ? "villagelife.book.contracts.we_give" : "villagelife.book.contracts.we_get"));
        y += ui.paragraph(Component.translatable("villagelife.book.contracts.terms_good", c.getIntOr("percent", 0),
                EconomyPage.itemName(c.getStringOr("good", "")), c.getIntOr("max", 0), c.getIntOr("days", 1)), x, y, w, UiTheme.INK) + 3;
        y = heading(ui, x, y, w, Component.translatable(weGive ? "villagelife.book.contracts.they_pay" : "villagelife.book.contracts.we_pay"));
        y += ui.paragraph(Component.translatable("villagelife.book.contracts.terms_payment", c.getIntOr("payment_count", 0),
                EconomyPage.itemName(c.getStringOr("payment", ""))), x, y, w, UiTheme.INK) + 3;
        y = ContractsRoad.render(ui, x, y, w, c);
        int id = c.getIntOr("contract", -1);
        if (!"offer".equals(status)) {
            y = escorts(ui, x, y + 2, w, id, convoy, villageId);
        }
        return buttons(ui, x, y + 4, w, c, section, villageId, status, myTurn, id);
    }

    private int buttons(Ui ui, int x, int y, int w, CompoundTag c, CompoundTag section, String villageId, String status,
                        boolean myTurn, int id) {
        String partner = c.getStringOr("id", "");
        boolean weGive = c.getBooleanOr("we_give", true);
        int third = (w - 8) / 3;
        switch (status) {
            case "offer" -> {
                if (myTurn) {
                    ui.button(new UiRect(x, y, third, 16), Component.translatable("villagelife.book.contracts.accept"), true,
                            () -> ContractForm.send(villageId, "accept", id, partner, weGive, NO_TERMS));
                    ui.button(new UiRect(x + third + 4, y, third, 16), Component.translatable("villagelife.book.contracts.counter_button"), true,
                            () -> {
                                form.startCounter(c, section);
                                detail.reset();
                            });
                    ui.button(new UiRect(x + 2 * (third + 4), y, third, 16), Component.translatable("villagelife.book.contracts.decline"), true,
                            () -> ContractForm.send(villageId, "decline", id, partner, weGive, NO_TERMS));
                } else {
                    ui.button(new UiRect(x, y, Math.min(w, 180), 16), Component.translatable("villagelife.book.contracts.withdraw"), true,
                            () -> ContractForm.send(villageId, "decline", id, partner, weGive, NO_TERMS));
                }
                return y + 20;
            }
            case "active" -> {
                ui.button(new UiRect(x, y, Math.min(w, 180), 16), Component.translatable("villagelife.book.contracts.cancel"), true,
                        () -> ContractForm.send(villageId, "cancel", id, partner, weGive, NO_TERMS));
                return y + 20;
            }
            default -> {
                return y;
            }
        }
    }

    /** M20c: how safe the road is, and [◀] n [▶] guards that go with our shipments. */
    private static int escorts(Ui ui, int x, int y, int w, int id, CompoundTag convoy, String villageId) {
        CompoundTag k = convoy.getCompoundOrEmpty("contracts").getCompoundOrEmpty(String.valueOf(id));
        if (k.isEmpty()) {
            return y;
        }
        String level = k.getStringOr("level", "medium");
        int color = switch (level) {
            case "safe" -> UiTheme.GOOD_INK;
            case "dangerous" -> UiTheme.BAD_INK;
            default -> UiTheme.INK;
        };
        y = heading(ui, x, y, w, Component.translatable("villagelife.ui.economy.road"));
        ui.text(Component.translatable("villagelife.book.convoy.safety", Component.translatable("villagelife.book.convoy.level." + level),
                k.getIntOr("safety", 0)), x, y, color, w);
        y += LINE + 2;
        int escorts = k.getIntOr("escorts", 0);
        int max = convoy.getIntOr("max_escorts", 3);
        ui.text(Component.translatable("villagelife.book.convoy.escorts"), x, y + 4, UiTheme.INK, w - 64);
        ui.button(new UiRect(x + w - 56, y, 16, 16), Component.literal("◀"), escorts > 0, () -> sendEscorts(villageId, id, escorts - 1));
        ui.text(Component.literal(String.valueOf(escorts)), new UiRect(x + w - 40, y, 24, 16), UiTheme.INK, 0);
        ui.button(new UiRect(x + w - 16, y, 16, 16), Component.literal("▶"), escorts < max, () -> sendEscorts(villageId, id, escorts + 1));
        return y + 20;
    }

    static int heading(Ui ui, int x, int y, int w, Component title) {
        ui.text(title, x, y, UiTheme.INK, w);
        ui.graphics().fill(x, y + 10, x + w, y + 11, 0x33705A3A);
        return y + LINE + 3;
    }

    private static Component ago(long ticks) {
        return ticks < 24000 ? Component.translatable("villagelife.book.convoy.ago_hours", Math.max(1, ticks / 1000))
                : Component.translatable("villagelife.book.convoy.ago_days", ticks / 24000);
    }

    private static void sendEscorts(String villageId, int contractId, int escorts) {
        if (!villageId.isEmpty()) {
            ClientPacketDistributor.sendToServer(new ConvoyEscortPayload(villageId, contractId, escorts));
        }
    }

    boolean mouseScrolled(double x, double y, double amount) {
        return list.mouseScrolled(x, y, amount) || raids.mouseScrolled(x, y, amount) || detail.mouseScrolled(x, y, amount);
    }

    boolean mouseDragged(double x, double y) {
        return list.mouseDragged(x, y) || raids.mouseDragged(x, y) || detail.mouseDragged(x, y);
    }

    void mouseReleased() {
        list.mouseReleased();
        raids.mouseReleased();
        detail.mouseReleased();
    }
}
