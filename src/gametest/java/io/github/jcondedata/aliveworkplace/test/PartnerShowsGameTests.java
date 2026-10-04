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
