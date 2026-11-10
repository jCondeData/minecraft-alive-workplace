package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.ranch.RanchWork;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;

/**
 * A Shepherd weaving for builders at his loom (ROADMAP 46.2; vanilla's Shepherd sells beds, carpets, banners and
 * paintings): wool from string, dyed wool, carpets, beds, banners, banner patterns and paintings, his list
 * ({@code aliveworkplace:crafts/shepherd}), with the game's recipes and the chain steps a job takes (the wool spun from
 * string first, then dyed, then the bed). He works it in between the flock ({@code ranch/ShepherdWork}): each waits
 * while the other is busy, so the bag the two share never mixes a job's makings with the wool just sheared.
 *
 * <p>What another job makes (a dye is the Florist's, planks the Carpenter's) he still makes when a step needs it and the
 * makings are in the chests (lapis into blue dye, as the dyer who wove before him did, so nothing he made becomes
 * unmakable); but when it's short, the builder's line names that thing itself ("short of 6 Blue Dye to make 2 Blue
 * Beds"), which is what a player hands over, not the lapis or cornflowers that make it. Since 46.3, with a Florist at
 * work near him, he leaves the dyes to her ({@link #planFor}).
 */
public class WeaverWork extends CrafterWork {
	public WeaverWork() {
		super(Crafting.Kind.CRAFTING, "weaver", "crafter", false);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		// the flock's work and the loom's take turns (the Librarian's books and enchanting do the same)
		return super.checkExtraStartConditions(level, villager) && (isBusy(villager) || !RanchWork.isBusy(villager));
	}

	@Override
	protected boolean wants(Item item) {
		return false;
	}

	/** The Shepherd planning, for {@link #planFor} (46.3: whether a Florist works near him). */
	@org.jetbrains.annotations.Nullable
	private Villager self;

	@org.jetbrains.annotations.Nullable
	@Override
	protected Job choose(ServerLevel level, Villager villager, net.minecraft.core.BlockPos station) {
		self = villager;
		return super.choose(level, villager, station);
	}

	/**
	 * 46.3: with a Florist at work near him the dyes are hers to make ({@link FloristCraftWork#helps}): he takes them made,
	 * never from the lapis or flowers in the chests, and waits for hers. With none about he makes them as before.
	 */
	@org.jetbrains.annotations.Nullable
	@Override
	protected Crafting.Plan planFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		Villager shepherd = self;
		if (shepherd == null || CraftJobs.workersNear(level, shepherd.blockPosition(), io.github.jcondedata.aliveworkplace.work.Village.RADIUS,
				io.github.jcondedata.aliveworkplace.registry.ModVillagers.FLORIST).isEmpty()) {
			return super.planFor(level, item, count, usable);
		}
		return Crafting.asRaw(WeaverWork::floristsDye, () -> super.planFor(level, item, count, usable));
	}

	private static boolean floristsDye(Item item) {
		return CraftJobs.tagged(item) == CraftJobs.Job.FLORIST;
	}

	/** 46.2: the Shepherd's own list ({@code aliveworkplace:crafts/shepherd}), no one's stand-in any more. */
	@Override
	protected boolean wants(ServerLevel level, Item item) {
		return CraftJobs.makes(level, CraftJobs.Job.SHEPHERD, item);
	}

	/** What's short, with another job's things (a dye, planks, sticks) named as they are rather than what makes them. */
	@Override
	protected Map<Item, Integer> lackFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		if (Crafting.recipesFor(level, kind, item).isEmpty()) {
			return Map.of();
		}
		return Crafting.lacking(level, kind, item, Math.min(Math.max(1, count), Crafting.MAX_CRAFTS), usable, WeaverWork::notHis);
	}

	private static boolean notHis(Item item) {
		// on another job's list (its tag): a dye the Florist's, planks and sticks the Carpenter's
		CraftJobs.Job job = CraftJobs.tagged(item);
		return job != null && job != CraftJobs.Job.SHEPHERD;
	}
}
