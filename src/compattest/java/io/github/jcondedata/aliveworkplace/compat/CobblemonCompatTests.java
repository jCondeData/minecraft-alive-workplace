package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.nurse.Nurses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/** Alive Workplace with the real Cobblemon installed. */
public class CobblemonCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** A nurse heals the player's Pokémon too. */
	@GameTest(template = AREA)
	public void nurseHealsTheParty(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos station = new BlockPos(2, 1, 2);
		helper.setBlock(station, ModBlocks.NURSE_STATION);
		Villager nurse = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(station), ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		Pokemon pokemon = PokemonProperties.Companion.parse("pikachu level=20", " ", "=").create();
		party.add(pokemon);
		pokemon.setCurrentHealth(1);
		helper.assertFalse(pokemon.isFullHealth(), "setup: the Pokémon should be hurt");

		Nurses.resetCooldowns();
		Nurses.treat(player, nurse);
		helper.assertTrue(pokemon.isFullHealth(), "the Pokémon was not healed (" + pokemon.getCurrentHealth() + "/" + pokemon.getMaxHealth() + ")");
		helper.succeed();
	}

	/** Trainer teams: the same for the same trainer and tier, bigger and stronger as the tier goes up, no legendaries. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void trainerTeamsScaleWithTier(GameTestHelper helper) {
		java.util.UUID id = java.util.UUID.randomUUID();
		java.util.List<Pokemon> novice = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.team(id, 1);
		java.util.List<Pokemon> master = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.team(id, 5);
		helper.assertTrue(novice.size() == 2 && master.size() == 6, "team sizes " + novice.size() + " / " + master.size());
		helper.assertTrue(novice.stream().allMatch(p -> p.getLevel() >= 5 && p.getLevel() <= 12), "novice levels");
		helper.assertTrue(master.stream().allMatch(p -> p.getLevel() >= 80), "master levels");
		helper.assertTrue(master.stream().noneMatch(p -> p.getSpecies().getLabels().contains("legendary")), "a legendary in a trainer's team");
		java.util.List<String> again = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.team(id, 5).stream()
			.map(p -> p.getSpecies().getName()).toList();
		helper.assertTrue(again.equals(master.stream().map(p -> p.getSpecies().getName()).toList()), "the team changed between battles");
		helper.succeed();
	}

	/** A player challenges a trainer: a real battle starts; winning pays and both sides learn from it. */
	@GameTest(template = AREA, timeoutTicks = 400)
	public void trainerBattleStartsAndPays(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(2, 1, 2);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		party.add(PokemonProperties.Companion.parse("charmander level=30", " ", "=").create());

		io.github.jcondedata.aliveworkplace.trainer.Trainers.challenge(player, trainer);
		var battle = com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player);
		helper.assertTrue(battle != null, "no battle started");
		int xpBefore = trainer.getVillagerXp();
		io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.finishForTest(battle, true);
		helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == 1, "no prize (1 emerald without CobbleDollars)");
		helper.assertTrue(trainer.getVillagerXp() > xpBefore, "the trainer got no XP from the battle");
		helper.assertTrue(trainer.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.TRAINER_BATTLES, 0) == 1, "battle not counted");
		helper.succeed();
	}

	private static Villager tutor(GameTestHelper helper, int level) {
		helper.setDayTime(2000);
		BlockPos desk = new BlockPos(2, 1, 2);
		helper.setBlock(desk, ModBlocks.TUTORS_DESK);
		Villager tutor = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
		Jobs.employ(helper.getLevel(), tutor, helper.absolutePos(desk), ModVillagers.TUTORS_DESK_POI, ModVillagers.TUTOR);
		tutor.setVillagerData(tutor.getVillagerData().setLevel(level));
		return tutor;
	}

	/** A Master tutor teaches a move for emeralds: two clicks on the lesson, and the Pokémon can use it. */
	@GameTest(template = AREA)
	public void tutorTeachesAMove(GameTestHelper helper) {
		Villager tutor = tutor(helper, 5);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 64));
		Pokemon pokemon = PokemonProperties.Companion.parse("bulbasaur level=20", " ", "=").create();
		Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);

		var lessons = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTutors.lessons(pokemon);
		helper.assertTrue(!lessons.isEmpty(), "Bulbasaur has nothing to learn from a tutor");
		int index = Math.min(lessons.size() - 1, 26); // the hardest lesson on the first page: a Master can teach it
		var lesson = lessons.get(index);
		var menu = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTutors.menuForTest(player, tutor);
		int slot = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTutors.FIRST_MOVE_SLOT + index;
		helper.assertTrue(menu.icon(0).getItem() instanceof com.cobblemon.mod.common.item.PokemonItem, "the party isn't shown");
		menu.press(slot, player);
		helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == 64, "the first click already paid");
		menu.press(slot, player);
		String name = lesson.move().getName();
		boolean knows = pokemon.getAllAccessibleMoves().stream().anyMatch(m -> m.getName().equals(name))
			|| pokemon.getMoveSet().getMoves().stream().anyMatch(m -> m.getName().equals(name));
		helper.assertTrue(knows, "the Pokémon didn't learn " + name);
		helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == 64 - lesson.price(),
			"paid " + (64 - player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD)) + ", expected " + lesson.price());
		helper.assertTrue(tutor.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.TUTOR_LESSONS, 0) == 1, "lesson not counted");
		String title = io.github.jcondedata.aliveworkplace.tutor.Tutors.title(tutor).getString();
		helper.assertTrue(title.equals("Master Move Tutor"), "title: " + title);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTutors.lessons(pokemon).stream()
			.noneMatch(l -> l.move().getName().equals(name)), "the lesson is still offered");
		helper.succeed();
	}

	/** A Novice tutor only teaches easy moves, and nobody gets a lesson they can't pay for. */
	@GameTest(template = AREA)
	public void noviceTutorsTeachEasyMovesForPayment(GameTestHelper helper) {
		Villager tutor = tutor(helper, 1);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 64));
		Pokemon pokemon = PokemonProperties.Companion.parse("charmander level=20", " ", "=").create();
		Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
		var lessons = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTutors.lessons(pokemon);
		var menu = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTutors.menuForTest(player, tutor);
		int hard = -1;
		for (int i = 0; i < Math.min(27, lessons.size()); i++) {
			if (lessons.get(i).grade() > 1) {
				hard = i;
				break;
			}
		}
		helper.assertTrue(hard >= 0, "Charmander has no hard lessons on the first page");
		int slot = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTutors.FIRST_MOVE_SLOT + hard;
		helper.assertTrue(menu.icon(slot).is(net.minecraft.world.item.Items.PAPER), "a lesson beyond the tutor should be greyed out");
		menu.press(slot, player);
		menu.press(slot, player);
		helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == 64, "a Novice gave a hard lesson");

		if (lessons.get(0).grade() == 1) {
			player.getInventory().clearContent();
			int easy = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTutors.FIRST_MOVE_SLOT;
			menu.press(easy, player);
			menu.press(easy, player);
			helper.assertTrue(tutor.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.TUTOR_LESSONS, 0) == 0,
				"a lesson without payment");
		}
		helper.assertTrue(io.github.jcondedata.aliveworkplace.tutor.Tutors.grade(40, false) == 1
			&& io.github.jcondedata.aliveworkplace.tutor.Tutors.grade(90, false) == 4
			&& io.github.jcondedata.aliveworkplace.tutor.Tutors.grade(120, true) == 5
			&& io.github.jcondedata.aliveworkplace.tutor.Tutors.grade(0, false) == 3, "grades");
		helper.succeed();
	}

	/** A trader's offers: the same all day, different on other days, more and stronger at higher tiers. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void traderOffersChangeDaily(GameTestHelper helper) {
		java.util.UUID id = java.util.UUID.randomUUID();
		var novice = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders.offers(id, 1, 7);
		helper.assertTrue(novice.equals(io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders.offers(id, 1, 7)), "offers changed within a day");
		var master = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders.offers(id, 5, 7);
		helper.assertTrue(novice.size() == 1 && master.size() == 3, "offer counts " + novice.size() + " / " + master.size());
		helper.assertTrue(novice.stream().allMatch(o -> o.level() >= 5 && o.level() <= 15), "novice levels");
		helper.assertTrue(master.stream().allMatch(o -> o.level() >= 50 && o.level() <= 70), "master levels");
		java.util.Set<String> species = new java.util.HashSet<>();
		for (long day = 0; day < 10; day++) {
			for (var offer : io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders.offers(id, 3, day)) {
				species.add(offer.species().getName());
				for (var type : offer.species().getTypes()) {
					helper.assertTrue(!type.getName().equals(offer.wanted().getName()), "a trader wants the type they give away");
				}
				helper.assertTrue(offer.minLevel() <= offer.level() && offer.minLevel() >= 5, "wanted level " + offer.minLevel());
			}
		}
		helper.assertTrue(species.size() >= 5, "offers barely change between days: " + species);
		helper.succeed();
	}

	/** A fitting Pokémon is swapped for the trader's; one that doesn't fit stays; one trade a day. */
	@GameTest(template = AREA)
	public void traderSwapsPokemonOnceADay(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos board = new BlockPos(2, 1, 2);
		helper.setBlock(board, ModBlocks.TRADE_BOARD);
		Villager trader = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
		Jobs.employ(helper.getLevel(), trader, helper.absolutePos(board), ModVillagers.TRADE_BOARD_POI, ModVillagers.POKEMON_TRADER);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

		var offer = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders.offers(trader).get(0);
		String wanted = offer.wanted().getName();
		java.util.List<com.cobblemon.mod.common.pokemon.Species> all = new java.util.ArrayList<>(com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getImplemented());
		all.sort(java.util.Comparator.comparing(com.cobblemon.mod.common.pokemon.Species::getName));
		java.util.function.Predicate<com.cobblemon.mod.common.pokemon.Species> fits = s -> {
			for (var type : s.getTypes()) {
				if (type.getName().equals(wanted)) {
					return true;
				}
			}
			return false;
		};
		Pokemon wrong = all.stream().filter(fits.negate()).findFirst().orElseThrow().create(offer.minLevel() + 5);
		Pokemon mine = all.stream().filter(fits).findFirst().orElseThrow().create(offer.minLevel() + 5);
		party.add(wrong);
		party.add(mine);

		var menu = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders.menuForTest(player, trader);
		int first = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders.FIRST_PARTY_SLOT;
		menu.press(first, player);
		menu.press(first, player);
		helper.assertTrue(contains(party, wrong), "a Pokémon of the wrong type was traded away");
		menu.press(first + 1, player);
		helper.assertTrue(contains(party, mine), "the first click already traded");
		menu.press(first + 1, player);
		helper.assertFalse(contains(party, mine), "the fitting Pokémon is still in the party");
		boolean got = false;
		for (Pokemon p : party) {
			got |= p.getSpecies() == offer.species() && p.getLevel() == offer.level();
		}
		helper.assertTrue(got, "the trader's " + offer.species().getName() + " didn't arrive");
		helper.assertTrue(trader.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.POKEMON_TRADE_COUNT, 0) == 1, "trade not counted");

		Pokemon another = all.stream().filter(fits).findFirst().orElseThrow().create(offer.minLevel() + 5);
		party.add(another);
		helper.assertFalse(io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders.trade(player, trader, another, offer),
			"a second trade the same day");
		helper.assertTrue(contains(party, another), "the second Pokémon left the party");
		helper.succeed();
	}

	private static boolean contains(PlayerPartyStore party, Pokemon pokemon) {
		for (Pokemon p : party) {
			if (p == pokemon) {
				return true;
			}
		}
		return false;
	}

	/** A Trainer Leader starts at Expert strength, pays three times the prize, and takes one challenge a day per player. */
	@GameTest(template = AREA, timeoutTicks = 400)
	public void leaderStartsStrongAndPaysMore(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos podium = new BlockPos(2, 1, 2);
		helper.setBlock(podium, ModBlocks.LEADERS_PODIUM);
		Villager leader = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
		Jobs.employ(helper.getLevel(), leader, helper.absolutePos(podium), ModVillagers.LEADERS_PODIUM_POI, ModVillagers.TRAINER_LEADER);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.trainer.Trainers.tier(leader) == 4, "a new leader should battle at Expert strength");

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Cobblemon.INSTANCE.getStorage().getParty(player).add(PokemonProperties.Companion.parse("squirtle level=60", " ", "=").create());
		io.github.jcondedata.aliveworkplace.trainer.Trainers.challenge(player, leader);
		var battle = com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player);
		helper.assertTrue(battle != null, "no battle started");
		io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.finishForTest(battle, true);
		helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == 24, "expert prize x3 = 24 emeralds, got "
			+ player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD));
		io.github.jcondedata.aliveworkplace.trainer.Trainers.challenge(player, leader);
		helper.assertTrue(com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player) == null, "a second challenge the same day");
		helper.succeed();
	}
}
