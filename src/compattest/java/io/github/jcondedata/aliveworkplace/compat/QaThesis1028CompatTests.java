package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonThesis;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.ProfessorsThesis;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-d-1028-0109) for ROADMAP 31.21, written from its text: the patron is "the village's best Scholar at
 * Expert or above"; it "starts in a Town ... 6% a day"; the three topics the arc's own tests leave out (the forest's web,
 * sea and shore, living fossils) and the other two's places; every height and time edge from both sides; Thessaly
 * "battles at Master strength ... then leaves" (a loss leaves her there); the ending's +150 reputation; the book's
 * pages (species, place and day, you as co-author); the thesis and Thessaly through a save; and every sentence the
 * player reads, rendered with its real names.
 */
public class QaThesis1028CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	/** A Town with an Expert Scholar, Alder, and nothing started yet; undone when the test ends. */
	private static BlockPos town(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		entity.setRank(VillageRanks.Rank.TOWN);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.stopAll(level, hall);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			ProfessorsThesis.FORCE_TOPIC = null;
			Arcs.stopAll(level, hall);
			level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
		});
		return hall;
	}

	private static Villager scholar(GameTestHelper helper, BlockPos at, String name, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.SCHOLAR).setLevel(level));
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static Arcs.Arc arc() {
		return Arcs.get(ProfessorsThesis.ARC).orElseThrow(() -> new GameTestAssertException("professors_thesis.json didn't load with Cobblemon"));
	}

	/** The thesis on {@code topic} started in a Town with the Expert Scholar Alder. */
	static ArcState start(GameTestHelper helper, String topic) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = town(helper);
		scholar(helper, new BlockPos(5, 2, 5), "Alder", 4);
		ProfessorsThesis.FORCE_TOPIC = topic;
		ArcState s = Arcs.start(level, hall, arc());
		helper.assertTrue(s != null && s.chapter == 0 && s.quests.size() == 3, "the thesis didn't post three catches");
		return s;
	}

	private static ArcState restart(GameTestHelper helper, String topic) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.stopAll(level, hall);
		ProfessorsThesis.FORCE_TOPIC = topic;
		ArcState s = Arcs.start(level, hall, arc());
		helper.assertTrue(s != null && s.quests.size() == 3, topic + " didn't start");
		return s;
	}

	static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static Pokemon mon(String species) {
		return PokemonProperties.Companion.parse(species + " level=20", " ", "=").create();
	}

	private static ProfessorsThesis.CatchInfo info(GameTestHelper helper, Pokemon pokemon, ResourceKey<Biome> biome, int y, long dayTime, int moon, boolean fossil) {
		ServerLevel level = helper.getLevel();
		ProfessorsThesis.CatchInfo real = CobblemonThesis.info(level, pokemon, helper.absolutePos(new BlockPos(6, 2, 6)), fossil);
		return new ProfessorsThesis.CatchInfo(real.uuid(), real.species(), real.name(), real.types(), real.pos().atY(y),
			level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome), dayTime, moon, real.day(), fossil);
	}

	private static boolean caught(GameTestHelper helper, ServerPlayer player, String species, ResourceKey<Biome> biome, int y, long dayTime, int moon) {
		return ProfessorsThesis.onCatch(helper.getLevel(), player, info(helper, mon(species), biome, y, dayTime, moon, false));
	}

	private static boolean caught(GameTestHelper helper, ServerPlayer player, String species, ResourceKey<Biome> biome) {
		return caught(helper, player, species, biome, 70, 1000, 0);
	}

	/**
	 * The patron and the trigger: a Journeyman Scholar (level 3) is no patron and the thesis can't start; an Expert is;
	 * with a Master there too, the Master is ("the best"); a Village (below Town) with an Expert doesn't meet the trigger;
	 * the chance is 6% a day; the patron is "Professor <name>" and the request names the topic.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaThesisPatron"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaThesisPatron")
	public void thePatronAndTheTriggerFromBothSides(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = town(helper);
		Arcs.Arc arc = arc();
		helper.assertTrue(Math.abs(arc.chance() - 0.06f) < 1e-6, "the chance is " + arc.chance() + ", not 6% a day");
		Villager journeyman = scholar(helper, new BlockPos(5, 2, 5), "Juno", 3);
		helper.assertTrue(ProfessorsThesis.patron(level, hall) == null, "a Journeyman Scholar is the patron");
		helper.assertTrue(!arc.conditions().stream().allMatch(c -> c.met(level, hall)), "the trigger is met with only a Journeyman Scholar");
		Villager expert = scholar(helper, new BlockPos(6, 2, 5), "Alder", 4);
		helper.assertTrue(ProfessorsThesis.patron(level, hall) == expert, "the Expert Scholar isn't the patron");
		helper.assertTrue(arc.conditions().stream().allMatch(c -> c.met(level, hall)), "a Town with an Expert Scholar doesn't meet the trigger");
		Villager master = scholar(helper, new BlockPos(7, 2, 5), "Rowan", 5);
		helper.assertTrue(ProfessorsThesis.patron(level, hall) == master, "the best Scholar (the Master) isn't the patron: "
			+ (ProfessorsThesis.patron(level, hall) == null ? null : ProfessorsThesis.patron(level, hall).getCustomName().getString()));
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.VILLAGE);
		helper.assertTrue(!arc.conditions().stream().allMatch(c -> c.met(level, hall)), "a Village (below Town) meets the trigger");
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		QaDenHelperCompatTests.Talker talker = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(5, 2, 7)), 0);
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(talker.player()));
		ProfessorsThesis.FORCE_TOPIC = "forest";
		ArcState s = Arcs.start(level, hall, arc);
		helper.assertTrue(s != null && s.roles.get(ProfessorsThesis.PROFESSOR).name().getString().equals("Professor Rowan"),
			"the patron is " + (s == null ? null : s.roles.get(ProfessorsThesis.PROFESSOR).name().getString()));
		String request = "Professor Rowan: \"I'm writing my thesis on The Forest's Web. Will you help me with the field work?\"";
		helper.assertTrue(talker.said().contains(request), "the player read " + talker.said() + ", not " + request);
		List<String> chronicle = ((VillageHallBlockEntity) level.getBlockEntity(hall)).chronicle().stream().map(e -> e.text().getString()).toList();
		helper.assertTrue(chronicle.contains("Professor Rowan began a thesis: The Forest's Web."), "the chronicle: " + chronicle);
		helper.assertTrue(journeyman.isAlive(), "setup");
		helper.succeed();
	}

	/**
	 * The three topics no other test plays: the Forest's Web (a Bug type in a jungle, a Grass type in a forest, a Flying
	 * type in a meadow or cherry grove), Sea and Shore (a Water type in a warm ocean, a deep ocean, on a beach) and Living
	 * Fossils (a fossil revived, a Dragon type, a Rock type in badlands), each with its near misses refused.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaThesisTopics"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaThesisTopics")
	public void theForestTheSeaAndTheFossils(GameTestHelper helper) {
		ArcState s = start(helper, "forest");
		ServerPlayer player = player(helper);
		helper.assertTrue(!caught(helper, player, "caterpie", Biomes.PLAINS), "a Bug type in the plains counted");
		helper.assertTrue(caught(helper, player, "caterpie", Biomes.BAMBOO_JUNGLE), "a Bug type in a bamboo jungle didn't count");
		helper.assertTrue(!caught(helper, player, "pidgey", Biomes.PLAINS), "a Flying type in the plains counted");
		helper.assertTrue(caught(helper, player, "pidgey", Biomes.CHERRY_GROVE), "a Flying type in a cherry grove didn't count");
		helper.assertTrue(caught(helper, player, "oddish", Biomes.BIRCH_FOREST), "a Grass type in a birch forest didn't count");
		helper.assertTrue(s.chapter == 1, "the forest's three catches didn't end Field Notes: chapter " + (s.chapter + 1));

		s = restart(helper, "sea");
		helper.assertTrue(!caught(helper, player, "magikarp", Biomes.OCEAN), "a Water type in a plain ocean counted");
		helper.assertTrue(!caught(helper, player, "charmander", Biomes.BEACH), "a Fire type on a beach counted");
		helper.assertTrue(caught(helper, player, "magikarp", Biomes.BEACH), "a Water type on a beach didn't count");
		helper.assertTrue(caught(helper, player, "magikarp", Biomes.DEEP_COLD_OCEAN), "a Water type in a deep cold ocean didn't count");
		helper.assertTrue(caught(helper, player, "magikarp", Biomes.WARM_OCEAN), "a Water type in a warm ocean didn't count");
		helper.assertTrue(s.chapter == 1, "the sea's three catches didn't end Field Notes");

		s = restart(helper, "fossils");
		helper.assertTrue(!caught(helper, player, "omanyte", Biomes.PLAINS), "a wild Omanyte (not revived) in the plains counted");
		helper.assertTrue(!caught(helper, player, "geodude", Biomes.PLAINS), "a Rock type outside the badlands counted");
		helper.assertTrue(ProfessorsThesis.onCatch(helper.getLevel(), player, info(helper, mon("omanyte"), Biomes.PLAINS, 70, 1000, 0, true)),
			"a fossil revived by a Fossil Scientist didn't count");
		helper.assertTrue(caught(helper, player, "dratini", Biomes.PLAINS), "a Dragon type didn't count");
		helper.assertTrue(caught(helper, player, "geodude", Biomes.WOODED_BADLANDS), "a Rock type in the wooded badlands didn't count");
		helper.assertTrue(s.chapter == 1, "the fossils' three catches didn't end Field Notes");
		helper.succeed();
	}

	/**
	 * Every height and time edge from both sides: above y=150 (151 yes), below y=0 (0 no, -1 yes); night from 13000 to
	 * 23000 (12999 and 23000 no, 13000 yes, 22999 on a later day yes); a full moon (phase 0) only at night; Fire and
	 * Stone's places (a Fire type in a desert, a Rock type in stony peaks, not in windswept hills).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaThesisEdges"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaThesisEdges")
	public void heightAndTimeEdgesFromBothSides(GameTestHelper helper) {
		ArcState s = start(helper, "cold");
		ServerPlayer player = player(helper);
		helper.assertTrue(caught(helper, player, "pidgey", Biomes.PLAINS, 151, 1000, 0), "a catch at y=151 isn't above y=150");
		s = restart(helper, "fire");
		helper.assertTrue(!caught(helper, player, "sandshrew", Biomes.PLAINS, 0, 1000, 0), "a Ground type at y=0 counted as below y=0");
		helper.assertTrue(caught(helper, player, "sandshrew", Biomes.PLAINS, -1, 1000, 0), "a Ground type at y=-1 didn't count");
		helper.assertTrue(!caught(helper, player, "geodude", Biomes.WINDSWEPT_HILLS), "a Rock type in windswept hills counted as peaks");
		helper.assertTrue(caught(helper, player, "geodude", Biomes.STONY_PEAKS), "a Rock type in stony peaks didn't count");
		helper.assertTrue(!caught(helper, player, "charmander", Biomes.SAVANNA), "a Fire type in a savanna counted");
		helper.assertTrue(caught(helper, player, "charmander", Biomes.DESERT), "a Fire type in a desert didn't count");
		helper.assertTrue(s.chapter == 1, "Fire and Stone didn't end Field Notes");
		s = restart(helper, "night");
		helper.assertTrue(!caught(helper, player, "poochyena", Biomes.PLAINS, 70, 12999, 3), "a Dark type at 12999 (dusk, not night) counted");
		helper.assertTrue(caught(helper, player, "poochyena", Biomes.PLAINS, 70, 13000, 3), "a Dark type at 13000 (night) didn't count");
		helper.assertTrue(!caught(helper, player, "gastly", Biomes.PLAINS, 70, 23000, 3), "a Ghost type at 23000 (dawn) counted");
		helper.assertTrue(caught(helper, player, "gastly", Biomes.PLAINS, 70, 24000L * 7 + 22999, 3), "a Ghost type at 22999 on day 7 didn't count");
		helper.assertTrue(!caught(helper, player, "abra", Biomes.PLAINS, 70, 6000, 0), "a Psychic type by day under a 'full moon' counted");
		helper.assertTrue(!caught(helper, player, "abra", Biomes.PLAINS, 70, 18000, 1), "a Psychic type under a waning moon (phase 1) counted");
		helper.assertTrue(caught(helper, player, "abra", Biomes.PLAINS, 70, 18000, 0), "a Psychic type on a full-moon night didn't count");
		helper.assertTrue(s.chapter == 1, "the night's three catches didn't end Field Notes");
		helper.succeed();
	}

	static void fieldNotes(GameTestHelper helper, ServerPlayer player) {
		helper.assertTrue(caught(helper, player, "snorunt", Biomes.SNOWY_TAIGA), "the Ice catch didn't count");
		helper.assertTrue(caught(helper, player, "squirtle", Biomes.FROZEN_OCEAN, 62, 1000, 0), "the Water catch didn't count");
		helper.assertTrue(caught(helper, player, "pidgey", Biomes.FROZEN_PEAKS, 180, 1000, 0), "the high catch didn't count");
	}

	/**
	 * What the player reads, start to end, with the real names: each catch's line ("Snorunt goes in the field notes: Catch
	 * an Ice type in a snowy biome."), the professor's "Not quite ..." with 200, Thessaly's arrival and parting words,
	 * the quest line for her, the published line; a loss to Thessaly leaves her at the hall and the thesis open; the win
	 * pays +150 reputation (at least) and the chronicle has the publication.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaThesisWords"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaThesisWords")
	public void everySentenceTheCoAuthorReads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = town(helper);
		Villager alder = scholar(helper, new BlockPos(5, 2, 5), "Alder", 4);
		QaDenHelperCompatTests.Talker talker = QaDenHelperCompatTests.talker(helper, helper.absolutePos(new BlockPos(5, 2, 7)), 0);
		ServerPlayer player = talker.player();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		ProfessorsThesis.FORCE_TOPIC = "cold";
		ArcState s = Arcs.start(level, hall, arc());
		helper.assertTrue(s != null, "the thesis didn't start");
		fieldNotes(helper, player);
		List<String> said = talker.said();
		for (String line : List.of("Snorunt goes in the field notes: Catch an Ice type in a snowy biome.",
			"Squirtle goes in the field notes: Catch a Water type in a frozen ocean or river.",
			"Pidgey goes in the field notes: Catch any Pokémon above y=150.")) {
			helper.assertTrue(said.contains(line), "missing '" + line + "' in " + said);
		}
		helper.assertTrue(s.chapter == 1, "Field Notes didn't end");
		helper.assertTrue(ProfessorsThesis.show(level, player, alder), "the professor didn't look");
		String notQuite = "Professor Alder: \"Not quite. Show me one of the Pokémon you caught for me after it has evolved, or one with friendship of 200 or more.\"";
		helper.assertTrue(talker.last().equals(notQuite), "the professor said " + talker.last());
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2, "not at Peer Review: chapter " + (s.chapter + 1));
		Villager thessaly = ProfessorsThesis.thessaly(level, s);
		helper.assertTrue(thessaly != null, "Thessaly didn't come");
		String arrives = "Thessaly: \"I am Thessaly, and I've read your professor's draft. Beat me in battle and I'll admit it holds up.\"";
		helper.assertTrue(talker.said().contains(arrives), "Thessaly's arrival: " + talker.said());
		String quest = io.github.jcondedata.aliveworkplace.story.Stories.open(level, hall).stream().filter(q -> s.id.equals(q.arc)).findFirst().orElseThrow()
			.objectives.get(0).line().getString();
		helper.assertTrue(quest.equals("Beat the rival scholar Thessaly at the hall"), "the quest says " + quest);
		// a loss: she stays, the thesis stays open
		Trainers.battleOver(level.getServer(), thessaly.getUUID(), player.getUUID(), false);
		helper.assertTrue(!thessaly.isRemoved() && Arcs.running(level, hall) == s, "a loss to Thessaly ended the review or sent her off");
		int before = Reputation.points(level, hall, player.getUUID());
		Trainers.battleOver(level.getServer(), thessaly.getUUID(), player.getUUID(), true);
		helper.assertTrue(thessaly.isRemoved() && Arcs.running(level, hall) == null, "the win didn't end the thesis");
		helper.assertTrue(talker.said().contains("Thessaly: \"Fine. The thesis stands. I'll say so in my review.\""), "Thessaly's parting words: " + talker.said());
		String published = "Professor Alder's thesis \"Life in the Cold\" is published, with you as co-author!";
		helper.assertTrue(talker.said().contains(published), "the published line: " + talker.said());
		int after = Reputation.points(level, hall, player.getUUID());
		helper.assertTrue(after - before >= 150, "the ending paid " + (after - before) + " reputation, not +150");
		List<String> chronicle = ((VillageHallBlockEntity) level.getBlockEntity(hall)).chronicle().stream().map(e -> e.text().getString()).toList();
		helper.assertTrue(chronicle.contains("Professor Alder published the thesis \"Life in the Cold\"."), "the chronicle: " + chronicle);
		helper.assertTrue(chronicle.stream().anyMatch(l -> l.startsWith("The thesis taught the scholars a level of ")), "no research line: " + chronicle);
		helper.assertTrue(talker.said().stream().noneMatch(l -> l.contains("%s") || l.contains("aliveworkplace.")), "an unrendered line: " + talker.said());
		helper.succeed();
	}

	/**
	 * The book's pages, as the co-author reads them: the cover (topic, "A thesis by Professor Alder", "Co-author: <you>"),
	 * one page per catch with species, the biome's name, the place and the day, and the end page.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaThesisBook"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaThesisBook")
	public void theBooksPagesNameSpeciesPlaceAndDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ArcState s = start(helper, "cold");
		ServerPlayer player = player(helper);
		String me = player.getGameProfile().getName();
		fieldNotes(helper, player);
		long day = Chronicle.day(level);
		BlockPos at = helper.absolutePos(new BlockPos(6, 2, 6));
		ItemStack book = ProfessorsThesis.book(level, s, Component.literal("Professor Alder"), ProfessorsThesis.topic("cold").orElseThrow(), me);
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		List<String> pages = content.pages().stream().map(p -> p.raw().getString()).toList();
		helper.assertTrue(pages.size() == 5, "pages: " + pages.size());
		helper.assertTrue(pages.get(0).equals("Life in the Cold\n\nA thesis by Professor Alder\n\nCo-author: " + me), "the cover: " + pages.get(0));
		String first = "Snorunt\n\nCaught in Snowy Taiga\nat " + at.getX() + ", 70, " + at.getZ() + "\non day " + day + "\nby " + me;
		helper.assertTrue(pages.get(1).equals(first), "the first catch's page: " + pages.get(1) + " (expected " + first + ")");
		helper.assertTrue(pages.get(2).startsWith("Squirtle\n\nCaught in Frozen Ocean\n") && pages.get(3).startsWith("Pidgey\n\nCaught in Frozen Peaks\nat "
			+ at.getX() + ", 180, "), "the other catches' pages: " + pages.subList(2, 4));
		helper.assertTrue(pages.get(4).equals("With thanks to everyone who helped in the field.\n\nProfessor Alder"), "the end page: " + pages.get(4));
		helper.assertTrue(book.getHoverName().getString().equals("Professor Alder: Life in the Cold") && content.author().equals("Professor Alder"),
			"the title and author: " + book.getHoverName().getString() + " / " + content.author());
		helper.succeed();
	}

	/**
	 * Save and reload: the thesis' state saved and loaded keeps its topic, its catches (species, place, day, catcher) and
	 * Thessaly; Thessaly saved and loaded back (her chunk reloaded) is still Thessaly, a Master trainer, and beating her
	 * then ends the thesis.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaThesisReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaThesisReload")
	public void theThesisAndThessalySurviveASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ArcState s = start(helper, "cold");
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = player(helper);
		fieldNotes(helper, player);
		Arcs.next(level, hall);
		Villager thessaly = ProfessorsThesis.thessaly(level, s);
		helper.assertTrue(s.chapter == 2 && thessaly != null, "setup: Peer Review with Thessaly");
		ArcState loaded = ArcState.load(s.save());
		helper.assertTrue(loaded.chapter == 2 && ProfessorsThesis.catches(loaded).equals(ProfessorsThesis.catches(s)),
			"the catches after a save: " + ProfessorsThesis.catches(loaded) + " vs " + ProfessorsThesis.catches(s));
		helper.assertTrue(ProfessorsThesis.thessaly(level, loaded) == thessaly, "Thessaly isn't found from the saved thesis");
		helper.assertTrue(loaded.roles.get(ProfessorsThesis.PROFESSOR).name().getString().equals("Professor Alder"), "the patron after a save");
		CompoundTag tag = new CompoundTag();
		thessaly.save(tag);
		UUID id = thessaly.getUUID();
		thessaly.discard();
		Villager back = EntityType.VILLAGER.create(level);
		back.load(tag);
		level.addFreshEntity(back);
		helper.assertTrue(back.getUUID().equals(id) && ProfessorsThesis.thessaly(level, s) == back, "the reloaded Thessaly isn't the thesis' Thessaly");
		helper.assertTrue(back.getCustomName() != null && back.getCustomName().getString().equals("Thessaly") && Trainers.tier(back) == 5,
			"after a reload she's " + (back.getCustomName() == null ? null : back.getCustomName().getString()) + " at tier " + Trainers.tier(back));
		Trainers.battleOver(level.getServer(), back.getUUID(), player.getUUID(), true);
		helper.assertTrue(back.isRemoved() && Arcs.running(level, hall) == null, "beating the reloaded Thessaly didn't end the thesis");
		helper.assertTrue(Optional.ofNullable(player.getInventory()).map(i -> i.countItem(Items.WRITTEN_BOOK)).orElse(0) == 1, "no book after the reload");
		helper.succeed();
	}
}
