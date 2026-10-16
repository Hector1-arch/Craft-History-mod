package com.crafthistory.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Derives the addon's colour palette from the vanilla recipe book texture.
 *
 * <p>The section is drawn directly above the recipe book, so it has to match whatever chrome the
 * active resource pack uses. Instead of hard coding the vanilla grey, the texture's dominant opaque
 * colour is used as the panel background and its darkest common colour as the outline/outline
 * border. Text and slot colours are then derived so they contrast with the background. That way a
 * dark GUI resource pack automatically produces a dark section with a matching outline, and the
 * classic look is reproduced if the pack is absent.</p>
 *
 * <p>The palette is cached and only recomputed when the selected resource packs change.</p>
 */
public final class Theme {
	private static final Identifier RECIPE_BOOK =
		Identifier.withDefaultNamespace("textures/gui/recipe_book.png");

	/** Classic vanilla look, used when the texture cannot be read for any reason. */
	private static final Palette FALLBACK =
		new Palette(0xFFC6C6C6, 0xFF000000, 0xFF404040, 0xFF8B8B8B, 0xFF373737, 0xFFFFFFFF);

	private static volatile Palette cached;
	private static volatile String cachedKey;

	private Theme() {
	}

	/** The palette matching the currently selected resource packs. */
	public static Palette get(final Minecraft minecraft) {
		String key = packKey(minecraft);
		Palette current = cached;
		if (current != null && key.equals(cachedKey)) {
			return current;
		}
		Palette computed = compute(minecraft);
		cachedKey = key;
		cached = computed;
		return computed;
	}

	/** A stable key for the active pack selection, so changes invalidate the cache. */
	private static String packKey(final Minecraft minecraft) {
		try {
			Collection<String> ids = minecraft.getResourcePackRepository().getSelectedIds();
			List<String> sorted = new ArrayList<>(ids);
			Collections.sort(sorted);
			return String.join(",", sorted);
		} catch (Throwable ignored) {
			return "";
		}
	}

	private static Palette compute(final Minecraft minecraft) {
		NativeImage image = null;
		try {
			Optional<Resource> resource = minecraft.getResourceManager().getResource(RECIPE_BOOK);
			if (resource.isEmpty()) {
				return FALLBACK;
			}
			try (InputStream in = resource.get().open()) {
				image = NativeImage.read(in);
			}
			return sample(image);
		} catch (Throwable ignored) {
			return FALLBACK;
		} finally {
			if (image != null) {
				image.close();
			}
		}
	}

	private static Palette sample(final NativeImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		if (width <= 0 || height <= 0) {
			return FALLBACK;
		}

		Map<Integer, Integer> histogram = new HashMap<>();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int raw = image.getPixel(x, y);
				if (((raw >>> 24) & 0xFF) < 128) {
					// Skip transparent pixels (most of the texture is empty around the panel).
					continue;
				}
				histogram.merge(raw | 0xFF000000, 1, Integer::sum);
			}
		}
		if (histogram.isEmpty()) {
			return FALLBACK;
		}

		int background = 0xFFC6C6C6;
		int best = 0;
		for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
			if (entry.getValue() > best) {
				best = entry.getValue();
				background = entry.getKey();
			}
		}

		// The outline is the darkest colour that actually forms part of the texture (a real outline
		// spans many pixels), which is black in vanilla and follows a pack's own outline colour.
		int minCount = Math.max(4, (width + height) / 8);
		int outline = darkestCommon(histogram, minCount);
		return paletteFor(background, outline);
	}

	private static int darkestCommon(final Map<Integer, Integer> histogram, final int minCount) {
		int best = -1;
		double bestLuminance = Double.MAX_VALUE;
		for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
			if (entry.getValue() < minCount) {
				continue;
			}
			double luminance = luminance(entry.getKey());
			if (luminance < bestLuminance) {
				bestLuminance = luminance;
				best = entry.getKey();
			}
		}
		return best == -1 ? 0xFF000000 : best;
	}

	private static Palette paletteFor(final int background, final int outline) {
		int bg = background | 0xFF000000;
		boolean dark = luminance(bg) < 0.5;

		// Keep the sampled outline, but if it is indistinguishable from the background fall back to a
		// contrasting one so the panel is never left without a visible border.
		int border = Math.abs(luminance(outline) - luminance(bg)) < 0.08
			? (dark ? lighten(bg, 0.55) : 0xFF000000)
			: (outline | 0xFF000000);

		int text = dark ? 0xFFE0E0E0 : 0xFF404040;
		int slotBackground = dark ? lighten(bg, 0.12) : darken(bg, 0.30);
		int slotBorder = dark ? lighten(bg, 0.35) : darken(bg, 0.72);
		return new Palette(bg, border, text, slotBackground, slotBorder, 0xFFFFFFFF);
	}

	private static double luminance(final int argb) {
		int r = channel(argb, 16);
		int g = channel(argb, 8);
		int b = channel(argb, 0);
		return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
	}

	private static int lighten(final int argb, final double amount) {
		int r = channel(argb, 16);
		int g = channel(argb, 8);
		int b = channel(argb, 0);
		r = (int) Math.round(r + (255 - r) * amount);
		g = (int) Math.round(g + (255 - g) * amount);
		b = (int) Math.round(b + (255 - b) * amount);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static int darken(final int argb, final double amount) {
		int r = (int) Math.round(channel(argb, 16) * (1.0 - amount));
		int g = (int) Math.round(channel(argb, 8) * (1.0 - amount));
		int b = (int) Math.round(channel(argb, 0) * (1.0 - amount));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static int channel(final int argb, final int shift) {
		return (argb >> shift) & 0xFF;
	}

	/** The colours used to draw the addon sections. */
	public record Palette(int background, int border, int text, int slotBackground, int slotBorder, int hoverBorder) {
	}
}
