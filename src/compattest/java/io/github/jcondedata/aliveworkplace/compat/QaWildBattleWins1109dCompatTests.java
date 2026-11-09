package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.bounty.Rogues;
import io.github.jcondedata.aliveworkplace.hall.Foes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.Bank;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane D, 2026-11-09: a battle a player WINS against wild Pokémon, as Cobblemon reports it (its BATTLE_VICTORY event,
 * posted here with real actors: the player's side and the wild side). From the spec: 31.14 "a battle a player wins
 * against it, or its capture, pays the bounty (once)"; B301 "a battle a player wins against wild Pokémon, or a wild
 * Pokémon caught, counts towards the village's clearing-out quests, each Pokémon beaten once". A capture's battle ends
 * in a victory too, and it pays and counts through the capture alone. Before this, no test went through the
 * BATTLE_VICTORY listeners of CobblemonRogues and CobblemonWildFoes (QaRogueCapture1101d: "needs a whole Cobblemon
 * battle"), so any of their checks could be deleted unnoticed.
 */
public class QaWildBattleWins1109dCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos SPOT = new BlockPos(18, 2, 18);

	private static BlockPos village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(48)).forEach(Entity::discard);
		int radius = VillageHalls.RADIUS;
		boolean enabled = Bounties.ENABLED;
		boolean rogues = Rogues.ENABLED;
		VillageHalls.RADIUS = 10;
		Bounties.ENABLED = true;
		Rogues.ENABLED = true;
		Foes.HOSTILES_FOR_TESTS = false;
		Bounties.forget(level);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		hall.setLastQuestDay(Long.MAX_VALUE / 2);
		hall.setRank(VillageRanks.Rank.TOWN);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Bounties.ENABLED = enabled;
			Rogues.ENABLED = rogues;
			Foes.HOSTILES_FOR_TESTS = null;
			Bounties.forget(level);
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(48)).forEach(Entity::discard);
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
		});
		return helper.absolutePos(HALL);
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos hall) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(hall.getX() + 2.5, hall.getY(), hall.getZ() + 2.5);
		Cobblemon.INSTANCE.getStorage().getParty(player).add(PokemonProperties.Companion.parse("pikachu level=30", " ", "=").create());
		return player;
	}

	private static long money(ServerPlayer player) {
		long dollars = Bank.EXTENSION.call(bank -> bank.balance(player), 0L);
		return dollars + (long) player.getInventory().countItem(Items.EMERALD) * Money.DOLLARS_PER_EMERALD;
	}

	private static PlayerBattleActor side(ServerPlayer player) {
		Pokemon lead = Cobblemon.INSTANCE.getStorage().getParty(player).get(0);
		return new PlayerBattleActor(player.getUUID(), List.of(BattlePokemon.Companion.playerOwned(lead)));
	}

	private static PokemonBattleActor wild(Pokemon pokemon) {
		return new PokemonBattleActor(pokemon.getUuid(), BattlePokemon.Companion.safeCopyOf(pokemon), 15f, new RandomBattleAI());
	}

	/** Cobblemon's word that the battle between {@code winners} and {@code losers} is over. */
	private static void victory(List<? extends BattleActor> winners, List<? extends BattleActor> losers, boolean capture) {
		BattleSide one = new BattleSide(winners.toArray(new BattleActor[0]));
		BattleSide two = new BattleSide(losers.toArray(new BattleActor[0]));
		PokemonBattle battle = new PokemonBattle(BattleFormat.Companion.getGEN_9_SINGLES(), one, two);
		// Each side's lead out on the field, as in a battle under way (other mods' listeners, Cobblemon Raid Dens', read it).
		for (BattleActor a : battle.getActors()) {
			a.getActivePokemon().add(new com.cobblemon.mod.common.battles.ActiveBattlePokemon(a, a.getPokemonList().get(0)));
		}
		CobblemonEvents.BATTLE_VICTORY.post(new BattleVictoryEvent(battle, winners, losers, capture));
	}

	/** A plain wild Pokémon of {@code pokemon}'s species and level. */
	private static Pokemon like(Pokemon pokemon) {
		return wildOne(pokemon.getSpecies().getResourceIdentifier().toString() + " level=" + pokemon.getLevel());
	}

	private static Pokemon wildOne(String spec) {
		return PokemonProperties.Companion.parse(spec, " ", "=").create();
	}

	private static Quest slay(ServerLevel level, BlockPos hall) {
		long now = level.getGameTime();
		Quest quest = new Quest(UUID.randomUUID(), AliveWorkplace.id("daily/slay"), "hall", Optional.empty(), "", now, now + VillageQuests.LASTS,
			List.of(new Objectives.Kill("monster", 8, false)), new int[1], List.of());
		Stories.post(level, hall, quest);
		return quest;
	}

	/**
	 * A rogue beaten in a battle the player won pays the player the bounty, and the bounty comes down; the same win
	 * reported again pays nothing more. The beaten rogue (not caught) is no one's to keep.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_wild_wins_rogue_1109d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_wild_wins_rogue_1109d")
	public void aRogueBeatenInABattleThePlayerWonPaysOnce(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:onix"), helper.absolutePos(SPOT));
		helper.assertTrue(b != null, "setup: no rogue bounty posted");
		Entity e = Rogues.spawn(level, b, null);
		helper.assertTrue(e instanceof PokemonEntity, "setup: no rogue: " + e);
		Pokemon rogue = ((PokemonEntity) e).getPokemon();
		ServerPlayer player = player(helper, hall);
		long[] at = {money(player), 0};
		// The control: a plain wild Pokémon of the rogue's species and level beaten the same way (CobbleDollars pays its
		// own prize for a won battle, a few hundred that varies; the bounty is paid on top of it).
		victory(List.of(side(player)), List.of(wild(like(rogue))), false);
		helper.runAfterDelay(2, () -> {
			long prize = money(player) - at[0];
			at[1] = money(player);
			victory(List.of(side(player)), List.of(wild(rogue)), false);
			helper.runAfterDelay(2, () -> {
				long expected = (long) b.reward * Money.DOLLARS_PER_EMERALD;
				long paid = money(player) - at[1];
				helper.assertTrue(prize < expected, "setup: CobbleDollars' own prize for a plain win (" + prize + ") isn't under the bounty (" + expected + ")");
				helper.assertTrue(paid >= expected,
					"expected the battle won against the rogue to pay the bounty, " + b.reward + " emeralds' worth (" + expected + "), with CobbleDollars' own prize (a plain win: " + prize + "); paid " + paid);
				helper.assertTrue(Bounties.open(level, hall).isEmpty() && Bounties.Data.get(level).claimed.contains(b.id), "expected the bounty down after the win");
				at[1] = money(player);
				victory(List.of(side(player)), List.of(wild(rogue)), false);
				helper.runAfterDelay(2, () -> {
					helper.assertTrue(money(player) - at[1] < expected, "the same win reported twice paid the bounty again: " + (money(player) - at[1]) + " (a plain win: " + prize + ")");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * The rogue's battle ending in its capture is a victory too: it pays nothing through the battle (the capture pays),
	 * and the bounty stays up until the capture is reported.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_wild_wins_capture_1109d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_wild_wins_capture_1109d")
	public void aCapturesVictoryPaysNothingThroughTheBattle(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:tauros"), helper.absolutePos(SPOT));
		helper.assertTrue(b != null, "setup: no rogue bounty posted");
		Entity e = Rogues.spawn(level, b, null);
		helper.assertTrue(e instanceof PokemonEntity, "setup: no rogue: " + e);
		Pokemon rogue = ((PokemonEntity) e).getPokemon();
		ServerPlayer player = player(helper, hall);
		Quest quest = slay(level, hall);
		long[] at = {money(player), 0};
		victory(List.of(side(player)), List.of(wild(like(rogue))), true); // the control (CobbleDollars' own prize, if any)
		helper.runAfterDelay(2, () -> {
			long prize = money(player) - at[0];
			at[1] = money(player);
			victory(List.of(side(player)), List.of(wild(rogue)), true);
			helper.runAfterDelay(2, () -> {
				long bounty = (long) b.reward * Money.DOLLARS_PER_EMERALD;
				helper.assertTrue(prize < bounty, "setup: CobbleDollars' own prize for a plain capture's battle (" + prize + ") isn't under the bounty (" + bounty + ")");
				helper.assertTrue(money(player) - at[1] < bounty, "a capture's battle paid the bounty through the victory: " + (money(player) - at[1]) + " (a plain capture's battle: " + prize + ")");
				helper.assertTrue(Bounties.open(level, hall).size() == 1, "the bounty came down on a capture's victory alone");
				helper.assertTrue(quest.progress[0] == 0, "a capture's victory counted towards 'Defeat 8 wild Pokémon': " + quest.progress[0]);
				helper.succeed();
			});
		});
	}

	/** The wild side won (the player lost the battle): no bounty is paid, no wild Pokémon counted. */
	//$ gametest_ticks_batch AREA '200' '"qa_wild_wins_lost_1109d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_wild_wins_lost_1109d")
	public void aBattleThePlayerLostPaysAndCountsNothing(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:pinsir"), helper.absolutePos(SPOT));
		helper.assertTrue(b != null, "setup: no rogue bounty posted");
		Entity e = Rogues.spawn(level, b, null);
		helper.assertTrue(e instanceof PokemonEntity, "setup: no rogue: " + e);
		Pokemon rogue = ((PokemonEntity) e).getPokemon();
		ServerPlayer player = player(helper, hall);
		Quest quest = slay(level, hall);
		long before = money(player);
		victory(List.of(wild(rogue)), List.of(side(player)), false);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(money(player) == before, "a battle the player lost to the rogue paid: " + (money(player) - before));
			helper.assertTrue(Bounties.open(level, hall).size() == 1, "the bounty came down when the rogue won");
			helper.assertTrue(quest.progress[0] == 0, "a battle lost to a wild Pokémon counted towards 'Defeat 8 wild Pokémon': " + quest.progress[0]);
			helper.succeed();
		});
	}

	/**
	 * A battle won against two wild Pokémon (a double) counts both towards "Defeat 8 wild Pokémon", once each, and one
	 * won against a single wild Pokémon counts one; a capture
	 * counts one; a capture's own victory counts nothing more.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_wild_wins_count_1109d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_wild_wins_count_1109d")
	public void eachWildPokemonBeatenInAWonBattleCountsOnce(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper, hall);
		Quest quest = slay(level, hall);
		victory(List.of(side(player)), List.of(wild(wildOne("rattata level=5")), wild(wildOne("pidgey level=5"))), false);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(quest.progress[0] == 2, "two wild Pokémon beaten in a won battle counted " + quest.progress[0] + ", expected 2");
			victory(List.of(side(player)), List.of(wild(wildOne("zubat level=6"))), false);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(quest.progress[0] == 3, "one wild Pokémon beaten in a won battle counted " + (quest.progress[0] - 2) + ", expected 1");
				Pokemon caught = wildOne("caterpie level=4");
				victory(List.of(side(player)), List.of(wild(caught)), true);
				CobblemonEvents.POKEMON_CAPTURED.post(new com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent(caught, player,
					new com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity(level)));
				helper.runAfterDelay(2, () -> {
					helper.assertTrue(quest.progress[0] == 4, "a capture (its victory and its capture) counted " + (quest.progress[0] - 3) + ", expected 1");
					helper.succeed();
				});
			});
		});
	}

	/** A battle between a player and another player's side with no wild Pokémon in it counts nothing. */
	//$ gametest_ticks_batch AREA '200' '"qa_wild_wins_pvp_1109d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_wild_wins_pvp_1109d")
	public void aWinOverAnotherPlayerCountsNoWildPokemon(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper, hall);
		ServerPlayer other = player(helper, hall);
		Quest quest = slay(level, hall);
		victory(List.of(side(player)), List.of(side(other)), false);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(quest.progress[0] == 0, "a win over another player counted " + quest.progress[0] + " wild Pokémon");
			helper.succeed();
		});
	}
}
