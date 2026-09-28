package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * A builder who finishes a build that has an upgrade ({@code <name>_2}, see {@link BlueprintUpgrades}) sells that
 * upgrade's blueprint from then on: a new trade added to their offers, kept with the villager like any other.
 */
public final class UpgradeOffers {
	/** Most times the upgrade can be bought before the builder restocks. */
	static final int MAX_USES = 3;

	/**
	 * Adds the trade for the upgrade of {@code built} if there is one and the builder doesn't sell it yet.
	 * Returns the upgrade's id when a trade was added.
	 */
	public static Optional<ResourceLocation> offer(ServerLevel level, Villager builder, ResourceLocation built) {
		ResourceLocation upgrade = BlueprintUpgrades.upgradeOf(built);
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, upgrade);
		if (blueprint.isEmpty() || sells(builder, upgrade)) {
			return Optional.empty();
		}
		builder.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, price(blueprint.get())),
			BlueprintItem.create(upgrade, blueprint.get().size()), MAX_USES, 10, 0.05f));
		return Optional.of(upgrade);
	}

	/** Whether the villager already has a trade for this blueprint. */
	public static boolean sells(Villager villager, ResourceLocation blueprint) {
		return villager.getOffers().stream().anyMatch(offer -> BlueprintItem.data(offer.getResult())
			.map(data -> data.structure().equals(blueprint)).orElse(false));
	}

	/** Emeralds for an upgrade: more for bigger builds (the starter upgrades cost 8 to 18). */
	static int price(Blueprint blueprint) {
		return Mth.clamp(4 + blueprint.solidBlockCount() / 40, 6, 32);
	}

	private UpgradeOffers() {
	}
}
