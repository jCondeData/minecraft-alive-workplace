package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
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
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.threat.ThreatDamage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The Builder asks before repairing a house (ROADMAP 36.15): a damaged build is asked about and listed on the Village
 * Hall's Repairs page and left alone until its owner says yes; yes repairs it, not now leaves it (asked again once more
 * goes missing), a repair going on stops or pauses from the page and the builder goes straight to their next job, a
 * blueprint handed over mid-repair puts the repair aside, {@code repairAsk} off repairs as before, what a threat took is
 * never asked about (32.16a), and the answers survive a save and reload.
 */
public class RepairAskGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	/** The finished wall: four planks in a row, the second knocked out. */
	private static final BlockPos WALL = new BlockPos(6, 2, 6);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);

	private record Stage(ServerPlayer owner, Villager builder, BlueprintData.Placement placement, BlockPos at, Container chest) {
	}

	/** The hall, a builder hired by {@code owner} at a bench with planks beside it, and the owner's finished wall with a hole. */
	private static Stage stage(GameTestHelper helper, boolean ask) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		VillageHalls.RADIUS = 16;
		Upkeep.ENABLED = true; // builderRepairs on, as in a player's game
		Upkeep.ASK = ask;
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000); // working hours: a builder repairs only on shift, and the full suite may leave it night
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Upkeep.ENABLED = enabled;
			Upkeep.ASK = asks;
			level.getServer().getPlayerList().remove(owner);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (int x = 0; x < 4; x++) {
			helper.setBlock(WALL.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation wall = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "repair_ask_" + Long.toHexString(level.getGameTime())
			+ "_" + helper.absolutePos(WALL).getX());
		level.getStructureManager().getOrCreate(wall).fillFromWorld(level, helper.absolutePos(WALL), new Vec3i(4, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(WALL.offset(1, 0, 0), Blocks.AIR); // knocked out
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(WALL), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(wall, placement, owner.getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Stage(owner, builder, placement, helper.absolutePos(WALL), chest);
	}

	/** The builder looks over their buildings now (instead of at their next round). */
	private static void look(GameTestHelper helper, Stage s) {
		if (!ModAttachments.BUILDER_JOB.has(s.builder())) {
			Upkeep.look(helper.getLevel(), s.builder());
		}
	}

	private static RepairAsks.Ask ask(GameTestHelper helper, Stage s) {
		return BuildSiteManager.get(helper.getLevel()).repairAsk(s.placement());
	}

	/** The hall's screen on the Repairs page, through its tab. */
	private static ChoiceMenu openPage(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		int tab = HallPages.slot(RepairsPage.PAGE);
		helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW, "the Repairs page has no tab: " + tab);
		helper.assertTrue(menu.icon(tab).is(Items.ANVIL), "the tab: " + menu.icon(tab));
		menu.press(tab, player);
		helper.assertTrue(menu.icon(4).is(Items.ANVIL), "the page's header: " + menu.icon(4));
		return menu;
	}

	private static String lore(ItemStack stack) {
		StringBuilder out = new StringBuilder();
		for (net.minecraft.network.chat.Component line : stack.getOrDefault(net.minecraft.core.component.DataComponents.LORE,
			net.minecraft.world.item.component.ItemLore.EMPTY).lines()) {
			out.append(line.getString()).append('\n');
		}
		return out.toString();
	}

	/**
	 * The ask: the builder finds the hole, starts nothing, and the wall goes on the list with the question; the hall's
	 * Repairs page shows it first with what's missing, and its view has Yes and Not now. The hole stays a while later.
	 */
	//$ gametest_ticks_batch AREA '400' '"repair_ask"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "repair_ask")
	public void theBuilderAsksBeforeRepairing(GameTestHelper helper) {
		Stage s = stage(helper, true);
		helper.runAfterDelay(2, () -> {
			look(helper, s);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "a repair started before anyone said yes");
			RepairAsks.Ask ask = ask(helper, s);
			helper.assertTrue(ask != null && ask.state() == RepairAsks.State.ASKED && ask.missing() == 1, "the wall wasn't asked about: " + ask);
			ChoiceMenu menu = openPage(helper, s.owner());
			ItemStack first = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(first.is(Items.CRACKED_STONE_BRICKS), "the asked wall isn't first on the page: " + first);
			String lore = lore(first);
			helper.assertTrue(lore.contains("1 block missing") && lore.contains("Waiting for your answer"), "the wall's lines: " + lore);
			menu.press(VillageHallScreen.FIRST_ROW, s.owner());
			helper.assertTrue(menu.icon(RepairsPage.YES).is(Items.LIME_DYE) && menu.icon(RepairsPage.NOT_NOW).is(Items.RED_DYE),
				"the wall's view has no Yes and Not now: " + menu.icon(RepairsPage.YES) + ", " + menu.icon(RepairsPage.NOT_NOW));
			look(helper, s); // looking again doesn't start it either
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "a second look started the repair");
		});
		helper.runAtTickTime(300, () -> {
			helper.assertBlockPresent(Blocks.AIR, WALL.offset(1, 0, 0));
			helper.assertTrue(s.chest().countItem(Items.OAK_PLANKS) == 8, "planks taken: " + s.chest().countItem(Items.OAK_PLANKS));
			helper.succeed();
		});
	}

	/** Yes, on the hall's page: the builder sets about it straight away, puts the plank back, and the wall comes off the list. */
	//$ gametest_ticks_batch AREA '1600' '"repair_ask_yes"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "repair_ask_yes")
	public void yesOnTheHallPageRepairsIt(GameTestHelper helper) {
		Stage s = stage(helper, true);
		helper.runAfterDelay(2, () -> {
			look(helper, s);
			ChoiceMenu menu = openPage(helper, s.owner());
			menu.press(VillageHallScreen.FIRST_ROW, s.owner());
			menu.press(RepairsPage.YES, s.owner());
			BuildSite site = RepairAsks.repairOf(helper.getLevel(), s.placement());
			helper.assertTrue(site != null && s.builder().getUUID().equals(site.builder()), "yes didn't set the builder to it: " + site);
			helper.assertTrue(ask(helper, s).state() == RepairAsks.State.YES, "the answer: " + ask(helper, s));
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.SCAFFOLDING), "the page doesn't show the repair going on: "
				+ menu.icon(VillageHallScreen.FIRST_ROW));
		});
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, WALL.offset(1, 0, 0));
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "still repairing");
			helper.assertTrue(ask(helper, s) == null, "the wall is still on the list once repaired: " + ask(helper, s));
			helper.assertTrue(s.chest().countItem(ModItems.BLUEPRINT) == 0, "a blueprint was handed out");
		});
	}

	/**
	 * Not now, from the chat's button (the command it runs): the wall is left as it is, looks don't start it, and once
	 * another block goes missing it's asked about again; a stranger's answer is refused.
	 */
	//$ gametest_ticks_batch AREA '400' '"repair_ask_not_now"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "repair_ask_not_now")
	public void notNowLeavesItUntilMoreGoesMissing(GameTestHelper helper) {
		Stage s = stage(helper, true);
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(stranger));
		helper.runAfterDelay(2, () -> {
			look(helper, s);
			helper.assertTrue(RepairAsks.yes(stranger, s.at()) == RepairAsks.Outcome.NOT_YOURS, "a stranger said yes to the owner's wall");
			helper.assertTrue(ask(helper, s).state() == RepairAsks.State.ASKED, "the stranger's answer counted: " + ask(helper, s));
			int ran;
			try {
				ran = helper.getLevel().getServer().getCommands().getDispatcher().execute(RepairAsks.command("notnow", s.at()).substring(1),
					s.owner().createCommandSourceStack());
			} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
				throw new net.minecraft.gametest.framework.GameTestAssertException("the chat's [Not now] command didn't parse: " + e.getMessage());
			}
			helper.assertTrue(ran == 1 && ask(helper, s).state() == RepairAsks.State.NOT_NOW, "not now: " + ask(helper, s));
		});
		helper.runAfterDelay(4, () -> {
			look(helper, s);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "a repair started after not now");
			helper.assertTrue(ask(helper, s).state() == RepairAsks.State.NOT_NOW, "asked again with nothing more missing: " + ask(helper, s));
			helper.setBlock(WALL.offset(3, 0, 0), Blocks.AIR); // another plank goes
			look(helper, s);
			RepairAsks.Ask again = ask(helper, s);
			helper.assertTrue(again.state() == RepairAsks.State.ASKED && again.missing() == 2, "not asked again with more missing: " + again);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "a repair started without an answer");
			helper.succeed();
		});
	}

	/**
	 * A repair going on is stopped from the hall's page: the builder ends it there and then and goes straight to the next
	 * job in their queue; the wall is left for now. Pause works the same and the wall waits, paused, till Carry on.
	 */
	//$ gametest_ticks_batch AREA '400' '"repair_ask_stop"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "repair_ask_stop")
	public void aRepairGoingOnStopsAndPausesFromTheHall(GameTestHelper helper) {
		Stage s = stage(helper, true);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			look(helper, s);
			RepairAsks.yes(s.owner(), s.at());
			BuildSite repair = RepairAsks.repairOf(level, s.placement());
			helper.assertTrue(repair != null, "yes didn't start the repair");
			BlueprintData.Placement next = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 12)), Rotation.NONE, Mirror.NONE);
			BuildSite queued = Builders.enqueue(level, s.builder(), s.owner(), BuildSiteManager.get(level).finishedIn(level).stream()
				.filter(f -> f.placement().equals(s.placement())).findFirst().orElseThrow().structure(), next);
			ChoiceMenu menu = openPage(helper, s.owner());
			menu.press(VillageHallScreen.FIRST_ROW, s.owner());
			helper.assertTrue(menu.icon(RepairsPage.PAUSE).is(Items.CLOCK) && menu.icon(RepairsPage.STOP).is(Items.BARRIER),
				"no Pause and Stop while it's repaired: " + menu.icon(RepairsPage.PAUSE) + ", " + menu.icon(RepairsPage.STOP));
			menu.press(RepairsPage.STOP, s.owner());
			helper.assertTrue(BuildSiteManager.get(level).get(repair.id()) == null, "the repair is still on");
			helper.assertTrue(ask(helper, s).state() == RepairAsks.State.NOT_NOW, "stopped, the wall isn't left for now: " + ask(helper, s));
			BuildSite now = Builders.activeSite(level, s.builder());
			helper.assertTrue(now != null && now.id().equals(queued.id()), "the builder didn't go straight to their next job: " + now);
			helper.assertTrue(level.getServer().getPlayerList().getPlayer(s.owner().getUUID()).getInventory().countItem(ModItems.BLUEPRINT) == 0,
				"stopping a repair handed out a blueprint");
			Builders.cancel(level, now); // (the next job, done with)
			// Pause: yes again, then Pause on the page
			RepairAsks.yes(s.owner(), s.at());
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) != null, "yes again didn't start it again");
			menu = openPage(helper, s.owner());
			menu.press(VillageHallScreen.FIRST_ROW, s.owner());
			menu.press(RepairsPage.PAUSE, s.owner());
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) == null && !ModAttachments.BUILDER_JOB.has(s.builder()), "pause didn't free the builder");
			helper.assertTrue(ask(helper, s).state() == RepairAsks.State.PAUSED, "paused: " + ask(helper, s));
			look(helper, s);
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) == null, "a paused repair started again by itself");
			menu = openPage(helper, s.owner());
			menu.press(VillageHallScreen.FIRST_ROW, s.owner());
			helper.assertTrue(menu.icon(RepairsPage.YES).getHoverName().getString().equals("Carry on"), "no Carry on: " + menu.icon(RepairsPage.YES));
			menu.press(RepairsPage.YES, s.owner());
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) != null, "carry on didn't start it again");
			helper.succeed();
		});
	}

	/**
	 * Moving the builder to another job: a blueprint handed to them mid-repair starts at once (not queued behind the
	 * repair); the repair is put aside, still agreed, and taken up again when they're free.
	 */
	//$ gametest_ticks_batch AREA '400' '"repair_ask_moved"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "repair_ask_moved")
	public void aNewJobPutsTheRepairAside(GameTestHelper helper) {
		Stage s = stage(helper, true);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			look(helper, s);
			RepairAsks.yes(s.owner(), s.at());
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) != null, "yes didn't start the repair");
			ResourceLocation structure = BuildSiteManager.get(level).finishedIn(level).stream().filter(f -> f.placement().equals(s.placement()))
				.findFirst().orElseThrow().structure();
			ItemStack blueprint = BlueprintItem.create(structure, new Vec3i(4, 1, 1));
			blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(
				new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 12)), Rotation.NONE, Mirror.NONE))));
			Builders.assign(s.owner(), s.builder(), blueprint);
			BuildSite now = Builders.activeSite(level, s.builder());
			helper.assertTrue(now != null && !now.isRepair() && !now.isQueued(), "the new job didn't start at once: " + now);
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) == null, "the repair wasn't put aside");
			helper.assertTrue(ask(helper, s).state() == RepairAsks.State.YES, "the repair isn't still agreed: " + ask(helper, s));
			Builders.cancel(level, now); // the new job over: free again
			look(helper, s);
			helper.assertTrue(RepairAsks.repairOf(level, s.placement()) != null, "the agreed repair wasn't taken up again");
			helper.succeed();
		});
	}

	/** {@code repairAsk} off: the builder repairs by themselves as before, with no question. */
	//$ gametest_ticks_batch AREA '1600' '"repair_ask_off"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "repair_ask_off")
	public void withTheSwitchOffTheyRepairByThemselves(GameTestHelper helper) {
		Stage s = stage(helper, false);
		helper.runAfterDelay(2, () -> {
			look(helper, s);
			helper.assertTrue(RepairAsks.repairOf(helper.getLevel(), s.placement()) != null || helper.getBlockState(WALL.offset(1, 0, 0)).is(Blocks.OAK_PLANKS),
				"no repair with the switch off");
			helper.assertTrue(ask(helper, s) == null, "asked with the switch off: " + ask(helper, s));
		});
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, WALL.offset(1, 0, 0));
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "still repairing");
		});
	}

	/** What a threat took (32.16a) is never asked about: the repair starts by itself with the switch on. */
	//$ gametest_ticks_batch AREA '400' '"repair_ask_threat"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "repair_ask_threat")
	public void whatAThreatTookIsRepairedWithoutAsking(GameTestHelper helper) {
		Stage s = stage(helper, true);
		boolean threats = Upkeep.THREATS;
		Upkeep.THREATS = true;
		Leftovers.after(helper, () -> Upkeep.THREATS = threats);
		helper.runAfterDelay(2, () -> {
			ThreatDamage.took(helper.getLevel(), helper.absolutePos(WALL.offset(1, 0, 0))); // a raider broke it
			look(helper, s);
			helper.assertTrue(RepairAsks.repairOf(helper.getLevel(), s.placement()) != null, "a threat's damage waited for an answer");
			helper.assertTrue(ask(helper, s) == null, "a threat's damage was asked about: " + ask(helper, s));
			helper.succeed();
		});
	}

	/** The answers are saved with the finished building, an older save has none, and a building taken down forgets its. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void answersSurviveASaveAndReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "starter_cottage");
		BuildSiteManager manager = new BuildSiteManager();
		UUID owner = UUID.randomUUID();
		BlueprintData.Placement[] spots = new BlueprintData.Placement[5];
		for (int i = 0; i < spots.length; i++) {
			spots[i] = new BlueprintData.Placement(level.dimension().location(), new BlockPos(100 + 20 * i, 70, 100), Rotation.NONE, Mirror.NONE);
			manager.recordFinished(id, spots[i], owner);
		}
		manager.setRepairAsk(spots[0], new RepairAsks.Ask(RepairAsks.State.ASKED, 3));
		manager.setRepairAsk(spots[1], new RepairAsks.Ask(RepairAsks.State.YES, 5));
		manager.setRepairAsk(spots[2], new RepairAsks.Ask(RepairAsks.State.NOT_NOW, 2));
		manager.setRepairAsk(spots[3], new RepairAsks.Ask(RepairAsks.State.PAUSED, 7));
		CompoundTag tag = manager.save(new CompoundTag(), level.registryAccess());
		BuildSiteManager loaded = BuildSiteManager.load(tag, level.registryAccess());
		helper.assertTrue(new RepairAsks.Ask(RepairAsks.State.ASKED, 3).equals(loaded.repairAsk(spots[0])), "asked: " + loaded.repairAsk(spots[0]));
		helper.assertTrue(new RepairAsks.Ask(RepairAsks.State.YES, 5).equals(loaded.repairAsk(spots[1])), "yes: " + loaded.repairAsk(spots[1]));
		helper.assertTrue(new RepairAsks.Ask(RepairAsks.State.NOT_NOW, 2).equals(loaded.repairAsk(spots[2])), "not now: " + loaded.repairAsk(spots[2]));
		helper.assertTrue(new RepairAsks.Ask(RepairAsks.State.PAUSED, 7).equals(loaded.repairAsk(spots[3])), "paused: " + loaded.repairAsk(spots[3]));
		helper.assertTrue(loaded.repairAsk(spots[4]) == null, "a building nobody was asked about has an answer: " + loaded.repairAsk(spots[4]));
		// an older save: the finished buildings without the new fields
		CompoundTag old = tag.copy();
		net.minecraft.nbt.ListTag done = old.getList("finished", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int i = 0; i < done.size(); i++) {
			done.getCompound(i).remove("repair_ask");
			done.getCompound(i).remove("repair_missing");
		}
		BuildSiteManager older = BuildSiteManager.load(old, level.registryAccess());
		helper.assertTrue(older.finishedIn(level).size() == 5 && older.repairAsk(spots[0]) == null, "an older save didn't load as never asked");
		loaded.forgetFinished(spots[0]);
		loaded.recordFinished(id, spots[1], owner); // built again on its spot (an upgrade)
		helper.assertTrue(loaded.repairAsk(spots[0]) == null && loaded.repairAsk(spots[1]) == null, "a building taken down or rebuilt kept its answer");
		helper.succeed();
	}

	/** Every new sentence a player reads: the question with its buttons, the answers, the page and the setting. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void repairAskWordsAreWritten(GameTestHelper helper) {
		Language lang = Language.getInstance();
		String one = lang.getOrDefault("message.aliveworkplace.repair_ask.one");
		String many = lang.getOrDefault("message.aliveworkplace.repair_ask");
		helper.assertTrue(one.contains("%s block missing") && one.contains("Repair it?") && one.split("%s", -1).length == 7, "the question for one block: " + one);
		helper.assertTrue(many.contains("%s blocks missing") && many.contains("Repair it?") && many.split("%s", -1).length == 7, "the question: " + many);
		helper.assertTrue(lang.getOrDefault("message.aliveworkplace.repair_ask.yes").equals("[Yes]")
			&& lang.getOrDefault("message.aliveworkplace.repair_ask.notnow").equals("[Not now]"), "the buttons");
		for (String key : new String[] {"agreed", "left", "paused", "not_yours"}) {
			String line = lang.getOrDefault("message.aliveworkplace.repair_answer." + key);
			helper.assertTrue(line.split("%s", -1).length == 2, key + ": " + line);
		}
		helper.assertTrue(lang.getOrDefault("screen.aliveworkplace.repairs").equals("Repairs"), "the tab");
		helper.assertTrue(lang.getOrDefault("screen.aliveworkplace.repairs.state.asked").equals("Waiting for your answer"), "the asked line");
		helper.assertTrue(lang.has("aliveworkplace.config.repairAsk") && lang.getOrDefault("aliveworkplace.config.repairAsk.tooltip").contains("Off:")
			&& lang.getOrDefault("aliveworkplace.config.repairAsk.tooltip").contains("raiders"), "the setting's words");
		helper.assertTrue(new io.github.jcondedata.aliveworkplace.WorkplaceConfig().repairAsk, "the setting should default to asking");
		helper.succeed();
	}
}
