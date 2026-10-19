package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.guard.RaidDens;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * ROADMAP 38.2: the village knows the Cobblemon Raid Dens crystals round it. A den is found when its chunk loads
 * ({@link RaidDens#chunkLoaded}, then the level's tick), read by property name, listed on the Defence page and marked on
 * the village map; a den too far off isn't the village's; the list is saved. Dens of other tests may be near, so each
 * test looks for its own den by place, and takes it away again at the end.
 */
public class RaidDenCompatTests implements FabricGameTest {
	static final BlockPos HALL = new BlockPos(2, 2, 2);
	static final BlockPos DEN = new BlockPos(6, 2, 6);

	/** The den block with its states set by name. */
	static BlockState den(String tier, String type, boolean active) {
		Block block = BuiltInRegistries.BLOCK.getOptional(RaidDens.DEN).orElseThrow();
		BlockState state = block.defaultBlockState();
		state = with(state, "raid_tier", tier);
		state = with(state, "raid_type", type);
		return with(state, "is_active", Boolean.toString(active));
	}

	static BlockState with(BlockState state, String name, String value) {
		Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
		return property == null ? state : set(state, property, value);
	}

	private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String value) {
		return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
	}

	/** Places the hall and a den, and lets the chunk-load path find it. */
	static BlockPos[] village(GameTestHelper helper, BlockState den) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(DEN, den);
		BlockPos at = helper.absolutePos(DEN);
		RaidDens.chunkLoaded(level, level.getChunkAt(at));
		RaidDens.tick(level);
		return new BlockPos[] {helper.absolutePos(HALL), at};
	}

	static Optional<RaidDens.Den> listed(GameTestHelper helper, BlockPos hall, BlockPos den) {
		return RaidDens.near(helper.getLevel(), hall).stream().filter(d -> d.pos().equals(den)).findFirst();
	}

	/** Takes the den away: the next look drops it. */
	static void clear(GameTestHelper helper, BlockPos hall, BlockPos den) {
		helper.getLevel().setBlockAndUpdate(den, Blocks.AIR.defaultBlockState());
		helper.assertTrue(listed(helper, hall, den).isEmpty(), "a broken den is still listed");
	}

	static List<String> lines(ItemStack icon) {
		List<String> lines = new ArrayList<>();
		lines.add(icon.getHoverName().getString());
		ItemLore lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(line -> lines.add(line.getString()));
		}
		return lines;
	}

	/** A den near the hall is listed with its tier, type and state, on the Defence page too; broken, it's dropped. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidDenListed"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidDenListed")
	public void aDenNearAHallIsListed(GameTestHelper helper) {
		BlockPos[] at = village(helper, den("tier_three", "fire", true));
		Optional<RaidDens.Den> den = listed(helper, at[0], at[1]);
		helper.assertTrue(den.isPresent(), "the den isn't listed: " + RaidDens.near(helper.getLevel(), at[0]));
		helper.assertTrue(den.get().tier() == 3 && den.get().type().equals("fire") && den.get().active(), "the den: " + den.get());
		String line = RaidDens.describe(den.get()).getString();
		helper.assertTrue(line.equals("Raid den, tier 3, Fire, active"), "the den's line: " + line);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ChoiceMenu menu = VillageHallScreen.forTest(player, at[0]);
		DefencePage.render(menu, helper.getLevel(), at[0]);
		boolean shown = false;
		for (int slot : DefencePage.DENS) {
			List<String> lines = lines(menu.icon(slot));
			if (lines.get(0).equals(line)) {
				shown = true;
				helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("Found on day")), "the den's icon: " + lines);
				helper.assertTrue(menu.icon(slot).getOrDefault(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false), "an active den should glow");
			}
			for (String l : lines) {
				helper.assertFalse(l.contains("aliveworkplace.") || l.contains("%"), "slot " + slot + " shows a raw line: " + l);
			}
		}
		helper.assertTrue(shown, "the Defence page doesn't show the den");
		clear(helper, at[0], at[1]);
		helper.succeed();
	}

	/** A den that goes quiet (beaten) reads "resting" at the next look. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidDenResting"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidDenResting")
	public void aDenTurnedInactiveShowsInactive(GameTestHelper helper) {
		BlockPos[] at = village(helper, den("tier_two", "water", true));
		helper.assertTrue(listed(helper, at[0], at[1]).map(RaidDens.Den::active).orElse(false), "the den should start active");
		helper.getLevel().setBlockAndUpdate(at[1], den("tier_two", "water", false));
		Optional<RaidDens.Den> den = listed(helper, at[0], at[1]);
		helper.assertTrue(den.isPresent() && !den.get().active(), "the den: " + den);
		helper.assertTrue(RaidDens.describe(den.get()).getString().equals("Raid den, tier 2, Water, resting"), "the line: " + RaidDens.describe(den.get()).getString());
		clear(helper, at[0], at[1]);
		helper.succeed();
	}

	/** A den 200 blocks from the hall isn't the village's. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidDenFar"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidDenFar")
	public void aDenFarOffIsntListed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos far = hall.offset(200, 0, 0);
		level.setBlockAndUpdate(far, den("tier_one", "grass", true));
		RaidDens.chunkLoaded(level, level.getChunkAt(far));
		RaidDens.tick(level);
		helper.assertTrue(listed(helper, hall, far).isEmpty(), "a den 200 blocks off is listed");
		level.setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	/** The list is saved: a save and load keeps the den with its states and the day it was found. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidDenReload"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidDenReload")
	public void aReloadKeepsTheList(GameTestHelper helper) {
		BlockPos[] at = village(helper, den("tier_four", "dragon", true));
		RaidDens.Den den = listed(helper, at[0], at[1]).orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("not listed"));
		var registries = helper.getLevel().registryAccess();
		CompoundTag saved = RaidDens.Sites.get(helper.getLevel()).save(new CompoundTag(), registries);
		RaidDens.Sites loaded = RaidDens.Sites.load(saved, registries);
		helper.assertTrue(loaded.dens().contains(den), "after a reload: " + loaded.dens());
		// A save from before 2.2 has no dens.
		helper.assertTrue(RaidDens.Sites.load(new CompoundTag(), registries).dens().isEmpty(), "an old save has dens");
		clear(helper, at[0], at[1]);
		helper.succeed();
	}

	/** The village map marks the den with a magenta banner and its line, and the legend counts it. */
	//$ gametest_batch 'CompatGameTests.AREA' '"raidDenMap"'
	@GameTest(template = CompatGameTests.AREA, batch = "raidDenMap")
	public void theMapMarksTheDen(GameTestHelper helper) {
		BlockPos[] at = village(helper, den("tier_five", "ghost", true));
		Optional<VillageMaps.Mark> mark = VillageMaps.marks(helper.getLevel(), at[0], 64).stream()
			.filter(m -> m.kind() == VillageMaps.Kind.RAID_DEN && m.pos().getX() == at[1].getX() && m.pos().getZ() == at[1].getZ()).findFirst();
		helper.assertTrue(mark.isPresent(), "no mark for the den");
		helper.assertTrue(mark.get().name().getString().equals("Raid den, tier 5, Ghost, active"), "the mark's name: " + mark.get().name().getString());
		ItemStack map = VillageMaps.map(helper.getLevel(), at[0]);
		helper.assertTrue(lines(map).stream().anyMatch(l -> l.contains("raid dens")), "the map's legend: " + lines(map));
		clear(helper, at[0], at[1]);
		helper.succeed();
	}

	static boolean known(GameTestHelper helper, BlockPos den) {
		return RaidDens.Sites.get(helper.getLevel()).dens().stream().anyMatch(d -> d.pos().equals(den));
	}

	/**
	 * The known dens are read again every 10 seconds, not every tick: a den broken while nobody looks at the list stays
	 * listed between two looks and is dropped at the next.
	 */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '400' '"raidDenLook"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 400, batch = "raidDenLook")
	public void aBrokenDenIsDroppedAtTheNextLook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = village(helper, den("tier_one", "grass", true));
		helper.assertTrue(known(helper, at[1]), "the den isn't known");
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(level.getGameTime() % 200 != 0, "on a look's tick"))
			.thenExecute(() -> {
				level.setBlockAndUpdate(at[1], Blocks.AIR.defaultBlockState());
				RaidDens.tick(level);
				helper.assertTrue(known(helper, at[1]), "the dens were read again between two looks (at " + level.getGameTime() + ")");
			})
			.thenWaitUntil(() -> helper.assertFalse(known(helper, at[1]), "the broken den is still known"))
			.thenSucceed();
	}
}
