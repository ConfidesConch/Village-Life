package io.github.confidesconch.villagelife.client.ui.page;

import java.util.ArrayList;
import java.util.List;

import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;
import io.github.confidesconch.villagelife.market.contract.ContractActionPayload;
import io.github.confidesconch.villagelife.market.contract.ContractRules;
import io.github.confidesconch.villagelife.market.contract.ContractTerms;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The offer form in "Contracts" (M21b7, the book's form since M15d1): partner, direction, good, share,
 * maximum, rhythm and payment, each with ◀ ▶; also for counter offers (partner fixed). Holds only the
 * input; the server checks everything again.
 */
final class ContractForm {
    private static final String EMERALD = "minecraft:emerald";
    private static final int LINE = 18;

    private boolean open;
    private int counterId = -1;
    private String counterPartner = "";
    private Component counterPartnerName = Component.empty();
    private int partner;
    private boolean weGive = true;
    private int good;
    private int percent = 30;
    private int max = 64;
    private int days = 3;
    private int payment;
    private int paymentCount = 4;

    boolean isOpen() {
        return open;
    }

    void close() {
        open = false;
    }

    void startNew() {
        open = true;
        counterId = -1;
    }

    /** A counter offer to contract entry {@code c}: starts with its terms, seen from our side. */
    void startCounter(CompoundTag c, CompoundTag section) {
        open = true;
        counterId = c.getIntOr("contract", -1);
        counterPartner = c.getStringOr("id", "");
        counterPartnerName = EconomyPage.villageName(c);
        weGive = c.getBooleanOr("we_give", true);
        good = Math.max(0, goods(section).indexOf(c.getStringOr("good", "")));
        percent = c.getIntOr("percent", 30);
        max = c.getIntOr("max", 64);
        days = c.getIntOr("days", 3);
        payment = Math.max(0, payments(section).indexOf(c.getStringOr("payment", EMERALD)));
        paymentCount = c.getIntOr("payment_count", 4);
    }

    /** Draws the form from {@code y}; returns the y below it. */
    int render(Ui ui, int x, int y, int w, CompoundTag section, String villageId) {
        ListTag partners = section.getListOrEmpty("partners");
        List<String> goods = goods(section);
        List<String> payments = payments(section);
        if (goods.isEmpty() || (counterId < 0 && partners.isEmpty())) {
            open = false;
            return y;
        }
        partner = Math.floorMod(partner, Math.max(1, partners.size()));
        good = Math.floorMod(good, goods.size());
        payment = Math.floorMod(payment, payments.size());
        ui.text(Component.translatable(counterId < 0 ? "villagelife.book.contracts.new" : "villagelife.book.contracts.counter"),
                x, y, UiTheme.INK, w);
        y += 14;
        if (counterId < 0) {
            y = selector(ui, x, y, w, "partner", EconomyPage.villageName(partners.getCompoundOrEmpty(partner)), () -> partner--, () -> partner++);
        } else {
            ui.text(Component.translatable("villagelife.book.contracts.field.partner"), x, y + 4, UiTheme.INK_DIM, w / 3);
            ui.text(counterPartnerName, x + w / 3, y + 4, UiTheme.INK, w - w / 3);
            y += LINE;
        }
        y = selector(ui, x, y, w, "direction", Component.translatable(weGive ? "villagelife.book.contracts.we_give"
                : "villagelife.book.contracts.we_get"), () -> weGive = !weGive, () -> weGive = !weGive);
        y = selector(ui, x, y, w, "good", EconomyPage.itemName(goods.get(good)), () -> good--, () -> good++);
        y = selector(ui, x, y, w, "percent", Component.literal(percent + " %"),
                () -> percent = step(percent, -ContractRules.PERCENT_STEP, ContractRules.PERCENT_MIN, ContractRules.PERCENT_MAX),
                () -> percent = step(percent, ContractRules.PERCENT_STEP, ContractRules.PERCENT_MIN, ContractRules.PERCENT_MAX));
        y = selector(ui, x, y, w, "max", Component.literal(String.valueOf(max)),
                () -> max = step(max, -ContractRules.MAX_STEP, ContractRules.MAX_MIN, ContractRules.MAX_MAX),
                () -> max = step(max, ContractRules.MAX_STEP, ContractRules.MAX_MIN, ContractRules.MAX_MAX));
        y = selector(ui, x, y, w, "days", Component.translatable("villagelife.book.settings.days", days),
                () -> days = step(days, -1, ContractRules.DAYS_MIN, ContractRules.DAYS_MAX),
                () -> days = step(days, 1, ContractRules.DAYS_MIN, ContractRules.DAYS_MAX));
        y = selector(ui, x, y, w, "payment", EconomyPage.itemName(payments.get(payment)), () -> payment--, () -> payment++);
        y = selector(ui, x, y, w, "payment_count", Component.literal(String.valueOf(paymentCount)),
                () -> paymentCount = step(paymentCount, -1, ContractRules.PAYMENT_MIN, ContractRules.PAYMENT_MAX),
                () -> paymentCount = step(paymentCount, 1, ContractRules.PAYMENT_MIN, ContractRules.PAYMENT_MAX));
        y += 2 + ui.paragraph(Component.translatable("villagelife.book.contracts.summary", percent, EconomyPage.itemName(goods.get(good)),
                max, paymentCount, EconomyPage.itemName(payments.get(payment))), x, y + 2, w, UiTheme.INK_DIM);
        boolean same = goods.get(good).equals(payments.get(payment));
        if (same) {
            y += ui.paragraph(Component.translatable("villagelife.book.contracts.same_good"), x, y, w, UiTheme.BAD_INK);
        }
        String partnerId = counterId < 0 ? partners.getCompoundOrEmpty(partner).getStringOr("id", "") : counterPartner;
        ContractTerms terms = new ContractTerms(goods.get(good), percent, max, days, payments.get(payment), paymentCount);
        int half = (w - 6) / 2;
        ui.button(new UiRect(x, y + 4, half, 16), Component.translatable("villagelife.book.contracts.send"), !same, () -> {
            open = false;
            send(villageId, counterId < 0 ? "offer" : "counter", counterId, partnerId, weGive, terms);
        });
        ui.button(new UiRect(x + half + 6, y + 4, half, 16), Component.translatable("villagelife.book.contracts.abort"), true,
                () -> open = false);
        return y + 24;
    }

    /** "Label   [◀] value [▶]". */
    private static int selector(Ui ui, int x, int y, int w, String field, Component value, Runnable less, Runnable more) {
        int labelWidth = Math.min(80, w / 3);
        ui.text(Component.translatable("villagelife.book.contracts.field." + field), x, y + 4, UiTheme.INK_DIM, labelWidth - 4);
        ui.button(new UiRect(x + labelWidth, y, 16, 16), Component.literal("◀"), true, less);
        ui.button(new UiRect(x + w - 16, y, 16, 16), Component.literal("▶"), true, more);
        ui.text(value, new UiRect(x + labelWidth + 20, y, w - labelWidth - 40, 16), UiTheme.INK, 0);
        return y + LINE;
    }

    private static int step(int value, int delta, int min, int max) {
        return Math.max(min, Math.min(max, value + delta));
    }

    private static List<String> goods(CompoundTag section) {
        List<String> list = new ArrayList<>();
        ListTag goods = section.getListOrEmpty("goods");
        for (int i = 0; i < goods.size(); i++) {
            list.add(goods.getCompoundOrEmpty(i).getStringOr("item", ""));
        }
        return list;
    }

    /** Emeralds first, then every market good. */
    private static List<String> payments(CompoundTag section) {
        List<String> list = new ArrayList<>();
        list.add(EMERALD);
        list.addAll(goods(section));
        return list;
    }

    static void send(String villageId, String action, int contractId, String partner, boolean weGive, ContractTerms terms) {
        if (!villageId.isEmpty()) {
            ClientPacketDistributor.sendToServer(new ContractActionPayload(villageId, action, contractId, partner, weGive, terms));
        }
    }
}
