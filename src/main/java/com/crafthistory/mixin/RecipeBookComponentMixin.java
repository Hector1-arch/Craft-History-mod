package com.crafthistory.mixin;

import com.crafthistory.ui.RecipeBookAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Draws the "Craft History" section above the vanilla recipe book. */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
	@Shadow
	private int width;
	@Shadow
	private int height;
	@Shadow
	private int xOffset;
	@Shadow
	protected Minecraft minecraft;

	private int crafthistory$originX() {
		return (this.width - 147) / 2 - this.xOffset;
	}

	private int crafthistory$originY() {
		return (this.height - 166) / 2;
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void crafthistory$renderAddon(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a, final CallbackInfo ci) {
		RecipeBookComponent self = (RecipeBookComponent) (Object) this;
		if (this.minecraft == null || this.minecraft.player == null || !self.isVisible()) {
			return;
		}
		RecipeBookAddon.render(graphics, this.minecraft, this.crafthistory$originX(), this.crafthistory$originY(), mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void crafthistory$mouseClicked(final MouseButtonEvent event, final boolean doubleClick, final CallbackInfoReturnable<Boolean> cir) {
		RecipeBookComponent self = (RecipeBookComponent) (Object) this;
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		if (!self.isVisible() || this.minecraft.player.isSpectator()) {
			return;
		}
		if (RecipeBookAddon.mouseClicked(event, this.minecraft, this.crafthistory$originX(), this.crafthistory$originY())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "extractTooltip", at = @At("TAIL"))
	private void crafthistory$extractTooltip(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final Slot hoveredSlot, final CallbackInfo ci) {
		RecipeBookComponent self = (RecipeBookComponent) (Object) this;
		if (this.minecraft == null || this.minecraft.player == null || !self.isVisible()) {
			return;
		}
		RecipeBookAddon.extractTooltip(graphics, this.minecraft, this.crafthistory$originX(), this.crafthistory$originY(), mouseX, mouseY);
	}

	/**
	 * Pressing the selection key (Space/Enter, matching vanilla) refills the crafting grid with the
	 * most recently crafted recipe, so a craft can be repeated without touching the recipe book.
	 * This works even while the recipe book is hidden, which is the common case.
	 */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void crafthistory$keyPressed(final KeyEvent event, final CallbackInfoReturnable<Boolean> cir) {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		RecipeBookComponent self = (RecipeBookComponent) (Object) this;
		if (!event.isSelection() || self.capturesInput() || this.minecraft.player.isSpectator()) {
			return;
		}
		if (RecipeBookAddon.refillLastCrafted(this.minecraft, event.hasShiftDown())) {
			cir.setReturnValue(true);
		}
	}
}
