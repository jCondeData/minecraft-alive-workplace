package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.block.entity.CampfireBlockEntity;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 28.13 with the real Cobblemon: the Pokémon jobs' builds are in the Blueprint Table, and a builder finishes
 * each tier from barrels holding exactly its materials, with its job block in place (a point of interest a villager can
 * take the job at): the Camp Kitchen's Campfire Pot (with its pot on), the Berry Nursery's composter, the Daycare's
 * Pasture Block. A new build is a row: its test below, with the spots its job block (and what's new in it) stand on.
 */
public class PokemonBuildsCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";

	/** A block that must stand at a template spot when the build is done. */
	private record Expect(BlockPos spot, String block) {
	}

	private static Expect at(int x, int y, int z, String block) {
		return new Expect(new BlockPos(x, y, z), block);
	}

	/** Every Pokémon-job build is in the Blueprint Table with Cobblemon, both tiers. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePokemonJobBuildsAreInTheTable(GameTestHelper helper) {
		List<ResourceLocation> listed = TableServer.listing(helper.getLevel().getServer()).stream().map(e -> e.id()).toList();
		for (StarterBlueprints.Entry entry : StarterBlueprints.JOB_BUILDS) {
			helper.assertTrue(listed.contains(entry.id()), entry.id() + " isn't in the Blueprint Table");
		}
		helper.succeed();
	}

	/** The Camp Kitchen: its Campfire Pot gets a pot on it, the benches and the grain store's Hearty Grain bales. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"camp_kitchen_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "camp_kitchen_build")
	public void aBuilderBuildsTheCampKitchen(GameTestHelper helper) {
		build(helper, StarterBlueprints.CAMP_KITCHEN, List.of(at(6, 1, 5, "cobblemon:campfire"), at(3, 1, 7, "cobblemon:hearty_grain_bale")));
	}

	/** Camp Kitchen II: the Hearty Grain plot (ripe grains on farmland) and the smokehouse's smoker. */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"camp_kitchen_2_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "camp_kitchen_2_build")
	public void aBuilderBuildsCampKitchenII(GameTestHelper helper) {
		build(helper, StarterBlueprints.CAMP_KITCHEN_2, List.of(at(6, 1, 5, "cobblemon:campfire"), at(16, 1, 7, "minecraft:smoker"),
			at(2, 1, 12, "cobblemon:hearty_grains"), at(2, 0, 12, "minecraft:farmland")));
	}

	/** The Berry Nursery: the composter by the path and the paired farmland beds with water between. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"berry_nursery_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "berry_nursery_build")
	public void aBuilderBuildsTheBerryNursery(GameTestHelper helper) {
		build(helper, StarterBlueprints.BERRY_NURSERY, List.of(at(5, 1, 8, "minecraft:composter"), at(2, 0, 2, "minecraft:farmland"),
			at(3, 0, 2, "minecraft:water")));
	}

	/** Berry Nursery II: the greenhouse's beds, within the breeder's reach of the composter. */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"berry_nursery_2_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "berry_nursery_2_build")
	public void aBuilderBuildsBerryNurseryII(GameTestHelper helper) {
		build(helper, StarterBlueprints.BERRY_NURSERY_2, List.of(at(5, 1, 8, "minecraft:composter"), at(2, 0, 13, "minecraft:farmland"),
			at(10, 0, 16, "minecraft:farmland")));
	}

	/** The Daycare: the Pasture Block in the paddock behind the barn. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"daycare_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "daycare_build")
	public void aBuilderBuildsTheDaycare(GameTestHelper helper) {
		build(helper, StarterBlueprints.DAYCARE, List.of(at(7, 1, 12, "cobblemon:pasture"), at(7, 0, 4, "minecraft:hay_block")));
	}

	/** Daycare II: a second paddock with its own Pasture Block, and the hatchery's nests. */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"daycare_2_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "daycare_2_build")
	public void aBuilderBuildsDaycareII(GameTestHelper helper) {
		build(helper, StarterBlueprints.DAYCARE_2, List.of(at(7, 1, 12, "cobblemon:pasture"), at(7, 1, 20, "cobblemon:pasture"),
			at(14, 1, 5, "minecraft:hay_block")));
	}

	/**
	 * Builds {@code entry} by (9, 2, 3) from barrels holding exactly its materials; when it's done every block is right,
	 * nothing was skipped, each {@code expect} block stands at its spot and the first is a job site (a point of interest).
	 */
	private static void build(GameTestHelper helper, StarterBlueprints.Entry entry, List<Expect> expect) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		// At (9, 2, 3), moved in so a wide or deep build keeps room round it in the area for its landscaping (two blocks)
		BlockPos origin = new BlockPos(Math.min(9, 29 - entry.size().getX()), 2, Math.min(3, 29 - entry.size().getZ()));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + entry.id());
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
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
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			for (Expect e : expect) {
				BlockState there = level.getBlockState(placement.origin().offset(e.spot()));
				helper.assertTrue(BuiltInRegistries.BLOCK.getKey(there.getBlock()).toString().equals(e.block()),
					"expected " + e.block() + " at " + e.spot() + ", found " + there);
			}
			BlockPos job = placement.origin().offset(expect.get(0).spot());
			helper.assertTrue(level.getPoiManager().getType(job).isPresent(), "the job block at " + expect.get(0).spot() + " is no job site");
			if (level.getBlockEntity(job) instanceof CampfireBlockEntity pot) {
				helper.assertTrue(pot.getPotItem() != null && !pot.getPotItem().isEmpty(), "the Campfire Pot has no pot on it");
			}
		});
	}
}
