package io.github.confidesconch.villagelife.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

class LenientCodecsTest {

    private final Codec<List<Integer>> codec = LenientCodecs.list(Codec.INT, "number");

    @Test
    void skipsBrokenEntriesInsteadOfFailing() {
        var json = JsonParser.parseString("[1, \"broken\", 3]");
        List<Integer> decoded = codec.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.of(1, 3), decoded);
    }

    @Test
    void roundTripKeepsAllEntries() {
        var encoded = codec.encodeStart(JsonOps.INSTANCE, List.of(4, 5, 6)).getOrThrow();
        assertEquals(List.of(4, 5, 6), codec.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
}
