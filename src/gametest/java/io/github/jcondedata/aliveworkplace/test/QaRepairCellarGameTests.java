package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Cellars;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.build.RepairAsks;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModTrades;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane-b-1024-1237) for 36.15 (the Builder asks before repairing) and 36.7 (cellars and "Dig the cellar"): what
 * RepairAskGameTests and CellarGameTests leave out. Two builds answered differently, the question coming back after a
 * hand repair, a stranger's and a friend's click on the hall's page, the agreed repair outliving its builder, the switch
 * turned off with a question waiting and mid-repair, every sentence rendered with real arguments; the "more than half
 * solid" boundary from both sides through the builder's real trade refresh, the blueprint clicked onto an old build, and
 * a turned Smithy's cellar dug with nothing in the chests at first.
 */
public class QaRepairCellarGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final BlockPos WALL_A = new BlockPos(6, 2, 6);
	private static final BlockPos WALL_B = new BlockPos(6, 2, 10);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);

	private record Wall(BlueprintData.Placement placement, BlockPos at, BlockPos rel) {
	}

	private record Stage(ServerPlayer owner, Villager builder, Wall a, Wall b, Container chest) {
	}

	// --- 36.15 ---------------------------------------------------------------------------------------------------------

	private static Wall wall(GameTestHelper helper, BlockPos rel, UUID owner) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 4; x++) {
			helper.setBlock(rel.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_repair_" + Long.toHexString(level.getGameTime())
			+ "_" + helper.absolutePos(rel).getX() + "_" + helper.absolutePos(rel).getZ());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(rel), new Vec3i(4, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(rel.offset(1, 0, 0), Blocks.AIR); // knocked out
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, owner);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Wall(placement, helper.absolutePos(rel), rel);
	}

	/** The hall, a builder hired by the owner at a bench with planks, and two of the owner's finished walls, each with a hole. */
	private static Stage stage(GameTestHelper helper, boolean ask, boolean second) {
		return stage(helper, ask, second, null);
	}

	private static Stage stage(GameTestHelper helper, boolean ask, boolean second, @org.jetbrains.annotations.Nullable List<Component> seen) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		VillageHalls.RADIUS = 16;
		Upkeep.ENABLED = true;
		Upkeep.ASK = ask;
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		ServerPlayer owner = seen != null ? WonderFinishedGameTests.listener(helper, seen, "qa_owner") : helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Upkeep.ENABLED = enabled;
			Upkeep.ASK = asks;
			level.getServer().getPlayerList().remove(owner);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Wall a = wall(helper, WALL_A, owner.getUUID());
		Wall b = second ? wall(helper, WALL_B, owner.getUUID()) : null;
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 16));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = hire(helper, owner, BENCH, new BlockPos(3, 2, 3));
		return new Stage(owner, builder, a, b, chest);
	}

	private static Villager hire(GameTestHelper helper, ServerPlayer owner, BlockPos bench, BlockPos at) {
		Villager builder = helper.spawn(EntityType.VILLAGER, at);
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(bench));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		return builder;
	}

	private static void look(GameTestHelper helper, Villager builder) {
		if (!ModAttachments.BUILDER_JOB.has(builder)) {
			Upkeep.look(helper.getLevel(), builder);
		}
	}

	private static RepairAsks.Ask ask(GameTestHelper helper, Wall w) {
		return BuildSiteManager.get(helper.getLevel()).repairAsk(w.placement());
	}

	/** Runs the chat button's command as {@code player}, as a click on [Yes] / [Not now] does. */
	private static int click(GameTestHelper helper, ServerPlayer player, String action, Wall w) {
		try {
			return helper.getLevel().getServer().getCommands().getDispatcher().execute(RepairAsks.command(action, w.at()).substring(1),
				player.createCommandSourceStack());
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new GameTestAssertException("the chat's [" + action + "] command didn't parse: " + e.getMessage());
		}
	}

	private static ChoiceMenu openPage(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		menu.press(HallPages.slot(RepairsPage.PAGE), player);
		helper.assertTrue(menu.icon(4).is(Items.ANVIL), "the Repairs page didn't open: " + menu.icon(4));
		return menu;
	}

	/** The page's row for the build at {@code at} (the rows' order is the page's business). */
	private static int rowOf(GameTestHelper helper, ServerPlayer player, BlockPos at) {
		List<RepairAsks.Entry> entries = RepairAsks.entries(helper.getLevel(), helper.absolutePos(HALL));
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).building().placement().origin().equals(at)) {
				return VillageHallScreen.FIRST_ROW + i;
			}
		}
		throw new GameTestAssertException("the build at " + at.toShortString() + " isn't on the Repairs page");
	}

	private static String lore(ItemStack stack) {
		StringBuilder out = new StringBuilder();
		for (Component line : stack.getOrDefault(DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines()) {
			out.append(line.getString()).append('\n');
		}
		return out.toString();
	}

	/**
	 * Two damaged walls, answered differently: yes to one from chat, not now to the other from the hall's page. The builder
	 * repairs only the one agreed to; the other keeps its hole, stays "left for now" and is never started, even once the
	 * builder is free again and looks round.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa_repair_two"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_repair_two")
	public void twoBuildsAnsweredDifferently(GameTestHelper helper) {
		Stage s = stage(helper, true, true);
		helper.runAfterDelay(2, () -> {
			look(helper, s.builder());
			helper.assertTrue(ask(helper, s.a()).state() == RepairAsks.State.ASKED && ask(helper, s.b()).state() == RepairAsks.State.ASKED,
				"both walls should be asked about: " + ask(helper, s.a()) + ", " + ask(helper, s.b()));
			helper.assertTrue(RepairAsks.waiting(helper.getLevel(), helper.absolutePos(HALL)) == 2, "waiting: " + RepairAsks.waiting(helper.getLevel(), helper.absolutePos(HALL)));
			ChoiceMenu menu = openPage(helper, s.owner());
			menu.press(rowOf(helper, s.owner(), s.b().at()), s.owner());
			menu.press(RepairsPage.NOT_NOW, s.owner());
			helper.assertTrue(ask(helper, s.b()).state() == RepairAsks.State.NOT_NOW, "not now on the page: " + ask(helper, s.b()));
			helper.assertTrue(click(helper, s.owner(), "yes", s.a()) == 1, "the chat's yes failed");
			BuildSite site = RepairAsks.repairOf(helper.getLevel(), s.a().placement());
			helper.assertTrue(site != null && s.builder().getUUID().equals(site.builder()), "yes on A didn't set the builder to it: " + site);
			helper.assertTrue(RepairAsks.repairOf(helper.getLevel(), s.b().placement()) == null, "B was started too");
		});
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, WALL_A.offset(1, 0, 0));
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "still repairing A");
			look(helper, s.builder()); // free again, they look round: B is still left
			helper.assertTrue(RepairAsks.repairOf(helper.getLevel(), s.b().placement()) == null, "B, left for now, was started once the builder was free");
			helper.assertBlockPresent(Blocks.AIR, WALL_B.offset(1, 0, 0));
			helper.assertTrue(ask(helper, s.b()).state() == RepairAsks.State.NOT_NOW, "B's answer changed: " + ask(helper, s.b()));
			helper.assertTrue(ask(helper, s.a()) == null, "A still listed once repaired: " + ask(helper, s.a()));
		});
	}

	/**
	 * Not now, then the player puts the block back by hand: the wall comes off the list. When a block goes missing again
	 * (as many as before) it is asked about afresh, not left under the old answer; the builder still starts nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_repair_byhand"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_repair_byhand")
	public void aHandRepairClearsTheAnswerAndNewDamageIsAskedAgain(GameTestHelper helper) {
		Stage s = stage(helper, true, false);
		helper.runAfterDelay(2, () -> {
			look(helper, s.builder());
			helper.assertTrue(click(helper, s.owner(), "notnow", s.a()) == 1 && ask(helper, s.a()).state() == RepairAsks.State.NOT_NOW, "not now: " + ask(helper, s.a()));
			helper.setBlock(WALL_A.offset(1, 0, 0), Blocks.OAK_PLANKS); // the player mends it
			look(helper, s.builder());
			helper.assertTrue(ask(helper, s.a()) == null, "mended by hand, still on the list: " + ask(helper, s.a()));
			helper.assertTrue(RepairAsks.entries(helper.getLevel(), helper.absolutePos(HALL)).isEmpty(), "the page still lists the mended wall");
			helper.setBlock(WALL_A.offset(2, 0, 0), Blocks.AIR); // a different plank goes: one missing, as before
			look(helper, s.builder());
			RepairAsks.Ask again = ask(helper, s.a());
			helper.assertTrue(again != null && again.state() == RepairAsks.State.ASKED && again.missing() == 1, "new damage wasn't asked about afresh: " + again);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "a repair started without an answer");
			helper.succeed();
		});
	}

	/**
	 * The hall's page used by a stranger: the wall shows, but their Yes is refused (the question still waits, no repair);
	 * a friend of the owner may answer, and their yes sets the owner's builder to it.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_repair_stranger"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_repair_stranger")
	public void aStrangersClickOnTheHallPageIsRefused(GameTestHelper helper) {
		Stage s = stage(helper, true, false);
		ServerLevel level = helper.getLevel();
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		ServerPlayer friend = helper.makeMockServerPlayerInLevel();
		Friends friends = Friends.get(level.getServer());
		friends.add(s.owner().getUUID(), friend.getUUID(), friend.getGameProfile().getName());
		Leftovers.after(helper, () -> {
			friends.remove(s.owner().getUUID(), friend.getUUID());
			level.getServer().getPlayerList().remove(stranger);
			level.getServer().getPlayerList().remove(friend);
		});
		helper.runAfterDelay(2, () -> {
			look(helper, s.builder());
			ChoiceMenu menu = openPage(helper, stranger);
			menu.press(VillageHallScreen.FIRST_ROW, stranger);
			menu.press(RepairsPage.YES, stranger);
			menu.press(RepairsPage.NOT_NOW, stranger);
			helper.assertTrue(ask(helper, s.a()).state() == RepairAsks.State.ASKED, "a stranger's click on the page counted: " + ask(helper, s.a()));
			helper.assertTrue(RepairAsks.repairOf(level, s.a().placement()) == null && !ModAttachments.BUILDER_JOB.has(s.builder()), "a stranger started the repair");
			helper.assertTrue(click(helper, stranger, "yes", s.a()) == 0 || ask(helper, s.a()).state() == RepairAsks.State.ASKED,
				"a stranger's chat yes counted: " + ask(helper, s.a()));
			helper.assertTrue(ask(helper, s.a()).state() == RepairAsks.State.ASKED, "a stranger's chat yes counted: " + ask(helper, s.a()));
			ChoiceMenu theirs = openPage(helper, friend);
			theirs.press(VillageHallScreen.FIRST_ROW, friend);
			theirs.press(RepairsPage.YES, friend);
			helper.assertTrue(ask(helper, s.a()).state() == RepairAsks.State.YES, "the owner's friend couldn't say yes: " + ask(helper, s.a()));
			BuildSite site = RepairAsks.repairOf(level, s.a().placement());
			helper.assertTrue(site != null && s.builder().getUUID().equals(site.builder()), "the friend's yes didn't set the builder to it: " + site);
			helper.succeed();
		});
	}

	/**
	 * The builder repairing an agreed wall dies: the repair's site goes, the yes stays, and another builder of the owner
	 * takes it up at their next look (no second question) and finishes it.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa_repair_died"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_repair_died")
	public void anAgreedRepairOutlivesItsBuilder(GameTestHelper helper) {
		Stage s = stage(helper, true, false);
		ServerLevel level = helper.getLevel();
		BlockPos bench2 = new BlockPos(2, 2, 10);
		Villager[] second = new Villager[1];
		helper.runAfterDelay(2, () -> {
			look(helper, s.builder());
			RepairAsks.yes(s.owner(), s.a().at());
			helper.assertTrue(RepairAsks.repairOf(level, s.a().placement()) != null, "yes didn't start the repair");
			s.builder().kill();
			helper.assertTrue(RepairAsks.repairOf(level, s.a().placement()) == null, "the dead builder's repair is still on");
			helper.assertTrue(ask(helper, s.a()) != null && ask(helper, s.a()).state() == RepairAsks.State.YES, "the yes was lost with the builder: " + ask(helper, s.a()));
			helper.setBlock(bench2, ModBlocks.BUILDERS_BENCH);
			second[0] = hire(helper, s.owner(), bench2, new BlockPos(3, 2, 10));
			look(helper, second[0]);
			BuildSite site = RepairAsks.repairOf(level, s.a().placement());
			helper.assertTrue(site != null && second[0].getUUID().equals(site.builder()), "the second builder didn't take up the agreed repair: " + site);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(second[0] != null, "no second builder yet");
			helper.assertBlockPresent(Blocks.OAK_PLANKS, WALL_A.offset(1, 0, 0));
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(second[0]), "still repairing");
			helper.assertTrue(ask(helper, s.a()) == null, "still listed once repaired");
		});
	}

	/**
	 * repairAsk turned off with a question waiting and another wall left for now: the old automatic repairs come back,
	 * both walls are repaired without an answer, and nothing is listed any more.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_repair_switch_off"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_repair_switch_off")
	public void switchedOffWithQuestionsWaitingRepairsByThemselves(GameTestHelper helper) {
		Stage s = stage(helper, true, true);
		helper.runAfterDelay(2, () -> {
			look(helper, s.builder());
			click(helper, s.owner(), "notnow", s.b());
			helper.assertTrue(ask(helper, s.a()).state() == RepairAsks.State.ASKED && ask(helper, s.b()).state() == RepairAsks.State.NOT_NOW, "the answers");
			Upkeep.ASK = false; // the owner turns Ask Before Repairs off
		});
		helper.onEachTick(() -> {
			if (helper.getTick() > 4) {
				look(helper, s.builder());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, WALL_A.offset(1, 0, 0));
			helper.assertBlockPresent(Blocks.OAK_PLANKS, WALL_B.offset(1, 0, 0));
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "still repairing");
			helper.assertTrue(ask(helper, s.a()) == null && ask(helper, s.b()) == null, "still listed with the switch off: " + ask(helper, s.a()) + ", " + ask(helper, s.b()));
		});
	}

	/**
	 * The switch turned on and off during an agreed repair: the repair goes on to the end either way, and once it is on
	 * again a new hole is asked about, not repaired.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa_repair_switch_mid"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_repair_switch_mid")
	public void theSwitchToggledMidRepair(GameTestHelper helper) {
		Stage s = stage(helper, true, false);
		boolean[] asked = {false};
		String[] debug = {""};
		helper.runAfterDelay(2, () -> {
			look(helper, s.builder());
			click(helper, s.owner(), "yes", s.a());
			helper.assertTrue(RepairAsks.repairOf(helper.getLevel(), s.a().placement()) != null, "yes didn't start the repair");
			Upkeep.ASK = false;
		});
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(RepairAsks.repairOf(helper.getLevel(), s.a().placement()) != null || helper.getBlockState(WALL_A.offset(1, 0, 0)).is(Blocks.OAK_PLANKS),
				"the agreed repair stopped when the switch went off");
		});
		helper.runAfterDelay(20, () -> Upkeep.ASK = true);
		helper.succeedWhen(() -> {
			if (!asked[0]) {
				helper.assertTrue(helper.getTick() > 20, "the switch isn't back on yet");
				helper.assertBlockPresent(Blocks.OAK_PLANKS, WALL_A.offset(1, 0, 0));
				helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "still repairing");
				asked[0] = true;
				helper.setBlock(WALL_A.offset(3, 0, 0), Blocks.AIR);
				BuildSite started = Upkeep.look(helper.getLevel(), s.builder());
				debug[0] = "look=" + started + " ask=" + Upkeep.ASK + " sites=" + BuildSiteManager.get(helper.getLevel()).all().size()
					+ " finished=" + BuildSiteManager.get(helper.getLevel()).finishedIn(helper.getLevel()).stream().anyMatch(f -> f.placement().equals(s.a().placement()))
					+ " tick=" + helper.getTick();
			}
			helper.assertTrue(RepairAsks.repairOf(helper.getLevel(), s.a().placement()) == null, "with the switch back on, new damage was repaired unasked");
			RepairAsks.Ask a = ask(helper, s.a());
			helper.assertTrue(a != null && a.state() == RepairAsks.State.ASKED && a.missing() == 1, "the new hole wasn't asked about: " + a + " " + debug[0]);
		});
	}

	/**
	 * Every new sentence with its real arguments: the question in chat (one and many), the answers, the put-aside line, the
	 * page's lines for an asked wall and a wall being repaired (the builder's name and a percentage), and the cellar's
	 * blueprint name, line-up line, advice and finished message. No placeholder or key is left in any of them.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_repair_words"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_repair_words")
	public void everySentenceRendersWithRealArguments(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		Stage s = stage(helper, true, false, seen);
		helper.runAfterDelay(2, () -> {
			Component cottage = Blueprints.displayName(StarterBlueprints.STARTER_COTTAGE.id());
			String name = cottage.getString();
			Component builder = s.builder().getDisplayName();
			List<String> lines = new ArrayList<>();
			String one = Words.counted("message.aliveworkplace.repair_ask", 1, builder, 1, cottage, 10, 64, -20).getString();
			String many = Words.counted("message.aliveworkplace.repair_ask", 3, builder, 3, cottage, 10, 64, -20).getString();
			helper.assertTrue(one.equals(builder.getString() + " found 1 block missing from the " + name + " at 10, 64, -20. Repair it?"), "one: " + one);
			helper.assertTrue(many.contains("found 3 blocks missing from the " + name + " at 10, 64, -20."), "many: " + many);
			lines.add(one);
			lines.add(many);
			for (String key : new String[] {"agreed", "left", "paused", "not_yours"}) {
				String line = Component.translatable("message.aliveworkplace.repair_answer." + key, cottage).getString();
				helper.assertTrue(line.contains(name), key + ": " + line);
				lines.add(line);
			}
			String aside = Component.translatable("message.aliveworkplace.repair_answer.put_aside", builder, cottage).getString();
			helper.assertTrue(aside.startsWith(builder.getString() + " puts the repair of the " + name), "put aside: " + aside);
			lines.add(aside);
			Component smithy = Blueprints.displayName(StarterBlueprints.SMITHY.id());
			lines.add(Component.translatable("item.aliveworkplace.blueprint.cellar", smithy).getString());
			lines.add(Component.translatable("message.aliveworkplace.blueprint.cellar", smithy).getString());
			lines.add(Component.translatable("message.aliveworkplace.cellar_dug", builder, smithy).getString());
			lines.add(new io.github.jcondedata.aliveworkplace.hall.VillageAdvice.Tip("cellar", Items.PAPER, smithy).title().getString());
			helper.assertTrue(lines.get(lines.size() - 4).equals("Dig the cellar: " + smithy.getString()), "the job's name: " + lines.get(lines.size() - 4));
			helper.assertTrue(lines.get(lines.size() - 1).equals("Dig the cellar under the " + smithy.getString()), "the advice: " + lines.get(lines.size() - 1));
			// the page, for real: an asked wall, then a wall being repaired
			look(helper, s.builder());
			// the question the owner got in chat, with its two buttons running the answer commands
			BlockPos at = s.a().at();
			String question = s.builder().getDisplayName().getString() + " found 1 block missing from the ";
			Component chat = seen.stream().filter(c -> c.getString().startsWith(question)).findFirst()
				.orElseThrow(() -> new GameTestAssertException("the owner wasn't asked in chat; got " + seen.stream().map(Component::getString).toList()));
			String text = chat.getString();
			helper.assertTrue(text.endsWith(" at " + at.getX() + ", " + at.getY() + ", " + at.getZ() + ". Repair it? [Yes] [Not now]"), "the question: " + text);
			List<String> commands = new ArrayList<>();
			chat.visit((style, part) -> {
				if (style.getClickEvent() != null) {
					commands.add(part + "=" + style.getClickEvent().getValue());
				}
				return Optional.<Object>empty();
			}, net.minecraft.network.chat.Style.EMPTY);
			helper.assertTrue(commands.contains("[Yes]=" + RepairAsks.command("yes", at)) && commands.contains("[Not now]=" + RepairAsks.command("notnow", at)),
				"the buttons: " + commands);
			lines.add(text);
			look(helper, s.builder()); // asked once: a second look doesn't ask again
			helper.assertTrue(seen.stream().filter(c -> c.getString().startsWith(question)).count() == 1, "asked twice in chat for the same hole");
			ChoiceMenu menu = openPage(helper, s.owner());
			String asked = lore(menu.icon(VillageHallScreen.FIRST_ROW));
			lines.add(asked);
			helper.assertTrue(asked.contains("1 block missing") && asked.contains("Waiting for your answer"), "asked wall: " + asked);
			RepairAsks.yes(s.owner(), s.a().at());
			menu = openPage(helper, s.owner());
			String going = lore(menu.icon(VillageHallScreen.FIRST_ROW));
			lines.add(going);
			helper.assertTrue(going.contains("Being repaired by " + builder.getString() + ": ") && going.contains("%"), "wall being repaired: " + going);
			for (String line : lines) {
				helper.assertFalse(line.contains("%s") || line.contains("%1$") || line.contains("aliveworkplace."), "a placeholder or key left in: " + line);
			}
			helper.succeed();
		});
	}

	// --- 36.7 ----------------------------------------------------------------------------------------------------------

	private static final int GROUND = 6;
	private static final BlockPos DIG_BENCH = new BlockPos(2, GROUND + 1, 2);
	private static final BlockPos DIG_CHEST = new BlockPos(2, GROUND + 1, 4);

	private static void ground(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				for (int y = 2; y <= GROUND; y++) {
					helper.setBlock(new BlockPos(x, y, z), y < 4 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK);
				}
			}
		}
	}

	private static BlockPos world(BlueprintData.Placement placement, BlockPos rel) {
		return placement.origin().offset(StructureTemplate.transform(rel, placement.mirror(), placement.rotation(), BlockPos.ZERO));
	}

	/** The Smithy placed as drawn (its cellar dug) at {@code originRel}, turned {@code rotation}, and recorded finished. */
	private static BlueprintData.Placement smithy(GameTestHelper helper, BlockPos originRel, Rotation rotation) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry entry = StarterBlueprints.SMITHY;
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(originRel), rotation, Mirror.NONE);
		BlockPos file = helper.absolutePos(originRel).below(entry.basement());
		level.getStructureManager().get(entry.id()).orElseThrow()
			.placeInWorld(level, file, file, new StructurePlaceSettings().setRotation(rotation), RandomSource.create(1L), 2);
		BuildSiteManager.get(level).recordFinished(entry.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return placement;
	}

	/** The world spots of the Smithy's basement that the drawing leaves open. */
	private static List<BlockPos> openSpots(ServerLevel level, BlueprintData.Placement placement) {
		List<BlockPos> out = new ArrayList<>();
		for (Blueprint.Entry e : BlueprintLibrary.get(level, StarterBlueprints.SMITHY.id()).orElseThrow().blocks()) {
			if (e.pos().getY() < 0 && e.state().isAir()) {
				out.add(world(placement, e.pos()));
			}
		}
		return out;
	}

	private static Villager builderAt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(DIG_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, GROUND + 1, 3));
		Builders.employ(level, villager, helper.absolutePos(DIG_BENCH));
		return villager;
	}

	private static boolean sellsDig(Villager villager, BlueprintData.Placement placement) {
		return villager.getOffers().stream().anyMatch(o -> o.getResult().has(DataComponents.CUSTOM_NAME) && BlueprintItem.data(o.getResult())
			.map(d -> d.structure().equals(StarterBlueprints.SMITHY.id()) && d.placement().equals(Optional.of(placement))).orElse(false));
	}

	/**
	 * The "more than half of its open spaces solid" boundary, from both sides, through the builder's real trade refresh
	 * (opening their trades): exactly half (or the most below half) solid is no offer and nothing to dig; one more spot
	 * solid and "Dig the cellar" is sold and listed. Grass-like and fluid fill doesn't count as solid.
	 */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qa_cellar_half"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qa_cellar_half")
	public void digTheCellarBoundaryIsMoreThanHalf(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		BlueprintData.Placement placement = smithy(helper, new BlockPos(9, GROUND + 1, 9), Rotation.NONE);
		List<BlockPos> open = openSpots(level, placement);
		helper.assertTrue(open.size() > 4, "the Smithy's basement has " + open.size() + " open spots");
		int half = open.size() / 2;
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		for (BlockPos p : open) {
			level.removeBlockEntity(p);
			level.setBlock(p, Blocks.WATER.defaultBlockState(), flags); // flooded: not solid
		}
		helper.assertFalse(Cellars.needsDigging(level, StarterBlueprints.SMITHY.id(), placement), "a flooded cellar counted as ground");
		for (BlockPos p : open) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
		}
		for (int i = 0; i < half; i++) {
			level.setBlock(open.get(i), Blocks.DIRT.defaultBlockState(), flags);
		}
		Villager builder = builderAt(helper);
		ModTrades.builderHomes(builder);
		helper.assertFalse(Cellars.needsDigging(level, StarterBlueprints.SMITHY.id(), placement), half + " of " + open.size() + " solid (not more than half) needs digging");
		helper.assertFalse(sellsDig(builder, placement), half + " of " + open.size() + " solid: \"Dig the cellar\" offered");
		helper.assertTrue(Cellars.toDig(level, placement.origin(), 64).isEmpty(), "listed to dig at " + half + " of " + open.size());
		level.setBlock(open.get(half), Blocks.DIRT.defaultBlockState(), flags);
		ModTrades.builderHomes(builder);
		helper.assertTrue(Cellars.needsDigging(level, StarterBlueprints.SMITHY.id(), placement), (half + 1) + " of " + open.size() + " solid doesn't need digging");
		helper.assertTrue(sellsDig(builder, placement), (half + 1) + " of " + open.size() + " solid: no \"Dig the cellar\" trade");
		helper.assertTrue(Cellars.toDig(level, placement.origin(), 64).size() == 1, "not listed to dig at " + (half + 1) + " of " + open.size());
		helper.succeed();
	}

	/**
	 * The Smithy's own blueprint clicked onto an old Smithy (its cellar ground) lines up over it exactly, turned as it
	 * stands, with the "dig its cellar" line; sneaking, it doesn't (the player wants a new one elsewhere). On a Smithy with
	 * its cellar it doesn't line up for a dig.
	 */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qa_cellar_click"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qa_cellar_click")
	public void theBlueprintClickedOnAnOldBuildLinesUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		BlockPos originRel = new BlockPos(20, GROUND + 1, 9);
		BlueprintData.Placement placement = smithy(helper, originRel, Rotation.CLOCKWISE_90);
		for (BlockPos p : openSpots(level, placement)) {
			level.removeBlockEntity(p);
			level.setBlock(p, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		helper.assertTrue(Cellars.needsDigging(level, StarterBlueprints.SMITHY.id(), placement), "the turned Smithy with its cellar filled doesn't need digging");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		// a wall block of the Smithy, above the ground
		Blueprint b = BlueprintLibrary.get(level, StarterBlueprints.SMITHY.id()).orElseThrow();
		BlockPos wall = b.blocks().stream().filter(e -> e.pos().getY() == 2 && !e.state().isAir()).map(e -> world(placement, e.pos())).findFirst().orElseThrow();
		ItemStack sneaked = useOn(helper, player, wall, true);
		helper.assertFalse(BlueprintItem.data(sneaked).flatMap(BlueprintData::placement).equals(Optional.of(placement)), "sneaking, it lined up over the old Smithy");
		ItemStack lined = useOn(helper, player, wall, false);
		helper.assertTrue(BlueprintItem.data(lined).flatMap(BlueprintData::placement).equals(Optional.of(placement)),
			"didn't line up over the old Smithy: " + BlueprintItem.data(lined).flatMap(BlueprintData::placement));
		helper.assertTrue(Cellars.isCellarDig(level, StarterBlueprints.SMITHY.id(), placement), "lined up, it isn't a cellar dig");
		// the cellar dug by hand: no dig any more, and the click no longer lines up for one
		for (BlockPos p : openSpots(level, placement)) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		helper.assertFalse(Cellars.isCellarDig(level, StarterBlueprints.SMITHY.id(), placement), "a dug cellar is still a dig");
		helper.succeed();
	}

	private static ItemStack useOn(GameTestHelper helper, ServerPlayer player, BlockPos wall, boolean sneak) {
		ItemStack blueprint = BlueprintItem.create(StarterBlueprints.SMITHY.id(), StarterBlueprints.SMITHY.size());
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, blueprint);
		player.setShiftKeyDown(sneak);
		blueprint.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
			new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(wall), net.minecraft.core.Direction.UP, wall, false)));
		player.setShiftKeyDown(false);
		return player.getMainHandItem();
	}

	/**
	 * A turned old Smithy's cellar dug with empty chests: the builder digs the cellar out but can't fit it out, waits for
	 * materials (not done, nothing above touched, nothing skipped); stocked, they finish it, the whole Smithy then stands
	 * as drawn, the build above untouched block for block, and the builder no longer sells the dig.
	 */
	//$ gametest_ticks_batch HUGE_AREA '20000' '"qa_cellar_short"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 20000, batch = "qa_cellar_short")
	public void aTurnedCellarDugWithMissingMaterials(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		BlockPos originRel = new BlockPos(20, GROUND + 1, 9);
		BlueprintData.Placement placement = smithy(helper, originRel, Rotation.CLOCKWISE_90);
		Blueprint whole = BlueprintLibrary.get(level, StarterBlueprints.SMITHY.id()).orElseThrow();
		Blueprint cellar = Cellars.cellarOnly(whole);
		Set<BlockPos> dug = new HashSet<>();
		cellar.blocks().forEach(e -> dug.add(world(placement, e.pos())));
		// as built before 26.15: the basement ground, the way down a block of floor
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		for (Blueprint.Entry e : cellar.blocks()) {
			BlockPos p = world(placement, e.pos());
			level.removeBlockEntity(p);
			level.setBlock(p, (e.pos().getY() < 0 ? (e.pos().getY() >= -2 ? Blocks.DIRT : Blocks.STONE) : Blocks.COBBLESTONE).defaultBlockState(), flags);
		}
		// what stands above, block for block (outside what the dig may work)
		Map<BlockPos, BlockState> above = new HashMap<>();
		for (Blueprint.Entry e : whole.blocks()) {
			BlockPos p = world(placement, e.pos());
			if (e.pos().getY() >= 0 && !dug.contains(p)) {
				above.put(p, level.getBlockState(p));
			}
		}
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Villager villager = builderAt(helper);
		helper.setBlock(DIG_CHEST, Blocks.CHEST);
		ModTrades.builderHomes(villager);
		helper.assertTrue(sellsDig(villager, placement), "the turned old Smithy isn't offered \"Dig the cellar\"");
		ItemStack job = villager.getOffers().stream().map(o -> o.getResult()).filter(r -> r.has(DataComponents.CUSTOM_NAME)
			&& BlueprintItem.data(r).flatMap(BlueprintData::placement).equals(Optional.of(placement))).findFirst().orElseThrow().copy();
		BlueprintData data = BlueprintItem.data(job).orElseThrow();
		BuildSite site = Builders.start(level, villager, null, data.structure(), data.placement().orElseThrow());
		helper.assertTrue(site.isCellarOnly(), "the job didn't start a cellar dig");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan");
		for (BuildPlan.Stage st : BuildPlan.Stage.values()) {
			for (BuildPlan.Step step : plan.steps(st)) {
				helper.assertTrue(dug.contains(step.pos()) || st == BuildPlan.Stage.CLEAR && step.pos().getY() < placement.origin().getY(),
					"the dig works " + st + " at " + step.pos().subtract(placement.origin()).toShortString() + ", outside the turned cellar");
			}
		}
		boolean[] stocked = {false};
		helper.onEachTick(() -> {
			if (!stocked[0] && site.status() == BuildSite.Status.WAITING_FOR_MATERIALS && site.stage() != BuildPlan.Stage.CLEAR) {
				stocked[0] = true;
				helper.assertTrue(BuildSiteManager.get(level).get(site.id()) != null, "a dig without materials was called done");
				List<String> moved = new ArrayList<>();
				above.forEach((p, s) -> {
					if (!level.getBlockState(p).equals(s)) {
						moved.add(p.subtract(placement.origin()).toShortString());
					}
				});
				helper.assertTrue(moved.isEmpty(), "waiting, the dig changed " + moved.size() + " block(s) above, e.g. " + moved.stream().limit(4).toList());
				Container chest = helper.getBlockEntity(DIG_CHEST);
				int slot = 0;
				for (Map.Entry<Item, Integer> m : BuildPlan.create(cellar, placement).materials().entrySet()) {
					for (int left = m.getValue(); left > 0 && slot < 27; left -= m.getKey().getDefaultMaxStackSize()) {
						chest.setItem(slot++, new ItemStack(m.getKey(), Math.min(left, m.getKey().getDefaultMaxStackSize())));
					}
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stocked[0], "the dig never waited for its materials");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still digging: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> wrong = BuildPlan.create(whole, placement).unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the Smithy wrong after the dig, e.g. "
				+ wrong.stream().limit(4).map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			List<String> moved = new ArrayList<>();
			above.forEach((p, s) -> {
				if (!level.getBlockState(p).equals(s)) {
					moved.add(p.subtract(placement.origin()).toShortString());
				}
			});
			helper.assertTrue(moved.isEmpty(), "the dig changed " + moved.size() + " block(s) above, e.g. " + moved);
			helper.assertTrue(site.skipped() == 0, site.skipped() + " step(s) skipped");
			helper.assertFalse(Cellars.needsDigging(level, StarterBlueprints.SMITHY.id(), placement), "dug, it still needs digging");
			helper.assertTrue(Cellars.offer(level, villager).isEmpty(), "dug, a new \"Dig the cellar\" was offered");
		});
	}
}
