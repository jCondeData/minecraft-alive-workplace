package io.github.jcondedata.aliveworkplace.client.mixin;

import io.github.jcondedata.aliveworkplace.mc.Tooltips;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A menu button's tooltip on a chest screen of buttons (a {@code ChoiceMenu}: the research screen, the lessons...)
 * opens under the button's row, its long lines wrapped, so the row it belongs to stays in sight (B141: the Great
 * Library's Scholarship tooltip, one line wider than the screen, was pushed left over the whole second-tier row).
 * Only buttons ({@link Tooltips#isPicture}); a real item in a real chest keeps the game's own tooltip.
 */
@Mixin(AbstractContainerScreen.class)
abstract class ButtonTooltipMixin {
	@Shadow
	protected Slot hoveredSlot;
	@Shadow
	protected int topPos;

	@Shadow
	protected abstract List<Component> getTooltipFromContainerItem(ItemStack stack);

	@Inject(method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$buttonTooltip(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (!(screen instanceof ContainerScreen) || !screen.getMenu().getCarried().isEmpty() || hoveredSlot == null || !hoveredSlot.hasItem()) {
			return;
		}
		ItemStack stack = hoveredSlot.getItem();
		if (!Tooltips.isPicture(stack)) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (Component line : getTooltipFromContainerItem(stack)) {
			lines.addAll(font.split(line, Tooltips.BUTTON_WIDTH));
		}
		graphics.renderTooltip(font, lines, x, Tooltips.belowSlot(topPos + hoveredSlot.y));
		ci.cancel();
	}
}
