package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A Toolsmith's shift as the village's blacksmith: when a worker is waiting for a tool (a miner's pickaxe, a
 * lumberjack's axe, a fisherman's rod — see {@link Requests}), make one with the game's recipes and take it to their
 * chests. Iron if the storehouse or the smelters have it, else stone; diamond only from the toolsmith's own chests (a
 * player's diamonds go into tools only when they put them there). Goes about the usual day in between.
 */
public class ToolsmithWork extends CrafterWork {
	/** Tools a toolsmith makes, best first within each kind. */
	static final List<Item> IRON_AND_STONE = List.of(Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.IRON_AXE, Items.STONE_AXE,
		Items.IRON_SHOVEL, Items.STONE_SHOVEL, Items.IRON_HOE, Items.STONE_HOE, Items.SHEARS, Items.FISHING_ROD);
	static final List<Item> DIAMOND = List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE);

	public ToolsmithWork() {
		super(Crafting.Kind.CRAFTING, "toolsmith", "toolsmith", false);
	}

	@Nullable
	@Override
	protected Job choose(ServerLevel level, Villager villager, BlockPos station) {
		if (Village.RADIUS <= 0) {
			return null;
		}
		List<BlockPos> own = SupplyContainers.find(level, station, null);
		List<BlockPos> sources = new ArrayList<>(own);
		for (Village.Stash stash : Village.stashes(level, villager, station, null)) {
			VillagerProfession job = stash.job();
			if (job == ModVillagers.PORTER || job == VillagerProfession.ARMORER) {
				sources.addAll(stash.chests());
			}
		}
		Map<Item, Long> ownStock = null;
		Map<Item, Long> stock = null;
		long now = level.getGameTime();
		for (Requests.Request request : Requests.forVillage(level, villager, station)) {
			if (request.worker() == villager) {
				continue;
			}
			String claim = request.worker().getUUID() + "|tool";
			if (claimed(claim, now)
					|| SupplyContainers.firstMatching(level, SupplyContainers.find(level, request.station(), null), request.accepts()) != null) {
				continue; // someone's on it, or one is waiting in their chests already
			}
			if (ownStock == null) {
				ownStock = SupplyContainers.contents(level, own);
				stock = SupplyContainers.contents(level, sources);
			}
			Crafting.Plan plan = best(level, request, DIAMOND, ownStock);
			if (plan == null) {
				plan = best(level, request, IRON_AND_STONE, stock);
			}
			if (plan != null) {
				claim(claim, now);
				return new Job(request.station(), null, plan, claim, plan.takes().keySet().stream().allMatch(ownStock::containsKey) ? own : sources);
			}
		}
		return null;
	}

	/** A plan for the first of {@code tools} the request takes that can be made from {@code stock}, or null. */
	@Nullable
	private Crafting.Plan best(ServerLevel level, Requests.Request request, List<Item> tools, Map<Item, Long> stock) {
		for (Item tool : tools) {
			if (!request.accepts().test(new ItemStack(tool))) {
				continue;
			}
			Crafting.Plan plan = Crafting.plan(level, kind, tool, 1, stock);
			if (fits(plan)) {
				return plan;
			}
		}
		return null;
	}

	/** Partners at work (ROADMAP 28.5): sparks at the smithing table. */
	@Override
	protected String partnerCue() {
		return "forge";
	}
}
