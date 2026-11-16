package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.move.MoveRules;
import io.github.jcondedata.aliveworkplace.move.Movers;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA, lane C 2026-11-16, ROADMAP 47.3 (moving a building in Copperline), from the spec: step 1's table says for each
 * building whether it "Can it move?" (Yes, Wait or No with why, Planned) and that word agrees with what its click does;
 * step 3 carries From, To and Takes and the checklist (spot, Mover, not already moving) the screen reads; a refused spot
 * keeps its red reason on the Move building button; and every sentence the new look shows has its words.
 */
public class QaMoveCopper1116cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos HALL = new BlockPos(15, 2, 11);
	/** The finished Test Hut: x 3-7, z 3-7 (5 by 5). */
	private static final BlockPos HUT_ORIGIN = new BlockPos(3, 2, 3);
	private static final int EAST = MovePage.CENTRE + 2;
	private static final int NORTH = MovePage.CENTRE - 2 * MovePage.COLUMNS;

	private record Village(ServerLevel level, BlockPos hall, BlockPos hut, ServerPlayer player) {
	}

	private static Village stage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Predicate<Villager> mover = Movers.isMover;
		boolean enabled = MoveRules.ENABLED;
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement hut = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT_ORIGIN), Rotation.NONE, Mirror.NONE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Movers.isMover = mover;
			MoveRules.ENABLED = enabled;
			sites.forgetFinished(hut);
			PendingMoves.get(level).remove(hut.origin());
			MovePage.stop(player);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(HUT_ORIGIN);
		level.getStructureManager().get(HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		sites.recordFinished(HUT, hut, UUID.randomUUID());
		return new Village(level, helper.absolutePos(HALL), origin, player);
	}

	/** The hall opened and its Move tab clicked: step 1's list. */
	private static ChoiceMenu list(GameTestHelper helper, Village v) {
		ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
		int tab = HallPages.slot(MovePage.PAGE);
		helper.assertTrue(tab >= 0, "the Move page has no tab");
		menu.press(tab, v.player());
		return menu;
	}

	private static CompoundTag row(ItemStack icon) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		return data == null || !data.contains(MovePage.ROW_MARK) ? null : data.copyTag().getCompound(MovePage.ROW_MARK);
	}

	private static String why(ServerLevel level, CompoundTag row) {
		if (!row.contains("why")) {
			return null;
		}
		Component c = Component.Serializer.fromJson(row.getString("why"), level.registryAccess());
		return c == null ? "(unreadable)" : c.getString();
	}

	private static List<Component> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
	}

	private static boolean hasKey(List<Component> lines, String key) {
		return lines.stream().anyMatch(l -> l.getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	private static String arg0(Component line) {
		if (line.getContents() instanceof TranslatableContents t && t.getArgs().length > 0) {
			Object a = t.getArgs()[0];
			return a instanceof Component c ? c.getString() : String.valueOf(a);
		}
		return null;
	}

	/**
	 * Step 1's "Can it move?" column through the hall's clicks, each state against its click: Yes (5 x 5, no reason) opens
	 * the picker; with Move buildings switched off, No with the switch's sentence and the click opens nothing; with
	 * builders on it, Wait with the builders' sentence and the click opens nothing; once a Mover's move is confirmed,
	 * Planned (and still Planned while builders work on it, as the spec's "Planned" row).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaC1116MoveRowStates"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaC1116MoveRowStates")
	public void stepOneSaysWhetherEachBuildingCanMoveAndTheClickAgrees(GameTestHelper helper) {
		Village v = stage(helper);
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		Villager mover = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 15));
		mover.setNoAi(true);
		Movers.isMover = villager -> villager.getUUID().equals(mover.getUUID());
		helper.runAfterDelay(2, () -> {
			// Yes
			ChoiceMenu menu = list(helper, v);
			CompoundTag yes = row(menu.icon(VillageHallScreen.FIRST_ROW));
			helper.assertTrue(yes != null, "the hut's row carries no Copperline columns: " + menu.icon(VillageHallScreen.FIRST_ROW));
			helper.assertTrue(yes.getInt("state") == MovePage.ROW_YES && !yes.contains("why"), "a movable hut: " + yes);
			helper.assertTrue(yes.getInt("w") == 5 && yes.getInt("d") == 5, "the hut's size: " + yes);
			menu.press(VillageHallScreen.FIRST_ROW, v.player());
			helper.assertTrue(MovePage.picking(v.player()).isPresent(), "a Yes row's click opened no picker");
			menu.press(MovePage.CANCEL, v.player());

			// No: switched off
			MoveRules.ENABLED = false;
			menu = list(helper, v);
			CompoundTag no = row(menu.icon(VillageHallScreen.FIRST_ROW));
			String off = Component.translatable("message.aliveworkplace.move.refused.off").getString();
			helper.assertTrue(no.getInt("state") == MovePage.ROW_NO && off.equals(why(v.level(), no)),
				"switched off, the row says " + no.getInt("state") + " / \"" + why(v.level(), no) + "\", not No / \"" + off + "\"");
			menu.press(VillageHallScreen.FIRST_ROW, v.player());
			helper.assertTrue(MovePage.picking(v.player()).isEmpty(), "a No row's click opened the picker");
			MoveRules.ENABLED = true;

			// Wait: builders on it
			UUID site = sites.create(UUID.randomUUID(), "Builder", HUT,
				new BlueprintData.Placement(v.level().dimension().location(), v.hut(), Rotation.NONE, Mirror.NONE)).id();
			try {
				menu = list(helper, v);
				CompoundTag wait = row(menu.icon(VillageHallScreen.FIRST_ROW));
				String building = Component.translatable("message.aliveworkplace.move.refused.building").getString();
				helper.assertTrue(wait.getInt("state") == MovePage.ROW_WAIT && building.equals(why(v.level(), wait)),
					"with builders on it the row says " + wait.getInt("state") + " / \"" + why(v.level(), wait) + "\"");
				menu.press(VillageHallScreen.FIRST_ROW, v.player());
				helper.assertTrue(MovePage.picking(v.player()).isEmpty(), "a Wait row's click opened the picker");
			} finally {
				sites.remove(site);
			}

			// Planned: confirmed with a Mover
			menu = list(helper, v);
			menu.press(VillageHallScreen.FIRST_ROW, v.player());
			menu.press(EAST, v.player());
			helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.PISTON), "Move building isn't ready with a Mover and a fitting spot");
			menu.press(MovePage.CONFIRM, v.player());
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.hut()).isPresent(), "no move saved");
			menu = list(helper, v);
			CompoundTag planned = row(menu.icon(VillageHallScreen.FIRST_ROW));
			helper.assertTrue(planned.getInt("state") == MovePage.ROW_PLANNED && !planned.contains("why"), "a planned move's row: " + planned);
			menu.press(VillageHallScreen.FIRST_ROW, v.player());
			helper.assertTrue(MovePage.picking(v.player()).isPresent(), "a Planned row's click didn't open the picker on its spot");
			helper.succeed();
		});
	}

	/**
	 * Step 3 as the screen reads it: the guide's lines give From (where it stands now, from the Hall), To (the new spot)
	 * and Takes (whole minutes, at least one, the same as the guide's own estimate); the checklist's three ticks follow
	 * the Move building button: a fitting spot and a Mover tick all three; without a Mover, "needs a Mover" is on it and it
	 * stays gray; a refused spot (outside the village) keeps the red reason on the button, the same words as the fit line,
	 * and the guide is back on step 2.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaC1116MoveStepThree"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaC1116MoveStepThree")
	public void stepThreeCarriesFromToTakesAndTheChecklist(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = list(helper, v);
			menu.press(VillageHallScreen.FIRST_ROW, v.player());
			menu.press(EAST, v.player());
			// no Mover yet
			List<Component> confirm = lore(menu.icon(MovePage.CONFIRM));
			helper.assertTrue(hasKey(confirm, "screen.aliveworkplace.move.needs_mover") && menu.icon(MovePage.CONFIRM).is(Items.GRAY_DYE),
				"no Mover, but the checklist's Mover line would tick: " + confirm.stream().map(Component::getString).toList());
			// a Mover hired
			Villager mover = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 15));
			mover.setNoAi(true);
			Movers.isMover = villager -> villager.getUUID().equals(mover.getUUID());
			menu.press(EAST, v.player()); // the same spot again: the picker redraws
			ItemStack guide = menu.icon(MovePage.GUIDE);
			List<Component> lines = lore(guide);
			helper.assertTrue(lines.size() > 3, "the guide has " + lines.size() + " lines, the screen needs 4 for From, To and Takes");
			helper.assertTrue(hasKey(List.of(lines.get(0)), "screen.aliveworkplace.move.now") && arg0(lines.get(0)) != null && !arg0(lines.get(0)).isBlank(),
				"From: " + lines.get(0).getString());
			helper.assertTrue(hasKey(List.of(lines.get(1)), "screen.aliveworkplace.move.spot") && arg0(lines.get(1)) != null
				&& !arg0(lines.get(1)).equals(arg0(lines.get(0))), "To (" + lines.get(1).getString() + ") vs From (" + lines.get(0).getString() + ")");
			String takes = arg0(lines.get(3));
			helper.assertTrue(takes != null && takes.matches("\\d+") && Integer.parseInt(takes) >= 1, "Takes: '" + takes + "' of " + lines.get(3).getString());
			helper.assertTrue(lines.get(3).getContents() instanceof TranslatableContents t && t.getKey().startsWith("screen.aliveworkplace.move.time"),
				"line 4 isn't the time: " + lines.get(3).getString());
			String rendered = Component.translatable("screen.aliveworkplace.move.takes_value", takes).getString();
			helper.assertTrue(rendered.equals("About " + takes + " min of a Mover's work"), "Takes reads: " + rendered);
			confirm = lore(menu.icon(MovePage.CONFIRM));
			helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.PISTON) && !hasKey(confirm, "screen.aliveworkplace.move.needs_mover")
				&& !hasKey(confirm, "message.aliveworkplace.move.under_way") && !hasKey(confirm, "message.aliveworkplace.move.putting_back"),
				"with a Mover and a fitting spot a checklist line is red: " + confirm.stream().map(Component::getString).toList());
			// a refused spot: step 2 again, the reason in red on the button, the same words as the fit line
			menu.press(NORTH, v.player());
			String verdict = menu.icon(MovePage.VERDICT).getHoverName().getString();
			String outside = Component.translatable("screen.aliveworkplace.move.fit.outside").getString();
			helper.assertTrue(verdict.equals(outside), "the fit line: " + verdict);
			confirm = lore(menu.icon(MovePage.CONFIRM));
			helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.GRAY_DYE) && confirm.stream().anyMatch(l -> l.getString().equals(outside)),
				"a refused spot's Move building: " + confirm.stream().map(Component::getString).toList());
			CustomData step = menu.icon(MovePage.GUIDE).get(DataComponents.CUSTOM_DATA);
			helper.assertTrue(step != null && step.copyTag().getInt(MovePage.STEP_MARK) == 2, "a refused spot isn't step 2: " + step);
			menu.press(MovePage.CONFIRM, v.player());
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.hut()).isEmpty(), "a refused spot was confirmed");
			helper.succeed();
		});
	}

	/** Every sentence the Copperline Move page shows has its words, in American English, with the right number of blanks. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyCopperlineMoveSentenceHasItsWords(GameTestHelper helper) {
		Language en = Language.getInstance();
		String[][] keys = {
			{"steps", "0"}, {"step.pick", "0"}, {"step.place", "0"}, {"step.confirm", "0"}, {"your_move", "-1"},
			{"col.building", "0"}, {"col.can", "0"}, {"state.yes", "0"}, {"state.wait", "0"}, {"state.planned", "0"}, {"state.no", "0"},
			{"row.size", "2"}, {"what", "0"}, {"what.none", "0"}, {"what.list", "1"}, {"why_heading", "-1"}, {"next.place", "0"},
			{"next.confirm", "0"}, {"fits_here", "0"}, {"refused_here", "0"}, {"from", "0"}, {"to", "0"}, {"takes", "0"},
			{"takes_value", "1"}, {"faces_label", "0"}, {"check.spot", "0"}, {"check.spot_no", "0"}, {"check.mover", "0"},
			{"check.mover_no", "0"}, {"check.idle", "0"}, {"check.idle_no", "0"}, {"whisper.list", "0"}, {"whisper.row", "1"},
			{"whisper.locked", "2"}, {"whisper.place", "0"}, {"whisper.confirm", "0"}, {"do", "0"}, {"back_short", "0"},
			{"question", "-1"}, {"plan", "0"}, {"cancel", "0"}, {"rotate", "0"}, {"call_off", "0"}};
		StringBuilder problems = new StringBuilder();
		for (String[] k : keys) {
			String key = "screen.aliveworkplace.move." + k[0];
			if (!en.has(key)) {
				problems.append("no words for ").append(key).append("; ");
				continue;
			}
			String text = en.getOrDefault(key);
			int blanks = text.split("%s", -1).length - 1 + text.split("%\\d\\$s", -1).length - 1;
			int want = Integer.parseInt(k[1]);
			if (want >= 0 && blanks != want) {
				problems.append(key).append(" has ").append(blanks).append(" blanks, the screen fills ").append(want).append("; ");
			}
			for (String british : List.of("colour", "centre", "metre", "travelled", "cancelled", "defence", "armour", "grey")) {
				if (text.toLowerCase().contains(british)) {
					problems.append(key).append(" says \"").append(british).append("\"; ");
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.toString());
		helper.succeed();
	}
}
