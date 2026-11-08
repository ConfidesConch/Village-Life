package io.github.confidesconch.villagelife.building;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.api.ApiEvents;
import io.github.confidesconch.villagelife.building.BuildingRequirement.Problem;
import io.github.confidesconch.villagelife.building.scan.LevelScanView;
import io.github.confidesconch.villagelife.building.scan.RoomScanner;
import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.core.registry.ModBlocks;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageService;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Registers buildings: scans the room around a marker, checks the type's requirements and stores
 * the result (valid or invalid with reason) in the village. Runs on the server thread; one scan is
 * capped by {@code maxBuildingVolume}.
 */
public final class BuildingService {
    private BuildingService() {}

    /** Blocks that count as storage (chests, barrels, ...). Defined in data/villagelife/tags/block. */
    public static final TagKey<Block> STORAGE_CONTAINERS = tag("storage_containers");
    /** Blocks that count as work stations (crafting table, furnace, ...). */
    public static final TagKey<Block> WORKSTATIONS = tag("workstations");

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, path));
    }

    /** Result of checking one marker: the scanned contents and, if invalid, why. */
    public record Evaluation(Optional<Problem> problem, RoomScanner.Result scan, Contents contents) {
        public boolean isValid() {
            return problem.isEmpty();
        }

        public int volume() {
            return scan.isSuccess() ? scan.interior().size() : 0;
        }
    }

    /** Beds, doors, containers and work stations around the room, plus other markers inside it. */
    public static final class Contents {
        final List<BlockPos> beds = new ArrayList<>();
        final List<BlockPos> doors = new ArrayList<>();
        final List<BlockPos> containers = new ArrayList<>();
        final List<BlockPos> workstations = new ArrayList<>();
        boolean otherMarker;

        static final Contents EMPTY = new Contents();
    }

    /** Scans and checks the room around a marker without changing any saved data. */
    public static Evaluation evaluate(ServerLevel level, BlockPos markerPos, BuildingType type) {
        int maxVolume = SyncedConfig.MAX_BUILDING_VOLUME.getAsInt();
        RoomScanner.Result result = scanRoom(level, markerPos, maxVolume);
        if (!result.isSuccess()) {
            String reason = switch (result.failure()) {
                case NO_ROOF -> "villagelife.building.problem.no_roof";
                case UNLOADED -> "villagelife.building.problem.unloaded";
                case NOT_ENCLOSED -> "villagelife.building.problem.not_enclosed";
            };
            return new Evaluation(Optional.of(new Problem(reason, maxVolume)), result, Contents.EMPTY);
        }
        Contents contents = classify(level, markerPos, result);
        if (contents.otherMarker || registeredElsewhere(level, markerPos, result)) {
            return new Evaluation(Optional.of(new Problem("villagelife.building.problem.already_registered", 0)),
                    result, contents);
        }
        BuildingRequirement.Counts counts = new BuildingRequirement.Counts(result.interior().size(),
                contents.doors.size(), contents.beds.size(), contents.containers.size(), contents.workstations.size());
        return new Evaluation(type.requirement().check(counts), result, contents);
    }

    /** Player placed or clicked a marker: (re)register the building and report the result. */
    public static void scanAndRegister(ServerLevel level, BlockPos markerPos, BuildingType type, Player player) {
        VillageManager manager = VillageManager.get(level);
        Optional<Village> village = manager.findAt(markerPos);
        Component typeName = Component.translatable(type.translationKey());
        if (village.isEmpty()) {
            manager.removeBuildingAt(markerPos);
            showValidity(level, markerPos, false);
            tell(player, Component.translatable("villagelife.building.problem.not_in_village", typeName), false);
            return;
        }
        Optional<Building> previous = manager.findBuilding(markerPos).map(VillageManager.Located::building);
        Building building = type.isArea() ? AreaService.build(level, markerPos, type, previous) // M21d
                : toBuilding(level, markerPos, type, evaluate(level, markerPos, type), previous);
        manager.removeBuildingAt(markerPos); // in case the marker belonged to another village before
        manager.putBuilding(village.get(), building);
        ApiEvents.buildingRegistered(level, village.get().id(), building, previous.isEmpty()); // M22a
        BuildingRescanner.forget(level, markerPos);
        showValidity(level, markerPos, building.isValid());

        if (building.isValid() && type.isArea()) {
            tell(player, Component.translatable("villagelife.building.area_valid", typeName, village.get().displayName(),
                    building.interiorVolume()), true);
        } else if (building.isValid()) {
            tell(player, Component.translatable("villagelife.building.valid", typeName, village.get().displayName(),
                    building.interiorVolume(), building.doors().size(), building.beds().size(),
                    building.containers().size(), building.workstations().size()), true);
        } else {
            tell(player, Component.translatable("villagelife.building.invalid", typeName, describe(building.problem().get())), false);
        }
    }

    /** Automatic rescan (after changes or periodically). Tells the owner only when validity changes. */
    public static void rescan(ServerLevel level, BlockPos markerPos) {
        VillageManager manager = VillageManager.get(level);
        Optional<VillageManager.Located> located = manager.findBuilding(markerPos);
        if (located.isEmpty()) {
            return;
        }
        BlockState state = level.getBlockState(markerPos);
        if (!state.is(ModBlocks.BUILDING_MARKER.get())) {
            manager.removeBuildingAt(markerPos); // marker vanished without a removal event
            return;
        }
        Building old = located.get().building();
        BuildingType type = state.getValue(BuildingMarkerBlock.TYPE);
        Building updated;
        boolean unloaded;
        if (type.isArea()) { // M21d: field or pasture
            updated = AreaService.build(level, markerPos, type, Optional.of(old));
            unloaded = updated.problem().filter(p -> AreaService.UNLOADED.equals(p.reasonKey())).isPresent();
        } else {
            Evaluation evaluation = evaluate(level, markerPos, type);
            unloaded = !evaluation.scan().isSuccess() && evaluation.scan().failure() == RoomScanner.Failure.UNLOADED;
            updated = toBuilding(level, markerPos, type, evaluation, Optional.of(old));
        }
        if (unloaded) {
            // part of the room is not loaded right now: keep the last result, try again next round
            manager.putBuilding(located.get().village(), withScanTime(old, level.getGameTime()));
            return;
        }
        manager.putBuilding(located.get().village(), updated);
        showValidity(level, markerPos, updated.isValid());

        if (old.isValid() != updated.isValid()) {
            Component typeName = nameOf(level, updated);
            Component message = updated.isValid()
                    ? Component.translatable("villagelife.building.valid_again", typeName, markerPos.toShortString())
                    : Component.translatable("villagelife.building.now_invalid", typeName, markerPos.toShortString(),
                    describe(updated.problem().get()));
            // M19a: to owner and managers, unless they switched building messages off
            VillageService.tellManagers(level.getServer(), located.get().village(), NotificationTopic.BUILDINGS,
                    message.copy().withStyle(updated.isValid() ? ChatFormatting.GREEN : ChatFormatting.RED));
            VillageLife.LOGGER.debug("Building {} at {} is now {}", old.id(), markerPos, updated.isValid() ? "valid" : "invalid");
        }
    }

    /**
     * Finds the profession of a workplace from its work stations (set by the profession system, which
     * knows the profession files); empty = unknown.
     */
    private static java.util.function.Function<List<Block>, Optional<String>> professionOfStations = blocks -> Optional.empty();

    public static void setProfessionResolver(java.util.function.Function<List<Block>, Optional<String>> resolver) {
        professionOfStations = resolver;
    }

    /**
     * Name of a building for messages (M19a): a workplace by its profession ("Küche"), as the book shows it;
     * professions from data packs without a workplace name show the profession name.
     */
    private static Component nameOf(ServerLevel level, Building building) {
        Component typeName = Component.translatable(building.type().translationKey());
        if (building.type() != BuildingType.WORKPLACE) {
            return typeName;
        }
        Optional<String> profession = professionOfStations.apply(building.workstations().stream()
                .filter(level::isLoaded).map(pos -> level.getBlockState(pos).getBlock()).toList());
        if (profession.isEmpty()) {
            return typeName;
        }
        String id = profession.get();
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = (colon < 0 ? id : id.substring(colon + 1)).replace('/', '.');
        return Component.translatableWithFallback(namespace + ".workplace." + path, "%s",
                Component.translatable(namespace + ".profession." + path));
    }

    /** The marker frame shows the building's symbol and glows purple while the building is valid (M18d). */
    static void showValidity(ServerLevel level, BlockPos markerPos, boolean valid) {
        BlockState state = level.getBlockState(markerPos);
        if (!state.is(ModBlocks.BUILDING_MARKER.get())) {
            return;
        }
        MarkerSymbol symbol = symbolOf(level, markerPos, state.getValue(BuildingMarkerBlock.TYPE));
        if (state.getValue(BuildingMarkerBlock.VALID) != valid || state.getValue(BuildingMarkerBlock.SYMBOL) != symbol) {
            level.setBlock(markerPos, state.setValue(BuildingMarkerBlock.VALID, valid)
                    .setValue(BuildingMarkerBlock.SYMBOL, symbol), Block.UPDATE_CLIENTS);
        }
    }

    private static MarkerSymbol symbolOf(ServerLevel level, BlockPos markerPos, BuildingType type) {
        return switch (type) {
            case HOUSE -> MarkerSymbol.HOUSE;
            case STORAGE -> MarkerSymbol.STORAGE;
            case FIELD -> MarkerSymbol.FIELD;
            case PASTURE -> MarkerSymbol.PASTURE;
            case WORKPLACE -> VillageManager.get(level).findBuilding(markerPos)
                    .map(located -> located.building().workstations().stream()
                            .filter(level::isLoaded).map(pos -> level.getBlockState(pos).getBlock()).toList())
                    .flatMap(professionOfStations)
                    .map(MarkerSymbol::forProfession).orElse(MarkerSymbol.WORKPLACE);
        };
    }

    /** The air cells of a registered room as they are now (M21e: places in the tavern); empty if it is not closed. */
    public static List<BlockPos> interior(ServerLevel level, Building building) {
        if (building.type().isArea() || !level.isLoaded(building.markerPos())) {
            return List.of();
        }
        RoomScanner.Result result = scanRoom(level, building.markerPos(), SyncedConfig.MAX_BUILDING_VOLUME.getAsInt());
        return result.isSuccess() ? result.interior().stream().map(c -> new BlockPos(c.x(), c.y(), c.z())).toList() : List.of();
    }

    public static void onMarkerRemoved(ServerLevel level, BlockPos markerPos) {
        BuildingRescanner.forget(level, markerPos);
        VillageManager manager = VillageManager.get(level);
        Optional<UUID> villageId = manager.findBuilding(markerPos).map(located -> located.village().id());
        manager.removeBuildingAt(markerPos).ifPresent(b -> {
            VillageLife.LOGGER.debug("Building {} at {} unregistered", b.id(), markerPos);
            villageId.ifPresent(id -> ApiEvents.buildingRemoved(level, id, b)); // M22a
        });
    }

    public static Component describe(Problem problem) {
        return Component.translatable(problem.reasonKey(), problem.value());
    }

    private static Building toBuilding(ServerLevel level, BlockPos markerPos, BuildingType type,
                                       Evaluation evaluation, Optional<Building> previous) {
        UUID id = previous.map(Building::id).orElseGet(UUID::randomUUID);
        long now = level.getGameTime();
        if (!evaluation.scan().isSuccess()) {
            // no room found: keep the last known bounds so later changes nearby still trigger a rescan
            BlockPos min = previous.map(Building::min).orElse(markerPos);
            BlockPos max = previous.map(Building::max).orElse(markerPos);
            return new Building(id, type, markerPos, min, max, 0, List.of(), List.of(), List.of(), List.of(),
                    now, evaluation.problem());
        }
        RoomScanner.Result scan = evaluation.scan();
        Contents c = evaluation.contents();
        return new Building(id, type, markerPos,
                new BlockPos(scan.min().x(), scan.min().y(), scan.min().z()),
                new BlockPos(scan.max().x(), scan.max().y(), scan.max().z()),
                evaluation.volume(), c.beds, c.doors, c.containers, c.workstations, now, evaluation.problem());
    }

    private static Building withScanTime(Building b, long now) {
        return new Building(b.id(), b.type(), b.markerPos(), b.min(), b.max(), b.interiorVolume(), b.beds(),
                b.doors(), b.containers(), b.workstations(), now, b.problem());
    }

    /**
     * Finds the room the marker belongs to (M18d, owner's wish): the marker may hang inside the room or
     * outside on its wall. Tries, in this order: the marker's own spot (inside), the room behind the wall
     * it hangs on (also behind thick walls, at door height), and both sides of every door nearby – so
     * buildings with a porch, a balcony or a corridor as entrance are found too. The first enclosed room
     * wins; if none is found, the most telling failure is reported.
     */
    static RoomScanner.Result scanRoom(ServerLevel level, BlockPos markerPos, int maxVolume) {
        LevelScanView view = new LevelScanView(level);
        RoomScanner.Result first = null;
        RoomScanner.Result behindWall = null;
        for (StartCandidate candidate : startCandidates(level, markerPos)) {
            BlockPos start = candidate.pos();
            RoomScanner.Result result = RoomScanner.scan(view, new RoomScanner.Cell(start.getX(), start.getY(), start.getZ()), maxVolume);
            if (result.isSuccess() || result.failure() == RoomScanner.Failure.UNLOADED) {
                return result;
            }
            first = first == null ? result : first;
            behindWall = behindWall == null && candidate.behindWall() ? result : behindWall;
        }
        boolean markerInside = !view.canSeeSky(markerPos.getX(), markerPos.getY(), markerPos.getZ());
        RoomScanner.Result reported = markerInside ? first : behindWall != null ? behindWall : first;
        return reported != null ? reported : RoomScanner.scan(view,
                new RoomScanner.Cell(markerPos.getX(), markerPos.getY(), markerPos.getZ()), maxVolume);
    }

    private record StartCandidate(BlockPos pos, boolean behindWall) {}

    private static final int DOOR_SEARCH_RADIUS = 6;

    private static List<StartCandidate> startCandidates(ServerLevel level, BlockPos markerPos) {
        List<StartCandidate> candidates = new ArrayList<>();
        Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.function.BiConsumer<BlockPos, Boolean> add = (pos, behind) -> {
            if (seen.add(pos) && level.isLoaded(pos)
                    && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                candidates.add(new StartCandidate(pos.immutable(), behind));
            }
        };
        add.accept(markerPos, false);
        BlockState marker = level.getBlockState(markerPos);
        if (marker.is(ModBlocks.BUILDING_MARKER.get())) {
            Direction intoWall = marker.getValue(BuildingMarkerBlock.FACING).getOpposite();
            for (int depth = 2; depth <= 4; depth++) {
                for (int down = 0; down <= 2; down++) {
                    add.accept(markerPos.relative(intoWall, depth).below(down), true);
                }
            }
        }
        List<BlockPos> doors = new ArrayList<>(); // nearest door first, so a neighbour's door comes last
        for (BlockPos pos : BlockPos.betweenClosed(markerPos.offset(-DOOR_SEARCH_RADIUS, -3, -DOOR_SEARCH_RADIUS),
                markerPos.offset(DOOR_SEARCH_RADIUS, 2, DOOR_SEARCH_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.DOORS) && state.hasProperty(DoorBlock.HALF)
                    && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                doors.add(pos.immutable());
            }
        }
        doors.sort(java.util.Comparator.comparingDouble(door -> door.distSqr(markerPos)));
        for (BlockPos door : doors) {
            Direction facing = level.getBlockState(door).getValue(DoorBlock.FACING);
            add.accept(door.relative(facing), false);
            add.accept(door.relative(facing.getOpposite()), false);
        }
        return candidates;
    }

    /** True if another marker already registered exactly this room (markers outside are not in the room). */
    private static boolean registeredElsewhere(ServerLevel level, BlockPos markerPos, RoomScanner.Result result) {
        BlockPos min = new BlockPos(result.min().x(), result.min().y(), result.min().z());
        BlockPos max = new BlockPos(result.max().x(), result.max().y(), result.max().z());
        return VillageManager.get(level).all().stream().flatMap(v -> v.buildings().stream())
                .anyMatch(b -> !b.markerPos().equals(markerPos) && b.min().equals(min) && b.max().equals(max));
    }

    private static Contents classify(ServerLevel level, BlockPos markerPos, RoomScanner.Result result) {
        Contents contents = new Contents();
        Set<BlockPos> beds = new LinkedHashSet<>();
        Set<BlockPos> doors = new LinkedHashSet<>();
        for (RoomScanner.Cell cell : RoomScanner.sorted(result.boundary())) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.BEDS) && state.hasProperty(AbstractBedBlock.PART)) {
                // count each bed once, by its head half
                beds.add(state.getValue(AbstractBedBlock.PART) == BedPart.HEAD
                        ? pos : pos.relative(state.getValue(AbstractBedBlock.FACING)));
            } else if (state.is(BlockTags.DOORS) && state.hasProperty(DoorBlock.HALF)) {
                // count each door once, by its lower half
                doors.add(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below());
            } else {
                if (state.is(STORAGE_CONTAINERS)) {
                    contents.containers.add(pos);
                }
                if (state.is(WORKSTATIONS)) { // a barrel is both: storage and the fisherman's workstation (M13a)
                    contents.workstations.add(pos);
                }
            }
        }
        for (RoomScanner.Cell cell : result.interior()) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!pos.equals(markerPos) && level.getBlockState(pos).is(ModBlocks.BUILDING_MARKER.get())) {
                contents.otherMarker = true;
                break;
            }
        }
        contents.beds.addAll(beds);
        contents.doors.addAll(doors);
        return contents;
    }

    private static void tell(Player player, Component message, boolean success) {
        if (player != null) {
            player.sendSystemMessage(message.copy().withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }
}
