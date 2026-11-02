package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (1102c): village sentences read right when their count is one. The hall's Food button with one kind of meal in
 * the store, its Mercenaries button with one mercenary left of the band, and the chronicle of a raid of one fought off;
 * each with its plural control. The singular checks, which fail on the mod's text, are QaOneCount1102cBugTests on
 * tests/one-count-1102c (B-numbers in ROADMAP Bugs).
 */
public class QaOneCount1102cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(HALL);
	}

	// ---- The Food button: kinds of meal in the store ----

	static void mealKinds(GameTestHelper helper, List<ItemStack> meals, String expected) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(17, 2, 17), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(17, 2, 19), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(17, 2, 19));
		for (int i = 0; i < meals.size(); i++) {
			chest.setItem(i, meals.get(i));
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(VillageHalls.mealKinds(level, hall) == meals.size(), "setup: kinds " + VillageHalls.mealKinds(level, hall));
			List<String> lines = lore(VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.FOOD));
			List<String> kinds = lines.stream().filter(l -> l.contains("of meal in the store")).toList();
			helper.assertTrue(kinds.equals(List.of(expected)), "the Food button's kinds line: " + kinds + ", expected '" + expected + "'");
			helper.succeed();
		});
	}


	/** Bread and baked potatoes: the plural control. */
	//$ gametest_ticks_batch AREA '60' '"qaOne1102cTwoMealKinds"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaOne1102cTwoMealKinds")
	public void qaTwoKindsOfMealReadPlural(GameTestHelper helper) {
		mealKinds(helper, List.of(new ItemStack(Items.BREAD, 5), new ItemStack(Items.BAKED_POTATO, 5)),
			"2 kinds of meal in the store (3 or more for a varied diet)");
	}

	// ---- The Mercenaries button: the band at the hall ----

	/** Hires a band with the hall's own button, lets {@code fallen} of it die, and reads the button again. */
	static void mercenaries(GameTestHelper helper, int fallen, String expected) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.EMERALD, Mercenaries.PRICE_EMERALDS));
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.runAfterDelay(5, () -> {
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			menu.press(VillageHallScreen.MERCENARIES, player);
			List<Villager> band = Mercenaries.near(level, hall);
			helper.assertTrue(band.size() == Mercenaries.BAND, "setup: the button hired a band of " + band.size());
			for (int i = 0; i < fallen; i++) {
				band.get(i).discard();
			}
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(Mercenaries.near(level, hall).size() == Mercenaries.BAND - fallen, "setup: left " + Mercenaries.near(level, hall).size());
				List<String> lines = lore(VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.MERCENARIES));
				helper.assertTrue(lines.equals(List.of(expected)), "the Mercenaries button: " + lines + ", expected '" + expected + "'");
				band.forEach(Villager::discard);
				helper.succeed();
			});
		});
	}


	/** The whole band: the plural control. */
	//$ gametest_ticks_batch AREA '60' '"qaOne1102cThreeMercenaries"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaOne1102cThreeMercenaries")
	public void qaWholeBandReadsPlural(GameTestHelper helper) {
		mercenaries(helper, 0, "3 mercenaries are here until dawn");
	}

	// ---- The chronicle: a raid fought off ----

	/** A raid of {@code raiders} zombies at the hall, all slain; the hall's round ends it and writes the chronicle. */
	static void raidWon(GameTestHelper helper, int raiders, String expected) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> VillageRaids.forget(hall));
		helper.runAfterDelay(5, () -> {
			List<net.minecraft.world.entity.Mob> band = new ArrayList<>();
			for (int i = 0; i < raiders; i++) {
				Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(4 + 2 * i, 2, 4));
				z.setNoAi(true);
				z.addTag(VillageRaids.TAG);
				band.add(z);
			}
			VillageRaids.track(level, hall, raiders);
			helper.assertTrue(VillageRaids.active(hall).isPresent(), "setup: no raid");
			band.forEach(m -> m.kill());
			helper.runAfterDelay(3, () -> {
				long day = Chronicle.day(level);
				VillageRaids.tick(level, hall, 8, 0, day - 10, d -> { });
				helper.assertTrue(VillageRaids.active(hall).isEmpty(), "setup: the raid didn't end");
				VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
				List<String> raids = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.RAID).map(e -> e.text().getString()).toList();
				helper.assertTrue(raids.equals(List.of(expected)), "the chronicle's raid entries: " + raids + ", expected '" + expected + "'");
				helper.succeed();
			});
		});
	}


	/** Two zombies: the plural control. */
	//$ gametest_ticks_batch AREA '60' '"qaOne1102cRaidOfTwo"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaOne1102cRaidOfTwo")
	public void qaRaidOfTwoFoughtOffReadsPlural(GameTestHelper helper) {
		raidWon(helper, 2, "The village fought off the raid (2 monsters)");
	}
}
