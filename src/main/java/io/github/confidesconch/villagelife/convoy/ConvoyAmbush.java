package io.github.confidesconch.villagelife.convoy;

import java.util.Optional;
import java.util.UUID;

import io.github.confidesconch.villagelife.convoy.ConvoyWagons.Wagon;
import io.github.confidesconch.villagelife.defense.DefenseRules;
import io.github.confidesconch.villagelife.defense.RaidService;
import io.github.confidesconch.villagelife.market.contract.ContractService;
import io.github.confidesconch.villagelife.market.contract.ContractService.Found;
import io.github.confidesconch.villagelife.market.contract.ContractStore;
import io.github.confidesconch.villagelife.market.contract.Shipment;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.VillageService;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.phys.Vec3;

/**
 * Bandits attack a trade wagon someone can see (M20c): they come from ahead on the road and go for the
 * escorts and the donkey. All bandits beaten (by the escorts or a player): nothing is lost. Donkey killed,
 * all escorts down or two minutes over: the rest run off with part of the goods.
 */
final class ConvoyAmbush {
    private ConvoyAmbush() {}

    private static final int AMBUSH_AHEAD = 14;
    private static final long AMBUSH_TICKS = 2400;

    static void removeBandits(Wagon wagon) {
        for (UUID id : wagon.bandits) {
            Entity bandit = wagon.level.getEntity(id);
            if (bandit != null && bandit.isAlive()) {
                wagon.level.sendParticles(ParticleTypes.POOF, bandit.getX(), bandit.getY() + 1.0, bandit.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                bandit.discard(); // off with the loot
            }
        }
        wagon.bandits.clear();
        wagon.ambushUntil = 0;
    }

    static boolean start(MinecraftServer server, Wagon wagon) {
        if (!(wagon.level.getEntity(wagon.donkey) instanceof Donkey donkey)) {
            return false;
        }
        Shipment shipment = wagon.shipment;
        Optional<Found> from = ContractService.find(server, shipment.from());
        double power = from.map(f -> RaidService.defenderPower(f.village())).orElse(0.0);
        int banditLevel = DefenseRules.banditLevel(power);
        int count = ConvoyRules.bandits(shipment.escorts());
        int crossbows = DefenseRules.crossbowBandits(count, banditLevel);
        Vec3 ahead = ConvoyWagons.road(server, shipment).map(r -> r.to().subtract(r.from()).normalize()).orElse(Vec3.ZERO);
        Vec3 camp = donkey.position().add(ahead.scale(AMBUSH_AHEAD));
        for (int i = 0; i < count; i++) {
            Vec3 spot = camp.add(wagon.level.getRandom().nextInt(5) - 2, 0, wagon.level.getRandom().nextInt(5) - 2);
            BlockPos at = ConvoyWagons.ground(wagon.level, spot);
            Mob bandit = at == null ? null : RaidService.spawnBandit(wagon.level, at, i < crossbows, banditLevel);
            if (bandit != null) {
                bandit.addTag(ConvoyWagons.AMBUSH_TAG);
                bandit.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(bandit, Donkey.class, false,
                        (target, level) -> target.entityTags().contains(ConvoyWagons.WAGON_TAG)));
                wagon.bandits.add(bandit.getUUID());
            }
        }
        if (wagon.bandits.isEmpty()) {
            return false;
        }
        wagon.ambushUntil = wagon.level.getGameTime() + AMBUSH_TICKS;
        donkey.getNavigation().stop();
        Component message = Component.translatable("villagelife.convoy.ambush",
                from.map(f -> f.village().displayName()).orElse(Component.literal("?"))).withStyle(ChatFormatting.GOLD);
        from.ifPresent(f -> VillageService.tellManagers(server, f.village(), NotificationTopic.TRADE, message));
        for (ServerPlayer player : wagon.level.players()) {
            if (player.distanceToSqr(donkey) < ConvoyWagons.SHOW_RANGE * ConvoyWagons.SHOW_RANGE) {
                player.sendSystemMessage(message);
            }
        }
        return true;
    }

    static void update(MinecraftServer server, Wagon wagon, long now) {
        wagon.bandits.removeIf(id -> !(wagon.level.getEntity(id) instanceof Mob bandit) || !bandit.isAlive());
        if (wagon.bandits.isEmpty()) {
            finish(server, wagon, true); // beaten off – by the escorts or a player
            return;
        }
        boolean escortsDown = !wagon.escorts.isEmpty() && wagon.escorts.stream()
                .noneMatch(id -> wagon.level.getEntity(id) instanceof ResidentEntity escort && !escort.isKnockedOut());
        if (escortsDown || now >= wagon.ambushUntil) {
            finish(server, wagon, false);
        }
    }

    static void finish(MinecraftServer server, Wagon wagon, boolean defended) {
        ContractStore contracts = ContractStore.get(server);
        contracts.shipments().stream().filter(wagon.shipment::sameTrip).filter(Shipment::raidPending).findFirst()
                .ifPresent(current -> {
                    Shipment after = ConvoyService.raided(server, current, defended);
                    contracts.replaceShipment(current, after);
                    wagon.shipment = after;
                });
        removeBandits(wagon);
    }
}
