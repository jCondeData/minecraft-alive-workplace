package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeats;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 37.6 without the furniture mods: {@code #aliveworkplace:villager_seats} lists only the mods' chairs, stools,
 * benches, couches and sofas, each {@code "required": false}, so the tag still loads (an entry that had to be there would
 * fail the whole tag), holds nothing here, and vanilla's stairs (the builds' plain seats) aren't in it.
 */
public class FurnitureSeatsGameTests implements FabricGameTest {
	private static final Set<String> MODS = Set.of("handcrafted", "cozyhome", "cobblefurnies");

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSeatsTagLoadsWithoutTheFurnitureMods(GameTestHelper helper) {
		var tag = BuiltInRegistries.BLOCK.getTag(FurnitureSeats.SEATS);
		helper.assertTrue(tag.isPresent(), "#aliveworkplace:villager_seats didn't load");
		for (Holder<Block> block : tag.get()) {
			String mod = BuiltInRegistries.BLOCK.getKey(block.value()).getNamespace();
			helper.assertTrue(MODS.contains(mod), "a seat of " + mod + " in the tag: " + BuiltInRegistries.BLOCK.getKey(block.value()));
		}
		helper.assertFalse(Blocks.OAK_STAIRS.defaultBlockState().is(FurnitureSeats.SEATS), "vanilla stairs shouldn't be villager seats");
		helper.assertFalse(Blocks.OAK_SLAB.defaultBlockState().is(FurnitureSeats.SEATS), "vanilla slabs shouldn't be villager seats");
		helper.succeed();
	}
}
