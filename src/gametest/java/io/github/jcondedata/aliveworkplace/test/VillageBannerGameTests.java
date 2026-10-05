package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.VillageBannerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The Village Banner (ROADMAP 30.13): crafting keeps the design, the hall takes it as its colours, and they survive a reload. */
public class VillageBannerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 2);

	private static BannerPatternLayers design(GameTestHelper helper) {
		var patterns = helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		return new BannerPatternLayers.Builder().add(patterns.getOrThrow(BannerPatterns.STRIPE_TOP), DyeColor.RED).build();
	}

	/** A red-striped blue banner and a gold ingot make a Village Banner of that design, which sets the hall's colours and survives a reload. */
	//$ gametest_ticks_batch AREA '100' '"villageBanner"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "villageBanner")
	public void villageBannerSetsTheHallsColoursAndSurvivesAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ItemStack banner = new ItemStack(Items.BLUE_BANNER);
			banner.set(DataComponents.BANNER_PATTERNS, design(helper));
			VillageBannerItem.Recipe recipe = new VillageBannerItem.Recipe(CraftingBookCategory.MISC);
			CraftingInput input = CraftingInput.of(2, 1, List.of(banner, new ItemStack(Items.GOLD_INGOT)));
			helper.assertTrue(recipe.matches(input, level), "a banner and a gold ingot match");
			helper.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(banner, new ItemStack(Items.IRON_INGOT))), level), "iron doesn't");
			ItemStack village = recipe.assemble(input, level.registryAccess());
			helper.assertTrue(VillageBannerItem.base(village) == DyeColor.BLUE && VillageBannerItem.patterns(village).equals(design(helper)), "keeps the design");

			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(entity.colours() == null, "a new hall has no colours");
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			VillageBannerItem.setColours(level, player, village, hall);
			helper.assertTrue(village.getCount() == 1, "the banner isn't used up");
			VillageBanners.Colours colours = entity.colours();
			helper.assertTrue(colours != null && colours.base() == DyeColor.BLUE && colours.patterns().equals(design(helper)), "set: " + colours);

			CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
			VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(copy != null && colours.equals(copy.colours()), "reloaded: " + (copy == null ? null : copy.colours()));
			tag.remove("bannerBase");
			tag.remove("bannerPatterns");
			VillageHallBlockEntity old = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(old != null && old.colours() == null, "a hall saved before banners has none");
			helper.assertTrue(WorkplaceConfig.parse("{}").villageBanners, "villageBanners defaults on");
			helper.succeed();
		});
	}
}
