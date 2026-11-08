package io.github.confidesconch.villagelife.book;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.registry.ModBlocks;
import io.github.confidesconch.villagelife.core.util.ModProfiler;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Opens the village window for a player (M11a as book, since M21b10b only the window): by key, by
 * right-clicking the village center, a building marker or a resident. The server picks the village and
 * decides what the player may see: the owner sees his villages from anywhere in the dimension, everybody
 * else only the village he is standing in. The window then asks for the data of the area it shows.
 */
public final class VillageBookService {
    private VillageBookService() {}

    /** Book key pressed on the client, or a village clicked on the character page (M11h). */
    public static void onOpenRequest(OpenVillageBookPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Village wanted = null;
        if (!payload.villageId().isEmpty()) {
            try {
                wanted = VillageManager.get(player.level()).byId(UUID.fromString(payload.villageId()))
                        .filter(v -> maySee(player, v)).orElse(null);
            } catch (IllegalArgumentException invalid) {
                return;
            }
        }
        open(player, wanted, null, wanted == null ? null : "overview"); // payload.window() no longer matters (M21b10b)
    }

    /** M21b2: the sections each player's open village window shows (refreshed after actions); server thread only. */
    private static final java.util.Map<UUID, java.util.Set<String>> WINDOW_SECTIONS = new java.util.HashMap<>();
    /** M21b5: the building whose container contents the window shows (chosen in "Buildings"). */
    private static final java.util.Map<UUID, UUID> WINDOW_BUILDING = new java.util.HashMap<>();

    /** Forgets everything kept in memory for the stopped server (bug review 08.11.2026). */
    public static void clear() {
        WINDOW_SECTIONS.clear();
        WINDOW_BUILDING.clear();
    }
    /** M21b2: what a new window needs at once – its header. The areas ask for their own data. */
    private static final java.util.List<String> HEADER_SECTIONS = java.util.List.of("overview", "growth", "market");

    /** M21b2: the window asks for the data of the areas it shows. */
    public static void onSectionRequest(VillageSectionRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof VillageBookMenu)) {
            return; // only for an open window
        }
        Village village;
        try {
            village = VillageManager.get(player.level()).byId(UUID.fromString(payload.villageId()))
                    .filter(v -> maySee(player, v)).orElse(null);
        } catch (IllegalArgumentException invalid) {
            return;
        }
        if (village == null) {
            return;
        }
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        payload.sections().stream().filter(BookSections::isKnown).limit(VillageSectionRequestPayload.MAX_SECTIONS)
                .forEach(keys::add);
        WINDOW_SECTIONS.put(player.getUUID(), keys);
        ModProfiler.time(ModProfiler.BOOK, () -> sendSections(player, village, keys));
    }

    private static void sendSections(ServerPlayer player, Village village, java.util.Collection<String> keys) {
        CompoundTag data = BookSections.buildSome(player, village, keys);
        data.putString("village_id", village.id().toString());
        data.putString("name", village.customName().orElse(""));
        data.putString("owner_name", village.ownerName());
        UUID building = WINDOW_BUILDING.get(player.getUUID());
        if (building != null && keys.contains("buildings")) {
            BuildingsSection.addFocus(player.level(), village, building, data); // M21b5: contents of the chosen building
        }
        PacketDistributor.sendToPlayer(player, new VillageSectionDataPayload(data));
    }

    /** A player left: forget his window. */
    public static void forget(UUID player) {
        WINDOW_SECTIONS.remove(player);
        WINDOW_BUILDING.remove(player);
    }

    /** Right-click on a village center opens the book of that village at its professions (M13b). */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !level.getBlockState(event.getPos()).is(ModBlocks.VILLAGE_CENTER.get()) || player.isShiftKeyDown()) {
            return;
        }
        Optional<Village> village = VillageManager.get(level).findAt(event.getPos())
                .filter(v -> v.center().equals(event.getPos()));
        open(player, village.orElse(null), null, "professions"); // the village's professions at a glance (M13b)
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Right-click on a building marker opens the book at that building's page (M11i); sneaking keeps the old rescan. */
    public static void onRightClickMarker(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND || player.isShiftKeyDown()
                || !level.getBlockState(event.getPos()).is(ModBlocks.BUILDING_MARKER.get())) {
            return;
        }
        Optional<VillageManager.Located> located = VillageManager.get(level).findBuilding(event.getPos())
                .filter(l -> maySee(player, l.village()));
        if (located.isEmpty()) {
            return; // not registered or not ours to see: the marker reacts as before (scan and message)
        }
        openBuilding(player, located.get().village(), located.get().building().id());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Right-click on a resident opens the book at that resident's page (M11b). */
    public static void onInteractResident(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ResidentEntity resident)) {
            return;
        }
        // handled on both sides, so neither the client nor the server runs the vanilla interaction
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND) {
            Optional<Village> village = resident.getVillageId().flatMap(id -> VillageManager.get(player.level()).byId(id));
            open(player, village.orElse(null), resident.getUUID());
        }
    }

    /**
     * Sends the book to the player. {@code preferred} (e.g. the clicked village) is used if the player
     * may see it; otherwise the village the player stands in, otherwise his nearest own village.
     */
    public static void open(ServerPlayer player, Village preferred, UUID focusResident) {
        open(player, preferred, focusResident, null);
    }

    /** Like {@link #open(ServerPlayer, Village, UUID)}, and asks the book to show chapter {@code focusChapter}. */
    public static void open(ServerPlayer player, Village preferred, UUID focusResident, String focusChapter) {
        open(player, preferred, focusResident, focusChapter, null);
    }

    /** Opens the book at the page of building {@code buildingId}, with the contents of its containers (M11i). */
    public static void openBuilding(ServerPlayer player, Village village, UUID buildingId) {
        open(player, village, null, null, buildingId);
    }

    private static void open(ServerPlayer player, Village preferred, UUID focusResident, String focusChapter, UUID focusBuilding) {
        ModProfiler.time(ModProfiler.BOOK, () -> build(player, preferred, focusResident, focusChapter, focusBuilding));
    }

    private static void build(ServerPlayer player, Village preferred, UUID focusResident, String focusChapter, UUID focusBuilding) {
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        VillageManager manager = VillageManager.get(level);
        Optional<Village> village = Optional.ofNullable(preferred)
                .or(() -> manager.findAt(pos))
                .or(() -> manager.all().stream()
                        .filter(v -> v.mayManage(player.getUUID()))
                        .min(Comparator.comparingDouble(v -> v.center().distSqr(pos))))
                .filter(v -> maySee(player, v));
        if (player.containerMenu instanceof VillageBookMenu && village.isPresent()
                && WINDOW_SECTIONS.containsKey(player.getUUID())) {
            // M21b2: the window is open – after an action only its areas come back, not the whole book
            if (focusBuilding != null) {
                WINDOW_BUILDING.put(player.getUUID(), focusBuilding);
            }
            sendSections(player, village.get(), WINDOW_SECTIONS.get(player.getUUID()));
            return;
        }
        WINDOW_SECTIONS.put(player.getUUID(), new java.util.LinkedHashSet<>(HEADER_SECTIONS));
        WINDOW_BUILDING.remove(player.getUUID());
        CompoundTag data = new CompoundTag();
        if (village.isEmpty()) {
            data.putBoolean("none", true);
        } else {
            data = BookSections.buildSome(player, village.get(), HEADER_SECTIONS); // M21b2: only the header
            data.putString("village_id", village.get().id().toString());
            data.putString("name", village.get().customName().orElse(""));
            data.putString("owner_name", village.get().ownerName());
            String page = focusPage(focusResident, focusChapter, focusBuilding);
            if (page != null) {
                data.putString("focus_page", page); // M21b10b: the window opens this area
            }
            if (focusBuilding != null) {
                WINDOW_BUILDING.put(player.getUUID(), focusBuilding); // its container contents come with "buildings"
            }
        }
        BookSections.addGlobal(player, data);
        data.putBoolean("window", true);
        if (player.containerMenu instanceof VillageBookMenu) {
            PacketDistributor.sendToPlayer(player, new VillageBookPayload(data)); // already open: refresh
        } else {
            CompoundTag initial = data;
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new VillageBookMenu(id, inventory, initial),
                    Component.translatable("villagelife.book.title")), buf -> buf.writeNbt(initial));
        }
        VillageLife.LOGGER.debug("Sent village book to {} ({})", player.getName().getString(),
                village.map(v -> v.id().toString()).orElse("no village"));
    }

    /** The window area for a right click (M21b10b): a resident, a building, or the old book chapter names. */
    private static String focusPage(UUID resident, String chapter, UUID building) {
        if (resident != null) {
            return "citizens:id=" + resident;
        }
        if (building != null) {
            return "buildings:id=" + building;
        }
        if (chapter == null) {
            return null;
        }
        return switch (chapter) {
            case "professions" -> "jobs";
            case "market" -> "economy";
            case "contracts" -> "economy:contracts";
            case "settings", "management" -> "admin";
            default -> chapter.equals("overview") ? "overview" : null;
        };
    }

    /** Owners and managers (M11g2) see their villages from anywhere; others only the village they stand in, if the owner allows it (M11c). */
    static boolean maySee(ServerPlayer player, Village village) {
        return village.mayManage(player.getUUID())
                || (village.settings().strangersMayRead() && village.area().contains(player.getBlockX(), player.getBlockZ()));
    }
}
