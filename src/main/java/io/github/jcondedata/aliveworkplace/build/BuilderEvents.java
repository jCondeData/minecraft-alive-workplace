package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/** Player ↔ builder interactions and lifecycle hooks. */
public final class BuilderEvents {
	public static void init() {
		// Right-click a builder with a blueprint: hand it over. Sneak-right-click with an empty hand: status.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !(entity instanceof Villager villager) || !Builders.isBuilder(villager)) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (held.is(ModItems.BLUEPRINT)) {
				if (level.isClientSide) {
					return InteractionResult.SUCCESS;
				}
				return Builders.assign((ServerPlayer) player, villager, held);
			}
			if (held.isEmpty() && player.isShiftKeyDown()) {
				if (!level.isClientSide) {
					Builders.sendStatus(player, villager);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Villager villager && entity.level() instanceof ServerLevel level) {
				Builders.onBuilderDeath(level, villager);
			}
		});
	}

	private BuilderEvents() {
	}
}
