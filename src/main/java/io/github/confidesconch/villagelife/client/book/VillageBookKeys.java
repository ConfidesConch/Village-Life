package io.github.confidesconch.villagelife.client.book;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.confidesconch.villagelife.VillageLife;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Key bindings of Village Life (shown in Options → Controls under "Village Life"). */
public final class VillageBookKeys {
    private VillageBookKeys() {}

    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "main"));

    /** Opens the village book; default B. */
    public static final KeyMapping OPEN_BOOK =
            new KeyMapping("key.villagelife.open_book", InputConstants.KEY_B, CATEGORY);

    /** Shows or hides the minimap; default N. */
    public static final KeyMapping TOGGLE_MINIMAP =
            new KeyMapping("key.villagelife.toggle_minimap", InputConstants.KEY_N, CATEGORY);
}
