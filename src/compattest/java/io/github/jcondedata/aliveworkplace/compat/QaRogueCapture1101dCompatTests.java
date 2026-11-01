package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.bounty.Rogues;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonRogues;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Bank;
import io.github.jcondedata.aliveworkplace.work.Money;
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
 * QA (lane-d-1101-1310) for 31.14 Rogue Pokémon with the real Cobblemon, from the spec ("its capture pays the bounty
 * (once)", the rogue "appears at the bounty's spot"), killing two survivors of a mutation spot check on
 * CobblemonRogues: the capture listener deleted (:89; the older tests call {@code beaten} directly, never through
 * Cobblemon's own event) and the rogue moved half a block off its spot (:153). (:76, the battle-victory listener's
 * empty-side check, needs a whole Cobblemon battle and is left to RogueCompatTests' direct calls.)
 */
public class QaRogueCapture1101dCompatTests implements FabricGameTest {
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
		Bounties.forget(level);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		hall.setLastQuestDay(Long.MAX_VALUE / 2);
		hall.setRank(VillageRanks.Rank.TOWN);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Bounties.ENABLED = enabled;
			Rogues.ENABLED = rogues;
			Bounties.forget(level);
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(48)).forEach(Entity::discard);
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
		});
		return helper.absolutePos(HALL);
	}

	private static long money(ServerPlayer player) {
		long dollars = Bank.EXTENSION.call(bank -> bank.balance(player), 0L);
		return dollars + (long) player.getInventory().countItem(Items.EMERALD) * Money.DOLLARS_PER_EMERALD;
	}

	/**
	 * A rogue caught in a ball, as Cobblemon reports a capture (its {@code POKEMON_CAPTURED} event): the catcher is paid
	 * the bounty, the bounty comes down, the caught Pokémon carries it no more; the same capture reported again pays nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_rogue_catch_event_1101d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_rogue_catch_event_1101d")
	public void cobblemonsOwnCaptureEventPaysTheCatcherOnce(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:onix"), helper.absolutePos(SPOT));
		helper.assertTrue(b != null, "setup: no rogue bounty posted");
		Entity e = Rogues.spawn(level, b, null);
		helper.assertTrue(e instanceof PokemonEntity, "setup: no rogue: " + e);
		PokemonEntity rogue = (PokemonEntity) e;
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Pokemon pokemon = rogue.getPokemon();
		rogue.discard();
		com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
		long before = money(player);
		EmptyPokeBallEntity ball = new EmptyPokeBallEntity(level);
		CobblemonEvents.POKEMON_CAPTURED.post(new PokemonCapturedEvent(pokemon, player, ball));
		long expected = (long) b.reward * Money.DOLLARS_PER_EMERALD;
		helper.assertTrue(money(player) - before == expected,
			"expected Cobblemon's capture event to pay the catcher " + b.reward + " emeralds' worth (" + expected + "), paid " + (money(player) - before));
		helper.assertTrue(Bounties.open(level, hall).isEmpty() && Bounties.Data.get(level).claimed.contains(b.id), "expected the bounty down after the capture");
		helper.assertTrue(CobblemonRogues.bountyOf(pokemon) == null, "expected the caught rogue to carry no bounty");
		CobblemonEvents.POKEMON_CAPTURED.post(new PokemonCapturedEvent(pokemon, player, ball));
		helper.assertTrue(money(player) - before == expected, "the same capture reported twice paid twice: " + (money(player) - before));
		helper.succeed();
	}

	/** The rogue stands over its bounty's spot, in the middle of the block on top of the column. */
	//$ gametest_ticks_batch AREA '200' '"qa_rogue_spot_1101d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_rogue_spot_1101d")
	public void theRogueStandsOnItsSpot(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos spot = helper.absolutePos(SPOT);
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:tauros"), spot);
		Entity e = Rogues.spawn(level, b, null);
		helper.assertTrue(e instanceof PokemonEntity, "setup: no rogue: " + e);
		BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot);
		helper.assertTrue(e.blockPosition().equals(top) && Math.abs(e.getX() - (spot.getX() + 0.5)) < 1.0E-6 && Math.abs(e.getZ() - (spot.getZ() + 0.5)) < 1.0E-6,
			"expected the rogue in the middle of its spot's column, on top (" + top.toShortString() + "), it stands at " + e.position());
		helper.succeed();
	}
}
