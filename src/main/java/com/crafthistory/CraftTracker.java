package com.crafthistory;

import com.crafthistory.data.CraftHistoryData;
import com.crafthistory.util.RecipeLookup;
import com.crafthistory.util.RecipeLookup.Resolved;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

/**
 * Detects a completed craft and records it in the "Craft History" history.
 *
 * <p>A craft is observed client side when the player clicks the result slot of a crafting menu
 * (2x2 inventory grid or 3x3 crafting table). Placing a recipe from the recipe book never triggers
 * this, because placing only fills the grid; the craft happens later, when the result slot is
 * taken.</p>
 */
public final class CraftTracker {
	/** The most recent craft of this session, used to refill the grid when the selection key is pressed. */
	private static volatile String sessionKey;
	private static volatile String sessionItem;

	private CraftTracker() {
	}

	/** The recipe key of the last craft in this session, or {@code null} if nothing was crafted yet. */
	public static String lastCraftedKey() {
		return sessionKey;
	}

	/** The produced item id of the last craft in this session, or {@code null} if nothing was crafted yet. */
	public static String lastCraftedItem() {
		return sessionItem;
	}

	public static void recordCrafted(final ItemStack crafted) {
		if (crafted.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}

		String item = RecipeLookup.itemId(crafted.getItem());
		RecipeDisplayEntry best = null;
		boolean exact = false;
		for (RecipeDisplayEntry entry : RecipeLookup.known(minecraft).values()) {
			Resolved resolved = RecipeLookup.resolve(minecraft, entry);
			if (resolved == null || resolved.stack().isEmpty()) {
				continue;
			}
			if (ItemStack.isSameItemSameComponents(resolved.stack(), crafted)) {
				best = entry;
				exact = true;
				break;
			}
			if (!exact && resolved.stack().getItem() == crafted.getItem()) {
				best = entry;
			}
		}

		String key = best != null ? RecipeLookup.resolve(minecraft, best).key() : null;
		sessionKey = key;
		sessionItem = item;
		CraftHistoryData.get().recordCrafted(key, item);
	}
}
