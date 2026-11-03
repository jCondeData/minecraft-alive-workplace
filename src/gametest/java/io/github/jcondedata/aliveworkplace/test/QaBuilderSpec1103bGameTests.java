package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.LOGGER;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.Run;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b-1103-1238) of docs/wiki/builder.md section 2, "How it works", written from the spec sentences that no test
 * pinned down yet:
 * <ul>
 * <li>Working hours: "A Builder works from early morning (1000) to dusk (11000), longer than vanilla villagers" (vanilla
 * starts work at 2000). The tests that exist hold the day at 2000 or 6000 and check dusk and night, never the early hour
 * nor the last minutes before dusk.</li>
 * <li>Who may order: "With workplaceBuilderOwnership on, a Builder takes orders only from the player who hired it, their
 * friends (/workplace friend add|remove|list) and operators." Existing tests cover a stranger, a friend added and a
 * friend cancelling; these cover an operator who is nobody's friend handing over a blueprint by a click, a friend
 * removed again, and a friendship the wrong way round (the stranger named the owner, not the owner the stranger).</li>
 * <li>Materials: "A fixed set of blocks is never placed (bedrock, command blocks, spawners, portals, barrier, fire...)",
 * the spawners, bedrock, repeating command block and soul fire QaBuildStagesGameTests' row leaves out.</li>
 * <li>Table preview: "air, invisible blocks, jigsaw and structure blocks are dropped, and a cell whose six neighbors all
 * occlude is left out", on small blueprints from both sides (stone and glass cubes).</li>
 * </ul>
 * (A hall's Upgrades order handed to another player's hired Builder: QaBuilderSpec1103bBugTests.)
 */
public class QaBuilderSpec1103bGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:build_area";
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos BENCH = new BlockPos(2, 2, 2);
	static final BlockPos CHEST = new BlockPos(2, 2, 4);
	static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	// ===================================================================================================== working hours

	/**
	 * "works from early morning (1000)": mid-STRUCTURE the clock is put back to 500 (before the working day) and held
	 * there. From 200 ticks on, for 500 ticks, no block of the hut changes. Then the clock is held between 1000 and 1019
	 * (never reaching vanilla's 2000 work start): he must lay at least three blocks in 400 ticks and finish.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qaSpec1103bDawn"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qaSpec1103bDawn")
	public void aBuilderLaysNothingBefore1000AndBuildsFrom1000(GameTestHelper helper) {
		Run r = slowHut(helper);
		int[] phase = {0};
		long[] since = {-1};
		int[] unbuilt = {-1};
		long[] morning = {-1};
		int[] atMorning = {-1};
		boolean[] checked = {false};
		helper.onEachTick(() -> {
			if (phase[0] == 0) {
				helper.setDayTime(2000);
				return;
			}
			if (phase[0] == 2) {
				long m = helper.getTick() - morning[0];
				helper.setDayTime((int) (1000 + m % 20));
				if (!checked[0] && m >= 400) {
					checked[0] = true;
					int now = r.plan().unfinished(r.level()).size();
					helper.assertTrue(atMorning[0] - now >= 3 || r.site() == null, "between 1000 and 1019 he laid only " + (atMorning[0] - now)
						+ " block(s) in 400 ticks (activity " + activity(r.builder()[0]) + ", " + QaBuilderTerrain1102GameTests.state(helper, r) + ")");
				}
				return;
			}
			helper.setDayTime(500);
			long t = helper.getTick() - since[0];
			int now = r.plan().unfinished(r.level()).size();
			if (t == 200) {
				helper.assertTrue(r.site() != null, "setup: the hut was done before the clock went back");
				unbuilt[0] = now;
			} else if (t > 200) {
				helper.assertTrue(now == unbuilt[0], "the hut changed at day time 500, before the working day: " + unbuilt[0] + " unbuilt, then " + now
					+ " (activity " + activity(r.builder()[0]) + ")");
				if (t >= 700) {
					phase[0] = 2;
					morning[0] = helper.getTick();
					atMorning[0] = now;
					helper.setDayTime(1000);
				}
			}
		});
		midStructureWithABag(helper, r, () -> {
			phase[0] = 1;
			since[0] = helper.getTick();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(checked[0], "the early morning isn't watched yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	/**
	 * "to dusk (11000)": mid-STRUCTURE the clock is held between 10900 and 10919, the last minutes before dusk. He keeps
	 * working there: at least three blocks in 400 ticks, and he finishes.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qaSpec1103bBeforeDusk"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qaSpec1103bBeforeDusk")
	public void aBuilderKeepsBuildingInTheLastMinutesBeforeDusk(GameTestHelper helper) {
		Run r = slowHut(helper);
		long[] since = {-1};
		int[] unbuilt = {-1};
		boolean[] checked = {false};
		helper.onEachTick(() -> {
			if (since[0] < 0) {
				helper.setDayTime(2000);
				return;
			}
			long t = helper.getTick() - since[0];
			helper.setDayTime((int) (10900 + t % 20));
			if (!checked[0] && t >= 400) {
				int now = r.plan().unfinished(r.level()).size();
				checked[0] = true;
				helper.assertTrue(unbuilt[0] - now >= 3 || r.site() == null, "between 10900 and 10919 he laid only " + (unbuilt[0] - now)
					+ " block(s) in 400 ticks (activity " + activity(r.builder()[0]) + ", " + QaBuilderTerrain1102GameTests.state(helper, r) + ")");
				LOGGER.info("qa spec1103b before dusk: {} blocks laid in 400 ticks", unbuilt[0] - now);
			}
		});
		midStructureWithABag(helper, r, () -> {
			since[0] = helper.getTick();
			unbuilt[0] = r.plan().unfinished(r.level()).size();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(checked[0], "the late afternoon isn't watched yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	/**
	 * "then idles and goes to bed at 12000": mid-STRUCTURE the clock is held at 11500 (after dusk, before bed). For 300
	 * ticks his activity is idle, not rest, and nothing is built. Then held at 12500: his activity is rest and within
	 * 1200 ticks he lies in his bed by the bench. In the morning he finishes, nothing made or lost.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qaSpec1103bBedtime"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qaSpec1103bBedtime")
	public void aBuilderIdlesAfterDuskAndIsInBedAfter12000(GameTestHelper helper) {
		Run r = slowHut(helper);
		int[] phase = {0};
		long[] since = {-1};
		int[] unbuilt = {-1};
		helper.onEachTick(() -> {
			if (phase[0] == 0) {
				helper.setDayTime(2000);
				return;
			}
			if (phase[0] == 3) {
				if (helper.getTick() % 20 == 0) {
					helper.setDayTime(2000);
				}
				return;
			}
			long t = helper.getTick() - since[0];
			Villager b = r.builder()[0];
			int now = r.plan().unfinished(r.level()).size();
			if (phase[0] == 1) {
				helper.setDayTime(11500);
				if (t == 150) {
					unbuilt[0] = now;
				} else if (t > 150) {
					helper.assertTrue(now == unbuilt[0], "the hut changed at 11500, after dusk: " + unbuilt[0] + " unbuilt, then " + now);
					helper.assertTrue(activity(b).equals(net.minecraft.world.entity.schedule.Activity.IDLE.toString()),
						"at 11500 his activity is " + activity(b) + ", not idle (asleep=" + b.isSleeping() + ")");
					helper.assertFalse(b.isSleeping(), "in bed at 11500, before bedtime");
					if (t >= 450) {
						phase[0] = 2;
						since[0] = helper.getTick();
					}
				}
				return;
			}
			helper.setDayTime(12500);
			helper.assertTrue(now == unbuilt[0], "the hut changed at 12500: " + unbuilt[0] + " unbuilt, then " + now);
			if (t >= 40) {
				helper.assertTrue(activity(b).equals(net.minecraft.world.entity.schedule.Activity.REST.toString()), "at 12500 his activity is " + activity(b) + ", not rest");
			}
			if (b.isSleeping()) {
				LOGGER.info("qa spec1103b bedtime: in bed {} ticks after 12500", t);
				phase[0] = 3;
				helper.setDayTime(1000);
			} else {
				helper.assertTrue(t < 1200, "not in bed 1200 ticks after 12500 (activity " + activity(b) + ", at " + helper.relativePos(b.blockPosition()) + ")");
			}
		});
		midStructureWithABag(helper, r, () -> {
			phase[0] = 1;
			since[0] = helper.getTick();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 3, "the night isn't over yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	// ===================================================================================================== who may order

	/**
	 * "and operators": with ownership on, an operator who is neither the hirer nor anyone's friend right-clicks the
	 * owner's builder with a placed blueprint. Not refused, and the builder takes it (a build starts, the blueprint is
	 * gone from his hand).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSpec1103bOpOrders"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSpec1103bOpOrders")
	public void anOperatorWhoIsNobodysFriendGivesAHiredBuilderABlueprint(GameTestHelper helper) {
		Leftovers.clear(helper);
		ownershipOn(helper);
		QaStageFiveGameTests.Client owner = survivor(helper, "qs3boss");
		QaStageFiveGameTests.Client other = survivor(helper, "qs3op");
		ServerPlayer o = owner.player();
		ServerPlayer p = other.player();
		QaStageSixGameTests.profileCache(helper, o, p);
		Villager builder = hiredBuilder(helper, o);
		var players = helper.getLevel().getServer().getPlayerList();
		players.getOps().add(new net.minecraft.server.players.ServerOpListEntry(p.getGameProfile(), 4, false));
		Leftovers.after(helper, () -> players.deop(p.getGameProfile()));
		helper.assertTrue(p.hasPermissions(2), "setup: the operator isn't one");
		helper.assertFalse(Friends.get(helper.getLevel().getServer()).mayDirect(o.getUUID(), p.getUUID()), "setup: the operator is the owner's friend");
		p.setItemInHand(InteractionHand.MAIN_HAND, placedHut(helper, new BlockPos(6, 2, 6)));
		QaStageSixGameTests.clickVillager(p, builder, other.seen());
		QaStageFiveGameTests.closeScreen(p);
		helper.assertFalse(QaStageFourGameTests.saw(other.seen(), "message.aliveworkplace.not_your_builder"), "the operator was refused: " + keys(other));
		helper.assertTrue(sitesOf(helper, builder).size() == 1, "the operator's blueprint didn't start a build: " + keys(other));
		helper.assertFalse(p.getMainHandItem().is(ModItems.BLUEPRINT), "the operator still holds the blueprint the builder took");
		helper.succeed();
	}

	/**
	 * "/workplace friend add|remove": the owner adds a player, then removes him with /workplace friend remove ("can no
	 * longer give orders"). That player's placed blueprint, clicked on the owner's builder, is refused again with the
	 * not-your-builder sentence, stays in his hand and starts nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSpec1103bFriendRemoved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSpec1103bFriendRemoved")
	public void aFriendRemovedIsRefusedOrdersAgain(GameTestHelper helper) {
		Leftovers.clear(helper);
		ownershipOn(helper);
		QaStageFiveGameTests.Client owner = survivor(helper, "qs3own");
		QaStageFiveGameTests.Client other = survivor(helper, "qs3ex");
		ServerPlayer o = owner.player();
		ServerPlayer p = other.player();
		QaStageSixGameTests.profileCache(helper, o, p);
		Leftovers.after(helper, () -> Friends.get(helper.getLevel().getServer()).remove(o.getUUID(), p.getUUID()));
		Villager builder = hiredBuilder(helper, o);
		QaStageSixGameTests.command(owner, "/workplace friend add " + p.getGameProfile().getName());
		helper.assertTrue(QaStageFourGameTests.saw(owner.seen(), "command.aliveworkplace.friend.added"), "friend add: " + keys(owner));
		QaStageSixGameTests.command(owner, "/workplace friend remove " + p.getGameProfile().getName());
		helper.assertTrue(QaStageFourGameTests.saw(owner.seen(), "command.aliveworkplace.friend.removed"), "friend remove: " + keys(owner));
		p.setItemInHand(InteractionHand.MAIN_HAND, placedHut(helper, new BlockPos(6, 2, 6)));
		QaStageSixGameTests.clickVillager(p, builder, other.seen());
		QaStageFiveGameTests.closeScreen(p);
		helper.assertTrue(QaStageFourGameTests.saw(other.seen(), "message.aliveworkplace.not_your_builder"), "the removed friend wasn't refused: " + keys(other));
		helper.assertTrue(p.getMainHandItem().is(ModItems.BLUEPRINT) && sitesOf(helper, builder).isEmpty(),
			"the removed friend's blueprint was taken: " + sitesOf(helper, builder));
		helper.succeed();
	}

	/**
	 * "their friends": the friends the hirer names. A stranger who runs /workplace friend add with the owner's name has
	 * made the owner his friend, not himself the owner's: his blueprint on the owner's builder is still refused. Only
	 * once the owner names him does the builder take it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSpec1103bFriendWrongWay"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSpec1103bFriendWrongWay")
	public void namingTheOwnerYourFriendGivesNoOrdersOverHisBuilder(GameTestHelper helper) {
		Leftovers.clear(helper);
		ownershipOn(helper);
		QaStageFiveGameTests.Client owner = survivor(helper, "qs3hirer");
		QaStageFiveGameTests.Client other = survivor(helper, "qs3fan");
		ServerPlayer o = owner.player();
		ServerPlayer p = other.player();
		QaStageSixGameTests.profileCache(helper, o, p);
		Leftovers.after(helper, () -> {
			Friends.get(helper.getLevel().getServer()).remove(o.getUUID(), p.getUUID());
			Friends.get(helper.getLevel().getServer()).remove(p.getUUID(), o.getUUID());
		});
		Villager builder = hiredBuilder(helper, o);
		QaStageSixGameTests.command(other, "/workplace friend add " + o.getGameProfile().getName());
		helper.assertTrue(QaStageFourGameTests.saw(other.seen(), "command.aliveworkplace.friend.added"), "the stranger's friend add: " + keys(other));
		other.seen().clear();
		p.setItemInHand(InteractionHand.MAIN_HAND, placedHut(helper, new BlockPos(6, 2, 6)));
		QaStageSixGameTests.clickVillager(p, builder, other.seen());
		QaStageFiveGameTests.closeScreen(p);
		helper.assertTrue(QaStageFourGameTests.saw(other.seen(), "message.aliveworkplace.not_your_builder"),
			"a player who named the owner his friend gave the owner's builder orders: " + keys(other));
		helper.assertTrue(p.getMainHandItem().is(ModItems.BLUEPRINT) && sitesOf(helper, builder).isEmpty(), "his blueprint was taken: " + sitesOf(helper, builder));

		QaStageSixGameTests.command(owner, "/workplace friend add " + p.getGameProfile().getName());
		other.seen().clear();
		QaStageSixGameTests.clickVillager(p, builder, other.seen());
		QaStageFiveGameTests.closeScreen(p);
		helper.assertFalse(QaStageFourGameTests.saw(other.seen(), "message.aliveworkplace.not_your_builder"), "named by the owner, still refused: " + keys(other));
		helper.assertTrue(sitesOf(helper, builder).size() == 1, "named by the owner, his blueprint started no build: " + keys(other));
		helper.succeed();
	}

	// ===================================================================================================== never placed

	/**
	 * "A fixed set of blocks is never placed (bedrock, command blocks, spawners, portals, barrier, fire...)", the ones
	 * QaBuildStagesGameTests' row leaves out: a scan holding a monster spawner, bedrock, a trial spawner, a repeating
	 * command block and soul fire (on soul soil, where it burns for ever) between cobblestone. With an empty chest the
	 * Builder is short of the 2 cobblestone and the soul soil only; stocked, he finishes with none of the five put up
	 * and nothing in their place, and the chest emptied.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaSpec1103bNeverPlaced"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaSpec1103bNeverPlaced")
	public void spawnersBedrockARepeatingCommandBlockAndSoulFireAreLeftOut(GameTestHelper helper) {
		ServerLevel level = stageRules(helper);
		BlockPos origin = new BlockPos(6, 2, 9);
		List<Block> row = List.of(Blocks.SPAWNER, Blocks.COBBLESTONE, Blocks.BEDROCK, Blocks.TRIAL_SPAWNER, Blocks.REPEATING_COMMAND_BLOCK, Blocks.COBBLESTONE,
			Blocks.SOUL_SOIL);
		for (int x = 0; x < row.size(); x++) {
			helper.setBlock(origin.offset(x, 0, 0), row.get(x));
		}
		BlockPos fire = origin.offset(row.size() - 1, 1, 0);
		helper.setBlock(fire, Blocks.SOUL_FIRE);
		helper.assertBlockPresent(Blocks.SOUL_FIRE, fire);
		ResourceLocation id = scan(helper, "qaspec_never", origin, new Vec3i(row.size(), 2, 1));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, id, placement);
		helper.assertTrue(site != null, "the Builder took no build");
		boolean[] stocked = {false};
		helper.runAfterDelay(600, () -> {
			Map<Item, Integer> missing = site.missing();
			helper.assertTrue(missing.equals(Map.of(Items.COBBLESTONE, 2, Items.SOUL_SOIL, 1)),
				"with an empty chest the Builder should be short of 2 cobblestone and 1 soul soil only: " + missing);
			Container chest = helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.COBBLESTONE, 2));
			chest.setItem(1, new ItemStack(Items.SOUL_SOIL, 1));
			stocked[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stocked[0], "not stocked yet");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			for (int x = 0; x < row.size(); x++) {
				Block drawn = row.get(x);
				Block want = drawn == Blocks.COBBLESTONE || drawn == Blocks.SOUL_SOIL ? drawn : Blocks.AIR;
				helper.assertTrue(helper.getBlockState(origin.offset(x, 0, 0)).is(want), "where the scan had " + drawn + " the build has "
					+ helper.getBlockState(origin.offset(x, 0, 0)).getBlock() + ", not " + want);
			}
			helper.assertTrue(helper.getBlockState(fire).isAir(), "the Builder lit the soul fire: " + helper.getBlockState(fire));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0 && chest.countItem(Items.SOUL_SOIL) == 0, "materials left: "
				+ chest.countItem(Items.COBBLESTONE) + " cobblestone, " + chest.countItem(Items.SOUL_SOIL) + " soul soil");
		});
	}

	// ===================================================================================================== table preview

	/**
	 * "Only blocks a player could see are sent (air, invisible blocks, jigsaw and structure blocks are dropped ...)": a
	 * blueprint of a jigsaw, a structure block, a structure void, a light block, a barrier and air, with one stone, is
	 * one cell: the stone.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePreviewDropsJigsawStructureVoidLightBarrierAndAir(GameTestHelper helper) {
		List<Block> row = List.of(Blocks.JIGSAW, Blocks.STRUCTURE_BLOCK, Blocks.STRUCTURE_VOID, Blocks.LIGHT, Blocks.BARRIER, Blocks.AIR, Blocks.STONE);
		List<Blueprint.Entry> blocks = new ArrayList<>();
		for (int x = 0; x < row.size(); x++) {
			blocks.add(new Blueprint.Entry(new BlockPos(x, 0, 0), row.get(x).defaultBlockState(), null));
		}
		Blueprint b = new Blueprint(AliveWorkplace.id("test/qaspec_preview_hidden"), new Vec3i(row.size(), 1, 1), blocks);
		Map<BlockPos, BlockState> cells = cells(TableServer.buildPreview(b.id(), b));
		helper.assertTrue(cells.equals(Map.of(new BlockPos(row.size() - 1, 0, 0), Blocks.STONE.defaultBlockState())), "the preview's cells: " + cells);
		helper.succeed();
	}

	/**
	 * "... and a cell whose six neighbors all occlude is left out": a 3x3x3 stone cube is 26 cells, its middle left out;
	 * a glass cube (glass doesn't occlude) is all 27; a stone cube with glass in the middle is 26; a stone cube with one
	 * face's middle block glass shows its middle stone too (27).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePreviewLeavesOutOnlyACellWhoseSixNeighborsAllOcclude(GameTestHelper helper) {
		BlockPos middle = new BlockPos(1, 1, 1);
		Map<BlockPos, BlockState> stone = cube(helper, "stone", Blocks.STONE, Blocks.STONE, null);
		helper.assertTrue(stone.size() == 26 && !stone.containsKey(middle), "a stone cube: " + stone.size() + " cells, middle sent " + stone.containsKey(middle));
		Map<BlockPos, BlockState> glass = cube(helper, "glass", Blocks.GLASS, Blocks.GLASS, null);
		helper.assertTrue(glass.size() == 27, "a glass cube: " + glass.size() + " cells, not 27");
		Map<BlockPos, BlockState> core = cube(helper, "core", Blocks.STONE, Blocks.GLASS, null);
		helper.assertTrue(core.size() == 26 && !core.containsKey(middle), "a stone cube round glass: " + core.size() + " cells, middle sent " + core.containsKey(middle));
		Map<BlockPos, BlockState> window = cube(helper, "window", Blocks.STONE, Blocks.STONE, new BlockPos(1, 1, 0));
		helper.assertTrue(window.size() == 27 && Blocks.STONE.defaultBlockState().equals(window.get(middle)),
			"a stone cube with a glass window: " + window.size() + " cells, middle " + window.get(middle));
		helper.succeed();
	}

	/** A 3x3x3 cube's preview: {@code shell} all round, {@code core} in the middle, glass at {@code window} if not null. */
	private static Map<BlockPos, BlockState> cube(GameTestHelper helper, String name, Block shell, Block core, BlockPos window) {
		List<Blueprint.Entry> blocks = new ArrayList<>();
		for (int x = 0; x < 3; x++) {
			for (int y = 0; y < 3; y++) {
				for (int z = 0; z < 3; z++) {
					BlockPos p = new BlockPos(x, y, z);
					Block b = p.equals(window) ? Blocks.GLASS : x == 1 && y == 1 && z == 1 ? core : shell;
					blocks.add(new Blueprint.Entry(p, b.defaultBlockState(), null));
				}
			}
		}
		Blueprint b = new Blueprint(AliveWorkplace.id("test/qaspec_preview_" + name), new Vec3i(3, 3, 3), blocks);
		TablePayloads.Preview preview = TableServer.buildPreview(b.id(), b);
		helper.assertTrue(preview.scale() == 1, name + ": scale " + preview.scale());
		return cells(preview);
	}

	private static Map<BlockPos, BlockState> cells(TablePayloads.Preview preview) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		int[] b = preview.blocks();
		for (int i = 0; i < b.length; i += 4) {
			out.put(new BlockPos(b[i], b[i + 1], b[i + 2]), Block.stateById(preview.palette().get(b[i + 3])));
		}
		return out;
	}

	/** As QaBuildStagesGameTests.rules: by day, materials needed, a quick delay, no helpers, all restored after. */
	private static ServerLevel stageRules(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		return level;
	}

	/** A blueprint scanned from the world (as a player's Scan Tool does), the scanned blocks then taken away again. */
	private static ResourceLocation scan(GameTestHelper helper, String name, BlockPos origin, Vec3i size) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), size, false, Blocks.STRUCTURE_VOID);
		for (int y = size.getY() - 1; y >= 0; y--) {
			for (int x = 0; x < size.getX(); x++) {
				for (int z = 0; z < size.getZ(); z++) {
					level.setBlock(helper.absolutePos(origin.offset(x, y, z)), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		return id;
	}

	// ===================================================================================================== helpers

	private static void ownershipOn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = level.getGameRules().getBoolean(ModGameRules.BUILDER_OWNERSHIP);
		level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(was, level.getServer()));
	}

	private static QaStageFiveGameTests.Client survivor(GameTestHelper helper, String name) {
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(name));
		helper.assertFalse(c.player().hasPermissions(2), "the player is an operator; the check would prove nothing");
		return c;
	}

	private static List<String> keys(QaStageFiveGameTests.Client c) {
		return QaStageFourGameTests.keys(c.seen());
	}

	private static ItemStack placedHut(GameTestHelper helper, BlockPos rel) {
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, new Vec3i(5, 4, 5));
		BlueprintData data = BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(new BlueprintData.Placement(
			helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE)));
		blueprint.set(ModComponents.BLUEPRINT, data);
		return blueprint;
	}

	private static Villager hiredBuilder(GameTestHelper helper, ServerPlayer owner) {
		helper.setBlock(new BlockPos(1, 2, 5), ModBlocks.BUILDERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 4));
		v.setNoAi(true);
		Builders.employ(helper.getLevel(), v, helper.absolutePos(new BlockPos(1, 2, 5)));
		Friends.hire(owner, v);
		helper.assertTrue(ModAttachments.BUILDER_EMPLOYER.get(v).id().equals(owner.getUUID()), "setup: the owner didn't hire him");
		return v;
	}

	private static List<BuildSite> sitesOf(GameTestHelper helper, Villager builder) {
		return BuildSiteManager.get(helper.getLevel()).all().stream().filter(s -> builder.getUUID().equals(s.builder())).toList();
	}

	/** As QaBuilderFixes1103GameTests' night tests: prepare(), a slow builder (workplaceBuildDelay 40) with a bed by the bench. */
	private static Run slowHut(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(40, level.getServer());
		Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		Beds.home(r.builder()[0], helper.absolutePos(new BlockPos(1, 2, 8)));
		return r;
	}

	private static String activity(Villager b) {
		return b.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("none");
	}

	private static Map<Item, Integer> bag(Villager v, BuildPlan plan) {
		Map<Item, Integer> out = new HashMap<>();
		for (Item item : plan.materials().keySet()) {
			int n = ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
			if (n > 0) {
				out.put(item, n);
			}
		}
		return out;
	}

	/** Runs {@code then} once, mid-STRUCTURE: four or more blocks built since STRUCTURE began, and materials in the bag. */
	private static void midStructureWithABag(GameTestHelper helper, Run r, Runnable then) {
		int[] atStart = {-1};
		boolean[] done = {false};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (done[0] || s == null || s.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			int unbuilt = r.plan().unfinished(r.level()).size();
			if (atStart[0] < 0) {
				atStart[0] = unbuilt;
			}
			if (atStart[0] - unbuilt < 4 || bag(r.builder()[0], r.plan()).isEmpty()) {
				return;
			}
			done[0] = true;
			then.run();
		});
	}
}
