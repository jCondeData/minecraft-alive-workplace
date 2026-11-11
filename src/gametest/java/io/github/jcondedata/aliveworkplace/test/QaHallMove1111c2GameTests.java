package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.Movers;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane C, 2026-11-11, second pass): the Village Hall's Move page after B1163 (45.2a), from the owner's words ("a
 * screen a first-time player understands: which building is being moved, where it is now and where it would go, what the
 * Mover will do and how long, and a clear Confirm/Cancel") and the wiki ("lists the village's finished buildings ... and,
 * once planned, where it's going"; "Call off the planned move once one is planned"). QaMovePage1111cGameTests,
 * MovePageGuideGameTests and QaMoveSpotSaves1111eGameTests cover the picker's refusals, rotation, Cancel and one planned
 * move; these walk what they leave out: a village with no buildings, a list longer than the page ("And N more", both
 * sides of the boundary), the tab counting two moves and back down to one after a Call off (with its own sentence on the
 * action bar), a quarter-turned move through a save and reload reopened turned, and Confirm's own sentence.
 */
public class QaHallMove1111c2GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos HALL = new BlockPos(15, 2, 11);
	/** Hut A: x 3-7, y 2-5, z 3-7. */
	private static final BlockPos A_ORIGIN = new BlockPos(3, 2, 3);
	/** Hut B: x 3-7, y 2-5, z 12-16. */
	private static final BlockPos B_ORIGIN = new BlockPos(3, 2, 12);
	/** A's new spot: its middle 8 east of A's, x 11-15, z 3-7. */
	private static final BlockPos A_TO = new BlockPos(13, 2, 5);
	/** B's new spot: x 11-15, z 15-19, clear of the Hall and of A_TO. */
	private static final BlockPos B_TO = new BlockPos(13, 2, 17);

	record Seen(Component text, boolean overlay) {
	}

	record Village(ServerLevel level, BlockPos hall, BlockPos a, BlockPos b, ServerPlayer player, List<Seen> seen) {
	}

	/** The hall, huts A and B (when {@code huts}), a hired Mover (when {@code mover}) and a player whose messages are kept. */
	static Village stage(GameTestHelper helper, int radius, boolean huts, boolean mover) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = radius;
		Predicate<Villager> isMover = Movers.isMover;
		Movers.isMover = villager -> false;
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement a = placement(helper, A_ORIGIN);
		BlueprintData.Placement b = placement(helper, B_ORIGIN);
		List<Seen> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen);
		Leftovers.after(helper, () -> {
			for (BlockPos from : List.of(a.origin(), b.origin())) {
				MoveSites.get(level).cancel(level, from);
				for (MoveSite s : MoveSites.get(level).all().stream().filter(m -> m.from().equals(from)).toList()) {
					for (int i = 0; i < 100_000 && s.stage() != MoveSite.Stage.DONE; i++) {
						MoveSites.get(level).step(level, s);
					}
				}
				PendingMoves.get(level).remove(from);
			}
			VillageHalls.RADIUS = was;
			Movers.isMover = isMover;
			sites.forgetFinished(a);
			sites.forgetFinished(b);
			MovePage.stop(player);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		if (huts) {
			for (BlueprintData.Placement p : List.of(a, b)) {
				level.getStructureManager().get(HUT).orElseThrow().placeInWorld(level, p.origin(), p.origin(), new StructurePlaceSettings(), RandomSource.create(1L), 2);
				sites.recordFinished(HUT, p, UUID.randomUUID());
			}
		}
		if (mover) {
			Villager hired = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 11));
			Movers.isMover = villager -> villager.getUUID().equals(hired.getUUID());
		}
		return new Village(level, helper.absolutePos(HALL), a.origin(), b.origin(), player, seen);
	}

	/** A real player in the level whose action-bar and chat lines are kept (as QaLostCaravanGameTests' listener). */
	static ServerPlayer listener(GameTestHelper helper, List<Seen> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "QaMover"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(new Seen(message, overlay));
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.CREATIVE);
		BlockPos at = helper.absolutePos(new BlockPos(15, 2, 14));
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	static BlueprintData.Placement placement(GameTestHelper helper, BlockPos rel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
	}

	static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	static String name() {
		return Blueprints.displayName(HUT).getString();
	}

	static CompoundTag view(ChoiceMenu menu) {
		CustomData data = menu.icon(MovePage.VIEW).get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag().getCompound(MovePage.VIEW_MARK);
	}

	static int step(ChoiceMenu menu) {
		CustomData data = menu.icon(MovePage.GUIDE).get(DataComponents.CUSTOM_DATA);
		return data == null ? -1 : data.copyTag().getInt(MovePage.STEP_MARK);
	}

	/** A fresh Hall with its Move tab clicked: the list (step 1). */
	static ChoiceMenu list(Village v) {
		ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
		menu.press(HallPages.slot(MovePage.PAGE), v.player());
		return menu;
	}

	/** The tab's lines on a fresh Hall. */
	static List<String> tab(Village v) {
		ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
		List<String> out = lore(menu.icon(HallPages.slot(MovePage.PAGE)));
		menu.removed(v.player());
		return out;
	}

	/** The list's slot of the building at {@code from}: its row, found by opening each until its picker opens. */
	static int rowOf(GameTestHelper helper, Village v, BlockPos from) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			ChoiceMenu menu = list(v);
			if (menu.icon(slot).isEmpty()) {
				break;
			}
			menu.press(slot, v.player());
			boolean it = MovePage.picking(v.player()).map(p -> p.from().equals(from)).orElse(false);
			menu.removed(v.player());
			MovePage.stop(v.player());
			if (it) {
				return slot;
			}
		}
		throw new AssertionError("no row on the Move page opens the picker of the building at " + from);
	}

	static ChoiceMenu openPickerFor(GameTestHelper helper, Village v, BlockPos from) {
		int slot = rowOf(helper, v, from);
		ChoiceMenu menu = list(v);
		menu.press(slot, v.player());
		helper.assertTrue(MovePage.picking(v.player()).map(p -> p.from().equals(from)).orElse(false), "the picker of " + from + " didn't open");
		return menu;
	}

	static void clickMap(GameTestHelper helper, Village v, ChoiceMenu menu, BlockPos rel) {
		BlockPos at = helper.absolutePos(rel);
		int id = MovePage.mapClick(v.player(), at.getX(), at.getZ());
		helper.assertTrue(MovePage.handles(id), "the block " + at + " isn't on the map: " + id);
		helper.assertTrue(menu.clickMenuButton(v.player(), id), "the hall didn't take the map click " + id);
	}

	/** Plans the move of the building at {@code from} to the spot round {@code to}, turned {@code turns} quarter turns, through its picker. */
	static PendingMoves.Move plan(GameTestHelper helper, Village v, BlockPos from, BlockPos to, int turns) {
		ChoiceMenu menu = openPickerFor(helper, v, from);
		clickMap(helper, v, menu, to);
		for (int i = 0; i < turns; i++) {
			menu.press(MovePage.ROTATE, v.player());
		}
		helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.PISTON), "Confirm isn't ready for " + from + " (setup): " + lore(menu.icon(MovePage.CONFIRM)));
		menu.press(MovePage.CONFIRM, v.player());
		menu.removed(v.player());
		return PendingMoves.get(v.level()).forBuilding(from).orElseThrow(() -> new AssertionError("the move of " + from + " wasn't saved (setup)"));
	}

	/** The last action-bar line the player was shown, or "(none)". */
	static String actionBar(Village v) {
		for (int i = v.seen().size() - 1; i >= 0; i--) {
			if (v.seen().get(i).overlay()) {
				return v.seen().get(i).text().getString();
			}
		}
		return "(none)";
	}

	static String planned(BlockPos to) {
		return "Move planned: to " + to.getX() + ", " + to.getY() + ", " + to.getZ();
	}

	/** Every name and line of every slot, for finding a sentence wherever the page puts it. */
	static List<String> words(ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (icon.isEmpty()) {
				continue;
			}
			out.add(icon.getHoverName().getString());
			out.addAll(lore(icon));
		}
		return out;
	}

	/**
	 * A village with no finished buildings: the list says so plainly and how buildings get there, the tab counts 0 moves,
	 * the page's title names the village, the header says no Mover works here, and clicking the note opens nothing.
	 * With a Mover hired the header says one works here.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHallMove1111c2None"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHallMove1111c2None")
	public void aVillageWithNoBuildingsSaysSoAndOpensNothing(GameTestHelper helper) {
		Village v = stage(helper, 6, false, false);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(BuildSiteManager.get(v.level()).finishedInVillage(v.level(), v.hall()).isEmpty(),
				"setup: a leftover finished building in this village: " + BuildSiteManager.get(v.level()).finishedInVillage(v.level(), v.hall()));
			helper.assertTrue(tab(v).contains("0 moves planned"), "the tab with nothing planned: " + tab(v));
			ChoiceMenu menu = list(v);
			String village = VillageHalls.name(v.level(), v.hall()).getString();
			List<String> all = words(menu);
			helper.assertTrue(all.contains("Move a building: " + village), "the page's title: " + all);
			helper.assertTrue(all.contains("No Mover works here yet: hire one to move buildings"), "no Mover: " + all);
			int rows = 0;
			int note = -1;
			for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
				ItemStack icon = menu.icon(slot);
				CustomData data = icon.get(DataComponents.CUSTOM_DATA);
				if (data != null && data.contains(MovePage.LIST_MARK)) {
					rows++;
					note = slot;
				}
			}
			helper.assertTrue(rows == 1, "an empty village shows " + rows + " rows, not one note");
			ItemStack icon = menu.icon(note);
			helper.assertTrue(icon.getHoverName().getString().equals("No buildings to move yet")
				&& lore(icon).equals(List.of("Buildings your builders finish in the village are listed here")), "the note: " + icon.getHoverName().getString() + " " + lore(icon));
			menu.press(note, v.player());
			helper.assertTrue(MovePage.picking(v.player()).isEmpty(), "clicking the note opened a picker");
			helper.assertTrue(menu.icon(note).getHoverName().getString().equals("No buildings to move yet"), "clicking the note changed the page");
			menu.removed(v.player());
			Villager hired = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 11));
			Movers.isMover = villager -> villager.getUUID().equals(hired.getUUID());
			List<String> withMover = words(list(v));
			helper.assertTrue(withMover.contains("A Mover works here") && !withMover.contains("No Mover works here yet: hire one to move buildings"),
				"with a Mover: " + withMover);
			helper.succeed();
		});
	}

	/** Adds {@code count} finished-building records (no blocks) inside the village round the hall, forgotten after the test. */
	private static void records(GameTestHelper helper, int count, List<BlueprintData.Placement> made) {
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		int i = made.size();
		for (int n = 0; n < count; n++, i++) {
			BlueprintData.Placement p = placement(helper, new BlockPos(6 + i % 13, 2, 4 + i / 13));
			sites.recordFinished(HUT, p, UUID.randomUUID());
			made.add(p);
		}
	}

	/**
	 * More buildings than the list has rows (four rows of nine): exactly that many are all listed, each a row saying where
	 * it stands and to click it; one more and the last slot says "And 2 more" instead (the rows it gives up and the extra
	 * one), every other slot still a building's row, and clicking that note opens nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHallMove1111c2More"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHallMove1111c2More")
	public void aListLongerThanThePageSaysHowManyMore(GameTestHelper helper) {
		Village v = stage(helper, 16, false, false);
		List<BlueprintData.Placement> made = new ArrayList<>();
		Leftovers.after(helper, () -> made.forEach(p -> BuildSiteManager.get(v.level()).forgetFinished(p)));
		helper.runAfterDelay(2, () -> {
			int shown = ChoiceMenu.SIZE - VillageHallScreen.FIRST_ROW;
			helper.assertTrue(shown == 36, "the list's rows: " + shown);
			int already = BuildSiteManager.get(v.level()).finishedInVillage(v.level(), v.hall()).size();
			helper.assertTrue(already <= shown, "setup: " + already + " leftover buildings in the village");
			records(helper, shown - already, made);
			ChoiceMenu full = list(v);
			for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
				ItemStack icon = full.icon(slot);
				List<String> lines = lore(icon);
				helper.assertTrue(icon.is(Items.BRICKS) && icon.getHoverName().getString().equals(name()) && lines.size() == 2
						&& lines.get(0).startsWith("Now: ") && lines.get(1).equals("Click to pick its new spot"),
					"with exactly " + shown + " buildings, slot " + slot + " isn't a building's row: " + icon.getHoverName().getString() + " " + lines);
			}
			full.removed(v.player());
			records(helper, 1, made);
			ChoiceMenu over = list(v);
			for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE - 1; slot++) {
				helper.assertTrue(over.icon(slot).is(Items.BRICKS), "with " + (shown + 1) + " buildings, slot " + slot + " isn't a row: " + over.icon(slot));
			}
			ItemStack more = over.icon(ChoiceMenu.SIZE - 1);
			helper.assertTrue(more.is(Items.PAPER) && more.getHoverName().getString().equals("And 2 more"),
				"with " + (shown + 1) + " buildings the last slot: " + more + " \"" + more.getHoverName().getString() + "\"");
			over.press(ChoiceMenu.SIZE - 1, v.player());
			helper.assertTrue(MovePage.picking(v.player()).isEmpty(), "clicking \"And 2 more\" opened a picker");
			helper.succeed();
		});
	}

	/**
	 * Two moves planned: Confirm's own sentence names the building and where it goes, the tab says "2 moves planned", both
	 * rows say where they're going. Call off on A's picker: the action bar says "The Test Hut stays where it is: the move
	 * is called off", the page is back on the list with A's row as before (no "Move planned"), B's move untouched, the tab
	 * "1 move planned", and A's blocks exactly as they were.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHallMove1111c2CallOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHallMove1111c2CallOff")
	public void callingOffOneOfTwoMovesCountsDownAndSaysSo(GameTestHelper helper) {
		Village v = stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			List<BlockState> before = snapshot(helper);
			PendingMoves.Move a = plan(helper, v, v.a(), A_TO, 0);
			helper.assertTrue(actionBar(v).equals("Move planned: a Mover will put the " + name() + " up again at " + a.to().getX() + ", " + a.to().getY() + ", " + a.to().getZ()),
				"Confirm's sentence: " + actionBar(v));
			helper.assertTrue(a.to().equals(v.a().offset(8, 0, 0)), "A's move: " + a);
			PendingMoves.Move b = plan(helper, v, v.b(), B_TO, 0);
			helper.assertTrue(tab(v).contains("2 moves planned"), "the tab with two planned: " + tab(v));
			int rowA = rowOf(helper, v, v.a());
			int rowB = rowOf(helper, v, v.b());
			ChoiceMenu menu = list(v);
			helper.assertTrue(menu.icon(rowA).is(Items.STICKY_PISTON) && lore(menu.icon(rowA)).contains(planned(a.to())), "A's row: " + lore(menu.icon(rowA)));
			helper.assertTrue(menu.icon(rowB).is(Items.STICKY_PISTON) && lore(menu.icon(rowB)).contains(planned(b.to())), "B's row: " + lore(menu.icon(rowB)));
			menu.removed(v.player());
			ChoiceMenu picker = openPickerFor(helper, v, v.a());
			ItemStack callOff = picker.icon(MovePage.CALL_OFF);
			helper.assertTrue(callOff.is(Items.COBWEB) && callOff.getHoverName().getString().equals("Call off the planned move"), "Call off: " + callOff);
			picker.press(MovePage.CALL_OFF, v.player());
			helper.assertTrue(actionBar(v).equals("The " + name() + " stays where it is: the move is called off"), "Call off's sentence: " + actionBar(v));
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isEmpty(), "A's move is still planned after Call off");
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.b()).equals(java.util.Optional.of(b)), "B's move changed: " + PendingMoves.get(v.level()).forBuilding(v.b()));
			CustomData row = picker.icon(VillageHallScreen.FIRST_ROW).get(DataComponents.CUSTOM_DATA);
			helper.assertTrue(row != null && row.contains(MovePage.LIST_MARK), "Call off didn't go back to the list: " + picker.icon(VillageHallScreen.FIRST_ROW));
			ItemStack aRow = picker.icon(rowA);
			helper.assertTrue(aRow.is(Items.BRICKS) && lore(aRow).stream().noneMatch(l -> l.startsWith("Move planned")), "A's row after Call off: " + aRow + " " + lore(aRow));
			helper.assertTrue(picker.icon(rowB).is(Items.STICKY_PISTON) && lore(picker.icon(rowB)).contains(planned(b.to())), "B's row after A's Call off: " + lore(picker.icon(rowB)));
			picker.removed(v.player());
			helper.assertTrue(tab(v).contains("1 move planned"), "the tab after one Call off: " + tab(v));
			helper.assertTrue(snapshot(helper).equals(before), "A's or B's blocks changed");
			helper.assertTrue(MoveSites.get(v.level()).forBuilding(v.a()).isEmpty(), "A's move started");
			helper.succeed();
		});
	}

	/** Every block of both huts and a block round them. */
	private static List<BlockState> snapshot(GameTestHelper helper) {
		List<BlockState> out = new ArrayList<>();
		for (int x = 2; x <= 8; x++) {
			for (int y = 1; y <= 6; y++) {
				for (int z = 2; z <= 17; z++) {
					out.add(helper.getBlockState(new BlockPos(x, y, z)));
				}
			}
		}
		return out;
	}

	/** Saves the planned moves and loads them back, as a server restart does. */
	private static void reload(ServerLevel level) {
		CompoundTag pending = PendingMoves.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(PendingMoves.NAME, PendingMoves.load(pending, level.registryAccess()));
	}

	/**
	 * A move planned a quarter turn (its front east), then the world saved and loaded: the tab still counts it, A's row
	 * says where it's going, and its picker opens on the planned spot still turned (the map's front east, the Rotate
	 * button's next turn south), with the step-3 instruction, "New spot" from the Hall and Call off; Confirm there again
	 * keeps one move, unchanged.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHallMove1111c2Reload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHallMove1111c2Reload")
	public void aTurnedMoveSurvivesAReloadAndReopensTurned(GameTestHelper helper) {
		Village v = stage(helper, 16, true, true);
		helper.runAfterDelay(2, () -> {
			PendingMoves.Move a = plan(helper, v, v.a(), A_TO, 1);
			helper.assertTrue(a.rotation() == Rotation.CLOCKWISE_90, "the planned turn: " + a.rotation());
			reload(v.level());
			PendingMoves.Move loaded = PendingMoves.get(v.level()).forBuilding(v.a()).orElseThrow(() -> new AssertionError("A's move didn't survive the reload"));
			helper.assertTrue(loaded.equals(a), "A's move over the reload: " + a + " became " + loaded);
			helper.assertTrue(tab(v).contains("1 move planned"), "the tab after the reload: " + tab(v));
			int rowA = rowOf(helper, v, v.a());
			ChoiceMenu menu = list(v);
			helper.assertTrue(menu.icon(rowA).is(Items.STICKY_PISTON) && lore(menu.icon(rowA)).contains(planned(a.to())), "A's row after the reload: " + lore(menu.icon(rowA)));
			menu.press(rowA, v.player());
			MovePage.Picking p = MovePage.picking(v.player()).orElseThrow(() -> new AssertionError("A's picker didn't open after the reload"));
			helper.assertTrue(p.rotation() == Rotation.CLOCKWISE_90, "reopened unturned: " + p.rotation());
			helper.assertTrue(MovePage.fit(v.player()).orElseThrow().min().equals(a.to()), "reopened off the planned spot: " + MovePage.fit(v.player()).orElseThrow().min() + " not " + a.to());
			helper.assertTrue(Direction.from2DDataValue(view(menu).getInt("front")) == Direction.EAST, "the map's front: " + Direction.from2DDataValue(view(menu).getInt("front")));
			helper.assertTrue(lore(menu.icon(MovePage.ROTATE)).contains("A quarter turn: its front will face south"), "Rotate's next turn: " + lore(menu.icon(MovePage.ROTATE)));
			helper.assertTrue(step(menu) == 3 && menu.icon(MovePage.GUIDE).getHoverName().getString().equals("Step 3 of 3: the spot fits. Press Confirm and a Mover moves the " + name()),
				"the instruction: " + menu.icon(MovePage.GUIDE).getHoverName().getString());
			List<String> guide = lore(menu.icon(MovePage.GUIDE));
			helper.assertTrue(guide.size() == 5 && guide.get(0).startsWith("Now: ") && guide.get(0).endsWith(" of the Hall")
				&& guide.get(1).startsWith("New spot: ") && guide.get(1).endsWith(" of the Hall") && guide.get(4).equals("A Mover works here"), "the guide: " + guide);
			helper.assertTrue(menu.icon(MovePage.CALL_OFF).is(Items.COBWEB) && lore(menu.icon(MovePage.CALL_OFF)).contains(planned(a.to())), "Call off: " + lore(menu.icon(MovePage.CALL_OFF)));
			helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.PISTON), "Confirm isn't ready on the planned spot: " + lore(menu.icon(MovePage.CONFIRM)));
			menu.press(MovePage.CONFIRM, v.player());
			helper.assertTrue(PendingMoves.get(v.level()).of(v.hall()).size() == 1 && PendingMoves.get(v.level()).forBuilding(v.a()).equals(java.util.Optional.of(loaded)),
				"Confirm again changed the moves: " + PendingMoves.get(v.level()).of(v.hall()));
			menu.removed(v.player());
			helper.assertTrue(tab(v).contains("1 move planned"), "the tab after confirming again: " + tab(v));
			helper.succeed();
		});
	}
}
