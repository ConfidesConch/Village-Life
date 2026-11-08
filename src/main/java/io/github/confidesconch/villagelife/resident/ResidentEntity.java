package io.github.confidesconch.villagelife.resident;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.core.util.ModProfiler;
import io.github.confidesconch.villagelife.resident.ai.OpenFenceGateGoal;
import io.github.confidesconch.villagelife.resident.ai.ResidentNavigation;
import io.github.confidesconch.villagelife.resident.needs.Needs;
import io.github.confidesconch.villagelife.resident.schedule.Activity;
import io.github.confidesconch.villagelife.resident.ai.DeliverArrivalPackGoal;
import io.github.confidesconch.villagelife.resident.ai.EatGoal;
import io.github.confidesconch.villagelife.resident.ai.ResidentBrain;
import io.github.confidesconch.villagelife.resident.ai.ResidentHooks;
import io.github.confidesconch.villagelife.resident.ai.SleepInBedGoal;
import io.github.confidesconch.villagelife.resident.ai.StayNearGoal;
import io.github.confidesconch.villagelife.resident.ai.WanderInVillageGoal;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The "body" of a village resident: looks like a player (default Minecraft skins), shows its name,
 * never despawns. The resident's file lives in its village ({@link ResidentRecord}).
 * Follows its daily schedule (M7), sleeps in its bed (M6), opens doors, avoids water and runs away
 * from monsters; what to do is decided by {@link ResidentBrain}. Workers carry what they produced
 * in their own {@link #inventory()} (saved with the entity) until they deliver it (M8/M9a).
 */
public class ResidentEntity extends PathfinderMob {

    /**
     * Path range (in blocks) before the resident knows its village. Minecraft's default of 16 is too
     * short for a village; once the village is known, {@link #fitPathRangeTo} sizes it to the village.
     */
    public static final double PATH_RANGE = 48.0;
    /** Upper limit for the path range: longer paths cost noticeably more server time. */
    public static final double MAX_PATH_RANGE = 128.0;

    /** At bedtime, flee only from monsters closer than 4 blocks. */
    private static final double BEDTIME_FLEE_DISTANCE_SQR = 16.0;
    private static final int HOMELESS_NIGHT_RADIUS = 10;
    /** Defenders heal 1 health point this often (5 s) – until healers exist (later professions). */
    private static final int DEFENDER_HEAL_TICKS = 100;
    private static final long MAX_KNOCKOUT_TICKS = 3000;
    private static final float DEFENDER_DAMAGE_TAKEN = 0.5F;

    /** The look number (M21c: any int; {@link ResidentLook} turns it into skin tone, hair, clothes …). */
    private static final EntityDataAccessor<Integer> SKIN =
            SynchedEntityData.defineId(ResidentEntity.class, EntityDataSerializers.INT);
    /** M21c: woman (slim arms, women's clothes) – follows from the first name. */
    private static final EntityDataAccessor<Boolean> FEMALE =
            SynchedEntityData.defineId(ResidentEntity.class, EntityDataSerializers.BOOLEAN);
    /** M21c: the profession id, so the client can draw the work clothes ("" = none). */
    private static final EntityDataAccessor<String> PROFESSION =
            SynchedEntityData.defineId(ResidentEntity.class, EntityDataSerializers.STRING);

    private UUID villageId;
    private String firstName = "";
    private String lastName = "";
    private final ResidentBrain brain = new ResidentBrain();
    /** Size of the resident's own inventory: two rows like a small chest. */
    public static final int INVENTORY_SIZE = 18;
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private final Needs needs = new Needs();
    /** Mood change from relationships (M17), set by the social system; not saved, recalculated every few seconds. */
    private int socialMood;
    /** Provisions a newcomer brings along and puts into the village storage (wish of the owner, 04.11.2026). */
    private final List<ItemStack> arrivalPack = new ArrayList<>();
    /** Until when (game time) a defender is knocked out and does not fight (M20a); saved. */
    private long knockedOutUntil;

    public ResidentEntity(EntityType<? extends ResidentEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        // walk through (and open) wooden doors; swim instead of sinking
        getNavigation().getNodeEvaluator().setCanOpenDoors(true);
        getNavigation().getNodeEvaluator().setCanPassDoors(true);
        getNavigation().getNodeEvaluator().setCanFloat(true);
        // the tool in hand is only for show and must not drop
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            setDropChance(slot, 1.0F); // M20a: a guard's armor comes from the village storage and drops completely
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, PATH_RANGE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // monsters do not attack residents (yet); at bedtime only flee from very close ones,
        // so that a zombie outside the window does not keep everyone awake
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Monster.class,
                monster -> (!ResidentHooks.isDefender(this) || isKnockedOut()) // M20a: defenders stand and fight
                        && (brain.activity() != Activity.SLEEP || distanceToSqr(monster) < BEDTIME_FLEE_DISTANCE_SQR),
                8.0F, 0.6, 0.8, EntitySelector.NO_CREATIVE_OR_SPECTATOR));
        goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        goalSelector.addGoal(2, new OpenFenceGateGoal(this)); // M13c: through pasture gates
        goalSelector.addGoal(2, new EatGoal(this, 0.6));
        goalSelector.addGoal(3, new DeliverArrivalPackGoal(this, 0.6)); // newcomers' provisions first
        goalSelector.addGoal(3, new SleepInBedGoal(this, 0.6));
        goalSelector.addGoal(4, new StayNearGoal(this, 0.6, Activity.HOME, ResidentBrain::bedPos, 5));
        goalSelector.addGoal(4, new StayNearGoal(this, 0.6, Activity.MEAL, ResidentBrain::villageCenter, 6));
        // homeless residents spend the night at the village center (wide circle: many may be homeless)
        goalSelector.addGoal(4, new StayNearGoal(this, 0.6, Activity.SLEEP,
                b -> b.bedPos() == null ? b.villageCenter() : null, HOMELESS_NIGHT_RADIUS));
        goalSelector.addGoal(5, new WanderInVillageGoal(this, 0.6));
        ResidentHooks.applyGoals(this);
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, 0);
        builder.define(FEMALE, false);
        builder.define(PROFESSION, "");
    }

    /** Sets up a newly created resident. */
    public void initialize(UUID villageId, NameGenerator.Name name, int skin) {
        this.villageId = villageId;
        this.firstName = name.first();
        this.lastName = name.last();
        entityData.set(SKIN, skin);
        entityData.set(FEMALE, NameGenerator.isFemale(name.first()));
        setCustomName(Component.literal(name.full()));
        setCustomNameVisible(true);
    }

    public ResidentBrain brain() {
        return brain;
    }

    /**
     * Lets the resident plan paths across its whole village: from one edge to the other plus a
     * little margin, at most {@link #MAX_PATH_RANGE}. Only changes the value when needed.
     */
    public void fitPathRangeTo(int villageRadius) {
        double wanted = Math.max(PATH_RANGE, Math.min(MAX_PATH_RANGE, villageRadius * 2.0 + 8));
        AttributeInstance range = Objects.requireNonNull(getAttribute(Attributes.FOLLOW_RANGE));
        if (range.getBaseValue() != wanted) {
            range.setBaseValue(wanted);
        }
    }

    /** The provisions the resident still has to bring to the storage; changed in place. */
    public List<ItemStack> arrivalPack() {
        return arrivalPack;
    }

    /** Hunger and rest (M10). */
    public Needs needs() {
        return needs;
    }

    /** Satisfaction 0–100 right now (needs a home = bed for full marks), plus the mood from relationships (M17) and laws (M19b). */
    public int satisfaction() {
        int base = needs.satisfaction(level().getOverworldClockTime(), level().getGameTime(), brain.bedPos() != null);
        return Math.max(0, Math.min(100, base + socialMood + brain.lawMood())); // M19b: laws
    }

    /** Satisfaction from needs only, without relationships (decides whether a resident is grumpy). */
    public int needsSatisfaction() {
        return needs.satisfaction(level().getOverworldClockTime(), level().getGameTime(), brain.bedPos() != null);
    }

    /** Children are half as tall (M18b); the scale attribute is synced to all players. */
    public void setChildSize(boolean child) {
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        double wanted = child ? 0.5 : 1.0;
        if (scale != null && scale.getBaseValue() != wanted) {
            scale.setBaseValue(wanted);
        }
    }

    public int socialMood() {
        return socialMood;
    }

    /** Set by the social system (M17): friends nearby lift the mood, a recent quarrel lowers it. */
    public void setSocialMood(int value) {
        socialMood = value;
    }

    /** Work speed factor from needs: 0.5 when starving, 0.8–1.1 otherwise. */
    public double workSpeed() {
        return needs.workSpeed(level().getOverworldClockTime(), level().getGameTime(), brain.bedPos() != null)
                * EducationRules.speedFactor(brain.education()); // M18c: school pays off
    }

    /** Ground navigation that measures its path searches (M12c). */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new ResidentNavigation(this, level);
    }

    /** The resident's own inventory (M9a): what it carries, e.g. logs or seeds. Saved with the entity. */
    public SimpleContainer inventory() {
        return inventory;
    }

    /** Copies of all non-empty stacks in the inventory. */
    public List<ItemStack> carriedItems() {
        return CarriedItems.copies(inventory);
    }

    /** Total number of carried items. */
    public int carriedCount() {
        return CarriedItems.count(inventory, stack -> true);
    }

    /** Number of carried items matching the filter. */
    public int carriedCount(Predicate<ItemStack> filter) {
        return CarriedItems.count(inventory, filter);
    }

    /** True if at least part of the stack would fit into the inventory. */
    public boolean canCarry(ItemStack stack) {
        return inventory.canAddItem(stack);
    }

    /** Takes up to {@code max} carried items matching the filter (one item type per call). */
    public ItemStack takeCarried(Predicate<ItemStack> filter, int max) {
        return CarriedItems.take(inventory, filter, max);
    }

    /** Takes one carried item matching the filter; empty stack if there is none. */
    public ItemStack takeCarried(Predicate<ItemStack> filter) {
        return takeCarried(filter, 1);
    }

    /** Empties the inventory and returns everything that was in it. */
    public List<ItemStack> takeAllCarried() {
        List<ItemStack> items = carriedItems();
        inventory.clearContent();
        return items;
    }

    /**
     * Adds items to the inventory. What does not fit is dropped on the ground, so nothing is lost.
     */
    public void carry(ItemStack stack) {
        ItemStack rest = inventory.addItem(stack.copy());
        if (!rest.isEmpty() && level() instanceof ServerLevel level) {
            spawnAtLocation(level, rest);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        long start = System.nanoTime();
        brain.tick(this, level);
        ModProfiler.addDetail(ModProfiler.BRAIN, System.nanoTime() - start, 1); // M21
        if (tickCount % 20 == 0) { // M21c: work clothes follow the job
            String profession = brain.job() == null ? "" : brain.job().profession();
            if (!profession.equals(entityData.get(PROFESSION))) {
                entityData.set(PROFESSION, profession);
            }
        }
        if (knockedOutUntil > level.getGameTime() + MAX_KNOCKOUT_TICKS) {
            knockedOutUntil = level.getGameTime() + MAX_KNOCKOUT_TICKS; // M20b: at most 3 h (also for older saves)
        }
        if (knockedOutUntil != 0 && level.getGameTime() >= knockedOutUntil) { // M20a: back on duty, fully rested
            knockedOutUntil = 0;
            setHealth(getMaxHealth());
        } else if (tickCount % DEFENDER_HEAL_TICKS == 0 && getHealth() < getMaxHealth() && ResidentHooks.isDefender(this)) {
            heal(1.0F); // defenders recover slowly between fights
        }
    }

    /**
     * Residents do not push each other (M12a): in crowds (e.g. at the village center at night) pushing
     * cost a lot of time, kept them walking around, and vanilla "entity cramming" could even hurt them.
     * Players, animals and other mobs are still pushed as usual.
     */
    @Override
    protected void pushEntities() {
        for (Entity entity : level().getPushableEntities(this, getBoundingBox())) {
            if (!(entity instanceof ResidentEntity)) {
                doPush(entity);
            }
        }
    }

    /** Measures the total cost of this resident per tick (movement, goals, thinking) on the server. */
    @Override
    public void aiStep() {
        if (level().isClientSide()) {
            super.aiStep();
            return;
        }
        long start = System.nanoTime();
        super.aiStep();
        ModProfiler.add(ModProfiler.RESIDENTS, System.nanoTime() - start, 1);
    }

    public int getSkin() {
        return entityData.get(SKIN);
    }

    /** M21c: woman or man (from the first name). */
    public boolean isFemale() {
        return entityData.get(FEMALE);
    }

    /** M21c: the profession id for the work clothes, "" without a job (and for children). */
    public String getProfessionId() {
        return entityData.get(PROFESSION);
    }

    public Optional<UUID> getVillageId() {
        return Optional.ofNullable(villageId);
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    /** M21a: residents jump down at most 2 blocks, unless their target lies further below. */
    @Override
    public int getMaxFallDistance() {
        int normal = super.getMaxFallDistance();
        return getNavigation() instanceof ResidentNavigation navigation && !navigation.allowsDeepFall() ? Math.min(2, normal) : normal;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    /**
     * Residents never travel through portals (bug review 08.11.2026): a resident walking into a nether portal
     * near the village would end up in another dimension, where its village does not exist.
     */
    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return false;
    }

    /** M20b2 (owner's wish): defenders take half the damage, whatever armor they wear. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean defender = ResidentHooks.isDefender(this) && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        return super.hurtServer(level, source, defender ? damage * DEFENDER_DAMAGE_TAKEN : damage);
    }

    /** True while a defender is knocked out (M20a): it does not fight and flees like everybody else. */
    public boolean isKnockedOut() {
        return level().getGameTime() < knockedOutUntil;
    }

    /** Game time when a knocked-out defender is back (0 or in the past = awake); for the village window (M21b9). */
    public long knockedOutUntil() {
        return knockedOutUntil;
    }

    /** Knocks the resident out until {@code gameTime} (M20a). */
    public void knockOutUntil(long gameTime) {
        knockedOutUntil = gameTime;
    }

    @Override
    public void die(DamageSource source) {
        // M20a (owner's choice "3a"): defenders are only knocked out; /kill and the void still kill them
        if (level() instanceof ServerLevel && ResidentHooks.isDefender(this)
                && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            setHealth(1.0F);
            ResidentHooks.knockOut(this);
            return;
        }
        super.die(source);
        if (level() instanceof ServerLevel level) {
            takeAllCarried().forEach(stack -> spawnAtLocation(level, stack));
            ResidentService.onDeath(level, this);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("village_id", UUIDUtil.CODEC, villageId);
        output.putString("first_name", firstName);
        output.putString("last_name", lastName);
        output.putInt("skin", getSkin());
        inventory.storeAsItemList(output.list("inventory", ItemStack.CODEC));
        needs.save(output);
        output.putLong("knocked_out_until", knockedOutUntil);
        if (!arrivalPack.isEmpty()) {
            output.store("arrival_pack", ItemStack.CODEC.listOf(), List.copyOf(arrivalPack));
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // residents saved before 0.0.15 stored the old path range of 16; the brain fits it to the village
        Objects.requireNonNull(getAttribute(Attributes.FOLLOW_RANGE)).setBaseValue(PATH_RANGE);
        villageId = input.read("village_id", UUIDUtil.CODEC).orElse(null);
        firstName = input.getStringOr("first_name", "");
        lastName = input.getStringOr("last_name", "");
        entityData.set(SKIN, input.getIntOr("skin", 0));
        entityData.set(FEMALE, NameGenerator.isFemale(firstName)); // M21c: older saves get it from the name
        // tools in hand are only shown while working; the work goal hands them out again
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        needs.load(input);
        knockedOutUntil = input.getLongOr("knocked_out_until", 0L);
        arrivalPack.clear();
        input.read("arrival_pack", ItemStack.CODEC.listOf()).ifPresent(arrivalPack::addAll);
        inventory.clearContent();
        input.list("inventory", ItemStack.CODEC).ifPresent(inventory::fromItemList);
        // items carried before M9a (0.0.10/0.0.11) were stored as a plain list
        input.read("carried", ItemStack.CODEC.listOf()).ifPresent(old -> old.forEach(inventory::addItem));
    }
}
