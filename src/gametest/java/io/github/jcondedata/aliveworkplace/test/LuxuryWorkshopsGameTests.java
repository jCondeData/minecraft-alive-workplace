package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mixin.VillagerAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModTrades;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.tailor.Tailors;
import io.github.jcondedata.aliveworkplace.vintner.Vintners;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import io.github.jcondedata.aliveworkplace.world.VillagePieces;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 34.13, the Winery and the Tailor's Shop: both blueprints and their II in the Blueprint Table and built by a
 * builder, the Vintner taking the cauldron and the Tailor the loom; their {@code workplace_*} Steward rules in 27.11's
 * form; the blueprint a Journeyman of each sells; and the village houses' switches. (The village pieces themselves and
 * their workers are in {@code VillageGameTests}.)
 */
public class LuxuryWorkshopsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";

	static final List<WorkplacesGameTests.Case> CASES = List.of(
		new WorkplacesGameTests.Case("workplace_winery", "aliveworkplace:vintner", "winery", "workshops", VillageRanks.Rank.HAMLET, false),
		new WorkplacesGameTests.Case("workplace_tailors_shop", "aliveworkplace:tailor", "tailors_shop", "market", VillageRanks.Rank.HAMLET, false));

	/** Each rule: a Vintner (a Tailor) without a workstation, and the Steward asks for the Winery (the Tailor's Shop) in its zone. */
	@GameTestGenerator
	public Collection<TestFunction> rules() {
		List<TestFunction> out = new ArrayList<>();
		for (WorkplacesGameTests.Case c : CASES) {
			String name = "luxuryWorkshopRule_" + c.blueprint();
			out.add(new TestFunction(name, name, AREA, 60, 0, true, helper -> WorkplacesGameTests.rule(helper, c)));
		}
		return out;
	}

	static List<CraftWorkplacesGameTests.Workplace> workplaces() {
		CraftWorkplacesGameTests.Worker vintner = new CraftWorkplacesGameTests.Worker("aliveworkplace:vintner", Blocks.CAULDRON);
		CraftWorkplacesGameTests.Worker tailor = new CraftWorkplacesGameTests.Worker("aliveworkplace:tailor", Blocks.LOOM);
		return List.of(
			new CraftWorkplacesGameTests.Workplace(StarterBlueprints.WINERY, List.of(vintner)),
			new CraftWorkplacesGameTests.Workplace(StarterBlueprints.WINERY_2, List.of(vintner)),
			new CraftWorkplacesGameTests.Workplace(StarterBlueprints.TAILORS_SHOP, List.of(tailor)),
			new CraftWorkplacesGameTests.Workplace(StarterBlueprints.TAILORS_SHOP_2, List.of(tailor)));
	}

	/**
	 * Each of the four built by a builder from the chests' materials; then its worker walks in from the street and takes
	 * its one job block (the Vintner up the outside stair to the vat). No other job block in it: no barrel above all.
	 */
	@GameTestGenerator
	public Collection<TestFunction> builds() {
		List<TestFunction> out = new ArrayList<>();
		for (CraftWorkplacesGameTests.Workplace w : workplaces()) {
			String name = "luxuryWorkshopBuilt_" + w.entry().id().getPath();
			out.add(new TestFunction(name, name, HUGE_AREA, 12000, 0, true, helper -> CraftWorkplacesGameTests.build(helper, w)));
		}
		return out;
	}

	/** The four are in the Blueprint Table, as two buildings of two tiers, named, and on the Village Map as workshops. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void luxuryWorkshopsAreInTheBlueprintTable(GameTestHelper helper) {
		List<ResourceLocation> library = BlueprintLibrary.list(helper.getLevel().getServer(), false);
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.WINERY, StarterBlueprints.WINERY_2, StarterBlueprints.TAILORS_SHOP, StarterBlueprints.TAILORS_SHOP_2)) {
			helper.assertTrue(library.contains(e.id()), e.id() + " isn't in the Blueprint Table");
			helper.assertTrue(StarterBlueprints.ALL.contains(e), e.id() + " isn't a starter blueprint");
			helper.assertTrue(VillageMaps.kindOf(e.id()).orElse(null) == VillageMaps.Kind.WORKSHOPS, e.id() + " is on the map as " + VillageMaps.kindOf(e.id()));
			String key = "blueprint.aliveworkplace." + e.id().getPath();
			helper.assertTrue(net.minecraft.locale.Language.getInstance().has(key), "no name for " + e.id());
		}
		for (String house : List.of("winery", "tailors_shop")) {
			helper.assertTrue(net.minecraft.locale.Language.getInstance().has("village_piece.aliveworkplace." + house), "no name for the village's " + house);
		}
		helper.succeed();
	}

	/**
	 * What the item asks of the drawings: the Winery has casks (spruce logs laid on their sides), a cellar of chests under
	 * the press room (below the vat) and no barrel; its II a garden of sweet berries and glow berries on the pergola.
	 * The Tailor's Shop has bolts of wool in several colours; its II more of them a storey up, and a ladder.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theBuildsHaveWhatTheirTradesNeed(GameTestHelper helper) {
		Blueprint winery = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.WINERY.id()).orElseThrow();
		Blueprint winery2 = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.WINERY_2.id()).orElseThrow();
		Blueprint shop = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.TAILORS_SHOP.id()).orElseThrow();
		Blueprint shop2 = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.TAILORS_SHOP_2.id()).orElseThrow();
		for (Blueprint b : List.of(winery, winery2)) {
			int vat = b.blocks().stream().filter(e -> e.state().is(Blocks.CAULDRON)).mapToInt(e -> e.pos().getY()).findFirst().orElse(-1);
			long cellarChests = b.blocks().stream().filter(e -> e.state().is(Blocks.CHEST) && e.pos().getY() < vat - 1).count();
			long casks = b.blocks().stream().filter(e -> e.state().is(Blocks.SPRUCE_LOG)
				&& e.state().getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS).isHorizontal() && e.pos().getY() < vat - 1).count();
			helper.assertTrue(vat >= 3 && cellarChests >= 4, "the cellar has " + cellarChests + " chests under a vat at y " + vat);
			helper.assertTrue(casks >= 12, "only " + casks + " casks down in the cellar");
			helper.assertTrue(count(b, Blocks.BARREL.defaultBlockState()) == 0, "a barrel in the Winery: the fisherman's job block");
		}
		helper.assertTrue(count(winery, Blocks.SWEET_BERRY_BUSH.defaultBlockState()) == 0 && count(winery2, Blocks.SWEET_BERRY_BUSH.defaultBlockState()) >= 16,
			"the Winery II's garden has " + count(winery2, Blocks.SWEET_BERRY_BUSH.defaultBlockState()) + " berry bushes");
		long glow = winery2.blocks().stream().filter(e -> e.state().is(Blocks.CAVE_VINES) && e.state().getValue(net.minecraft.world.level.block.CaveVines.BERRIES)).count();
		helper.assertTrue(glow >= 6, "the pergola has " + glow + " glow berry vines");
		long colours = shop.blocks().stream().filter(e -> e.state().is(net.minecraft.tags.BlockTags.WOOL)).map(e -> e.state().getBlock()).distinct().count();
		long bolts = shop.blocks().stream().filter(e -> e.state().is(net.minecraft.tags.BlockTags.WOOL)).count();
		long bolts2 = shop2.blocks().stream().filter(e -> e.state().is(net.minecraft.tags.BlockTags.WOOL)).count();
		helper.assertTrue(colours >= 6 && bolts >= 8, "the Tailor's Shop has " + bolts + " bolts of wool in " + colours + " colours");
		helper.assertTrue(bolts2 >= bolts + 12, "the Tailor's Shop II has only " + bolts2 + " bolts, the shop " + bolts);
		helper.assertTrue(count(shop, Blocks.LADDER.defaultBlockState()) == 0 && shop2.blocks().stream().filter(e -> e.state().is(Blocks.LADDER)).count() >= 9,
			"the Tailor's Shop II has no ladder through both floors");
		helper.succeed();
	}

	private static long count(Blueprint b, BlockState like) {
		return b.blocks().stream().filter(e -> e.state().is(like.getBlock())).count();
	}

	/**
	 * A Vintner who becomes a Journeyman sells the Winery's blueprint from then on, a Tailor the Tailor's Shop's, each
	 * for 12 emeralds and once only, with the level's own trades still there; an Apprentice doesn't yet, and a Shepherd
	 * at the same loom never does.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void journeymenSellTheirBuildingsBlueprint(GameTestHelper helper) {
		record Seller(VillagerProfession job, StarterBlueprints.Entry sells) {
		}
		int x = 0;
		for (Seller s : List.of(new Seller(ModVillagers.VINTNER, StarterBlueprints.WINERY), new Seller(ModVillagers.TAILOR, StarterBlueprints.TAILORS_SHOP),
			new Seller(VillagerProfession.SHEPHERD, null))) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x++, 2, 0));
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().setProfession(s.job()).setLevel(1));
			v.setVillagerXp(1);
			helper.assertTrue(blueprints(v).isEmpty(), "a Novice " + s.job() + " sells " + blueprints(v));
			((VillagerAccessor) v).aliveworkplace$increaseMerchantCareer();
			helper.assertTrue(v.getVillagerData().getLevel() == 2 && blueprints(v).isEmpty(), "an Apprentice " + s.job() + " sells " + blueprints(v));
			int before = v.getOffers().size();
			((VillagerAccessor) v).aliveworkplace$increaseMerchantCareer();
			helper.assertTrue(v.getVillagerData().getLevel() == 3, s.job() + " is level " + v.getVillagerData().getLevel());
			if (s.sells() == null) {
				helper.assertTrue(blueprints(v).isEmpty(), "a Journeyman " + s.job() + " sells " + blueprints(v));
			} else {
				helper.assertTrue(blueprints(v).equals(List.of(s.sells().id())), "a Journeyman " + s.job() + " sells " + blueprints(v));
				helper.assertTrue(v.getOffers().size() == before + 3, "a Journeyman " + s.job() + " got " + (v.getOffers().size() - before) + " new trades, not his two and the blueprint");
				var offer = v.getOffers().stream().filter(o -> o.getResult().is(io.github.jcondedata.aliveworkplace.registry.ModItems.BLUEPRINT)).findFirst().orElseThrow();
				helper.assertTrue(offer.getCostA().is(Items.EMERALD) && offer.getCostA().getCount() == ModTrades.LUXURY_BLUEPRINT_PRICE,
					"the blueprint costs " + offer.getCostA());
				helper.assertTrue(BlueprintItem.data(offer.getResult()).flatMap(d -> d.size()).map(size -> size.equals(s.sells().size())).orElse(false),
					"the blueprint sold doesn't know its size");
				ModTrades.journeymanBlueprint(v);
				helper.assertTrue(blueprints(v).size() == 1, "sold twice: " + blueprints(v));
				((VillagerAccessor) v).aliveworkplace$increaseMerchantCareer();
				helper.assertTrue(blueprints(v).size() == 1, "an Expert sells it twice: " + blueprints(v));
			}
			v.discard();
		}
		helper.succeed();
	}

	private static List<ResourceLocation> blueprints(Villager v) {
		return v.getOffers().stream().map(o -> BlueprintItem.data(o.getResult())).flatMap(java.util.Optional::stream).map(d -> d.structure()).toList();
	}

	/**
	 * Villages grow a Winery only while Vintners are on (config {@code vintners}) and a Tailor's Shop only while Tailors
	 * are (config {@code tailors}): off, the house is left out of the pools and of the hall's list of houses, the other
	 * stays; back on, both are there again.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void villagesGrowTheHousesOnlyWhileTheirJobsAreOn(GameTestHelper helper) {
		boolean vintners = Vintners.ENABLED;
		boolean tailors = Tailors.ENABLED;
		try {
			Vintners.ENABLED = true;
			Tailors.ENABLED = true;
			helper.assertTrue(VillageHouses.houseNames().containsAll(List.of("winery", "tailors_shop")), "both on: " + VillageHouses.houseNames());
			helper.assertTrue(VillagePieces.houses().containsAll(List.of("winery", "tailors_shop")), "both on, the hall's houses: " + VillagePieces.houses());
			Vintners.ENABLED = false;
			helper.assertTrue(!VillageHouses.houseNames().contains("winery") && VillageHouses.houseNames().contains("tailors_shop"),
				"vintners off: " + VillageHouses.houseNames());
			helper.assertTrue(!VillagePieces.houses().contains("winery"), "vintners off, the hall's houses: " + VillagePieces.houses());
			Vintners.ENABLED = true;
			Tailors.ENABLED = false;
			helper.assertTrue(VillageHouses.houseNames().contains("winery") && !VillageHouses.houseNames().contains("tailors_shop"),
				"tailors off: " + VillageHouses.houseNames());
		} finally {
			Vintners.ENABLED = vintners;
			Tailors.ENABLED = tailors;
		}
		helper.succeed();
	}
}
