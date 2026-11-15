package io.github.confidesconch.villagelife.market;

import java.util.Optional;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.market.treasury.Treasury;
import io.github.confidesconch.villagelife.profession.work.EnchanterGoal;
import io.github.confidesconch.villagelife.profession.work.EnchanterRules;
import io.github.confidesconch.villagelife.resident.Job;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Enchanting for players (M26, owner's choice "right-click, only emeralds"): a player right-clicks the
 * village's enchanter holding an item that can be enchanted. He enchants it at random like an enchanting
 * table, as strong as the bookshelves around his table allow; the player pays
 * {@link EnchanterRules#price emeralds}, which go into the village storage (shown in the treasury).
 * No lapis, no experience.
 */
public final class EnchantingService {
    private EnchantingService() {}

    public static void init() {
        // before the village window (it cancels every click on a resident and would hide this one)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EnchantingService::onInteract);
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof ResidentEntity enchanter) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Job job = enchanter.brain().job();
        Village village = enchanter.brain().village();
        Building workplace = enchanter.brain().workplace();
        ItemStack held = player.getMainHandItem();
        if (job == null || !EnchanterGoal.PROFESSION.equals(job.profession()) || village == null || workplace == null
                || held.isEmpty() || enchanter.isSleeping()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        BlockPos table = EnchanterGoal.tableOf(level, workplace.workstations());
        if (table == null) {
            return;
        }
        if (!held.isEnchantable()) {
            player.sendOverlayMessage(Component.translatable("villagelife.enchanter.cannot", held.getHoverName()));
            return;
        }
        int tier = EnchanterGoal.tierAt(level, table);
        int price = EnchanterRules.price(tier);
        boolean free = player.hasInfiniteMaterials();
        if (!free && ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(Items.EMERALD), 0, true) < price) {
            player.sendOverlayMessage(Component.translatable("villagelife.enchanter.price", price, tier));
            return;
        }
        if (!free && MarketService.room(level, village, new ItemStack(Items.EMERALD)) < price) {
            player.sendOverlayMessage(Component.translatable("villagelife.enchanter.no_room"));
            return;
        }
        ItemStack result = enchant(level, held.copyWithCount(1), tier);
        if (result.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("villagelife.enchanter.cannot", held.getHoverName()));
            return;
        }
        if (!free) {
            ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(Items.EMERALD), price, false);
            MarketService.insert(level, village, new ItemStack(Items.EMERALD, price));
            Treasury.log(level, village, Treasury.ENCHANTING, BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), 1, price);
        }
        held.shrink(1); // one item of a stack (books) – the rest stays in the hand
        if (held.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, result);
        } else {
            player.getInventory().placeItemBackInInventory(result, Prediction.SERVER_ONLY);
        }
        enchanter.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        level.playSound(null, enchanter.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.5, 0.5, 0.5, 0.8);
        player.sendOverlayMessage(Component.translatable("villagelife.enchanter.done", result.getHoverName(), free ? 0 : price));
    }

    /** A random table enchantment at this strength; empty if none fits (should not happen for enchantable items). */
    private static ItemStack enchant(ServerLevel level, ItemStack stack, int tier) {
        Optional<HolderSet.Named<Enchantment>> table = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .get(EnchantmentTags.IN_ENCHANTING_TABLE);
        ItemStack result = EnchantmentHelper.enchantItem(level.getRandom(), stack, EnchanterRules.costLevel(tier),
                level.registryAccess(), table);
        boolean enchanted = result.isEnchanted() || !EnchantmentHelper.getEnchantmentsForCrafting(result).isEmpty();
        return enchanted ? result : ItemStack.EMPTY;
    }
}
