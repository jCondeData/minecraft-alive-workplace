package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (2026-11-01) for ROADMAP 38.2 (the village knows its raid dens), written from the spec. 38.3 (Den Wardens) and
 * 38.4 (village trainers challenge the dens) were superseded by 38.1a and have no code left, so only 38.2's edges are
 * here, the ones RaidDenCompatTests, RaidDenFoundCompatTests and QaDenMegaCompatTests leave open:
 * <ul>
 * <li>a resting den's Defence-page row, word for word with its real arguments (where it is, the resting hint, the day),
 * and no glow; turned active, the active hint and the glow;</li>
 * <li>the save: a resting den and its first day come back as they were; an entry from a save without "active" loads
 * active; a den read as changed or found gone marks the save to be written (else the change is lost on a restart);</li>
 * <li>a den broken by a player is dropped at the next ten-second look by itself, with nobody opening the list;</li>
 * <li>a hall set down by dens that were there first (the B190 look-around path) takes the one 160 blocks off and not
 * the one 161 off;</li>
 * <li>the village map marks a resting den as resting, and forgets it once it's broken;</li>
 * <li>a den known in the Overworld isn't listed for a hall at the same place in the Nether.</li>
 * </ul>
 * Each test runs in its own batch, clears stray entities round it first and puts back every block it set.
 */
public class QaDens1101CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(2, 2, 2);

	/** Takes every block this test set away again at its end, passed or failed. */
	private static List<BlockPos> cleanup(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		List<BlockPos> placed = new ArrayList<>();
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.after(helper, () -> {
			for (BlockPos p : placed) {
				level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			}
			for (BlockPos p : placed) {
				RaidDens.near(level, p);
			}
		});
		return placed;
	}

	private static BlockPos hall(GameTestHelper helper) {
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(HALL);
	}

	/** A den at the absolute {@code pos}, found as the chunk-load path finds it. */
	private static void denAt(GameTestHelper helper, BlockPos pos, BlockState state, List<BlockPos> placed) {
		ServerLevel level = helper.getLevel();
		level.setBlockAndUpdate(pos, state);
		placed.add(pos);
		RaidDens.chunkLoaded(level, level.getChunkAt(pos));
		RaidDens.tick(level);
	}

	private static boolean known(GameTestHelper helper, BlockPos den) {
		return RaidDens.Sites.get(helper.getLevel()).dens().stream().anyMatch(d -> d.pos().equals(den));
	}

	private static Optional<RaidDens.Den> listed(GameTestHelper helper, BlockPos hall, BlockPos den) {
		return RaidDens.near(helper.getLevel(), hall).stream().filter(d -> d.pos().equals(den)).findFirst();
	}

	/** The Defence page's den icon whose first line is {@code name}, or null. */
	private static ItemStack denIcon(GameTestHelper helper, BlockPos hall, String name) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			DefencePage.render(menu, helper.getLevel(), hall);
			for (int slot : DefencePage.DENS) {
				ItemStack icon = menu.icon(slot);
				if (icon != null && !icon.isEmpty() && icon.getHoverName().getString().equals(name)) {
					return icon.copy();
				}
			}
			return null;
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}

	private static GameTestAssertException fail(String message) {
		return new GameTestAssertException(message);
	}

	/**
	 * A resting den's row reads, line by line, "Raid den, tier 2, Water, resting" / "8 blocks east" / "Beaten for now: it
	 * glows again by itself" / "Found on day N" and doesn't glow; the same den turned active reads "It glows: a raid can be
	 * fought here" and glows.
	 */
	//$ gametest_batch AREA '"qaDens1101Rows"'
	@GameTest(template = AREA, batch = "qaDens1101Rows")
	public void aRestingDenRowSaysBeatenAndDoesntGlow(GameTestHelper helper) {
		List<BlockPos> placed = cleanup(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		BlockPos at = hall.offset(8, 0, 0);
		denAt(helper, at, RaidDenCompatTests.den("tier_two", "water", false), placed);
		long day = listed(helper, hall, at).orElseThrow(() -> fail("the resting den isn't listed")).day();
		helper.assertTrue(day == Chronicle.day(level), "found on day " + day + ", today is " + Chronicle.day(level));
		ItemStack resting = denIcon(helper, hall, "Raid den, tier 2, Water, resting");
		helper.assertTrue(resting != null, "no Defence-page row reads \"Raid den, tier 2, Water, resting\"");
		List<String> lines = RaidDenCompatTests.lines(resting);
		List<String> expect = List.of("Raid den, tier 2, Water, resting", "8 blocks east", "Beaten for now: it glows again by itself", "Found on day " + day);
		helper.assertTrue(lines.containsAll(expect), "the resting den's row: " + lines + ", expected to hold " + expect);
		helper.assertFalse(lines.contains("It glows: a raid can be fought here"), "a resting den says it glows: " + lines);
		helper.assertFalse(resting.getOrDefault(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false), "a resting den's icon glows");
		level.setBlockAndUpdate(at, RaidDenCompatTests.den("tier_two", "water", true));
		ItemStack active = denIcon(helper, hall, "Raid den, tier 2, Water, active");
		helper.assertTrue(active != null, "turned active, no row reads \"Raid den, tier 2, Water, active\"");
		List<String> now = RaidDenCompatTests.lines(active);
		helper.assertTrue(now.contains("It glows: a raid can be fought here") && !now.contains("Beaten for now: it glows again by itself")
			&& now.contains("8 blocks east") && now.contains("Found on day " + day), "the active den's row: " + now);
		helper.assertTrue(active.getOrDefault(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false), "an active den's icon doesn't glow");
		helper.succeed();
	}

	/**
	 * The save keeps a resting den resting with the day it was first found; a den saved before "active" was stored loads
	 * active; and an unknown tier or empty list doesn't break the load.
	 */
	//$ gametest_batch AREA '"qaDens1101Save"'
	@GameTest(template = AREA, batch = "qaDens1101Save")
	public void aRestingDenComesBackRestingAfterASave(GameTestHelper helper) {
		List<BlockPos> placed = cleanup(helper);
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		BlockPos hall = hall(helper);
		BlockPos at = hall.offset(5, 0, 7);
		denAt(helper, at, RaidDenCompatTests.den("tier_six", "ghost", false), placed);
		RaidDens.Den den = listed(helper, hall, at).orElseThrow(() -> fail("the resting den isn't listed"));
		helper.assertTrue(den.tier() == 6 && den.type().equals("ghost") && !den.active(), "the den as found: " + den);
		RaidDens.Sites loaded = RaidDens.Sites.load(RaidDens.Sites.get(level).save(new CompoundTag(), registries), registries);
		helper.assertTrue(loaded.dens().contains(den), "after a reload the resting den is " + loaded.dens().stream().filter(d -> d.pos().equals(at)).toList()
			+ ", expected " + den);
		// a save written before the den's state was stored: no "active", so it loads as active
		CompoundTag old = new CompoundTag();
		ListTag list = new ListTag();
		CompoundTag entry = new CompoundTag();
		BlockPos far = new BlockPos(12345, 70, -6789);
		entry.putLong("pos", far.asLong());
		entry.putInt("tier", 3);
		entry.putString("type", "fire");
		entry.putLong("firstSeen", 9L);
		list.add(entry);
		old.put("dens", list);
		List<RaidDens.Den> oldDens = List.copyOf(RaidDens.Sites.load(old, registries).dens());
		helper.assertTrue(oldDens.equals(List.of(new RaidDens.Den(far, 3, "fire", true, 9L))), "an old save's den: " + oldDens);
		helper.succeed();
	}

	/**
	 * A den read as changed (gone quiet) or found gone marks the level's den list to be saved; otherwise the change would
	 * be lost when the server stops before anything else in the list changes.
	 */
	//$ gametest_batch AREA '"qaDens1101Dirty"'
	@GameTest(template = AREA, batch = "qaDens1101Dirty")
	public void aChangedOrGoneDenMarksTheSave(GameTestHelper helper) {
		List<BlockPos> placed = cleanup(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		BlockPos at = hall.offset(6, 0, -5);
		RaidDens.Sites sites = RaidDens.Sites.get(level);
		sites.setDirty(false);
		denAt(helper, at, RaidDenCompatTests.den("tier_one", "ice", true), placed);
		helper.assertTrue(known(helper, at), "the den isn't recorded");
		helper.assertTrue(sites.isDirty(), "a den recorded didn't mark the save");
		sites.setDirty(false);
		level.setBlockAndUpdate(at, RaidDenCompatTests.den("tier_one", "ice", false));
		helper.assertTrue(listed(helper, hall, at).map(d -> !d.active()).orElse(false), "the den isn't read as resting");
		helper.assertTrue(sites.isDirty(), "a den gone quiet didn't mark the save: a restart would show it active again");
		sites.setDirty(false);
		level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
		helper.assertTrue(listed(helper, hall, at).isEmpty(), "a broken den is still listed");
		helper.assertTrue(sites.isDirty(), "a den dropped didn't mark the save: a restart would bring it back");
		helper.succeed();
	}

	/** A player breaks a known den; with nobody opening the list, the level's own ten-second look drops it. */
	//$ gametest_ticks_batch AREA '320' '"qaDens1101Broken"'
	@GameTest(template = AREA, timeoutTicks = 320, batch = "qaDens1101Broken")
	public void aDenBrokenByAPlayerIsDroppedByItself(GameTestHelper helper) {
		List<BlockPos> placed = cleanup(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		BlockPos at = hall.offset(4, 0, 4);
		denAt(helper, at, RaidDenCompatTests.den("tier_three", "electric", true), placed);
		helper.assertTrue(known(helper, at), "the den isn't recorded");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		boolean broken;
		try {
			broken = player.gameMode.destroyBlock(at);
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		helper.assertTrue(broken && level.getBlockState(at).isAir(), "the player couldn't break the den: " + level.getBlockState(at));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertFalse(known(helper, at), "the den broken by a player is still known at tick " + level.getGameTime()))
			.thenSucceed();
	}

	/**
	 * Dens that were there first (their chunks loaded, no hall near): a hall set down then takes the one exactly 160
	 * blocks off and not the one 161 off. A way no other test's hall reaches is used, so "no hall near" holds.
	 */
	//$ gametest_batch AREA '"qaDens1101HallReach"'
	@GameTest(template = AREA, batch = "qaDens1101HallReach")
	public void aNewHallTakesTheDen160OffNot161(GameTestHelper helper) {
		List<BlockPos> placed = cleanup(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.assertFalse(level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL), "setup: a hall already stands here");
		int[] way = null;
		for (int[] w : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			if (RaidDens.hallNear(level, hall.offset(160 * w[0], 0, 160 * w[1])).isEmpty()
				&& RaidDens.hallNear(level, hall.offset(161 * w[0], 0, 161 * w[1])).isEmpty()) {
				way = w;
				break;
			}
		}
		helper.assertTrue(way != null, "setup: other tests' halls reach 160 blocks off this spot every way");
		BlockPos in = hall.offset(160 * way[0], 0, 160 * way[1]);
		BlockPos out = hall.offset(161 * way[0], 0, 161 * way[1]);
		BlockState den = RaidDenCompatTests.den("tier_two", "rock", true);
		for (BlockPos p : List.of(in, out)) {
			level.setBlockAndUpdate(p, den);
			placed.add(p);
			RaidDens.chunkLoaded(level, level.getChunkAt(p));
		}
		RaidDens.tick(level);
		helper.assertFalse(known(helper, in) || known(helper, out), "a den with no hall within 160 blocks was recorded");
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL); // the hall's own placing looks round it on the next tick
		placed.add(hall);
		helper.assertTrue(level.getChunkSource().getChunkNow(in.getX() >> 4, in.getZ() >> 4) != null, "setup: the den's chunk unloaded");
		RaidDens.tick(level);
		helper.assertTrue(known(helper, in), "a hall set down 160 blocks from a den that was there first didn't take it");
		helper.assertTrue(listed(helper, hall, in).isPresent(), "the den 160 blocks off isn't on the new hall's list");
		helper.assertFalse(known(helper, out), "a hall set down 161 blocks from a den took it");
		helper.succeed();
	}

	/** The village map marks a resting den with its resting line; once the den is broken, the map forgets it. */
	//$ gametest_batch AREA '"qaDens1101Map"'
	@GameTest(template = AREA, batch = "qaDens1101Map")
	public void theMapMarksARestingDenAndForgetsABrokenOne(GameTestHelper helper) {
		List<BlockPos> placed = cleanup(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		BlockPos at = hall.offset(-9, 0, 11);
		denAt(helper, at, RaidDenCompatTests.den("tier_one", "ice", false), placed);
		List<VillageMaps.Mark> marks = VillageMaps.marks(level, hall, 64).stream()
			.filter(m -> m.kind() == VillageMaps.Kind.RAID_DEN && m.pos().getX() == at.getX() && m.pos().getZ() == at.getZ()).toList();
		helper.assertTrue(marks.size() == 1, marks.size() + " marks for one den");
		helper.assertTrue(marks.get(0).name().getString().equals("Raid den, tier 1, Ice, resting"), "the mark: " + marks.get(0).name().getString());
		level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
		boolean still = VillageMaps.marks(level, hall, 64).stream()
			.anyMatch(m -> m.kind() == VillageMaps.Kind.RAID_DEN && m.pos().getX() == at.getX() && m.pos().getZ() == at.getZ());
		helper.assertFalse(still, "the map still marks a broken den");
		helper.succeed();
	}

	/** The dens are a level's own: a den known in the Overworld isn't listed for the same place in the Nether. */
	//$ gametest_batch AREA '"qaDens1101Nether"'
	@GameTest(template = AREA, batch = "qaDens1101Nether")
	public void anOverworldDenIsntTheNethers(GameTestHelper helper) {
		List<BlockPos> placed = cleanup(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		BlockPos at = hall.offset(3, 0, 6);
		denAt(helper, at, RaidDenCompatTests.den("tier_two", "fire", true), placed);
		helper.assertTrue(listed(helper, hall, at).isPresent(), "the den isn't listed in its own level");
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "setup: no Nether");
		helper.assertTrue(RaidDens.near(nether, hall).stream().noneMatch(d -> d.pos().equals(at)), "the Overworld's den is listed in the Nether");
		helper.assertTrue(RaidDens.Sites.get(nether).dens().stream().noneMatch(d -> d.pos().equals(at)), "the Overworld's den is saved with the Nether");
		helper.succeed();
	}
}
