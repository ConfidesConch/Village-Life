package io.github.confidesconch.villagelife.client.ui.core;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.confidesconch.villagelife.book.VillageBookMenu;
import io.github.confidesconch.villagelife.book.VillageSectionRequestPayload;
import io.github.confidesconch.villagelife.client.book.VillageBookKeys;
import io.github.confidesconch.villagelife.core.config.ClientConfig;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The village window (M21b1, replaces the village book in M21b10): one screen with a navigation on the
 * left, a header with the village and its live values, the open area in the middle and a footer with
 * the window size slider (owner's choice "2a": 60–100 % of the screen). Still a container screen, so
 * the player's real inventory can be shown on the player page (owner's choice "3b"). All data comes from
 * the server snapshot in the {@link VillageBookMenu}; the areas are registered in {@link UiPages}.
 */
public class VillageScreen extends AbstractContainerScreen<VillageBookMenu> {
    private static final int MIN_WIDTH = 320;
    private static final int MIN_HEIGHT = 200;
    private static final int BORDER = 10;
    private static final int HEADER = 26;
    private static final int FOOTER = 16;
    private static final int NAV_WIDE = 112;
    private static final int NAV_NARROW = 26;
    private static final int NAV_ROW = 20;
    private static final int GAP = 6;
    private static final int SLIDER_WIDTH = 64;

    /** M21b2: the header's sections, asked for with every request. */
    private static final List<String> HEADER_SECTIONS = List.of("overview", "growth", "market");
    /** M21b2: live values are asked for again this often while the window is open (5 s). */
    private static final int REFRESH_TICKS = 100;

    /** The area that was open last time (kept while the game runs). */
    private static String lastPage = "";

    private final List<Ui.Clickable> clickables = new ArrayList<>();
    private UiPage page;
    private boolean draggingSize;
    private int pendingSize = -1;
    /** Where the size slider was when the drag began: the window changes size while dragging, the slider must not move. */
    private UiRect dragTrack;
    private int ticks;
    private String shownVillage = "";

    public VillageScreen(VillageBookMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 0, 0); // the window sizes itself to the screen (see layout)
        List<UiPage> pages = UiPages.all();
        page = pages.stream().filter(p -> p.id().equals(lastPage)).findFirst().orElse(pages.isEmpty() ? null : pages.getFirst());
    }

    private CompoundTag data() {
        return menu.data();
    }

    private boolean hasVillage() {
        return !data().getBooleanOr("none", false);
    }

    private int sizePercent() {
        return pendingSize >= 0 ? pendingSize : ClientConfig.UI_SIZE.get();
    }

    // ---- layout --------------------------------------------------------------------------------

    /** The window: {@link #sizePercent()} of the screen, at least 320 × 200 (or the whole screen if smaller). */
    private UiRect window() {
        int w = Math.min(width - 4, Math.max(MIN_WIDTH, width * sizePercent() / 100));
        int h = Math.min(height - 4, Math.max(MIN_HEIGHT, height * sizePercent() / 100));
        return new UiRect((width - w) / 2, (height - h) / 2, w, h);
    }

    private UiRect inner() {
        return window().inset(BORDER);
    }

    private boolean narrow() {
        return inner().w() < 420;
    }

    private UiRect header() {
        return inner().top(HEADER);
    }

    private UiRect footer() {
        return inner().bottomPart(FOOTER);
    }

    private UiRect nav() {
        UiRect body = inner().inset(0, HEADER + GAP, 0, FOOTER + GAP);
        return body.left(narrow() ? NAV_NARROW : NAV_WIDE);
    }

    private UiRect content() {
        UiRect body = inner().inset(0, HEADER + GAP, 0, FOOTER + GAP);
        return body.rightOf((narrow() ? NAV_NARROW : NAV_WIDE) + GAP);
    }

    // ---- drawing -------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        UiTheme.draw(graphics, UiTheme.WINDOW, window());
        // M21b10a: the whole window is drawn here, in the background layer, so the real inventory slots
        // (drawn afterwards by the container screen) lie on top of a page that shows them
        clickables.clear();
        Ui ui = new Ui(graphics, font, mouseX, mouseY, clickables, this::openById);
        HeaderBar.render(ui, header(), data(), hasVillage());
        renderNav(ui);
        renderContent(ui);
        renderFooter(ui);
        int[] origin = page == null || (page.needsVillage() && !hasVillage()) ? null : page.inventoryOrigin(content());
        menu.setInventoryShown(origin != null);
        if (origin != null) {
            leftPos = origin[0]; // slot positions are relative to this corner
            topPos = origin[1];
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        // the window draws its own texts
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // the window itself is drawn in extractBackground; here only the inventory slots, when a page shows them
        super.extractContents(graphics, mouseX, mouseY, a);
    }

    private void renderNav(Ui ui) {
        UiRect nav = nav();
        boolean narrow = narrow();
        int y = nav.y();
        for (UiPage entry : UiPages.all()) {
            UiRect row = new UiRect(nav.x(), y, nav.w(), NAV_ROW);
            boolean selected = entry == page;
            boolean hover = ui.hovered(row);
            if (selected) {
                UiTheme.draw(ui.graphics(), UiTheme.NAV_SELECTED, row);
            } else if (hover) {
                UiTheme.draw(ui.graphics(), UiTheme.NAV_HOVER, row);
            }
            ui.item(entry.icon(), row.x() + 5, row.y() + 2);
            if (narrow) {
                ui.tooltip(row, entry.title());
            } else {
                ui.text(entry.title(), row.inset(26, 0, 4, 0), selected ? UiTheme.GOLD : UiTheme.TEXT, -1);
            }
            if (!selected) {
                ui.clickable(row, () -> open(entry));
            }
            y += NAV_ROW + 2;
            if (y + NAV_ROW > nav.bottom()) {
                break;
            }
        }
    }

    private void renderContent(Ui ui) {
        UiRect area = content();
        if (page == null) {
            return;
        }
        if (page.needsVillage() && !hasVillage()) {
            UiTheme.draw(ui.graphics(), UiTheme.PANEL, area);
            UiRect inner = area.inset(12);
            ui.text(Component.translatable("villagelife.book.no_village.title"), inner.x(), inner.y(), UiTheme.INK, inner.w());
            ui.paragraph(Component.translatable("villagelife.book.no_village"), inner.x(), inner.y() + 16, inner.w(), UiTheme.INK);
            return;
        }
        page.render(ui, area, data());
    }

    /** Footer: what the keys do, and the window size slider. */
    private void renderFooter(Ui ui) {
        UiRect footer = footer();
        ui.graphics().fill(footer.x(), footer.y() - 3, footer.right(), footer.y() - 2, 0x66E7C66B);
        Component label = Component.translatable("villagelife.ui.size", sizePercent());
        int labelWidth = font.width(label);
        UiRect track = draggingSize && dragTrack != null ? dragTrack // stays under the mouse while the window resizes
                : new UiRect(footer.right() - SLIDER_WIDTH, footer.y() + 6, SLIDER_WIDTH, 4);
        ui.text(Component.translatable("villagelife.ui.keys"), footer.x(), footer.y() + 4, UiTheme.TEXT_DIM,
                footer.w() - SLIDER_WIDTH - labelWidth - 16);
        ui.text(label, track.x() - labelWidth - 6, footer.y() + 4, UiTheme.TEXT_DIM, labelWidth);
        UiTheme.draw(ui.graphics(), UiTheme.SCROLL_TRACK, track);
        int knob = track.x() + (sizePercent() - ClientConfig.MIN_UI_SIZE) * (track.w() - 6)
                / (ClientConfig.MAX_UI_SIZE - ClientConfig.MIN_UI_SIZE);
        UiTheme.draw(ui.graphics(), UiTheme.SCROLL_THUMB, new UiRect(knob, track.y() - 4, 6, 12));
        ui.clickable(new UiRect(track.x() - 4, footer.y(), track.w() + 8, FOOTER), () -> {
            draggingSize = true;
            dragTrack = track;
        });
    }

    /** "id" or "id:hint" (M21b4). */
    private void openById(String target) {
        int colon = target.indexOf(':');
        String id = colon < 0 ? target : target.substring(0, colon);
        UiPages.all().stream().filter(p -> p.id().equals(id)).findFirst().ifPresent(found -> {
            if (found != page) {
                open(found);
            }
            if (colon >= 0) {
                found.focus(target.substring(colon + 1));
            }
        });
    }

    private void open(UiPage target) {
        if (page != null) {
            page.blur();
        }
        page = target;
        lastPage = target.id();
        target.onOpen();
        requestData();
    }

    @Override
    protected void init() {
        super.init();
        requestData(); // the window opens with its header only; the open area asks for its data
    }

    /** M21b2: asks the server for the header and the open area – nothing else. */
    private void requestData() {
        String villageId = data().getStringOr("village_id", "");
        if (villageId.isEmpty()) {
            return;
        }
        List<String> sections = new ArrayList<>(HEADER_SECTIONS);
        if (page != null) {
            page.sections().stream().filter(k -> !sections.contains(k)).forEach(sections::add);
        }
        ClientPacketDistributor.sendToServer(new VillageSectionRequestPayload(villageId,
                sections.subList(0, Math.min(sections.size(), VillageSectionRequestPayload.MAX_SECTIONS))));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        String focus = data().getStringOr("focus_page", "");
        if (!focus.isEmpty()) { // M21b10b: opened by a right click on a resident, a building marker or the village center
            data().remove("focus_page");
            openById(focus);
        }
        String villageId = data().getStringOr("village_id", "");
        boolean otherVillage = !villageId.equals(shownVillage); // M21b10a: e.g. opened from "My villages"
        shownVillage = villageId;
        if (otherVillage || ++ticks % REFRESH_TICKS == 0) {
            requestData();
        }
    }

    // ---- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (page != null) {
            page.blur();
        }
        if ((right || event.button() == InputConstants.MOUSE_BUTTON_LEFT) && menu.getCarried().isEmpty()
                && Ui.click(clickables, event.x(), event.y(), right)) {
            if (draggingSize) {
                setSizeFromMouse(event.x());
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick); // inventory slots
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingSize) {
            setSizeFromMouse(event.x());
            return true;
        }
        if (page != null && page.mouseDragged(event.x(), event.y())) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSize) {
            draggingSize = false;
            ClientConfig.UI_SIZE.set(sizePercent());
            ClientConfig.UI_SIZE.save();
            pendingSize = -1;
            dragTrack = null;
        }
        if (page != null) {
            page.mouseReleased();
        }
        return super.mouseReleased(event);
    }

    /** Size in steps of 5 %, from where the mouse is on the slider as it was when the drag began. */
    private void setSizeFromMouse(double mouseX) {
        if (dragTrack == null) {
            return;
        }
        double share = (mouseX - dragTrack.x()) / dragTrack.w();
        int span = ClientConfig.MAX_UI_SIZE - ClientConfig.MIN_UI_SIZE;
        int value = ClientConfig.MIN_UI_SIZE + (int) Math.round(Math.max(0, Math.min(1, share)) * span / 5.0) * 5;
        pendingSize = Math.max(ClientConfig.MIN_UI_SIZE, Math.min(ClientConfig.MAX_UI_SIZE, value));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (page != null && content().contains(x, y) && page.mouseScrolled(x, y, scrollY)) {
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (page != null && page.keyPressed(event)) {
            return true; // e.g. typing into the search: "e" and "b" do not close the window
        }
        if (VillageBookKeys.OPEN_BOOK.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (page != null && page.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        if (page != null) {
            page.blur(); // switches text input off again (M21b4)
        }
        super.removed();
    }

    /** Clicks inside the window are never "outside" (that would drop the carried item). */
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return !window().contains(mx, my);
    }
}
