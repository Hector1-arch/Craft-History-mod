package com.crafthistory.util;

import com.crafthistory.mixin.ClientRecipeBookAccessor;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.util.context.ContextMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client side helper for looking up recipe display entries and resolving their keys / result items.
 *
 * <p>Keys are expensive to compute (they serialize the whole display), so resolved values are
 * memoized per {@link RecipeDisplayId}. A cached value is only reused while the entry object it was
 * computed from is still the one registered in the client recipe book, which keeps the cache
 * correct across recipe re-syncs.</p>
 */
public final class RecipeLookup {
	private static final Map<RecipeDisplayId, Cached> CACHE = new ConcurrentHashMap<>();

	private RecipeLookup() {
	}

	/** All entries currently known to the client recipe book. */
	public static Map<RecipeDisplayId, RecipeDisplayEntry> known(final Minecraft minecraft) {
		if (minecraft.player == null) {
			return Map.of();
		}
		ClientRecipeBook book = minecraft.player.getRecipeBook();
		return ((ClientRecipeBookAccessor) book).crafthistory$getKnown();
	}

	public static RecipeDisplayEntry entryFor(final Minecraft minecraft, final RecipeDisplayId id) {
		if (id == null) {
			return null;
		}
		return known(minecraft).get(id);
	}

	/** Resolves (and caches) the key and result stack of an entry. */
	public static Resolved resolve(final Minecraft minecraft, final RecipeDisplayEntry entry) {
		if (entry == null) {
			return null;
		}
		Cached cached = CACHE.get(entry.id());
		if (cached != null && cached.entry == entry) {
			return cached.resolved;
		}
		Resolved resolved = compute(minecraft, entry);
		if (resolved != null) {
			CACHE.put(entry.id(), new Cached(entry, resolved));
		}
		return resolved;
	}

	public static List<Resolved> resolveAll(final Minecraft minecraft) {
		List<Resolved> result = new ArrayList<>();
		for (RecipeDisplayEntry entry : known(minecraft).values()) {
			Resolved resolved = resolve(minecraft, entry);
			if (resolved != null) {
				result.add(resolved);
			}
		}
		return result;
	}

	private static Resolved compute(final Minecraft minecraft, final RecipeDisplayEntry entry) {
		if (minecraft.level == null) {
			return null;
		}
		ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);
		ItemStack stack = entry.display().result().resolveForFirstStack(context);
		String key = RecipeKeys.of(entry.display(), minecraft.level.registryAccess());
		return new Resolved(entry, key, stack);
	}

	public static String itemId(final Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** A recipe entry together with its computed key and primary result stack. */
	public record Resolved(RecipeDisplayEntry entry, String key, ItemStack stack) {
	}

	private record Cached(RecipeDisplayEntry entry, Resolved resolved) {
	}
}
