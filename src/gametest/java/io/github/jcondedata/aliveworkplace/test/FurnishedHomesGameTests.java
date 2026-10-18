package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot.Kind;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 37.4 without any furniture mod: every home (the Starter Cottage, Stone House, Townhouse and Manor at every
 * level, the Artisan's House) and the Inn has furnished rooms drawn as furniture spots (a dining set, a bedroom with a
 * nightstand and a lamp, a sitting corner, shelves of crockery, a clock, pictures, blinds), they plan as the vanilla
 * build, and a builder builds the furnished Inn in plain vanilla with nothing skipped, waiting for a missing picture.
 */
public class FurnishedHomesGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation INN = AliveWorkplace.id("inn");
	/** The kinds every furnished room set has. */
	private static final Set<Kind> ROOMS = EnumSet.of(Kind.BED, Kind.CHAIR, Kind.TABLE, Kind.NIGHTSTAND, Kind.LAMP);
	/** And the full set: the houses of the Blueprint Table and the Manor have every piece the item names. */
	private static final Set<Kind> FULL = EnumSet.of(Kind.BED, Kind.CHAIR, Kind.TABLE, Kind.NIGHTSTAND, Kind.LAMP, Kind.COUCH,
		Kind.SHELF, Kind.CROCKERY, Kind.CLOCK, Kind.PICTURE, Kind.BLINDS);

	private static Map<String, Set<Kind>> homes() {
		Map<String, Set<Kind>> out = new LinkedHashMap<>();
		for (String home : List.of("starter_cottage", "townhouse", "manor")) {
			for (String tier : List.of("", "_2", "_3", "_4")) {
				out.put(home + tier, FULL);
			}
		}
		Set<Kind> noCouch = EnumSet.copyOf(FULL);
		noCouch.remove(Kind.COUCH); // the first Stone House has no room for one: its couch comes with the upper storey
		out.put("stone_house", noCouch);
		for (String tier : List.of("_2", "_3", "_4")) {
			out.put("stone_house" + tier, FULL);
		}
		out.put("inn", FULL);
		out.put("inn_2", FULL);
		Set<Kind> artisan = EnumSet.copyOf(ROOMS);
		artisan.add(Kind.COUCH);
		out.put("artisans_house", artisan);
		out.put("artisans_house_2", artisan);
		return out;
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyHomeHasItsFurnishedRooms(GameTestHelper helper) {
		Map<String, String> wrong = new TreeMap<>();
		homes().forEach((name, kinds) -> {
			Set<Kind> has = EnumSet.noneOf(Kind.class);
			BlueprintFurniture.of(AliveWorkplace.id(name)).forEach(s -> has.add(s.kind()));
			Set<Kind> missing = EnumSet.copyOf(kinds);
			missing.removeAll(has);
			if (!missing.isEmpty()) {
				wrong.put(name, "no " + missing);
			}
		});
		helper.assertTrue(wrong.isEmpty(), "homes without their furnished rooms: " + wrong);
		helper.succeed();
	}

	/** No furniture mod here: every furnished home plans as its vanilla build, its pieces in vanilla blocks. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withoutFurnitureModsEveryHomeIsVanilla(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			for (String name : homes().keySet()) {
				helper.assertTrue(FurnitureChoice.forNewBuild(AliveWorkplace.id(name)).isVanilla(), name + " is furnished with no furniture mod");
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/** The furnished Inn built in vanilla from chests of exactly its materials: every block right, nothing skipped. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"furnished_inn_vanilla"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "furnished_inn_vanilla")
	public void aBuilderBuildsTheFurnishedInnInVanilla(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuildSite site = start(helper, INN, null);
		ServerLevel level = helper.getLevel();
		BuildPlan plan = site.plan(level);
		BlueprintData.Placement placement = site.placement();
		List<FurnitureSpot> spots = BlueprintFurniture.of(INN);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(3)
				.map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			for (FurnitureSpot spot : spots) {
				for (FurnitureSpot.Cell cell : spot.blocks()) {
					BlockPos at = placement.origin().offset(spot.pos()).offset(cell.at());
					BlockState there = level.getBlockState(at);
					helper.assertTrue(there.is(cell.state().getBlock()), spot.kind() + " at " + spot.pos().offset(cell.at()).toShortString()
						+ ": " + BuiltInRegistries.BLOCK.getKey(there.getBlock()) + ", not " + BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()));
				}
			}
		});
	}

	/** Without the guest rooms' pictures (red banners) in the chests, the builder waits for them by name. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"furnished_inn_missing"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "furnished_inn_missing")
	public void theBuilderWaitsForAMissingPicture(GameTestHelper helper) {
		Leftovers.clear(helper);
		BuildSite site = start(helper, INN, Items.RED_BANNER);
		// Everything else is in the chests: once he has taken it all, the banners are the one thing he still waits for
		helper.succeedWhen(() -> helper.assertTrue(site.missing().keySet().equals(Set.of(Items.RED_BANNER)),
			"not waiting for the red banners alone: " + site.stage() + " " + site.status() + " missing " + site.missing()));
	}

	/** A builder on {@code id} at (9, 2, 3), his chests holding exactly what the plan costs, less {@code without}. */
	private static BuildSite start(GameTestHelper helper, ResourceLocation id, Item without) {
		ServerLevel level = helper.getLevel();
		new WorkplaceConfig().apply();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 3)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			if (e.getKey() == without) {
				continue;
			}
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
		return site;
	}
}
