package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.UpgradeOffers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * Basements (ROADMAP 26.15): a Level 4 digs its basement under a finished Level III on real ground, a Level 1 build with
 * a cellar (the Butcher's Shop and its freezer) goes up from bare ground, a site saved and loaded mid-dig carries on
 * (its foundation never fills the dug room), a builder with no materials still digs and leaves a chest in the way, and
 * the "leave the ground" switch leaves the landscaping but not the cellar. Every area here is earth five deep over its
 * floor (y 2-6, grass on top), the builds standing on it at y 7.
 */
public class BasementGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final String TALL_AREA = "aliveworkplace_test:tall_area";
	/** The top of the ground laid in the area (grass); builds stand on it. */
	private static final int GROUND = 6;
	private static final BlockPos BENCH = new BlockPos(2, GROUND + 1, 2);
	private static final BlockPos CHEST = new BlockPos(2, GROUND + 1, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, GROUND + 1, 3);

	private record Setup(ServerLevel level, Villager[] builder, BuildSite site, BuildPlan plan, Blueprint blueprint) {
		Villager villager() {
			return builder[0];
		}
	}

	// --- pure logic ---------------------------------------------------------------------------------------------------

	/**
	 * Every build with a basement loads with it under its y = 0: the library's blueprint reaches {@code basement} down,
	 * its size is the part above the ground (what StarterBlueprints says), the file holds both, and a plan's box takes
	 * the basement in while the build still stands on the clicked block.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void basementsLoadUnderTheGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(StarterBlueprints.LEVEL_4.size() >= 8, "only " + StarterBlueprints.LEVEL_4.size() + " Level 4s");
		for (StarterBlueprints.Entry entry : StarterBlueprints.WITH_BASEMENTS) {
			Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow(() -> new GameTestAssertException("missing " + entry.id()));
			helper.assertTrue(entry.basement() > 0, entry.id() + " is listed with a basement of " + entry.basement());
			helper.assertTrue(blueprint.basement() == entry.basement(), entry.id() + " reaches " + blueprint.basement() + " down, not " + entry.basement());
			helper.assertTrue(blueprint.size().equals(entry.size()), entry.id() + " is " + blueprint.size() + " above the ground, not " + entry.size());
			int fileHeight = level.getStructureManager().get(entry.id()).orElseThrow().getSize().getY();
			helper.assertTrue(fileHeight == entry.size().getY() + entry.basement(), entry.id() + "'s file is " + fileHeight + " high");
			BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), new BlockPos(0, 100, 0), Rotation.CLOCKWISE_90, Mirror.NONE);
			BoundingBox box = BuildPlan.create(blueprint, at).bounds();
			helper.assertTrue(box.minY() == 100 - entry.basement() && box.maxY() == 100 + entry.size().getY() - 1, entry.id() + "'s plan spans " + box);
			// The basement has a way down: a ladder or stairs below the ground floor
			helper.assertTrue(blueprint.blocks().stream().anyMatch(e -> e.pos().getY() < 0
					&& (e.state().is(Blocks.LADDER) || e.state().is(net.minecraft.tags.BlockTags.STAIRS))),
				entry.id() + " has no ladder or stairs down into its basement");
			helper.assertTrue(blueprint.blocks().stream().anyMatch(e -> e.pos().getY() < 0 && e.state().is(Blocks.LANTERN)),
				entry.id() + "'s basement has no light");
		}
		// The Level 4s upgrade their Level III, and a builder who finished a Level III sells its Level 4
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		for (StarterBlueprints.Entry four : StarterBlueprints.LEVEL_4) {
			ResourceLocation three = BlueprintUpgrades.baseOf(four.id()).orElseThrow();
			helper.assertTrue(three.getPath().endsWith("_3") && BlueprintLibrary.get(level, three).isPresent(), four.id() + " upgrades " + three);
			helper.assertTrue(UpgradeOffers.offer(level, builder, three).equals(Optional.of(four.id())), "a builder who built " + three + " doesn't sell " + four.id());
		}
		builder.discard();
		helper.succeed();
	}

	// --- building -----------------------------------------------------------------------------------------------------

	/**
	 * Starter Cottage IV clicked onto a finished Cottage III on real ground: the builder digs the root cellar out of the
	 * earth, walls and floors it, puts in the ladder, the bins and the lights, and leaves the cottage as it was.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"basement_cottage"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "basement_cottage")
	public void levelFourDigsAndFinishesItsBasement(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		BlockPos origin = new BlockPos(8, GROUND + 1, 9);
		BlueprintData.Placement placement = placement(helper, origin);
		level.getStructureManager().get(StarterBlueprints.STARTER_COTTAGE_3.id()).orElseThrow()
			.placeInWorld(level, helper.absolutePos(origin), helper.absolutePos(origin), new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.STARTER_COTTAGE_3.id(), placement, java.util.UUID.randomUUID());
		ResourceLocation four = StarterBlueprints.STARTER_COTTAGE_4.id();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH); // the report counts what's in reach of the bench
		var report = io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.check(level,
				new BlueprintData(four, Optional.of(StarterBlueprints.STARTER_COTTAGE_4.size()), Optional.of(placement)))
			.orElseThrow(() -> new GameTestAssertException("no supply report"));
		List<ItemStack> needed = new ArrayList<>();
		report.missing().forEach(m -> needed.addAll(stacks(m.item(), m.count())));
		Setup s = setup(helper, four, origin, needed);
		int total = s.plan().steps(BuildPlan.Stage.STRUCTURE).size() + s.plan().steps(BuildPlan.Stage.DECORATION).size();
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			assertBasementEmpty(helper, s);
			helper.assertBlockPresent(Blocks.RED_BED, origin.offset(7, 1, 6)); // the cottage's own, kept
			helper.assertBlockPresent(Blocks.LADDER, origin.offset(8, -2, 3));
			helper.assertBlockPresent(Blocks.PACKED_MUD, origin.offset(5, -4, 5));
			helper.assertTrue(s.site().placed() < total * 0.8, "placed " + s.site().placed() + " of " + total + ": the cottage should have been kept");
		});
	}

	/** The Butcher's Shop from bare ground, stocked with exactly what it takes: the shop, the pen and the ice-lined freezer. */
	//$ gametest_ticks_batch HUGE_AREA '24000' '"basement_butcher"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "basement_butcher")
	public void butchersShopGoesUpWithItsFreezer(GameTestHelper helper) {
		Leftovers.clear(helper);
		ground(helper, 30);
		BlockPos origin = new BlockPos(9, GROUND + 1, 9);
		Setup s = setup(helper, StarterBlueprints.BUTCHERS_SHOP.id(), origin, materials(helper, StarterBlueprints.BUTCHERS_SHOP.id(), origin));
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			assertBasementEmpty(helper, s);
			helper.assertBlockPresent(Blocks.SMOKER, origin.offset(8, 1, 5));
			helper.assertBlockPresent(Blocks.BLUE_ICE, origin.offset(5, -4, 5));
			helper.assertBlockPresent(Blocks.LADDER, origin.offset(3, -1, 3));
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped");
		});
	}

	/**
	 * Saved and loaded twice, mid-dig and once the structure is going up (the site from its save, the plan worked out
	 * again from the ground as it stands, the builder from its save): the Storehouse and its vault still finish, nothing
	 * skipped, and the foundation worked out after the dig never fills the dug room.
	 */
	//$ gametest_ticks_batch HUGE_AREA '24000' '"basement_reload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "basement_reload")
	public void reloadMidDigCarriesOn(GameTestHelper helper) {
		Leftovers.clear(helper);
		ground(helper, 30);
		BlockPos origin = new BlockPos(9, GROUND + 1, 9);
		ResourceLocation id = StarterBlueprints.STOREHOUSE.id();
		Setup s = setup(helper, id, origin, materials(helper, id, origin));
		int clearSteps = s.plan().steps(BuildPlan.Stage.CLEAR).size();
		boolean[] reloaded = {false, false};
		helper.onEachTick(() -> {
			BuildSite site = s.site();
			boolean midDig = !reloaded[0] && site.stage() == BuildPlan.Stage.CLEAR && site.cursor() > clearSteps / 2;
			boolean building = reloaded[0] && !reloaded[1] && site.stage() == BuildPlan.Stage.STRUCTURE && site.cursor() > 10;
			if (midDig || building) {
				reloaded[midDig ? 0 : 1] = true;
				reload(helper, s);
				BuildPlan again = site.plan(s.level());
				helper.assertTrue(again != null && again != s.plan(), "the plan wasn't worked out again");
				for (BuildPlan.Step step : again.steps(BuildPlan.Stage.FOUNDATION)) {
					helper.assertFalse(inCellar(s, step.pos()), "the foundation fills "
						+ step.pos().subtract(site.placement().origin()).toShortString() + ", a spot of the build itself");
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0] && reloaded[1], "not reloaded twice: " + reloaded[0] + ", " + reloaded[1]);
			assertBuilt(helper, s);
			assertBasementEmpty(helper, s);
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped after the reloads");
			helper.assertBlockPresent(Blocks.CHEST, origin.offset(6, -3, 4));
		});
	}

	/**
	 * A builder with nothing to build the Smithy with still digs its coal store (clearing never waits for materials),
	 * then waits for them; a chest someone left in the ground where the cellar goes stays where it is, its contents too,
	 * and so does a barrel buried against the cellar's wall outside.
	 */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"basement_missing"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "basement_missing")
	public void noMaterialsStillDigsAndKeepsContainers(GameTestHelper helper) {
		Leftovers.clear(helper);
		ground(helper, 30);
		BlockPos origin = new BlockPos(9, GROUND + 1, 9);
		BlockPos chest = origin.offset(5, -2, 5); // in the coal store's room
		BlockPos barrel = origin.offset(5, -2, 9); // outside its back wall (z 8)
		helper.setBlock(chest, Blocks.CHEST);
		((Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 3));
		helper.setBlock(barrel, Blocks.BARREL);
		((Container) helper.getBlockEntity(barrel)).setItem(0, new ItemStack(Items.EMERALD, 2));
		Setup s = setup(helper, StarterBlueprints.SMITHY.id(), origin, List.of());
		BlockPos dug = origin.offset(3, -2, 5);
		helper.succeedWhen(() -> {
			helper.assertTrue(s.site().stage().ordinal() > BuildPlan.Stage.CLEAR.ordinal(), "still clearing: " + s.site().stage());
			helper.assertTrue(s.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "not waiting for materials: " + s.site().status());
			helper.assertBlockPresent(Blocks.AIR, dug);
			helper.assertBlockPresent(Blocks.CHEST, chest);
			helper.assertTrue(((Container) helper.getBlockEntity(chest)).countItem(Items.DIAMOND) == 3, "the chest in the way was emptied");
			helper.assertBlockPresent(Blocks.BARREL, barrel);
			helper.assertTrue(((Container) helper.getBlockEntity(barrel)).countItem(Items.EMERALD) == 2, "the barrel by the wall was emptied");
		});
	}

	/**
	 * The blueprint's "leave the ground" switch off, and no foundation: the Kitchen's cold larder is still dug and built
	 * (it is the build, not the ground round it), while a mound beside the kitchen stays.
	 */
	//$ gametest_ticks_batch HUGE_AREA '20000' '"basement_switches"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 20000, batch = "basement_switches")
	public void cellarIsBuiltWithTheGroundLeftAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		ground(helper, 30);
		BlockPos origin = new BlockPos(9, GROUND + 1, 9);
		BlockPos mound = origin.offset(-2, 0, 6);
		helper.setBlock(mound, Blocks.DIRT);
		Setup site = setup(helper, StarterBlueprints.KITCHEN.id(), origin, materials(helper, StarterBlueprints.KITCHEN.id(), origin));
		site.site().setLevelGround(false);
		site.site().setFoundation(false);
		helper.succeedWhen(() -> {
			assertBuilt(helper, site);
			assertBasementEmpty(helper, site);
			helper.assertBlockPresent(Blocks.LADDER, origin.offset(2, -1, 4));
			helper.assertBlockPresent(Blocks.DIRT, mound);
		});
	}

	// --- helpers ------------------------------------------------------------------------------------------------------

	/** Earth over the area's floor: stone, dirt, then grass at {@link #GROUND}, {@code size} blocks each way. */
	private static void ground(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				for (int y = 2; y <= GROUND; y++) {
					helper.setBlock(new BlockPos(x, y, z), y < 4 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK);
				}
			}
		}
	}

	private static Setup setup(GameTestHelper helper, ResourceLocation structure, BlockPos originRel, List<ItemStack> stock) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		List<BlockPos> stores = new ArrayList<>(List.of(CHEST));
		for (int i = 0; i * 27 < Math.max(1, stock.size()) - 27; i++) {
			stores.add(new BlockPos(1 + i % 6, GROUND + 1, 5 + i / 6)); // barrels beside the chest, as many as the stock needs
		}
		for (int i = 0; i < stores.size(); i++) {
			helper.setBlock(stores.get(i), i == 0 ? Blocks.CHEST : Blocks.BARREL);
			Container box = helper.getBlockEntity(stores.get(i));
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				box.setItem(slot, stock.get(i * 27 + slot).copy());
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, structure, placement(helper, originRel));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + structure);
		}
		return new Setup(level, new Villager[]{villager}, site, plan, BlueprintLibrary.get(level, structure).orElseThrow());
	}

	/** Exactly what {@code id} takes to build from scratch at {@code originRel}, in stacks. */
	private static List<ItemStack> materials(GameTestHelper helper, ResourceLocation id, BlockPos originRel) {
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), id).orElseThrow();
		List<ItemStack> out = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : BuildPlan.create(blueprint, placement(helper, originRel)).materials().entrySet()) {
			out.addAll(stacks(e.getKey(), e.getValue()));
		}
		return out;
	}

	private static List<ItemStack> stacks(Item item, int count) {
		List<ItemStack> out = new ArrayList<>();
		for (int left = count; left > 0; left -= item.getDefaultMaxStackSize()) {
			out.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
		}
		return out;
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos originRel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(originRel), Rotation.NONE, Mirror.NONE);
	}

	/** As a save and a load would: the site from its save (and the same again), its plan worked out again, the builder from its save. */
	private static void reload(GameTestHelper helper, Setup s) {
		CompoundTag saved = s.site().save();
		BuildSite back = BuildSite.load(saved);
		helper.assertTrue(back != null && back.save().equals(saved), "the site didn't come back from its save as it was");
		helper.assertTrue(back.stage() == s.site().stage() && back.cursor() == s.site().cursor(), "the site came back at "
			+ back.stage() + " " + back.cursor() + ", not " + s.site().stage() + " " + s.site().cursor());
		s.site().setFoundation(s.site().foundation()); // the plan is worked out again, from the ground as it stands now
		Villager old = s.villager();
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, s.level(), e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		s.level().addFreshEntity(loaded);
		s.builder()[0] = (Villager) loaded;
	}

	private static boolean inCellar(Setup s, BlockPos world) {
		BlockPos rel = world.subtract(s.site().placement().origin());
		return s.blueprint().blocks().stream().anyMatch(e -> e.pos().equals(rel));
	}

	private static void assertBuilt(GameTestHelper helper, Setup s) {
		helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null,
			"still building: stage=" + s.site().stage() + " status=" + s.site().status() + " progress=" + Math.round(s.site().progress(s.plan()) * 100)
				+ "% missing=" + s.site().missing() + " builder at " + helper.relativePos(s.villager().blockPosition()));
		List<BlockPos> unfinished = s.plan().unfinished(s.level());
		if (!unfinished.isEmpty()) {
			String sample = unfinished.stream().limit(5)
				.map(p -> p.subtract(s.site().placement().origin()).toShortString() + "=" + s.level().getBlockState(p))
				.collect(Collectors.joining(", "));
			throw new GameTestAssertException(unfinished.size() + " block(s) wrong after the build, e.g. " + sample);
		}
	}

	/** Every spot the basement's drawing leaves empty is empty: dug out, nothing filled back in. */
	private static void assertBasementEmpty(GameTestHelper helper, Setup s) {
		BlockPos origin = s.site().placement().origin();
		List<String> full = new ArrayList<>();
		for (Blueprint.Entry e : s.blueprint().blocks()) {
			if (e.pos().getY() < 0 && e.state().isAir()) {
				BlockState world = s.level().getBlockState(origin.offset(e.pos()));
				if (!world.isAir()) {
					full.add(e.pos().toShortString() + "=" + world);
				}
			}
		}
		helper.assertTrue(full.isEmpty(), full.size() + " spot(s) of the basement not dug out, e.g. " + full.stream().limit(4).toList());
	}
}
