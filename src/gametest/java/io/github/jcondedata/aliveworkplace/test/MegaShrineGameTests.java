package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import io.github.jcondedata.aliveworkplace.trainer.MegaShrines;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * ROADMAP 38.7 without Mega Showdown: the Mega Shrine isn't at the Blueprint Table (its blocks don't exist), no show is
 * held, and its text and mood read as they should. With Mega Showdown: {@code MegaShrineCompatTests}.
 */
public class MegaShrineGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** Without Mega Showdown the shrine is hidden at the Table and in the creative tab's list, and its show is off. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void hiddenWithoutMegaShowdown(GameTestHelper helper) {
		helper.assertTrue(!MegaBracelets.MOD && !MegaShrines.blocksExist(), "this suite runs without Mega Showdown");
		helper.assertTrue(!BlueprintLibrary.list(helper.getLevel().getServer(), false).contains(MegaShrines.SHRINE),
			"the Mega Shrine is listed without Mega Showdown");
		helper.assertTrue(!BlueprintLibrary.megaOk(MegaShrines.SHRINE) && BlueprintLibrary.megaOk(StarterBlueprints.WELL.id()),
			"megaOk hides the wrong builds");
		helper.assertTrue(!MegaShrines.live() && !MegaShrines.tick(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)),
			"the shrine's show is on without Mega Showdown");
		helper.assertTrue(StarterBlueprints.MEGA_SHRINE.id().equals(MegaShrines.SHRINE) && Decorations.points(MegaShrines.SHRINE) == 2,
			"the shrine is a decoration worth 2");
		helper.assertTrue("villageMegas".equals(StarterBlueprints.expansionSwitch(MegaShrines.SHRINE)), "the shrine isn't behind villageMegas");
		helper.succeed();
	}

	/** The show's mood: +3 while as fresh as a festival's, then gone; kept through a save. And its text. */
	//$ gametest_batch AREA '"megaShrineMood"'
	@GameTest(template = AREA, batch = "megaShrineMood")
	public void theShowsMoodAndText(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		v.setNoAi(true);
		long today = Chronicle.day(helper.getLevel());
		helper.assertTrue(MegaShrines.mood(helper.getLevel(), v) == 0, "a mood without a show");
		ModAttachments.MEGA_SHOW_DAY.set(v, today);
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(v.saveWithoutId(new CompoundTag()));
		helper.assertTrue(MegaShrines.mood(helper.getLevel(), copy) == 3, "the show's +3 lost in a save: " + MegaShrines.mood(helper.getLevel(), copy));
		ModAttachments.MEGA_SHOW_DAY.set(v, today - Festivals.MOOD_DAYS - 1);
		helper.assertTrue(MegaShrines.mood(helper.getLevel(), v) == 0, "the show's mood lasts too long");
		Language en = Language.getInstance();
		helper.assertTrue(en.has("message.aliveworkplace.mega_shrine.show") && en.has("mood.aliveworkplace.reason.mega_show")
			&& en.has("blueprint.aliveworkplace.mega_shrine") && en.has("blueprint_desc.aliveworkplace.mega_shrine"), "the shrine's text is missing");
		helper.assertTrue(en.getOrDefault("message.aliveworkplace.mega_shrine.show").contains("%s"), "the show's line names no trainer");
		copy.discard();
		v.discard();
		helper.succeed();
	}
}
