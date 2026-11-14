package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.RequestErrands;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA (lane E, 2026-11-14, B1229) from the spec: a village always has a name a player can read; a blank saved or custom
 * name counts as missing and falls back to the made-up name. Whitespace-only and styled-but-empty names, saved on the
 * caravan list (a far hall) or set on a loaded hall by a Name Tag, never leave {@code /workplace quests} reading "()" or
 * a letter reading "in ."; the made-up name is the same before and after a save and load, and on the loaded and the
 * unloaded path; a real name (even one with spaces round it) still wins.
 */
public class QaFarVillageBlankName1114e2GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(10, 2, 10);

	/** The far-village line from {@code /workplace quests} names the made-up name and never reads "()" or "( )". */
	private static void lineNamesMadeUp(GameTestHelper helper, ServerPlayer me, List<Component> seen, BlockPos hall, String what) {
		Component line = QaFarVillageName1114eGameTests.questLine(helper, me, seen);
		String made = VillageHalls.madeUpName(hall).getString();
		String village = QaFarVillageName1114eGameTests.village(line).getString();
		helper.assertTrue(!made.isBlank() && village.equals(made), what + ": the line names '" + village + "', not the made-up " + made + ": " + line.getString());
		helper.assertTrue(line.getString().contains(" (" + made + "): ") && !line.getString().matches(".*\\(\\s*\\).*"), what + ": the line: " + line.getString());
	}

	/** Whitespace-only saved names ("   ", a tab, a newline and spaces) on the caravan list: the far village gets its made-up name, before and after a save and load. */
	//$ gametest_ticks_batch AREA '40' '"qa1114e2WhitespaceSavedName"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114e2WhitespaceSavedName")
	public void aWhitespaceSavedNameFallsBackToTheMadeUpName(GameTestHelper helper) {
		BlockPos spaces = QaFarVillageName1114eGameTests.far(helper, 2800);
		BlockPos tab = QaFarVillageName1114eGameTests.far(helper, 3200);
		BlockPos mixed = QaFarVillageName1114eGameTests.far(helper, 3600);
		QaFarVillageName1114eGameTests.setUp(helper, spaces);
		ServerLevel level = helper.getLevel();
		for (BlockPos h : List.of(tab, mixed)) {
			Leftovers.after(helper, () -> {
				Stories.Data.get(level).remove(h);
				Caravans.Data.get(level).remove(h);
			});
		}
		Caravans.Data.get(level).setWants(spaces, Component.literal("   "), List.of());
		Caravans.Data.get(level).setWants(tab, Component.literal("\t"), List.of());
		Caravans.Data.get(level).setWants(mixed, Component.literal(" \n \t "), List.of());
		List<String> before = new ArrayList<>();
		for (BlockPos h : List.of(spaces, tab, mixed)) {
			Component name = VillageHalls.nameIfLoaded(level, h);
			helper.assertTrue(name.equals(VillageHalls.madeUpName(h)), "a whitespace saved name isn't replaced by the made-up name: '" + name.getString() + "'");
			before.add(name.getString());
		}
		QaFarVillageName1114eGameTests.reload(helper, tag -> { });
		for (int i = 0; i < 3; i++) {
			BlockPos h = List.of(spaces, tab, mixed).get(i);
			helper.assertTrue(Caravans.Data.get(level).village(h) != null, "a village with a whitespace name was dropped by a save and load");
			String after = VillageHalls.nameIfLoaded(level, h).getString();
			helper.assertTrue(after.equals(before.get(i)), "the made-up name changed across a save and load: " + before.get(i) + " -> " + after);
		}
		List<Component> seen = new ArrayList<>();
		ServerPlayer me = QaFarVillageName1114eGameTests.player(helper, seen, "qa1114e2-spaces");
		QaFarVillageName1114eGameTests.track(helper, me, spaces);
		lineNamesMadeUp(helper, me, seen, spaces, "three spaces");
		helper.assertTrue(!QaFarVillageName1114eGameTests.loaded(level, spaces), "naming it loaded the far hall's chunk");
		helper.succeed();
	}

	/** A saved name that is empty text with styling (gold, bold; or an empty parent with a blank italic child): the made-up name. */
	//$ gametest_ticks_batch AREA '40' '"qa1114e2StyledBlankName"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114e2StyledBlankName")
	public void aStyledBlankSavedNameFallsBackToTheMadeUpName(GameTestHelper helper) {
		BlockPos gold = QaFarVillageName1114eGameTests.far(helper, 4000);
		BlockPos nested = QaFarVillageName1114eGameTests.far(helper, 4400);
		QaFarVillageName1114eGameTests.setUp(helper, gold);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			Stories.Data.get(level).remove(nested);
			Caravans.Data.get(level).remove(nested);
		});
		Caravans.Data.get(level).setWants(gold, Component.literal("").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), List.of());
		Caravans.Data.get(level).setWants(nested, Component.empty().append(Component.literal("  ").withStyle(ChatFormatting.ITALIC)), List.of());
		QaFarVillageName1114eGameTests.reload(helper, tag -> { });
		for (BlockPos h : List.of(gold, nested)) {
			helper.assertTrue(Caravans.Data.get(level).village(h) != null, "a village with a styled blank name was dropped by a save and load");
			Component name = VillageHalls.nameIfLoaded(level, h);
			helper.assertTrue(name.getString().equals(VillageHalls.madeUpName(h).getString()), "a styled blank saved name: '" + name.getString() + "'");
		}
		List<Component> seen = new ArrayList<>();
		ServerPlayer me = QaFarVillageName1114eGameTests.player(helper, seen, "qa1114e2-styled");
		QaFarVillageName1114eGameTests.track(helper, me, gold);
		lineNamesMadeUp(helper, me, seen, gold, "styled empty");
		helper.succeed();
	}

	/**
	 * The loaded path: a hall named with a Name Tag whose name is three spaces (a /give tag). The village is called by
	 * its made-up name in {@code /workplace quests}, its round saves that made-up name (not the blank one) on the caravan
	 * list, so the unloaded path later gives the same name, also after a save and load.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114e2BlankNameTag"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114e2BlankNameTag")
	public void aLoadedHallWithABlankNameTagKeepsItsMadeUpName(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			Stories.Data.get(level).remove(hall);
			Caravans.Data.get(level).remove(hall);
		});
		Component madeUp = VillageHalls.name(level, hall);
		helper.assertTrue(madeUp.equals(VillageHalls.madeUpName(hall)), "setup: a fresh hall isn't called by its made-up name: " + madeUp.getString());
		ServerPlayer namer = helper.makeMockServerPlayerInLevel();
		ItemStack tag = new ItemStack(Items.NAME_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal("   "));
		namer.setItemInHand(InteractionHand.MAIN_HAND, tag);
		helper.useBlock(HALL, namer);
		helper.assertTrue(VillageHalls.name(level, hall).equals(madeUp), "a hall named '   ' isn't called by its made-up name: '" + VillageHalls.name(level, hall).getString() + "'");
		helper.assertTrue(VillageHalls.nameIfLoaded(level, hall).equals(madeUp), "the loaded path: '" + VillageHalls.nameIfLoaded(level, hall).getString() + "'");
		Caravans.round(level, hall, VillageHalls.census(level, hall));
		Caravans.Village saved = Caravans.Data.get(level).village(hall);
		helper.assertTrue(saved != null, "the hall's round didn't put it on the caravan list");
		helper.assertTrue(saved.name().equals(madeUp), "the round saved '" + saved.name().getString() + "' for a blank-named hall, not its made-up name " + madeUp.getString());
		Caravans.Data read = QaFarVillageName1114eGameTests.reload(helper, t -> { });
		helper.assertTrue(read.village(hall) != null && read.village(hall).name().equals(madeUp),
			"the saved made-up name changed in a save and load: " + (read.village(hall) == null ? "gone" : read.village(hall).name().getString()));
		List<Component> seen = new ArrayList<>();
		ServerPlayer me = QaFarVillageName1114eGameTests.player(helper, seen, "qa1114e2-nametag");
		QaFarVillageName1114eGameTests.track(helper, me, hall);
		lineNamesMadeUp(helper, me, seen, hall, "a loaded hall named '   '");
		helper.succeed();
	}

	/**
	 * A real name still wins: a Name Tag "Testville" on a loaded hall (saved by its round), and far villages saved as
	 * "  Ashby  " (kept as is, spaces and all) and as an empty parent holding a gold "Ferncombe".
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114e2RealNameWins"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114e2RealNameWins")
	public void aRealNameStillWins(GameTestHelper helper) {
		BlockPos ashby = QaFarVillageName1114eGameTests.far(helper, 4800);
		BlockPos fern = QaFarVillageName1114eGameTests.far(helper, 5200);
		QaFarVillageName1114eGameTests.setUp(helper, ashby);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		for (BlockPos h : List.of(fern, hall)) {
			Leftovers.after(helper, () -> {
				Stories.Data.get(level).remove(h);
				Caravans.Data.get(level).remove(h);
			});
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer namer = helper.makeMockServerPlayerInLevel();
		ItemStack tag = new ItemStack(Items.NAME_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal("Testville"));
		namer.setItemInHand(InteractionHand.MAIN_HAND, tag);
		helper.useBlock(HALL, namer);
		helper.assertTrue(VillageHalls.name(level, hall).getString().equals("Testville"), "the Name Tag's name lost: " + VillageHalls.name(level, hall).getString());
		Caravans.round(level, hall, VillageHalls.census(level, hall));
		helper.assertTrue(Caravans.Data.get(level).village(hall).name().getString().equals("Testville"), "the round saved: " + Caravans.Data.get(level).village(hall).name().getString());
		Component spaced = Component.literal("  Ashby  ");
		Component styled = Component.empty().append(Component.literal("Ferncombe").withStyle(ChatFormatting.GOLD));
		Caravans.Data.get(level).setWants(ashby, spaced, List.of());
		Caravans.Data.get(level).setWants(fern, styled, List.of());
		QaFarVillageName1114eGameTests.reload(helper, t -> { });
		helper.assertTrue(VillageHalls.nameIfLoaded(level, ashby).equals(spaced), "'  Ashby  ' was replaced: '" + VillageHalls.nameIfLoaded(level, ashby).getString() + "'");
		helper.assertTrue(VillageHalls.nameIfLoaded(level, fern).getString().equals("Ferncombe"), "a styled child name was replaced: " + VillageHalls.nameIfLoaded(level, fern).getString());
		List<Component> seen = new ArrayList<>();
		ServerPlayer me = QaFarVillageName1114eGameTests.player(helper, seen, "qa1114e2-real");
		QaFarVillageName1114eGameTests.track(helper, me, fern);
		Component line = QaFarVillageName1114eGameTests.questLine(helper, me, seen);
		helper.assertTrue(line.getString().contains(" (Ferncombe): "), "the line for a real saved name: " + line.getString());
		helper.succeed();
	}

	/**
	 * The letter: a reply written again for a poster whose far village was saved with a blank name says "For Lia in
	 * &lt;made-up name&gt;", never "in " or "in .".
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1114e2BlankNameLetter"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114e2BlankNameLetter")
	public void aReplyForABlankNamedFarVillageNamesItsMadeUpName(GameTestHelper helper) {
		BlockPos hall = QaFarVillageName1114eGameTests.far(helper, 5600);
		QaFarVillageName1114eGameTests.setUp(helper, hall);
		ServerLevel level = helper.getLevel();
		Caravans.Data.get(level).setWants(hall, Component.literal(" \t "), List.of());
		QaFarVillageName1114eGameTests.reload(helper, t -> { });
		Villager hal = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Hal");
		ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 6));
		RequestErrands.Deliver deliver = new RequestErrands.Deliver(Optional.empty(), Optional.of(hal.getUUID()), "Hal", "Ashford", "brother");
		Quest quest = QaFarVillageName1114eGameTests.request(level, deliver);
		quest.villager = UUID.randomUUID();
		quest.helpers.put(me.getUUID(), 0);
		quest.progress[0] = 1; // the letter was delivered; the reply is on its way back
		Stories.post(level, hall, quest);
		helper.assertTrue(RequestErrands.use(me, level, InteractionHand.MAIN_HAND, hal), "the lost reply wasn't written again");
		ItemStack reply = RequestErrands.isCarried(me.getMainHandItem()) ? me.getMainHandItem()
			: me.getInventory().items.stream().filter(RequestErrands::isCarried).findFirst().orElse(ItemStack.EMPTY);
		ItemLore lore = reply.get(DataComponents.LORE);
		List<String> lines = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
		String made = VillageHalls.madeUpName(hall).getString();
		helper.assertTrue(lines.contains("For Lia in " + made), "the reply's lines (made-up name " + made + "): " + lines);
		helper.assertTrue(lines.stream().noneMatch(l -> l.matches(".*\\bin\\s*[.]?\\s*$")), "a reply line ends 'in ': " + lines);
		helper.assertTrue(!QaFarVillageName1114eGameTests.loaded(level, hall), "writing the reply loaded the far hall's chunk");
		me.discard();
		helper.succeed();
	}
}
