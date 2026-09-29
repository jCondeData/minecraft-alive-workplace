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
	/** With Cobblemon, explorers find apricorns, berries, Poké Balls, evolution stones and fossils too. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void explorersFindCobblemonThings(GameTestHelper helper) {
		var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(io.github.jcondedata.aliveworkplace.explore.Explorers.COBBLEMON);
		helper.assertTrue(table != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "the Cobblemon finds table didn't load");
		Villager explorer = helper.spawn(EntityType.VILLAGER, new BlockPos(0, 1, 0));
		java.util.Set<String> found = new java.util.HashSet<>();
		for (int i = 0; i < 200; i++) {
			for (var stack : io.github.jcondedata.aliveworkplace.explore.Explorers.finds(helper.getLevel(), explorer, helper.absolutePos(new BlockPos(0, 1, 0)), false)) {
				var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
				if (id.getNamespace().equals("cobblemon")) {
					found.add(id.getPath());
				}
			}
		}
		helper.assertTrue(found.size() >= 5, "only found " + found);
		explorer.discard();
		helper.succeed();
	}

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

	/** With Radical Cobblemon Trainers' level caps: a Master's team comes down to just over the cap; small teams stay as they are. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void trainerTeamsMatchALevelCap(GameTestHelper helper) {
		java.util.UUID id = java.util.UUID.randomUUID();
		java.util.List<Pokemon> master = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.team(id, 5);
		java.util.List<Pokemon> capped = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.scaleToCap(master, 5, 20);
		helper.assertTrue(capped.size() == master.size(), "the team lost members");
		helper.assertTrue(capped.stream().allMatch(p -> p.getLevel() <= 25 && p.getLevel() >= 23), "capped levels: "
			+ capped.stream().map(Pokemon::getLevel).toList());
		for (int i = 0; i < master.size(); i++) {
			helper.assertTrue(capped.get(i).getSpecies() == master.get(i).getSpecies(), "a capped team changed species");
		}
		java.util.List<Pokemon> novice = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.team(id, 1);
		java.util.List<Pokemon> same = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.scaleToCap(novice, 1, 50);
		helper.assertTrue(same.equals(novice), "a Novice team below the cap was changed");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.trainer.Trainers.capCeiling(1, 20) == 14
			&& io.github.jcondedata.aliveworkplace.trainer.Trainers.capCeiling(5, 20) == 25, "ceilings");
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
		helper.assertTrue(io.github.jcondedata.aliveworkplace.work.Money.cobbleDollars(), "CobbleDollars is installed here");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player) == 100, "a Novice's prize is 100 CobbleDollars, got " + io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player));
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

	/** A Master tutor teaches a move for CobbleDollars: two clicks on the lesson, and the Pokémon can use it. */
	@GameTest(template = AREA)
	public void tutorTeachesAMove(GameTestHelper helper) {
		Villager tutor = tutor(helper, 5);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.add(player, 10_000);
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
		helper.assertTrue(io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player) == 10_000, "the first click already paid");
		menu.press(slot, player);
		String name = lesson.move().getName();
		boolean knows = pokemon.getAllAccessibleMoves().stream().anyMatch(m -> m.getName().equals(name))
			|| pokemon.getMoveSet().getMoves().stream().anyMatch(m -> m.getName().equals(name));
		helper.assertTrue(knows, "the Pokémon didn't learn " + name);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player) == 10_000 - lesson.dollars(),
			"paid " + (10_000 - io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player)) + ", expected " + lesson.dollars());
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
		io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.add(player, 10_000);
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
		helper.assertTrue(io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player) == 10_000, "a Novice gave a hard lesson");

		if (lessons.get(0).grade() == 1) {
			io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.take(player, io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player));
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
		helper.assertTrue(novice.size() == 1 && master.size() == 4, "offer counts (a Master adds a special request) " + novice.size() + " / " + master.size());
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
		helper.assertTrue(io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player) == 3000, "expert prize x3 = 3000 CobbleDollars, got " + io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player));
		io.github.jcondedata.aliveworkplace.trainer.Trainers.challenge(player, leader);
		helper.assertTrue(com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player) == null, "a second challenge the same day");
		helper.succeed();
	}

	/** An orchard with Cobblemon: berries and an apricorn seed from the chest go into the ground as plants. */
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "cobblemon_orchard_planting")
	public void orchardKeeperPlantsApricornsAndBerries(GameTestHelper helper) {
		var level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos basket = new BlockPos(2, 2, 2); // y = 1 is this area's floor
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(basket, ModBlocks.FRUIT_BASKET);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
		// Berries grow on farmland (the column by the water), apricorns on grass.
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(12, 1, 12))) {
			helper.setBlock(p, p.getX() == 8 ? net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7)
				: net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
		}
		helper.setBlock(new BlockPos(7, 1, 10), net.minecraft.world.level.block.Blocks.WATER);
		net.minecraft.world.Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new net.minecraft.world.item.ItemStack(com.cobblemon.mod.common.CobblemonItems.ORAN_BERRY, 2));
		chest.setItem(1, new net.minecraft.world.item.ItemStack(com.cobblemon.mod.common.CobblemonItems.RED_APRICORN_SEED, 1));
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, keeper, helper.absolutePos(basket), ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER);
		io.github.jcondedata.aliveworkplace.orchard.Orchards.start(keeper,
			net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(helper.absolutePos(new BlockPos(8, 1, 8)), helper.absolutePos(new BlockPos(12, 1, 12))));
		helper.succeedWhen(() -> {
			int berries = 0;
			int saplings = 0;
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 2, 8), new BlockPos(12, 2, 12))) {
				var block = helper.getBlockState(p).getBlock();
				if (block instanceof com.cobblemon.mod.common.block.BerryBlock) {
					berries++;
				} else if (block instanceof com.cobblemon.mod.common.block.ApricornSaplingBlock) {
					saplings++;
				}
			}
			helper.assertTrue(berries == 2 && saplings == 1, berries + " berry plants and " + saplings + " apricorn saplings");
			helper.assertTrue(chest.countItem(com.cobblemon.mod.common.CobblemonItems.ORAN_BERRY) == 0
				&& chest.countItem(com.cobblemon.mod.common.CobblemonItems.RED_APRICORN_SEED) == 0, "seeds left in the chest");
		});
	}

	/** With a hoe in the chest, an Orchard Keeper tills grass into farmland for berries. */
	@GameTest(template = AREA, timeoutTicks = 1600)
	public void orchardKeeperTillsGrassForBerries(GameTestHelper helper) {
		var level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos basket = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(basket, ModBlocks.FRUIT_BASKET);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(10, 1, 10))) {
			helper.setBlock(p, net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
		}
		net.minecraft.world.Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new net.minecraft.world.item.ItemStack(com.cobblemon.mod.common.CobblemonItems.ORAN_BERRY, 2));
		chest.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_HOE));
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, keeper, helper.absolutePos(basket), ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER);
		io.github.jcondedata.aliveworkplace.orchard.Orchards.start(keeper,
			net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(helper.absolutePos(new BlockPos(8, 1, 8)), helper.absolutePos(new BlockPos(10, 1, 10))));
		helper.succeedWhen(() -> {
			int berries = 0;
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 2, 8), new BlockPos(10, 2, 10))) {
				if (helper.getBlockState(p).getBlock() instanceof com.cobblemon.mod.common.block.BerryBlock) {
					berries++;
					helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.FARMLAND, p.below());
				}
			}
			helper.assertTrue(berries == 2, berries + " berry plants");
			helper.assertTrue(keeper.getMainHandItem().is(net.minecraft.world.item.Items.WOODEN_HOE), "the keeper should hold the hoe");
		});
	}

	/** An Orchard Keeper picks a ripe apricorn and a ripe berry plant; both stay to grow again. */
	@GameTest(template = AREA, timeoutTicks = 1600)
	public void orchardKeeperPicksApricornsAndBerries(GameTestHelper helper) {
		var level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos basket = new BlockPos(2, 2, 2); // y = 1 is this area's floor
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(basket, ModBlocks.FRUIT_BASKET);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);

		// An apricorn hanging off apricorn leaves.
		BlockPos leaves = new BlockPos(10, 4, 10);
		var leafState = com.cobblemon.mod.common.CobblemonBlocks.APRICORN_LEAVES.defaultBlockState();
		if (leafState.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) {
			leafState = leafState.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
		}
		helper.setBlock(leaves, leafState);
		BlockPos apricorn = new BlockPos(11, 4, 10);
		var apricornAge = com.cobblemon.mod.common.block.ApricornBlock.Companion.getAGE();
		helper.setBlock(apricorn, com.cobblemon.mod.common.CobblemonBlocks.RED_APRICORN.defaultBlockState()
			.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.WEST)
			.setValue(apricornAge, com.cobblemon.mod.common.block.ApricornBlock.MAX_AGE));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.orchard.Fruit.isRipe(helper.getBlockState(apricorn)), "a grown apricorn should be ripe");
		helper.assertTrue(!io.github.jcondedata.aliveworkplace.orchard.Fruit.isRipe(helper.getBlockState(apricorn).setValue(apricornAge, 1)), "a small apricorn is not ripe");

		// A berry plant in fruit, on farmland.
		BlockPos berry = new BlockPos(6, 2, 11);
		helper.setBlock(berry.below(), net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
		var berryAge = com.cobblemon.mod.common.block.BerryBlock.Companion.getAGE();
		helper.setBlock(berry, com.cobblemon.mod.common.CobblemonBlocks.ORAN_BERRY.defaultBlockState().setValue(berryAge, com.cobblemon.mod.common.block.BerryBlock.FRUIT_AGE));
		if (level.getBlockEntity(helper.absolutePos(berry)) instanceof com.cobblemon.mod.common.block.entity.BerryBlockEntity plant) {
			plant.generateSimpleYields();
		} else {
			helper.fail("the berry plant has no block entity");
		}
		helper.assertTrue(io.github.jcondedata.aliveworkplace.orchard.Fruit.isRipe(helper.getBlockState(berry)), "a berry plant in fruit should be ripe");

		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, keeper, helper.absolutePos(basket), ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER);
		helper.succeedWhen(() -> {
			net.minecraft.world.Container chest = helper.getBlockEntity(chestPos);
			var red = com.cobblemon.mod.common.CobblemonItems.RED_APRICORN;
			var oran = com.cobblemon.mod.common.CobblemonItems.ORAN_BERRY;
			helper.assertTrue(chest.countItem(red) >= 1, "no apricorn in the chest");
			helper.assertTrue(chest.countItem(oran) >= 1, "no oran berries in the chest");
			helper.assertBlockPresent(com.cobblemon.mod.common.CobblemonBlocks.RED_APRICORN, apricorn);
			helper.assertTrue(helper.getBlockState(apricorn).getValue(apricornAge) < com.cobblemon.mod.common.block.ApricornBlock.MAX_AGE, "the apricorn should start again");
			helper.assertBlockPresent(com.cobblemon.mod.common.CobblemonBlocks.ORAN_BERRY, berry);
			helper.assertTrue(helper.getBlockState(berry).getValue(berryAge) < com.cobblemon.mod.common.block.BerryBlock.FRUIT_AGE, "the berry plant should grow again");
		});
	}

	/** With CobbleDollars, the shop screen sells emerald-priced goods for CobbleDollars (100 each) paid to the owner. */
	@GameTest(template = AREA)
	public void shopSellsForCobbleDollars(GameTestHelper helper) {
		var level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos counterPos = new BlockPos(2, 1, 2);
		BlockPos chestPos = new BlockPos(2, 1, 4);
		helper.setBlock(counterPos, ModBlocks.SHOP_COUNTER);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
		var counter = (io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity) helper.getBlockEntity(counterPos);
		int columns = io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity.COLUMNS;
		counter.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 16));
		counter.setItem(columns, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 2));
		counter.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 1)); // none in stock
		counter.setItem(columns + 1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 1));
		counter.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 8));
		counter.setItem(columns + 2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 1)); // items only
		net.minecraft.world.Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 40));
		chest.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 16));

		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		counter.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
		Jobs.employ(level, keeper, helper.absolutePos(counterPos), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		ServerPlayer buyer = helper.makeMockServerPlayerInLevel();
		buyer.setGameMode(GameType.SURVIVAL);
		io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.add(buyer, 500);
		long ownerBefore = io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(owner);

		int first = io.github.jcondedata.aliveworkplace.shop.Shops.FIRST_GOODS_SLOT;
		var menu = io.github.jcondedata.aliveworkplace.shop.Shops.menuForTest(buyer, keeper);
		helper.assertTrue(menu.icon(first).is(net.minecraft.world.item.Items.COBBLESTONE), "cobblestone should be on sale");
		helper.assertTrue(menu.icon(first + 1).isEmpty() || !menu.icon(first + 1).is(net.minecraft.world.item.Items.BREAD), "bread is out of stock");
		helper.assertTrue(menu.icon(first + 2).is(net.minecraft.world.item.Items.OAK_LOG), "logs should be on sale");
		menu.press(first, buyer);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(buyer) == 500, "one click only chooses");
		menu.press(first, buyer);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(buyer) == 300,
			"buyer has " + io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(buyer));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(owner) == ownerBefore + 200, "the owner was not paid");
		helper.assertTrue(buyer.getInventory().countItem(net.minecraft.world.item.Items.COBBLESTONE) == 16, "the buyer didn't get the goods");
		helper.assertTrue(chest.countItem(net.minecraft.world.item.Items.COBBLESTONE) == 24, chest.countItem(net.minecraft.world.item.Items.COBBLESTONE) + " left in stock");
		helper.assertTrue(counter.sales().size() == 1 && counter.sales().get(0).dollars() == 200, "sales log: " + counter.sales());

		// A Price Tag renamed "150" charges exactly 150 CobbleDollars.
		net.minecraft.world.item.ItemStack tag = new net.minecraft.world.item.ItemStack(io.github.jcondedata.aliveworkplace.registry.ModItems.PRICE_TAG);
		tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("150"));
		counter.setItem(columns + 2, tag);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.shop.Shops.buy(buyer, keeper, 2), "the price-tag sale didn't go through");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(buyer) == 150,
			"after the tag sale the buyer has " + io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(buyer));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(owner) == ownerBefore + 350, "the owner wasn't paid for the tag sale");
		counter.setItem(columns + 2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 1));
		helper.assertTrue(chest.countItem(net.minecraft.world.item.Items.OAK_LOG) == 8, "the tag sale should have taken 8 logs");
		chest.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 16));
		buyer.getInventory().clearOrCountMatchingItems(st -> st.is(net.minecraft.world.item.Items.OAK_LOG), 8, buyer.inventoryMenu.getCraftSlots());
		io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.add(buyer, 150); // back to 300 for the rest

		// A diamond price is paid in diamonds, into the chests.
		buyer.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
		menu.press(first + 2, buyer);
		menu.press(first + 2, buyer);
		helper.assertTrue(buyer.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND) == 0
			&& buyer.getInventory().countItem(net.minecraft.world.item.Items.OAK_LOG) == 8, "the log sale went wrong");
		helper.assertTrue(chest.countItem(net.minecraft.world.item.Items.DIAMOND) == 1, "the diamond should be in the shop's chest");

		// An owner who is offline gets paid when they come back.
		java.util.UUID away = java.util.UUID.randomUUID();
		counter.setOwner(away, "Away");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.shop.Shops.buy(buyer, keeper, 0), "second cobblestone sale failed");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.shop.ShopLedger.get(level.getServer()).pending(away) == 200, "takings for an offline owner not kept");
		helper.succeed();
	}

	/** With CobbleDollars, a ferryman's screen sells tickets for CobbleDollars (the emerald fare × 100). */
	@GameTest(template = AREA)
	public void ferrymanSellsTicketsForCobbleDollars(GameTestHelper helper) {
		helper.setDayTime(2000);
		var dim = helper.getLevel().dimension();
		var network = io.github.jcondedata.aliveworkplace.travel.TravelNetwork.get(helper.getLevel().getServer());
		BlockPos home = new BlockPos(2, 1, 2);
		BlockPos away = new BlockPos(14, 1, 14);
		helper.setBlock(home, ModBlocks.TRAVEL_POST);
		helper.setBlock(away, ModBlocks.TRAVEL_POST);
		network.add(net.minecraft.core.GlobalPos.of(dim, helper.absolutePos(home)), "Harbour");
		var awayPost = network.add(net.minecraft.core.GlobalPos.of(dim, helper.absolutePos(away)), "Lighthouse");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		network.visit(player.getUUID(), awayPost);
		io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.add(player, 250);
		Villager ferryman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
		Jobs.employ(helper.getLevel(), ferryman, helper.absolutePos(home), ModVillagers.TRAVEL_POST_POI, ModVillagers.FERRYMAN);

		int slot = io.github.jcondedata.aliveworkplace.travel.Ferrymen.FIRST_DESTINATION_SLOT;
		var menu = io.github.jcondedata.aliveworkplace.travel.Ferrymen.menuForTest(player, ferryman);
		helper.assertTrue(menu.icon(slot).is(io.github.jcondedata.aliveworkplace.registry.ModItems.TRAVEL_TICKET), "no ticket to the Lighthouse on offer");
		menu.press(slot, player);
		menu.press(slot, player);
		long left = io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player);
		helper.assertTrue(left == 150, "a short trip should cost 100 CobbleDollars, the player has " + left + " left");
		helper.assertTrue(player.getInventory().countItem(io.github.jcondedata.aliveworkplace.registry.ModItems.TRAVEL_TICKET) == 1, "no ticket in the inventory");
		helper.succeed();
	}

	/** A Fossil Scientist takes a Dome Fossil and the fee, works on it at the lab, and a Kabuto joins the player's party. */
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "fossil")
	public void fossilScientistRevivesAFossil(GameTestHelper helper) {
		int usual = io.github.jcondedata.aliveworkplace.fossil.FossilScientists.REVIVE_TICKS;
		io.github.jcondedata.aliveworkplace.fossil.FossilScientists.REVIVE_TICKS = 60;
		helper.setDayTime(2000);
		BlockPos lab = new BlockPos(2, 2, 2);
		helper.setBlock(lab, ModBlocks.FOSSIL_LAB);
		Villager scientist = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), scientist, helper.absolutePos(lab), ModVillagers.FOSSIL_LAB_POI, ModVillagers.FOSSIL_SCIENTIST);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL); // (creative players keep what they hand over)
		io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.add(player, 10_000);
		var dome = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "dome_fossil"));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.fossil.FossilScientists.isFossil(new net.minecraft.world.item.ItemStack(dome)), "a Dome Fossil isn't a fossil");
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(dome, 2));
		io.github.jcondedata.aliveworkplace.fossil.FossilScientists.handOver(player, scientist);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.fossil.FossilScientists.queue(scientist).size() == 1, "the scientist didn't take the fossil");
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "one fossil should be taken, not " + (2 - player.getMainHandItem().getCount()));
		long left = io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player);
		helper.assertTrue(left == 10_000 - 800, "a revival should cost 800 CobbleDollars, " + left + " left");
		helper.succeedWhen(() -> {
			boolean kabuto = false;
			for (com.cobblemon.mod.common.pokemon.Pokemon p : com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player)) {
				kabuto |= p.getSpecies().getName().equalsIgnoreCase("kabuto");
			}
			helper.assertTrue(kabuto, "no Kabuto in the party yet");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.fossil.FossilScientists.queue(scientist).isEmpty(), "the revival is still queued");
			helper.assertTrue(scientist.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.FOSSILS_REVIVED, 0) == 1, "not counted");
			io.github.jcondedata.aliveworkplace.fossil.FossilScientists.REVIVE_TICKS = usual;
		});
	}

	/** With Cobblemon a chef also cooks in the Campfire Pot's way: Poké Bait from honey, mushrooms and wheat (the bottles come back). */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void chefCooksPokeBait(GameTestHelper helper) {
		var bait = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_bait"));
		helper.assertTrue(bait != net.minecraft.world.item.Items.AIR, "no cobblemon:poke_bait");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.craft.Chefs.menu(helper.getLevel()).contains(bait), "Poké Bait isn't on the menu");
		helper.setDayTime(2000);
		BlockPos stove = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(stove, ModBlocks.KITCHEN_STOVE);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE, 2));
		chest.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BROWN_MUSHROOM, 2));
		chest.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT, 2));
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(stove), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(bait) == 8, chest.countItem(bait) + " Poké Bait cooked");
			helper.assertTrue(chest.countItem(net.minecraft.world.item.Items.GLASS_BOTTLE) == 2, "the honey bottles didn't come back empty");
			helper.assertTrue(chest.countItem(net.minecraft.world.item.Items.WHEAT) == 0, "wheat left");
		});
	}

	/** A novice Ball Smith turns red apricorns and copper into Poké Balls, and leaves Great Ball makings for later. */
	@GameTest(template = AREA, timeoutTicks = 1400)
	public void ballSmithMakesPokeBalls(GameTestHelper helper) {
		var recipes = io.github.jcondedata.aliveworkplace.smith.BallRecipes.all(helper.getLevel());
		java.util.function.Function<String, Integer> tierOf = id -> recipes.stream()
			.filter(r -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(r.result().getItem()).getPath().equals(id))
			.map(io.github.jcondedata.aliveworkplace.smith.BallRecipes.BallRecipe::tier).findFirst().orElse(-1);
		helper.assertTrue(tierOf.apply("poke_ball") == 1 && tierOf.apply("great_ball") == 2 && tierOf.apply("ultra_ball") == 3,
			"ball tiers: poke " + tierOf.apply("poke_ball") + ", great " + tierOf.apply("great_ball") + ", ultra " + tierOf.apply("ultra_ball"));
		helper.assertTrue(tierOf.apply("master_ball") == -1, "a smith must never make Master Balls");

		helper.setDayTime(2000);
		BlockPos bench = new BlockPos(2, 2, 2); // y = 1 is this area's floor
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(bench, ModBlocks.BALL_WORKBENCH);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new net.minecraft.world.item.ItemStack(com.cobblemon.mod.common.CobblemonItems.RED_APRICORN, 8));
		chest.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 2));
		// Great Ball makings (with the red apricorns): too hard for a novice. Two blue aren't enough for an Azure Ball.
		chest.setItem(2, new net.minecraft.world.item.ItemStack(com.cobblemon.mod.common.CobblemonItems.BLUE_APRICORN, 2));
		chest.setItem(3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 1));
		Villager smith = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), smith, helper.absolutePos(bench), ModVillagers.BALL_WORKBENCH_POI, ModVillagers.BALL_SMITH);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(com.cobblemon.mod.common.CobblemonItems.POKE_BALL) == 8,
				chest.countItem(com.cobblemon.mod.common.CobblemonItems.POKE_BALL) + " Poké Balls made");
			helper.assertTrue(chest.countItem(com.cobblemon.mod.common.CobblemonItems.RED_APRICORN) == 0
				&& chest.countItem(net.minecraft.world.item.Items.COPPER_INGOT) == 0, "the makings weren't used up");
			helper.assertTrue(chest.countItem(com.cobblemon.mod.common.CobblemonItems.BLUE_APRICORN) == 2
				&& chest.countItem(net.minecraft.world.item.Items.IRON_INGOT) == 1, "a novice shouldn't make Great Balls");
			helper.assertTrue(smith.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.BALLS_MADE, 0) == 8, "balls not counted");
		});
	}

	/** Orders: asked only for Azure Balls, the smith leaves the red apricorns alone even though it could make Poké Balls. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void ballSmithTakesOrders(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos bench = new BlockPos(2, 2, 2); // y = 1 is this area's floor
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(bench, ModBlocks.BALL_WORKBENCH);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new net.minecraft.world.item.ItemStack(com.cobblemon.mod.common.CobblemonItems.RED_APRICORN, 8));
		chest.setItem(1, new net.minecraft.world.item.ItemStack(com.cobblemon.mod.common.CobblemonItems.BLUE_APRICORN, 8));
		chest.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 4));
		Villager smith = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), smith, helper.absolutePos(bench), ModVillagers.BALL_WORKBENCH_POI, ModVillagers.BALL_SMITH);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var menu = io.github.jcondedata.aliveworkplace.smith.BallSmiths.ordersMenuForTest(player, smith);
		int azure = -1;
		for (int slot = io.github.jcondedata.aliveworkplace.smith.BallSmiths.FIRST_BALL_SLOT; slot < io.github.jcondedata.aliveworkplace.work.ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).is(com.cobblemon.mod.common.CobblemonItems.AZURE_BALL)) {
				azure = slot;
			}
		}
		helper.assertTrue(azure >= 0, "no Azure Ball on the orders screen");
		menu.press(azure, player);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.smith.BallSmiths.orders(smith).size() == 1, "orders: " + io.github.jcondedata.aliveworkplace.smith.BallSmiths.orders(smith));
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(com.cobblemon.mod.common.CobblemonItems.AZURE_BALL) >= 1, "no Azure Balls made");
			helper.assertTrue(chest.countItem(com.cobblemon.mod.common.CobblemonItems.POKE_BALL) == 0
				&& chest.countItem(com.cobblemon.mod.common.CobblemonItems.RED_APRICORN) == 8, "made Poké Balls nobody asked for");
		});
	}

	/** A Farmer's field of Cobblemon mints: ripe ones are picked (leaves to the chest) and planted again. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void farmerHarvestsCobblemonMints(GameTestHelper helper) {
		var level = helper.getLevel();
		helper.setDayTime(2000);
		var mint = com.cobblemon.mod.common.CobblemonBlocks.INSTANCE.getRED_MINT();
		BlockPos water = new BlockPos(9, 1, 9);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(10, 1, 10))) {
			if (p.equals(water)) {
				helper.setBlock(p, net.minecraft.world.level.block.Blocks.WATER);
			} else {
				helper.setBlock(p, net.minecraft.world.level.block.Blocks.FARMLAND);
				helper.setBlock(p.above(), mint.getStateForAge(mint.getMaxAge()));
			}
		}
		BlockPos composter = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(composter, net.minecraft.world.level.block.Blocks.COMPOSTER);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, farmer, helper.absolutePos(composter), net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER,
			net.minecraft.world.entity.npc.VillagerProfession.FARMER);
		io.github.jcondedata.aliveworkplace.farm.Fields.start(level, farmer, net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
			helper.absolutePos(new BlockPos(8, 1, 8)), helper.absolutePos(new BlockPos(10, 1, 10))));
		net.minecraft.world.Container chest = helper.getBlockEntity(chestPos);
		helper.succeedWhen(() -> {
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 2, 8), new BlockPos(10, 2, 10))) {
				if (!p.below().equals(water)) {
					helper.assertBlockPresent(mint, p);
				}
			}
			int leaves = chest.countItem(com.cobblemon.mod.common.CobblemonItems.RED_MINT_LEAF);
			helper.assertTrue(leaves >= 8, "only " + leaves + " mint leaves in the chest");
		});
	}

	/** Expert and Master traders add a special request for one particular Pokémon line; beginners don't. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void expertTradersMakeASpecialRequest(GameTestHelper helper) {
		java.util.UUID id = java.util.UUID.randomUUID();
		var expert = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders.offers(id, 4, 3);
		helper.assertTrue(expert.size() == 4, expert.size() + " offers for an Expert");
		var special = expert.get(3);
		helper.assertTrue(special.wantedSpecies() != null, "no special request");
		helper.assertTrue(special.wantedSpecies().getPreEvolution() == null, "the request should name the first of a line");
		Pokemon fits = special.wantedSpecies().create(special.minLevel());
		helper.assertTrue(special.refusal(fits) == null, "the requested Pokémon was refused: " + special.refusal(fits));
		Pokemon other = PokemonProperties.Companion.parse(special.wantedSpecies().getName().equalsIgnoreCase("magikarp") ? "eevee" : "magikarp",
			" ", "=").create();
		other.setLevel(Math.max(other.getLevel(), special.minLevel()));
		helper.assertTrue(special.refusal(other) != null, "a Pokémon from another line was accepted");
		helper.assertTrue(expert.subList(0, 3).stream().allMatch(o -> o.wantedSpecies() == null), "only the last offer is a special request");
		var novice = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders.offers(id, 1, 3);
		helper.assertTrue(novice.stream().allMatch(o -> o.wantedSpecies() == null), "a Novice made a special request");
		helper.succeed();
	}

	/** Expert and Master trainers bring trained teams: top IVs, EVs, a matching nature and a held item; Novices don't. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void strongTrainersBringTrainedTeams(GameTestHelper helper) {
		java.util.UUID id = java.util.UUID.randomUUID();
		for (Pokemon p : io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.team(id, 5)) {
			helper.assertTrue(p.getIvs().get(com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED) == 31
				&& p.getIvs().get(com.cobblemon.mod.common.api.pokemon.stats.Stats.HP) == 31, p.getSpecies().getName() + " IVs");
			helper.assertTrue(p.getEvs().get(com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED) == 252, p.getSpecies().getName() + " EVs");
			helper.assertTrue(p.getNature() == com.cobblemon.mod.common.api.pokemon.Natures.ADAMANT
				|| p.getNature() == com.cobblemon.mod.common.api.pokemon.Natures.MODEST, p.getSpecies().getName() + " nature " + p.getNature().getName());
			helper.assertTrue(!p.heldItem().isEmpty(), p.getSpecies().getName() + " holds nothing");
			helper.assertTrue(p.getCurrentHealth() == p.getMaxHealth(), p.getSpecies().getName() + " isn't at full health");

		}
		for (Pokemon p : io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.team(id, 1)) {
			helper.assertTrue(p.heldItem().isEmpty(), "a Novice's " + p.getSpecies().getName() + " holds an item");
		}
		// Masters' moves: at least two attacks, whatever the species (a Smeargle, which only ever knows Sketch, aside).
		for (int i = 0; i < 40; i++) {
			for (Pokemon p : io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.team(new java.util.UUID(i, i * 31L), 5)) {
				var moves = p.getMoveSet().getMoves();
				long attacks = moves.stream().filter(m -> m.getTemplate().getPower() > 0).count();
				boolean few = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.attackPool(p, null).size() < 2;
				helper.assertTrue(few || attacks >= 2, p.getSpecies().getName() + " (" + p.getNature().getName() + ") has "
					+ moves.stream().map(m -> m.getName()).toList());
			}
		}
		helper.succeed();
	}

	/** With Cobblemon, blueprints can be built in apricorn wood: every spruce, oak and dark oak block becomes apricorn. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void blueprintsComeInApricornWood(GameTestHelper helper) {
		var styles = io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.all().stream().map(s -> s.name()).toList();
		helper.assertTrue(styles.contains("apricorn"), "styles: " + styles);
		var id = io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.styled(
			io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.STARTER_COTTAGE.id(), "apricorn");
		var blueprint = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(helper.getLevel(), id).orElseThrow();
		java.util.Set<String> ids = blueprint.blocks().stream()
			.map(e -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).toString())
			.collect(java.util.stream.Collectors.toSet());
		helper.assertTrue(ids.contains("cobblemon:apricorn_planks") && ids.contains("cobblemon:apricorn_log") && ids.contains("cobblemon:apricorn_stairs"),
			"apricorn cottage: " + ids);
		helper.assertTrue(ids.stream().noneMatch(i -> i.startsWith("minecraft:spruce_")), "spruce left: " + ids);
		// Every block still has a cost, so builders can build it.
		var plan = io.github.jcondedata.aliveworkplace.build.BuildPlan.create(blueprint,
			new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(BlockPos.ZERO),
				net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE));
		helper.assertTrue(plan.materials().keySet().stream().anyMatch(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString()
			.equals("cobblemon:apricorn_planks")), "materials: " + plan.materials().keySet());
		helper.succeed();
	}

	/** With Cobblemon, sifting gravel can turn up evolution stones (their table loads only with Cobblemon). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void siftingFindsEvolutionStones(GameTestHelper helper) {
		var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
			io.github.jcondedata.aliveworkplace.AliveWorkplace.id("sifting/cobblemon/gravel"));
		helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(key) != net.minecraft.world.level.storage.loot.LootTable.EMPTY,
			"the Cobblemon sifting table didn't load");
		helper.succeed();
	}

	/** With Cobblemon, a netherworker's expedition can turn up Fire and Dusk Stones (the table loads only with Cobblemon). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void netherExpeditionsFindStones(GameTestHelper helper) {
		helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(io.github.jcondedata.aliveworkplace.nether.Netherworkers.COBBLEMON)
			!= net.minecraft.world.level.storage.loot.LootTable.EMPTY, "the Cobblemon nether table didn't load");
		helper.succeed();
	}
}
