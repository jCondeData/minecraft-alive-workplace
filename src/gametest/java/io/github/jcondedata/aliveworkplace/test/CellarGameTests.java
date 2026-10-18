package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Cellars;
import io.github.jcondedata.aliveworkplace.build.UpgradeOffers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * Cellars (ROADMAP 36.7): the eight Level IIIs line up over their Level IIs, three of them are dug and built over a
 * finished Level II on real ground, and a Smithy finished before it had a cellar (its basement still ground) is offered
 * "Dig the cellar", whose builder digs and fits out only the cellar (and carries on after a save and a load); a Smithy
 * with its cellar isn't offered it. Every area here is earth five deep over its floor (y 2-6, grass on top), the builds
 * standing on it at y 7.
 */
public class CellarGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final int GROUND = 6;
	private static final BlockPos BENCH = new BlockPos(2, GROUND + 1, 2);
	private static final BlockPos CHEST = new BlockPos(2, GROUND + 1, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, GROUND + 1, 3);

	private record Setup(ServerLevel level, Villager[] builder, BuildSite site, BuildPlan plan, Blueprint blueprint) {
		Villager villager() {
			return builder[0];
		}
	}

	// --- data ---------------------------------------------------------------------------------------------------------

	/**
	 * All eight Level IIIs: each upgrades its Level II by name, has the same footprint and height above the ground, keeps
	 * every block of it but the hatch over its ladder (so the workers' chests and job blocks stay where they were), is
	 * listed with its basement, unlocks as a managed build, and a builder who finished the Level II sells it.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void levelThreesLineUpOverTheirLevelTwos(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(StarterBlueprints.LEVEL_3_CELLARS.size() == 8, StarterBlueprints.LEVEL_3_CELLARS.size() + " cellared Level IIIs");
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		for (StarterBlueprints.Entry three : StarterBlueprints.LEVEL_3_CELLARS) {
			ResourceLocation two = BlueprintUpgrades.baseOf(three.id()).orElseThrow();
			helper.assertTrue(two.getPath().endsWith("_2"), three.id() + " upgrades " + two);
			Blueprint b3 = BlueprintLibrary.get(level, three.id()).orElseThrow(() -> new GameTestAssertException("missing " + three.id()));
			Blueprint b2 = BlueprintLibrary.get(level, two).orElseThrow(() -> new GameTestAssertException("missing " + two));
			helper.assertTrue(StarterBlueprints.WITH_BASEMENTS.contains(three) && b3.basement() == 4, three.id() + " reaches " + b3.basement() + " down");
			helper.assertTrue(b3.size().equals(b2.size()), three.id() + " is " + b3.size() + ", its Level II " + b2.size());
			helper.assertTrue(StarterBlueprints.ALL.contains(three) && BlueprintUnlocks.managed(three.id()), three.id() + " isn't a listed, lockable build");
			Map<BlockPos, BlockState> above = new HashMap<>();
			for (Blueprint.Entry e : b3.blocks()) {
				if (e.pos().getY() >= 0) {
					above.put(e.pos(), e.state());
				}
			}
			Set<Long> cellarColumns = new HashSet<>();
			b3.blocks().stream().filter(e -> e.pos().getY() < 0).forEach(e -> cellarColumns.add(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())));
			List<String> changed = new ArrayList<>();
			for (Blueprint.Entry e : b2.blocks()) {
				BlockState now = above.get(e.pos());
				if (now == null || !now.equals(e.state())) {
					boolean hatch = e.pos().getY() == 0 && now != null && now.is(BlockTags.TRAPDOORS)
						&& cellarColumns.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ()));
					if (!hatch) {
						changed.add(e.pos().toShortString() + " " + e.state() + " -> " + now);
					}
				}
			}
			helper.assertTrue(changed.isEmpty(), three.id() + " changes " + changed.size() + " block(s) of its Level II, e.g. " + changed.stream().limit(3).toList());
			helper.assertTrue(b3.blocks().stream().anyMatch(e -> e.pos().getY() == 0 && e.state().is(BlockTags.TRAPDOORS)
				&& cellarColumns.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ()))), three.id() + " has no hatch down into its cellar");
			helper.assertTrue(UpgradeOffers.offer(level, builder, two).equals(Optional.of(three.id())), "a builder who built " + two + " doesn't sell " + three.id());
		}
		builder.discard();
		helper.succeed();
	}

	// --- three built over their Level II --------------------------------------------------------------------------------

	//$ gametest_ticks_batch HUGE_AREA '24000' '"cellar_barracks"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "cellar_barracks")
	public void barracksThreeDigsItsArmoury(GameTestHelper helper) {
		levelThreeOverLevelTwo(helper, StarterBlueprints.BARRACKS_2, StarterBlueprints.BARRACKS_3);
	}

	//$ gametest_ticks_batch HUGE_AREA '24000' '"cellar_lab"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "cellar_lab")
	public void researchLabThreeDigsItsLowerLab(GameTestHelper helper) {
		levelThreeOverLevelTwo(helper, StarterBlueprints.RESEARCH_LAB_2, StarterBlueprints.RESEARCH_LAB_3);
	}

	//$ gametest_ticks_batch HUGE_AREA '24000' '"cellar_jeweller"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "cellar_jeweller")
	public void jewellersThreeDigsItsVault(GameTestHelper helper) {
		levelThreeOverLevelTwo(helper, StarterBlueprints.JEWELLERS_WORKSHOP_2, StarterBlueprints.JEWELLERS_WORKSHOP_3);
	}

	/**
	 * The Level III clicked onto a finished Level II on real ground, with what the supply report says it still needs: the
	 * builder digs the cellar out of the earth and fits it out, and leaves the Level II as it stood (its chests too).
	 */
	private static void levelThreeOverLevelTwo(GameTestHelper helper, StarterBlueprints.Entry two, StarterBlueprints.Entry three) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		BlockPos origin = new BlockPos(6, GROUND + 1, 9);
		BlueprintData.Placement placement = placement(helper, origin);
		level.getStructureManager().get(two.id()).orElseThrow()
			.placeInWorld(level, helper.absolutePos(origin), helper.absolutePos(origin), new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BuildSiteManager.get(level).recordFinished(two.id(), placement, UUID.randomUUID());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		var report = io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.check(level,
				new BlueprintData(three.id(), Optional.of(three.size()), Optional.of(placement)))
			.orElseThrow(() -> new GameTestAssertException("no supply report"));
		List<ItemStack> needed = new ArrayList<>();
		report.missing().forEach(m -> needed.addAll(stacks(m.item(), m.count())));
		Setup s = setup(helper, three.id(), origin, needed);
		helper.assertFalse(s.site().isCellarOnly(), "an upgrade was taken for a cellar dig");
		Blueprint lower = BlueprintLibrary.get(level, two.id()).orElseThrow();
		List<BlockPos> chests = lower.blocks().stream().filter(e -> e.state().is(Blocks.CHEST)).map(e -> origin.offset(e.pos())).toList();
		int total = s.plan().steps(BuildPlan.Stage.STRUCTURE).size() + s.plan().steps(BuildPlan.Stage.DECORATION).size();
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			assertBasementEmpty(helper, s);
			for (BlockPos chest : chests) {
				helper.assertBlockPresent(Blocks.CHEST, chest); // the workers' chests, where they were
			}
			helper.assertTrue(s.site().placed() < total * 0.6, "placed " + s.site().placed() + " of " + total + ": the Level II should have been kept");
		});
	}

	// --- digging an old build's cellar ----------------------------------------------------------------------------------

	/**
	 * A Smithy finished before 26.15 (its coal store still ground) is offered "Dig the cellar" by the village's builder, as
	 * a blueprint of the Smithy lined up over it; given to the builder, it digs and fits out only the coal store and the
	 * hatch down to it: a block a player left in the forge stays, and no more is placed than the cellar takes. The Smithy's
	 * upgrade, clicked on instead, would dig the cellar with the rest.
	 */
	//$ gametest_ticks_batch HUGE_AREA '16000' '"cellar_dig"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 16000, batch = "cellar_dig")
	public void oldSmithyDigsOnlyItsCellar(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		BlockPos origin = new BlockPos(9, GROUND + 1, 9);
		ResourceLocation id = StarterBlueprints.SMITHY.id();
		BlueprintData.Placement placement = oldBuild(helper, StarterBlueprints.SMITHY, origin);
		BlockPos marker = playerBlockInTheRoom(helper, id, origin);
		helper.assertTrue(Cellars.needsDigging(level, id, placement), "the Smithy without its cellar doesn't need it dug");
		// The upgrade's plan clears the whole basement too (upgrading such a build digs its cellar)
		Blueprint two = BlueprintLibrary.get(level, StarterBlueprints.SMITHY_2.id()).orElseThrow();
		Set<BlockPos> clears = BuildPlan.create(two, placement).steps(BuildPlan.Stage.CLEAR).stream().map(BuildPlan.Step::pos).collect(Collectors.toSet());
		for (Blueprint.Entry e : BlueprintLibrary.get(level, id).orElseThrow().blocks()) {
			if (e.pos().getY() < 0 && e.state().isAir() && two.blocks().stream().anyMatch(t -> t.pos().equals(e.pos()) && t.state().isAir())) {
				helper.assertTrue(clears.contains(helper.absolutePos(origin).offset(e.pos())), "the Smithy II's plan leaves " + e.pos().toShortString() + " of the cellar");
			}
		}
		// The offer: a builder of the village sells the job
		Setup s = digSetup(helper, id, origin, placement);
		int cellarSteps = s.plan().steps(BuildPlan.Stage.STRUCTURE).size() + s.plan().steps(BuildPlan.Stage.DECORATION).size();
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			assertBasementEmpty(helper, s);
			assertWholeBuildStands(helper, s, marker);
			helper.assertBlockPresent(Blocks.GOLD_BLOCK, marker);
			helper.assertTrue(s.site().placed() <= cellarSteps, "placed " + s.site().placed() + ", more than the cellar's " + cellarSteps);
			helper.assertTrue(!Cellars.needsDigging(s.level(), id, placement), "the dug cellar still needs digging");
		});
	}

	/** A Smithy with its coal store dug: no "Dig the cellar" from its builder, and its own blueprint over it is no dig. */
	//$ gametest_ticks_batch HUGE_AREA '200' '"cellar_none"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "cellar_none")
	public void noOfferWhenTheCellarIsThere(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		BlockPos origin = new BlockPos(9, GROUND + 1, 9);
		StarterBlueprints.Entry smithy = StarterBlueprints.SMITHY;
		BlueprintData.Placement placement = placement(helper, origin);
		BlockPos file = helper.absolutePos(origin).below(smithy.basement());
		level.getStructureManager().get(smithy.id()).orElseThrow().placeInWorld(level, file, file, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BuildSiteManager.get(level).recordFinished(smithy.id(), placement, UUID.randomUUID());
		// and a Level II with no basement beside it: never a dig
		BlockPos lab = new BlockPos(1, GROUND + 1, 20);
		level.getStructureManager().get(StarterBlueprints.JEWELLERS_WORKSHOP_2.id()).orElseThrow()
			.placeInWorld(level, helper.absolutePos(lab), helper.absolutePos(lab), new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.JEWELLERS_WORKSHOP_2.id(), placement(helper, lab), UUID.randomUUID());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		helper.assertFalse(Cellars.needsDigging(level, smithy.id(), placement), "a Smithy with its cellar needs it dug");
		helper.assertTrue(Cellars.offer(level, villager).isEmpty(), "the builder offers to dig a cellar that is there");
		helper.assertTrue(villager.getOffers().stream().noneMatch(o -> o.getResult().has(DataComponents.CUSTOM_NAME)), "a \"Dig the cellar\" trade");
		helper.assertTrue(Cellars.toDig(level, helper.absolutePos(origin), 64).isEmpty(), "the hall's advice would list a cellar to dig");
		BuildSite site = Builders.start(level, villager, null, smithy.id(), placement);
		helper.assertFalse(site.isCellarOnly(), "the Smithy's blueprint over a Smithy with its cellar was taken for a dig");
		BuildSiteManager.get(level).remove(site.id());
		helper.succeed();
	}

	/**
	 * Saved and loaded mid-dig (the site from its save, its plan worked out again, the builder from its save): it is still
	 * a cellar dig, and it finishes the cellar with nothing skipped and nothing above touched.
	 */
	//$ gametest_ticks_batch HUGE_AREA '16000' '"cellar_reload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 16000, batch = "cellar_reload")
	public void reloadMidDigFinishesTheCellar(GameTestHelper helper) {
		Leftovers.clear(helper);
		ground(helper, 30);
		BlockPos origin = new BlockPos(9, GROUND + 1, 9);
		ResourceLocation id = StarterBlueprints.SMITHY.id();
		BlueprintData.Placement placement = oldBuild(helper, StarterBlueprints.SMITHY, origin);
		BlockPos marker = playerBlockInTheRoom(helper, id, origin);
		Setup s = digSetup(helper, id, origin, placement);
		int clearSteps = s.plan().steps(BuildPlan.Stage.CLEAR).size();
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite site = s.site();
			if (!reloaded[0] && site.stage() == BuildPlan.Stage.CLEAR && site.cursor() > clearSteps / 2) {
				reloaded[0] = true;
				CompoundTag saved = site.save();
				BuildSite back = BuildSite.load(saved);
				helper.assertTrue(back != null && back.isCellarOnly() && back.save().equals(saved), "the dig didn't come back from its save as a dig");
				site.setFoundation(site.foundation()); // the plan is worked out again
				BuildPlan again = site.plan(s.level());
				helper.assertTrue(again != null && again != s.plan() && again.steps(BuildPlan.Stage.FOUNDATION).isEmpty()
					&& again.steps(BuildPlan.Stage.LANDSCAPE).isEmpty(), "the dig's plan, worked out again, isn't the cellar's alone");
				Villager old = s.villager();
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(old.save(tag), "the builder wasn't saved");
				old.discard();
				Entity loaded = EntityType.loadEntityRecursive(tag, s.level(), e -> e);
				helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
				s.level().addFreshEntity(loaded);
				s.builder()[0] = (Villager) loaded;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "not reloaded mid-dig");
			assertBuilt(helper, s);
			assertBasementEmpty(helper, s);
			assertWholeBuildStands(helper, s, marker);
			helper.assertBlockPresent(Blocks.GOLD_BLOCK, marker);
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " step(s) skipped after the reload");
		});
	}

	// --- helpers ------------------------------------------------------------------------------------------------------

	/**
	 * {@code entry} standing finished at {@code originRel} as it was built before 26.15: everything above its y = 0 as drawn,
	 * its basement ground (dirt over stone) and the hatch down a block of floor.
	 */
	private static BlueprintData.Placement oldBuild(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos originRel) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = placement(helper, originRel);
		BlockPos file = helper.absolutePos(originRel).below(entry.basement());
		level.getStructureManager().get(entry.id()).orElseThrow().placeInWorld(level, file, file, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
		Set<Long> columns = new HashSet<>();
		blueprint.blocks().stream().filter(e -> e.pos().getY() < 0).forEach(e -> columns.add(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())));
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE; // no drops from what hung on what's filled in
		for (Blueprint.Entry e : blueprint.blocks()) {
			BlockPos world = helper.absolutePos(originRel).offset(e.pos());
			if (e.pos().getY() < 0) {
				level.removeBlockEntity(world);
				level.setBlock(world, (e.pos().getY() >= -2 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), flags);
			} else if (e.pos().getY() == 0 && columns.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ()))
					&& (e.state().is(BlockTags.TRAPDOORS) || e.state().is(Blocks.LADDER) || e.state().isAir())) {
				level.setBlock(world, Blocks.COBBLESTONE.defaultBlockState(), flags);
			}
		}
		BuildSiteManager.get(level).recordFinished(entry.id(), placement, UUID.randomUUID());
		return placement;
	}

	/** A gold block where a player left it, in the build's room over the cellar (the drawing's air at y = 1): a dig leaves it. */
	private static BlockPos playerBlockInTheRoom(GameTestHelper helper, ResourceLocation id, BlockPos originRel) {
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), id).orElseThrow();
		Set<Long> columns = new HashSet<>();
		blueprint.blocks().stream().filter(e -> e.pos().getY() < 0).forEach(e -> columns.add(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())));
		Set<Long> wayDown = new HashSet<>();
		blueprint.blocks().stream().filter(e -> e.pos().getY() >= 0 && (e.state().is(Blocks.LADDER) || e.state().is(BlockTags.TRAPDOORS)))
			.forEach(e -> wayDown.add(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())));
		Blueprint.Entry spot = blueprint.blocks().stream()
			.filter(e -> e.pos().getY() == 1 && e.state().isAir())
			.filter(e -> columns.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())) && !wayDown.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())))
			.findFirst().orElseThrow(() -> new GameTestAssertException(id + " has no room over its cellar"));
		BlockPos rel = originRel.offset(spot.pos());
		helper.setBlock(rel, Blocks.GOLD_BLOCK);
		return rel;
	}

	/**
	 * The village's builder offers "Dig the cellar" for the build at {@code placement}: the trade's blueprint, given back
	 * to them (as a player hands a builder a blueprint), starts a cellar dig stocked with just what the cellar takes.
	 */
	private static Setup digSetup(GameTestHelper helper, ResourceLocation id, BlockPos originRel, BlueprintData.Placement placement) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		List<BuildSiteManager.Finished> offered = Cellars.offer(level, villager);
		helper.assertTrue(offered.size() == 1 && offered.get(0).placement().equals(placement), "the builder offers to dig " + offered);
		MerchantOffer trade = villager.getOffers().stream().filter(o -> BlueprintItem.data(o.getResult())
				.map(d -> d.structure().equals(id) && d.placement().equals(Optional.of(placement))).orElse(false))
			.findFirst().orElseThrow(() -> new GameTestAssertException("no \"Dig the cellar\" trade"));
		var name = trade.getResult().get(DataComponents.CUSTOM_NAME);
		helper.assertTrue(name != null && name.getContents() instanceof TranslatableContents t && t.getKey().equals("item.aliveworkplace.blueprint.cellar"),
			"the trade's blueprint isn't called \"Dig the cellar\": " + name);
		helper.assertTrue(Cellars.offer(level, villager).isEmpty(), "the trade was added twice");
		Blueprint cellar = Cellars.cellarOnly(BlueprintLibrary.get(level, id).orElseThrow());
		List<ItemStack> stock = new ArrayList<>();
		BuildPlan.create(cellar, placement).materials().forEach((item, count) -> stock.addAll(stacks(item, count)));
		fill(helper, stock);
		BlueprintData data = BlueprintItem.data(trade.getResult()).orElseThrow();
		BuildSite site = Builders.start(level, villager, null, data.structure(), data.placement().orElseThrow());
		helper.assertTrue(site.isCellarOnly(), "the job's blueprint didn't start a cellar dig");
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				helper.assertTrue(step.pos().getY() <= placement.origin().getY() + 1, "the dig works " + step.pos().subtract(placement.origin()).toShortString());
			}
		}
		return new Setup(level, new Villager[]{villager}, site, plan, BlueprintLibrary.get(level, id).orElseThrow());
	}

	private static void ground(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				for (int y = 2; y <= GROUND; y++) {
					helper.setBlock(new BlockPos(x, y, z), y < 4 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK);
				}
			}
		}
	}

	private static void fill(GameTestHelper helper, List<ItemStack> stock) {
		List<BlockPos> stores = new ArrayList<>(List.of(CHEST));
		for (int i = 0; i * 27 < Math.max(1, stock.size()) - 27; i++) {
			stores.add(new BlockPos(1 + i % 4, GROUND + 1, 5 + i / 4));
		}
		for (int i = 0; i < stores.size(); i++) {
			helper.setBlock(stores.get(i), i == 0 ? Blocks.CHEST : Blocks.BARREL);
			Container box = helper.getBlockEntity(stores.get(i));
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				box.setItem(slot, stock.get(i * 27 + slot).copy());
			}
		}
	}

	private static Setup setup(GameTestHelper helper, ResourceLocation structure, BlockPos originRel, List<ItemStack> stock) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		fill(helper, stock);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, structure, placement(helper, originRel));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + structure);
		}
		return new Setup(level, new Villager[]{villager}, site, plan, BlueprintLibrary.get(level, structure).orElseThrow());
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

	/** After a dig, the whole build matches its blueprint (the cellar now too) but for the player's block in the room. */
	private static void assertWholeBuildStands(GameTestHelper helper, Setup s, BlockPos markerRel) {
		BlockPos marker = helper.absolutePos(markerRel);
		List<BlockPos> wrong = BuildPlan.create(s.blueprint(), s.site().placement()).unfinished(s.level()).stream()
			.filter(p -> !p.equals(marker)).toList();
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the whole build wrong after the dig, e.g. "
			+ wrong.stream().limit(4).map(p -> p.subtract(s.site().placement().origin()).toShortString() + "=" + s.level().getBlockState(p)).toList());
	}

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
