package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.brew.AlchemistWork;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;

/**
 * A Cleric making the magic blocks for builders at his brewing stand (ROADMAP 46.4, {@code aliveworkplace:crafts/cleric}):
 * brewing stands, blaze powder, eyes of ender, ender chests, end rods and beacons, with the game's recipes, step by step
 * like every crafting job (the blaze powder ground from a rod first, then the eye of ender). He works it in between his
 * potions ({@link AlchemistWork}, which still brews for the guards and makes the tonics): each waits while the other is
 * busy, so the bag the two share never mixes a job's makings with the bottles just fetched.
 *
 * <p>A blaze rod comes from the Nether and no job makes it: short of one, the builder's line names it ("short of 1
 * Blaze Rod to make 1 Brewing Stand"). Another job's thing on the way (the Mason's stone, the Florist's dye) is named as
 * it is, as the player would hand it over, never what makes it.
 */
public class ClericCraftWork extends CrafterWork {
	public ClericCraftWork() {
		super(Crafting.Kind.CRAFTING, "cleric_craft", "crafter", false);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		// the potions' work and the stand's crafting take turns (the Shepherd's flock and loom do the same)
		return super.checkExtraStartConditions(level, villager) && AlchemistWork.isAlchemist(villager)
			&& (isBusy(villager) || !AlchemistWork.isBusy(villager));
	}

	@Override
	protected boolean wants(Item item) {
		return false;
	}

	/** 46.4: the Cleric's own list ({@code aliveworkplace:crafts/cleric}), no one's stand-in any more. */
	@Override
	protected boolean wants(ServerLevel level, Item item) {
		return CraftJobs.makes(level, CraftJobs.Job.CLERIC, item);
	}

	/** What's short, with another job's things named as they are rather than what makes them. */
	@Override
	protected Map<Item, Integer> lackFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		if (Crafting.recipesFor(level, kind, item).isEmpty()) {
			return Map.of();
		}
		return Crafting.lacking(level, kind, item, Math.min(Math.max(1, count), Crafting.MAX_CRAFTS), usable, ClericCraftWork::notHis);
	}

	private static boolean notHis(Item item) {
		CraftJobs.Job job = CraftJobs.tagged(item);
		return job != null && job != CraftJobs.Job.CLERIC;
	}
}
