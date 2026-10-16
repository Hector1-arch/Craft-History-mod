package com.crafthistory.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent, client side storage for the "Craft History" history.
 *
 * <p>Each record stores the stable recipe key produced by {@link com.crafthistory.util.RecipeKeys}
 * together with the produced item id, so entries can still be matched when a recipe's serialized
 * form changes between sessions.</p>
 *
 * <p>Data is written to {@code config/crafthistory.json}.</p>
 */
public final class CraftHistoryData {
	public static final int MAX_RECENT = 3;

	private static final Logger LOGGER = LoggerFactory.getLogger("crafthistory");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static CraftHistoryData instance;

	/** Recently crafted recipes, newest first (at most {@link #MAX_RECENT}). */
	public List<Entry> recent = new ArrayList<>();

	private transient Path file;

	private CraftHistoryData() {
	}

	public static synchronized CraftHistoryData get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static CraftHistoryData load() {
		CraftHistoryData data = new CraftHistoryData();
		data.file = FabricLoader.getInstance().getConfigDir().resolve("crafthistory.json");
		if (Files.exists(data.file)) {
			try (Reader reader = Files.newBufferedReader(data.file, StandardCharsets.UTF_8)) {
				CraftHistoryData read = GSON.fromJson(reader, CraftHistoryData.class);
				if (read != null && read.recent != null) {
					data.recent = read.recent;
				}
			} catch (Exception e) {
				LOGGER.warn("[Craft History] Could not read crafthistory.json, starting fresh.", e);
			}
		}
		data.recent.removeIf(e -> e == null || e.item == null);
		while (data.recent.size() > MAX_RECENT) {
			data.recent.remove(data.recent.size() - 1);
		}
		return data;
	}

	/**
	 * Records a freshly crafted item. Duplicates are removed (matched by produced item, as the
	 * history tracks item types) before the new entry is pushed to the front.
	 */
	public synchronized void recordCrafted(final String key, final String item) {
		if (item == null) {
			return;
		}
		this.recent.removeIf(entry -> item.equals(entry.item) || (key != null && key.equals(entry.key)));
		this.recent.add(0, new Entry(key, item));
		while (this.recent.size() > MAX_RECENT) {
			this.recent.remove(this.recent.size() - 1);
		}
		this.save();
	}

	public synchronized List<Entry> getRecent() {
		return new ArrayList<>(this.recent);
	}

	private void save() {
		if (this.file == null) {
			return;
		}
		try {
			Files.createDirectories(this.file.getParent());
			try (Writer writer = Files.newBufferedWriter(this.file, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("[Craft History] Could not save crafthistory.json.", e);
		}
	}

	/** A single persisted recipe reference. */
	public static final class Entry {
		/** Stable content hash of the recipe display (may be {@code null} for unsupported recipes). */
		public String key;
		/** The id of the item the recipe produces, e.g. {@code oak_planks}. */
		public String item;

		public Entry() {
		}

		public Entry(final String key, final String item) {
			this.key = key;
			this.item = item;
		}
	}
}
