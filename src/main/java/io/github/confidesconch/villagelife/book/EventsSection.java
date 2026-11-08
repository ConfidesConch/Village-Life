package io.github.confidesconch.villagelife.book;

import io.github.confidesconch.villagelife.village.EventLog;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;

/**
 * Data of the event log in the window's overview (M21b3): the village's last events, newest first. The
 * message travels as a component, so every player reads it in his own language.
 */
final class EventsSection {
    private EventsSection() {}

    static void write(ServerPlayer player, Village village, CompoundTag out) {
        var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        ListTag list = new ListTag();
        for (EventLog.Entry entry : EventLog.get(player.level().getServer()).of(village.id())) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("id", entry.id()); // M21b6b: for deleting one entry
            tag.putLong("day", entry.day());
            tag.putString("topic", entry.topic());
            ComponentSerialization.CODEC.encodeStart(ops, entry.text()).result().ifPresent(text -> tag.put("text", text));
            list.add(tag);
        }
        out.put("list", list);
    }
}
