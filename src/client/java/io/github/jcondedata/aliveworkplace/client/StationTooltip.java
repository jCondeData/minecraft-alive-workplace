package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;

/**
 * A workstation's jobs on its tooltip (ROADMAP 21.1a): hold Shift over a composter, a grindstone, a Shop Counter... to
 * see which job a villager takes by themselves there and which item picks each of the others.
 */
public final class StationTooltip {
	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (!(stack.getItem() instanceof BlockItem item)) {
				return;
			}
			Stations.at(item.getBlock()).ifPresent(station -> {
				List<Stations.Job> jobs = station.jobs().stream().filter(Stations::available).toList();
				if (jobs.isEmpty()) {
					return;
				}
				if (!Screen.hasShiftDown()) {
					lines.add(Component.translatable("tooltip.aliveworkplace.station.hint").withStyle(ChatFormatting.DARK_GRAY));
					return;
				}
				if (station.byItself() && jobs.get(0) == station.jobs().get(0)) {
					lines.add(Component.translatable("tooltip.aliveworkplace.station.by_itself", Stations.name(jobs.get(0).profession().get()))
						.withStyle(ChatFormatting.GRAY));
				}
				lines.add(Component.translatable("tooltip.aliveworkplace.station.header").withStyle(ChatFormatting.GOLD));
				for (Stations.Job job : jobs) {
					lines.add(Component.translatable("tooltip.aliveworkplace.station.job", Component.translatable(Stations.itemKey(job)),
						Stations.name(job.profession().get())).withStyle(ChatFormatting.GRAY));
				}
			});
		});
	}

	private StationTooltip() {
	}
}
