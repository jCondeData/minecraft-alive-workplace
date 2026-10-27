package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA for B642 on 36.21 ("Refresh to the latest look"), from the specs: a furnished house built from an older drawing,
 * refreshed through the hall's Repairs page, ends as a fresh build of the current drawing (chests, beds, job blocks and
 * players' blocks aside), with nothing doubled and nothing floating. The builder's own tests cover the Stone House; these
 * take the other furnished houses whose drawing AND spots file changed since (Starter Cottage, Inn, Townhouse), each
 * furnished by the spots file of its day and turned or mirrored, and the Builder's own refresh of a furnished old Stone
 * House across a save and reload, with a player's blocks on an old piece's spot and on a new piece's spot.
 */
public class QaRefreshFurnishedGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** The house's origin: the middle of the area, so it fits however it's turned. */
	private static final BlockPos HOUSE = new BlockPos(15, 2, 15);

	private record Stage(ServerLevel level, ServerPlayer owner, ResourceLocation structure, BlueprintData.Placement placement, Blueprint asBuilt,
						 BlockPos hall, List<BlockPos> stores, Villager builder) {
	}

	/** The spots file of {@code structure} as it was at {@code key}^ (the day the older drawing was current). */
	static FurnitureChoice oldFurniture(String structure, String key) {
		String file = "/qa_b642/" + structure + "_" + key + ".json";
		try (InputStream in = QaRefreshFurnishedGameTests.class.getResourceAsStream(file)) {
			if (in == null) {
				throw new IllegalStateException("missing " + file);
			}
			var spots = BlueprintFurniture.read(com.google.gson.JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
			FurnitureChoice chosen = FurnitureChoice.choose(spots, List.of(FurnishingTableGameTests.TEST_TABLE), true);
			return FurnitureChoice.load(chosen.save()); // (as an old save has it)
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * {@code structure} standing as its older drawing {@code key} drew it, furnished with {@code furniture}; a hall, a
	 * Builder, his bench and four stores in the corner the house leaves free.
	 */
	private static Stage stage(GameTestHelper helper, ResourceLocation structure, String key, Rotation rotation, Mirror mirror, FurnitureChoice furniture) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 40;
		helper.setDayTime(2000);
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000);
			}
		});
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getServer().getPlayerList().remove(owner);
		});
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), rotation, mirror);
		Blueprint old = Refreshes.olderDrawing(level.getServer(), structure, key)
			.orElseThrow(() -> new IllegalStateException(structure + "'s drawing " + key + " isn't shipped"));
		Blueprint asBuilt = furniture.applyAsBuilt(old);
		Map<BlockPos, BlockState> built = BuildPlan.create(asBuilt, placement).targets();
		for (Map.Entry<BlockPos, BlockState> e : built.entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		// the corner the house leaves free (it stands in one quarter round HOUSE, as turned and mirrored)
		BlockPos o = helper.absolutePos(HOUSE);
		double sx = built.keySet().stream().mapToInt(p -> p.getX() - o.getX()).average().orElse(0);
		double sz = built.keySet().stream().mapToInt(p -> p.getZ() - o.getZ()).average().orElse(0);
		int x0 = sx > 0 ? 1 : 22;
		int z0 = sz > 0 ? 1 : 22;
		BlockPos hall = new BlockPos(x0, 2, z0);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		List<BlockPos> stores = List.of(new BlockPos(x0, 2, z0 + 4), new BlockPos(x0, 2, z0 + 6), new BlockPos(x0 + 4, 2, z0 + 4), new BlockPos(x0 + 4, 2, z0 + 6));
		stores.forEach(c -> helper.setBlock(c, Blocks.CHEST));
		BlockPos bench = new BlockPos(x0 + 2, 2, z0 + 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(x0 + 2, 2, z0 + 2));
		Builders.employ(level, builder, helper.absolutePos(bench));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(structure, placement, owner.getUUID());
		if (!furniture.isVanilla()) {
			manager.setFurniture(placement, furniture);
		}
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Stage(level, owner, structure, placement, asBuilt, helper.absolutePos(hall), stores, builder);
	}

	/** The owner presses Refresh on the hall's Repairs page (the build's entry, then the button). */
	private static BuildSite pressRefresh(GameTestHelper helper, Stage s) {
		ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), s.hall());
		menu.press(HallPages.slot(RepairsPage.PAGE), s.owner());
		int slot = -1;
		for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE && slot < 0; i++) {
			if (menu.icon(i).is(Items.PAINTING) || menu.icon(i).is(Items.SCAFFOLDING)) {
				slot = i;
			}
		}
		helper.assertTrue(slot >= 0, "the old " + s.structure() + " has no Refresh on the Repairs page");
		menu.press(slot, s.owner());
		helper.assertTrue(menu.icon(RepairsPage.REFRESH).is(Items.BRUSH), "no Refresh button: " + menu.icon(RepairsPage.REFRESH));
		menu.press(RepairsPage.REFRESH, s.owner());
		BuildSite site = Refreshes.siteOf(s.level(), s.placement());
		helper.assertTrue(site != null, "no Builder took the refresh up");
		return site;
	}

	/** The current drawing as this build would be built now (its furniture brought to today's spots). */
	private static Blueprint currentFor(Stage s) {
		Blueprint base = BlueprintLibrary.get(s.level(), s.structure()).orElseThrow();
		return Refreshes.refreshedFurniture(s.level(), s.structure(), BuildSiteManager.get(s.level()).furniture(s.placement())).apply(base, s.structure());
	}

	/** Every block in the build (old or new drawing's spots) that can't stand where it is: a torch, lantern, banner, plate... floating. */
	private static List<String> floating(Stage s, Blueprint current) {
		Set<BlockPos> spots = new HashSet<>(BuildPlan.create(s.asBuilt(), s.placement()).targets().keySet());
		spots.addAll(BuildPlan.create(current, s.placement()).targets().keySet());
		List<String> out = new ArrayList<>();
		for (BlockPos pos : spots) {
			BlockState world = s.level().getBlockState(pos);
			if (!world.isAir() && !world.canSurvive(s.level(), pos)) {
				out.add(pos.toShortString() + " " + world.getBlock().getDescriptionId());
			}
		}
		return out;
	}

	/** Pressed through the Repairs page, the refresh's plan carried out at once: a fresh build of today's drawing, nothing floating. */
	private static void refreshedEqualsAFreshBuild(GameTestHelper helper, ResourceLocation structure, String key, Rotation rotation, Mirror mirror) {
		FurnitureChoice furniture = oldFurniture(structure.getPath(), key);
		helper.assertTrue(!furniture.isVanilla(), "the old spots file furnished nothing");
		Stage s = stage(helper, structure, key, rotation, mirror, furniture);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!RefreshMatchesFreshBuildGameTests.differences(s.level(), s.placement(), s.asBuilt(), currentFor(s)).isEmpty(),
				"the old house already equals the current drawing");
			BuildSite site = pressRefresh(helper, s);
			helper.assertTrue(key.equals(site.refreshFrom()), "refreshed from the wrong drawing: " + site.refreshFrom() + ", built from " + key);
			BuildPlan plan = site.plan(s.level());
			Map<BlockPos, BlockState> now = BuildPlan.create(currentFor(s), s.placement()).targets();
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					s.level().setBlock(step.pos(), step.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					if (step.state().getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
						BlockPos other = step.pos().relative(net.minecraft.world.level.block.BedBlock.getConnectedDirection(step.state()));
						if (now.containsKey(other)) {
							s.level().setBlock(other, now.get(other), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
						}
					}
				}
			}
			List<String> left = RefreshMatchesFreshBuildGameTests.differences(s.level(), s.placement(), s.asBuilt(), currentFor(s));
			helper.assertTrue(left.isEmpty(), structure.getPath() + " " + key + ": " + left.size() + " spots differ from a fresh build: "
				+ left.subList(0, Math.min(10, left.size())));
			List<String> loose = floating(s, currentFor(s));
			helper.assertTrue(loose.isEmpty(), structure.getPath() + ": floating after the refresh: " + loose.subList(0, Math.min(10, loose.size())));
			AliveWorkplace.LOG.info("QA B642: {} {} refreshed ({} steps) equals a fresh build, nothing floating", structure, key, plan.steps(BuildPlan.Stage.CLEAR).size()
				+ plan.steps(BuildPlan.Stage.STRUCTURE).size() + plan.steps(BuildPlan.Stage.DECORATION).size());
			helper.succeed();
		});
	}

	/** The Starter Cottage, furnished before its 15f3b652a redraw, turned half round and mirrored. */
	//$ gametest_ticks_batch HUGE '400' '"qa_b642_cottage"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qa_b642_cottage")
	public void aFurnishedOldStarterCottageTurnedAndMirroredIsRefreshedToAFreshBuild(GameTestHelper helper) {
		refreshedEqualsAFreshBuild(helper, AliveWorkplace.id("starter_cottage"), "15f3b652a", Rotation.CLOCKWISE_180, Mirror.FRONT_BACK);
	}

	/** The Inn, furnished before B260 (its spots file has changed most since), turned three quarters. */
	//$ gametest_ticks_batch HUGE '400' '"qa_b642_inn"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qa_b642_inn")
	public void aFurnishedOldInnTurnedIsRefreshedToAFreshBuild(GameTestHelper helper) {
		refreshedEqualsAFreshBuild(helper, AliveWorkplace.id("inn"), "aec09cb26", Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
	}

	/** The Townhouse, furnished before its own-exterior redraw, mirrored left to right. */
	//$ gametest_ticks_batch HUGE '400' '"qa_b642_townhouse"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qa_b642_townhouse")
	public void aFurnishedOldTownhouseMirroredIsRefreshedToAFreshBuild(GameTestHelper helper) {
		refreshedEqualsAFreshBuild(helper, AliveWorkplace.id("townhouse"), "42864073a", Rotation.NONE, Mirror.LEFT_RIGHT);
	}

	/**
	 * The owner's case end to end: a Stone House furnished before B260, turned; a player has put a gold block where an
	 * old piece stood (the new drawing has nothing there) and another where a new piece goes. The Builder refreshes it
	 * through the hall's button, the sites and the Builder are saved and loaded mid-way: afterwards the house is a fresh
	 * build (both gold blocks aside, both kept), nothing floats, and the page has no second refresh to offer.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa_b642_reload"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa_b642_reload")
	public void aFurnishedOldStoneHouseRefreshedAcrossAReloadKeepsThePlayersBlocksWithoutDoubles(GameTestHelper helper) {
		ResourceLocation stoneHouse = AliveWorkplace.id("stone_house");
		FurnitureChoice furniture = RefreshMatchesFreshBuildGameTests.oldFurniture();
		Stage s = stage(helper, stoneHouse, RefreshMatchesFreshBuildGameTests.BEFORE_B260, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE, furniture);
		Map<BlockPos, BlockState> was = BuildPlan.create(s.asBuilt(), s.placement()).targets();
		Map<BlockPos, BlockState> oldUnfurnished = BuildPlan.create(Refreshes.olderDrawing(s.level().getServer(), stoneHouse,
			RefreshMatchesFreshBuildGameTests.BEFORE_B260).orElseThrow(), s.placement()).targets();
		Map<BlockPos, BlockState> now = BuildPlan.create(currentFor(s), s.placement()).targets();
		// an old furniture piece's spot (furnished differently from the bare drawing) that the new drawing leaves empty
		BlockPos onOld = was.keySet().stream().filter(p -> !was.get(p).isAir() && !was.get(p).equals(oldUnfurnished.get(p))
				&& now.getOrDefault(p, Blocks.AIR.defaultBlockState()).isAir() && s.level().getBlockState(p.below()).isFaceSturdy(s.level(), p.below(), net.minecraft.core.Direction.UP))
			.sorted(java.util.Comparator.comparingLong(BlockPos::asLong)).findFirst().orElse(null);
		// a spot where the new drawing puts a piece the old one didn't have, on something solid
		BlockPos onNew = now.keySet().stream().filter(p -> !now.get(p).isAir() && was.getOrDefault(p, Blocks.AIR.defaultBlockState()).isAir()
				&& !p.equals(onOld) && p.getY() > s.placement().origin().getY() && now.get(p).getBlock() != Blocks.CHEST)
			.sorted(java.util.Comparator.comparingLong(BlockPos::asLong)).findFirst().orElse(null);
		helper.assertTrue(onOld != null && onNew != null, "no spot for the player's blocks: " + onOld + " " + onNew);
		BuildPlan[] plan = {null};
		Villager[] builder = {s.builder()};
		boolean[] reloaded = {false};
		int[] ticks = {0};
		helper.runAfterDelay(2, () -> {
			s.level().setBlock(onOld, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
			s.level().setBlock(onNew, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
			BuildSite site = pressRefresh(helper, s);
			plan[0] = site.plan(s.level());
			int at = 0;
			for (Map.Entry<net.minecraft.world.item.Item, Integer> need : plan[0].materials().entrySet()) {
				for (int left = need.getValue() + 2; left > 0 && at < 27 * s.stores().size(); at++) {
					int n = Math.min(left, need.getKey().getDefaultMaxStackSize());
					Container store = helper.getBlockEntity(s.stores().get(at / 27));
					store.setItem(at % 27, new ItemStack(need.getKey(), n));
					left -= n;
				}
			}
		});
		helper.onEachTick(() -> {
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			if (reloaded[0] || site == null || ++ticks[0] < 40) {
				return;
			}
			reloaded[0] = true;
			BuildSiteManager manager = BuildSiteManager.get(s.level());
			BuildSiteManager loaded = BuildSiteManager.load(manager.save(new CompoundTag(), s.level().registryAccess()), s.level().registryAccess());
			helper.assertTrue(loaded.get(site.id()) != null && loaded.get(site.id()).isRefresh(), "the refresh wasn't saved");
			s.level().getDataStorage().set(BuildSiteManager.NAME, loaded);
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(builder[0].save(tag), "the builder wasn't saved");
			builder[0].discard();
			Entity entity = EntityType.loadEntityRecursive(tag, s.level(), e -> e);
			helper.assertTrue(entity instanceof Villager, "the builder didn't load: " + entity);
			s.level().addFreshEntity(entity);
			builder[0] = (Villager) entity;
			AliveWorkplace.LOG.info("QA B642: reloaded the furnished Stone House's refresh mid-way");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(plan[0] != null, "not started");
			helper.assertTrue(reloaded[0], "never reloaded mid-way");
			helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) == null, "still refreshing");
			helper.assertTrue(BuildSiteManager.get(s.level()).all().stream().noneMatch(b -> b.placement().equals(s.placement())), "a site left on the house");
			helper.assertTrue(s.level().getBlockState(onOld).is(Blocks.GOLD_BLOCK), "the player's block on the old piece's spot is gone: " + s.level().getBlockState(onOld));
			helper.assertTrue(s.level().getBlockState(onNew).is(Blocks.GOLD_BLOCK), "the player's block where a new piece goes is gone: " + s.level().getBlockState(onNew));
			List<String> left = new ArrayList<>(RefreshMatchesFreshBuildGameTests.differences(s.level(), s.placement(), s.asBuilt(), currentFor(s)));
			left.removeIf(d -> d.startsWith(onOld.toShortString() + " ") || d.startsWith(onNew.toShortString() + " "));
			// (a bed a villager has claimed stays, as the spec says: the Builder himself may claim the old one)
			Set<String> claimed = new HashSet<>();
			for (BlockPos pos : was.keySet()) {
				BlockState world = s.level().getBlockState(pos);
				if (world.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
					BlockPos head = world.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
						? pos : pos.relative(net.minecraft.world.level.block.BedBlock.getConnectedDirection(world));
					if (s.level().getPoiManager().getFreeTickets(head) == 0) {
						claimed.add(pos.toShortString());
						BlockState other = now.get(pos);
						if (other != null && other.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
							// the new drawing's bed there can't go in beside a kept one
							claimed.add(pos.relative(net.minecraft.world.level.block.BedBlock.getConnectedDirection(other)).toShortString());
						}
					}
				}
			}
			helper.assertTrue(left.stream().noneMatch(d -> claimed.stream().noneMatch(c -> d.startsWith(c + " "))),
				left.size() + " spots differ from a fresh build: " + left.subList(0, Math.min(10, left.size())) + "; claimed beds: " + claimed);
			List<String> loose = floating(s, currentFor(s));
			helper.assertTrue(loose.isEmpty(), "floating after the refresh: " + loose.subList(0, Math.min(10, loose.size())));
			helper.assertTrue(Refreshes.entries(s.level(), s.hall()).stream().noneMatch(e -> e.building().placement().equals(s.placement())),
				"the refreshed house still offers a refresh on the Repairs page");
			AliveWorkplace.LOG.info("QA B642: furnished Stone House refreshed across a reload; player's blocks at {} and {} kept; differences only at claimed beds {}",
				onOld, onNew, claimed);
		});
	}
}
