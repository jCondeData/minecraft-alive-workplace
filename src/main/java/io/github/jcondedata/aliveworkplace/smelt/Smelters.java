package io.github.jcondedata.aliveworkplace.smelt;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.work.Hiring;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Armorers as the village's smelters (see {@link SmelterWork}): every Armorer with a chest by its blast furnace smelts
 * for the village; sneak-right-clicking one with coal or charcoal hires them, so they fetch ore from your own workers too.
 */
public final class Smelters {
	/** Iron ingots a smelter keeps in its chests (the porter leaves them): enough for a set of armor. */
	public static final int KEEP_INGOTS = 24;

	public static boolean isSmelter(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.ARMORER;
	}

	/** Coal or charcoal. */
	public static boolean isFuel(Item item) {
		return new ItemStack(item).is(ItemTags.COALS);
	}

	/** Vanilla's armorer routine runs while the smelter has nothing in hand. */
	public static boolean vanillaMayRun(Villager villager) {
		return !SmelterWork.isBusy(villager);
	}

	/** A player sneak-right-clicked an Armorer holding coal or charcoal: hire them. */
	public static InteractionResult hire(ServerPlayer player, Villager villager) {
		return Hiring.hire(player, villager, Component.translatable("message.aliveworkplace.smelter.hired", villager.getDisplayName(),
			SupplyContainers.RADIUS));
	}

	private Smelters() {
	}
}
