package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.flower.FloristWork;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;

/**
 * A Florist making her list for builders at the Flower Stand (ROADMAP 46.3, {@code aliveworkplace:crafts/florist}):
 * flower pots and decorated pots from bricks, dyes from the flowers she grows (and bone meal, lapis, cocoa...), moss
 * carpet, with the game's recipes, step by step like every crafting job. She works it in between the garden
 * ({@link FloristWork}, which still fills the empty pots round the stand): each waits while the other is busy, so the
 * bag the two share never mixes a job's makings with the flowers just picked.
 *
 * <p>Bricks are the Mason's: short of them she names them on the builder's line ("short of 3 Bricks to make 1 Flower
 * Pot"), and a Mason with a furnace by his stonecutter fires them from the clay in the chests ({@link MasonWork#helps}).
 * The dyes the Shepherd's beds, wool and banners need are hers to make for him while she works near the builder
 * ({@link #helps}; {@link WeaverWork} waits for them then).
 */
public class FloristCraftWork extends CrafterWork {
	public FloristCraftWork() {
		super(Crafting.Kind.CRAFTING, "florist_craft", "crafter", false);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		// the garden's work and the stand's take turns (the Shepherd's flock and loom do the same)
		return super.checkExtraStartConditions(level, villager) && FloristWork.isFlorist(villager)
			&& (isBusy(villager) || !FloristWork.isBusy(villager));
	}

	@Override
	protected boolean wants(Item item) {
		return false;
	}

	/** 46.3: the Florist's own list ({@code aliveworkplace:crafts/florist}), no one's stand-in any more. */
	@Override
	protected boolean wants(ServerLevel level, Item item) {
		return CraftJobs.makes(level, CraftJobs.Job.FLORIST, item);
	}

	/** The dyes for the Shepherd's things (46.3). */
	@Override
	protected boolean helps(CraftJobs.Job maker) {
		return maker == CraftJobs.Job.SHEPHERD;
	}

	/** What's short, with another job's things (the Mason's bricks) named as they are rather than the clay that makes them. */
	@Override
	protected Map<Item, Integer> lackFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		if (Crafting.recipesFor(level, kind, item).isEmpty()) {
			return Map.of();
		}
		return Crafting.lacking(level, kind, item, Math.min(Math.max(1, count), Crafting.MAX_CRAFTS), usable, FloristCraftWork::notHers);
	}

	private static boolean notHers(Item item) {
		CraftJobs.Job job = CraftJobs.tagged(item);
		return job != null && job != CraftJobs.Job.FLORIST;
	}
}
