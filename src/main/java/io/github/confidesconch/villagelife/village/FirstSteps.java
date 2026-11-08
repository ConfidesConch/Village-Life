package io.github.confidesconch.villagelife.village;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.core.registry.ModItems;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Help for the first steps (1.0.1, owner's wish): every player gets a village center and five building
 * markers once per world, and a hint when a plain bell is placed outside a village – a normal bell
 * does not found one.
 */
public final class FirstSteps {
    private FirstSteps() {}

    public static final int STARTER_MARKERS = 5;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(FirstSteps::onLogin);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FirstSteps::onPlace); // not when a claim blocked it
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SyncedConfig.STARTER_KIT.get()) {
            return;
        }
        Given given = player.level().getServer().getDataStorage().computeIfAbsent(Given.TYPE);
        if (!given.add(player.getUUID())) {
            return; // already had it in this world
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.VILLAGE_CENTER.get()), Prediction.SERVER_ONLY);
        player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.BUILDING_MARKER.get(), STARTER_MARKERS), Prediction.SERVER_ONLY);
        player.sendSystemMessage(Component.translatable("villagelife.starter_kit.given", STARTER_MARKERS));
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && event.getPlacedBlock().is(Blocks.BELL) && VillageManager.get(level).findAt(event.getPos()).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("villagelife.hint.plain_bell"));
        }
    }

    /** Players who already received the starter kit, once per world ({@code data/villagelife/starter_kits.dat}). */
    private static final class Given extends SavedData {
        private static final Codec<Given> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC_LINKED_SET.optionalFieldOf("players", Set.of()).forGetter(g -> g.players)
        ).apply(instance, Given::new));

        static final SavedDataType<Given> TYPE = new SavedDataType<>(
                Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "starter_kits"), Given::new, CODEC);

        private final Set<UUID> players = new LinkedHashSet<>();

        Given() {}

        private Given(Set<UUID> loaded) {
            players.addAll(loaded);
        }

        /** True if the player was not in the list yet. */
        boolean add(UUID player) {
            if (!players.add(player)) {
                return false;
            }
            setDirty();
            return true;
        }
    }
}
