package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.people.Tonics;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * A tonic's tooltip (ROADMAP 30.15): what it does, for which jobs and who makes it from what, on any item the server's
 * tonic files make a tonic (ours, or a pack's), as the server sent it ({@link Tonics.Sync}).
 */
public final class TonicTooltip {
	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Tonics.Sync.TYPE, (payload, context) -> Tonics.receive(payload));
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (!stack.isEmpty()) {
				lines.addAll(Tonics.clientTooltip(BuiltInRegistries.ITEM.getKey(stack.getItem())));
			}
		});
	}

	private TonicTooltip() {
	}
}
