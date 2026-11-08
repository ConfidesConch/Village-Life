package io.github.confidesconch.villagelife.client.ui.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.book.BuildingActionPayload;
import io.github.confidesconch.villagelife.client.ui.component.ScrollList;
import io.github.confidesconch.villagelife.client.ui.component.TextField;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiPage;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;
import io.github.confidesconch.villagelife.resident.Job;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * "Buildings" of the village window (M21b5): all buildings with search, sorting and filters on the
 * left, the chosen building on the right ({@link BuildingDetail}). Choosing a building asks the server
 * for the contents of its containers.
 */
public final class BuildingsPage implements UiPage {
    private static final int ROW = 14;
    private static final int TOOLBAR = 18;

    /** Filters; the id is also the hint other areas use ("buildings:invalid"). */
    enum Filter {
        ALL(b -> true),
        HOUSES(b -> "house".equals(b.getStringOr("type_id", ""))),
        STORAGE(b -> "storage".equals(b.getStringOr("type_id", ""))),
        WORKPLACES(b -> "workplace".equals(b.getStringOr("type_id", ""))),
        AREAS(b -> "field".equals(b.getStringOr("type_id", "")) || "pasture".equals(b.getStringOr("type_id", ""))),
        INVALID(b -> !b.getBooleanOr("valid", false)),
        NO_CHEST(BuildingsPage::withoutChest),
        FREE_BEDS(b -> b.getBooleanOr("valid", false) && b.getIntOr("beds", 0) > b.getListOrEmpty("residents").size());

        final Predicate<CompoundTag> test;

        Filter(Predicate<CompoundTag> test) {
            this.test = test;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Sort orders. */
    enum Sort { NAME, TYPE, STATE }

    private final TextField search = new TextField(32);
    private final ScrollList list = new ScrollList();
    private final BuildingDetail detail = new BuildingDetail();
    private Filter filter = Filter.ALL;
    private Sort sort = Sort.TYPE;
    private String selected;
    /** Marker position to select once the list is there ("buildings:at:1, 64, 2"). */
    private String selectAt;
    /** The building whose contents were asked for last. */
    private String requested;

    @Override
    public String id() {
        return "buildings";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BRICKS);
    }

    @Override
    public Component title() {
        return Component.translatable("villagelife.ui.page.buildings");
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public List<String> sections() {
        return List.of("buildings");
    }

    @Override
    public void onOpen() {
        requested = null; // the server forgets the contents when another area was open – ask again
    }

    @Override
    public void focus(String hint) {
        search.setValue("");
        list.reset();
        if (hint.startsWith("id=")) { // right click on a building marker (M21b10b)
            filter = Filter.ALL;
            selected = hint.substring(3);
            requested = selected; // the server sends its contents already
            return;
        }
        if (hint.startsWith("at:")) {
            filter = Filter.ALL;
            selectAt = hint.substring(3);
            return;
        }
        for (Filter f : Filter.values()) {
            if (f.id().equals(hint)) {
                filter = f;
                selected = null;
            }
        }
    }

    @Override
    public void render(Ui ui, UiRect area, CompoundTag data) {
        CompoundTag section = data.getCompoundOrEmpty("buildings");
        ListTag all = section.getListOrEmpty("list");
        if (selectAt != null && !all.isEmpty()) {
            for (int i = 0; i < all.size(); i++) {
                if (all.getCompoundOrEmpty(i).getStringOr("pos", "").equals(selectAt)) {
                    selected = all.getCompoundOrEmpty(i).getStringOr("id", null);
                }
            }
            selectAt = null;
        }
        renderToolbar(ui, area.top(TOOLBAR));
        UiRect body = area.below(TOOLBAR + 4);
        int listWidth = Math.max(120, body.w() * 42 / 100);
        List<CompoundTag> shown = filtered(all);
        if (shown.stream().noneMatch(b -> b.getStringOr("id", "").equals(selected))) {
            selected = shown.isEmpty() ? null : shown.getFirst().getStringOr("id", null);
        }
        String villageId = data.getStringOr("village_id", "");
        if (selected != null && !selected.equals(requested) && !villageId.isEmpty()) {
            requested = selected;
            ClientPacketDistributor.sendToServer(new BuildingActionPayload(villageId, selected, "view"));
        }
        renderList(ui, body.left(listWidth), shown, all.size());
        CompoundTag building = shown.stream().filter(b -> b.getStringOr("id", "").equals(selected)).findFirst().orElse(null);
        detail.render(ui, body.rightOf(listWidth + 6), building, section, villageId);
    }

    private void renderToolbar(Ui ui, UiRect bar) {
        int searchWidth = Math.min(150, bar.w() * 36 / 100);
        String before = search.value();
        search.render(ui, bar.left(searchWidth), Component.translatable("villagelife.ui.search"));
        UiRect rest = bar.rightOf(searchWidth + 6);
        int half = (rest.w() - 6) / 2;
        UiRect sortButton = rest.left(half);
        UiRect filterButton = rest.rightOf(half + 6);
        int sorts = Sort.values().length;
        ui.button(sortButton, Component.translatable("villagelife.ui.citizens.sort",
                Component.translatable("villagelife.ui.buildings.sort." + sort.name().toLowerCase(Locale.ROOT))), true,
                () -> sort = Sort.values()[(sort.ordinal() + 1) % sorts]);
        ui.clickable(sortButton, null, () -> sort = Sort.values()[(sort.ordinal() + sorts - 1) % sorts]);
        int filters = Filter.values().length;
        ui.button(filterButton, Component.translatable("villagelife.ui.citizens.filter",
                Component.translatable("villagelife.ui.buildings.filter." + filter.id())), true,
                () -> setFilter(Filter.values()[(filter.ordinal() + 1) % filters]));
        ui.clickable(filterButton, null, () -> setFilter(Filter.values()[(filter.ordinal() + filters - 1) % filters]));
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
        ui.text(Component.translatable("villagelife.ui.buildings.count", shown.size(), total), inner.x(), inner.y() + 1,
                UiTheme.GOLD, inner.w());
        UiRect rows = inner.below(13);
        if (shown.isEmpty()) {
            ui.paragraph(Component.translatable(total == 0 ? "villagelife.book.buildings.none" : "villagelife.ui.buildings.none"),
                    rows.x(), rows.y() + 2, rows.w(), UiTheme.TEXT_DIM);
            return;
        }
        list.render(ui, rows, shown.size(), ROW, (u, index, row) -> {
            CompoundTag b = shown.get(index);
            String id = b.getStringOr("id", "");
            boolean isSelected = id.equals(selected);
            if (isSelected) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x50E7C66B);
            } else if (u.hovered(row)) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x25E7C66B);
            }
            u.graphics().fill(row.x() + 2, row.y() + 5, row.x() + 6, row.y() + 9, stateColor(b));
            int nameWidth = row.w() * 62 / 100;
            u.text(name(b), row.inset(10, 0, 0, 0).left(nameWidth - 10), isSelected ? UiTheme.GOLD : UiTheme.TEXT, -1);
            u.text(Component.literal(b.getStringOr("pos", "")), row.rightOf(nameWidth + 2).inset(0, 0, 2, 0), UiTheme.TEXT_DIM, 1);
            u.clickable(row, () -> selected = id);
        });
    }

    private List<CompoundTag> filtered(ListTag all) {
        String query = search.value().trim().toLowerCase(Locale.ROOT);
        List<CompoundTag> result = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            CompoundTag b = all.getCompoundOrEmpty(i);
            if (filter.test.test(b) && (query.isEmpty() || name(b).getString().toLowerCase(Locale.ROOT).contains(query)
                    || b.getStringOr("pos", "").contains(query) || people(b).contains(query))) {
                result.add(b);
            }
        }
        Comparator<CompoundTag> byName = Comparator.comparing(b -> name(b).getString(), String.CASE_INSENSITIVE_ORDER);
        Comparator<CompoundTag> order = switch (sort) {
            case NAME -> byName;
            case TYPE -> Comparator.<CompoundTag, String>comparing(b -> b.getStringOr("type_id", "")).thenComparing(byName);
            case STATE -> Comparator.<CompoundTag>comparingInt(BuildingsPage::stateRank).thenComparing(byName); // problems first
        };
        result.sort(order);
        return result;
    }

    /** Lower-case names of everybody living or working in the building (the search finds "Anna's house"). */
    private static String people(CompoundTag b) {
        StringBuilder names = new StringBuilder();
        for (String key : List.of("residents", "workers")) {
            ListTag people = b.getListOrEmpty(key);
            for (int i = 0; i < people.size(); i++) {
                names.append(people.getCompoundOrEmpty(i).getStringOr("name", "").toLowerCase(Locale.ROOT)).append('|');
            }
        }
        return names.toString();
    }

    static boolean withoutChest(CompoundTag b) {
        return "workplace".equals(b.getStringOr("type_id", "")) && b.getBooleanOr("valid", false) && b.getIntOr("containers", 0) == 0;
    }

    private static int stateRank(CompoundTag b) {
        if (!b.getBooleanOr("valid", false)) {
            return 0;
        }
        return withoutChest(b) || (b.getBooleanOr("needs_area", false) && !b.contains("work_area") && !b.contains("auto_area"))
                ? 1 : 2;
    }

    static int stateColor(CompoundTag b) {
        return switch (stateRank(b)) {
            case 0 -> UiTheme.BAD;
            case 1 -> UiTheme.WARN;
            default -> UiTheme.GOOD;
        };
    }

    /**
     * Name of a building (same rule as the book): a workplace's own name ("Küche"), else "Arbeitsplatz:
     * Koch" for professions from data packs without a name, else the building type.
     */
    static Component name(CompoundTag b) {
        String profession = b.getStringOr("profession", "");
        if (profession.isEmpty()) {
            return Component.translatable(b.getStringOr("type", ""));
        }
        String professionKey = Job.translationKey(profession);
        String workplaceKey = professionKey.replace(".profession.", ".workplace.");
        if (Language.getInstance().has(workplaceKey)) {
            return Component.translatable(workplaceKey);
        }
        return Component.translatable("villagelife.book.building.workplace_named", Component.translatable(professionKey));
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
