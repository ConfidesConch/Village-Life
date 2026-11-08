package io.github.confidesconch.villagelife.book;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The open village window as a container (M11h as book, M21b10 window): it carries the window's data and
 * the player's own inventory (armour, main inventory, hotbar, off hand), so items can be moved inside it
 * like in the normal inventory. The server checks every click. The slots are only shown and usable on the
 * "Player" page; slot positions are relative to the corner the screen chooses.
 */
public class VillageBookMenu extends AbstractContainerMenu {
    /**
     * M21b10a: the slots are laid out for the village window's "Player" page – one row of equipment
     * (four armour slots and, at the right end, the off hand) above the inventory and the hotbar.
     * Positions are relative to the corner the screen puts them at.
     */
    public static final int ARMOR_X = 0;
    public static final int ARMOR_Y = 0;
    public static final int ARMOR_STEP = 20;
    public static final int OFFHAND_X = 8 * 18 + 2;
    public static final int OFFHAND_Y = 0;
    public static final int INVENTORY_X = 0;
    public static final int INVENTORY_Y = 26;
    /** Size of the whole slot block. */
    public static final int LAYOUT_WIDTH = 9 * 18;
    public static final int LAYOUT_HEIGHT = INVENTORY_Y + 58 + 18;

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Identifier[] ARMOR_ICONS = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};

    // slot indices in this menu
    private static final int MAIN_START = 4;
    private static final int HOTBAR_START = 31;
    private static final int OFFHAND = 40;
    private static final int END = 41;

    private CompoundTag data;
    private int dataVersion;
    /** Client only: the book shows the slots (character chapter). Always true on the server. */
    private boolean inventoryShown = true;

    public VillageBookMenu(int containerId, Inventory inventory, CompoundTag data) {
        super(BookSystem.VILLAGE_BOOK_MENU.get(), containerId);
        this.data = data;
        Player owner = inventory.player;
        for (int i = 0; i < 4; i++) {
            EquipmentSlot slot = ARMOR[i];
            addSlot(new ArmorSlot(inventory, owner, slot, 39 - i, ARMOR_X + i * ARMOR_STEP, ARMOR_Y, ARMOR_ICONS[i]) {
                @Override
                public boolean isActive() {
                    return inventoryShown;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new BookSlot(inventory, col + (row + 1) * 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new BookSlot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
        addSlot(new BookSlot(inventory, 40, OFFHAND_X, OFFHAND_Y) {
            @Override
            public void setByPlayer(ItemStack stack, ItemStack previous) {
                owner.onEquipItem(EquipmentSlot.OFFHAND, previous, stack);
                super.setByPlayer(stack, previous);
            }

            @Override
            public Identifier getNoItemIcon() {
                return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
            }
        });
    }

    /** Client side: built from the data the server sent when opening the book. */
    static VillageBookMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        CompoundTag data = buf == null ? null : buf.readNbt();
        return new VillageBookMenu(containerId, inventory, data == null ? new CompoundTag() : data);
    }

    /** A normal inventory slot that is hidden when the book does not show the inventory. */
    private class BookSlot extends Slot {
        BookSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isActive() {
            return inventoryShown;
        }
    }

    public CompoundTag data() {
        return data;
    }

    /** Changes with every new snapshot, so the screen can react (e.g. open a focused chapter). */
    public int dataVersion() {
        return dataVersion;
    }

    public void setData(CompoundTag newData) {
        data = newData;
        dataVersion++;
    }

    public void setInventoryShown(boolean shown) {
        inventoryShown = shown;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    /** Shift-click like in the normal inventory: armour/off hand ↔ inventory, main inventory ↔ hotbar. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        EquipmentSlot equipment = player.getEquipmentSlotForItem(original);
        boolean moved;
        if (index < MAIN_START || index == OFFHAND) {
            moved = moveItemStackTo(stack, MAIN_START, OFFHAND, false);
        } else if (equipment.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !slots.get(3 - equipment.getIndex()).hasItem()) {
            int target = 3 - equipment.getIndex();
            moved = moveItemStackTo(stack, target, target + 1, false);
        } else if (equipment == EquipmentSlot.OFFHAND && !slots.get(OFFHAND).hasItem()) {
            moved = moveItemStackTo(stack, OFFHAND, END, false);
        } else if (index < HOTBAR_START) {
            moved = moveItemStackTo(stack, HOTBAR_START, OFFHAND, false);
        } else {
            moved = moveItemStackTo(stack, MAIN_START, HOTBAR_START, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, original);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
