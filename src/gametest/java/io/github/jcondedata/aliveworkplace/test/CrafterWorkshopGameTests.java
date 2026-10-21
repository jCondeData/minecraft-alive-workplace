package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
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
 * ROADMAP 37.16a: the Crafter's Workshop retired with the Crafter (37.14a). It's no longer in the Blueprint Table, the
 * Steward no longer plans one (its rule is gone and the shortfall condition never holds), and villages no longer grow it;
 * its blueprint and village structures stay, so workshops already built (and build sites already started) still load as
 * plain buildings. The old house's Crafter going jobless is VillageGameTests'.
 */
public class CrafterWorkshopGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation WORKSHOP = StarterBlueprints.CRAFTERS_WORKSHOP.id();

	/** Not in the Blueprint Table with Milestone 37 open (another job's workshop is); its names stay, for the ones built. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theWorkshopIsNoLongerOffered(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		helper.assertTrue(!Crafters.ENABLED, "the Crafter should be retired");
		helper.assertTrue(!StarterBlueprints.offered(WORKSHOP), "the Crafter's Workshop is still offered");
		helper.assertTrue(!BlueprintLibrary.list(server, false).contains(WORKSHOP), "the Crafter's Workshop is still in the Blueprint Table");
		helper.assertTrue(StarterBlueprints.offered(StarterBlueprints.JEWELLERS_WORKSHOP.id()), "another job's workshop went with it");
		helper.assertTrue(VillageMaps.kindOf(WORKSHOP).orElse(null) == VillageMaps.Kind.WORKSHOPS, "a built one is on the map as " + VillageMaps.kindOf(WORKSHOP));
		helper.assertTrue(net.minecraft.locale.Language.getInstance().has("blueprint.aliveworkplace.crafters_workshop"), "no blueprint name for the ones built");
		helper.assertTrue(net.minecraft.locale.Language.getInstance().has("village_piece.aliveworkplace.crafters_workshop"), "no village house name");
		helper.assertTrue(helper.getLevel().getStructureManager().get(WORKSHOP).isPresent(), "the blueprint's structure is gone: old build sites can't load");
		helper.succeed();
	}

	/**
	 * The Steward's plans: the Crafter's Workshop rule is gone, and the shortfall condition doesn't hold even with three
	 * crafted parts waited for over two sites (it would have before), saying the Crafter is retired.
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
		helper.assertTrue(StewardRules.all().stream().noneMatch(r -> r.id().equals(AliveWorkplace.id("workplace_crafters_workshop"))
			|| r.effect().blueprint().map(WORKSHOP::equals).orElse(false)), "a Steward's rule still builds the Crafter's Workshop");
		helper.assertTrue(!StewardRules.all().isEmpty(), "no Steward's rules loaded at all");
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<BuildSite> made = new java.util.ArrayList<>();
		Leftovers.after(helper, () -> made.forEach(s -> sites.remove(s.id())));
		waiting(helper, made, new BlockPos(2, 2, 2), Map.of(Items.OAK_STAIRS, 6));
		waiting(helper, made, new BlockPos(16, 2, 2), Map.of(Items.OAK_SLAB, 8, Items.OAK_DOOR, 1));
		helper.assertTrue(StewardConditions.BuildersShortOfParts.shortfalls(level, hall) == 3, "setup: three crafted parts should be waited for");
		StewardConditions.Check check = new StewardConditions.BuildersShortOfParts(3).test(StewardConditions.Facts.of(level, hall));
		helper.assertTrue(!check.held() && check.shown().getString().contains("retired"), "the condition with the Crafter retired: " + check);
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

	/** Villages no longer grow the Crafter's Workshop in any style; its structures stay, workbench and all, for the ones grown. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void villagesNoLongerGrowTheWorkshop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(!VillageHouses.houseNames().contains("crafters_workshop") && VillageHouses.houseNames().contains("jewellers_workshop"),
			"the village houses: " + VillageHouses.houseNames());
		var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		for (String style : VillageHouses.STYLES) {
			ResourceLocation id = AliveWorkplace.id("village/" + style + "_crafters_workshop");
			StructureTemplatePool pool = pools.get(VillageHouses.housePool(style));
			boolean listed = ((StructureTemplatePoolAccessor) pool).aliveworkplace$rawTemplates().stream()
				.anyMatch(p -> p.getFirst().toString().matches(".*" + java.util.regex.Pattern.quote(id.toString()) + "(?![a-z_0-9]).*"));
			helper.assertTrue(!listed, style + " villages still grow a Crafter's Workshop");
			var template = level.getStructureManager().get(id).orElseThrow(() -> new GameTestAssertException("missing " + id + ": old villages' pieces can't load"));
			helper.assertTrue(template.filterBlocks(BlockPos.ZERO, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
				ModBlocks.CRAFTERS_WORKBENCH).size() == 1, id + " should still have its Crafter's Workbench");
		}
		helper.succeed();
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
