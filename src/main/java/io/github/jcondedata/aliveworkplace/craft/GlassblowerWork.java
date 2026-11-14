package io.github.jcondedata.aliveworkplace.craft;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * A Glassblower's work for the builders nearby at his Glass Kiln (ROADMAP 46.6, {@code aliveworkplace:crafts/glassblower}):
 * glass fired from sand in the kiln, a coal or charcoal for every {@link #GLASS_PER_FUEL} fired (a furnace's rate), then
 * the crafting table's panes, stained glass and stained panes with the Florist's dyes, tinted glass from amethyst and
 * glass bottles, whole chains a bagful at a time like every crafting job (a red pane from sand: the glass, the stained
 * glass, then the panes). The kiln is his own: he never needs a furnace, so he never takes a player's.
 *
 * <p>Another job's thing on the way (the Florist's dye) is named as it is on the builder's line ("short of 1 Red Dye to
 * make 8 Red Stained Glass"), as the player would hand it over, never the flower that makes it; the coal the firing wants
 * is named when it's the coal that's short ("short of 1 Coal to make 8 Glass Panes").
 */
public class GlassblowerWork extends CrafterWork {
	/** Glass fired per coal or charcoal (a furnace's rate). */
	public static final int GLASS_PER_FUEL = 8;

	public GlassblowerWork() {
		super(Crafting.Kind.KILN, "glassblower", "crafter", true);
	}

	@Override
	protected boolean wants(Item item) {
		return false;
	}

	/** His own list ({@code aliveworkplace:crafts/glassblower}): no one's stand-in, and the Mason's no longer. */
	@Override
	protected boolean wants(ServerLevel level, Item item) {
		return CraftJobs.makes(level, CraftJobs.Job.GLASSBLOWER, item);
	}

	/** The kiln's whole chain (sand into glass into panes), with a coal or charcoal for every eight things fired. */
	@Nullable
	@Override
	protected Crafting.Plan planFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		Crafting.Plan plan = chains() ? chainPlan(level, kind, item, count, usable) : Crafting.plan(level, kind, item, count, usable);
		return plan == null ? null : withFuel(level, plan, usable, kind, GLASS_PER_FUEL);
	}

	/**
	 * What's short of the kiln's chain, its fuel counted as a raw material (sand there but no coal names the coal), with
	 * another job's things (the Florist's dyes) named as they are rather than the flowers that make them.
	 */
	@Override
	protected Map<Item, Integer> lackFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		if (Crafting.recipesFor(level, kind, item).isEmpty()) {
			return Map.of();
		}
		return Crafting.asRaw(GlassblowerWork::notHis,
			() -> lackingWithFuel(level, kind, item, Math.min(Math.max(1, count), Crafting.MAX_CRAFTS), usable, GLASS_PER_FUEL));
	}

	/** The kiln's roar and a puff of flame at its mouth while he works. */
	@Override
	protected void workEffects(ServerLevel level, BlockPos station) {
		super.workEffects(level, station);
		level.playSound(null, station, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.6f, 1.0f);
		level.sendParticles(ParticleTypes.FLAME, station.getX() + 0.5, station.getY() + 0.6, station.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.01);
	}

	private static boolean notHis(Item item) {
		CraftJobs.Job job = CraftJobs.tagged(item);
		return job != null && job != CraftJobs.Job.GLASSBLOWER;
	}
}
