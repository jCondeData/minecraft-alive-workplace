package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A Fletcher's shift for the guards: one with no bow (and none in their chests) gets a crossbow if there's iron for one,
 * else a bow; one with a bow and few special arrows gets spectral arrows (glowstone and arrows — arrows made from flint,
 * sticks and feathers if need be). Made with the game's recipes from the fletcher's chests, the storehouse and the
 * smelters, and brought to the chests by the Guard Post, where the guard picks them up.
 */
public class FletcherWork extends CrafterWork {
	static final List<Item> LAUNCHERS = List.of(Items.CROSSBOW, Items.BOW);
	/** Spectral arrows made at a time. */
	static final int ARROWS = 8;

	public FletcherWork() {
		super(Crafting.Kind.CRAFTING, "fletcher", "fletcher", false);
	}

	@Nullable
	@Override
	protected Job choose(ServerLevel level, Villager villager, BlockPos station) {
		if (Village.RADIUS <= 0) {
			return null;
		}
		List<BlockPos> sources = new ArrayList<>(SupplyContainers.find(level, station, null));
		for (Village.Stash stash : Village.stashes(level, villager, station, null)) {
			VillagerProfession job = stash.job();
			if (job == ModVillagers.PORTER || job == VillagerProfession.ARMORER) {
				sources.addAll(stash.chests());
			}
		}
		Map<Item, Long> stock = null;
		long now = level.getGameTime();
		var boss = ModAttachments.BUILDER_EMPLOYER.get(villager);
		for (Villager guard : level.getEntitiesOfClass(Villager.class, new AABB(station).inflate(Village.RADIUS),
				v -> v.isAlive() && Guards.isGuard(v) && Village.sameSide(level, ModAttachments.BUILDER_EMPLOYER.get(v), boss))) {
			BlockPos post = Builders.benchPos(guard).orElse(null);
			if (post == null) {
				continue;
			}
			List<BlockPos> theirs = SupplyContainers.find(level, post, null);
			String claim = guard.getUUID() + "|fletcher";
			if (theirs.isEmpty() || claimed(claim, now)) {
				continue;
			}
			boolean bow = Guards.isBow(guard.getItemBySlot(EquipmentSlot.OFFHAND)) || SupplyContainers.firstMatching(level, theirs, Guards::isBow) != null;
			if (stock == null) {
				stock = SupplyContainers.contents(level, sources);
			}
			if (!bow) {
				for (Item launcher : LAUNCHERS) {
					Crafting.Plan plan = Crafting.plan(level, kind, launcher, 1, stock);
					if (fits(plan)) {
						claim(claim, now);
						return new Job(post, null, plan, claim, sources);
					}
				}
				continue;
			}
			long arrows = Guards.quiver(guard) + SupplyContainers.countMatching(level, theirs, new net.minecraft.world.item.ItemStack(Items.SPECTRAL_ARROW));
			if (arrows < Guards.QUIVER / 2) {
				Crafting.Plan plan = Crafting.plan(level, kind, Items.SPECTRAL_ARROW, ARROWS, stock);
				if (fits(plan)) {
					claim(claim, now);
					return new Job(post, null, plan, claim, sources);
				}
			}
		}
		return null;
	}

	/** Partners at work (ROADMAP 28.5): a feather or string brought to the table. */
	@Override
	protected String partnerCue() {
		return "fletch";
	}
}
