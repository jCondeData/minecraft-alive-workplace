package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonProfessor;
import io.github.jcondedata.aliveworkplace.legend.PokemonProfessor;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * QA (lane D, 2026-11-15) for the Pokemon Professor's list screen (ROADMAP 42.2, wiki list-screen.md: "the tooltip of
 * whatever is under the mouse, as the chest showed it", the money line's tooltip is the Professor's book): hovering the
 * money line says what he does and whether he speaks in words or exact numbers (Regional Survey), and hovering today's
 * stone says its price, then that it's sold once bought. Kills the mutation pass's CobblemonProfessor:241 (the lore
 * dropped from every named icon) and :240 ({@code lines.length > 0} to {@code > 1}: the stone's one-line tooltip lost).
 */
public class QaProfessorTooltips1115dCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	private static List<String> keys(ItemStack stack) {
		var lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream()
			.map(c -> c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString()).toList();
	}

	//$ gametest_ticks_batch AREA '200' '"qaProfessorTooltips1115d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaProfessorTooltips1115d")
	public void theMoneyLineAndTheStoneSayWhatTheyDoOnHover(GameTestHelper helper) {
		ProfessorCompatTests.setUp(helper);
		helper.runAfterDelay(5, () -> {
			Villager prof = ProfessorCompatTests.professor(helper, new BlockPos(13, 2, 13));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			CobbleDollarsBank.add(player, 2000);

			ChoiceMenu menu = CobblemonProfessor.menuForTest(player, prof);
			helper.assertValueEqual(keys(menu.icon(CobblemonProfessor.INFO)),
				List.of("screen.aliveworkplace.professor.info", "screen.aliveworkplace.professor.words"), "the money line's tooltip without Regional Survey");

			ProfessorCompatTests.research(helper, Map.of(PokemonProfessor.EVOLUTION_STUDIES, 1, PokemonProfessor.REGIONAL_SURVEY, 1));
			helper.assertTrue(PokemonProfessor.exact(prof) && PokemonProfessor.sellsStones(prof), "setup: the research didn't take");
			menu = CobblemonProfessor.menuForTest(player, prof);
			helper.assertValueEqual(keys(menu.icon(CobblemonProfessor.INFO)),
				List.of("screen.aliveworkplace.professor.info", "screen.aliveworkplace.professor.exact"), "the money line's tooltip with Regional Survey");
			ItemStack stone = menu.icon(CobblemonProfessor.STONE);
			helper.assertTrue(stone.is(PokemonProfessor.stoneOfDay(helper.getLevel())), "setup: today's stone isn't on the screen: " + stone);
			helper.assertValueEqual(keys(stone), List.of("screen.aliveworkplace.professor.stone_buy"), "today's stone's tooltip before it's bought");
			Component buy = stone.get(DataComponents.LORE).lines().get(0);
			String price = io.github.jcondedata.aliveworkplace.work.Money.describe((long) PokemonProfessor.STONE_PRICE * io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD,
				PokemonProfessor.STONE_PRICE).getString();
			Object arg = ((TranslatableContents) buy.getContents()).getArgs()[0];
			helper.assertValueEqual(arg instanceof Component c ? c.getString() : String.valueOf(arg), price, "the price in the stone's tooltip");

			menu.press(CobblemonProfessor.STONE, player);
			helper.assertTrue(player.getInventory().countItem(stone.getItem()) == 1, "setup: no stone bought");
			helper.assertValueEqual(keys(menu.icon(CobblemonProfessor.STONE)), List.of("screen.aliveworkplace.professor.stone_sold"), "today's stone's tooltip once it's sold");
			helper.succeed();
		});
	}
}
