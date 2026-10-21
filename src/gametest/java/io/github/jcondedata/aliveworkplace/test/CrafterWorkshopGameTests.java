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
 * ROADMAP 37.16a: the Crafter's Workshop retired with the Crafter (37.14a). The Blueprint Table no longer offers it, the
 * Steward no longer plans it, villages no longer grow it; one already built, bought or grown still loads by id and stands
 * as a plain building. The shortfall count the Steward's rule read is still kept (the village's edge, below).
 */
public class CrafterWorkshopGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation WORKSHOP = StarterBlueprints.CRAFTERS_WORKSHOP.id();

	/** Not in the Blueprint Table even with Milestone 37 open; still loads by id, named, and on the Village Map as a workshop. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theWorkshopIsNoLongerOfferedButStillLoads(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		helper.assertTrue(Expansions.isOpen("M37"), "Milestone 37 should be open in GameTests");
		helper.assertTrue(!StarterBlueprints.offered(WORKSHOP), "the retired Crafter's Workshop is still offered");
		helper.assertTrue(!BlueprintLibrary.list(server, false).contains(WORKSHOP), "the retired Crafter's Workshop is in the Blueprint Table");
		helper.assertTrue(StarterBlueprints.offered(StarterBlueprints.JEWELLERS_WORKSHOP.id()), "another job's workshop went with it");
		helper.assertTrue(BlueprintLibrary.get(server, WORKSHOP).isPresent(), "a Crafter's Workshop already bought no longer loads by id");
		helper.assertTrue(VillageMaps.kindOf(WORKSHOP).orElse(null) == VillageMaps.Kind.WORKSHOPS, "on the map as " + VillageMaps.kindOf(WORKSHOP));
		helper.assertTrue(net.minecraft.locale.Language.getInstance().has("blueprint.aliveworkplace.crafters_workshop"), "no blueprint name");
		helper.assertTrue(net.minecraft.locale.Language.getInstance().has("village_piece.aliveworkplace.crafters_workshop"), "no village house name");
		helper.succeed();
	}

	/**
	 * The Steward no longer plans it: no rule builds a Crafter's Workshop, and the parts-shortfall condition reads off
	 * (with its reason) however many crafted parts the builders wait for.
	 */
	//$ gametest_ticks_batch AREA '40' '"crafterWorkshopRule"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "crafterWorkshopRule")
	public void theStewardNoLongerPlansAWorkshop(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		List<StewardRules.Rule> building = StewardRules.all().stream().filter(r -> r.effect().blueprint().map(WORKSHOP::equals).orElse(false)).toList();
		helper.assertTrue(building.isEmpty(), "a Steward rule still builds the Crafter's Workshop: " + building.stream().map(StewardRules.Rule::id).toList());
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<BuildSite> made = new java.util.ArrayList<>();
		Leftovers.after(helper, () -> made.forEach(s -> sites.remove(s.id())));
		waiting(helper, made, new BlockPos(2, 2, 2), Map.of(Items.OAK_STAIRS, 6));
		waiting(helper, made, new BlockPos(16, 2, 2), Map.of(Items.OAK_SLAB, 8, Items.OAK_DOOR, 1));
		StewardConditions.Check check = new StewardConditions.BuildersShortOfParts(3).test(StewardConditions.Facts.of(level, hall));
		helper.assertTrue(!check.held(), "three crafted parts short and the retired workshop's condition holds: " + check);
		helper.succeed();
	}

	/**
	 * A build site counts toward the shortfalls out to the village's edge: one exactly {@link VillageHalls#RADIUS} blocks
	 * from the hall counts (the hall's census takes the same edge), one a block further doesn't.
	 */
	//$ gametest_ticks_batch AREA '40' '"crafterWorkshopEdge"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "crafterWorkshopEdge")
	public void aSiteAtTheVillagesEdgeCountsOneBeyondItDoesnt(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		BlockPos hall = helper.absolutePos(HALL);
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		List<BuildSite> made = new java.util.ArrayList<>();
		Leftovers.after(helper, () -> made.forEach(s -> sites.remove(s.id())));
		helper.assertTrue(StewardConditions.BuildersShortOfParts.shortfalls(helper.getLevel(), hall) == 0, "shortfalls before any site");
		waiting(helper, made, HALL.offset(0, 0, 16), Map.of(Items.OAK_STAIRS, 6));
		long atEdge = StewardConditions.BuildersShortOfParts.shortfalls(helper.getLevel(), hall);
		helper.assertTrue(atEdge == 1, "a site exactly 16 blocks from the hall (the village's edge) should count once, counted " + atEdge);
		waiting(helper, made, HALL.offset(17, 0, 0), Map.of(Items.OAK_SLAB, 8));
		long beyond = StewardConditions.BuildersShortOfParts.shortfalls(helper.getLevel(), hall);
		helper.assertTrue(beyond == 1, "a site 17 blocks from the hall is outside the village and shouldn't count: " + beyond + " shortfalls");
		helper.succeed();
	}

	/** Villages no longer grow the workshop (37.16a); its pieces stay, so a workshop a village already grew still loads. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void villagesNoLongerGrowTheWorkshop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(!VillageHouses.houseNames().contains("crafters_workshop") && VillageHouses.houseNames().contains("jewellers_workshop"),
			"the houses villages grow: " + VillageHouses.houseNames());
		var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		for (String style : VillageHouses.STYLES) {
			ResourceLocation id = AliveWorkplace.id("village/" + style + "_crafters_workshop");
			StructureTemplatePool pool = pools.get(VillageHouses.housePool(style));
			boolean listed = ((StructureTemplatePoolAccessor) pool).aliveworkplace$rawTemplates().stream()
				.anyMatch(p -> p.getFirst().toString().matches(".*" + java.util.regex.Pattern.quote(id.toString()) + "(?![a-z_0-9]).*"));
			helper.assertTrue(!listed, style + " villages can still grow a Crafter's Workshop");
			helper.assertTrue(level.getStructureManager().get(id).isPresent(), "the " + style + " workshop's piece is gone: " + id);
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
