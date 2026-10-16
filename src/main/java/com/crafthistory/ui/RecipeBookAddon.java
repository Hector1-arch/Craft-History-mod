package com.crafthistory.ui;

import com.crafthistory.CraftTracker;
import com.crafthistory.data.CraftHistoryData;
import com.crafthistory.ui.Theme.Palette;
import com.crafthistory.util.RecipeLookup;
import com.crafthistory.util.RecipeLookup.Resolved;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The visual and input layer of the mod.
 *
 * <p>Draws a compact "Craft History" bar directly above the vanilla recipe book panel, keeping the
 * recipe book itself untouched. Clicking an entry places that recipe through the normal vanilla
 * mechanism; nothing is ever auto-crafted.</p>
 *
 * <p>All drawing uses vanilla colours, the vanilla font and vanilla item rendering so the result
 * blends in with the vanilla interface. The palette itself is taken from the recipe book texture
 * (see {@link Theme}) so it follows the active resource pack.</p>
 */
public final class RecipeBookAddon {
	public static final int PANEL_WIDTH = 147;
	// The vanilla recipe book is 166 tall and vertically centred, so on the smallest GUI scale
	// (a 240 pixel tall screen, e.g. 1280x720 at scale 3) there are 37 pixels free above it.
	public static final int SECTION_HEIGHT = 18;
	public static final int SLOT = 16;
	private static final int SLOT_PITCH = 17;

	private static final int LEFT_BUTTON = 1;

	private static final Component RECENT_LABEL = Component.translatable("crafthistory.section.title");
	private static final Component PLACE_HINT = Component.translatable("crafthistory.tooltip.place");
	private static final Component SHIFT_HINT = Component.translatable("crafthistory.tooltip.shift_place");

	private RecipeBookAddon() {
	}

	// ---------------------------------------------------------------------------------------------
	// Layout
	// ---------------------------------------------------------------------------------------------

	private static int recentY(final int yo) {
		return yo - SECTION_HEIGHT;
	}

	private static int slotX(final int sectionX, final int labelWidth, final int index) {
		return sectionX + 4 + labelWidth + 4 + index * SLOT_PITCH;
	}

	private static int maxSlots(final int sectionX, final int labelWidth) {
		int right = sectionX + PANEL_WIDTH - 4;
		int start = sectionX + 4 + labelWidth + 4;
		return Math.max(1, (right - start) / SLOT_PITCH);
	}

	private static List<Resolved> collectRecent(final List<Resolved> all) {
		List<Resolved> result = new ArrayList<>();
		for (CraftHistoryData.Entry entry : CraftHistoryData.get().getRecent()) {
			Resolved resolved = match(all, entry);
			if (resolved != null) {
				result.add(resolved);
			}
		}
		return result;
	}

	// ---------------------------------------------------------------------------------------------
	// Rendering
	// ---------------------------------------------------------------------------------------------

	public static void render(final GuiGraphicsExtractor graphics, final Minecraft minecraft, final int xo, final int yo, final int mouseX, final int mouseY) {
		if (!hasCraftingMenu(minecraft)) {
			return;
		}
		try {
			int y = recentY(yo);
			if (y < 0) {
				return;
			}
			Palette palette = Theme.get(minecraft);
			List<Resolved> all = RecipeLookup.resolveAll(minecraft);
			renderRecent(graphics, minecraft, xo, y, mouseX, mouseY, all, palette);
		} catch (Throwable ignored) {
			// Never let the addon crash the game for a visual problem.
		}
	}

	private static void renderRecent(final GuiGraphicsExtractor graphics, final Minecraft minecraft, final int x, final int y, final int mouseX, final int mouseY, final List<Resolved> all, final Palette palette) {
		drawPanel(graphics, x, y, palette);
		int labelWidth = minecraft.font.width(RECENT_LABEL);
		graphics.text(minecraft.font, RECENT_LABEL, x + 4, y + (SECTION_HEIGHT - 8) / 2, palette.text());

		List<Resolved> recent = collectRecent(all);
		int visible = Math.min(maxSlots(x, labelWidth), recent.size());
		for (int i = 0; i < visible; i++) {
			Resolved resolved = recent.get(i);
			int sx = slotX(x, labelWidth, i);
			drawSlot(graphics, sx, y + 1, resolved.stack(), mouseX, mouseY, palette);
		}
	}

	private static void drawPanel(final GuiGraphicsExtractor graphics, final int x, final int y, final Palette palette) {
		graphics.fill(x, y, x + PANEL_WIDTH, y + SECTION_HEIGHT, palette.background());
		graphics.outline(x, y, PANEL_WIDTH, SECTION_HEIGHT, palette.border());
	}

	private static void drawSlot(final GuiGraphicsExtractor graphics, final int x, final int y, final ItemStack stack, final int mouseX, final int mouseY, final Palette palette) {
		graphics.fill(x, y, x + SLOT, y + SLOT, palette.slotBackground());
		// The item is a full 16x16, so it fills the slot and is drawn before the outline to keep
		// the (hover) border visible on top of it.
		graphics.fakeItem(stack, x, y);
		graphics.outline(x, y, SLOT, SLOT, isHovered(mouseX, mouseY, x, y, SLOT, SLOT) ? palette.hoverBorder() : palette.slotBorder());
	}

	// ---------------------------------------------------------------------------------------------
	// Input
	// ---------------------------------------------------------------------------------------------

	/** Handles clicks on the addon section. Returns {@code true} when the click was consumed. */
	public static boolean mouseClicked(final MouseButtonEvent event, final Minecraft minecraft, final int xo, final int yo) {
		if (!hasCraftingMenu(minecraft) || event.button() != LEFT_BUTTON) {
			return false;
		}
		try {
			int y = recentY(yo);
			if (y < 0 || !isHovered(event.x(), event.y(), xo, y, PANEL_WIDTH, SECTION_HEIGHT)) {
				return false;
			}
			int labelWidth = minecraft.font.width(RECENT_LABEL);
			List<Resolved> recent = collectRecent(RecipeLookup.resolveAll(minecraft));
			int visible = Math.min(maxSlots(xo, labelWidth), recent.size());
			for (int i = 0; i < visible; i++) {
				int sx = slotX(xo, labelWidth, i);
				if (isHovered(event.x(), event.y(), sx, y + 1, SLOT, SLOT)) {
					placeRecipe(minecraft, recent.get(i), event.hasShiftDown());
					return true;
				}
			}
			// Consume clicks anywhere on the panel so they do not fall through to the recipe book.
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** Adds a tooltip for the addon section and its slots. */
	public static void extractTooltip(final GuiGraphicsExtractor graphics, final Minecraft minecraft, final int xo, final int yo, final int mouseX, final int mouseY) {
		if (!hasCraftingMenu(minecraft)) {
			return;
		}
		try {
			int y = recentY(yo);
			if (y < 0 || !isHovered(mouseX, mouseY, xo, y, PANEL_WIDTH, SECTION_HEIGHT)) {
				return;
			}
			int labelWidth = minecraft.font.width(RECENT_LABEL);
			List<Resolved> recent = collectRecent(RecipeLookup.resolveAll(minecraft));
			int visible = Math.min(maxSlots(xo, labelWidth), recent.size());
			for (int i = 0; i < visible; i++) {
				int sx = slotX(xo, labelWidth, i);
				if (isHovered(mouseX, mouseY, sx, y + 1, SLOT, SLOT)) {
					List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, recent.get(i).stack()));
					lines.add(PLACE_HINT);
					lines.add(SHIFT_HINT);
					graphics.setComponentTooltipForNextFrame(minecraft.font, lines, mouseX, mouseY);
					return;
				}
			}
		} catch (Throwable ignored) {
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Actions
	// ---------------------------------------------------------------------------------------------

	/** Refills the crafting grid with the most recent crafted recipe. Returns {@code true} if one was placed. */
	public static boolean refillLastCrafted(final Minecraft minecraft, final boolean useMaxItems) {
		if (!hasCraftingMenu(minecraft) || minecraft.gameMode == null) {
			return false;
		}
		try {
			String key = CraftTracker.lastCraftedKey();
			String item = CraftTracker.lastCraftedItem();
			if (key == null && item == null) {
				// Nothing crafted in this session: leave the vanilla selection behaviour untouched.
				return false;
			}
			Resolved resolved = match(RecipeLookup.resolveAll(minecraft), new CraftHistoryData.Entry(key, item));
			if (resolved == null) {
				return false;
			}
			AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
			minecraft.gameMode.handlePlaceRecipe(minecraft.player.containerMenu.containerId, resolved.entry().id(), useMaxItems);
			return true;
		} catch (Throwable ignored) {
		}
		return false;
	}

	private static void placeRecipe(final Minecraft minecraft, final Resolved resolved, final boolean useMaxItems) {
		if (minecraft.player == null || minecraft.gameMode == null) {
			return;
		}
		minecraft.gameMode.handlePlaceRecipe(minecraft.player.containerMenu.containerId, resolved.entry().id(), useMaxItems);
	}

	// ---------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------

	private static boolean hasCraftingMenu(final Minecraft minecraft) {
		return minecraft.player != null
			&& minecraft.level != null
			&& minecraft.player.containerMenu instanceof AbstractCraftingMenu;
	}

	private static Resolved match(final List<Resolved> all, final CraftHistoryData.Entry wanted) {
		if (wanted.key != null) {
			for (Resolved resolved : all) {
				if (wanted.key.equals(resolved.key())) {
					return resolved;
				}
			}
		}
		if (wanted.item != null) {
			for (Resolved resolved : all) {
				if (!resolved.stack().isEmpty() && wanted.item.equals(RecipeLookup.itemId(resolved.stack().getItem()))) {
					return resolved;
				}
			}
		}
		return null;
	}

	private static boolean isHovered(final double mouseX, final double mouseY, final int x, final int y, final int width, final int height) {
		return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
	}
}
