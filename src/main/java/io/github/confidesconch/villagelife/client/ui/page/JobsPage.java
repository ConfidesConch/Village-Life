package io.github.confidesconch.villagelife.client.ui.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.client.ui.component.ScrollList;
import io.github.confidesconch.villagelife.client.ui.component.TabBar;
import io.github.confidesconch.villagelife.client.ui.component.TextField;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiPage;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;
import io.github.confidesconch.villagelife.resident.Job;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * "Work &amp; professions" of the village window (M21b6). Tab "Professions": every profession with
 * taken and free places, the chosen one with its guide, needs, products and workplaces
 * ({@link ProfessionDetail}). Tab "Priorities": the order in which jobless residents are given work
 * ({@link PrioritiesView}). Data: the server's "professions" and "management" sections.
 */
public final class JobsPage implements UiPage {
    private static final int ROW = 18;
    private static final int TOOLBAR = 18;

    /** Filters of the profession list. */
    enum Filter {
        ALL(p -> true),
        BUILT(p -> !p.getListOrEmpty("workplaces").isEmpty()),
        FREE(p -> free(p) > 0),
        PROBLEMS(p -> issues(p) > 0),
        NOT_BUILT(p -> p.getListOrEmpty("workplaces").isEmpty());

        final Predicate<CompoundTag> test;

        Filter(Predicate<CompoundTag> test) {
            this.test = test;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final TextField search = new TextField(32);
    private final ScrollList list = new ScrollList();
    private final ProfessionDetail detail = new ProfessionDetail();
    private final PrioritiesView priorities = new PrioritiesView();
    private Filter filter = Filter.ALL;
    private int tab;
    private String selected;

    @Override
    public String id() {
        return "jobs";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.IRON_PICKAXE);
    }

    @Override
    public Component title() {
        return Component.translatable("villagelife.ui.page.jobs");
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public List<String> sections() {
        return List.of("professions", "management");
    }

    /** "jobs:priorities" opens the priorities, "jobs:free" the professions with free places. */
    @Override
    public void focus(String hint) {
        if (hint.equals("priorities")) {
            tab = 1;
            return;
        }
        tab = 0;
        for (Filter f : Filter.values()) {
            if (f.id().equals(hint)) {
                filter = f;
                search.setValue("");
                list.reset();
            }
        }
    }

    @Override
    public void render(Ui ui, UiRect area, CompoundTag data) {
        UiRect body = TabBar.render(ui, area, List.of(Component.translatable("villagelife.ui.jobs.tab.professions"),
                Component.translatable("villagelife.ui.jobs.tab.priorities")), tab, i -> {
                    tab = i;
                    search.blur();
                });
        CompoundTag management = data.getCompoundOrEmpty("management");
        if (tab == 1) {
            priorities.render(ui, body, management, data.getStringOr("village_id", ""));
            return;
        }
        CompoundTag professions = data.getCompoundOrEmpty("professions");
        renderToolbar(ui, body.top(TOOLBAR), professions.getIntOr("unemployed", 0));
        UiRect content = body.below(TOOLBAR + 4);
        List<CompoundTag> shown = filtered(professions.getListOrEmpty("list"), ranking(management));
        if (shown.stream().noneMatch(p -> p.getStringOr("id", "").equals(selected))) {
            selected = shown.isEmpty() ? null : shown.getFirst().getStringOr("id", null);
        }
        int listWidth = Math.max(130, content.w() * 42 / 100);
        renderList(ui, content.left(listWidth), shown, essentials(management));
        CompoundTag profession = shown.stream().filter(p -> p.getStringOr("id", "").equals(selected)).findFirst().orElse(null);
        detail.render(ui, content.rightOf(listWidth + 6), profession);
    }

    private void renderToolbar(Ui ui, UiRect bar, int unemployed) {
        int searchWidth = Math.min(140, bar.w() * 34 / 100);
        String before = search.value();
        search.render(ui, bar.left(searchWidth), Component.translatable("villagelife.ui.search"));
        UiRect rest = bar.rightOf(searchWidth + 6);
        int filterWidth = Math.min(150, rest.w() / 2);
        UiRect filterButton = rest.left(filterWidth);
        int filters = Filter.values().length;
        ui.button(filterButton, Component.translatable("villagelife.ui.citizens.filter",
                Component.translatable("villagelife.ui.jobs.filter." + filter.id())), true,
                () -> setFilter(Filter.values()[(filter.ordinal() + 1) % filters]));
        ui.clickable(filterButton, null, () -> setFilter(Filter.values()[(filter.ordinal() + filters - 1) % filters]));
        ui.tooltip(filterButton, Component.translatable("villagelife.ui.cycle_hint"));
        if (filter != Filter.ALL) {
            ui.graphics().fill(filterButton.x() + 2, filterButton.bottom() - 2, filterButton.right() - 2, filterButton.bottom() - 1, UiTheme.GOLD);
        }
        if (unemployed > 0) { // "5 without work" – a click shows them in "Residents"
            UiRect link = rest.rightOf(filterWidth + 8);
            boolean hover = ui.hovered(link);
            ui.text(Component.translatable("villagelife.book.professions.unemployed", unemployed), link,
                    hover ? UiTheme.GOLD : UiTheme.WARN, -1);
            ui.tooltip(link, Component.translatable("villagelife.ui.overview.goto", Component.translatable("villagelife.ui.page.citizens")));
            ui.clickable(link, () -> ui.navigate("citizens:unemployed"));
        }
        if (!before.equals(search.value())) {
            list.reset();
        }
    }

    private void setFilter(Filter next) {
        filter = next;
        list.reset();
    }

    private void renderList(Ui ui, UiRect r, List<CompoundTag> shown, Map<String, Boolean> essential) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, r);
        UiRect rows = r.inset(4);
        if (shown.isEmpty()) {
            ui.paragraph(Component.translatable("villagelife.ui.jobs.none"), rows.x(), rows.y() + 2, rows.w(), UiTheme.TEXT_DIM);
            return;
        }
        list.render(ui, rows, shown.size(), ROW, (u, index, row) -> {
            CompoundTag p = shown.get(index);
            String id = p.getStringOr("id", "");
            boolean isSelected = id.equals(selected);
            if (isSelected) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x50E7C66B);
            } else if (u.hovered(row)) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x25E7C66B);
            }
            u.item(workstation(p), row.x() + 1, row.y() + 1);
            int places = p.getListOrEmpty("workplaces").size();
            Component count = Component.literal(places == 0 ? "–" : (places - free(p)) + "/" + places);
            int countWidth = u.font().width(count);
            int color = places == 0 ? UiTheme.TEXT_DIM : isSelected ? UiTheme.GOLD : UiTheme.TEXT;
            Component name = name(id);
            if (essential.getOrDefault(id, false)) {
                name = Component.literal("★ ").append(name);
            }
            u.text(name, row.inset(20, 0, countWidth + 8, 0), color, -1);
            u.text(count, row.inset(0, 0, 2, 0), issues(p) > 0 ? UiTheme.BAD : free(p) > 0 ? UiTheme.WARN : UiTheme.TEXT_DIM, 1);
            u.clickable(row, () -> selected = id);
        });
    }

    /** Filtered and searched, in the order of the job priorities (the village's own order). */
    private List<CompoundTag> filtered(ListTag all, Map<String, Integer> ranking) {
        String query = search.value().trim().toLowerCase(Locale.ROOT);
        List<CompoundTag> result = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            CompoundTag p = all.getCompoundOrEmpty(i);
            if (filter.test.test(p) && (query.isEmpty() || name(p.getStringOr("id", "")).getString().toLowerCase(Locale.ROOT).contains(query))) {
                result.add(p);
            }
        }
        result.sort(Comparator.comparingInt(p -> ranking.getOrDefault(p.getStringOr("id", ""), Integer.MAX_VALUE)));
        return result;
    }

    private static Map<String, Integer> ranking(CompoundTag management) {
        Map<String, Integer> ranking = new HashMap<>();
        ListTag list = management.getListOrEmpty("priorities");
        for (int i = 0; i < list.size(); i++) {
            ranking.put(list.getCompoundOrEmpty(i).getStringOr("id", ""), i);
        }
        return ranking;
    }

    private static Map<String, Boolean> essentials(CompoundTag management) {
        Map<String, Boolean> essential = new HashMap<>();
        ListTag list = management.getListOrEmpty("priorities");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag p = list.getCompoundOrEmpty(i);
            essential.put(p.getStringOr("id", ""), p.getBooleanOr("essential", false));
        }
        return essential;
    }

    static int free(CompoundTag profession) {
        ListTag places = profession.getListOrEmpty("workplaces");
        int free = 0;
        for (int i = 0; i < places.size(); i++) {
            free += places.getCompoundOrEmpty(i).getStringOr("worker_id", "").isEmpty() ? 1 : 0;
        }
        return free;
    }

    static int issues(CompoundTag profession) {
        ListTag places = profession.getListOrEmpty("workplaces");
        int issues = 0;
        for (int i = 0; i < places.size(); i++) {
            issues += places.getCompoundOrEmpty(i).getStringOr("issue", "").isEmpty() ? 0 : 1;
        }
        return issues;
    }

    static Component name(String professionId) {
        return Component.translatable(Job.translationKey(professionId));
    }

    static ItemStack workstation(CompoundTag p) {
        List<ItemStack> stacks = stacks(p.getListOrEmpty("workstations"));
        return stacks.isEmpty() ? new ItemStack(Items.CRAFTING_TABLE) : stacks.getFirst();
    }

    static List<ItemStack> stacks(ListTag ids) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            ItemStack stack = stack(ids.getStringOr(i, ""));
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    static ItemStack stack(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(parsed));
    }

    // ---- input ---------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        return search.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return search.charTyped(event);
    }

    @Override
    public void blur() {
        search.blur();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (tab == 1) {
            return priorities.mouseScrolled(x, y, amount);
        }
        return list.mouseScrolled(x, y, amount) || detail.mouseScrolled(x, y, amount);
    }

    @Override
    public boolean mouseDragged(double x, double y) {
        if (tab == 1) {
            return priorities.mouseDragged(x, y);
        }
        return list.mouseDragged(x, y) || detail.mouseDragged(x, y);
    }

    @Override
    public void mouseReleased() {
        list.mouseReleased();
        detail.mouseReleased();
        priorities.mouseReleased();
    }
}
