package io.github.jcondedata.aliveworkplace.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

/**
 * Copperline's toast (ROADMAP 48.3, the VillagerCard board): the toast nine-slice with its Lantern edge, a 16-pixel section
 * icon, the title in Quartz and a line in Tuff ({@link CopperStyle#toast}), five seconds on screen like vanilla's.
 */
public class CopperToast implements Toast {
	public static final int WIDTH = 160;
	private static final long SHOWN = 5000L;

	private final String icon;
	private final Component title;
	private final Component line;

	public CopperToast(String icon, Component title, Component line) {
		this.icon = icon;
		this.title = title;
		this.line = line;
	}

	public Component title() {
		return title;
	}

	public Component line() {
		return line;
	}

	@Override
	public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
		CopperStyle.toast(g, toasts.getMinecraft().font, icon, title, line, 0, 0, width());
		return time >= SHOWN * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
	}

	@Override
	public int width() {
		return WIDTH;
	}

	@Override
	public int height() {
		return 30;
	}
}
