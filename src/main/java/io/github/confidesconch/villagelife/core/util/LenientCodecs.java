package io.github.confidesconch.villagelife.core.util;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

/**
 * Codecs that tolerate damaged save data: a broken list entry is skipped and logged instead of
 * making the whole file unreadable (see CLAUDE.md, quality rules).
 */
public final class LenientCodecs {
    private LenientCodecs() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("villagelife");

    public static <A> Codec<List<A>> list(Codec<A> element, String what) {
        Codec<List<A>> strict = element.listOf();
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<List<A>, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getStream(input).map(stream -> {
                    List<A> result = new ArrayList<>();
                    stream.forEach(entry -> element.parse(ops, entry)
                            .resultOrPartial(error -> LOGGER.warn("Skipping invalid {} in save data: {}", what, error))
                            .ifPresent(result::add));
                    return Pair.of(result, ops.empty());
                });
            }

            @Override
            public <T> DataResult<T> encode(List<A> input, DynamicOps<T> ops, T prefix) {
                return strict.encode(input, ops, prefix);
            }

            @Override
            public String toString() {
                return "LenientList[" + element + "]";
            }
        };
    }
}
