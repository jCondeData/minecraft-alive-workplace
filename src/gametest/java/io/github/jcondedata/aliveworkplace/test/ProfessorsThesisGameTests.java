package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.ProfessorsThesis;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;

/**
 * The Professor's Thesis (ROADMAP 31.21) without Cobblemon: the arc file doesn't load, so it never starts (a Town's
 * rolls never see it) and a catch reported anyway counts for nothing; its objective types still read and write
 * back (a pack may use them), and the catch rules hold on their own. The real catches are in ThesisCompatTests.
 */
public class ProfessorsThesisGameTests implements FabricGameTest {
	private static final BlockPos HALL = new BlockPos(1, 2, 1);

	/** Without Cobblemon the arc isn't loaded, never starts in a Town, and a stray catch counts for nothing. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"thesisWithoutCobblemon"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "thesisWithoutCobblemon")
	public void withoutCobblemonTheArcNeverStarts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Arcs.get(ProfessorsThesis.ARC).isEmpty() && Arcs.find("professors_thesis").isEmpty(), "the thesis loaded without Cobblemon");
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		entity.setRank(VillageRanks.Rank.TOWN);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.stopAll(level, hall);
		helper.assertTrue(Arcs.all().stream().noneMatch(a -> a.id().equals(ProfessorsThesis.ARC)), "the thesis is among the arcs a Town may roll");
		var info = new ProfessorsThesis.CatchInfo(UUID.randomUUID(), ResourceLocation.parse("cobblemon:snorunt"), Component.literal("Snorunt"),
			java.util.List.of("ice"), hall, level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SNOWY_PLAINS), 1000, 0, 1, false);
		helper.assertTrue(!ProfessorsThesis.onCatch(level, helper.makeMockServerPlayerInLevel(), info), "a catch counted with no thesis running");
		level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	/** The six topics ask three catches each; catch, show and battle_leader read and write back the same; the catch rules. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"thesisObjectives"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "thesisObjectives")
	public void theObjectivesReadAndMatch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(ProfessorsThesis.TOPICS.size() == 6 && ProfessorsThesis.TOPICS.stream().allMatch(t -> t.catches().size() == 3), "not six topics of three");
		for (ProfessorsThesis.Topic t : ProfessorsThesis.TOPICS) {
			for (JsonObject c : t.catches()) {
				Objectives.Objective o = Objectives.parse(c);
				helper.assertTrue(o.json().equals(c), "catch doesn't write back: " + c + " vs " + o.json());
				helper.assertTrue(!o.line().getString().startsWith("quest.aliveworkplace"), "no line for " + c);
			}
		}
		for (String s : new String[] {"{\"type\":\"show\",\"friendship\":200}", "{\"type\":\"battle_leader\",\"village\":\"\"}"}) {
			JsonObject j = JsonParser.parseString(s).getAsJsonObject();
			helper.assertTrue(Objectives.parse(j).json().equals(j), s + " doesn't write back");
		}
		var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
		BlockPos at = helper.absolutePos(HALL);
		ProfessorsThesis.Catch rock = (ProfessorsThesis.Catch) Objectives.parse(ProfessorsThesis.topic("fossils").orElseThrow().catches().get(2));
		helper.assertTrue(rock.matches(level, new ProfessorsThesis.CatchInfo(UUID.randomUUID(), ResourceLocation.parse("cobblemon:geodude"), Component.literal("Geodude"),
			java.util.List.of("rock", "ground"), at, biomes.getHolderOrThrow(Biomes.WOODED_BADLANDS), 6000, 0, 1, false)), "a Rock type in wooded badlands");
		helper.assertTrue(!rock.matches(level, new ProfessorsThesis.CatchInfo(UUID.randomUUID(), ResourceLocation.parse("cobblemon:geodude"), Component.literal("Geodude"),
			java.util.List.of("rock", "ground"), at, biomes.getHolderOrThrow(Biomes.DESERT), 6000, 0, 1, false)), "a Rock type in the desert counted as badlands");
		ProfessorsThesis.Catch fossil = (ProfessorsThesis.Catch) Objectives.parse(ProfessorsThesis.topic("fossils").orElseThrow().catches().get(0));
		var plains = biomes.getHolderOrThrow(Biomes.PLAINS);
		helper.assertTrue(!fossil.matches(level, new ProfessorsThesis.CatchInfo(UUID.randomUUID(), ResourceLocation.parse("cobblemon:omanyte"), Component.literal("Omanyte"),
			java.util.List.of("rock", "water"), at, plains, 6000, 0, 1, false)), "a wild catch counted as a revived fossil");
		helper.assertTrue(fossil.matches(level, new ProfessorsThesis.CatchInfo(UUID.randomUUID(), ResourceLocation.parse("cobblemon:omanyte"), Component.literal("Omanyte"),
			java.util.List.of("rock", "water"), at, plains, 6000, 0, 1, true)), "a revived fossil didn't count");
		helper.assertTrue(ProfessorsThesis.rewards().stream().anyMatch(s -> s.is(net.minecraft.world.item.Items.EMERALD) && s.getCount() == 16),
			"without Cobblemon the Rare Candies and the Ability Capsule aren't 16 emeralds: " + ProfessorsThesis.rewards());
		helper.succeed();
	}
}
