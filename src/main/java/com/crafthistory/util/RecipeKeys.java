package com.crafthistory.util;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Builds a stable, content based identifier for a {@link RecipeDisplay}.
 *
 * <p>Minecraft 26.3 does not send real recipe identifiers to the client (the recipe book only
 * knows transient {@code RecipeDisplayId} indices, which are assigned per session). To be able to
 * persist the craft history across sessions and worlds, we derive a content hash from the
 * serialized recipe display instead. Two recipes that produce the same item but are defined
 * differently (for example different ingredients or a different shape) serialize to different data,
 * so they receive different keys.</p>
 */
public final class RecipeKeys {
	private RecipeKeys() {
	}

	public static String of(final RecipeDisplay display, final RegistryAccess registries) {
		return hash(raw(display, registries));
	}

	private static String raw(final RecipeDisplay display, final RegistryAccess registries) {
		try {
			DynamicOps<JsonElement> ops = registries != null
				? RegistryOps.create(JsonOps.INSTANCE, registries)
				: JsonOps.INSTANCE;
			DataResult<JsonElement> encoded = RecipeDisplay.CODEC.encodeStart(ops, display);
			JsonElement element = encoded.result().orElse(null);
			if (element != null) {
				return element.toString();
			}
		} catch (Throwable ignored) {
			// Fall through to the degraded signature below.
		}

		// Degraded but still deterministic fallback for displays that cannot be serialized.
		return display.getClass().getName()
			+ '|' + display.result().getClass().getName()
			+ '|' + display.result()
			+ '|' + display.craftingStation().getClass().getName()
			+ '|' + display.craftingStation();
	}

	private static String hash(final String raw) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				builder.append(Character.forDigit((b >> 4) & 0xF, 16));
				builder.append(Character.forDigit(b & 0xF, 16));
			}
			return builder.toString();
		} catch (Exception e) {
			return Integer.toHexString(raw.hashCode());
		}
	}
}
