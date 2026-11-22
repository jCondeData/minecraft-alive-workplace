package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.SupplyReport;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA lane B (2026-11-22, lane-b-1122-0338).
 *
 * <p>B1289 in the base game, through the builder's own line (crafter.md: the line names what the chest is short of; "a
 * smoker names 4 Oak Logs"): a builder short of 1 Smoker, the furnace's 8 cobblestone in his chest and a Carpenter at his
 * bench, reads "Can't be made yet: short of 4 &lt;a Minecraft wood&gt; Logs to make 1 Smoker" (never 8, through wood; nothing taken);
 * and with 4 oak logs put in too, the Carpenter makes it and the build finishes.
 *
 * <p>B619 past QaFarSiteBugTests (which checks the table is found 150 blocks off in a radius-160 village): the material
 * list there counts the table's chests (what they hold is not "to bring"); and the boundaries the same rule draws, from
 * both sides: with no village, a Blueprint 120 blocks off (inside maxSiteDistance 128) finds the table and one 150 off
 * doesn't; in a village of radius 140, a Blueprint 150 off (outside the village and past 128) finds none either.
 */
public class QaBuilderAsks1122bGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 9);

	// ---- B1289 ------------------------------------------------------------------------------------------------------

	/** A one-block drawing of {@code block} at {@code origin}. */
	private static ResourceLocation one(GameTestHelper helper, BlockPos origin, net.minecraft.world.level.block.Block block) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(origin, block);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qa1122b_" + BuiltInRegistries.BLOCK.getKey(block).getPath() + "_" + Long.toHexString(level.getGameTime()) + "_" + Math.abs(helper.absolutePos(origin).getX()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(origin, Blocks.AIR);
		Leftovers.after(helper, () -> level.getStructureManager().remove(id));
		return id;
	}

	private record Job(Villager builder, BuildSite site, Villager carpenter) {
	}

	private static Job smokerJob(GameTestHelper helper, ItemStack... items) {
		QaCrafterFurnitureGameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CraftJobs.maker(level, Items.SMOKER) == CraftJobs.Job.CARPENTER,
			"(setup) a smoker's maker is " + CraftJobs.maker(level, Items.SMOKER) + ", expected the Carpenter");
		ResourceLocation id = one(helper, ORIGIN, Blocks.SMOKER);
		helper.setBlock(QaCrafterFurnitureGameTests.BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(QaCrafterFurnitureGameTests.CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(QaCrafterFurnitureGameTests.CHEST);
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, QaCrafterFurnitureGameTests.BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(QaCrafterFurnitureGameTests.BENCH));
		BuildSite site = Builders.start(level, builder, null, id,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null && site.plan(level) != null, "(setup) the smoker drawing wasn't found");
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, QaCrafterFurnitureGameTests.STATION);
		return new Job(builder, site, carpenter);
	}

	private static int count(Container chest, Item item) {
		return chest.countItem(item);
	}

	private static final Pattern FOUR_LOGS = Pattern.compile("Can't be made yet: short of 4 (Oak|Spruce|Birch|Jungle|Acacia|Dark Oak|Mangrove|Cherry) Logs to make 1 Smoker");

	/**
	 * Short of 1 Smoker, 8 cobblestone in the chest, a Carpenter at his bench: the line reads exactly "Can't be made yet:
	 * short of 4 &lt;wood&gt; Logs to make 1 Smoker" (a Minecraft wood, not 8 through wood), and nothing was taken from the chest.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaAsks1122bSmokerShort"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaAsks1122bSmokerShort")
	public void aBuilderShortOfASmokerReadsFourLogsWithTheCobblestoneThere(GameTestHelper helper) {
		Job job = smokerJob(helper, new ItemStack(Items.COBBLESTONE, 8));
		helper.runAfterDelay(1200, () -> {
			ServerLevel level = helper.getLevel();
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			helper.assertTrue(live != null, "the smoker was built with no logs anywhere");
			Component line = JobShortages.line(level, live.id(), live.missing(), helper.absolutePos(QaCrafterFurnitureGameTests.BENCH));
			String text = line == null ? null : line.getString();
			helper.assertTrue(text != null && FOUR_LOGS.matcher(text).matches(),
				"the builder's line: '" + text + "', expected 'Can't be made yet: short of 4 <wood> Logs to make 1 Smoker'; missing " + live.missing());
			Container chest = helper.getBlockEntity(QaCrafterFurnitureGameTests.CHEST);
			helper.assertTrue(count(chest, Items.COBBLESTONE) == 8, "the chest holds " + count(chest, Items.COBBLESTONE) + " cobblestone of 8");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(job.carpenter()).isEmpty(), "the Carpenter took something: "
				+ ModAttachments.BUILDER_BAG.getOrCreate(job.carpenter()).stacks());
			helper.succeed();
		});
	}

	/** The other side: 8 cobblestone and 4 oak logs there. The Carpenter makes the smoker and the build finishes, using them all. */
	//$ gametest_ticks_batch AREA '4000' '"qaAsks1122bSmokerMade"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaAsks1122bSmokerMade")
	public void withFourLogsTooTheCarpenterMakesTheSmoker(GameTestHelper helper) {
		Job job = smokerJob(helper, new ItemStack(Items.COBBLESTONE, 8), new ItemStack(Items.OAK_LOG, 4));
		helper.succeedWhen(() -> {
			ServerLevel level = helper.getLevel();
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			helper.assertTrue(live == null, "still building: status=" + (live == null ? "-" : live.status() + " missing=" + live.missing()
				+ " line=" + JobShortages.line(level, live.id(), live.missing())));
			helper.assertBlockPresent(Blocks.SMOKER, ORIGIN);
			Container chest = helper.getBlockEntity(QaCrafterFurnitureGameTests.CHEST);
			helper.assertTrue(count(chest, Items.COBBLESTONE) == 0 && count(chest, Items.OAK_LOG) == 0,
				"left in the chest: " + count(chest, Items.COBBLESTONE) + " cobblestone, " + count(chest, Items.OAK_LOG) + " oak logs");
		});
	}

	// ---- B619 -------------------------------------------------------------------------------------------------------

	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos WORK = new BlockPos(3, 2, 3);
	private static final BlockPos SUPPLY = new BlockPos(1, 2, 4);

	/** Takes every Village Hall but ours within {@code range} across of {@code spot}. */
	private static void hallsRound(GameTestHelper helper, BlockPos spot, int range) {
		ServerLevel level = helper.getLevel();
		BlockPos ours = helper.absolutePos(HALL);
		for (BlockPos hall : level.getPoiManager().getInSquare(h -> h.is(ModVillagers.VILLAGE_HALL_POI), spot, range, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos).filter(p -> !p.equals(ours)).toList()) {
			level.removeBlock(hall, false);
			if (level.getPoiManager().getType(hall).filter(h -> h.is(ModVillagers.VILLAGE_HALL_POI)).isPresent()) {
				level.getPoiManager().remove(hall);
			}
		}
		VillageHalls.forgetNearest();
	}

	/**
	 * Common: protection off, the hall radius as given (0: no hall at all), every other hall and table round the far
	 * spots taken away, our Blueprint Table at WORK. Returns the table.
	 */
	private static BlockPos setUp(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		int was = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.RADIUS = was;
			VillageHalls.forgetNearest();
		});
		VillageHalls.RADIUS = radius == 0 ? was : radius;
		BlockPos far = helper.absolutePos(WORK).offset(150, 0, 0);
		hallsRound(helper, far, 2 * (Math.max(VillageHalls.RADIUS, 160) + 1));
		Leftovers.benches(helper, far, 2 * (Math.max(VillageHalls.RADIUS, 160) + 1) + 16);
		if (radius > 0) {
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			BlockPos hall = helper.absolutePos(HALL);
			Leftovers.after(helper, () -> {
				level.removeBlock(hall, false);
				VillageHalls.forgetNearest();
			});
		}
		VillageHalls.forgetNearest();
		helper.setBlock(WORK, ModBlocks.BLUEPRINT_TABLE);
		return helper.absolutePos(WORK);
	}

	/**
	 * In a radius-160 village, a Blueprint 150 blocks from the table: its material list counts the chest by the table.
	 * Empty chest first: every line all to bring. Then the chest holds each line's whole need: nothing to bring.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaAsks1122bFarList"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaAsks1122bFarList")
	public void theMaterialListOfABuildAcrossTheVillageCountsTheTablesChest(GameTestHelper helper) {
		BlockPos table = setUp(helper, 160);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SUPPLY, Blocks.CHEST);
		ItemStack blueprint = QaBuilderLimitsGameTests.placedHut(level, table, new Vec3i(150, 0, 0));
		var data = BlueprintItem.data(blueprint).orElseThrow();
		BlueprintSupplies.Checklist empty = BlueprintSupplies.checklist(level, data).orElseThrow();
		helper.assertTrue(empty.bench().equals(Optional.of(table)), "(the list's table) " + empty.bench() + ", expected " + table);
		int need = empty.lines().stream().mapToInt(BlueprintSupplies.Line::need).sum();
		helper.assertTrue(need > 0 && empty.toBring() == need, "with the table's chest empty, " + empty.toBring() + " of " + need + " to bring");
		helper.assertTrue(empty.lines().size() <= 27, "(setup) the hut needs " + empty.lines().size() + " kinds, more than a chest's slots");
		Container chest = helper.getBlockEntity(SUPPLY);
		int slot = 0;
		for (BlueprintSupplies.Line line : empty.lines()) {
			chest.setItem(slot++, new ItemStack(line.item(), line.need()));
		}
		BlueprintSupplies.Checklist full = BlueprintSupplies.checklist(level, data).orElseThrow();
		List<String> still = full.lines().stream().filter(l -> l.toBring() > 0).map(l -> l.toBring() + " " + l.item()).toList();
		helper.assertTrue(full.toBring() == 0, "with every material in the table's chest, the list 150 blocks off still says to bring " + still);
		helper.succeed();
	}

	/** No village: 120 blocks off (inside maxSiteDistance 128) the report and list find the table; 150 off they don't. */
	//$ gametest_ticks_batch AREA '200' '"qaAsks1122bLongReach"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaAsks1122bLongReach")
	public void withNoVillageTheTableIsFoundInsideTheLongReachOnly(GameTestHelper helper) {
		BlockPos table = setUp(helper, 0);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Builders.MAX_SITE_DISTANCE == 128, "(setup) maxSiteDistance is " + Builders.MAX_SITE_DISTANCE);
		var near = BlueprintItem.data(QaBuilderLimitsGameTests.placedHut(level, table, new Vec3i(120, 0, 0))).orElseThrow();
		var far = BlueprintItem.data(QaBuilderLimitsGameTests.placedHut(level, table, new Vec3i(150, 0, 0))).orElseThrow();
		Optional<SupplyReport> nearReport = BlueprintSupplies.check(level, near);
		helper.assertTrue(nearReport.isPresent() && nearReport.get().bench().equals(Optional.of(table)),
			"120 blocks off with no village, the report's table is " + nearReport.map(SupplyReport::bench) + ", expected " + table);
		helper.assertTrue(BlueprintSupplies.checklist(level, near).orElseThrow().bench().equals(Optional.of(table)), "120 blocks off, the list counts no table");
		Optional<SupplyReport> farReport = BlueprintSupplies.check(level, far);
		helper.assertTrue(farReport.isPresent() && farReport.get().bench().isEmpty(),
			"150 blocks off with no village (past 128), the report's table is " + farReport.map(SupplyReport::bench) + ", expected none");
		helper.assertTrue(BlueprintSupplies.checklist(level, far).orElseThrow().bench().isEmpty(), "150 blocks off with no village, the list counts a table");
		helper.succeed();
	}

	/** A village of radius 140: a Blueprint 150 blocks off is outside it and past the long reach, so no table is found. */
	//$ gametest_ticks_batch AREA '200' '"qaAsks1122bPastVillage"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaAsks1122bPastVillage")
	public void aBuildJustOutsideTheVillageAndPastTheLongReachFindsNoTable(GameTestHelper helper) {
		BlockPos table = setUp(helper, 140);
		ServerLevel level = helper.getLevel();
		var far = BlueprintItem.data(QaBuilderLimitsGameTests.placedHut(level, table, new Vec3i(150, 0, 0))).orElseThrow();
		var inside = BlueprintItem.data(QaBuilderLimitsGameTests.placedHut(level, table, new Vec3i(130, 0, 0))).orElseThrow();
		Optional<SupplyReport> farReport = BlueprintSupplies.check(level, far);
		helper.assertTrue(farReport.isPresent() && farReport.get().bench().isEmpty(),
			"150 blocks off, outside a radius-140 village, the report's table is " + farReport.map(SupplyReport::bench) + ", expected none");
		Optional<SupplyReport> insideReport = BlueprintSupplies.check(level, inside);
		helper.assertTrue(insideReport.isPresent() && insideReport.get().bench().equals(Optional.of(table)),
			"(control) 130 blocks off, inside the radius-140 village and past 128, the report's table is " + insideReport.map(SupplyReport::bench));
		helper.succeed();
	}
}
