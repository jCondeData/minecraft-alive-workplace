package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

/**
 * QA bug test (lane E, saves and the pack, 2026-11-02; B791's kind for a village's colours, 30.13): banner patterns are a
 * registry that mods and datapacks add to. A village whose banner design used a pattern from a mod or datapack taken
 * out of the pack since must lose only that layer, as every banner standing in the world does (vanilla's banner reads
 * its layers with resultOrPartial). The hall reads its {@code bannerPatterns} with {@code .result()}, so one unknown
 * layer loses the whole design: the village's colours become a plain banner of the base colour, and the banners
 * already flying in its colours (which kept their other layers) no longer count as its colours at festivals
 * ({@link VillageBanners.Colours#on}).
 *
 * <p>The removed mod is a pattern id renamed in the saved tag to one nobody registers, as in
 * {@code QaBlockEntityRemovedModId1102GameTests}.
 */
public class QaVillageBannerRemovedPattern1102eBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String GONE = "gonemod:crest";

	/** Renames the middle layer's pattern of a saved layer list to {@link #GONE}; false if the list isn't as expected. */
	private static boolean renameMiddle(Tag layers) {
		if (!(layers instanceof ListTag list) || list.size() != 3 || !(list.get(1) instanceof CompoundTag layer) || !layer.contains("pattern")) {
			return false;
		}
		layer.putString("pattern", GONE);
		return true;
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void aVillageBannerLosesOnlyTheRemovedModsLayerAsABannerInTheWorldDoes(GameTestHelper helper) {
		Leftovers.clear(helper);
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		var patterns = registries.lookupOrThrow(Registries.BANNER_PATTERN);
		BannerPatternLayers design = new BannerPatternLayers.Builder()
			.add(patterns.getOrThrow(BannerPatterns.STRIPE_TOP), DyeColor.WHITE)
			.add(patterns.getOrThrow(BannerPatterns.FLOWER), DyeColor.BLUE) // stands in for a removed mod's pattern
			.add(patterns.getOrThrow(BannerPatterns.BORDER), DyeColor.BLACK)
			.build();

		BlockPos hallPos = new BlockPos(1, 2, 1);
		helper.setBlock(hallPos, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(hallPos);
		hall.setColours(new VillageBanners.Colours(DyeColor.RED, design));

		// A banner flying in the village's colours, as players and builders hang them.
		BlockPos bannerPos = new BlockPos(4, 2, 1);
		helper.setBlock(bannerPos, Blocks.RED_BANNER);
		BannerBlockEntity banner = helper.getBlockEntity(bannerPos);
		banner.fromItem(new VillageBanners.Colours(DyeColor.RED, design).banner(), DyeColor.RED);
		helper.assertTrue(hall.colours().on(banner), "setup: the banner doesn't show the village's colours before the save");

		// The world saved, the mod taken out, the world opened again.
		CompoundTag hallTag = hall.saveWithoutMetadata(registries);
		CompoundTag bannerTag = banner.saveWithoutMetadata(registries);
		helper.assertTrue(renameMiddle(hallTag.get("bannerPatterns")), "setup: the hall's saved design isn't three layers: " + hallTag.get("bannerPatterns"));
		helper.assertTrue(renameMiddle(bannerTag.get("patterns")), "setup: the banner's saved design isn't three layers: " + bannerTag.get("patterns"));
		hall.loadWithComponents(hallTag, registries);
		banner.loadWithComponents(bannerTag, registries);

		helper.assertTrue(banner.getPatterns().layers().size() == 2, "control: vanilla's banner kept " + banner.getPatterns().layers().size()
			+ " of its 2 readable layers");
		VillageBanners.Colours colours = hall.colours();
		helper.assertTrue(colours != null && colours.base() == DyeColor.RED, "the village's colours after the load: " + colours);
		helper.assertTrue(colours.patterns().layers().size() == 2, "the village's banner design lost every layer for one removed mod's pattern: "
			+ colours.patterns().layers().size() + " of 2 readable layers kept (a banner standing in the world kept "
			+ banner.getPatterns().layers().size() + ")");
		helper.assertTrue(colours.on(banner), "the village's own banner, flying since before, no longer counts as its colours after the load");
		helper.succeed();
	}
}
