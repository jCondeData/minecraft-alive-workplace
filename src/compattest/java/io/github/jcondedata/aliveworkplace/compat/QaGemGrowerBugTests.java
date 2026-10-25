package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.gem.GemGrowers;
import io.github.jcondedata.aliveworkplace.work.PokemonFeatures;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane d, 2026-10-25), ROADMAP 28.11: a FAILING bug test, not registered (the builder registers it with the fix).
 * Spec: "with glass in the chests and Cobblemon 1.8 she also makes Blank TMs from shards (Cobblemon's recipe), up to 8".
 * Cobblemon's recipe makes 2 a craft, and she crafts whenever her chests hold fewer than 8, so 7 become 9. Run with
 * -Pcobblemon18=true; on 1.7.3 it passes trivially.
 */
public class QaGemGrowerBugTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** 7 Blank TMs, glass and shards in her chest: she must never hold more than 8. Fails today: 9. */
	//$ gametest_ticks_batch AREA '1600' '"qa_gem_tm_bug"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_gem_tm_bug")
	public void qaSevenBlankTmsNeverBecomeNine(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		if (!PokemonFeatures.TYPE_GEMS.available()) {
			helper.succeed();
			return;
		}
		Item tm = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "blank_tm"));
		GemGrowerCompatTests.grower(helper, new ItemStack(tm, 7), new ItemStack(Items.GLASS, 16), new ItemStack(Items.AMETHYST_SHARD, 16));
		helper.onEachTick(() -> {
			Container chest = helper.getBlockEntity(new net.minecraft.core.BlockPos(2, 2, 4));
			helper.assertTrue(chest.countItem(tm) <= GemGrowers.BLANK_TMS_KEPT, "more than 8 Blank TMs: " + chest.countItem(tm));
		});
		helper.runAtTickTime(1200, helper::succeed);
	}
}
