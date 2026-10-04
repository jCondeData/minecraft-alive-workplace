package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;

/**
 * ROADMAP 28.3, the parts of partner shows that need no Cobblemon: the show files, a display left by a cut-off show,
 * and the toolbox's watering.
 */
public class PartnerShowsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** The engine's own show is loaded from data: a Fighting partner carrying what the builder fetched. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBuildersShowIsLoadedFromData(GameTestHelper helper) {
		PartnerShows.Show show = PartnerShows.shows().stream()
			.filter(s -> s.name().equals(AliveWorkplace.id("builder_carries_planks"))).findFirst().orElse(null);
		helper.assertTrue(show != null, "no builder_carries_planks show among " + PartnerShows.shows().stream().map(PartnerShows.Show::name).toList());
		helper.assertTrue(show.jobs().contains(AliveWorkplace.id("builder")) && show.types().equals(java.util.Set.of("fighting"))
			&& show.cue().equals("fetch") && "from_work".equals(show.carry()), "the builder's show reads " + show);
		helper.succeed();
	}

	/** 28.4's shows all load, and every sound and vanilla particle they name exists (a typo would play nothing). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyShowOfBuildingAndTheLandLoads(GameTestHelper helper) {
		java.util.Map<String, String> cues = java.util.Map.ofEntries(java.util.Map.entry("builder_carries_planks", "fetch"),
			java.util.Map.entry("builder_punches_blocks_home", "place"), java.util.Map.entry("builder_carries_stone", "fetch"),
			java.util.Map.entry("builder_carries_iron_parts", "fetch"), java.util.Map.entry("porter_carries_a_barrel", "haul"),
			java.util.Map.entry("crafter_holds_the_work", "craft"), java.util.Map.entry("farmer_water_waters_the_field", "tend"),
			java.util.Map.entry("farmer_grass_sparkles", "tend"), java.util.Map.entry("farmer_ground_walks_the_furrow", "till"),
			java.util.Map.entry("lumberjack_fighting_chops", "chop"), java.util.Map.entry("lumberjack_partner_plants_the_sapling", "replant"),
			java.util.Map.entry("orchard_partner_flutters_through_the_tree", "pick"));
		for (var e : cues.entrySet()) {
			PartnerShows.Show show = PartnerShows.shows().stream().filter(s -> s.name().equals(AliveWorkplace.id(e.getKey()))).findFirst().orElse(null);
			helper.assertTrue(show != null, "show " + e.getKey() + " didn't load");
			helper.assertTrue(show.cue().equals(e.getValue()), e.getKey() + " is cued at " + show.cue());
		}
		for (PartnerShows.Show show : PartnerShows.shows()) {
			helper.assertTrue(show.sound() == null || net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(show.sound()),
				show.name() + " names no sound: " + show.sound());
			helper.assertTrue(show.particles() == null || show.particles().getNamespace().equals("cobblemon")
				|| net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.containsKey(show.particles()), show.name() + " names no particle: " + show.particles());
		}
		PartnerShows.Show farmer = PartnerShows.shows().stream().filter(s -> s.name().equals(AliveWorkplace.id("farmer_water_waters_the_field"))).findFirst().get();
		helper.assertTrue(farmer.jobs().contains(ResourceLocation.withDefaultNamespace("farmer")) && farmer.types().equals(java.util.Set.of("water"))
			&& farmer.effect() == PartnerShows.Effect.HYDRATE_FARMLAND, "the farmer's watering show reads " + farmer);
		helper.succeed();
	}

	/** What a partner carries for the builder goes by its type: wood for Fighting, stone for Rock, iron parts for Steel. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachTypeCarriesOnlyItsOwnMaterials(GameTestHelper helper) {
		PartnerShows.Show wood = show(helper, "builder_carries_planks");
		PartnerShows.Show stone = show(helper, "builder_carries_stone");
		PartnerShows.Show iron = show(helper, "builder_carries_iron_parts");
		Object[][] cases = {
			{wood, Items.OAK_PLANKS, true}, {wood, Items.SPRUCE_LOG, true}, {wood, Items.COBBLESTONE, false}, {wood, Items.IRON_BARS, false},
			{stone, Items.COBBLESTONE, true}, {stone, Items.STONE_BRICKS, true}, {stone, Items.OAK_PLANKS, false},
			{iron, Items.IRON_BARS, true}, {iron, Items.LANTERN, true}, {iron, Items.CHAIN, true}, {iron, Items.IRON_DOOR, true},
			{iron, Items.COBBLESTONE, false}, {wood, net.minecraft.world.item.Items.AIR, false},
		};
		for (Object[] c : cases) {
			PartnerShows.Show show = (PartnerShows.Show) c[0];
			ItemStack stack = new ItemStack((net.minecraft.world.item.Item) c[1]);
			helper.assertTrue(show.plays(stack) == (boolean) c[2], show.name() + (show.plays(stack) ? " plays" : " doesn't play") + " for " + c[1]);
		}
		// A show with no carry tag plays whatever is handled; a bad tag id is refused with what's wrong.
		helper.assertTrue(show(helper, "builder_punches_blocks_home").plays(new ItemStack(Items.GLASS)), "the punching show is limited to some blocks");
		try {
			PartnerShows.parse(AliveWorkplace.id("broken"), JsonParser.parseString(
				"{\"jobs\":[\"aliveworkplace:builder\"],\"types\":[\"rock\"],\"cue\":\"fetch\",\"carry_tag\":\"Not An Id\"}").getAsJsonObject());
			helper.fail("accepted a show with a bad carry_tag");
		} catch (IllegalArgumentException e) {
			helper.assertTrue(e.getMessage().contains("carry_tag"), "the reason reads '" + e.getMessage() + "'");
		}
		helper.succeed();
	}

	private static PartnerShows.Show show(GameTestHelper helper, String name) {
		PartnerShows.Show show = PartnerShows.shows().stream().filter(s -> s.name().equals(AliveWorkplace.id(name))).findFirst().orElse(null);
		helper.assertTrue(show != null, "no show " + name);
		return show;
	}

	/** The toolbox's crack and dust: the crack particles of the board being worked, the dust of the ground, no change to blocks. */
	//$ gametest_ticks AREA '40'
	@GameTest(template = AREA, timeoutTicks = 40)
	public void theCrackAndDustEffectsLeaveTheWorldAlone(GameTestHelper helper) {
		BlockPos spot = new BlockPos(3, 2, 3);
		helper.setBlock(spot.below(), Blocks.FARMLAND);
		PartnerShows.apply(helper.getLevel(), PartnerShows.Effect.CRACK, helper.absolutePos(spot), new ItemStack(Items.OAK_PLANKS));
		PartnerShows.apply(helper.getLevel(), PartnerShows.Effect.CRACK, helper.absolutePos(spot), new ItemStack(Items.IRON_INGOT));
		PartnerShows.apply(helper.getLevel(), PartnerShows.Effect.CRACK, helper.absolutePos(spot), ItemStack.EMPTY);
		PartnerShows.apply(helper.getLevel(), PartnerShows.Effect.DUST, helper.absolutePos(spot), ItemStack.EMPTY);
		helper.assertBlockPresent(Blocks.FARMLAND, spot.below());
		helper.assertBlockPresent(Blocks.AIR, spot);
		helper.succeed();
	}

	/** A malformed show file is refused with what's wrong (the loader logs it and skips it; the rest still load). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aMalformedShowSaysWhatIsWrong(GameTestHelper helper) {
		ResourceLocation name = AliveWorkplace.id("broken");
		String[][] cases = {
			{"{\"types\":[\"fighting\"],\"cue\":\"fetch\"}", "jobs"},
			{"{\"jobs\":[\"aliveworkplace:builder\"],\"types\":[\"fighting\"]}", "cue"},
			{"{\"jobs\":[\"aliveworkplace:builder\"],\"types\":[\"fighting\"],\"cue\":\"fetch\",\"effect\":\"explode\"}", "effect"},
			{"{\"jobs\":[\"aliveworkplace:builder\"],\"types\":[\"fighting\"],\"cue\":\"fetch\",\"ticks\":5}", "ticks"},
			{"{\"jobs\":[\"aliveworkplace:builder\"],\"types\":[\"fighting\"],\"cue\":\"fetch\",\"carry\":\"minecraft:no_such_item\"}", "carry"},
			{"{\"jobs\":[\"aliveworkplace:builder\"],\"types\":[\"fighting\"],\"cue\":\"fetch\",\"animation\":\"dance\"}", "animation"},
		};
		for (String[] c : cases) {
			try {
				PartnerShows.parse(name, JsonParser.parseString(c[0]).getAsJsonObject());
				helper.fail("accepted a show with a bad " + c[1] + ": " + c[0]);
			} catch (IllegalArgumentException e) {
				helper.assertTrue(e.getMessage().contains(c[1]), "the reason for a bad " + c[1] + " reads '" + e.getMessage() + "'");
			}
		}
		PartnerShows.Show good = PartnerShows.parse(name, JsonParser.parseString(
			"{\"jobs\":[\"minecraft:farmer\"],\"types\":[\"Water\"],\"cue\":\"tend\",\"effect\":\"hydrate_farmland\"}").getAsJsonObject());
		helper.assertTrue(good.types().contains("water") && good.effect() == PartnerShows.Effect.HYDRATE_FARMLAND && good.ticks() == 40,
			"a good show reads " + good);
		helper.succeed();
	}

	/** A display left by a cut-off show (the server stopped mid-show) is removed as soon as it loads. */
	//$ gametest_ticks AREA '40'
	@GameTest(template = AREA, timeoutTicks = 40)
	public void aCutOffShowsDisplayIsRemovedWhenItLoads(GameTestHelper helper) {
		Display.ItemDisplay stray = EntityType.ITEM_DISPLAY.create(helper.getLevel());
		stray.getSlot(0).set(new ItemStack(Items.OAK_PLANKS));
		stray.addTag(PartnerShows.DISPLAY_TAG);
		BlockPos at = helper.absolutePos(new BlockPos(2, 3, 2));
		stray.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Display.ItemDisplay other = EntityType.ITEM_DISPLAY.create(helper.getLevel());
		other.getSlot(0).set(new ItemStack(Items.OAK_PLANKS));
		other.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
		helper.getLevel().addFreshEntity(stray);
		helper.getLevel().addFreshEntity(other);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(stray.isRemoved(), "the cut-off show's display is still there");
			helper.assertFalse(other.isRemoved(), "a display that isn't a show's was removed too");
			other.discard();
			helper.succeed();
		});
	}

	/** The toolbox's hydrate_farmland makes the 3×3 farmland round the spot fully moist, and nothing else. */
	//$ gametest_ticks AREA '40'
	@GameTest(template = AREA, timeoutTicks = 40)
	public void theWateringShowMoistensTheFarmland(GameTestHelper helper) {
		BlockPos centre = new BlockPos(3, 1, 3);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				helper.setBlock(centre.offset(dx, 0, dz), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
			}
		}
		PartnerShowsGameTests.apply(helper, centre.above());
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int moisture = helper.getBlockState(centre.offset(dx, 0, dz)).getValue(FarmBlock.MOISTURE);
				boolean inside = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
				helper.assertTrue(moisture == (inside ? 7 : 0), "farmland at " + dx + ", " + dz + " has moisture " + moisture);
			}
		}
		helper.succeed();
	}

	private static void apply(GameTestHelper helper, BlockPos spot) {
		PartnerShows.apply(helper.getLevel(), PartnerShows.Effect.HYDRATE_FARMLAND, helper.absolutePos(spot));
	}
}
