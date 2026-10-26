package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.MegaShrines;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA of ROADMAP 38.7 (the Mega Shrine) without Mega Showdown, written from the spec: the show's sentences read right with
 * a real name, the +3 is exactly what the villager's mood gains (once, with its reason) and lasts as long as a festival's
 * good mood from both sides, and a finished shrine holds no show while Mega Showdown is missing.
 */
public class QaMegaShrineGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static int showReasons(Moods.Mood mood) {
		int n = 0;
		for (Component c : mood.good()) {
			if (c.getContents() instanceof TranslatableContents t && t.getKey().equals("mood.aliveworkplace.reason.mega_show")) {
				n++;
			}
		}
		return n;
	}

	/** The chat line names the trainer and reads as one sentence; the mood's reason reads as the other reasons do. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaTheShowsLinesReadRight(GameTestHelper helper) {
		String line = Component.translatable("message.aliveworkplace.mega_shrine.show", Component.literal("Ava")).getString();
		helper.assertTrue(line.equals("Ava Mega Evolves their ace at the Mega Shrine, and the village cheers!"), "the show's line reads: " + line);
		String reason = Component.translatable("mood.aliveworkplace.reason.mega_show").getString();
		helper.assertTrue(reason.equals("watched a Mega Evolution at the shrine"), "the mood's reason reads: " + reason);
		String name = Component.translatable("blueprint.aliveworkplace.mega_shrine").getString();
		helper.assertTrue(name.equals("Mega Shrine"), "the blueprint's name reads: " + name);
		helper.succeed();
	}

	/**
	 * The villager's own mood (what the player reads) is exactly 3 higher with the show watched, with one "watched a Mega
	 * Evolution" reason; still on the festival mood's last day, gone the day after.
	 */
	//$ gametest_batch AREA '"qaMegaShrineMood"'
	@GameTest(template = AREA, batch = "qaMegaShrineMood")
	public void qaTheShowAddsExactlyThreeToTheMoodForAsLongAsAFestival(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		v.setNoAi(true);
		long today = Chronicle.day(level);
		Moods.Mood without = Moods.work(level, v);
		helper.assertTrue(showReasons(without) == 0, "a show's reason without a show");
		ModAttachments.MEGA_SHOW_DAY.set(v, today);
		Moods.Mood with = Moods.work(level, v);
		helper.assertTrue(with.score() - without.score() == 3, "the show adds " + (with.score() - without.score()) + ", not 3");
		helper.assertTrue(showReasons(with) == 1, "the show's reason is listed " + showReasons(with) + " times");
		// Read again (as every tick does): no stacking.
		helper.assertTrue(Moods.work(level, v).score() == with.score(), "the show's mood grows as it is read again");
		ModAttachments.MEGA_SHOW_DAY.set(v, today - Festivals.MOOD_DAYS);
		helper.assertTrue(Moods.work(level, v).score() - without.score() == 3, "the show's mood is gone on the festival mood's last day");
		ModAttachments.MEGA_SHOW_DAY.set(v, today - Festivals.MOOD_DAYS - 1);
		Moods.Mood after = Moods.work(level, v);
		helper.assertTrue(after.score() == without.score() && showReasons(after) == 0, "the show's mood outlasts a festival's: " + after.score());
		v.discard();
		helper.succeed();
	}

	/** Without Mega Showdown even a finished shrine with a trainer at its pedestal holds no show and gives nobody its mood. */
	//$ gametest_batch AREA '"qaMegaShrineNoMod"'
	@GameTest(template = AREA, batch = "qaMegaShrineNoMod")
	public void qaAFinishedShrineHoldsNoShowWithoutMegaShowdown(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 1, 5)),
			Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.MEGA_SHRINE.id(), placement, UUID.randomUUID());
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		try {
			VillageHalls.RADIUS = 16;
			level.setDayTime((Chronicle.day(level) - 1) * 24000L + Festivals.START + 100);
			Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 4, 7));
			trainer.setVillagerData(trainer.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(5));
			trainer.setNoAi(true);
			Villager watcher = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 2));
			watcher.setNoAi(true);
			BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1));
			helper.assertTrue(!MegaShrines.tick(level, hall), "the shrine took the festival's crowd without Mega Showdown");
			helper.assertTrue(ModAttachments.MEGA_SHOW_DAY.get(watcher) == null && MegaShrines.mood(level, watcher) == 0,
				"a show was held without Mega Showdown");
		} finally {
			BuildSiteManager.get(level).forgetFinished(placement);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds())) {
			v.discard();
		}
		helper.succeed();
	}
}
