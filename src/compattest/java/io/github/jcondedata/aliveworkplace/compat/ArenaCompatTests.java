package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 28.16 with the real Cobblemon: an Expert Trainer Leader sells the Arena's blueprint (and no Leader below Expert
 * does), and a builder builds Arena III with a real Healing Machine in each trainers' room. The Arena in the Blueprint
 * Table: {@code PokemonCenterCompatTests.thePokemonCenterIsInTheTable} (every Cobblemon-only blueprint).
 */
public class ArenaCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	/** The Healing Machines of Arena III (template spots, one in each trainers' room; tools/blueprints/arena.py). */
	private static final List<BlockPos> MACHINES = List.of(new BlockPos(3, 1, 19), new BlockPos(21, 1, 19));

	/** An Expert Trainer Leader sells the Arena's blueprint; nothing a Leader sells below Expert is one. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void anExpertTrainerLeaderSellsTheArena(GameTestHelper helper) {
		Villager leader = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		leader.setNoAi(true);
		var byLevel = VillagerTrades.TRADES.get(ModVillagers.TRAINER_LEADER);
		helper.assertTrue(byLevel != null, "Trainer Leaders trade nothing");
		int arenas = 0;
		for (int level = 1; level <= 5; level++) {
			VillagerTrades.ItemListing[] listings = byLevel.get(level);
			for (VillagerTrades.ItemListing listing : listings == null ? new VillagerTrades.ItemListing[0] : listings) {
				MerchantOffer offer = listing.getOffer(leader, RandomSource.create(level));
				boolean arena = offer != null && BlueprintItem.data(offer.getResult())
					.map(d -> d.structure().equals(StarterBlueprints.ARENA.id())).orElse(false);
				helper.assertTrue(!arena || level == 4, "a level " + level + " Leader sells the Arena");
				if (arena) {
					arenas++;
					helper.assertTrue(offer.getCostA().is(net.minecraft.world.item.Items.EMERALD), "the Arena costs " + offer.getCostA());
				}
			}
		}
		helper.assertTrue(arenas == 1, arenas + " Arena offers at Expert");
		leader.discard();
		helper.succeed();
	}

	/** A builder builds Arena III, both trainers' rooms' Healing Machines included, from what's in the barrels. */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"arena_3_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "arena_3_build")
	public void aBuilderBuildsArenaIIIWithItsHealingMachines(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(27, 2, 1);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(27, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(0, 2, 0)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.ARENA_3.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + StarterBlueprints.ARENA_3.id());
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(plan.materials().keySet().stream().anyMatch(i -> BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("cobblemon")),
			"the plan needs none of Cobblemon's blocks: " + plan.materials().keySet());
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(26 + i % 4, 2, 6 + 2 * (i / 4));
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(3)
				.map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			// (no check of site.skipped(): the build fills the area to its north and west edges, and the landscaping round
			// it reaches past them into the void outside the test area, where those steps are skipped)
			for (BlockPos spot : MACHINES) {
				helper.assertTrue(BuiltInRegistries.BLOCK.getKey(level.getBlockState(placement.origin().offset(spot)).getBlock())
					.equals(ModVillagers.HEALING_MACHINE_BLOCK), "no Healing Machine at " + spot + ": " + level.getBlockState(placement.origin().offset(spot)));
			}
		});
	}
}
