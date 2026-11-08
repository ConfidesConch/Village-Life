package io.github.confidesconch.villagelife.profession.work;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Item names as written in profession files (M14): {@code minecraft:wheat} is that item,
 * {@code #minecraft:planks} every item in the tag. Matchers are cached.
 */
final class ItemSpec {
    private ItemSpec() {}

    private static final Map<String, Predicate<ItemStack>> MATCHERS = new ConcurrentHashMap<>();

    static Predicate<ItemStack> matcher(String spec) {
        return MATCHERS.computeIfAbsent(spec, key -> {
            Identifier id = Identifier.tryParse(key.startsWith("#") ? key.substring(1) : key);
            if (id == null) {
                return stack -> false;
            }
            if (key.startsWith("#")) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
                return stack -> stack.is(tag);
            }
            return stack -> !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
        });
    }

    /** The item of a plain item name (no tag), or air. */
    static Item item(String spec) {
        Identifier id = Identifier.tryParse(spec);
        return id == null || spec.startsWith("#") ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
    }
}
