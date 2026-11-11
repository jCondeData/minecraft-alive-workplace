package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.bounty.Rogues;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Foes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane D, 2026-11-11: what the player is told when a rogue Pokémon's bounty pays, through Cobblemon's own events.
 * From the spec (ROADMAP 31.14): "a battle a player wins against it, or its capture, pays the bounty"; the player's
 * line and the hall's chronicle say which it was: a rogue beaten in battle is "beat ... in battle", and only a rogue
 * caught in a ball is "caught ... It's yours to keep". Before this, the BATTLE_VICTORY path was checked by money alone,
 * so a battle win told as a capture (the caught flag flipped) shipped unnoticed (mutation check, 2026-11-11).
 */
public class QaRoguesWildFoes1111dCompatTests implements FabricGameTest {
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
		Rogues.forget();
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
			Rogues.forget();
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(48)).forEach(Entity::discard);
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
		});
		return helper.absolutePos(HALL);
	}

	/** A survival player in the server's player list, standing by the hall, whose chat lines are kept in {@code seen}. */
	private static ServerPlayer listener(GameTestHelper helper, String name, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(Vec3.atCenterOf(HALL));
		player.teleportTo(level, at.x + 2, helper.absolutePos(HALL).getY(), at.z + 2, 0, 0);
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		Cobblemon.INSTANCE.getStorage().getParty(player).add(PokemonProperties.Companion.parse("pikachu level=30", " ", "=").create());
		return player;
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
		for (BattleActor a : battle.getActors()) {
			a.getActivePokemon().add(new com.cobblemon.mod.common.battles.ActiveBattlePokemon(a, a.getPokemonList().get(0)));
		}
		CobblemonEvents.BATTLE_VICTORY.post(new BattleVictoryEvent(battle, winners, losers, capture));
	}

	private static PokemonEntity rogue(GameTestHelper helper, Bounties.Bounty b) {
		helper.assertTrue(b != null, "setup: no rogue bounty posted");
		Entity e = Rogues.spawn(helper.getLevel(), b, null);
		helper.assertTrue(e instanceof PokemonEntity, "setup: no rogue: " + e);
		((PokemonEntity) e).setNoAi(true);
		return (PokemonEntity) e;
	}

	private static List<String> chronicle(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		return hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.BOUNTY).map(e -> e.text().getString()).toList();
	}

	private static List<String> lines(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/**
	 * A rogue beaten in a battle the player won (Cobblemon's BATTLE_VICTORY, no capture): the player is told "You beat
	 * Rogue Tauros! The bounty is yours", not that they caught it, and the hall's chronicle says they beat it in battle.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_rogue_told_beaten_1111d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_rogue_told_beaten_1111d")
	public void aRogueBeatenInBattleIsToldAsBeatenNotCaught(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:tauros"), helper.absolutePos(SPOT));
		Pokemon rogue = rogue(helper, b).getPokemon();
		String name = b.name(level).getString();
		helper.assertTrue(name.equals("Rogue Tauros"), "setup: the rogue's name: " + name);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, "Victor", seen);
		victory(List.of(side(player)), List.of(wild(rogue)), false);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Bounties.open(level, hall).isEmpty(), "setup: the battle won against the rogue didn't pay its bounty");
			List<String> said = lines(seen);
			helper.assertTrue(said.stream().anyMatch(s -> s.startsWith("You beat " + name + "! The bounty is yours")),
				"expected the winner told \"You beat " + name + "! The bounty is yours: ...\", got " + said);
			helper.assertTrue(said.stream().noneMatch(s -> s.contains("You caught")), "a battle win was told as a capture: " + said);
			List<String> chronicle = chronicle(helper);
			helper.assertTrue(chronicle.stream().anyMatch(s -> s.contains("Victor beat " + name + " in battle")),
				"expected the chronicle to say Victor beat " + name + " in battle, it says " + chronicle);
			helper.assertTrue(chronicle.stream().noneMatch(s -> s.contains(" caught " + name)), "the chronicle tells a battle win as a capture: " + chronicle);
			helper.succeed();
		});
	}

	/**
	 * A rogue caught in a ball (Cobblemon's POKEMON_CAPTURED, after the capture's own victory): the catcher is told "You
	 * caught Rogue Onix! It's yours to keep, and so is the bounty", once, and the chronicle says they caught it.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_rogue_told_caught_1111d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_rogue_told_caught_1111d")
	public void aRogueCaughtIsToldAsCaughtOnce(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Rogues.post(level, hall, ResourceLocation.parse("cobblemon:onix"), helper.absolutePos(SPOT));
		PokemonEntity entity = rogue(helper, b);
		Pokemon rogue = entity.getPokemon();
		String name = b.name(level).getString();
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, "Cathy", seen);
		victory(List.of(side(player)), List.of(wild(rogue)), true);
		helper.runAfterDelay(2, () -> {
			entity.discard();
			Cobblemon.INSTANCE.getStorage().getParty(player).add(rogue);
			CobblemonEvents.POKEMON_CAPTURED.post(new PokemonCapturedEvent(rogue, player, new EmptyPokeBallEntity(level)));
			helper.runAfterDelay(2, () -> {
				List<String> said = lines(seen);
				long caught = said.stream().filter(s -> s.startsWith("You caught " + name + "! It's yours to keep, and so is the bounty")).count();
				helper.assertTrue(caught == 1, "expected the catcher told once \"You caught " + name + "! It's yours to keep, and so is the bounty: ...\", got " + said);
				helper.assertTrue(said.stream().noneMatch(s -> s.startsWith("You beat")), "a capture was told as a battle win: " + said);
				List<String> chronicle = chronicle(helper);
				helper.assertTrue(chronicle.stream().anyMatch(s -> s.contains("Cathy caught " + name)),
					"expected the chronicle to say Cathy caught " + name + ", it says " + chronicle);
				helper.assertTrue(chronicle.stream().noneMatch(s -> s.contains(" beat " + name + " in battle")), "the chronicle tells a capture as a battle win: " + chronicle);
				helper.succeed();
			});
		});
	}
}
