package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.RepairAsks;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-b-1103-1238) for 36.15, the Builder's repair questions, written from builder.md ("Asking before a repair"),
 * what RepairAskGameTests and QaRepairCellarGameTests leave out: an operator who is nobody's friend may answer; a
 * stranger's chat answer is refused with the not-yours sentence naming the build; an answer for a spot with no build, or
 * for a build taken down since, gets the gone sentence and changes nothing; Stop mid-repair keeps what was put back and
 * loses no planks; a paused repair stays paused when more goes missing and Carry on repairs it all; a yes with the
 * builder busy waits and is taken up once they're free; a yes sends the nearest of two free builders, and a second yes
 * starts no second repair. (A [Yes] after the hole was mended by hand: QaRepairAsks1103bBugTests.)
 */
public class QaRepairAsks1103bGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:big_area";
	static final BlockPos HALL = new BlockPos(14, 2, 14);
	/** The finished wall: eight planks in a row along x from here. */
	static final BlockPos WALL = new BlockPos(6, 2, 6);
	static final int LENGTH = 8;
	static final BlockPos CHEST = new BlockPos(2, 2, 4);
	static final BlockPos BENCH = new BlockPos(2, 2, 2);
	static final int PLANKS = 16;

	record Stage(ServerPlayer owner, List<Component> seen, Villager builder, BlueprintData.Placement placement, BlockPos at, ResourceLocation structure,
		Container chest) {
		String name() {
			return Blueprints.displayName(structure).getString();
		}
	}

	/**
	 * The hall, a builder hired by the owner (a player whose messages are recorded) at a bench with 16 planks beside it,
	 * and the owner's finished eight-plank wall with the planks at {@code holes} knocked out.
	 */
	static Stage stage(GameTestHelper helper, int... holes) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		VillageHalls.RADIUS = 16;
		Upkeep.ENABLED = true;
		Upkeep.ASK = true;
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, seen, "qa_b_owner");
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Upkeep.ENABLED = enabled;
			Upkeep.ASK = asks;
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (int x = 0; x < LENGTH; x++) {
			helper.setBlock(WALL.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_ask_b_" + Long.toHexString(level.getGameTime())
			+ "_" + helper.absolutePos(WALL).getX() + "_" + helper.absolutePos(WALL).getZ());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(WALL), new Vec3i(LENGTH, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int hole : holes) {
			helper.setBlock(WALL.offset(hole, 0, 0), Blocks.AIR);
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(WALL), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, owner.getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, PLANKS));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = hire(helper, owner, BENCH, new BlockPos(3, 2, 3));
		return new Stage(owner, seen, builder, placement, helper.absolutePos(WALL), id, chest);
	}

	static Villager hire(GameTestHelper helper, ServerPlayer owner, BlockPos bench, BlockPos at) {
		Villager builder = helper.spawn(EntityType.VILLAGER, at);
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(bench));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		return builder;
	}

	static void look(GameTestHelper helper, Villager builder) {
		if (!ModAttachments.BUILDER_JOB.has(builder)) {
			Upkeep.look(helper.getLevel(), builder);
		}
	}

	static RepairAsks.Ask ask(GameTestHelper helper, Stage s) {
		return BuildSiteManager.get(helper.getLevel()).repairAsk(s.placement());
	}

	/** Runs the chat button's command as {@code player} for the spot {@code at}, as a click on [Yes] / [Not now] does. */
	static int click(GameTestHelper helper, ServerPlayer player, String action, BlockPos at) {
		try {
			return helper.getLevel().getServer().getCommands().getDispatcher().execute(RepairAsks.command(action, at).substring(1),
				player.createCommandSourceStack());
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new GameTestAssertException("the chat's [" + action + "] command didn't parse: " + e.getMessage());
		}
	}

	static ChoiceMenu openPage(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		menu.press(HallPages.slot(RepairsPage.PAGE), player);
		helper.assertTrue(menu.icon(4).is(Items.ANVIL), "the Repairs page didn't open: " + menu.icon(4));
		return menu;
	}

	/** The page's row for the wall. */
	static int rowOf(GameTestHelper helper, Stage s) {
		List<RepairAsks.Entry> entries = RepairAsks.entries(helper.getLevel(), helper.absolutePos(HALL));
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).building().placement().equals(s.placement())) {
				return VillageHallScreen.FIRST_ROW + i;
			}
		}
		throw new GameTestAssertException("the wall isn't on the Repairs page");
	}

	static boolean onPage(GameTestHelper helper, Stage s) {
		return RepairAsks.entries(helper.getLevel(), helper.absolutePos(HALL)).stream().anyMatch(e -> e.building().placement().equals(s.placement()));
	}

	/** The last message {@code seen} got (chat or action bar), as read. */
	static String last(List<Component> seen) {
		return seen.isEmpty() ? "(nothing)" : seen.get(seen.size() - 1).getString();
	}

	static void plain(GameTestHelper helper, String line) {
		helper.assertFalse(line.contains("%s") || line.contains("%1$") || line.contains("aliveworkplace."), "a placeholder or key left in: " + line);
	}

	static int filled(GameTestHelper helper, int... holes) {
		int n = 0;
		for (int hole : holes) {
			if (helper.getBlockState(WALL.offset(hole, 0, 0)).is(Blocks.OAK_PLANKS)) {
				n++;
			}
		}
		return n;
	}

	/** Every oak plank there is: in the chest, the builder's bag, the wall, and lying on the ground. */
	static int planksEverywhere(GameTestHelper helper, Stage s) {
		int n = s.chest().countItem(Items.OAK_PLANKS) + ModAttachments.BUILDER_BAG.getOrCreate(s.builder()).count(Items.OAK_PLANKS);
		for (int x = 0; x < LENGTH; x++) {
			if (helper.getBlockState(WALL.offset(x, 0, 0)).is(Blocks.OAK_PLANKS)) {
				n++;
			}
		}
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(40);
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.OAK_PLANKS))) {
			n += item.getItem().getCount();
		}
		return n;
	}

	/**
	 * An operator who is neither the owner nor their friend nor the village's leader may answer (builder.md: "Its owner,
	 * their friends, the village's leader and operators may answer"): their chat [Yes] counts, sets the builder to it and
	 * tells them it's agreed, naming the wall.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_ask_b_op"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_ask_b_op")
	public void anOperatorWhoIsNobodysFriendMayAnswer(GameTestHelper helper) {
		Stage s = stage(helper, 1);
		ServerLevel level = helper.getLevel();
		List<Component> heard = new ArrayList<>();
		ServerPlayer op = WonderFinishedGameTests.listener(helper, heard, "qa_b_op");
		var players = level.getServer().getPlayerList();
		helper.runAfterDelay(2, () -> {
			players.getOps().add(new net.minecraft.server.players.ServerOpListEntry(op.getGameProfile(), 4, false));
			Leftovers.after(helper, () -> players.deop(op.getGameProfile()));
			helper.assertTrue(op.hasPermissions(2), "the test's operator isn't one");
			look(helper, s.builder());
			helper.assertTrue(ask(helper, s) != null && ask(helper, s).state() == RepairAsks.State.ASKED, "not asked: " + ask(helper, s));
			helper.assertTrue(click(helper, op, "yes", s.at()) == 1, "the operator's yes was refused: " + last(heard));
			helper.assertTrue(ask(helper, s).state() == RepairAsks.State.YES, "the operator's yes didn't count: " + ask(helper, s));
			BuildSite site = RepairAsks.repairOf(level, s.placement());
			helper.assertTrue(site != null && s.builder().getUUID().equals(site.builder()), "the operator's yes didn't set the builder to it: " + site);
			String reply = last(heard);
			helper.assertTrue(reply.equals("Yes: a builder will repair the " + s.name() + "."), "the operator was told: " + reply);
			plain(helper, reply);
			helper.succeed();
		});
	}

	/**
	 * A stranger (not the owner, a friend, the leader or an operator) clicking the chat's [Yes] and [Not now]: both are
	 * refused with "The <build> isn't yours to answer for: ..." naming the build, the question still waits, and nothing
	 * is started.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_ask_b_stranger"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_ask_b_stranger")
	public void aStrangersChatAnswersAreRefusedByName(GameTestHelper helper) {
		Stage s = stage(helper, 1);
		ServerLevel level = helper.getLevel();
		List<Component> heard = new ArrayList<>();
		ServerPlayer stranger = WonderFinishedGameTests.listener(helper, heard, "qa_b_stranger");
		helper.runAfterDelay(2, () -> {
			helper.assertFalse(stranger.hasPermissions(2), "the stranger is an operator");
			look(helper, s.builder());
			String refused = "The " + s.name() + " isn't yours to answer for: its owner, their friends or the village's leader decide.";
			helper.assertTrue(click(helper, stranger, "yes", s.at()) == 0, "a stranger's [Yes] command succeeded");
			helper.assertTrue(last(heard).equals(refused), "the stranger's [Yes] was answered: " + last(heard));
			helper.assertTrue(click(helper, stranger, "notnow", s.at()) == 0, "a stranger's [Not now] command succeeded");
			helper.assertTrue(last(heard).equals(refused), "the stranger's [Not now] was answered: " + last(heard));
			plain(helper, refused);
			helper.assertTrue(ask(helper, s).state() == RepairAsks.State.ASKED, "a stranger's answer counted: " + ask(helper, s));
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) == null && !ModAttachments.BUILDER_JOB.has(s.builder()), "a stranger started the repair");
			helper.succeed();
		});
	}

	/**
	 * Answers for a spot where no finished build stands (a block of the wall that isn't its origin, a spot nearby) and for
	 * the wall after it was taken down: the gone sentence, the command fails, and nothing is asked, agreed or started.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_ask_b_gone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_ask_b_gone")
	public void answersForABuildThatIsGoneChangeNothing(GameTestHelper helper) {
		Stage s = stage(helper, 1);
		ServerLevel level = helper.getLevel();
		String gone = "That build is gone, or nothing is missing from it any more.";
		helper.runAfterDelay(2, () -> {
			look(helper, s.builder());
			for (BlockPos spot : new BlockPos[] {s.at().east(2), s.at().south(4)}) {
				for (String action : new String[] {"yes", "notnow"}) {
					helper.assertTrue(click(helper, s.owner(), action, spot) == 0, "[" + action + "] for " + spot.toShortString() + " succeeded");
					helper.assertTrue(last(s.seen()).equals(gone), "[" + action + "] for an empty spot said: " + last(s.seen()));
				}
			}
			helper.assertTrue(ask(helper, s).state() == RepairAsks.State.ASKED, "an answer for another spot changed the wall's: " + ask(helper, s));
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) == null && !ModAttachments.BUILDER_JOB.has(s.builder()), "a repair started");
			// the wall is taken down; the old chat question's [Yes] is clicked afterwards
			BuildSiteManager.get(level).forgetFinished(s.placement());
			helper.assertTrue(click(helper, s.owner(), "yes", s.at()) == 0, "[Yes] for a build taken down succeeded");
			helper.assertTrue(last(s.seen()).equals(gone), "[Yes] for a build taken down said: " + last(s.seen()));
			helper.assertTrue(BuildSiteManager.get(level).all().stream().noneMatch(site -> site.placement().equals(s.placement())),
				"a site was made for a build taken down");
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "the builder was set to a build taken down");
			helper.succeed();
		});
	}

	/**
	 * Stop while the builder repairs a wall with four holes: whatever was put back stays (builder.md: "Pausing or stopping
	 * ends the repair there and then (what is put back stays ...)"), not one plank is lost or doubled between the chest,
	 * the builder's bag, the wall and the ground, and nothing more is placed afterwards.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa_ask_b_stop_mid"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_ask_b_stop_mid")
	public void stoppingMidRepairKeepsWhatWasPutBackAndLosesNoPlanks(GameTestHelper helper) {
		int[] holes = {1, 3, 5, 7};
		Stage s = stage(helper, holes);
		ServerLevel level = helper.getLevel();
		int total = PLANKS + LENGTH - holes.length;
		long[] stoppedAt = {-1};
		int[] keptAtStop = {0};
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(planksEverywhere(helper, s) == total, "the count at the start: " + planksEverywhere(helper, s));
			look(helper, s.builder());
			helper.assertTrue(click(helper, s.owner(), "yes", s.at()) == 1, "the owner's yes was refused");
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) != null, "yes didn't start the repair");
		});
		helper.onEachTick(() -> {
			if (stoppedAt[0] >= 0 || helper.getTick() < 3) {
				return;
			}
			int now = filled(helper, holes);
			if (now == holes.length) {
				throw new GameTestAssertException("the whole repair finished before it could be stopped mid-way");
			}
			if (now >= 1) {
				ChoiceMenu menu = openPage(helper, s.owner());
				menu.press(rowOf(helper, s), s.owner());
				helper.assertTrue(menu.icon(RepairsPage.STOP).is(Items.BARRIER), "no Stop while it's repaired: " + menu.icon(RepairsPage.STOP));
				menu.press(RepairsPage.STOP, s.owner());
				stoppedAt[0] = helper.getTick();
				keptAtStop[0] = now;
				helper.assertTrue(RepairAsks.repairOf(level, s.placement()) == null, "stop didn't end the repair");
				helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "the builder is still on the repair");
				helper.assertTrue(filled(helper, holes) == now, "stopping took back what was put back");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stoppedAt[0] >= 0 && helper.getTick() > stoppedAt[0] + 200, "not stopped long enough yet");
			look(helper, s.builder());
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) == null, "the stopped repair started again by itself");
			helper.assertTrue(filled(helper, holes) == keptAtStop[0], "after the stop the wall has " + filled(helper, holes) + " of the holes filled, at the stop "
				+ keptAtStop[0]);
			RepairAsks.Ask a = ask(helper, s);
			helper.assertTrue(a != null && a.state() == RepairAsks.State.NOT_NOW, "stopped, the wall isn't left for now: " + a);
			int count = planksEverywhere(helper, s);
			helper.assertTrue(count == total, "planks lost or doubled by the stop: " + count + " instead of " + total + " (chest "
				+ s.chest().countItem(Items.OAK_PLANKS) + ", bag " + ModAttachments.BUILDER_BAG.getOrCreate(s.builder()).count(Items.OAK_PLANKS) + ")");
		});
	}

	/**
	 * A paused repair stays paused when another block goes missing (builder.md: "Pause leaves it until Carry on"): no new
	 * question in chat, no repair; Carry on then puts back both holes and the wall comes off the list.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa_ask_b_paused_more"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_ask_b_paused_more")
	public void aPausedRepairStaysPausedWhenMoreGoesMissing(GameTestHelper helper) {
		Stage s = stage(helper, 1);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			look(helper, s.builder());
			RepairAsks.yes(s.owner(), s.at());
			ChoiceMenu menu = openPage(helper, s.owner());
			menu.press(rowOf(helper, s), s.owner());
			menu.press(RepairsPage.PAUSE, s.owner());
			helper.assertTrue(ask(helper, s).state() == RepairAsks.State.PAUSED, "not paused: " + ask(helper, s));
			helper.assertTrue(last(s.seen()).startsWith("The repair of the " + s.name() + " is paused"), "the pause's line: " + last(s.seen()));
			int questions = (int) s.seen().stream().filter(c -> c.getString().contains("Repair it?")).count();
			helper.setBlock(WALL.offset(4, 0, 0), Blocks.AIR); // more goes missing while paused
			look(helper, s.builder());
			look(helper, s.builder());
			helper.assertTrue(ask(helper, s).state() == RepairAsks.State.PAUSED, "more damage undid the pause: " + ask(helper, s));
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) == null && !ModAttachments.BUILDER_JOB.has(s.builder()), "a paused repair started");
			helper.assertTrue(s.seen().stream().filter(c -> c.getString().contains("Repair it?")).count() == questions, "asked again while paused");
			menu = openPage(helper, s.owner());
			menu.press(rowOf(helper, s), s.owner());
			helper.assertTrue(menu.icon(RepairsPage.YES).getHoverName().getString().equals("Carry on"), "no Carry on: " + menu.icon(RepairsPage.YES));
			menu.press(RepairsPage.YES, s.owner());
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) != null, "Carry on didn't start it again");
		});
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, WALL.offset(1, 0, 0));
			helper.assertBlockPresent(Blocks.OAK_PLANKS, WALL.offset(4, 0, 0));
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "still repairing");
			helper.assertTrue(ask(helper, s) == null && !onPage(helper, s), "still listed once repaired: " + ask(helper, s));
		});
	}

	/**
	 * Yes while the only builder is busy with a build: nothing is pulled off that build, the wall waits agreed on the page,
	 * and once the builder is free their look takes the repair up with no new question.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_ask_b_busy"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_ask_b_busy")
	public void aYesWhileTheBuilderIsBusyWaitsAndIsTakenUpLater(GameTestHelper helper) {
		Stage s = stage(helper, 1);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			look(helper, s.builder());
			helper.assertTrue(ask(helper, s).state() == RepairAsks.State.ASKED, "not asked: " + ask(helper, s));
			ItemStack blueprint = BlueprintItem.create(s.structure(), new Vec3i(LENGTH, 1, 1));
			blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(
				new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(4, 2, 12)), Rotation.NONE, Mirror.NONE))));
			Builders.assign(s.owner(), s.builder(), blueprint);
			BuildSite job = Builders.activeSite(level, s.builder());
			helper.assertTrue(job != null && !job.isRepair(), "the builder didn't take the build: " + job);
			int questions = (int) s.seen().stream().filter(c -> c.getString().contains("Repair it?")).count();
			helper.assertTrue(click(helper, s.owner(), "yes", s.at()) == 1, "the yes was refused");
			helper.assertTrue(ask(helper, s).state() == RepairAsks.State.YES, "the yes didn't count: " + ask(helper, s));
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) == null, "a repair started with no builder free");
			BuildSite still = Builders.activeSite(level, s.builder());
			helper.assertTrue(still != null && still.id().equals(job.id()), "the yes pulled the builder off their build: " + still);
			helper.assertTrue(onPage(helper, s), "the agreed wall isn't on the page while it waits");
			Builders.cancel(level, job); // the build is over: free again
			look(helper, s.builder());
			BuildSite repair = RepairAsks.repairOf(level, s.placement());
			helper.assertTrue(repair != null && s.builder().getUUID().equals(repair.builder()), "the free builder didn't take up the agreed repair: " + repair);
			helper.assertTrue(s.seen().stream().filter(c -> c.getString().contains("Repair it?")).count() == questions, "asked again after a yes");
			helper.succeed();
		});
	}

	/**
	 * Yes sends the nearest free builder who looks after the wall (builder.md: "Yes sets the nearest free Builder who looks
	 * after it to it at once"), even when another was hired first; a second [Yes] starts no second repair.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_ask_b_nearest"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_ask_b_nearest")
	public void aYesSendsTheNearestFreeBuilderAndASecondYesNoSecondRepair(GameTestHelper helper) {
		Stage s = stage(helper, 1);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(12, 2, 2), ModBlocks.BUILDERS_BENCH);
		Villager near = hire(helper, s.owner(), new BlockPos(12, 2, 2), new BlockPos(9, 2, 8));
		helper.runAfterDelay(2, () -> {
			// the far builder (hired first) finds the hole and asks
			Upkeep.look(level, s.builder());
			helper.assertTrue(ask(helper, s) != null && ask(helper, s).state() == RepairAsks.State.ASKED, "not asked: " + ask(helper, s));
			BlockPos spot = Builders.spot(level, s.structure(), s.placement());
			double far = s.builder().distanceToSqr(spot.getX(), spot.getY(), spot.getZ());
			double close = near.distanceToSqr(spot.getX(), spot.getY(), spot.getZ());
			helper.assertTrue(close < far, "the set-up's near builder isn't nearer: " + close + " vs " + far);
			helper.assertTrue(click(helper, s.owner(), "yes", s.at()) == 1, "the yes was refused");
			BuildSite site = RepairAsks.repairOf(level, s.placement());
			helper.assertTrue(site != null && near.getUUID().equals(site.builder()), "yes didn't send the nearest free builder: " + (site == null ? null
				: site.builder().equals(s.builder().getUUID()) ? "the far one" : site.builder()));
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "the far builder was set to it too");
			helper.assertTrue(click(helper, s.owner(), "yes", s.at()) == 1, "the second yes was refused");
			long repairs = BuildSiteManager.get(level).all().stream().filter(x -> x.placement().equals(s.placement())).count();
			helper.assertTrue(repairs == 1, "a second yes made " + repairs + " repairs");
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "the second yes set the far builder to it");
			helper.succeed();
		});
	}
}
