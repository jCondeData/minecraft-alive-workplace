package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The Stadium (ROADMAP 35.23) without Cobblemon, and the village colours its seats take. Its design checks run with
 * Cobblemon (compat StadiumCompatTests); here: its file isn't loaded, so it isn't on the Wonders page, and the
 * red-and-white-to-banner-colours style.
 */
public class StadiumGameTests implements FabricGameTest {
	private static final ResourceLocation STADIUM = AliveWorkplace.id("stadium");

	/** Without Cobblemon the Stadium's file is skipped by its load conditions, quietly; the other Wonders load. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStadiumNeedsCobblemon(GameTestHelper helper) {
		helper.assertTrue(!Wonders.loaded().containsKey(STADIUM), "the Stadium is loaded without Cobblemon");
		helper.assertTrue(Wonders.loaded().containsKey(AliveWorkplace.id("observatory")), "the other Wonders aren't loaded: " + Wonders.loaded().keySet());
		helper.assertTrue(Wonders.problems().keySet().stream().noneMatch(p -> p.getPath().contains("stadium")),
			"the Stadium's file is logged as a mistake: " + Wonders.problems());
		ResourceLocation path = AliveWorkplace.id("wonders/stadium.json");
		var resource = helper.getLevel().getServer().getResourceManager().getResource(path)
			.orElseThrow(() -> new GameTestAssertException("no data file " + path));
		JsonObject json;
		try (Reader reader = resource.openAsReader()) {
			json = JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			throw new GameTestAssertException(path + " doesn't read: " + e.getMessage());
		}
		helper.assertTrue(json.getAsJsonArray("fabric:load_conditions").toString().contains("\"cobblemon\""),
			"its load conditions: " + json.get("fabric:load_conditions"));
		helper.assertTrue(Wonders.read(STADIUM, json) == null, "the file reads without Cobblemon");
		helper.succeed();
	}

	/**
	 * The village-colour style builds red as the first colour and white as the second (wool, carpet, terracotta,
	 * banners, keeping a banner's facing), leaves the field's white concrete and everything else alone, isn't listed
	 * among the styles a player picks, and doesn't exist for a colour that doesn't.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theColoursStyleSwapsRedAndWhite(GameTestHelper helper) {
		String name = BlueprintStyles.coloursStyle(DyeColor.BLUE, DyeColor.YELLOW);
		helper.assertTrue(name.equals("colours_blue_yellow"), "the name: " + name);
		BlueprintStyles.Style style = BlueprintStyles.get(name).orElseThrow(() -> new GameTestAssertException("no style " + name));
		helper.assertTrue(BlueprintStyles.apply(style, Blocks.RED_CARPET.defaultBlockState()).is(Blocks.BLUE_CARPET), "red carpet");
		helper.assertTrue(BlueprintStyles.apply(style, Blocks.WHITE_CARPET.defaultBlockState()).is(Blocks.YELLOW_CARPET), "white carpet");
		helper.assertTrue(BlueprintStyles.apply(style, Blocks.RED_TERRACOTTA.defaultBlockState()).is(Blocks.BLUE_TERRACOTTA), "red terracotta");
		helper.assertTrue(BlueprintStyles.apply(style, Blocks.WHITE_WOOL.defaultBlockState()).is(Blocks.YELLOW_WOOL), "white wool");
		BlockState banner = BlueprintStyles.apply(style, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH));
		helper.assertTrue(banner.is(Blocks.BLUE_WALL_BANNER) && banner.getValue(WallBannerBlock.FACING) == Direction.SOUTH, "the banner: " + banner);
		helper.assertTrue(BlueprintStyles.apply(style, Blocks.WHITE_CONCRETE.defaultBlockState()).is(Blocks.WHITE_CONCRETE), "the lines");
		helper.assertTrue(BlueprintStyles.apply(style, Blocks.BLACK_TERRACOTTA.defaultBlockState()).is(Blocks.BLACK_TERRACOTTA), "black terracotta");
		helper.assertTrue(BlueprintStyles.apply(style, Blocks.STONE_BRICKS.defaultBlockState()).is(Blocks.STONE_BRICKS), "stone bricks");
		helper.assertTrue(BlueprintStyles.all().stream().noneMatch(s -> s.name().startsWith(BlueprintStyles.COLOURS)), "listed: " + BlueprintStyles.all());
		helper.assertTrue(BlueprintStyles.get("colours_mauve_white").isEmpty(), "a style for a colour that doesn't exist");
		helper.succeed();
	}

	/** A village's banner picks the style: its base and the first other colour; none for red and white, or no banner. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBannerPicksTheColours(GameTestHelper helper) {
		var patterns = helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		BannerPatternLayers yellowStripe = new BannerPatternLayers.Builder()
			.add(patterns.getOrThrow(BannerPatterns.BORDER), DyeColor.BLUE)
			.add(patterns.getOrThrow(BannerPatterns.STRIPE_MIDDLE), DyeColor.YELLOW).build();
		BannerPatternLayers whiteStripe = new BannerPatternLayers.Builder().add(patterns.getOrThrow(BannerPatterns.STRIPE_MIDDLE), DyeColor.WHITE).build();
		helper.assertTrue(VillageBanners.wonderStyle(null) == null, "no banner");
		String blue = VillageBanners.wonderStyle(new VillageBanners.Colours(DyeColor.BLUE, yellowStripe));
		helper.assertTrue("colours_blue_yellow".equals(blue), "blue with a yellow stripe (on a blue border): " + blue);
		helper.assertTrue(VillageBanners.wonderStyle(new VillageBanners.Colours(DyeColor.RED, whiteStripe)) == null, "red and white");
		String plainWhite = VillageBanners.wonderStyle(new VillageBanners.Colours(DyeColor.WHITE, BannerPatternLayers.EMPTY));
		helper.assertTrue("colours_white_red".equals(plainWhite), "a plain white banner: " + plainWhite);
		String plainGreen = VillageBanners.wonderStyle(new VillageBanners.Colours(DyeColor.GREEN, BannerPatternLayers.EMPTY));
		helper.assertTrue("colours_green_white".equals(plainGreen), "a plain green banner: " + plainGreen);
		helper.succeed();
	}

	/**
	 * The scoreboard over the east wall reads "2 - 1" from both sides (B141: it was one face thick, so from outside the
	 * bowl it read mirrored, "1 - S"). Its part of stage III (offset 48, 0, 8 in the Stadium) has a face at x 49 read
	 * from the stands, looking east (north on the left), and one at x 50 read from the road, looking west (south on the
	 * left); the score's white cells on each, read left to right, top to bottom, are the same digits.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theScoreboardReadsFromBothSides(GameTestHelper helper) {
		ResourceLocation id = AliveWorkplace.id("wonder/stadium/stage_3_1");
		StructureTemplate part = helper.getLevel().getStructureManager().get(id)
			.orElseThrow(() -> new GameTestAssertException("no structure " + id));
		Set<BlockPos> white = new HashSet<>();
		for (StructureTemplate.StructureBlockInfo info : part.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.WHITE_TERRACOTTA)) {
			white.add(info.pos().offset(48, 0, 8));
		}
		String[] score = {"WWW....W.", "..W...WW.", "WWW.W..W.", "W......W.", "WWW...WWW"}; // "2 - 1", rows y 17 down to 13
		StringBuilder field = new StringBuilder();
		StringBuilder road = new StringBuilder();
		for (int row = 0; row < score.length; row++) {
			int y = 17 - row;
			for (int i = 0; i < 9; i++) {
				field.append(white.contains(new BlockPos(49, y, 17 + i)) ? 'W' : '.');
				road.append(white.contains(new BlockPos(50, y, 25 - i)) ? 'W' : '.');
			}
			field.append('/');
			road.append('/');
		}
		String expected = String.join("/", score) + "/";
		helper.assertTrue(field.toString().equals(expected), "from the stands it reads " + field + ", not 2 - 1 (" + expected + ")");
		helper.assertTrue(road.toString().equals(expected), "from outside it reads " + road + ", not 2 - 1 (" + expected + ")");
		helper.succeed();
	}
}
