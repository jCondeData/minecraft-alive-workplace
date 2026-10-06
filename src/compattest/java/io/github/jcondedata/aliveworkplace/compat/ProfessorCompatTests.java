package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonProfessor;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
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
import io.github.jcondedata.aliveworkplace.orchard.Fruit;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchTree;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.PasturedPokemon;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Partners;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 29.21 with the real Cobblemon: the Pokémon Professor's census of the village's Pasture Blocks (the condition
 * {@code pastured_pokemon}), hints that match a Pokémon's real IVs, nature, hidden ability and EVs, the village Pokédex
 * logging each species once (and keeping it through a save), and every Pokédex topic's unlock and effect.
 */
public class ProfessorCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	/** A hall in the middle of the area (radius 14), Legend needs off, everything put back afterwards. */
	private static void setUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(8)).forEach(e -> e.discard());
		level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8)).forEach(e -> e.discard());
		int radius = VillageHalls.RADIUS;
		boolean needs = LegendNeeds.ENABLED;
		VillageHalls.RADIUS = 14;
		LegendNeeds.ENABLED = false;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			LegendNeeds.ENABLED = needs;
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(8)).forEach(e -> e.discard());
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

	private static Legend legend() {
		return Legends.get(PokemonProfessor.ID).orElseThrow(() -> new AssertionError("pokemon_professor.json didn't load with Cobblemon"));
	}

	private static Villager professor(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		Legends.make(helper.getLevel(), v, legend(), "test");
		LegendPowers.forget();
		return v;
	}

	private static ResearchTree tree() {
		return ResearchTrees.get(PokemonProfessor.TREE).orElseThrow(() -> new AssertionError("the Pokédex tree didn't load with Cobblemon"));
	}

	/** The village's Pokédex research at these levels (by topic id). */
	private static void research(GameTestHelper helper, Map<String, Integer> levels) {
		Map<String, Integer> map = new HashMap<>();
		levels.forEach((k, v) -> map.put(tree().key() + "/" + k, v));
		hall(helper).setResearch(new Research.State(Map.copyOf(map), Optional.empty(), 0, false));
		CivicEffects.forget();
	}

	private static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static Object arg(Component c, int i) {
		return ((TranslatableContents) c.getContents()).getArgs()[i];
	}

	private static String lore(ItemStack stack) {
		var lore = stack.get(DataComponents.LORE);
		return lore == null ? "" : String.join(" / ", lore.lines().stream().map(Component::getString).toList());
	}

	/**
	 * The census counts the Pokémon kept in the village's Pasture Blocks and their types, not a wild one; the condition
	 * needs both the count and the types; the real file asks for 25 of 10 types.
	 */
	//$ gametest_ticks_batch AREA '200' '"professor_census"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "professor_census")
	public void theCensusCountsPasturedPokemonAndTheirTypes(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos a = PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		BlockPos b = PastureCompatTests.pasture(helper, new BlockPos(23, 2, 23));
		PastureCompatTests.pastured(helper, a, player, "pikachu", Direction.NORTH);
		PastureCompatTests.pastured(helper, a, player, "bulbasaur", Direction.WEST);
		PastureCompatTests.pastured(helper, b, player, "charmander", Direction.NORTH);
		PastureCompatTests.pastured(helper, b, player, "geodude", Direction.WEST);
		PokemonEntity wild = PokemonProperties.Companion.parse("squirtle level=5", " ", "=").createEntity(level);
		wild.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(12.5, 2, 12.5)));
		wild.setNoAi(true);
		level.addFreshEntity(wild);
		helper.runAfterDelay(10, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			PokemonCensus.Count count = PokemonCensus.of(level, hall);
			helper.assertTrue(count.pokemon() == 4, "pastured: " + count.pokemon() + " (the wild Squirtle mustn't count)");
			helper.assertTrue(count.types().equals(Set.of("electric", "grass", "poison", "fire", "rock", "ground")), "types: " + count.types());
			helper.assertTrue(count.species().equals(Set.of("cobblemon:pikachu", "cobblemon:bulbasaur", "cobblemon:charmander", "cobblemon:geodude")),
				"species: " + count.species());
			Condition.Progress enough = new PasturedPokemon(4, 6).progress(level, hall);
			helper.assertTrue(enough.met() && enough.have() == 4, "4 of 6 types should be met: " + enough.have());
			Condition.Progress fewTypes = new PasturedPokemon(4, 7).progress(level, hall);
			helper.assertTrue(!fewTypes.met() && fewTypes.have() == 3, "7 types aren't there: " + fewTypes.have());
			helper.assertTrue(fewTypes.line().getString().equals("Pokémon in the village's Pasture Blocks: 4 of 4, of 6 of 7 types"),
				"line: " + fewTypes.line().getString());
			helper.assertTrue(!new PasturedPokemon(5, 1).met(level, hall), "5 Pokémon aren't there");
			Legend professor = legend();
			helper.assertTrue(professor.conditions().size() == 1 && professor.conditions().get(0) instanceof PasturedPokemon p && p.count() == 25 && p.types() == 10,
				"the Professor's condition: " + professor.conditions());
			helper.assertTrue(!professor.conditions().get(0).met(level, hall), "4 Pokémon meet the Professor's 25");
			helper.assertTrue(professor.job().toString().equals("aliveworkplace:legend"), "job: " + professor.job());
			helper.assertTrue(professor.luxury().equals(Optional.of("books")), "likes: " + professor.luxury());
			helper.succeed();
		});
	}

	/**
	 * Hints match the Pokémon's real IVs (in the judge's words), nature (raised and lowered stats), hidden ability and
	 * EVs; the party row shows them, and Regional Survey gives exact numbers.
	 */
	//$ gametest_ticks_batch AREA '200' '"professor_hints"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "professor_hints")
	public void hintsMatchThePokemonsRealStats(GameTestHelper helper) {
		setUp(helper);
		helper.runAfterDelay(5, () -> {
			Villager prof = professor(helper, new BlockPos(13, 2, 13));
			helper.assertTrue(PokemonProfessor.isProfessor(prof) && PokemonProfessor.working(prof), "not a working Professor");
			Pokemon bulbasaur = make("bulbasaur level=12 nature=adamant ability=chlorophyll");
			Pokemon pikachu = make("pikachu level=5 nature=hardy ability=static");
			int[] ivs = {0, 10, 20, 28, 30, 31};
			int[] evs = {0, 40, 100, 200, 150, 0};
			String[] ivWords = {"no_good", "decent", "pretty_good", "very_good", "fantastic", "best"};
			String[] evWords = {"none", "a_little", "some", "a_lot", "a_lot", "none"};
			for (int i = 0; i < 6; i++) {
				bulbasaur.getIvs().set(CobblemonProfessor.STATS.get(i), ivs[i]);
				bulbasaur.getEvs().set(CobblemonProfessor.STATS.get(i), evs[i]);
			}
			helper.assertTrue(CobblemonProfessor.hasHiddenAbility(bulbasaur), "Chlorophyll is Bulbasaur's hidden ability");
			helper.assertTrue(!CobblemonProfessor.hasHiddenAbility(pikachu) && CobblemonProfessor.hiddenAbility(pikachu).isPresent(),
				"Static isn't Pikachu's hidden ability");
			List<Component> hints = CobblemonProfessor.hints(bulbasaur, false);
			helper.assertTrue(hints.size() == 8, "hints: " + hints.size());
			helper.assertTrue(key(hints.get(0)).equals("message.aliveworkplace.professor.nature")
				&& arg(hints.get(0), 1).equals(Stats.ATTACK.getDisplayName()) && arg(hints.get(0), 2).equals(Stats.SPECIAL_ATTACK.getDisplayName()),
				"Adamant raises Attack and lowers Sp. Atk: " + hints.get(0).getString());
			for (int i = 0; i < 6; i++) {
				Component line = hints.get(1 + i);
				helper.assertTrue(key(line).equals("message.aliveworkplace.professor.stat")
					&& arg(line, 0).equals(CobblemonProfessor.STATS.get(i).getDisplayName())
					&& key((Component) arg(line, 1)).equals("message.aliveworkplace.professor.iv." + ivWords[i])
					&& key((Component) arg(line, 2)).equals("message.aliveworkplace.professor.ev." + evWords[i]),
					"stat " + i + " (IV " + ivs[i] + ", EV " + evs[i] + "): " + line.getString());
			}
			helper.assertTrue(key(hints.get(7)).equals("message.aliveworkplace.professor.hidden"), "hidden ability: " + hints.get(7).getString());
			helper.assertTrue(hints.get(2).getString().endsWith(": Decent, a few EVs"), "said: " + hints.get(2).getString());
			List<Component> pika = CobblemonProfessor.hints(pikachu, false);
			helper.assertTrue(key(pika.get(0)).equals("message.aliveworkplace.professor.nature_neutral"), "Hardy is neutral: " + pika.get(0).getString());
			helper.assertTrue(key(pika.get(7)).equals("message.aliveworkplace.professor.not_hidden"), "Pikachu's ability: " + pika.get(7).getString());
			for (int i = 0; i < 6; i++) {
				int iv = pikachu.getIvs().getOrDefault(CobblemonProfessor.STATS.get(i));
				helper.assertTrue(key((Component) arg(pika.get(1 + i), 1)).equals("message.aliveworkplace.professor.iv." + PokemonProfessor.ivKey(iv)),
					"Pikachu's random IV " + iv + ": " + pika.get(1 + i).getString());
			}
			List<Component> exact = CobblemonProfessor.hints(bulbasaur, true);
			for (int i = 0; i < 6; i++) {
				Component line = exact.get(1 + i);
				helper.assertTrue(key(line).equals("message.aliveworkplace.professor.stat_exact") && arg(line, 2).equals(ivs[i]) && arg(line, 3).equals(evs[i]),
					"exact stat " + i + ": " + line.getString());
			}
			// The screen: the party row's tooltips
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			var party = Cobblemon.INSTANCE.getStorage().getParty(player);
			party.add(bulbasaur);
			party.add(pikachu);
			ChoiceMenu menu = CobblemonProfessor.menuForTest(player, prof);
			String first = lore(menu.icon(CobblemonProfessor.FIRST_PARTY_SLOT));
			helper.assertTrue(first.contains("Adamant") && first.contains("No good") && first.contains("Best") && first.contains("Chlorophyll"),
				"Bulbasaur's tooltip: " + first);
			helper.assertTrue(lore(menu.icon(CobblemonProfessor.FIRST_PARTY_SLOT + 1)).contains("Hardy"), "Pikachu's tooltip");
			helper.assertTrue(menu.icon(CobblemonProfessor.STONE).isEmpty(), "a stone for sale without Evolution Studies");
			menu.press(CobblemonProfessor.FIRST_PARTY_SLOT, player); // the hints to chat
			research(helper, Map.of(PokemonProfessor.REGIONAL_SURVEY, 1));
			helper.assertTrue(PokemonProfessor.exact(prof), "Regional Survey doesn't make the hints exact");
			menu = CobblemonProfessor.menuForTest(player, prof);
			helper.assertTrue(lore(menu.icon(CobblemonProfessor.FIRST_PARTY_SLOT)).contains("Best (IV 31), EVs 0"),
				"exact tooltip: " + lore(menu.icon(CobblemonProfessor.FIRST_PARTY_SLOT)));
			helper.succeed();
		});
	}

	/** Each species kept in the pastures is logged once, only with the Professor living there, and the Pokédex survives a save. */
	//$ gametest_ticks_batch AREA '200' '"professor_pokedex"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "professor_pokedex")
	public void eachSpeciesIsLoggedOnce(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos a = PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		PastureCompatTests.pastured(helper, a, player, "pikachu", Direction.NORTH);
		PastureCompatTests.pastured(helper, a, player, "pikachu", Direction.SOUTH);
		PastureCompatTests.pastured(helper, a, player, "bulbasaur", Direction.WEST);
		helper.runAfterDelay(10, () -> {
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = hall(helper);
			PokemonProfessor.round(level, hallPos, hall);
			helper.assertTrue(hall.pokedex().isEmpty(), "logged without a Professor: " + hall.pokedex());
			professor(helper, new BlockPos(13, 2, 13));
			PokemonProfessor.round(level, hallPos, hall);
			helper.assertTrue(hall.pokedex().equals(Set.of("cobblemon:pikachu", "cobblemon:bulbasaur")), "logged: " + hall.pokedex());
			PokemonProfessor.round(level, hallPos, hall);
			helper.assertTrue(hall.pokedex().size() == 2, "logged twice: " + hall.pokedex());
			helper.assertTrue(ResearchTrees.count(level, hallPos, PokemonProfessor.COUNTER) == 2, "the counter: " + ResearchTrees.count(level, hallPos, PokemonProfessor.COUNTER));
			CompoundTag tag = hall.saveWithoutMetadata(level.registryAccess());
			VillageHallBlockEntity other = new VillageHallBlockEntity(hallPos, hall.getBlockState());
			other.loadWithComponents(tag, level.registryAccess());
			helper.assertTrue(new ArrayList<>(other.pokedex()).equals(new ArrayList<>(hall.pokedex())), "after a save: " + other.pokedex());
			// The hall's Legends page shows the count and the species
			ChoiceMenu menu = ChoiceMenu.detached(player, m -> PokemonProfessor.button(m, level, hallPos));
			ItemStack book = menu.icon(PokemonProfessor.POKEDEX_SLOT);
			helper.assertTrue(book.is(Items.BOOK) && lore(book).contains("2 species logged") && lore(book).contains("Pikachu") && lore(book).contains("Bulbasaur")
				&& lore(book).contains("At 15 species: Field Notes"), "the Pokédex on the hall: " + lore(book));
			helper.succeed();
		});
	}

	/** Every Pokédex topic opens at its count of species (15, 25, 35, 45, 60, 80), not one sooner. */
	//$ gametest_ticks_batch AREA '200' '"professor_topics"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "professor_topics")
	public void everyTopicOpensAtItsCount(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(5, () -> {
			professor(helper, new BlockPos(13, 2, 13));
			ResearchTree tree = tree();
			BlockPos hallPos = helper.absolutePos(HALL);
			helper.assertTrue(tree.legend().equals(PokemonProfessor.ID), "the tree's Legend: " + tree.legend());
			List<String> ids = tree.topics().stream().map(ResearchTree.Topic::id).toList();
			helper.assertTrue(ids.equals(List.of("field_notes", "kinship_studies", "breeding_records", "berry_science", "evolution_studies", "regional_survey")),
				"topics: " + ids);
			int[] at = {15, 25, 35, 45, 60, 80};
			int[] levels = {2, 1, 2, 1, 1, 1};
			helper.assertTrue(ResearchTrees.inVillage(level, hallPos).contains(tree), "no Pokédex tab with the Professor in the village");
			VillageHallBlockEntity hall = hall(helper);
			int logged = 0;
			for (int i = 0; i < ids.size(); i++) {
				ResearchTree.Topic topic = tree.topics().get(i);
				helper.assertTrue(topic.levels() == levels[i] && topic.unlock().isPresent() && topic.unlock().get().at() == at[i]
					&& topic.unlock().get().counter().equals(PokemonProfessor.COUNTER), topic.id() + ": " + topic.levels() + " levels, " + topic.unlock());
				List<String> more = new ArrayList<>();
				while (logged + more.size() < at[i] - 1) {
					more.add("cobblemon:test_" + (logged + more.size()));
				}
				logged += hall.logPokedex(more).size();
				Optional<Component> why = ResearchTrees.whyNot(level, hallPos, hall.research(), tree, topic);
				helper.assertTrue(why.isPresent() && key(why.get()).equals("message.aliveworkplace.research.unlock"),
					topic.id() + " open at " + logged + " species: " + why.map(Component::getString));
				logged += hall.logPokedex(List.of("cobblemon:test_" + logged)).size();
				helper.assertTrue(ResearchTrees.whyNot(level, hallPos, hall.research(), tree, topic).isEmpty(),
					topic.id() + " still closed at " + logged + ": " + ResearchTrees.whyNot(level, hallPos, hall.research(), tree, topic).map(Component::getString));
			}
			helper.succeed();
		});
	}

	/** Field Notes: partners help 5% more a level; Kinship Studies: one more partner per worker. */
	//$ gametest_ticks_batch AREA '200' '"professor_partners"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "professor_partners")
	public void fieldNotesAndKinshipStudiesHelpPartners(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(10, 2, 10);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(11, 2, 11));
		builder.setNoAi(true);
		Jobs.employ(level, builder, helper.absolutePos(bench), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(18, 2, 10));
		PastureCompatTests.pastured(helper, pasture, player, "machop", Direction.NORTH);
		helper.runAfterDelay(10, () -> {
			Partners.forget(builder);
			helper.assertTrue(Partners.helpers(builder).size() == 1, "helpers: " + Partners.helpers(builder));
			helper.assertTrue(Math.abs(Partners.factor(builder) - 0.85f) < 1e-4, "without research: " + Partners.factor(builder));
			helper.assertTrue(Partners.max(builder) == Partners.MAX, "max without research: " + Partners.max(builder));
			research(helper, Map.of(PokemonProfessor.FIELD_NOTES, 1));
			helper.assertTrue(Math.abs(Partners.factor(builder) - (1f - 0.15f * 1.05f)) < 1e-4, "Field Notes I: " + Partners.factor(builder));
			research(helper, Map.of(PokemonProfessor.FIELD_NOTES, 2));
			helper.assertTrue(Math.abs(Partners.factor(builder) - (1f - 0.15f * 1.10f)) < 1e-4, "Field Notes II: " + Partners.factor(builder));
			research(helper, Map.of(PokemonProfessor.KINSHIP_STUDIES, 1));
			helper.assertTrue(Partners.max(builder) == Partners.MAX + 1, "Kinship Studies: " + Partners.max(builder));
			helper.assertTrue(Math.abs(Partners.factor(builder) - 0.85f) < 1e-4, "Kinship alone changes one partner's help: " + Partners.factor(builder));
			helper.succeed();
		});
	}

	/** Breeding Records: daycare eggs come 20% sooner a level (the dawn odds raised to match, never past 100). */
	//$ gametest_ticks_batch AREA '200' '"professor_breeding"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "professor_breeding")
	public void breedingRecordsBringEggsSooner(GameTestHelper helper) {
		setUp(helper);
		helper.runAfterDelay(5, () -> {
			Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
			keeper.setNoAi(true);
			helper.assertTrue(DaycareKeepers.chance(keeper, DaycareKeepers.WELL) == 50, "without research: " + DaycareKeepers.chance(keeper, DaycareKeepers.WELL));
			research(helper, Map.of(PokemonProfessor.BREEDING_RECORDS, 1));
			helper.assertTrue(DaycareKeepers.chance(keeper, DaycareKeepers.WELL) == 63, "Breeding Records I: " + DaycareKeepers.chance(keeper, DaycareKeepers.WELL));
			helper.assertTrue(DaycareKeepers.chance(keeper, DaycareKeepers.SO_SO) == 25, "so-so, I: " + DaycareKeepers.chance(keeper, DaycareKeepers.SO_SO));
			research(helper, Map.of(PokemonProfessor.BREEDING_RECORDS, 2));
			helper.assertTrue(DaycareKeepers.chance(keeper, DaycareKeepers.WELL) == 83, "Breeding Records II: " + DaycareKeepers.chance(keeper, DaycareKeepers.WELL));
			helper.assertTrue(DaycareKeepers.chance(keeper, DaycareKeepers.VERY_WELL) == 100, "very well, II: " + DaycareKeepers.chance(keeper, DaycareKeepers.VERY_WELL));
			helper.assertTrue(DaycareKeepers.chance(keeper, DaycareKeepers.NOT_AT_ALL) == 0, "a pair that won't breed still won't");
			helper.succeed();
		});
	}

	/** Berry Science: an orchard keeper picks one more berry from a berry plant (the same plant, picked with and without). */
	//$ gametest_ticks_batch AREA '200' '"professor_berries"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "professor_berries")
	public void berryScienceGivesOneMoreBerry(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos berry = new BlockPos(10, 3, 10);
		helper.setBlock(berry.below(), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
		var age = com.cobblemon.mod.common.block.BerryBlock.Companion.getAGE();
		var ripe = com.cobblemon.mod.common.CobblemonBlocks.ORAN_BERRY.defaultBlockState().setValue(age, com.cobblemon.mod.common.block.BerryBlock.FRUIT_AGE);
		helper.setBlock(berry, ripe);
		helper.runAfterDelay(5, () -> {
			Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(11, 2, 11));
			keeper.setNoAi(true);
			BlockPos at = helper.absolutePos(berry);
			var plant = (com.cobblemon.mod.common.block.entity.BerryBlockEntity) level.getBlockEntity(at);
			plant.generateSimpleYields();
			CompoundTag yields = plant.saveWithoutMetadata(level.registryAccess());
			int without = count(Fruit.pick(level, at, keeper));
			helper.assertTrue(without > 0, "no berries without research");
			research(helper, Map.of(PokemonProfessor.BERRY_SCIENCE, 1));
			level.setBlockAndUpdate(at, ripe);
			((com.cobblemon.mod.common.block.entity.BerryBlockEntity) level.getBlockEntity(at)).loadWithComponents(yields, level.registryAccess());
			List<ItemStack> with = Fruit.pick(level, at, keeper);
			helper.assertTrue(count(with) == without + 1, "Berry Science: " + count(with) + " berries, " + without + " without");
			helper.assertTrue(with.stream().allMatch(s -> s.is(com.cobblemon.mod.common.CobblemonItems.ORAN_BERRY)), "not all Oran Berries: " + with);
			helper.succeed();
		});
	}

	private static int count(List<ItemStack> stacks) {
		return stacks.stream().mapToInt(ItemStack::getCount).sum();
	}

	/** Evolution Studies: the Professor sells today's evolution stone, one a day, for 8 emeralds' worth. */
	//$ gametest_ticks_batch AREA '200' '"professor_stones"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "professor_stones")
	public void evolutionStudiesSellsAStoneADay(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(5, () -> {
			Villager prof = professor(helper, new BlockPos(13, 2, 13));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			CobbleDollarsBank.add(player, 2000);
			helper.assertTrue(!PokemonProfessor.sellsStones(prof), "sells stones without Evolution Studies");
			helper.assertTrue(key(PokemonProfessor.buyStone(player, prof)).equals("message.aliveworkplace.professor.no_stone"), "sold without the research");
			research(helper, Map.of(PokemonProfessor.EVOLUTION_STUDIES, 1));
			var stone = PokemonProfessor.stoneOfDay(level);
			helper.assertTrue(stone != null && PokemonProfessor.stones().size() >= 8, "the stones: " + PokemonProfessor.stones());
			ChoiceMenu menu = CobblemonProfessor.menuForTest(player, prof);
			helper.assertTrue(menu.icon(CobblemonProfessor.STONE).is(stone), "the stone on the screen: " + menu.icon(CobblemonProfessor.STONE));
			long before = CobbleDollarsBank.balance(player);
			menu.press(CobblemonProfessor.STONE, player);
			helper.assertTrue(player.getInventory().countItem(stone) == 1, "no stone bought");
			helper.assertTrue(CobbleDollarsBank.balance(player) == before - 800, "paid " + (before - CobbleDollarsBank.balance(player)));
			helper.assertTrue(key(PokemonProfessor.buyStone(player, prof)).equals("message.aliveworkplace.professor.stone_sold"), "a second stone today");
			helper.assertTrue(player.getInventory().countItem(stone) == 1, "two stones in a day");
			ModAttachments.PROFESSOR_STONE.set(prof, io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level) - 1);
			helper.assertTrue(key(PokemonProfessor.buyStone(player, prof)).equals("message.aliveworkplace.professor.stone_bought"), "no stone the next day");
			helper.succeed();
		});
	}
}
