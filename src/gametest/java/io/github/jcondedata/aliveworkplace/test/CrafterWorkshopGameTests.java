package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.craft.Crafters;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.mixin.StructureTemplatePoolAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * ROADMAP 37.16: the Crafter's Workshop where the other job workshops are. In the Blueprint Table (not while Crafters are
 * off or Milestone 37 is closed), planned by the Steward when the village's builders keep running short of crafted parts
 * (and not for one shortfall, raw materials, with Crafters off or a workshop already built), and grown by villages in
 * every style while Crafters are on. The house's Crafter taking its workbench is VillageGameTests'.
 */
public class CrafterWorkshopGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation WORKSHOP = StarterBlueprints.CRAFTERS_WORKSHOP.id();

	/** In the Blueprint Table, on the Village Map as a workshop and named; gone with Crafters off or Milestone 37 closed. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theWorkshopIsOfferedOnlyWhileCraftersAreOn(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		helper.assertTrue(StarterBlueprints.offered(WORKSHOP), "not offered with Crafters on");
		helper.assertTrue(BlueprintLibrary.list(server, false).contains(WORKSHOP), "not in the Blueprint Table with Crafters on");
		helper.assertTrue(VillageMaps.kindOf(WORKSHOP).orElse(null) == VillageMaps.Kind.WORKSHOPS, "on the map as " + VillageMaps.kindOf(WORKSHOP));
		helper.assertTrue(net.minecraft.locale.Language.getInstance().has("blueprint.aliveworkplace.crafters_workshop"), "no blueprint name");
		helper.assertTrue(net.minecraft.locale.Language.getInstance().has("village_piece.aliveworkplace.crafters_workshop"), "no village house name");
		boolean on = Crafters.ENABLED;
		boolean open = Expansions.openForTests;
		try {
			Crafters.ENABLED = false;
			helper.assertTrue(!StarterBlueprints.offered(WORKSHOP), "offered with Crafters off");
			helper.assertTrue(!BlueprintLibrary.list(server, false).contains(WORKSHOP), "in the Blueprint Table with Crafters off");
			helper.assertTrue(StarterBlueprints.offered(StarterBlueprints.JEWELLERS_WORKSHOP.id()), "another job's workshop went with it");
			Crafters.ENABLED = true;
			Expansions.openForTests = false;
			helper.assertTrue(Expansions.M37 || !StarterBlueprints.offered(WORKSHOP), "offered while Milestone 37 is closed");
		} finally {
			Crafters.ENABLED = on;
			Expansions.openForTests = open;
		}
		helper.assertTrue(StarterBlueprints.offered(WORKSHOP), "not offered again with Crafters back on");
		helper.succeed();
	}

	/**
	 * The Steward's rule: nothing waiting, one site short of one crafted part, or short only of raw and smelted materials
	 * (logs, cobblestone, glass) doesn't hold; three crafted parts waited for over two sites does, and the morning's wishes
	 * then plan a Crafter's Workshop in the workshops quarter. With Crafters off it doesn't, nor once a workshop is built.
	 */
	//$ gametest_ticks_batch AREA '40' '"crafterWorkshopRule"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "crafterWorkshopRule")
	public void theStewardPlansAWorkshopWhenBuildersKeepRunningShortOfParts(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		StewardRules.Rule rule = StewardRules.all().stream().filter(r -> r.id().equals(AliveWorkplace.id("workplace_crafters_workshop"))).findFirst()
			.orElseThrow(() -> new GameTestAssertException("the Crafter's Workshop rule didn't load"));
		helper.assertTrue(rule.effect().blueprint().map(WORKSHOP::equals).orElse(false), "the rule builds " + rule.effect().blueprint());
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<BuildSite> made = new java.util.ArrayList<>();
		Leftovers.after(helper, () -> made.forEach(s -> sites.remove(s.id())));

		expect(helper, rule, false, 0, "nothing waiting");
		BuildSite first = waiting(helper, made, new BlockPos(2, 2, 2), Map.of(Items.OAK_STAIRS, 6));
		expect(helper, rule, false, 1, "one site short of one crafted part");
		BuildSite raw = waiting(helper, made, new BlockPos(2, 2, 16), Map.of(Items.OAK_LOG, 10, Items.COBBLESTONE, 20, Items.GLASS, 4));
		expect(helper, rule, false, 1, "raw and smelted materials short");
		waiting(helper, made, new BlockPos(16, 2, 2), Map.of(Items.OAK_SLAB, 8, Items.OAK_DOOR, 1));
		expect(helper, rule, true, 3, "three crafted parts waited for over two sites");
		// a site working again (its materials came) no longer counts
		first.setStatus(BuildSite.Status.WORKING);
		expect(helper, rule, false, 2, "one site supplied again");
		first.setStatus(BuildSite.Status.WAITING_FOR_MATERIALS);
		raw.setMissing(new HashMap<>(Map.of(Items.OAK_LOG, 10, Items.STONE_BRICK_STAIRS, 4)));
		expect(helper, rule, true, 4, "four crafted parts");

		List<StewardWishes.Wish> wishes = StewardWishes.rank(List.of(rule), StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY,
			StewardWishes.day(level));
		helper.assertTrue(wishes.size() == 1 && wishes.get(0).effect().blueprint().map(WORKSHOP::equals).orElse(false)
			&& wishes.get(0).effect().zone().map("workshops"::equals).orElse(false), "the morning's wishes: " + wishes);

		boolean on = Crafters.ENABLED;
		Crafters.ENABLED = false;
		try {
			expect(helper, rule, false, 0, "Crafters off");
		} finally {
			Crafters.ENABLED = on;
		}

		BlueprintData.Placement built = placement(helper, new BlockPos(16, 2, 16));
		sites.recordFinished(WORKSHOP, built, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(built));
		StewardWishes.Verdict verdict = StewardWishes.judge(rule, StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, StewardWishes.day(level));
		helper.assertTrue(verdict.status() == StewardWishes.Status.NOT_HELD, "a workshop is built and the rule still " + verdict.status());
		helper.succeed();
	}

	/** Villages grow the Crafter's Workshop in every style while Crafters are on; off, it's left out of the houses. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void villagesGrowTheWorkshopOnlyWhileCraftersAreOn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(VillageHouses.houseNames().contains("crafters_workshop"), "Crafters on: " + VillageHouses.houseNames());
		var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		for (String style : VillageHouses.STYLES) {
			ResourceLocation id = AliveWorkplace.id("village/" + style + "_crafters_workshop");
			StructureTemplatePool pool = pools.get(VillageHouses.housePool(style));
			boolean listed = ((StructureTemplatePoolAccessor) pool).aliveworkplace$rawTemplates().stream()
				.anyMatch(p -> p.getFirst().toString().matches(".*" + java.util.regex.Pattern.quote(id.toString()) + "(?![a-z_0-9]).*"));
			helper.assertTrue(listed, style + " villages can't grow a Crafter's Workshop");
			var template = level.getStructureManager().get(id).orElseThrow(() -> new GameTestAssertException("missing " + id));
			helper.assertTrue(template.filterBlocks(BlockPos.ZERO, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
				ModBlocks.CRAFTERS_WORKBENCH).size() == 1, id + " should have one Crafter's Workbench");
		}
		boolean on = Crafters.ENABLED;
		try {
			Crafters.ENABLED = false;
			helper.assertTrue(!VillageHouses.houseNames().contains("crafters_workshop") && VillageHouses.houseNames().contains("jewellers_workshop"),
				"Crafters off: " + VillageHouses.houseNames());
		} finally {
			Crafters.ENABLED = on;
		}
		helper.succeed();
	}

	private static void expect(GameTestHelper helper, StewardRules.Rule rule, boolean held, long shortfalls, String when) {
		ServerLevel level = helper.getLevel();
		StewardWishes.Verdict verdict = StewardWishes.judge(rule, StewardConditions.Facts.of(level, helper.absolutePos(HALL)), StewardWishes.State.EMPTY,
			StewardWishes.day(level));
		StewardConditions.Check check = verdict.checks().get(0);
		helper.assertTrue((verdict.status() == StewardWishes.Status.HELD) == held && check.value() == shortfalls,
			when + ": the rule " + verdict.status() + " with " + check.value() + " shortfalls, expected " + (held ? "held" : "not held") + " with " + shortfalls);
	}

	private static BuildSite waiting(GameTestHelper helper, List<BuildSite> made, BlockPos at, Map<Item, Integer> missing) {
		BuildSite site = BuildSiteManager.get(helper.getLevel()).create(UUID.randomUUID(), "", AliveWorkplace.id("stone_house"), placement(helper, at));
		site.setStatus(BuildSite.Status.WAITING_FOR_MATERIALS);
		site.setMissing(new HashMap<>(missing));
		made.add(site);
		return site;
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos at) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(at), Rotation.NONE, Mirror.NONE);
	}
}
