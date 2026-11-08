package io.github.confidesconch.villagelife.client.ui.page;

import java.util.ArrayList;
import java.util.List;

import io.github.confidesconch.villagelife.client.ui.component.TabBar;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiPage;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * "Economy &amp; trade" of the village window (M21b7) – the book's market, treasury and contracts chapters
 * in one area with three tabs: "Market" (trade at the market, caravan), "Treasury" (owner and managers:
 * emeralds of the week, last business, prices) and "Contracts" (contracts with other villages, offer form,
 * escorts, raids). Data: the server's "market", "contracts" and "convoy" sections.
 */
public final class EconomyPage implements UiPage {
    private final MarketView market = new MarketView();
    private final TreasuryView treasury = new TreasuryView();
    private final ContractsView contracts = new ContractsView();
    /** 0 market, 1 treasury, 2 contracts. */
    private int tab;

    @Override
    public String id() {
        return "economy";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.EMERALD);
    }

    @Override
    public Component title() {
        return Component.translatable("villagelife.ui.page.economy");
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public List<String> sections() {
        return List.of("market", "contracts", "convoy");
    }

    /** "economy:contracts", "economy:treasury" or "economy:market". */
    @Override
    public void focus(String hint) {
        tab = switch (hint) {
            case "treasury" -> 1;
            case "contracts" -> 2;
            default -> 0;
        };
    }

    @Override
    public void render(Ui ui, UiRect area, CompoundTag data) {
        CompoundTag marketData = data.getCompoundOrEmpty("market");
        boolean managers = marketData.contains("treasury");
        List<Component> tabs = new ArrayList<>();
        tabs.add(Component.translatable("villagelife.ui.economy.tab.market"));
        tabs.add(Component.translatable("villagelife.ui.economy.tab.treasury"));
        tabs.add(Component.translatable("villagelife.ui.economy.tab.contracts"));
        UiRect body = TabBar.render(ui, area, tabs, tab, i -> tab = i);
        String villageId = data.getStringOr("village_id", "");
        switch (tab) {
            case 1 -> treasury.render(ui, body, marketData.getCompoundOrEmpty("treasury"), managers);
            case 2 -> contracts.render(ui, body, data.getCompoundOrEmpty("contracts"), data.getCompoundOrEmpty("convoy"), villageId);
            default -> market.render(ui, body, marketData, villageId);
        }
    }

    // ---- shared helpers ------------------------------------------------------------------------

    static ItemStack stack(String id, int count) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(parsed), Math.max(1, count));
    }

    static Component itemName(String id) {
        ItemStack stack = stack(id, 1);
        return stack.isEmpty() ? Component.literal(id) : stack.getHoverName();
    }

    /** Custom name or "Village of <owner>" of a village entry ({@code name}, {@code owner_name}). */
    static Component villageName(CompoundTag v) {
        String name = v.getStringOr("name", "");
        return name.isEmpty() ? Component.translatable("villagelife.village.default_name", v.getStringOr("owner_name", "?"))
                : Component.literal(name);
    }

    // ---- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        return switch (tab) {
            case 1 -> treasury.mouseScrolled(x, y, amount);
            case 2 -> contracts.mouseScrolled(x, y, amount);
            default -> market.mouseScrolled(x, y, amount);
        };
    }

    @Override
    public boolean mouseDragged(double x, double y) {
        return switch (tab) {
            case 1 -> treasury.mouseDragged(x, y);
            case 2 -> contracts.mouseDragged(x, y);
            default -> market.mouseDragged(x, y);
        };
    }

    @Override
    public void mouseReleased() {
        market.mouseReleased();
        treasury.mouseReleased();
        contracts.mouseReleased();
    }
}
