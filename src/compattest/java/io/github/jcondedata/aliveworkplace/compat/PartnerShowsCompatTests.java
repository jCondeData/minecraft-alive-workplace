package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 28.3, partners at work: a pastured Machop by a builder's bench is cued by the builder fetching planks, walks
 * toward the work within its pasture carrying a plank display, does its show, comes back, and the display is gone. No
 * show runs with no player near, or with {@code partnerShows} off.
 */
public class PartnerShowsCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos PASTURE = new BlockPos(10, 2, 10);
	private static final BlockPos WORK = new BlockPos(5, 2, 5);

	/** Turns the shows on for this test and off again when it ends, passed or failed. */
	private static void showsOn(GameTestHelper helper) {
		PartnerShows.ENABLED = true;
		try {
			java.lang.reflect.Field field = GameTestHelper.class.getDeclaredField("testInfo");
			field.setAccessible(true);
			((GameTestInfo) field.get(helper)).addListener(new GameTestListener() {
				@Override
				public void testStructureLoaded(GameTestInfo test) {
				}

				@Override
				public void testPassed(GameTestInfo test, GameTestRunner runner) {
					PartnerShows.stopAll();
					PartnerShows.ENABLED = false;
				}

				@Override
				public void testFailed(GameTestInfo test, GameTestRunner runner) {
					PartnerShows.stopAll();
					PartnerShows.ENABLED = false;
				}

				@Override
				public void testAddedForRerun(GameTestInfo old, GameTestInfo test, GameTestRunner runner) {
				}
			});
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Villager builder(GameTestHelper helper) {
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), builder, helper.absolutePos(BENCH), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		return builder;
	}

	private static PokemonEntity machop(GameTestHelper helper) {
		List<PokemonEntity> found = helper.getLevel().getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(4),
			e -> e.getPokemon().getSpecies().getName().equalsIgnoreCase("machop"));
		helper.assertTrue(found.size() == 1, "pastured Machops: " + found.size());
		return found.get(0);
	}

	/** The whole show: cued, there with the planks, back, and nothing left behind. */
	//$ gametest_ticks_batch AREA '700' '"partnerShows"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "partnerShows")
	public void aCuedMachopCarriesPlanksToTheWorkAndBack(GameTestHelper helper) {
		showsOn(helper);
		ServerLevel level = helper.getLevel();
		Villager builder = builder(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos by = helper.absolutePos(new BlockPos(1, 2, 6)); // a mock player starts at the world spawn: bring it to the test
		player.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
		BlockPos pasture = PastureCompatTests.pasture(helper, PASTURE);
		PastureCompatTests.pastured(helper, pasture, player, "machop", Direction.NORTH);
		BlockPos work = helper.absolutePos(WORK);
		double[] nearest = new double[1];
		Vec3[] start = new Vec3[1];
		boolean[] carried = new boolean[1];
		helper.runAfterDelay(20, () -> {
			PokemonEntity machop = machop(helper);
			start[0] = machop.position();
			nearest[0] = machop.position().distanceTo(Vec3.atBottomCenterOf(work));
			PartnerShows.forget(builder);
			helper.assertTrue(PartnerShows.cue(builder, "fetch", work, new ItemStack(Items.OAK_PLANKS)), "the builder's fetch didn't cue the Machop: " + PartnerShows.lastRefusal());
			Display.ItemDisplay display = PartnerShows.carrying(machop).orElse(null);
			helper.assertTrue(display != null && display.getSlot(0).get().is(Items.OAK_PLANKS),
				"the Machop isn't carrying planks: " + (display == null ? "no display" : display.getSlot(0).get()));
			helper.assertTrue(display.getTags().contains(PartnerShows.DISPLAY_TAG), "the display isn't tagged");
			helper.assertFalse(display.isPassenger(), "the display rides the Machop instead of following it");
			carried[0] = true;
			helper.assertFalse(PartnerShows.cue(builder, "fetch", work, new ItemStack(Items.OAK_PLANKS)), "a second show for the same worker within 10 s");
		});
		helper.onEachTick(() -> {
			if (start[0] == null) {
				return;
			}
			PokemonEntity machop = machop(helper);
			nearest[0] = Math.min(nearest[0], machop.position().distanceTo(Vec3.atBottomCenterOf(work)));
			helper.assertTrue(machop.getTethering() != null, "the show untethered the Machop");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(carried[0], "not cued yet");
			helper.assertTrue(PartnerShows.running(level) == 0, "the show is still on");
			PokemonEntity machop = machop(helper);
			double before = start[0].distanceTo(Vec3.atBottomCenterOf(work));
			helper.assertTrue(nearest[0] < before - 1.5, "the Machop never walked toward the work: from " + before + " to at best " + nearest[0]);
			helper.assertTrue(level.getEntitiesOfClass(Display.ItemDisplay.class, helper.getBounds().inflate(8),
				d -> d.getTags().contains(PartnerShows.DISPLAY_TAG)).isEmpty(), "the plank display was left behind");
			helper.assertTrue(machop.position().distanceTo(start[0]) <= 3.5, "the Machop didn't come back: " + machop.position().distanceTo(start[0]));
		});
	}

	/** No show with nobody within 48 blocks of the work, nor with partnerShows off. */
	//$ gametest_ticks_batch AREA '200' '"partnerShowsBudget"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "partnerShowsBudget")
	public void noShowWithNoPlayerNearOrWithTheSwitchOff(GameTestHelper helper) {
		showsOn(helper);
		Villager builder = builder(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos by = helper.absolutePos(new BlockPos(1, 2, 6)); // a mock player starts at the world spawn: bring it to the test
		player.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
		BlockPos pasture = PastureCompatTests.pasture(helper, PASTURE);
		PastureCompatTests.pastured(helper, pasture, player, "machop", Direction.NORTH);
		helper.runAfterDelay(20, () -> {
			BlockPos work = helper.absolutePos(WORK);
			PartnerShows.ENABLED = false;
			helper.assertFalse(PartnerShows.cue(builder, "fetch", work, new ItemStack(Items.OAK_PLANKS)), "a show with partnerShows off");
			PartnerShows.ENABLED = true;
			// Work 150 blocks up: nobody, this test's player included, is within 48 blocks of it.
			BlockPos sky = work.above(150);
			helper.assertTrue(helper.getLevel().getNearestPlayer(sky.getX(), sky.getY(), sky.getZ(), 48, false) == null, "a player is up there");
			helper.assertFalse(PartnerShows.cue(builder, "fetch", sky, new ItemStack(Items.OAK_PLANKS)), "a show with no player within 48 blocks");
			helper.assertFalse(PartnerShows.cue(builder, "dig", work), "a builder's cue that no show has started one");
			helper.assertTrue(PartnerShows.cue(builder, "fetch", work, new ItemStack(Items.OAK_PLANKS)), "with a player near, the show still didn't start: " + PartnerShows.lastRefusal());
			helper.getLevel().getServer().getPlayerList().remove(player);
			helper.succeed();
		});
	}
}
