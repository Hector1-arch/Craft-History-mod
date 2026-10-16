package com.crafthistory.mixin;

import com.crafthistory.CraftTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes clicks on crafting result slots. The click still goes through to the server unchanged;
 * this only feeds the "Craft History" history.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "handleContainerInput", at = @At("HEAD"))
	private void crafthistory$detectCraft(
		final int containerId,
		final int slotNum,
		final int buttonNum,
		final ContainerInput containerInput,
		final Player player,
		final CallbackInfo ci
	) {
		try {
			if (!(player.containerMenu instanceof AbstractCraftingMenu menu)) {
				return;
			}
			if (containerId != menu.containerId) {
				return;
			}
			Slot result = menu.getResultSlot();
			if (result == null || result.index != slotNum) {
				return;
			}
			ItemStack stack = result.getItem();
			if (stack.isEmpty()) {
				return;
			}
			CraftTracker.recordCrafted(stack);
		} catch (Throwable ignored) {
			// Never interfere with normal inventory interaction.
		}
	}
}
