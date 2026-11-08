package io.github.confidesconch.villagelife.client.ui.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.client.ui.component.ScrollList;
import io.github.confidesconch.villagelife.client.ui.component.TextField;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiPage;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;
import io.github.confidesconch.villagelife.resident.needs.NeedsRules;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * "Residents" of the village window (M21b4): on the left all residents with search, sorting and filters,
 * on the right the chosen resident ({@link CitizenDetail}). Everything is read from the server's
 * "residents" and "relations" sections; the page decides nothing.
 */
public final class CitizensPage implements UiPage {
    private static final int ROW = 14;
    private static final int TOOLBAR = 18;

    /** Filters; the id is also the hint other areas use ("citizens:hungry"). */
    enum Filter {
        ALL(r -> true),
        UNEMPLOYED(r -> !r.contains("job") && !r.contains("grows_up_in")),
        HOMELESS(r -> !r.contains("bed")),
        HUNGRY(r -> r.getBooleanOr("loaded", false) && NeedsRules.wantsToEat(r.getDoubleOr("food", 20))),
        UNHAPPY(r -> "unhappy".equals(r.getStringOr("mood", ""))),
        CHILDREN(r -> r.contains("grows_up_in")),
        GUARDS(r -> r.getBooleanOr("defender", false));

        final Predicate<CompoundTag> test;

        Filter(Predicate<CompoundTag> test) {
            this.test = test;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Sort orders. */
    enum Sort { NAME, JOB, MOOD, SINCE }

    private final TextField search = new TextField(32);
    private final ScrollList list = new ScrollList();
    private final CitizenDetail detail = new CitizenDetail();
    private Filter filter = Filter.ALL;
    private Sort sort = Sort.NAME;
    private String selected;

    @Override
    public String id() {
        return "citizens";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.PLAYER_HEAD);
    }

    @Override
    public Component title() {
        return Component.translatable("villagelife.ui.page.citizens");
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<String> sections() {
        return List.of("residents", "relations");
    }

    @Override
    public void focus(String hint) {
        if (hint.startsWith("id=")) { // a resident chosen elsewhere, e.g. in a building (M21b5)
            filter = Filter.ALL;
            search.setValue("");
            selected = hint.substring(3);
            list.reset();
            return;
        }
        for (Filter f : Filter.values()) {
            if (f.id().equals(hint)) {
                filter = f;
                search.setValue("");
                selected = null;
                list.reset();
            }
        }
    }

    @Override
    public void render(Ui ui, UiRect area, CompoundTag data) {
        renderToolbar(ui, area.top(TOOLBAR));
        UiRect body = area.below(TOOLBAR + 4);
        int listWidth = Math.max(110, body.w() * 40 / 100);
        UiRect left = body.left(listWidth);
        List<CompoundTag> shown = filtered(data.getCompoundOrEmpty("residents").getListOrEmpty("list"));
        if (shown.stream().noneMatch(r -> r.getStringOr("id", "").equals(selected))) {
            selected = shown.isEmpty() ? null : shown.getFirst().getStringOr("id", null);
        }
        renderList(ui, left, shown, data.getCompoundOrEmpty("residents").getListOrEmpty("list").size());
        UiRect right = body.rightOf(listWidth + 6);
        CompoundTag resident = shown.stream().filter(r -> r.getStringOr("id", "").equals(selected)).findFirst().orElse(null);
        detail.render(ui, right, resident, data.getCompoundOrEmpty("relations").getCompoundOrEmpty("residents"));
    }

    private void renderToolbar(Ui ui, UiRect bar) {
        int searchWidth = Math.min(150, bar.w() * 36 / 100);
        String before = search.value();
        search.render(ui, bar.left(searchWidth), Component.translatable("villagelife.ui.search"));
        UiRect rest = bar.rightOf(searchWidth + 6);
        int half = (rest.w() - 6) / 2;
        UiRect sortButton = rest.left(half);
        UiRect filterButton = rest.rightOf(half + 6);
        Component sortLabel = Component.translatable("villagelife.ui.citizens.sort",
                Component.translatable("villagelife.ui.citizens.sort." + sort.name().toLowerCase(Locale.ROOT)));
        ui.button(sortButton, sortLabel, true, () -> sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length]);
        ui.clickable(sortButton, null, () -> sort = Sort.values()[(sort.ordinal() + Sort.values().length - 1) % Sort.values().length]);
        Component filterLabel = Component.translatable("villagelife.ui.citizens.filter",
                Component.translatable("villagelife.ui.citizens.filter." + filter.id()));
        ui.button(filterButton, filterLabel, true, () -> setFilter(Filter.values()[(filter.ordinal() + 1) % Filter.values().length]));
        ui.clickable(filterButton, null, () -> setFilter(Filter.values()[(filter.ordinal() + Filter.values().length - 1) % Filter.values().length]));
        ui.tooltip(sortButton, Component.translatable("villagelife.ui.cycle_hint"));
        ui.tooltip(filterButton, Component.translatable("villagelife.ui.cycle_hint"));
        if (filter != Filter.ALL) {
            ui.graphics().fill(filterButton.x() + 2, filterButton.bottom() - 2, filterButton.right() - 2, filterButton.bottom() - 1, UiTheme.GOLD);
        }
        if (!before.equals(search.value())) {
            list.reset();
        }
    }

    private void setFilter(Filter next) {
        filter = next;
        list.reset();
    }

    private void renderList(Ui ui, UiRect r, List<CompoundTag> shown, int total) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, r);
        UiRect inner = r.inset(5);
        ui.text(Component.translatable("villagelife.ui.citizens.count", shown.size(), total), inner.x(), inner.y() + 1,
                UiTheme.GOLD, inner.w());
        UiRect rows = inner.below(13);
        if (shown.isEmpty()) {
            ui.text(Component.translatable("villagelife.ui.citizens.none"), rows.x(), rows.y() + 2, UiTheme.TEXT_DIM, rows.w());
            return;
        }
        list.render(ui, rows, shown.size(), ROW, (u, index, row) -> {
            CompoundTag c = shown.get(index);
            String id = c.getStringOr("id", "");
            boolean isSelected = id.equals(selected);
            if (isSelected) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x50E7C66B);
            } else if (u.hovered(row)) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x25E7C66B);
            }
            if (c.getBooleanOr("loaded", false)) {
                u.graphics().fill(row.x() + 2, row.y() + 5, row.x() + 6, row.y() + 9, moodColor(c.getStringOr("mood", "")));
            }
            int nameWidth = row.w() * 55 / 100;
            u.text(Component.literal(c.getStringOr("name", "?")), row.inset(10, 0, 0, 0).left(nameWidth - 10),
                    isSelected ? UiTheme.GOLD : UiTheme.TEXT, -1);
            u.text(jobName(c), row.rightOf(nameWidth + 2).inset(0, 0, 2, 0), UiTheme.TEXT_DIM, 1);
            u.clickable(row, () -> selected = id);
        });
    }

    /** Search, filter and sort; a new list every frame is cheap for a few hundred residents. */
    private List<CompoundTag> filtered(ListTag all) {
        String query = search.value().trim().toLowerCase(Locale.ROOT);
        List<CompoundTag> result = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            CompoundTag r = all.getCompoundOrEmpty(i);
            if (filter.test.test(r) && (query.isEmpty() || r.getStringOr("name", "").toLowerCase(Locale.ROOT).contains(query)
                    || jobName(r).getString().toLowerCase(Locale.ROOT).contains(query))) {
                result.add(r);
            }
        }
        Comparator<CompoundTag> byName = Comparator.comparing(r -> r.getStringOr("name", ""), String.CASE_INSENSITIVE_ORDER);
        Comparator<CompoundTag> order = switch (sort) {
            case NAME -> byName;
            case JOB -> Comparator.<CompoundTag, String>comparing(r -> jobName(r).getString(), String.CASE_INSENSITIVE_ORDER).thenComparing(byName);
            case MOOD -> Comparator.<CompoundTag>comparingInt(r -> r.getIntOr("satisfaction", 101)).thenComparing(byName); // unhappy first
            case SINCE -> Comparator.<CompoundTag>comparingLong(r -> r.getLongOr("joined_day", 0)).thenComparing(byName);
        };
        result.sort(order);
        return result;
    }

    static Component jobName(CompoundTag r) {
        if (r.contains("grows_up_in")) {
            return Component.translatable("villagelife.book.resident.child_short");
        }
        String job = r.getStringOr("job", "");
        return job.isEmpty() ? Component.translatable("villagelife.ui.no_job") : Component.translatable(job);
    }

    static int moodColor(String mood) {
        return switch (mood) {
            case "happy" -> UiTheme.GOOD;
            case "unhappy" -> UiTheme.BAD;
            default -> UiTheme.WARN;
        };
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
        return list.mouseScrolled(x, y, amount) || detail.mouseScrolled(x, y, amount);
    }

    @Override
    public boolean mouseDragged(double x, double y) {
        return list.mouseDragged(x, y) || detail.mouseDragged(x, y);
    }

    @Override
    public void mouseReleased() {
        list.mouseReleased();
        detail.mouseReleased();
    }
}
