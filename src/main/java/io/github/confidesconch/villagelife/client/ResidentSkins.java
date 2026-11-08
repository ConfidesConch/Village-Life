package io.github.confidesconch.villagelife.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.resident.ResidentLook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * Paints resident skins from building blocks (M21c): grey layers (skin, hair, clothes, shoes) tinted with
 * the colours of the resident's {@link ResidentLook}, coloured faces, and the clothes of the profession on
 * top. Every look is painted once and kept as a texture (at most {@value #MAX_TEXTURES}, the oldest is
 * released). The layers are PNGs in {@code textures/entity/resident/} – each can be redrawn without code.
 */
public final class ResidentSkins {
    private ResidentSkins() {}

    private static final String DIR = "textures/entity/resident/";
    private static final int MAX_TEXTURES = 400;
    private static final Identifier FALLBACK = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

    /** Painted skins by look key, oldest first. */
    private static final LinkedHashMap<String, Identifier> SKINS = new LinkedHashMap<>(64, 0.75F, true);
    /** Loaded building blocks (null = missing file). */
    private static final Map<String, NativeImage> LAYERS = new HashMap<>();

    /** The texture for a resident; painted on first use. */
    public static Identifier texture(int seed, boolean female, String profession) {
        ResidentLook look = ResidentLook.of(seed, female);
        String job = professionPath(profession);
        String key = look.key() + (job.isEmpty() ? "" : "_" + job);
        Identifier id = SKINS.get(key);
        if (id != null) {
            return id;
        }
        NativeImage image = paint(look, job);
        if (image == null) {
            return FALLBACK;
        }
        id = Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "resident_skin/" + key.toLowerCase(java.util.Locale.ROOT));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "villagelife resident skin", image));
        SKINS.put(key, id);
        if (SKINS.size() > MAX_TEXTURES) {
            var oldest = SKINS.entrySet().iterator();
            Identifier released = oldest.next().getValue();
            oldest.remove();
            Minecraft.getInstance().getTextureManager().release(released);
        }
        return id;
    }

    /** "villagelife:farmer" → "farmer"; professions of other mods/data packs without clothes → "". */
    private static String professionPath(String profession) {
        if (profession.isEmpty()) {
            return "";
        }
        Identifier id = Identifier.tryParse(profession);
        return id == null || !id.getNamespace().equals(VillageLife.MOD_ID) ? "" : id.getPath();
    }

    private static NativeImage paint(ResidentLook look, String job) {
        String g = look.gender();
        NativeImage base = layer(look.female() ? "base_slim" : "base_wide");
        if (base == null) {
            return null;
        }
        NativeImage out = new NativeImage(64, 64, true);
        draw(out, base, ResidentLook.SKIN_TONES[look.skinTone()]);
        draw(out, layer("face_" + g + look.face()), -1);
        draw(out, layer("shoes"), ResidentLook.SHOE_COLORS[look.shoeColor()]);
        draw(out, layer("bottom_" + g + look.bottom()), ResidentLook.PANTS_COLORS[look.bottomColor()]);
        draw(out, layer("top_" + g + look.top()), ResidentLook.CLOTH_COLORS[look.topColor()]);
        draw(out, layer("hair_" + g + look.hair()), ResidentLook.HAIR_COLORS[look.hairColor()]);
        if (!job.isEmpty()) {
            draw(out, layer("prof/" + job + "_" + g), -1);
        }
        return out;
    }

    /** Lays {@code layer} over {@code out}; with a tint (RGB) the layer's grey is multiplied with it, -1 = as drawn. */
    private static void draw(NativeImage out, NativeImage layer, int tint) {
        if (layer == null) {
            return;
        }
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int src = layer.getPixel(x, y);
                int alpha = src >>> 24;
                if (alpha == 0) {
                    continue;
                }
                int r = (src >> 16) & 0xFF;
                int g = (src >> 8) & 0xFF;
                int b = src & 0xFF;
                if (tint != -1) { // grey layer: multiply
                    r = r * ((tint >> 16) & 0xFF) / 255;
                    g = g * ((tint >> 8) & 0xFF) / 255;
                    b = b * (tint & 0xFF) / 255;
                }
                if (alpha < 255) {
                    int dst = out.getPixel(x, y);
                    int da = dst >>> 24;
                    r = (r * alpha + ((dst >> 16) & 0xFF) * (255 - alpha)) / 255;
                    g = (g * alpha + ((dst >> 8) & 0xFF) * (255 - alpha)) / 255;
                    b = (b * alpha + (dst & 0xFF) * (255 - alpha)) / 255;
                    alpha = Math.max(alpha, da);
                }
                out.setPixel(x, y, alpha << 24 | r << 16 | g << 8 | b);
            }
        }
    }

    private static NativeImage layer(String name) {
        if (LAYERS.containsKey(name)) {
            return LAYERS.get(name);
        }
        NativeImage image = null;
        Identifier id = Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, DIR + name + ".png");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isPresent()) {
            try (InputStream in = resource.get().open()) {
                image = NativeImage.read(in);
            } catch (IOException e) {
                VillageLife.LOGGER.warn("Could not read resident skin layer {}", id, e);
            }
        }
        LAYERS.put(name, image);
        return image;
    }

    /** After a resource reload (F3+T, resource packs): paint everything again from the new layers. */
    public static void clear() {
        LAYERS.values().forEach(image -> {
            if (image != null) {
                image.close();
            }
        });
        LAYERS.clear();
        SKINS.values().forEach(id -> Minecraft.getInstance().getTextureManager().release(id));
        SKINS.clear();
    }
}
