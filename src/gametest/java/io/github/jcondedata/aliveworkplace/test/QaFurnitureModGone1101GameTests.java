package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * QA (lane-b-1101-0938) for ROADMAP 37.3, written from its spec: "The choice is saved on the site, so a restart doesn't
 * change a build in progress; if the mod is later removed, upkeep puts the vanilla piece back where the modded one
 * vanished." A furniture mod being removed is played the way a server sees it: the saved data names a block the registry
 * no longer has (here {@code handcrafted:spruce_chair}, Handcrafted being absent from the GameTest server), and the
 * modded block in the world is gone (air). The test "mod" {@code aliveworkplace_test_pieces} (its chair an upturned
 * spruce stair) builds the piece first.
 */
public class QaFurnitureModGone1101GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String GONE = "handcrafted:spruce_chair[facing=north]";

	/** {@code tag} as a server without the test pieces mod but with a gone mod's chair reads it: the upturned stair renamed. */
	static CompoundTag modRemoved(CompoundTag tag) {
		String snbt = tag.toString().replaceAll("minecraft:spruce_stairs\\[[^\\]\"]*half=top[^\\]\"]*\\]", GONE);
		if (!snbt.contains(GONE)) {
			throw new GameTestAssertException("no upturned stair in the saved data to rename: " + snbt);
		}
		try {
			return TagParser.parseTag(snbt);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new GameTestAssertException("the renamed save doesn't parse: " + e.getMessage());
		}
	}

	static boolean vanillaChair(BlockState state) {
		return state != null && state.is(Blocks.SPRUCE_STAIRS) && state.getValue(StairBlock.HALF) == Half.BOTTOM;
	}

	/**
	 * A nook finished with the test mod's chair; the server restarts without that mod (its saved furniture names a chair
	 * block that no longer exists and the chair itself is gone). The builder's upkeep starts a repair that puts the nook's
	 * vanilla chair (a spruce stair the right way up) on the spot, asks for that stair and not for the gone chair, and
	 * builds it; afterwards the building's furniture reads vanilla.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFurnitureModGoneUpkeep"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurnitureModGoneUpkeep")
	public void aBuildingWhoseFurnitureModWasRemovedGetsItsVanillaPieceBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		List<String> mods = Furnishings.MODS;
		Leftovers.after(helper, () -> Furnishings.MODS = mods);
		ServerLevel level = helper.getLevel();
		Furnishings.MODS = List.of(QaFurnishedGameTests.PIECES);
		Villager builder = QaFurnishedGameTests.builderWithChest(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(5, 2, 5)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, QaFurnishedGameTests.NOOK, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for the nook");
		helper.assertTrue(!site.furniture().isVanilla(), "the nook didn't take the test mod's chair: " + site.furniture());
		QaFurnishedGameTests.stock(helper, plan);
		BlockPos chairAt = placement.origin().offset(QaFurnishedGameTests.CHAIR);
		BuildSite[] repair = {null};
		String[] failure = {null};
		helper.succeedWhen(() -> {
			if (failure[0] != null) {
				throw new GameTestAssertException(failure[0]);
			}
			BuildSiteManager manager = BuildSiteManager.get(level);
			if (repair[0] == null) {
				helper.assertTrue(manager.get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
				failure[0] = removeTheMod(helper, level, builder, manager, placement, chairAt, repair);
				if (failure[0] != null) {
					throw new GameTestAssertException(failure[0]);
				}
			}
			helper.assertTrue(manager.get(repair[0].id()) == null,
				"the repair is still going: " + repair[0].stage() + " " + repair[0].status() + " missing " + repair[0].missing());
			BlockState now = level.getBlockState(chairAt);
			helper.assertTrue(vanillaChair(now), "after the repair the chair spot holds " + now + ", not the nook's vanilla spruce stair");
			helper.assertTrue(manager.furniture(placement).isVanilla(), "after the repair the nook still records " + manager.furniture(placement));
			org.slf4j.LoggerFactory.getLogger("QaFurnitureModGone").info("the gone mod's chair spot was rebuilt as {} by tick {}", now, helper.getTick());
		});
	}

	/** The restart without the mod and the repair it needs; returns what's wrong, or null. */
	private static String removeTheMod(GameTestHelper helper, ServerLevel level, Villager builder, BuildSiteManager manager,
			BlueprintData.Placement placement, BlockPos chairAt, BuildSite[] repair) {
		BlockState built = level.getBlockState(chairAt);
		if (!built.is(Blocks.SPRUCE_STAIRS) || built.getValue(StairBlock.HALF) != Half.TOP) {
			return "the nook was built with " + built + " on its chair spot, not the test mod's upturned stair";
		}
		if (manager.furniture(placement).isVanilla()) {
			return "the finished nook didn't record its modded chair";
		}
		// The server stops, the mod is taken out of the pack and the server starts again: what it reads from the save
		BuildSiteManager reloaded = BuildSiteManager.load(modRemoved(manager.save(new CompoundTag(), level.registryAccess())), level.registryAccess());
		FurnitureChoice kept = reloaded.furniture(placement);
		if (!kept.spots().isEmpty()) {
			return "after a restart without the mod the nook still has pieces: " + kept;
		}
		if (reloaded.finishedIn(level).stream().noneMatch(f -> f.placement().equals(placement))) {
			return "after a restart without the mod the nook isn't a finished building any more";
		}
		level.getDataStorage().set(BuildSiteManager.NAME, reloaded); // the restarted server's sites
		level.setBlockAndUpdate(chairAt, Blocks.AIR.defaultBlockState()); // the gone mod's chair is air in the world
		Furnishings.MODS = List.of();
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		BuildSite site = Upkeep.look(level, builder);
		if (site == null) {
			return "no repair started for the chair spot left empty by the gone mod";
		}
		BuildPlan plan = site.plan(level);
		BlockState back = QaFurnishedGameTests.stepStates(plan).get(chairAt);
		if (!vanillaChair(back)) {
			return "the repair puts back " + back + " on the chair spot, not the vanilla spruce stair";
		}
		Map<Item, Integer> wants = plan.materials();
		if (!wants.containsKey(Items.SPRUCE_STAIRS)) {
			return "the repair doesn't ask for a spruce stair: " + wants;
		}
		((net.minecraft.world.Container) helper.getBlockEntity(QaFurnishedGameTests.CHEST)).setItem(0, new net.minecraft.world.item.ItemStack(Items.SPRUCE_STAIRS, 4));
		repair[0] = site;
		return null;
	}

	/**
	 * The server restarts without the furniture mod in the middle of a build, after the builder already put the modded
	 * chair down (it is air in the restarted world). The build carries on and ends with the nook whole: the vanilla chair
	 * on the spot, either from the build itself or from the builder's upkeep straight after, never a hole where the chair
	 * was; nothing is skipped.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFurnitureModGoneMidBuild"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurnitureModGoneMidBuild")
	public void aRestartWithoutTheModMidBuildStillEndsWithAChair(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		List<String> mods = Furnishings.MODS;
		Leftovers.after(helper, () -> Furnishings.MODS = mods);
		ServerLevel level = helper.getLevel();
		Furnishings.MODS = List.of(QaFurnishedGameTests.PIECES);
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		Villager builder = QaFurnishedGameTests.builderWithChest(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(5, 2, 5)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, QaFurnishedGameTests.NOOK, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null && !site.furniture().isVanilla(), "the nook didn't take the test mod's chair: " + site.furniture());
		QaFurnishedGameTests.stock(helper, plan);
		// a vanilla chair's stair too, for whichever of the build or the upkeep puts it back
		((net.minecraft.world.Container) helper.getBlockEntity(QaFurnishedGameTests.CHEST)).setItem(20, new net.minecraft.world.item.ItemStack(Items.SPRUCE_STAIRS, 2));
		BlockPos chairAt = placement.origin().offset(QaFurnishedGameTests.CHAIR);
		String[] restarted = {null};
		int[] repairs = {0};
		helper.onEachTick(() -> {
			if (restarted[0] != null) {
				BuildSite live = BuildSiteManager.get(level).all().stream().filter(s -> s.placement().equals(placement)).findFirst().orElse(null);
				if (live == null && level.getBlockState(chairAt).isAir() && repairs[0]++ < 3) {
					Upkeep.look(level, builder); // (upkeep's own look, sooner than its every-so-often round)
				}
				return;
			}
			BlockState now = level.getBlockState(chairAt);
			if (!now.is(Blocks.SPRUCE_STAIRS) || now.getValue(StairBlock.HALF) != Half.TOP) {
				return;
			}
			BuildSite live = BuildSiteManager.get(level).get(site.id());
			if (live == null) {
				restarted[0] = "missed: the chair was the nook's last step";
				return;
			}
			var registries = level.registryAccess();
			level.getDataStorage().set(BuildSiteManager.NAME,
				BuildSiteManager.load(modRemoved(BuildSiteManager.get(level).save(new CompoundTag(), registries)), registries));
			level.setBlockAndUpdate(chairAt, Blocks.AIR.defaultBlockState()); // the gone mod's chair is air in the world
			Furnishings.MODS = List.of();
			restarted[0] = "at " + live.stage() + " cursor " + live.cursor();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0] != null, "the modded chair never went down");
			helper.assertTrue(!restarted[0].startsWith("missed"), restarted[0]);
			BuildSiteManager manager = BuildSiteManager.get(level);
			BuildSite live = manager.all().stream().filter(s -> s.placement().equals(placement)).findFirst().orElse(null);
			helper.assertTrue(live == null, "still building (restarted " + restarted[0] + "): " + (live == null ? "" : live.stage() + " " + live.status()
				+ " missing " + live.missing()));
			BlockState now = level.getBlockState(chairAt);
			helper.assertTrue(vanillaChair(now), "restarted without the mod " + restarted[0] + ", the finished nook has " + now
				+ " on its chair spot, not the vanilla spruce stair (upkeep looks: " + repairs[0] + ")");
			helper.assertTrue(manager.furniture(placement).isVanilla(), "the finished nook still records " + manager.furniture(placement));
			org.slf4j.LoggerFactory.getLogger("QaFurnitureModGone").info("restarted {}; the chair spot is {} by tick {} ({} upkeep looks)",
				restarted[0], now, helper.getTick(), repairs[0]);
		});
	}

	/**
	 * A site saved part-way through with the test mod's chair chosen loads on a server where that chair's block is gone:
	 * its plan has the vanilla chair on the spot, its materials ask for no gone or empty item, and it saves again as vanilla.
	 * A two-block piece with one block gone is dropped whole (never half a modded piece beside a vanilla one).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSiteSavedMidBuildPlansVanillaWhenItsFurnitureModIsGone(GameTestHelper helper) {
		List<String> mods = Furnishings.MODS;
		Leftovers.after(helper, () -> Furnishings.MODS = mods);
		ServerLevel level = helper.getLevel();
		Furnishings.MODS = List.of(QaFurnishedGameTests.PIECES);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", QaFurnishedGameTests.NOOK, placement);
		BuildPlan modded = site.plan(level);
		helper.assertTrue(modded != null && !site.furniture().isVanilla(), "the nook didn't choose the test mod's chair: " + site.furniture());
		BlockPos chairAt = placement.origin().offset(QaFurnishedGameTests.CHAIR);
		BlockState moddedChair = QaFurnishedGameTests.stepStates(modded).get(chairAt);
		helper.assertTrue(moddedChair != null && moddedChair.getValue(StairBlock.HALF) == Half.TOP, "the modded plan's chair is " + moddedChair);

		Furnishings.MODS = List.of();
		BuildSite loaded = BuildSite.load(modRemoved(site.save()));
		helper.assertTrue(loaded != null, "the site saved mid-build doesn't load once its furniture mod is gone");
		BuildPlan plan = loaded.plan(level);
		helper.assertTrue(plan != null, "the site saved mid-build has no plan once its furniture mod is gone");
		BlockState chair = QaFurnishedGameTests.stepStates(plan).get(chairAt);
		helper.assertTrue(vanillaChair(chair), "with its mod gone the site plans " + chair + " on the chair spot, not the vanilla spruce stair");
		Map<Item, Integer> materials = plan.materials();
		helper.assertTrue(!materials.containsKey(Items.AIR) && materials.getOrDefault(Items.SPRUCE_STAIRS, 0) >= 1,
			"with its mod gone the site's materials are " + materials);
		helper.assertTrue(loaded.furniture().isVanilla(), "with its mod gone the site still holds " + loaded.furniture());
		CompoundTag again = loaded.save();
		helper.assertTrue(!again.toString().contains("handcrafted:"), "saved again, the site still names the gone mod's block: " + again);

		// A two-block piece (a chair and its footrest) with only the footrest's block gone is dropped whole
		Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
		cells.put(BlockPos.ZERO, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP));
		cells.put(new BlockPos(0, 0, 1), Blocks.SPRUCE_SLAB.defaultBlockState());
		FurnitureChoice two = new FurnitureChoice(Map.of(0, new FurnitureChoice.Placed(QaFurnishedGameTests.CHAIR, FurnitureSpot.Kind.CHAIR,
			"handcrafted", cells)));
		CompoundTag holder = new CompoundTag();
		holder.put("furniture", two.save());
		String snbt = holder.toString().replace("minecraft:spruce_slab", "handcrafted:spruce_footrest");
		FurnitureChoice half;
		try {
			half = FurnitureChoice.load(TagParser.parseTag(snbt).getList("furniture", net.minecraft.nbt.Tag.TAG_COMPOUND));
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new GameTestAssertException("the edited choice doesn't parse: " + e.getMessage());
		}
		helper.assertTrue(half.spots().isEmpty(), "a piece with one of its two blocks gone loads as " + half + ", not dropped whole");

		// Only the gone mod's piece is dropped: a piece of a mod still here, in the same saved choice, loads back whole
		Map<BlockPos, BlockState> table = new LinkedHashMap<>();
		table.put(BlockPos.ZERO, Blocks.SPRUCE_PLANKS.defaultBlockState());
		FurnitureChoice mixed = new FurnitureChoice(Map.of(
			0, new FurnitureChoice.Placed(QaFurnishedGameTests.CHAIR, FurnitureSpot.Kind.CHAIR, "handcrafted", cells),
			1, new FurnitureChoice.Placed(new BlockPos(0, 1, 0), FurnitureSpot.Kind.TABLE, QaFurnishedGameTests.PIECES, table)));
		CompoundTag mixedHolder = new CompoundTag();
		mixedHolder.put("furniture", mixed.save());
		FurnitureChoice back;
		try {
			back = FurnitureChoice.load(TagParser.parseTag(mixedHolder.toString().replace("minecraft:spruce_slab", "handcrafted:spruce_footrest"))
				.getList("furniture", net.minecraft.nbt.Tag.TAG_COMPOUND));
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new GameTestAssertException("the edited choice doesn't parse: " + e.getMessage());
		}
		FurnitureChoice.Placed kept = back.spots().get(1);
		helper.assertTrue(back.spots().size() == 1 && kept != null && kept.mod().equals(QaFurnishedGameTests.PIECES)
			&& kept.cells().equals(table) && kept.kind() == FurnitureSpot.Kind.TABLE,
			"a choice with one gone piece and one present piece loads as " + back + ", wanted only the present table");
		helper.succeed();
	}
}
