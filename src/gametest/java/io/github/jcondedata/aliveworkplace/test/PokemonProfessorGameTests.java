package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.PokemonCensus;
import io.github.jcondedata.aliveworkplace.legend.PokemonProfessor;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.io.Reader;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * 29.21 without Cobblemon: the Pokémon Professor's file and the Pokédex tree don't load, nothing errors, the condition
 * {@code pastured_pokemon} reads and counts nothing, the hall shows no Pokédex, and the village Pokédex saves (an old
 * hall loads with an empty one). Also the judge's words for IVs and EVs, at every boundary.
 */
public class PokemonProfessorGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(4, 2, 4);

	//$ gametest AREA
	@GameTest(template = AREA)
	public void withoutCobblemonNothingLoadsOrErrors(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cobblemon"), "Cobblemon is installed in the plain suite");
		helper.assertTrue(!PokemonCensus.EXTENSION.present(), "the census is filled without Cobblemon");
		helper.assertTrue(Legends.get(PokemonProfessor.ID).isEmpty(), "the Professor loaded without Cobblemon");
		helper.assertTrue(ResearchTrees.get(PokemonProfessor.TREE).isEmpty(), "the Pokédex tree loaded without Cobblemon");
		// The file itself reads as "needs a mod that isn't here", not as an error
		try (Reader reader = level.getServer().getResourceManager().getResourceOrThrow(AliveWorkplace.id("legends/pokemon_professor.json")).openAsReader()) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			helper.assertTrue(Legends.read(PokemonProfessor.ID, json) == null, "the file read as a Legend without Cobblemon");
		} catch (java.io.IOException e) {
			throw new AssertionError("pokemon_professor.json isn't in the jar", e);
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		JsonObject json = JsonParser.parseString("{\"type\": \"pastured_pokemon\", \"count\": 25, \"types\": 10}").getAsJsonObject();
		Condition condition = Conditions.parse(json);
		Condition.Progress progress = condition.progress(level, hall);
		helper.assertTrue(!progress.met() && progress.have() == 0 && progress.need() == 25, "progress: " + progress.have() + " of " + progress.need());
		helper.assertTrue(progress.line().getString().equals("Pokémon in the village's Pasture Blocks: 0 of 25, of 0 of 10 types"), "line: " + progress.line().getString());
		helper.assertTrue(PokemonCensus.of(level, hall) == PokemonCensus.Count.EMPTY, "a census without Cobblemon");
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		PokemonProfessor.round(level, hall, entity);
		helper.assertTrue(entity.pokedex().isEmpty(), "logged without Cobblemon");
		ChoiceMenu menu = ChoiceMenu.detached(helper.makeMockServerPlayerInLevel(), m -> PokemonProfessor.button(m, level, hall));
		helper.assertTrue(menu.icon(PokemonProfessor.POKEDEX_SLOT).isEmpty(), "a Pokédex on the hall without Cobblemon");
		helper.assertTrue(ResearchTrees.count(level, hall, PokemonProfessor.COUNTER) == 0, "the counter isn't 0");
		helper.assertTrue(PokemonProfessor.stoneOfDay(level) == null, "a stone without Cobblemon");
		// The Pokédex saves; an old hall (no "pokedex") loads with an empty one
		CompoundTag old = entity.saveWithoutMetadata(level.registryAccess());
		helper.assertTrue(!old.contains("pokedex"), "an empty Pokédex is written");
		List<String> added = entity.logPokedex(List.of("cobblemon:pikachu", "cobblemon:eevee", "cobblemon:pikachu"));
		helper.assertTrue(added.equals(List.of("cobblemon:pikachu", "cobblemon:eevee")), "logged: " + added);
		helper.assertTrue(entity.logPokedex(List.of("cobblemon:eevee")).isEmpty(), "Eevee logged twice");
		CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
		VillageHallBlockEntity other = new VillageHallBlockEntity(hall, entity.getBlockState());
		other.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(List.copyOf(other.pokedex()).equals(List.of("cobblemon:pikachu", "cobblemon:eevee")), "after a save: " + other.pokedex());
		VillageHallBlockEntity fresh = new VillageHallBlockEntity(hall, entity.getBlockState());
		fresh.loadWithComponents(old, level.registryAccess());
		helper.assertTrue(fresh.pokedex().isEmpty(), "an old hall: " + fresh.pokedex());
		helper.assertTrue(ResearchTrees.count(level, hall, PokemonProfessor.COUNTER) == 2, "the counter: " + ResearchTrees.count(level, hall, PokemonProfessor.COUNTER));
		level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	/** The judge's words: 0 No good, 1-15 Decent, 16-25 Pretty good, 26-29 Very good, 30 Fantastic, 31 Best; EVs none to full. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void ivsAndEvsInWords(GameTestHelper helper) {
		int[] ivs = {0, 1, 15, 16, 25, 26, 29, 30, 31};
		String[] words = {"No good", "Decent", "Decent", "Pretty good", "Pretty good", "Very good", "Very good", "Fantastic", "Best"};
		for (int i = 0; i < ivs.length; i++) {
			String said = PokemonProfessor.ivWord(ivs[i]).getString();
			helper.assertTrue(said.equals(words[i]), "IV " + ivs[i] + ": " + said);
		}
		int[] evs = {0, 1, 63, 64, 127, 128, 251, 252};
		String[] evWords = {"no EVs", "a few EVs", "a few EVs", "some EVs", "some EVs", "a lot of EVs", "a lot of EVs", "full EVs"};
		for (int i = 0; i < evs.length; i++) {
			String said = PokemonProfessor.evWord(evs[i]).getString();
			helper.assertTrue(said.equals(evWords[i]), "EV " + evs[i] + ": " + said);
		}
		helper.assertTrue(PokemonProfessor.statLine(Component.literal("Speed"), 31, 252, false).getString().equals("Speed: Best, full EVs"), "a stat in words");
		helper.assertTrue(PokemonProfessor.statLine(Component.literal("Speed"), 31, 252, true).getString().equals("Speed: Best (IV 31), EVs 252"), "a stat, exact");
		helper.succeed();
	}
}
