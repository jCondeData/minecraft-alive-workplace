package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * ROADMAP 41.1 without the Waystones mod (the main suite never loads it): the Waystone Plaza is never offered. The
 * Steward's rule for it is loaded, but stands aside as "needs a mod", nothing fills the waystones extension, and no
 * wish of a village with a hall is the plaza. The compat suite has the rest (WaystonesCompatTests).
 */
public class VillageWaystonesGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation RULE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "waystone_plaza");

	/** No Waystones: the plaza rule is skipped for the missing mod, nothing fills the extension, no wish is the plaza. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void withoutWaystonesNoPlazaIsOffered(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = new BlockPos(2, 2, 2);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		helper.assertFalse(VillageWaystones.EXTENSION.present(), "something fills the waystones extension without the mod");
		helper.assertFalse(VillageWaystones.offered(), "the plaza is offered without Waystones");
		StewardRules.Rule rule = StewardRules.all().stream().filter(r -> r.id().equals(RULE)).findFirst().orElse(null);
		helper.assertTrue(rule != null, "no Steward rule " + RULE + " is loaded");
		StewardConditions.Facts facts = StewardConditions.Facts.of(level, helper.absolutePos(hall));
		StewardWishes.Verdict verdict = StewardWishes.judge(rule, facts, StewardWishes.State.EMPTY, 0);
		helper.assertTrue(verdict.status() == StewardWishes.Status.MOD_MISSING, "the plaza rule without Waystones: " + verdict.status());
		boolean wished = StewardWishes.rank(StewardRules.all(), facts, StewardWishes.State.EMPTY, 0).stream()
			.anyMatch(w -> w.effect().blueprint().map(VillageWaystones::isPlaza).orElse(false));
		helper.assertFalse(wished, "the Steward wishes for a Waystone Plaza without Waystones");
		helper.succeed();
	}
}
