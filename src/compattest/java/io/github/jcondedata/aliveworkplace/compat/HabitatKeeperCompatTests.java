package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.CobblemonItemComponents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.block.PokeSnackBlock;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.item.components.BaitEffectsComponent;
import io.github.jcondedata.aliveworkplace.camp.CampCooks;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.habitat.HabitatData;
import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** ROADMAP 28.10 with the real Cobblemon: the Habitat Keeper's job at a Pasture Block, and her Saccharine logs. */
public class HabitatKeeperCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos PASTURE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos HALL = new BlockPos(1, 2, 8);
	/** The block a Field Marker marks; the snack goes on top of it. */
	private static final BlockPos MARKED = new BlockPos(9, 1, 9);
	private static final BlockPos SPOT = MARKED.above();
	private static final BlockPos LOG = new BlockPos(8, 2, 2);

	private static ResourceLocation c(String path) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", path);
	}

	/** One half of Cobblemon's Pasture Block ({@code part} bottom or top). */
	private static BlockState half(String part) {
		Block pasture = BuiltInRegistries.BLOCK.get(c("pasture"));
		BlockState state = pasture.defaultBlockState();
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

	private static Item item(String path) {
		return BuiltInRegistries.ITEM.get(c(path));
	}

	/** A Pasture Block, a chest by it with {@code chest} in it, and a villager made its Habitat Keeper. */
	static Villager keeper(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(PASTURE, half("bottom"));
		helper.setBlock(PASTURE.above(), half("top"));
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(PASTURE), ModVillagers.PASTURE_POI, ModVillagers.HABITAT_KEEPER);
		helper.assertTrue(HabitatKeepers.isKeeper(villager), "not a habitat keeper");
		return villager;
	}

	/** A Field Marker with one block marked (both corners on it), as a player marks it. */
	static ItemStack marker(GameTestHelper helper, BlockPos block) {
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		BlockPos abs = helper.absolutePos(block);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(helper.getLevel().dimension().location()), Optional.of(abs), Optional.of(abs)));
		return marker;
	}

	private static String said(GameTestHelper helper, Villager keeper) {
		WorkerStatus.Entry e = WorkerStatus.get(keeper, helper.getLevel().getGameTime());
		return (e == null ? "no status" : e.line().getString()) + " at " + helper.relativePos(keeper.blockPosition()) + ", bag "
			+ ModAttachments.BUILDER_BAG.getOrCreate(keeper).stacks();
	}

	private static boolean snackAt(GameTestHelper helper, BlockPos pos) {
		return BuiltInRegistries.BLOCK.getKey(helper.getBlockState(pos).getBlock()).equals(c("poke_snack"));
	}

	/**
	 * The Done when: a spot marked with a Field Marker (handed to her as a player does); she sets a Poké Snack from her
	 * chest out on it, and when the wild Pokémon have eaten it up (Cobblemon's own bites), she sets out the next one.
	 */
	//$ gametest_ticks_batch AREA '2400' '"habitat_snack"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "habitat_snack")
	public void aSnackIsSetOutOnAMarkedSpotAndAgainWhenEaten(GameTestHelper helper) {
		Villager keeper = keeper(helper, new ItemStack(item("poke_snack"), 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, marker(helper, MARKED));
		PartnerShowsCompatTests.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		// (what the use-entity event does when a player right-clicks her holding the marker)
		helper.assertTrue(HabitatKeepers.markSpot(player, keeper, player.getMainHandItem()).consumesAction(), "handing her the marker did nothing");
		helper.assertTrue(HabitatKeepers.spots(keeper).equals(List.of(helper.absolutePos(SPOT))), "her spots: " + HabitatKeepers.spots(keeper));
		helper.assertTrue(player.getMainHandItem().is(ModItems.FIELD_MARKER), "the marker didn't stay with the player");
		boolean[] eaten = {false};
		helper.onEachTick(() -> {
			if (!eaten[0] && snackAt(helper, SPOT)) {
				// The wild Pokémon eat it up, bite by bite, as Cobblemon's own snack is eaten.
				ServerLevel level = helper.getLevel();
				BlockPos abs = helper.absolutePos(SPOT);
				for (int bite = 0; bite < 20 && level.getBlockState(abs).getBlock() instanceof PokeSnackBlock block; bite++) {
					block.eat(level, abs, level.getBlockState(abs), null);
				}
				helper.assertTrue(!snackAt(helper, SPOT), "the snack wasn't eaten up");
				eaten[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(eaten[0], "no snack set out; she says " + said(helper, keeper));
			helper.assertTrue(snackAt(helper, SPOT), "no second snack set out after the first was eaten; she says " + said(helper, keeper));
			helper.assertTrue(chest.countItem(item("poke_snack")) == 0, "snacks left in the chest: " + chest.countItem(item("poke_snack")));
		});
	}

	/** The Done when: a Saccharine log near her pasture is found by her scan and slathered with honey from her chest, the bottle back in it. */
	//$ gametest_ticks_batch AREA '2400' '"habitat_log"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "habitat_log")
	public void sheSlathersASaccharineLogAtWork(GameTestHelper helper) {
		helper.setBlock(LOG, BuiltInRegistries.BLOCK.get(c("saccharine_log")));
		Villager keeper = keeper(helper, new ItemStack(Items.HONEY_BOTTLE, 1));
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(HabitatKeepers.isSlathered(helper.getBlockState(LOG)), "the log is " + helper.getBlockState(LOG) + "; she says "
				+ said(helper, keeper) + ", logs " + ModAttachments.HONEY_LOGS.getOrElse(keeper, List.of()));
			helper.assertTrue(ModAttachments.HONEY_LOGS.getOrElse(keeper, List.of()).contains(helper.absolutePos(LOG)), "the log isn't remembered");
			helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 0 && chest.countItem(Items.GLASS_BOTTLE) == 1,
				"the bottle didn't come back empty: " + chest.countItem(Items.HONEY_BOTTLE) + " honey, " + chest.countItem(Items.GLASS_BOTTLE) + " glass");
		});
	}

	/**
	 * Likely breakages: her Pasture Block broken mid-job (she stops, says so, sets nothing out), and the switch off
	 * (the same, and a honey bottle picks no job).
	 */
	//$ gametest_ticks_batch AREA '400' '"habitat_broken"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "habitat_broken")
	public void aBrokenPastureOrTheSwitchOffStopsHer(GameTestHelper helper) {
		Villager keeper = keeper(helper, new ItemStack(item("poke_snack"), 2));
		HabitatKeepers.addSpot(keeper, helper.absolutePos(SPOT));
		helper.setBlock(PASTURE.above(), Blocks.AIR);
		helper.setBlock(PASTURE, Blocks.AIR);
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(!snackAt(helper, SPOT), "a snack was set out with the pasture gone");
			// The game takes her job site away with the block (so she stops working), or she says it's gone.
			helper.assertTrue(said(helper, keeper).contains("Pasture Block is gone")
				|| keeper.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).isEmpty(),
				"she still has the broken pasture as her job site and doesn't say it's gone: " + said(helper, keeper));
			helper.assertTrue(((Container) helper.getBlockEntity(CHEST)).countItem(item("poke_snack")) == 2, "snacks left the chest");
			helper.succeed();
		});
	}

	/** The switch off: she sets nothing out and says she's switched off. */
	//$ gametest_ticks_batch AREA '400' '"habitat_off"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "habitat_off")
	public void switchedOffSheSetsNothingOut(GameTestHelper helper) {
		Villager keeper = keeper(helper, new ItemStack(item("poke_snack"), 2));
		HabitatKeepers.addSpot(keeper, helper.absolutePos(SPOT));
		HabitatKeepers.ENABLED = false;
		PartnerShowsCompatTests.after(helper, () -> HabitatKeepers.ENABLED = true);
		helper.runAfterDelay(200, () -> {
			HabitatKeepers.ENABLED = true;
			helper.assertTrue(!snackAt(helper, SPOT), "a snack was set out with the switch off");
			helper.assertTrue(said(helper, keeper).contains("switched off"), "she didn't say she's switched off: " + said(helper, keeper));
			helper.succeed();
		});
	}

	/**
	 * The Done when: a shiny wild Pokémon by the pasture is told to the players in the village and written in the
	 * chronicle once, not every minute; she keeps it in her last sightings (the hall's list). Switched off, she tells nobody.
	 */
	//$ gametest_ticks_batch AREA '200' '"habitat_sighting"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "habitat_sighting")
	public void aShinyWildPokemonIsAnnouncedAndChronicledOnce(GameTestHelper helper) {
		// (wild Pokémon left over from earlier batches nearby would be sighted too)
		helper.getLevel().getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(HabitatKeepers.SIGHT_RANGE + 2)).forEach(e -> e.discard());
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager keeper = keeper(helper);
		ServerLevel level = helper.getLevel();
		PokemonEntity wild = PokemonProperties.Companion.parse("eevee shiny=yes level=5", " ", "=").createEntity(level);
		helper.assertTrue(wild.getPokemon().getShiny() && wild.getPokemon().isWild(), "not a shiny wild Eevee");
		wild.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(12.5, 2, 12.5)));
		wild.setNoAi(true);
		level.addFreshEntity(wild);
		PartnerShowsCompatTests.after(helper, wild::discard);
		BlockPos pasture = helper.absolutePos(PASTURE);
		try {
			HabitatKeepers.SIGHTINGS = false;
			helper.assertTrue(HabitatKeepers.sight(level, keeper, pasture).isEmpty(), "switched off, she still told of it");
		} finally {
			HabitatKeepers.SIGHTINGS = true;
		}
		List<HabitatKeepers.Sighting> first = HabitatKeepers.sight(level, keeper, pasture);
		helper.assertTrue(first.size() == 1 && first.get(0).kind().equals("shiny"), "first look: " + first);
		helper.assertTrue(first.get(0).what().getString().contains("shiny"), "the sighting reads " + first.get(0).what().getString());
		for (int minute = 0; minute < 3; minute++) {
			helper.assertTrue(HabitatKeepers.sight(level, keeper, pasture).isEmpty(), "told of the same Eevee again on look " + (minute + 2));
		}
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		long written = hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.SIGHTING).count();
		helper.assertTrue(written == 1, "the chronicle has " + written + " sightings, not 1: " + hall.chronicle());
		Chronicle.Entry entry = hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.SIGHTING).findFirst().orElseThrow();
		helper.assertTrue(entry.text().getString().contains(keeper.getDisplayName().getString()) && entry.text().getString().contains("shiny"),
			"the chronicle says " + entry.text().getString());
		helper.assertTrue(Chronicle.Kind.SIGHTING.icon == Items.SPYGLASS, "the sighting icon isn't a spyglass");
		helper.assertTrue(HabitatKeepers.sightings(keeper).size() == 1 && HabitatKeepers.sightingLines(keeper).size() == 1,
			"her last sightings: " + HabitatKeepers.sightings(keeper));
		helper.succeed();
	}

	/**
	 * The lure picker (sneak-right-click with an empty hand): the Fire type, seasoned with Occa Berries per Cobblemon's
	 * bait data; the Camp Cook is asked for snacks seasoned so, and she tells a snack seasoned so from a plain one.
	 */
	//$ gametest_ticks_batch AREA '100' '"habitat_lure"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "habitat_lure")
	public void sheIsGivenAFireLureAndAsksTheCookForOccaSnacks(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		helper.assertTrue(HabitatData.lures().getOrDefault("typing/fire", List.of()).contains(c("occa_berry")),
			"Cobblemon's bait data gives no Occa Berry for Fire: " + HabitatData.lures().get("typing/fire"));
		helper.assertTrue(HabitatData.lures().containsKey("egg_group/field"), "no Field egg group lure: " + HabitatData.lures().keySet());
		// Bulbasaur spawns only rare in 1.7.3's data; Eevee and Dratini also uncommon, Pidgey common.
		helper.assertTrue(HabitatData.rareOnly(c("bulbasaur")) && !HabitatData.rareOnly(c("eevee")) && !HabitatData.rareOnly(c("dratini"))
			&& !HabitatData.rareOnly(c("pidgey")), "rare-only species are wrong (bulbasaur, eevee, dratini, pidgey)");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		int[] page = {0};
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> HabitatKeepers.fillLures(m, keeper, page));
		int slot = HabitatKeepers.lures().indexOf("typing/fire");
		helper.assertTrue(slot >= 0 && slot < 45, "Fire isn't on the first page: " + HabitatKeepers.lures());
		helper.assertTrue(menu.icon(slot).get(DataComponents.LORE).lines().stream().anyMatch(l -> l.getString().contains("Occa")),
			"the Fire lure doesn't say Occa: " + menu.icon(slot).get(DataComponents.LORE).lines());
		menu.press(slot, player);
		helper.assertTrue("typing/fire".equals(HabitatKeepers.lure(keeper)), "her lure is " + HabitatKeepers.lure(keeper));
		CampCooks.Dish dish = CampCooks.menu().stream().filter(d -> d.name().getPath().equals("poke_snack_for_the_habitat_keeper")).findFirst().orElseThrow();
		Set<ResourceLocation> asked = CampCooks.askedSeasonings(helper.getLevel(), helper.absolutePos(new BlockPos(6, 2, 6)), dish);
		helper.assertTrue(asked.contains(c("occa_berry")), "the cook isn't asked for Occa: " + asked);
		ItemStack plain = new ItemStack(item("poke_snack"));
		ItemStack occa = new ItemStack(item("poke_snack"));
		occa.set(CobblemonItemComponents.BAIT_EFFECTS, new BaitEffectsComponent(List.of(c("occa_berry"))));
		helper.assertTrue(HabitatKeepers.seasonedFor(occa, "typing/fire") && !HabitatKeepers.seasonedFor(plain, "typing/fire")
			&& HabitatKeepers.seasonedFor(plain, null), "seasoned snacks aren't told apart");
		menu.press(49, player);
		helper.assertTrue(HabitatKeepers.lure(keeper) == null, "the any-snack button left her lure at " + HabitatKeepers.lure(keeper));
		helper.succeed();
	}

	/**
	 * A honey bottle makes a villager by a Pasture Block a Habitat Keeper (not with the switch off); only the pasture's
	 * lower half is a workstation, and a jobless villager never takes it by itself.
	 */
	//$ gametest_ticks AREA '200'
	@GameTest(template = AREA, timeoutTicks = 200)
	public void aHoneyBottleMakesAHabitatKeeper(GameTestHelper helper) {
		helper.assertTrue(PoiTypes.forState(half("bottom")).map(t -> t.is(ModVillagers.PASTURE_POI)).orElse(false),
			"the pasture's lower half is no workstation: " + PoiTypes.forState(half("bottom")));
		helper.assertTrue(PoiTypes.forState(half("top")).isEmpty(), "the pasture's upper half is a workstation too");
		helper.setBlock(PASTURE, half("bottom"));
		helper.setBlock(PASTURE.above(), half("top"));
		helper.assertTrue(Stations.at(BuiltInRegistries.BLOCK.get(c("pasture"))).map(s -> !s.byItself() && s.has(ModVillagers.HABITAT_KEEPER))
			.orElse(false), "a jobless villager could take the pasture by itself");
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 3.5)));
		try {
			HabitatKeepers.ENABLED = false;
			helper.assertTrue(!HabitatKeepers.isHoney(new ItemStack(Items.HONEY_BOTTLE)), "honey still picks a job with the switch off");
			// (honey still names the Nurse's job, which needs a Healing Machine: she gets no job here)
			Stations.choose(player, villager, new ItemStack(Items.HONEY_BOTTLE));
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE,
				"switched off, honey still gave a job: " + villager.getVillagerData().getProfession());
		} finally {
			HabitatKeepers.ENABLED = true;
		}
		Stations.choose(player, villager, new ItemStack(Items.HONEY_BOTTLE));
		helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.HABITAT_KEEPER,
			"a honey bottle made a " + villager.getVillagerData().getProfession());
		helper.succeed();
	}

	/** The log scan finds a Saccharine log within 32 blocks, a few thousand blocks a tick, and honey slathers it as Cobblemon does. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSaccharineLogIsFoundAndSlathered(GameTestHelper helper) {
		BlockPos center = helper.absolutePos(PASTURE);
		BlockPos log = helper.absolutePos(new BlockPos(4, 2, 1));
		Block saccharine = BuiltInRegistries.BLOCK.get(c("saccharine_log"));
		helper.getLevel().setBlockAndUpdate(log, saccharine.defaultBlockState());
		List<BlockPos> found = new ArrayList<>();
		int cursor = 0;
		int ticks = 0;
		do {
			cursor = HabitatKeepers.scan(helper.getLevel(), center, cursor, Integer.MAX_VALUE, found);
			ticks++;
		} while (cursor != 0 && ticks < 1000);
		helper.assertTrue(ticks == (HabitatKeepers.scanSize() + HabitatKeepers.SCAN_PER_TICK - 1) / HabitatKeepers.SCAN_PER_TICK,
			"the scan took " + ticks + " ticks, not at most " + HabitatKeepers.SCAN_PER_TICK + " blocks a tick");
		helper.assertTrue(found.equals(List.of(log)), "the scan found " + found + ", not the log at " + log);
		Direction face = HabitatKeepers.faceToward(log, center);
		helper.assertTrue(face == Direction.WEST, "the honey faces " + face + ", not the pasture to the west");
		helper.assertTrue(HabitatKeepers.slather(helper.getLevel(), log, face), "the log wasn't slathered");
		BlockState state = helper.getLevel().getBlockState(log);
		helper.assertTrue(BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(c("saccharine_log_slathered")), "the log is " + state);
		helper.assertTrue(HabitatKeepers.isSlathered(state) && !HabitatKeepers.slather(helper.getLevel(), log, face),
			"a slathered log was slathered again");
		// A second scan remembers it once, slathered or not.
		do {
			cursor = HabitatKeepers.scan(helper.getLevel(), center, cursor, Integer.MAX_VALUE, found);
		} while (cursor != 0);
		helper.assertTrue(found.size() == 1, "the log was remembered " + found.size() + " times");
		helper.succeed();
	}
}
