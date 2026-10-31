package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonProfessor;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonRanger;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.PokemonCensus;
import io.github.jcondedata.aliveworkplace.legend.PokemonProfessor;
import io.github.jcondedata.aliveworkplace.legend.PokemonRanger;
import io.github.jcondedata.aliveworkplace.legend.WildPokemon;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-10-27): ROADMAP 29.21 (the Pokémon Professor) and 29.22 (the Pokémon Ranger) from their specs, on what
 * {@link ProfessorCompatTests}, {@link RangerCompatTests} and {@link QaPokemonLegendsCompatTests} leave open: a player's
 * sent-out Pokémon is never befriended even when it's the only one near; a Pasture Block outside the village counts
 * neither for the census nor for the village Pokédex; a stone the player can't pay for costs nothing and doesn't use up
 * the day's stone, which is still one a day for everybody; the hints offer only the clicking player's party; and a
 * Pokémon befriended while the owner is away waits in their PC, with its chronicle line.
 */
public class QaPokemonLegends1027CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	/** A hall in the middle (radius 14), Legend needs off, Pokémon and villagers round cleared; everything put back after. */
	private static void setUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(70)).forEach(e -> e.discard());
		level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8)).forEach(e -> e.discard());
		int radius = VillageHalls.RADIUS;
		boolean needs = LegendNeeds.ENABLED;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 14;
		LegendNeeds.ENABLED = false;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			LegendNeeds.ENABLED = needs;
			level.setDayTime(time);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(70)).forEach(e -> e.discard());
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
			CivicEffects.forget();
			LegendPowers.forget();
			VillageNeeds.forget();
			Moods.forget();
		});
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static Legend legend(net.minecraft.resources.ResourceLocation id) {
		return Legends.get(id).orElseThrow(() -> new AssertionError(id + " didn't load with Cobblemon"));
	}

	private static Villager make(GameTestHelper helper, net.minecraft.resources.ResourceLocation id, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		Legends.make(helper.getLevel(), v, legend(id), "test");
		LegendPowers.forget();
		return v;
	}

	private static PokemonEntity wild(GameTestHelper helper, String properties, BlockPos at) {
		PokemonEntity e = PokemonProperties.Companion.parse(properties, " ", "=").createEntity(helper.getLevel());
		e.setPos(helper.absoluteVec(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)));
		e.setNoAi(true);
		helper.getLevel().addFreshEntity(e);
		return e;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	/** The village's Pokédex research: Evolution Studies learned. */
	private static void evolutionStudies(GameTestHelper helper) {
		String tree = ResearchTrees.get(PokemonProfessor.TREE).orElseThrow(() -> new AssertionError("no Pokédex tree")).key();
		hall(helper).setResearch(new Research.State(Map.of(tree + "/" + PokemonProfessor.EVOLUTION_STUDIES, 1), Optional.empty(), 0, false));
		CivicEffects.forget();
	}

	private static void setBalance(ServerPlayer player, long amount) {
		CobbleDollarsBank.add(player, amount - CobbleDollarsBank.balance(player));
	}

	// ---------------------------------------------------------------- the Ranger (29.22)

	/**
	 * "befriends a wild Pokémon": a player's Pokémon sent out of their party beside the Ranger, the only Pokémon near, is
	 * not wild: never chosen for the day's befriending, never befriendable, a befriending asked for anyway is refused, and
	 * it stays its trainer's, out of the pasture.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa1027_sent_out"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa1027_sent_out")
	public void aPlayersSentOutPokemonIsNeverBefriended(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(level.getDayTime() / 24000L * 24000L + 7000L); // past the morning: no calming first
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = player(helper);
		hall(helper).setOwner(owner.getUUID(), owner.getGameProfile().getName());
		ServerPlayer trainer = player(helper);
		trainer.setPos(helper.absoluteVec(new Vec3(9.5, 2, 10.5)));
		Pokemon pikachu = PokemonProperties.Companion.parse("pikachu level=10", " ", "=").create();
		Cobblemon.INSTANCE.getStorage().getParty(trainer).add(pikachu);
		PokemonEntity out = pikachu.sendOut(level, helper.absoluteVec(new Vec3(8.5, 2, 9.5)), null, e -> {
			e.setNoAi(true);
			return kotlin.Unit.INSTANCE;
		});
		helper.assertTrue(out != null, "the trainer's Pikachu wasn't sent out");
		Villager ranger = make(helper, PokemonRanger.ID, new BlockPos(7, 2, 9));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(out.isAlive() && level.getEntity(out.getUUID()) == out, "the sent-out Pikachu isn't in the world");
			WildPokemon w = new CobblemonRanger();
			helper.assertTrue(!w.isWild(out), "a player's sent-out Pikachu counts as wild");
			helper.assertTrue(!w.befriendable(level, hall, PokemonRanger.BEFRIEND_RADIUS).contains(out), "a player's sent-out Pikachu is befriendable");
			helper.assertTrue(w.pastureWithRoom(level, hall, owner.getUUID()).isPresent(), "the control: the pasture has room");
			PokemonRanger.tick(ranger);
			PokemonRanger.tick(ranger);
			helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.NONE, "the Ranger goes after a trainer's Pokémon: " + PokemonRanger.state(ranger));
			WildPokemon.Befriended result = PokemonRanger.befriend(level, hall, ranger, out, pasture);
			helper.assertTrue(result == WildPokemon.Befriended.REFUSED, "a trainer's sent-out Pikachu befriended: " + result);
			helper.assertTrue(trainer.getUUID().equals(pikachu.getOwnerUUID()), "the Pikachu isn't its trainer's any more: " + pikachu.getOwnerUUID());
			helper.assertTrue(Cobblemon.INSTANCE.getStorage().getParty(trainer).get(0) == pikachu, "the Pikachu left its trainer's party");
			helper.assertTrue(((PokemonPastureBlockEntity) level.getBlockEntity(pasture)).getTetheredPokemon().isEmpty(), "the trainer's Pikachu went into the pasture");
			helper.assertTrue(hall(helper).chronicle().stream().noneMatch(e -> e.text().getString().contains("befriended")), "the chronicle tells of a befriending");
			helper.succeed();
		});
	}

	/**
	 * "where it joins as the hall owner's Pokémon (into their PC first, as Cobblemon does)": with the owner away (not on
	 * the server), the befriended Pokémon is theirs, waiting in their PC, the wild one gone, and the chronicle says so.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa1027_owner_away"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa1027_owner_away")
	public void befriendedWhileTheOwnerIsAwayWaitsInTheirPC(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(level.getDayTime() / 24000L * 24000L + 7000L);
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		BlockPos hall = helper.absolutePos(HALL);
		UUID away = UUID.nameUUIDFromBytes(("qa1027-away" + hall).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		hall(helper).setOwner(away, "Alex");
		PokemonEntity eevee = wild(helper, "eevee level=8", new BlockPos(8, 2, 9));
		Villager ranger = make(helper, PokemonRanger.ID, new BlockPos(7, 2, 9));
		helper.runAfterDelay(5, () -> {
			UUID wildId = eevee.getUUID();
			Pokemon pokemon = eevee.getPokemon();
			WildPokemon.Befriended result = PokemonRanger.befriend(level, hall, ranger, eevee, pasture);
			helper.assertTrue(result == WildPokemon.Befriended.IN_PC, "befriended with the owner away: " + result);
			var pc = Cobblemon.INSTANCE.getStorage().getPC(away, level.registryAccess());
			boolean inPc = false;
			for (Pokemon p : pc) {
				if (p.getUuid().equals(pokemon.getUuid())) {
					inPc = true;
				}
			}
			helper.assertTrue(inPc, "the befriended Eevee isn't in the owner's PC");
			helper.assertTrue(away.equals(pokemon.getOwnerUUID()), "the befriended Eevee isn't the owner's: " + pokemon.getOwnerUUID());
			helper.assertTrue(level.getEntity(wildId) == null || !level.getEntity(wildId).isAlive(), "the wild Eevee is still out");
			String line = hall(helper).chronicle().stream().map(e -> e.text().getString()).filter(s -> s.contains("befriended")).findFirst().orElse("");
			helper.assertTrue(line.contains("befriended a wild Eevee; it waits in Alex's PC"), "the chronicle: \"" + line + "\"");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the Professor (29.21)

	/**
	 * "the village's Pasture Blocks" / "every species ever kept in the village's pastures": a Pasture Block outside the
	 * village (beyond the hall's radius) neither counts for the census nor logs its species in the village Pokédex; one
	 * inside does both.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa1027_outside"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa1027_outside")
	public void aPastureOutsideTheVillageIsNotTheVillages(GameTestHelper helper) {
		setUp(helper);
		VillageHalls.RADIUS = 6; // setUp's after() puts the old radius back
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		BlockPos inside = PastureCompatTests.pasture(helper, new BlockPos(13, 2, 17));
		BlockPos outside = PastureCompatTests.pasture(helper, new BlockPos(27, 2, 27));
		PastureCompatTests.pastured(helper, inside, player, "pikachu", Direction.NORTH);
		PastureCompatTests.pastured(helper, outside, player, "squirtle", Direction.NORTH);
		PastureCompatTests.pastured(helper, outside, player, "charmander", Direction.SOUTH);
		helper.runAfterDelay(10, () -> {
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = hall(helper);
			PokemonPastureBlockEntity out = (PokemonPastureBlockEntity) level.getBlockEntity(outside);
			helper.assertTrue(out.getTetheredPokemon().size() == 2, "the control: the outside pasture holds " + out.getTetheredPokemon().size());
			for (PokemonEntity e : level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(4), e -> e.getTethering() != null)) {
				String name = e.getPokemon().getSpecies().getName();
				boolean in = VillageHalls.area(hallPos).contains(e.position());
				helper.assertTrue(name.equalsIgnoreCase("pikachu") == in, name + " stands " + (in ? "in" : "out of") + " the village at " + e.blockPosition());
			}
			PokemonCensus.Count count = PokemonCensus.of(level, hallPos);
			helper.assertTrue(count.pokemon() == 1 && count.species().equals(Set.of("cobblemon:pikachu")),
				"the census counts a pasture outside the village: " + count.pokemon() + " " + count.species());
			make(helper, PokemonProfessor.ID, new BlockPos(14, 2, 13));
			PokemonProfessor.round(level, hallPos, hall);
			helper.assertTrue(hall.pokedex().equals(Set.of("cobblemon:pikachu")), "the village Pokédex logs a pasture outside the village: " + hall.pokedex());
			helper.succeed();
		});
	}

	/**
	 * Evolution Studies, "sells one evolution stone a day for 8 emeralds": a player short by one (799 CobbleDollars) is
	 * told the price, keeps every dollar, gets no stone, and the day's stone isn't used up; another player then buys it;
	 * after that, the same day, nobody gets a second one (not the first player with the money now, nor the buyer).
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1027_stone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1027_stone")
	public void aStoneNobodyCanPayForCostsNothingAndStaysOneADay(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(5, () -> {
			Villager prof = make(helper, PokemonProfessor.ID, new BlockPos(13, 2, 13));
			evolutionStudies(helper);
			helper.assertTrue(PokemonProfessor.sellsStones(prof), "the control: Evolution Studies doesn't sell stones");
			Item stone = PokemonProfessor.stoneOfDay(level);
			helper.assertTrue(stone != null, "no stone of the day");
			ServerPlayer poor = player(helper);
			setBalance(poor, PokemonProfessor.STONE_PRICE * 100L - 1);
			int emeralds = poor.getInventory().countItem(net.minecraft.world.item.Items.EMERALD);
			Component refused = PokemonProfessor.buyStone(poor, prof);
			helper.assertTrue(key(refused).equals("message.aliveworkplace.professor.stone_cant_afford"), "short by one: " + key(refused) + " \"" + refused.getString() + "\"");
			helper.assertTrue(refused.getString().contains("800"), "the price told: \"" + refused.getString() + "\"");
			helper.assertTrue(CobbleDollarsBank.balance(poor) == 799, "a refused buyer lost money: " + CobbleDollarsBank.balance(poor));
			helper.assertTrue(poor.getInventory().countItem(stone) == 0, "a stone given without payment");
			helper.assertTrue(poor.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == emeralds, "emeralds taken");
			helper.assertTrue(!PokemonProfessor.soldToday(prof), "the refused sale used up the day's stone");
			// through the screen too: pressing the stone with 799 changes nothing
			ChoiceMenu menu = CobblemonProfessor.menuForTest(poor, prof);
			helper.assertTrue(menu.icon(CobblemonProfessor.STONE).is(stone), "the stone isn't on the screen");
			menu.press(CobblemonProfessor.STONE, poor);
			helper.assertTrue(CobbleDollarsBank.balance(poor) == 799 && poor.getInventory().countItem(stone) == 0 && !PokemonProfessor.soldToday(prof),
				"pressing the stone with 799: balance " + CobbleDollarsBank.balance(poor) + ", stones " + poor.getInventory().countItem(stone));
			ServerPlayer rich = player(helper);
			setBalance(rich, 2000);
			Component bought = PokemonProfessor.buyStone(rich, prof);
			helper.assertTrue(key(bought).startsWith("message.aliveworkplace.professor.stone_bought"), "another player can't buy the day's stone: " + bought.getString());
			helper.assertTrue(rich.getInventory().countItem(stone) == 1 && CobbleDollarsBank.balance(rich) == 1200, "the buyer: stones "
				+ rich.getInventory().countItem(stone) + ", balance " + CobbleDollarsBank.balance(rich));
			setBalance(poor, 5000);
			Component second = PokemonProfessor.buyStone(poor, prof);
			helper.assertTrue(key(second).equals("message.aliveworkplace.professor.stone_sold"), "a second stone today for another player: " + second.getString());
			helper.assertTrue(CobbleDollarsBank.balance(poor) == 5000 && poor.getInventory().countItem(stone) == 0, "the refused second sale took money or gave a stone");
			helper.assertTrue(second.getString().equals(prof.getDisplayName().getString() + " has sold today's stone already: come back tomorrow"), "said: " + second.getString());
			helper.succeed();
		});
	}

	/**
	 * "right-click them and pick one of your party": two players with different parties each see only their own Pokémon on
	 * the Professor's screen, never the other's.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1027_parties"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1027_parties")
	public void theHintsOfferOnlyTheClickingPlayersParty(GameTestHelper helper) {
		setUp(helper);
		helper.runAfterDelay(5, () -> {
			Villager prof = make(helper, PokemonProfessor.ID, new BlockPos(13, 2, 13));
			ServerPlayer alex = player(helper);
			ServerPlayer sam = player(helper);
			Cobblemon.INSTANCE.getStorage().getParty(alex).add(PokemonProperties.Companion.parse("eevee level=20", " ", "=").create());
			Cobblemon.INSTANCE.getStorage().getParty(sam).add(PokemonProperties.Companion.parse("pikachu level=7", " ", "=").create());
			Cobblemon.INSTANCE.getStorage().getParty(sam).add(PokemonProperties.Companion.parse("charmander level=9", " ", "=").create());
			ChoiceMenu forAlex = CobblemonProfessor.menuForTest(alex, prof);
			String a0 = forAlex.icon(CobblemonProfessor.FIRST_PARTY_SLOT).getHoverName().getString();
			helper.assertTrue(a0.contains("Eevee"), "Alex's first slot: " + a0);
			helper.assertTrue(forAlex.icon(CobblemonProfessor.FIRST_PARTY_SLOT + 1).isEmpty(), "Alex sees a second Pokémon: "
				+ forAlex.icon(CobblemonProfessor.FIRST_PARTY_SLOT + 1).getHoverName().getString());
			ChoiceMenu forSam = CobblemonProfessor.menuForTest(sam, prof);
			String s0 = forSam.icon(CobblemonProfessor.FIRST_PARTY_SLOT).getHoverName().getString();
			String s1 = forSam.icon(CobblemonProfessor.FIRST_PARTY_SLOT + 1).getHoverName().getString();
			helper.assertTrue(s0.contains("Pikachu") && s1.contains("Charmander"), "Sam's party: " + s0 + ", " + s1);
			helper.assertTrue(forSam.icon(CobblemonProfessor.FIRST_PARTY_SLOT + 2).isEmpty(), "Sam sees a third Pokémon: "
				+ forSam.icon(CobblemonProfessor.FIRST_PARTY_SLOT + 2).getHoverName().getString());
			for (int i = 0; i < 6; i++) {
				String n = forAlex.icon(CobblemonProfessor.FIRST_PARTY_SLOT + i).getHoverName().getString();
				helper.assertTrue(!n.contains("Pikachu") && !n.contains("Charmander"), "Alex is offered Sam's Pokémon: " + n);
			}
			helper.succeed();
		});
	}
}
