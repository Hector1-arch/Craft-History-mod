package com.crafthistory;

import com.crafthistory.data.CraftHistoryData;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only entrypoint for "Craft History".
 *
 * <p>Adds a "Craft History" section above the vanilla recipe book. Nothing here touches the server;
 * recipe placement is still handled by the vanilla recipe book mechanism.</p>
 */
public class CraftHistoryMod implements ClientModInitializer {
	public static final String MOD_ID = "crafthistory";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		// Load persisted history up front so it is ready when a container opens.
		CraftHistoryData.get();
		LOGGER.info("[Craft History] Initialised.");
	}
}
