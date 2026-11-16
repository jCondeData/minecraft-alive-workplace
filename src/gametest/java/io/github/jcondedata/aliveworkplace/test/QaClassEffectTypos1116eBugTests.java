package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * QA (lane E, 2026-11-16), the class files from a datapack author's side (wiki classes.md: "A data pack replaces one,
 * adds its own or switches ours off"). A class file with a typo in one effect's {@code type} or in a {@code jobs} entry
 * of an effect (a capital letter, as a person writing JSON by hand would) is loaded whole: the effect's strings are kept
 * as they are and read later, for every villager with a class (ClassPerks.pace, the wellbeing, the market, the
 * caravans). Expected: the bad effect is skipped (or the file, with the 'Skipping class' warning) and every other
 * effect still works; never an exception while villagers work.
 */
public class QaClassEffectTypos1116eBugTests {
	private static final String BATCH = "qaClassEffectTypos1116e";

	/** The server's class files with the peasant's (the floor, under every class) given one extra effect. */
	private static void loadWithPeasantEffect(ServerLevel level, String effect) {
		Map<ResourceLocation, JsonElement> files = new TreeMap<>(SocialClasses.files(level.getServer().getResourceManager()));
		ResourceLocation peasant = ResourceLocation.parse("aliveworkplace:peasant");
		helper(files.containsKey(peasant), "setup: no peasant class file");
		var o = files.get(peasant).deepCopy().getAsJsonObject();
		var effects = o.has("effects") ? o.getAsJsonArray("effects") : new com.google.gson.JsonArray();
		effects.add(JsonParser.parseString(effect));
		o.add("effects", effects);
		files.put(peasant, o);
		SocialClasses.load(files);
		ClassPerks.forget();
	}

	private static void helper(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}

	private static void restore(ServerLevel level) {
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		SocialClasses.forget();
		ClassPerks.forget();
	}

	private static void check(GameTestHelper helper, String what, String effect) {
		ServerLevel level = helper.getLevel();
		boolean enabled = SocialClasses.ENABLED;
		try {
			SocialClasses.ENABLED = true;
			loadWithPeasantEffect(level, effect);
			helper.assertTrue(SocialClasses.get(ResourceLocation.parse("aliveworkplace:artisan")) != null, "setup: the artisan class didn't load");
			Villager v = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 2, 2));
			v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.MASON));
			SocialClasses.seed(v, SocialClasses.get(ResourceLocation.parse("aliveworkplace:artisan")));
			float pace;
			try {
				pace = ClassPerks.pace(v);
			} catch (RuntimeException e) {
				throw new AssertionError("a class file with " + what + " makes an artisan mason's work pace throw: " + e, e);
			}
			// the artisan's own work_pace (10% for masons) still counts
			helper.assertTrue(pace < 1f, "with " + what + " the artisan mason lost his work pace bonus (pace " + pace + ")");
		} finally {
			SocialClasses.ENABLED = enabled;
			restore(level);
		}
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' 'BATCH'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
	public void aCapitalLetterInAnEffectsTypeBreaksNoVillagersWork(GameTestHelper helper) {
		check(helper, "a capital in an effect's type", "{\"type\": \"aliveworkplace:Wellbeing\", \"percent\": 3}");
		helper.succeed();
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' 'BATCH'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
	public void aCapitalLetterInAWorkPacesJobBreaksNoVillagersWork(GameTestHelper helper) {
		check(helper, "a capital in a work_pace job", "{\"type\": \"aliveworkplace:work_pace\", \"percent\": 5, \"jobs\": [\"minecraft:Mason\"]}");
		helper.succeed();
	}
}
