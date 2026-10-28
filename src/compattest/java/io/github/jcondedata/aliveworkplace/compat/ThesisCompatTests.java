package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonThesis;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.ProfessorsThesis;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
 * The Professor's Thesis (ROADMAP 31.21) with the real Cobblemon: the arc loads; catches count by type, biome, time and
 * height (and only once each); {@code show} takes an evolved catch or a Pokémon at friendship 200; {@code battle_leader}
 * counts the Trainer Leader of another village, not the thesis' own; Thessaly comes when no village near has a Leader,
 * battles at Master strength and leaves when beaten; the published book lists the catches, and the ending pays.
 */
public class ThesisCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos OTHER = new BlockPos(24, 2, 24);

	/** A Town with an Expert Scholar, Alder; the thesis forced to {@code topic}, started. */
	private static ArcState start(GameTestHelper helper, String topic) {
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		entity.setRank(VillageRanks.Rank.TOWN);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.stopAll(level, hall);
		Villager alder = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		alder.setNoAi(true);
		alder.setVillagerData(alder.getVillagerData().setProfession(ModVillagers.SCHOLAR).setLevel(4));
		alder.setCustomName(Component.literal("Alder"));
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			ProfessorsThesis.FORCE_TOPIC = null;
			Arcs.stopAll(level, hall);
			level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
		});
		helper.assertTrue(ProfessorsThesis.patron(level, hall) == alder, "the Expert Scholar isn't the patron");
		ProfessorsThesis.FORCE_TOPIC = topic;
		Arcs.Arc arc = Arcs.get(ProfessorsThesis.ARC).orElseThrow(() -> new AssertionError("professors_thesis.json didn't load with Cobblemon"));
		helper.assertTrue(arc.conditions().stream().allMatch(c -> c.met(level, hall)), "a Town with an Expert Scholar doesn't meet the trigger");
		ArcState s = Arcs.start(level, hall, arc);
		helper.assertTrue(s != null && s.chapter == 0 && s.quests.size() == 3, "the thesis didn't post three catches: " + (s == null ? null : s.quests));
		helper.assertTrue(s.roles.get(ProfessorsThesis.PROFESSOR).name().getString().equals("Professor Alder"),
			"the patron is called " + s.roles.get(ProfessorsThesis.PROFESSOR).name().getString());
		return s;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static Pokemon mon(String species) {
		return PokemonProperties.Companion.parse(species + " level=20", " ", "=").create();
	}

	/** {@code pokemon}'s catch at {@code y}, in {@code biome}, at {@code dayTime} with the moon at {@code moon}. */
	private static ProfessorsThesis.CatchInfo info(GameTestHelper helper, Pokemon pokemon, ResourceKey<Biome> biome, int y, long dayTime, int moon) {
		ServerLevel level = helper.getLevel();
		ProfessorsThesis.CatchInfo real = CobblemonThesis.info(level, pokemon, helper.absolutePos(new BlockPos(6, 2, 6)), false);
		return new ProfessorsThesis.CatchInfo(real.uuid(), real.species(), real.name(), real.types(), real.pos().atY(y),
			level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome), dayTime, moon, real.day(), false);
	}

	private static boolean caught(GameTestHelper helper, ServerPlayer player, ProfessorsThesis.CatchInfo info) {
		return ProfessorsThesis.onCatch(helper.getLevel(), player, info);
	}

	/** Life in the Cold: an Ice type counts in a snowy biome only, a Water type in a frozen river, anything above y=150; each once. */
	//$ gametest_ticks_batch AREA '200' '"thesis_catch"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "thesis_catch")
	public void catchesCountByTypeBiomeAndHeight(GameTestHelper helper) {
		ArcState s = start(helper, "cold");
		ServerPlayer player = player(helper);
		Pokemon snorunt = mon("snorunt");
		helper.assertTrue(snorunt.getTypes().iterator().next().getName().equalsIgnoreCase("ice"), "Snorunt isn't Ice");
		helper.assertTrue(!caught(helper, player, info(helper, snorunt, Biomes.PLAINS, 70, 1000, 0)), "an Ice type in the plains counted");
		helper.assertTrue(!caught(helper, player, info(helper, mon("charmander"), Biomes.SNOWY_PLAINS, 70, 1000, 0)), "a Fire type in the snow counted");
		helper.assertTrue(caught(helper, player, info(helper, snorunt, Biomes.SNOWY_PLAINS, 70, 1000, 0)), "an Ice type in the snow didn't count");
		helper.assertTrue(!caught(helper, player, info(helper, mon("snover"), Biomes.SNOWY_PLAINS, 70, 1000, 0)), "a second Ice type counted again");
		helper.assertTrue(!caught(helper, player, info(helper, mon("squirtle"), Biomes.RIVER, 62, 1000, 0)), "a Water type in a thawed river counted");
		helper.assertTrue(caught(helper, player, info(helper, mon("squirtle"), Biomes.FROZEN_RIVER, 62, 1000, 0)), "a Water type in a frozen river didn't count");
		helper.assertTrue(!caught(helper, player, info(helper, mon("pidgey"), Biomes.PLAINS, 150, 1000, 0)), "a catch at y=150 counted as above it");
		helper.assertTrue(caught(helper, player, info(helper, mon("pidgey"), Biomes.WINDSWEPT_HILLS, 160, 1000, 0)), "a catch above y=150 didn't count");
		helper.assertTrue(s.chapter == 1 && s.quests.size() == 1, "three catches didn't end Field Notes: chapter " + (s.chapter + 1));
		List<ProfessorsThesis.Caught> notes = ProfessorsThesis.catches(s);
		helper.assertTrue(notes.size() == 3 && notes.get(0).species().getPath().equals("snorunt") && notes.get(0).biome().equals("minecraft:snowy_plains")
			&& notes.get(2).pos().getY() == 160, "the field notes: " + notes);
		helper.succeed();
	}

	/** Creatures of the Night, Fire and Stone: by night (not day), a full moon (not a half), below y=0 (not above). */
	//$ gametest_ticks_batch AREA '200' '"thesis_time"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "thesis_time")
	public void catchesCountByTimeAndDepth(GameTestHelper helper) {
		ArcState s = start(helper, "night");
		ServerPlayer player = player(helper);
		helper.assertTrue(!caught(helper, player, info(helper, mon("poochyena"), Biomes.PLAINS, 70, 6000, 0)), "a Dark type by day counted");
		helper.assertTrue(caught(helper, player, info(helper, mon("poochyena"), Biomes.PLAINS, 70, 18000, 3)), "a Dark type at night didn't count");
		helper.assertTrue(caught(helper, player, info(helper, mon("gastly"), Biomes.PLAINS, 70, 18000, 3)), "a Ghost type at night didn't count");
		helper.assertTrue(!caught(helper, player, info(helper, mon("abra"), Biomes.PLAINS, 70, 18000, 4)), "a Psychic type under a new moon counted");
		helper.assertTrue(caught(helper, player, info(helper, mon("abra"), Biomes.PLAINS, 70, 18000, 0)), "a Psychic type under a full moon didn't count");
		helper.assertTrue(s.chapter == 1, "the night's three catches didn't end Field Notes");
		ServerLevel level = helper.getLevel();
		Arcs.stopAll(level, helper.absolutePos(HALL));
		ProfessorsThesis.FORCE_TOPIC = "fire";
		ArcState fire = Arcs.start(level, helper.absolutePos(HALL), Arcs.get(ProfessorsThesis.ARC).orElseThrow());
		helper.assertTrue(fire != null && fire.quests.size() == 3, "Fire and Stone didn't start");
		helper.assertTrue(!caught(helper, player, info(helper, mon("sandshrew"), Biomes.PLAINS, 5, 6000, 0)), "a Ground type above y=0 counted");
		helper.assertTrue(caught(helper, player, info(helper, mon("sandshrew"), Biomes.PLAINS, -5, 6000, 0)), "a Ground type below y=0 didn't count");
		helper.assertTrue(caught(helper, player, info(helper, mon("charmander"), Biomes.ERODED_BADLANDS, 70, 6000, 0)), "a Fire type in the badlands didn't count");
		helper.succeed();
	}

	/** A Closer Look: nothing to show is refused; one of the catches evolved counts; so does a Pokémon at friendship 200. */
	//$ gametest_ticks_batch AREA '200' '"thesis_show"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "thesis_show")
	public void showAnEvolvedCatchOrAFriend(GameTestHelper helper) {
		ArcState s = start(helper, "night");
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		Pokemon poochyena = mon("poochyena");
		Cobblemon.INSTANCE.getStorage().getParty(player).add(poochyena);
		helper.assertTrue(caught(helper, player, info(helper, poochyena, Biomes.PLAINS, 70, 18000, 3)), "the Dark catch didn't count");
		helper.assertTrue(caught(helper, player, info(helper, mon("gastly"), Biomes.PLAINS, 70, 18000, 3)), "the Ghost catch didn't count");
		helper.assertTrue(caught(helper, player, info(helper, mon("abra"), Biomes.PLAINS, 70, 18000, 0)), "the Psychic catch didn't count");
		helper.assertTrue(s.chapter == 1, "Field Notes didn't end");
		Villager alder = (Villager) level.getEntity(s.roles.get(ProfessorsThesis.PROFESSOR).who().orElseThrow());
		helper.assertTrue(ProfessorsThesis.show(level, player, alder) && s.chapter == 1, "an unevolved catch at low friendship was accepted");
		poochyena.setSpecies(PokemonSpecies.getByName("mightyena"));
		helper.assertTrue(ProfessorsThesis.show(level, player, alder), "the professor didn't look at the party");
		helper.assertTrue(s.chapter == 2, "the evolved catch didn't end A Closer Look");
		// friendship alone: any Pokémon at 200
		Pokemon eevee = mon("eevee");
		eevee.setFriendship(200, true);
		List<ProfessorsThesis.Mon> party = new CobblemonThesis().party(player);
		helper.assertTrue(party.size() == 1 && ProfessorsThesis.shown(s, party, 200) != null, "the evolved catch isn't read from the party");
		helper.assertTrue(ProfessorsThesis.shown(s, List.of(new ProfessorsThesis.Mon(eevee.getUuid(), eevee.getSpecies().getResourceIdentifier(), eevee.getFriendship())), 200) != null,
			"a Pokémon at friendship 200 wasn't accepted");
		helper.assertTrue(ProfessorsThesis.shown(s, List.of(new ProfessorsThesis.Mon(UUID.randomUUID(), eevee.getSpecies().getResourceIdentifier(), 199)), 200) == null,
			"a Pokémon at friendship 199 was accepted");
		helper.succeed();
	}

	/** Peer Review with a Leader near: the other village's Leader counts, the thesis' own village's doesn't. */
	//$ gametest_ticks_batch AREA '200' '"thesis_leader"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "thesis_leader")
	public void beatTheLeaderOfAnotherVillage(GameTestHelper helper) {
		ArcState s = start(helper, "cold");
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.setBlock(OTHER, ModBlocks.VILLAGE_HALL);
		BlockPos other = helper.absolutePos(OTHER);
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(other, Component.literal("Pewter"), List.of());
		Villager brock = helper.spawn(EntityType.VILLAGER, OTHER.offset(1, 0, 1));
		brock.setNoAi(true);
		brock.setVillagerData(brock.getVillagerData().setProfession(ModVillagers.TRAINER_LEADER).setLevel(4));
		Villager own = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 6));
		own.setNoAi(true);
		own.setVillagerData(own.getVillagerData().setProfession(ModVillagers.TRAINER_LEADER).setLevel(4));
		PartnerShowsCompatTests.after(helper, () -> {
			data.remove(other);
			level.setBlockAndUpdate(other, Blocks.AIR.defaultBlockState());
		});
		helper.assertTrue(ProfessorsThesis.leaderVillage(level, hall) != null && ProfessorsThesis.leaderVillage(level, hall).equals(other),
			"Pewter's Leader wasn't found: " + ProfessorsThesis.leaderVillage(level, hall));
		Arcs.next(level, hall);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && ProfessorsThesis.thessaly(level, s) == null, "Thessaly came though Pewter has a Leader");
		String line = Stories.open(level, hall).stream().filter(q -> s.id.equals(q.arc)).findFirst().orElseThrow().objectives.get(0).line().getString();
		helper.assertTrue(line.equals("Beat the Trainer Leader of " + VillageHalls.name(level, other).getString()), "the quest says " + line);
		ServerPlayer player = player(helper);
		helper.assertTrue(!ProfessorsThesis.onTrainerWon(level, own, player), "the thesis' own village's Leader counted");
		helper.assertTrue(ProfessorsThesis.onTrainerWon(level, brock, player), "Pewter's Leader didn't count");
		helper.assertTrue(Arcs.running(level, hall) == null, "the thesis didn't end with Peer Review");
		helper.succeed();
	}

	/**
	 * Peer Review with no Leader near: Thessaly comes to the hall, a Master trainer; beating her (through the trainer
	 * engine's battleOver) ends the arc, she leaves, and the co-author gets the book listing the catches, 3 Rare Candies,
	 * an Ability Capsule, Co-author and +150; the village gets a free research level.
	 */
	//$ gametest_ticks_batch AREA '200' '"thesis_thessaly"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "thesis_thessaly")
	public void thessalyComesAndTheBookIsPublished(GameTestHelper helper) {
		ArcState s = start(helper, "cold");
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = player(helper);
		helper.assertTrue(caught(helper, player, info(helper, mon("snorunt"), Biomes.SNOWY_TAIGA, 70, 1000, 0)), "the Ice catch didn't count");
		helper.assertTrue(caught(helper, player, info(helper, mon("squirtle"), Biomes.FROZEN_OCEAN, 62, 1000, 0)), "the Water catch didn't count");
		helper.assertTrue(caught(helper, player, info(helper, mon("pidgey"), Biomes.FROZEN_PEAKS, 180, 1000, 0)), "the high catch didn't count");
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2, "not at Peer Review: chapter " + (s.chapter + 1));
		Villager thessaly = ProfessorsThesis.thessaly(level, s);
		helper.assertTrue(thessaly != null && thessaly.getCustomName().getString().equals("Thessaly"), "Thessaly didn't come");
		helper.assertTrue(Trainers.isTrainer(thessaly) && Trainers.tier(thessaly) == 5, "Thessaly isn't a Master trainer: tier " + Trainers.tier(thessaly));
		int levels = ((VillageHallBlockEntity) level.getBlockEntity(hall)).research().totalLevels();
		Trainers.battleOver(level.getServer(), thessaly.getUUID(), player.getUUID(), true);
		helper.assertTrue(thessaly.isRemoved(), "Thessaly didn't leave once beaten");
		helper.assertTrue(Arcs.running(level, hall) == null, "the thesis didn't end");
		ItemStack book = null;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(Items.WRITTEN_BOOK)) {
				book = player.getInventory().getItem(i);
			}
		}
		helper.assertTrue(book != null, "no thesis book");
		helper.assertTrue(book.getHoverName().getString().equals("Professor Alder: Life in the Cold"), "the book is called " + book.getHoverName().getString());
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		String pages = String.join("|", content.pages().stream().map(p -> p.raw().getString()).toList());
		helper.assertTrue(pages.contains("Snorunt") && pages.contains("Squirtle") && pages.contains("Pidgey") && pages.contains(player.getGameProfile().getName()),
			"the book doesn't list the catches and the co-author: " + pages);
		helper.assertTrue(content.pages().size() == 5, "pages: " + content.pages().size());
		var candy = BuiltInRegistries.ITEM.get(ProfessorsThesis.RARE_CANDY);
		var capsule = BuiltInRegistries.ITEM.get(ProfessorsThesis.ABILITY_CAPSULE);
		helper.assertTrue(candy != Items.AIR && player.getInventory().countItem(candy) == 3, "not 3 Rare Candies");
		helper.assertTrue(capsule != Items.AIR && player.getInventory().countItem(capsule) == 1, "no Ability Capsule");
		helper.assertTrue(Reputation.honours(level, hall, player.getUUID()).contains("co_author"), "no Co-author honour");
		helper.assertTrue(((VillageHallBlockEntity) level.getBlockEntity(hall)).research().totalLevels() == levels + 1, "no free research level");
		helper.succeed();
	}

	/**
	 * B663: a Thessaly no running thesis remembers (one left by the hall in a world saved before an ended thesis sent
	 * her off) is gone as soon as she loads; the running thesis' own Thessaly stays.
	 */
	//$ gametest_ticks_batch AREA '200' '"thesis_stray"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "thesis_stray")
	public void aStrayThessalyGoesWhenSheLoads(GameTestHelper helper) {
		ArcState s = start(helper, "cold");
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = player(helper);
		helper.assertTrue(caught(helper, player, info(helper, mon("snorunt"), Biomes.SNOWY_TAIGA, 70, 1000, 0)), "the Ice catch didn't count");
		helper.assertTrue(caught(helper, player, info(helper, mon("squirtle"), Biomes.FROZEN_OCEAN, 62, 1000, 0)), "the Water catch didn't count");
		helper.assertTrue(caught(helper, player, info(helper, mon("pidgey"), Biomes.FROZEN_PEAKS, 180, 1000, 0)), "the high catch didn't count");
		Arcs.next(level, hall);
		Villager thessaly = ProfessorsThesis.thessaly(level, s);
		helper.assertTrue(s.chapter == 2 && thessaly != null && thessaly.isAlive(), "the running thesis' Thessaly didn't come or didn't stay");
		Villager stray = EntityType.VILLAGER.create(level);
		BlockPos at = helper.absolutePos(new BlockPos(7, 2, 7));
		stray.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		stray.addTag(ProfessorsThesis.THESSALY_TAG);
		PartnerShowsCompatTests.after(helper, stray::discard);
		level.addFreshEntity(stray);
		helper.assertTrue(stray.isRemoved(), "a Thessaly no thesis remembers stayed when she loaded");
		helper.assertTrue(thessaly.isAlive(), "the running thesis' own Thessaly went too");
		helper.succeed();
	}
}
