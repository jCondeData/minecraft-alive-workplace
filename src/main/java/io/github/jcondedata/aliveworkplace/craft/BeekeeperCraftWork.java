package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.bee.BeekeeperWork;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;

/**
 * A Beekeeper making her list for builders at the Apiary (ROADMAP 46.5, {@code aliveworkplace:crafts/beekeeper}):
 * candles from the hives' honeycomb and string, colored candles (a candle and a dye) and honeycomb blocks, with the
 * game's recipes, step by step like every crafting job (a red candle from honeycomb, string and red dye: the candle
 * first, then the color). She works it in between the hives ({@link BeekeeperWork}, which still harvests, plants and
 * breeds): each waits while the other is busy, so the bag the two share never mixes a job's makings with the harvest
 * on its way to the chests.
 *
 * <p>Another job's thing on the way (the Florist's dye) is named as it is on the builder's line ("short of 1 Red Dye
 * to make 1 Red Candle"), as the player would hand it over, never what makes it.
 */
public class BeekeeperCraftWork extends CrafterWork {
	public BeekeeperCraftWork() {
		super(Crafting.Kind.CRAFTING, "beekeeper_craft", "crafter", false);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		// the hives' work and the Apiary's crafting take turns (the Florist's garden and stand do the same)
		return super.checkExtraStartConditions(level, villager) && BeekeeperWork.isBeekeeper(villager)
			&& (isBusy(villager) || !BeekeeperWork.isBusy(villager));
	}

	@Override
	protected boolean wants(Item item) {
		return false;
	}

	/** 46.5: the Beekeeper's own list ({@code aliveworkplace:crafts/beekeeper}), no one's stand-in any more. */
	@Override
	protected boolean wants(ServerLevel level, Item item) {
		return CraftJobs.makes(level, CraftJobs.Job.BEEKEEPER, item);
	}

	/** What's short, with another job's things (the Florist's dyes) named as they are rather than the flowers that make them. */
	@Override
	protected Map<Item, Integer> lackFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		if (Crafting.recipesFor(level, kind, item).isEmpty()) {
			return Map.of();
		}
		return Crafting.lacking(level, kind, item, Math.min(Math.max(1, count), Crafting.MAX_CRAFTS), usable, BeekeeperCraftWork::notHers);
	}

	private static boolean notHers(Item item) {
		CraftJobs.Job job = CraftJobs.tagged(item);
		return job != null && job != CraftJobs.Job.BEEKEEPER;
	}
}
