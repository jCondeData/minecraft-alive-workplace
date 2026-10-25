package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.berry.BerryBreeders;
import io.github.jcondedata.aliveworkplace.berry.BerryChains;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.nurse.Nurses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Independent QA (lane-d-1025-0709) for ROADMAP 28.7 (the Pokémon Center's nurse), 28.9 (the Berry Breeder) and 28.12
 * (the Daycare Keeper) with the real Cobblemon, written from the specs: the promises and boundaries the builders' own
 * tests leave out.
 */
public class QaPokemonJobsCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static ResourceLocation c(String path) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", path);
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	// --- 28.7 The Pokémon Center ------------------------------------------------------------------

	private static final BlockPos MACHINE = new BlockPos(3, 2, 3);
	private static final BlockPos NURSE_AT = new BlockPos(4, 2, 4);

	private static List<Pokemon> hurtParty(ServerPlayer player, String... species) {
		List<Pokemon> team = new ArrayList<>();
		for (String name : species) {
			Pokemon pokemon = make(name + " level=20");
			Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
			pokemon.setCurrentHealth(1);
			team.add(pokemon);
		}
		return team;
	}

	/**
	 * The machine broken while she works there: a right-click doesn't fail or wait for a machine that isn't there; she
	 * heals the team by hand, as at any station.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaNurseWhoseMachineIsBrokenHealsByHand(GameTestHelper helper) {
		helper.setDayTime(2000);
		helper.setBlock(MACHINE, BuiltInRegistries.BLOCK.get(ModVillagers.HEALING_MACHINE_BLOCK));
		Villager nurse = helper.spawn(EntityType.VILLAGER, NURSE_AT);
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(MACHINE), ModVillagers.HEALING_MACHINE_POI, ModVillagers.NURSE);
		helper.assertTrue(nurse.getVillagerData().getProfession() == ModVillagers.NURSE, "setup: not a nurse");
		ServerPlayer player = player(helper);
		List<Pokemon> team = hurtParty(player, "pikachu", "squirtle");
		helper.setBlock(MACHINE, Blocks.AIR);
		Nurses.resetCooldowns();
		Nurses.treat(player, nurse);
		for (Pokemon pokemon : team) {
			helper.assertTrue(pokemon.isFullHealth(), "with her machine gone, " + pokemon.getSpecies().getName() + " is at "
				+ pokemon.getCurrentHealth() + "/" + pokemon.getMaxHealth());
		}
		helper.succeed();
	}

	/** A nurse at a brewing stand heals as before (by hand, at once), with Cobblemon installed. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaNurseAtABrewingStandHealsAsBefore(GameTestHelper helper) {
		helper.setDayTime(2000);
		helper.setBlock(MACHINE, Blocks.BREWING_STAND);
		Villager nurse = helper.spawn(EntityType.VILLAGER, NURSE_AT);
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(MACHINE), PoiTypes.CLERIC, ModVillagers.NURSE);
		helper.assertTrue(nurse.getVillagerData().getProfession() == ModVillagers.NURSE, "setup: not a nurse");
		helper.assertTrue(Nurses.machine(helper.getLevel(), nurse) == null, "a brewing stand counted as a Healing Machine");
		ServerPlayer player = player(helper);
		List<Pokemon> team = hurtParty(player, "bulbasaur", "charmander", "eevee");
		Nurses.resetCooldowns();
		Nurses.treat(player, nurse);
		for (Pokemon pokemon : team) {
			helper.assertTrue(pokemon.isFullHealth(), pokemon.getSpecies().getName() + " not healed at a brewing stand");
		}
		helper.succeed();
	}

	/**
	 * A team in the machine: every Pokémon of a full party of six is healed by the machine, and the machine stays charged
	 * afterwards while she's on shift (healing doesn't leave it flat).
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa_nurse_full_party"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_nurse_full_party")
	public void qaNurseHealsAFullPartyOfSixInTheMachine(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(3000);
		helper.setBlock(MACHINE, BuiltInRegistries.BLOCK.get(ModVillagers.HEALING_MACHINE_BLOCK));
		Villager nurse = helper.spawn(EntityType.VILLAGER, NURSE_AT);
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(MACHINE), ModVillagers.HEALING_MACHINE_POI, ModVillagers.NURSE);
		ServerPlayer player = player(helper);
		List<Pokemon> team = hurtParty(player, "pikachu", "bulbasaur", "charmander", "squirtle", "eevee", "magikarp");
		Nurses.resetCooldowns();
		Nurses.treat(player, nurse);
		HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
		helper.assertTrue(machine.isInUse(), "six Pokémon didn't go in the machine");
		helper.succeedWhen(() -> {
			for (Pokemon pokemon : team) {
				helper.assertTrue(pokemon.isFullHealth(), pokemon.getSpecies().getName() + " at " + pokemon.getCurrentHealth() + "/" + pokemon.getMaxHealth());
			}
			helper.assertFalse(machine.isInUse(), "the machine is still running");
			helper.assertTrue(machine.getHealingCharge() >= machine.getMaxCharge(), "on shift, the machine is left at " + machine.getHealingCharge()
				+ " of " + machine.getMaxCharge());
		});
	}

	/**
	 * The hall's "What next?": a Cobblemon village of Village rank or more without a Pokémon Center is told to build one;
	 * not a Hamlet, not without Cobblemon, and not once any tier or look of one is built.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheHallSuggestsAPokemonCenterOnlyWhenItShould(GameTestHelper helper) {
		ResourceLocation house = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("cottage");
		helper.assertTrue(VillageAdvice.wantsPokemonCenter(true, VillageRanks.Rank.VILLAGE, List.of(house)), "a Village with only a cottage isn't told");
		helper.assertTrue(VillageAdvice.wantsPokemonCenter(true, VillageRanks.Rank.CITY, List.of()), "a City without one isn't told");
		helper.assertFalse(VillageAdvice.wantsPokemonCenter(true, VillageRanks.Rank.HAMLET, List.of()), "a Hamlet is told already");
		helper.assertFalse(VillageAdvice.wantsPokemonCenter(false, VillageRanks.Rank.TOWN, List.of()), "told without Cobblemon");
		List<StarterBlueprints.Entry> every = new ArrayList<>(StarterBlueprints.POKEMON_CENTER_LOOKS);
		every.addAll(StarterBlueprints.POKEMON_CENTER_2_LOOKS);
		for (StarterBlueprints.Entry built : every) {
			helper.assertFalse(VillageAdvice.wantsPokemonCenter(true, VillageRanks.Rank.TOWN, List.of(house, built.id())),
				"still told with " + built.id() + " built");
		}
		helper.succeed();
	}

	// --- 28.9 The Berry Breeder -------------------------------------------------------------------

	private static final BlockPos COMPOSTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static ResourceLocation b(String name) {
		return c(name + "_berry");
	}

	private static ItemStack berry(String name, int count) {
		return new ItemStack(BuiltInRegistries.ITEM.get(b(name)), count);
	}

	private static Villager breeder(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container container = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			container.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(COMPOSTER), PoiTypes.FARMER, ModVillagers.BERRY_BREEDER);
		helper.assertTrue(BerryBreeders.isBreeder(villager), "setup: not a berry breeder");
		return villager;
	}

	private static ResourceLocation plant(GameTestHelper helper, BlockPos farmland) {
		return BerryBreeders.data().plantOf(helper.getBlockState(farmland.above()));
	}

	/**
	 * Her plot is farmland within 8 blocks of the composter: beds 8 and 9 blocks east (a pair, one half outside) get
	 * nothing for 300 ticks; once a bed 7 blocks east is added, the beds 7 and 8 blocks east get Oran and Cheri side by
	 * side, and the one 9 blocks east stays bare.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_berry_plot_edge"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_berry_plot_edge")
	public void qaBerryPlotReachesEightBlocksButNotNine(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos seven = new BlockPos(9, 1, 6), eight = new BlockPos(10, 1, 6), nine = new BlockPos(11, 1, 6);
		helper.setBlock(eight, Blocks.FARMLAND);
		helper.setBlock(nine, Blocks.FARMLAND);
		Villager breeder = breeder(helper, berry("oran", 8), berry("cheri", 8));
		BerryBreeders.setGoal(breeder, b("lum"));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(plant(helper, eight) == null && plant(helper, nine) == null,
				"planted a pair reaching 9 blocks from the composter: " + plant(helper, eight) + ", " + plant(helper, nine));
			helper.setBlock(seven, Blocks.FARMLAND);
			helper.succeedWhen(() -> {
				helper.assertTrue(plant(helper, nine) == null, "planted 9 blocks from the composter: " + plant(helper, nine));
				ResourceLocation a = plant(helper, seven), d = plant(helper, eight);
				helper.assertTrue(a != null && d != null && Set.of(a, d).equals(Set.of(b("oran"), b("cheri"))),
					"the beds 7 and 8 blocks away aren't planted side by side: " + a + ", " + d);
			});
		});
	}

	/**
	 * A Field Marker's area is her plot instead: with only farmland by the composter (outside the marked area) she plants
	 * nothing for 300 ticks; farmland in the marked area is then planted, and the ground by the composter left alone.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_berry_field_marker"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_berry_field_marker")
	public void qaBerryFieldMarkerPlotReplacesTheComposterGround(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos nearA = new BlockPos(4, 1, 2), nearB = new BlockPos(5, 1, 2);
		BlockPos markedA = new BlockPos(11, 1, 12), markedB = new BlockPos(12, 1, 12);
		helper.setBlock(nearA, Blocks.FARMLAND);
		helper.setBlock(nearB, Blocks.FARMLAND);
		Villager breeder = breeder(helper, berry("oran", 8), berry("cheri", 8));
		BlockPos lo = helper.absolutePos(new BlockPos(10, 1, 11)), hi = helper.absolutePos(new BlockPos(13, 2, 13));
		BerryBreeders.start(breeder, BoundingBox.fromCorners(lo, hi));
		BerryBreeders.setGoal(breeder, b("lum"));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(plant(helper, nearA) == null && plant(helper, nearB) == null, "planted by the composter, outside her marked plot");
			helper.setBlock(markedA, Blocks.FARMLAND);
			helper.setBlock(markedB, Blocks.FARMLAND);
			helper.succeedWhen(() -> {
				helper.assertTrue(plant(helper, nearA) == null && plant(helper, nearB) == null, "planted by the composter, outside her marked plot");
				ResourceLocation a = plant(helper, markedA), d = plant(helper, markedB);
				helper.assertTrue(a != null && d != null && Set.of(a, d).equals(Set.of(b("oran"), b("cheri"))),
					"the marked plot isn't planted side by side: " + a + ", " + d);
			});
		});
	}

	/**
	 * The book: a berry's pair is greyed while a parent is still missing, and not once the village has both; clicking a
	 * berry the village has already doesn't make it her goal, and one out of reach doesn't replace her goal.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaBerryBookGreysPairsAndRefusesUselessGoals(GameTestHelper helper) {
		Villager breeder = breeder(helper, berry("oran", 2));
		ServerPlayer player = player(helper);
		ChatFormatting missing = pairColour(helper, breeder, "lum");
		helper.assertTrue(missing == ChatFormatting.DARK_GRAY, "with Cheri missing, Lum's pair is " + missing + ", not greyed");
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(1, berry("cheri", 2));
		// The chests are read again a few ticks later (store contents may be cached for the tick)
		helper.runAfterDelay(5, () -> {
			ChatFormatting ready = pairColour(helper, breeder, "lum");
			helper.assertTrue(ready != null && ready != ChatFormatting.DARK_GRAY, "with Oran and Cheri, Lum's pair is still greyed");
			BerryBreeders.choose(player, breeder, b("oran"));
			helper.assertTrue(BerryBreeders.goal(breeder) == null, "Oran (in the chest) became her goal");
			BerryBreeders.choose(player, breeder, b("lum"));
			helper.assertTrue(b("lum").equals(BerryBreeders.goal(breeder)), "Lum didn't become her goal");
			BerryBreeders.choose(player, breeder, b("sitrus"));
			helper.assertTrue(b("lum").equals(BerryBreeders.goal(breeder)), "Sitrus (out of reach without Figy) replaced Lum: " + BerryBreeders.goal(breeder));
			helper.assertTrue(BerryBreeders.goalLine(helper.getLevel(), breeder).getString().equals("Goal: Lum Berry. Now: Cheri Berry + Oran Berry for Lum Berry")
				|| BerryBreeders.goalLine(helper.getLevel(), breeder).getString().equals("Goal: Lum Berry. Now: Oran Berry + Cheri Berry for Lum Berry"),
				"the goal line reads: " + BerryBreeders.goalLine(helper.getLevel(), breeder).getString());
			helper.succeed();
		});
	}

	/** The colour of the Oran + Cheri pair line on {@code name}'s page of the book (null: no such line). */
	private static ChatFormatting pairColour(GameTestHelper helper, Villager breeder, String name) {
		ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
		int[] page = {0};
		ChoiceMenu menu = ChoiceMenu.detached(viewer, m -> BerryBreeders.fill(m, helper.getLevel(), breeder, page));
		ItemStack want = berry(name, 1);
		for (int turn = 0; turn < 4; turn++) {
			for (int slot = 0; slot < 45; slot++) {
				ItemStack icon = menu.icon(slot);
				if (ItemStack.isSameItem(icon, want)) {
					for (Component line : icon.get(DataComponents.LORE).lines()) {
						if (line.getString().contains(" + ") && line.getString().contains("Oran") && line.getString().contains("Cheri")) {
							net.minecraft.network.chat.TextColor colour = line.getStyle().getColor();
							for (ChatFormatting f : ChatFormatting.values()) {
								if (f.isColor() && colour != null && colour.equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(f))) {
									return f;
								}
							}
							return null;
						}
					}
					throw new AssertionError(name + "'s page has no Oran + Cheri line: " + icon.get(DataComponents.LORE).lines());
				}
			}
			page[0]++;
			BerryBreeders.fill(menu, helper.getLevel(), breeder, page);
		}
		throw new AssertionError(name + " isn't in the book");
	}

	/** The village's found berries are kept in the hall: empty by default, and they survive a save and reload. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaHallKeepsFoundBerriesThroughAReload(GameTestHelper helper) {
		BlockPos hallPos = new BlockPos(1, 2, 7);
		helper.setBlock(hallPos, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(hallPos);
		helper.assertTrue(hall.berriesFound().isEmpty(), "a new hall has found berries already: " + hall.berriesFound());
		CompoundTag empty = hall.saveWithoutMetadata(helper.getLevel().registryAccess());
		hall.loadWithComponents(empty, helper.getLevel().registryAccess());
		helper.assertTrue(hall.berriesFound().isEmpty(), "an empty hall reloaded with berries");
		helper.assertTrue(hall.findBerry(b("lum")), "Lum wasn't new");
		helper.assertFalse(hall.findBerry(b("lum")), "Lum counted as new twice");
		hall.findBerry(b("sitrus"));
		CompoundTag tag = hall.saveWithoutMetadata(helper.getLevel().registryAccess());
		helper.setBlock(hallPos, Blocks.AIR);
		helper.setBlock(hallPos, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity loaded = helper.getBlockEntity(hallPos);
		loaded.loadWithComponents(tag, helper.getLevel().registryAccess());
		helper.assertTrue(loaded.berriesFound().equals(Set.of(b("lum"), b("sitrus"))), "after a reload the hall has " + loaded.berriesFound());
		helper.succeed();
	}

	// --- 28.12 The Daycare Keeper -----------------------------------------------------------------

	private static final BlockPos PASTURE = new BlockPos(2, 2, 2);

	private static BlockState half(String part) {
		BlockState state = BuiltInRegistries.BLOCK.get(c("pasture")).defaultBlockState();
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals("part")) {
				return with(state, property, part);
			}
		}
		throw new IllegalStateException("cobblemon:pasture has no part property");
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
		return state.setValue(property, property.getValue(value).orElseThrow());
	}

	private static Villager keeper(GameTestHelper helper) {
		helper.setDayTime(2000);
		helper.setBlock(PASTURE, half("bottom"));
		helper.setBlock(PASTURE.above(), half("top"));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(PASTURE), ModVillagers.PASTURE_POI, ModVillagers.DAYCARE_KEEPER);
		helper.assertTrue(DaycareKeepers.isKeeper(villager), "setup: not a daycare keeper");
		return villager;
	}

	private static ServerPlayer trainer(GameTestHelper helper, Pokemon... pokemon) {
		ServerPlayer player = player(helper);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		party.add(make("bulbasaur level=10"));
		for (Pokemon p : pokemon) {
			party.add(p);
		}
		return player;
	}

	private static void daysPass(Villager keeper, int days) {
		List<DaycareKeepers.Pair> pairs = new ArrayList<>();
		for (DaycareKeepers.Pair pair : DaycareKeepers.pairs(keeper)) {
			pairs.add(pair.withEggs(pair.eggs(), pair.day() - days));
		}
		DaycareKeepers.setPairs(keeper, pairs);
	}

	private static int eggs(Villager keeper) {
		return DaycareKeepers.pairs(keeper).stream().mapToInt(DaycareKeepers.Pair::eggs).sum();
	}

	private static void setLevel(Villager villager, int level) {
		villager.setVillagerData(villager.getVillagerData().setLevel(level));
	}

	/** One pair per player, three pairs per keeper at most: the same player's second pair and a fourth player are refused. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaDaycareOnePairPerPlayerThreePerKeeper(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		Pokemon m1 = make("eevee gender=female level=20"), f1 = make("eevee gender=male level=20");
		Pokemon m2 = make("pikachu gender=female level=20"), f2 = make("pikachu gender=male level=20");
		ServerPlayer first = trainer(helper, m1, f1, m2, f2);
		helper.assertTrue(CobblemonDaycareKeeper.leave(first, keeper, m1, f1), "the first pair wasn't taken");
		helper.assertFalse(CobblemonDaycareKeeper.leave(first, keeper, m2, f2), "a second pair from the same player was taken");
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(first);
		boolean kept = false;
		for (Pokemon p : party) {
			kept |= p == m2;
		}
		helper.assertTrue(kept, "the refused pair left the party anyway");
		for (int i = 0; i < 2; i++) {
			Pokemon m = make("eevee gender=female level=20"), f = make("eevee gender=male level=20");
			helper.assertTrue(CobblemonDaycareKeeper.leave(trainer(helper, m, f), keeper, m, f), "pair " + (i + 2) + " wasn't taken");
		}
		helper.assertTrue(DaycareKeepers.pairs(keeper).size() == 3, "pairs: " + DaycareKeepers.pairs(keeper).size());
		Pokemon m = make("eevee gender=female level=20"), f = make("eevee gender=male level=20");
		ServerPlayer fourth = trainer(helper, m, f);
		helper.assertFalse(CobblemonDaycareKeeper.leave(fourth, keeper, m, f), "a fourth pair was taken");
		helper.assertTrue(DaycareKeepers.pairs(keeper).size() == 3, "pairs after the fourth: " + DaycareKeepers.pairs(keeper).size());
		helper.succeed();
	}

	/**
	 * How well they get along, from the species data: very well (same species, different original trainers), well (same
	 * species and trainer, or a shared egg group across species), so-so (Ditto); the screen says so in words; the odds
	 * are 70/50/20%, +10% at Expert and Master but not at Journeyman.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaDaycareGetAlongAndTheOddsByLevel(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		ServerPlayer player = trainer(helper, mother, father);
		mother.setOriginalTrainer(UUID.randomUUID());
		father.setOriginalTrainer(UUID.randomUUID());
		int along = CobblemonDaycareKeeper.getAlong(mother, father);
		helper.assertTrue(along == DaycareKeepers.VERY_WELL, "two Eevee from different trainers get along " + along);
		UUID same = UUID.randomUUID();
		Pokemon a = make("eevee gender=female"), d = make("eevee gender=male");
		a.setOriginalTrainer(same);
		d.setOriginalTrainer(same);
		helper.assertTrue(CobblemonDaycareKeeper.getAlong(a, d) == DaycareKeepers.WELL, "same species, same trainer: " + CobblemonDaycareKeeper.getAlong(a, d));
		int cross = CobblemonDaycareKeeper.getAlong(make("eevee gender=female"), make("pikachu gender=male"));
		helper.assertTrue(cross == DaycareKeepers.WELL, "Eevee and Pikachu (both Field) get along " + cross);
		helper.assertTrue(CobblemonDaycareKeeper.getAlong(make("ditto"), make("pikachu gender=female")) == DaycareKeepers.SO_SO, "Ditto and Pikachu");
		ChoiceMenu menu = CobblemonDaycareKeeper.menuForTest(player, keeper);
		menu.press(CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 1, player);
		menu.press(CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 2, player);
		helper.assertTrue(DaycareKeepers.pairs(keeper).size() == 1, "the pair wasn't left from the screen");
		String said = menu.icon(CobblemonDaycareKeeper.GET_ALONG).getHoverName().getString();
		helper.assertTrue(said.equals("very well"), "the screen says they get along " + said);
		int[][] expect = {{1, 70, 50, 20}, {3, 70, 50, 20}, {4, 80, 60, 30}, {5, 80, 60, 30}};
		for (int[] row : expect) {
			setLevel(keeper, row[0]);
			helper.assertTrue(DaycareKeepers.chance(keeper, DaycareKeepers.VERY_WELL) == row[1]
					&& DaycareKeepers.chance(keeper, DaycareKeepers.WELL) == row[2]
					&& DaycareKeepers.chance(keeper, DaycareKeepers.SO_SO) == row[3]
					&& DaycareKeepers.chance(keeper, DaycareKeepers.NOT_AT_ALL) == 0,
				"level " + row[0] + ": " + DaycareKeepers.chance(keeper, DaycareKeepers.VERY_WELL) + "/" + DaycareKeepers.chance(keeper, DaycareKeepers.WELL)
					+ "/" + DaycareKeepers.chance(keeper, DaycareKeepers.SO_SO) + "/" + DaycareKeepers.chance(keeper, DaycareKeepers.NOT_AT_ALL));
		}
		helper.succeed();
	}

	/**
	 * Three eggs a pair at most: a day left with her gives nothing the same day, many dawns stop at three (one long
	 * absence too), and the waiting eggs survive a save and reload.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaDaycareEggsStopAtThreeAndSurviveAReload(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		setLevel(keeper, 5);
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		ServerPlayer player = trainer(helper, mother, father);
		helper.assertTrue(CobblemonDaycareKeeper.leave(player, keeper, mother, father), "the pair wasn't left");
		RandomSource random = RandomSource.create(42);
		helper.assertTrue(DaycareKeepers.dawn(helper.getLevel(), keeper, random) == 0 && eggs(keeper) == 0, "an egg on the day they were left");
		for (int day = 0; day < 20; day++) {
			daysPass(keeper, 1);
			DaycareKeepers.dawn(helper.getLevel(), keeper, random);
			helper.assertTrue(eggs(keeper) <= DaycareKeepers.MAX_EGGS && DaycareKeepers.MAX_EGGS == 3, eggs(keeper) + " eggs waiting on day " + day);
		}
		helper.assertTrue(eggs(keeper) == 3, "twenty dawns at 80% and only " + eggs(keeper) + " eggs");
		// A long absence: 30 days in one go still gives three at most
		DaycareKeepers.setPairs(keeper, List.of(DaycareKeepers.pairs(keeper).get(0).withEggs(0, DaycareKeepers.day(helper.getLevel()) - 30)));
		// Forced: every roll a hit, so only the cap holds the count down
		DaycareKeepers.dawn(helper.getLevel(), keeper, new net.minecraft.world.level.levelgen.SingleThreadedRandomSource(1) {
			@Override
			public int nextInt(int bound) {
				return 0;
			}
		});
		helper.assertTrue(eggs(keeper) == 3, "after 30 days away with every roll a hit: " + eggs(keeper) + " eggs");
		int waiting = eggs(keeper);
		CompoundTag saved = new CompoundTag();
		keeper.save(saved);
		Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
		loaded.load(saved);
		helper.assertTrue(eggs(loaded) == waiting && DaycareKeepers.pairs(loaded).get(0).day() == DaycareKeepers.pairs(keeper).get(0).day(),
			"after a reload: " + eggs(loaded) + " eggs (was " + waiting + ")");
		helper.succeed();
	}

	/**
	 * Eggs are kept for the owner: another player collects nothing; the owner without the money is told the price (4
	 * emeralds' worth) and keeps the eggs waiting; with it, every egg is collected, paid for once each, and then the
	 * pair can be taken back to the party.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaDaycareEggsAreTheOwnersAndCostFourEmeraldsEach(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		ServerPlayer owner = trainer(helper, mother, father);
		helper.assertTrue(CobblemonDaycareKeeper.leave(owner, keeper, mother, father), "the pair wasn't left");
		DaycareKeepers.setPairs(keeper, List.of(DaycareKeepers.pairs(keeper).get(0).withEggs(2, DaycareKeepers.day(helper.getLevel()))));
		ServerPlayer stranger = player(helper);
		CobbleDollarsBank.add(stranger, 5000);
		CobblemonDaycareKeeper.collect(stranger, keeper);
		helper.assertTrue(eggs(keeper) == 2 && CobblemonDaycareKeeper.countEggs(stranger) == 0 && CobbleDollarsBank.balance(stranger) == 5000,
			"another player collected the owner's eggs");
		CobbleDollarsBank.take(owner, CobbleDollarsBank.balance(owner));
		owner.getInventory().clearContent();
		String refused = CobblemonDaycareKeeper.collect(owner, keeper).getString();
		long price = (long) DaycareKeepers.EGG_PRICE * Money.DOLLARS_PER_EMERALD;
		helper.assertTrue(refused.startsWith("An egg costs ") && refused.equals("An egg costs " + Money.describe(price, DaycareKeepers.EGG_PRICE).getString() + "."),
			"the refusal reads: " + refused);
		helper.assertTrue(eggs(keeper) == 2, "eggs lost without paying");
		CobbleDollarsBank.add(owner, 1000);
		String collected = CobblemonDaycareKeeper.collect(owner, keeper).getString();
		helper.assertTrue(collected.startsWith("You collected ") && collected.endsWith("."), "the collection reads: " + collected);
		helper.assertTrue(eggs(keeper) == 0 && CobblemonDaycareKeeper.countEggs(owner) == 2, "collected " + CobblemonDaycareKeeper.countEggs(owner)
			+ " eggs, " + eggs(keeper) + " left waiting");
		helper.assertTrue(CobbleDollarsBank.balance(owner) == 1000 - 2 * price, "paid " + (1000 - CobbleDollarsBank.balance(owner)) + " for two eggs");
		helper.assertTrue(CobblemonDaycareKeeper.collect(owner, keeper).getString().isEmpty() && CobbleDollarsBank.balance(owner) == 1000 - 2 * price,
			"charged again with no eggs waiting");
		String back = CobblemonDaycareKeeper.takeBack(owner, keeper).getString();
		helper.assertTrue(back.equals("You took your pair back."), "taking back reads: " + back);
		boolean hasMother = false, hasFather = false;
		for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(owner)) {
			hasMother |= p.getUuid().equals(mother.getUuid());
			hasFather |= p.getUuid().equals(father.getUuid());
		}
		helper.assertTrue(hasMother && hasFather && DaycareKeepers.pairs(keeper).isEmpty(), "the pair isn't back in the party");
		helper.succeed();
	}

	/** A pair of the Undiscovered group (two Pichu) never gets an egg, even with a Master keeper. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaDaycareUndiscoveredNeverBreedsEvenAtMaster(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		setLevel(keeper, 5);
		Pokemon mother = make("pichu gender=female level=5");
		Pokemon father = make("pichu gender=male level=5");
		helper.assertTrue(CobblemonDaycareKeeper.getAlong(mother, father) == DaycareKeepers.NOT_AT_ALL, "two Pichu get along");
		helper.assertTrue(DaycareKeepers.chance(keeper, DaycareKeepers.NOT_AT_ALL) == 0, "a Master has odds for a pair that doesn't get along");
		ServerPlayer player = trainer(helper, mother, father);
		CobblemonDaycareKeeper.leave(player, keeper, mother, father);
		helper.assertTrue(DaycareKeepers.pairs(keeper).size() == 1, "the pair wasn't left");
		RandomSource random = RandomSource.create(9);
		for (int day = 0; day < 30; day++) {
			daysPass(keeper, 1);
			DaycareKeepers.dawn(helper.getLevel(), keeper, random);
		}
		helper.assertTrue(eggs(keeper) == 0, eggs(keeper) + " eggs from two Pichu");
		helper.succeed();
	}
}
