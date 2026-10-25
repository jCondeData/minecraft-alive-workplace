package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupPage;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Bugs QA found in the Festival Cup (28.17), as failing tests for the builder; NOT registered (main stays green). Register
 * this class in src/gametest/resources/fabric.mod.json together with the fix.
 */
public class QaCupBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final long DAY = 24000;

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static ChoiceMenu page(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(HallPages.slot(CupPage.PAGE), player);
		return menu;
	}

	/**
	 * Spec: "A bracket of 8, with 1 bye for the top seeds" with 7 entrants. Actual: "... with 1 byes ..." (the key
	 * screen.aliveworkplace.cup.byes has no singular form and CupPage doesn't use Words.counted).
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaCupBugByes"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCupBugByes")
	public void oneByeIsSingular(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			VillageHallBlockEntity entity = QaCupGameTests.host(helper, hall, (VillageHallBlockEntity) helper.getBlockEntity(HALL), List.of());
			entity.setRank(VillageRanks.Rank.TOWN);
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			List<CupData.Entrant> seven = new ArrayList<>();
			for (int i = 0; i < 7; i++) {
				seven.add(new CupData.Entrant(CupData.Kind.LEADER, UUID.nameUUIDFromBytes(("b" + i).getBytes()), "L" + i, 5 - i % 5, 0, hall));
			}
			cup.closed = true;
			cup.noCup = "";
			cup.entrants.clear();
			cup.entrants.addAll(Cups.seed(seven));
			cup.bracket.clear();
			cup.bracket.addAll(Cups.bracket(cup.entrants, 8));
			List<String> lines = lore(page(helper.makeMockServerPlayerInLevel(), hall).icon(CupPage.BRACKET));
			helper.assertTrue(lines.contains("A bracket of 8, with 1 bye for the top seeds"), "expected the singular, got " + lines);
			helper.succeed();
		});
	}

	/**
	 * Spec (28.17): "Players in circuit villages are told when sign-up opens (when the festival before ends)", and the page
	 * says "Sign-up opens after the festival on day N". Expected: before then, Sign up doesn't put the player's name down.
	 * Actual: Cups.signUp never checks Cups.signupOpen, so a player signs up while the card says sign-up hasn't opened.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaCupBugEarly"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCupBugEarly")
	public void noSignUpBeforeItOpens(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			VillageHallBlockEntity entity = QaCupGameTests.host(helper, hall, (VillageHallBlockEntity) helper.getBlockEntity(HALL), List.of());
			Cups.EVERY = 2;
			int gap = Festivals.every(entity);
			long cupDay = Cups.cupDay(300, Festivals.offset(hall), gap, 2);
			level.setDayTime((cupDay - gap - 1) * DAY + 12_999); // the festival before, a tick before it ends
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			ChoiceMenu menu = page(player, hall);
			helper.assertTrue(lore(menu.icon(CupPage.CARD)).contains("Sign-up opens after the festival on day " + (cupDay - gap)), "setup: the card "
				+ lore(menu.icon(CupPage.CARD)));
			String said = Cups.signUp(level, hall, player).getString();
			helper.assertTrue(cup.signups.isEmpty(), "signed up before sign-up opened (told \"" + said + "\")");
			helper.succeed();
		});
	}
}
