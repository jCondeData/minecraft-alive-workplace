package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * QA (lane d, 2026-10-25) for ROADMAP 28.4, 28.5 and 28.6, from their lists of shows: for every job and cue the roadmap
 * names, the Pokémon types whose partners do a show there are exactly the types it names (no type missing, none
 * extra), over all of that cue's show files. The earlier tests check each file's cue and some of its types; this one
 * reads the roadmap's table whole, jobs included (the fire breath is the Armorer's, the Miner's and the Fisherman's).
 */
public class QaPartnerShowTableGameTests implements FabricGameTest {
	static ResourceLocation job(String name) {
		return name.contains(":") ? ResourceLocation.parse(name) : ResourceLocation.fromNamespaceAndPath("aliveworkplace", name);
	}

	/** {job, cue, types...} as the roadmap lists them; vanilla jobs carry their namespace. */
	static final String[][] TABLE = {
		// 28.4: building and the land
		{"builder", "fetch", "fighting", "rock", "steel"},
		{"builder", "place", "fighting"},
		{"porter", "haul", "fighting", "normal"},
		{"carpenter", "craft", "fighting", "rock", "steel"},
		{"minecraft:mason", "craft", "fighting", "rock", "steel"},
		{"minecraft:farmer", "tend", "water", "grass"},
		{"minecraft:farmer", "till", "ground"},
		{"lumberjack", "chop", "fighting"},
		{"lumberjack", "replant", "grass", "bug"},
		{"orchard_keeper", "pick", "flying", "bug"},
		// 28.5: post, forge and kitchen
		{"postman", "air_mail", "flying"},
		{"postman", "deliver", "flying"},
		{"minecraft:armorer", "fire_smelt", "fire"},
		{"miner", "fire_smelt", "fire"},
		{"minecraft:fisherman", "fire_smelt", "fire"},
		{"chef", "cook", "fire", "normal"},
		{"minecraft:toolsmith", "forge", "steel", "fire"},
		{"ball_smith", "forge", "steel", "fire"},
		{"minecraft:weaponsmith", "mend", "steel", "fighting"},
		{"minecraft:fletcher", "fletch", "flying", "bug"},
		{"tinkerer", "tinker", "electric", "steel"},
		// 28.6: everyone else
		{"miner", "dig", "ground", "rock", "steel"},
		{"minecraft:fisherman", "cast", "water", "ice"},
		{"scholar", "study", "psychic"},
		{"teacher", "lesson", "psychic", "normal"},
		{"nurse", "cure", "fairy", "normal", "psychic"},
		{"composter", "compost", "poison", "grass"},
		{"florist", "grow", "grass", "fairy"},
		{"beekeeper", "harvest", "bug", "grass"},
		{"sifter", "sift", "ground", "rock"},
		{"netherworker", "depart", "fire", "dark"},
		{"minecraft:cartographer", "set_out", "flying", "ground"},
		{"rancher", "tame", "normal", "ground"},
	};

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyJobAndCueHasExactlyTheRoadmapsTypes(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (String[] row : TABLE) {
			ResourceLocation job = job(row[0]);
			String cue = row[1];
			Set<String> want = new TreeSet<>(List.of(row).subList(2, row.length));
			Set<String> got = new TreeSet<>();
			for (PartnerShows.Show show : PartnerShows.shows()) {
				if (show.cue().equals(cue) && show.jobs().contains(job)) {
					got.addAll(show.types());
				}
			}
			if (!got.equals(want)) {
				wrong.add(job + " at " + cue + ": " + got + ", not " + want);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "shows that don't match the roadmap: " + wrong);
		helper.succeed();
	}
}
