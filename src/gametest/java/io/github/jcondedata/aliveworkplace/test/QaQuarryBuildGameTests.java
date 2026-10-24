package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryBuilds;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA for the Quarry build (ROADMAP 36.12), written from the spec: the refill goes strictly bottom-up, deeper layers grow
 * deepslate and nothing outside the listed mix, the rate config at its extremes, player blocks (even ones that look like
 * the rock) stay, nothing outside the pit (rim, steps, air above the pit, a cut in the wall) is filled over a whole
 * refill, the Miner's finds reach the shed's chest, the refill state survives a save, and the switch off stops a hired
 * pit from growing while the Quarry Marker still works.
 */
public class QaQuarryBuildGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos ORIGIN = new BlockPos(2, 12, 2);

	/** Every block the pit may grow: the mix the spec lists, plain and deepslate. */
	private static final Set<Block> MIX = Set.of(Blocks.STONE, Blocks.COAL_ORE, Blocks.COPPER_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE,
		Blocks.REDSTONE_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE, Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_COPPER_ORE,
		Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);

	private record Setup(ServerLevel level, BlueprintData.Placement placement, BoundingBox pit, BlockPos furnace, BlockPos chest) {
	}

	/** The Quarry's ground (as in QuarryBuildGameTests): a granite ring round the pit, the shed's floor, furnace and chest. */
	private static Setup quarry(GameTestHelper helper, boolean full) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BoundingBox pit = QuarryBuilds.pit(placement);
		BlockPos o = placement.origin();
		for (BlockPos p : BlockPos.betweenClosed(o.getX() - 1, o.getY() - 1, o.getZ(), o.getX() + 16, o.getY() - 1, o.getZ() + 11)) {
			if (!pit.isInside(p)) {
				level.setBlockAndUpdate(p, Blocks.POLISHED_GRANITE.defaultBlockState());
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(pit.minX() - 1, pit.minY() - 1, pit.minZ() - 1, pit.maxX() + 1, pit.maxY(), pit.maxZ() + 1)) {
			level.setBlockAndUpdate(p, pit.isInside(p) ? (full ? Blocks.STONE : Blocks.AIR).defaultBlockState() : Blocks.POLISHED_GRANITE.defaultBlockState());
		}
		BlockPos furnace = QuarryBuilds.furnace(placement);
		BlockPos chest = furnace.north(2);
		for (BlockPos p : BlockPos.betweenClosed(o.getX() + 11, o.getY(), o.getZ() + 3, o.getX() + 15, o.getY(), o.getZ() + 9)) {
			level.setBlockAndUpdate(p, Blocks.SMOOTH_STONE.defaultBlockState());
		}
		level.setBlockAndUpdate(furnace, Blocks.BLAST_FURNACE.defaultBlockState());
		level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
		BuildSiteManager.get(level).recordFinished(QuarryBuilds.BLUEPRINT, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			QuarrySiteManager manager = QuarrySiteManager.get(level);
			manager.all().stream().filter(s -> placement.origin().equals(s.refillBuild()) || s.box().intersects(pit)).map(QuarrySite::id).toList()
				.forEach(manager::remove);
			QuarryBuilds.PER_DAY = 100;
			QuarryBuilds.ENABLED = true;
		});
		return new Setup(level, placement, pit, furnace, chest);
	}

	private static Villager miner(GameTestHelper helper, Setup s) {
		Villager villager = EntityType.VILLAGER.create(s.level());
		villager.moveTo(s.chest().getX() + 0.5, s.chest().getY(), s.chest().getZ() - 0.5);
		s.level().addFreshEntity(villager);
		Miners.employ(s.level(), villager, s.furnace());
		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		return villager;
	}

	private static QuarrySite siteOf(Setup s) {
		return QuarrySiteManager.get(s.level()).all().stream().filter(x -> s.placement().origin().equals(x.refillBuild())).findFirst().orElse(null);
	}

	/** A Quarry build's site on the pit, as the hire would make it. */
	private static QuarrySite refillSite(Setup s) {
		QuarrySite site = QuarrySiteManager.get(s.level()).create(UUID.randomUUID(), "", s.level().dimension().location(), s.pit(), s.pit().getYSpan());
		site.setRefillBuild(s.placement().origin());
		return site;
	}

	/** Grows the pit full (or as full as it gets) with the mod's own bottom-up growth. */
	private static int growAll(Setup s, QuarrySite site) {
		int total = 0;
		for (int i = 0; i < 100; i++) {
			int g = QuarryBuilds.grow(s.level(), site, 32);
			total += g;
			if (g == 0) {
				break;
			}
		}
		return total;
	}

	/**
	 * Grows a dug-out pit at {@code box} (air, outside the test area: cleared again after) full and returns how many of
	 * its layers are deepslate; fails if a layer is mixed, a deepslate layer sits above a stone one, a block is outside
	 * the mix or the pit didn't fill.
	 */
	private static int deepLayersIn(ServerLevel level, BoundingBox box) {
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		QuarrySite site = QuarrySiteManager.get(level).create(UUID.randomUUID(), "", level.dimension().location(), box, box.getYSpan());
		int volume = box.getXSpan() * box.getYSpan() * box.getZSpan();
		int grown = QuarryBuilds.grow(level, site, volume);
		QuarrySiteManager.get(level).remove(site.id());
		int deep = 0;
		String error = grown == volume ? null : grown + " grew of " + volume + " at y " + box.minY();
		for (int y = box.minY(); y <= box.maxY(); y++) {
			int deepHere = 0;
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int x = box.minX(); x <= box.maxX(); x++) {
					BlockState state = level.getBlockState(new BlockPos(x, y, z));
					if (!MIX.contains(state.getBlock()) && error == null) {
						error = "grew " + state + " outside the mix at y " + y;
					}
					deepHere += state.getBlock().builtInRegistryHolder().key().location().getPath().startsWith("deepslate") ? 1 : 0;
				}
			}
			int layer = box.getXSpan() * box.getZSpan();
			if (deepHere != 0 && deepHere != layer && error == null) {
				error = "layer y " + y + " is half deepslate: " + deepHere + " of " + layer;
			}
			if (deepHere == layer) {
				if (deep != y - box.minY() && error == null) {
					error = "a deepslate layer above a stone one at y " + y;
				}
				deep++;
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		if (error != null) {
			throw new net.minecraft.gametest.framework.GameTestAssertException(error);
		}
		return deep;
	}

	/**
	 * Strictly bottom-up: in a full pit, a hole at the top and a hole at the bottom; one block grows into the bottom hole,
	 * the top one stays open until the bottom one is filled. Then a hole in the middle layer comes before a new top one.
	 */
	//$ gametest_ticks_batch HUGE_AREA '100' '"qa_quarry_bottom_up"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 100, batch = "qa_quarry_bottom_up")
	public void aHoleAtTheTopWaitsForTheHoleBelow(GameTestHelper helper) {
		Setup s = quarry(helper, true);
		BoundingBox pit = s.pit();
		BlockPos top = new BlockPos(pit.minX(), pit.maxY(), pit.minZ()); // the first spot a top-down/x-first scan would find
		BlockPos bottom = new BlockPos(pit.maxX(), pit.minY(), pit.maxZ()); // the last spot of the bottom layer
		BlockPos middle = new BlockPos(pit.maxX() - 1, pit.minY() + 5, pit.maxZ() - 1);
		s.level().setBlockAndUpdate(top, Blocks.AIR.defaultBlockState());
		s.level().setBlockAndUpdate(bottom, Blocks.AIR.defaultBlockState());
		s.level().setBlockAndUpdate(middle, Blocks.AIR.defaultBlockState());
		QuarrySite site = refillSite(s);
		helper.assertTrue(QuarryBuilds.grow(s.level(), site, 1) == 1, "nothing grew into a pit with three holes");
		helper.assertFalse(s.level().getBlockState(bottom).isAir(), "the bottom hole wasn't the first to grow back");
		helper.assertTrue(s.level().getBlockState(middle).isAir() && s.level().getBlockState(top).isAir(), "a higher hole grew before the bottom one");
		helper.assertTrue(QuarryBuilds.grow(s.level(), site, 1) == 1, "the second block didn't grow");
		helper.assertFalse(s.level().getBlockState(middle).isAir(), "the middle hole wasn't next");
		helper.assertTrue(s.level().getBlockState(top).isAir(), "the top hole grew before the middle one");
		helper.assertTrue(QuarryBuilds.grow(s.level(), site, 5) == 1, "the last hole: exactly one block should grow");
		helper.assertTrue(MIX.contains(s.level().getBlockState(top).getBlock()), "the top grew " + s.level().getBlockState(top));
		helper.assertTrue(QuarryBuilds.grow(s.level(), site, 5) == 0, "a full pit grew more");
		helper.succeed();
	}

	/**
	 * A dug-out pit grown full: every block is in the listed mix, the lower layers are deepslate kinds and the upper ones
	 * never are; nothing outside the pit changes: the rim, the stone steps on it, a cut in the wall (air beside the pit)
	 * and the air above the pit's top all stay as they were.
	 */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qa_quarry_full_refill"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qa_quarry_full_refill")
	public void aWholeRefillStaysInThePitAndInTheMix(GameTestHelper helper) {
		Setup s = quarry(helper, false);
		BoundingBox pit = s.pit();
		ServerLevel level = s.level();
		// The steps: a cut in the pit's west wall going down, with stairs on the rim; and air above the pit
		Map<BlockPos, BlockState> outside = new HashMap<>();
		for (int i = 0; i < 4; i++) {
			BlockPos cut = new BlockPos(pit.minX() - 1, pit.maxY() - i, pit.minZ() + 2 + i);
			level.setBlockAndUpdate(cut, Blocks.AIR.defaultBlockState());
			BlockPos step = cut.below();
			level.setBlockAndUpdate(step, Blocks.STONE_BRICK_STAIRS.defaultBlockState());
		}
		for (BlockPos p : BlockPos.betweenClosed(pit.minX() - 1, pit.minY() - 1, pit.minZ() - 1, pit.maxX() + 1, pit.maxY() + 3, pit.maxZ() + 1)) {
			if (!pit.isInside(p)) {
				outside.put(p.immutable(), level.getBlockState(p));
			}
		}
		QuarrySite site = refillSite(s);
		int volume = pit.getXSpan() * pit.getYSpan() * pit.getZSpan();
		int grown = growAll(s, site);
		helper.assertTrue(grown == volume, grown + " grew back of " + volume);
		helper.assertTrue(site.grownTotal() == volume, "grownTotal " + site.grownTotal() + " of " + volume);
		int deepLayers = 0;
		int ores = 0;
		for (int y = pit.minY(); y <= pit.maxY(); y++) {
			int deepHere = 0;
			for (int z = pit.minZ(); z <= pit.maxZ(); z++) {
				for (int x = pit.minX(); x <= pit.maxX(); x++) {
					BlockState state = level.getBlockState(new BlockPos(x, y, z));
					helper.assertTrue(MIX.contains(state.getBlock()), "grew " + state + " outside the mix at y " + y);
					if (state.getBlock().builtInRegistryHolder().key().location().getPath().startsWith("deepslate")) {
						deepHere++;
					}
					if (!state.is(Blocks.STONE) && !state.is(Blocks.DEEPSLATE)) {
						ores++;
					}
				}
			}
			int layer = pit.getXSpan() * pit.getZSpan();
			helper.assertTrue(deepHere == 0 || deepHere == layer, "layer " + (y - pit.minY()) + " is half deepslate: " + deepHere + " of " + layer);
			if (deepHere == layer) {
				helper.assertTrue(deepLayers == y - pit.minY(), "a deepslate layer above a stone one at layer " + (y - pit.minY()));
				deepLayers++;
			}
		}
		// The test world sits below y 0 (deepslate country, like vanilla): there the whole pit is deepslate
		helper.assertTrue(pit.maxY() >= 0 || deepLayers == pit.getYSpan(), deepLayers + " deepslate layers of " + pit.getYSpan() + " below y 0");
		// The wiki: deepslate in the pit's lower third or below y 0. A 9-tall pit at y 1-9: its lower 3 layers; one at
		// y -5 to 3: the 5 layers below y 0 (y 0 itself is above that pit's lower third, so stone)
		helper.assertTrue(deepLayersIn(level, new BoundingBox(pit.minX(), 1, pit.minZ(), pit.maxX(), 9, pit.maxZ())) == 3,
			"a pit at y 1-9 should have 3 deepslate layers (its lower third)");
		helper.assertTrue(deepLayersIn(level, new BoundingBox(pit.minX(), -5, pit.minZ(), pit.maxX(), 3, pit.maxZ())) == 5,
			"a pit at y -5 to 3 should have 5 deepslate layers (below y 0)");
		helper.assertTrue(ores > volume / 20 && ores < volume / 3, ores + " ores in " + volume + " (some, not most)");
		for (Map.Entry<BlockPos, BlockState> e : outside.entrySet()) {
			helper.assertTrue(level.getBlockState(e.getKey()) == e.getValue(), "outside the pit at " + helper.relativePos(e.getKey()) + ": "
				+ e.getValue() + " became " + level.getBlockState(e.getKey()));
		}
		helper.succeed();
	}

	/**
	 * Blocks a player put in the pit stay, whatever they are: stone and deepslate ore that look like the rock, planks,
	 * cobblestone and a chest; growth goes round them, and with the pit otherwise full nothing more grows.
	 */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qa_quarry_player_blocks"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qa_quarry_player_blocks")
	public void playerBlocksInThePitAreNeverReplaced(GameTestHelper helper) {
		Setup s = quarry(helper, false);
		BoundingBox pit = s.pit();
		ServerLevel level = s.level();
		Map<BlockPos, BlockState> placed = new HashMap<>();
		placed.put(new BlockPos(pit.minX(), pit.minY(), pit.minZ()), Blocks.STONE.defaultBlockState());
		placed.put(new BlockPos(pit.minX() + 3, pit.minY() + 1, pit.minZ() + 4), Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState());
		placed.put(new BlockPos(pit.minX() + 1, pit.minY() + 6, pit.minZ() + 1), Blocks.OAK_PLANKS.defaultBlockState());
		placed.put(new BlockPos(pit.maxX(), pit.maxY(), pit.maxZ()), Blocks.COBBLESTONE.defaultBlockState());
		placed.put(new BlockPos(pit.minX() + 5, pit.minY() + 3, pit.minZ() + 5), Blocks.CHEST.defaultBlockState());
		for (Map.Entry<BlockPos, BlockState> e : placed.entrySet()) {
			level.setBlockAndUpdate(e.getKey(), e.getValue());
		}
		QuarrySite site = refillSite(s);
		int volume = pit.getXSpan() * pit.getYSpan() * pit.getZSpan();
		int grown = growAll(s, site);
		helper.assertTrue(grown == volume - placed.size(), grown + " grew of " + (volume - placed.size()) + " open blocks");
		for (Map.Entry<BlockPos, BlockState> e : placed.entrySet()) {
			helper.assertTrue(level.getBlockState(e.getKey()) == e.getValue(), "the player's " + e.getValue() + " became " + level.getBlockState(e.getKey()));
		}
		// Mined again later, the player's blocks still stay through another refill
		for (BlockPos p : BlockPos.betweenClosed(pit.minX(), pit.minY(), pit.minZ(), pit.maxX(), pit.minY() + 2, pit.maxZ())) {
			if (!placed.containsKey(p.immutable())) {
				level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			}
		}
		growAll(s, site);
		for (Map.Entry<BlockPos, BlockState> e : placed.entrySet()) {
			helper.assertTrue(level.getBlockState(e.getKey()) == e.getValue(), "after a second refill the player's " + e.getValue() + " became "
				+ level.getBlockState(e.getKey()));
		}
		helper.succeed();
	}

	/**
	 * The refill rate: the config clamps it to 0..2400 from both sides; 0 grows nothing even after many days; 1 a day grows
	 * nothing a tick before the day is up and one block at it; 2400 a day one every 10 ticks, not at 9.
	 */
	//$ gametest_ticks_batch HUGE_AREA '100' '"qa_quarry_rate"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 100, batch = "qa_quarry_rate")
	public void theRefillRateAtItsExtremes(GameTestHelper helper) {
		helper.assertTrue(WorkplaceConfig.parse("{\"quarryRegrowth\": -5}").quarryRegrowth == 0, "-5 isn't clamped to 0");
		helper.assertTrue(WorkplaceConfig.parse("{\"quarryRegrowth\": 0}").quarryRegrowth == 0, "0 isn't kept");
		helper.assertTrue(WorkplaceConfig.parse("{\"quarryRegrowth\": 1}").quarryRegrowth == 1, "1 isn't kept");
		helper.assertTrue(WorkplaceConfig.parse("{\"quarryRegrowth\": 2400}").quarryRegrowth == 2400, "2400 isn't kept");
		helper.assertTrue(WorkplaceConfig.parse("{\"quarryRegrowth\": 2401}").quarryRegrowth == 2400, "2401 isn't clamped to 2400");
		helper.assertTrue(WorkplaceConfig.parse("{\"quarryRegrowth\": 99999}").quarryRegrowth == 2400, "99999 isn't clamped to 2400");
		WorkplaceConfig config = WorkplaceConfig.parse("{\"quarryRegrowth\": 7, \"quarryBuild\": false}");
		config.apply();
		boolean applied = QuarryBuilds.PER_DAY == 7 && !QuarryBuilds.ENABLED;
		new WorkplaceConfig().apply();
		helper.assertTrue(applied, "apply() didn't put quarryRegrowth/quarryBuild into effect");
		helper.assertTrue(QuarryBuilds.PER_DAY == 100 && QuarryBuilds.ENABLED, "defaults not restored");

		Setup s = quarry(helper, false);
		QuarrySite site = refillSite(s);
		long now = s.level().getGameTime();
		QuarryBuilds.PER_DAY = 0;
		site.setLastGrowth(now - 10L * QuarryBuilds.TICKS_PER_DAY);
		helper.assertTrue(QuarryBuilds.regrow(s.level(), site, now) == 0, "at 0 a day something grew");
		QuarryBuilds.PER_DAY = 1;
		site.setLastGrowth(now - (QuarryBuilds.TICKS_PER_DAY - 1));
		helper.assertTrue(QuarryBuilds.regrow(s.level(), site, now) == 0, "at 1 a day a block grew before the day was up");
		site.setLastGrowth(now - QuarryBuilds.TICKS_PER_DAY);
		helper.assertTrue(QuarryBuilds.regrow(s.level(), site, now) == 1, "at 1 a day a day's due didn't grow exactly one");
		QuarryBuilds.PER_DAY = 2400;
		site.setLastGrowth(now - 9);
		helper.assertTrue(QuarryBuilds.regrow(s.level(), site, now) == 0, "at 2400 a day a block grew after 9 ticks");
		site.setLastGrowth(now - 10);
		helper.assertTrue(QuarryBuilds.regrow(s.level(), site, now) == 1, "at 2400 a day 10 ticks didn't grow one");
		helper.assertTrue(site.grownTotal() == 2, "grownTotal " + site.grownTotal());
		helper.succeed();
	}

	/**
	 * Saved and loaded with growth due: the loaded site grows exactly what was due (not again from scratch, not lost),
	 * keeps counting where it was, and its pit's next block is the same one the unsaved site would grow.
	 */
	//$ gametest_ticks_batch HUGE_AREA '100' '"qa_quarry_save"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 100, batch = "qa_quarry_save")
	public void theRefillStateSurvivesASave(GameTestHelper helper) {
		Setup s = quarry(helper, false);
		QuarryBuilds.PER_DAY = 2400;
		QuarrySite site = refillSite(s);
		QuarryBuilds.grow(s.level(), site, 5);
		long now = s.level().getGameTime();
		site.setLastGrowth(now - 100); // 10 due
		QuarrySite loaded = QuarrySite.load(site.save());
		helper.assertTrue(loaded != null && loaded.refills() && s.placement().origin().equals(loaded.refillBuild()), "the build was lost");
		helper.assertTrue(loaded.grownTotal() == 5 && loaded.lastGrowth() == now - 100 && loaded.box().equals(s.pit()), "the refill state was lost");
		QuarrySiteManager.get(s.level()).remove(site.id());
		QuarrySiteManager.get(s.level()).restore(loaded);
		int grown = QuarryBuilds.regrow(s.level(), loaded, now);
		helper.assertTrue(grown == 10, grown + " grew after the reload, 10 were due");
		helper.assertTrue(loaded.grownTotal() == 15, "grownTotal " + loaded.grownTotal());
		helper.assertTrue(QuarryBuilds.regrow(s.level(), loaded, now) == 0, "the due grew twice after the reload");
		int bottom = 0;
		for (BlockPos p : BlockPos.betweenClosed(s.pit().minX(), s.pit().minY(), s.pit().minZ(), s.pit().maxX(), s.pit().maxY(), s.pit().maxZ())) {
			if (!s.level().getBlockState(p).isAir()) {
				helper.assertTrue(p.getY() == s.pit().minY(), "grew above the bottom layer at " + helper.relativePos(p.immutable()));
				bottom++;
			}
		}
		helper.assertTrue(bottom == 15, bottom + " blocks in the pit, 15 expected");
		helper.succeed();
	}

	/**
	 * The switch off, with a Miner already working the pit: nothing grows back in it by the level's own tick, no second
	 * site is made, and a Quarry Marker handed to them still starts a free area's quarry.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"qa_quarry_off"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "qa_quarry_off")
	public void switchedOffAHiredPitStopsGrowing(GameTestHelper helper) {
		Setup s = quarry(helper, false);
		Villager villager = miner(helper, s);
		QuarryBuilds.hire(s.level());
		QuarrySite site = siteOf(s);
		helper.assertTrue(site != null && villager.getUUID().equals(site.miner()), "the Miner wasn't given the pit with the switch on");
		QuarryBuilds.ENABLED = false;
		QuarryBuilds.PER_DAY = 2400;
		site.setLastGrowth(s.level().getGameTime());
		int before = site.grownTotal();
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(site.grownTotal() == before, (site.grownTotal() - before) + " grew back with the switch off");
			int filled = 0;
			for (BlockPos p : BlockPos.betweenClosed(s.pit().minX(), s.pit().minY(), s.pit().minZ(), s.pit().maxX(), s.pit().maxY(), s.pit().maxZ())) {
				if (!s.level().getBlockState(p).isAir()) {
					filled++;
				}
			}
			helper.assertTrue(filled == 0, filled + " blocks appeared in the pit with the switch off");
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			BlockPos a = helper.absolutePos(new BlockPos(22, 2, 22));
			BlockPos b = helper.absolutePos(new BlockPos(23, 2, 23));
			for (BlockPos p : BlockPos.betweenClosed(a, b)) {
				s.level().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
			}
			ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
			marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(s.level().dimension().location()), Optional.of(a), Optional.of(b), 4));
			Miners.assign(player, villager, marker);
			QuarrySite free = Miners.activeSite(s.level(), villager);
			helper.assertTrue(free != null && !free.refills() && free.box().isInside(a), "with the switch off the marker's quarry wasn't started: "
				+ (free == null ? "none" : free.box()));
			QuarryBuilds.hire(s.level());
			helper.assertTrue(siteOf(s) == null, "with the switch off the pit was given again");
			Miners.cancel(s.level(), free);
			helper.succeed();
		});
	}

	/**
	 * The shed's blast furnace broken mid-shift: the Miner stops working the pit (no more blocks mined a
	 * minute later, 1200 to 1500 ticks after; the brain lets them finish the shift first),
	 * nothing outside the pit is touched, and with the furnace gone the pit isn't given to them again.
	 */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_quarry_furnace_broken"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_quarry_furnace_broken")
	public void theShedsFurnaceBrokenMidShift(GameTestHelper helper) {
		Setup s = quarry(helper, true);
		QuarryBuilds.PER_DAY = 0;
		miner(helper, s);
		helper.runAfterDelay(5, () -> QuarryBuilds.hire(s.level()));
		long[] brokenAt = {-1};
		int[] minedAt = {-1};
		helper.onEachTick(() -> {
			QuarrySite site = siteOf(s);
			long t = helper.getTick();
			if (brokenAt[0] < 0 && site != null && site.mined() >= 2) {
				s.level().destroyBlock(s.furnace(), false);
				brokenAt[0] = t;
			}
			if (brokenAt[0] >= 0 && t == brokenAt[0] + 1200) {
				minedAt[0] = site == null ? -2 : site.mined();
			}
			if (minedAt[0] != -1 && t == brokenAt[0] + 1500) {
				QuarrySite now = siteOf(s);
				int after = now == null ? -2 : now.mined();
				helper.assertTrue(minedAt[0] == -2 || after == minedAt[0], "the Miner kept mining with the furnace broken: " + minedAt[0] + " then " + after);
				helper.assertTrue(s.level().getBlockState(s.furnace()).isAir(), "the furnace came back");
				for (BlockPos p : BlockPos.betweenClosed(s.pit().minX() - 1, s.pit().minY() - 1, s.pit().minZ() - 1, s.pit().maxX() + 1, s.pit().maxY(), s.pit().maxZ() + 1)) {
					if (!s.pit().isInside(p)) {
						helper.assertTrue(s.level().getBlockState(p).is(Blocks.POLISHED_GRANITE), "outside the pit changed at " + helper.relativePos(p.immutable()));
					}
				}
				helper.succeed();
			}
		});
	}

	/**
	 * Through the real job: the Miner at the shed's blast furnace digs the ore at the top of a pit that's otherwise dug out,
	 * the coal they find reaches the shed's chest, and then they wait for the pit to grow back.
	 */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"qa_quarry_chest"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "qa_quarry_chest")
	public void theMinersFindsReachTheShedsChest(GameTestHelper helper) {
		Setup s = quarry(helper, false);
		QuarryBuilds.PER_DAY = 0;
		BoundingBox pit = s.pit();
		for (int i = 0; i < 4; i++) {
			s.level().setBlockAndUpdate(new BlockPos(pit.maxX() - i, pit.maxY(), pit.minZ() + 3), Blocks.COAL_ORE.defaultBlockState());
		}
		miner(helper, s);
		helper.runAfterDelay(5, () -> QuarryBuilds.hire(s.level()));
		helper.succeedWhen(() -> {
			QuarrySite site = siteOf(s);
			helper.assertTrue(site != null, "the Miner wasn't given the pit");
			int coal = 0;
			if (s.level().getBlockEntity(s.chest()) instanceof Container chest) {
				for (int i = 0; i < chest.getContainerSize(); i++) {
					if (chest.getItem(i).is(Items.COAL)) {
						coal += chest.getItem(i).getCount();
					}
				}
			}
			helper.assertTrue(coal >= 4, coal + " coal in the shed's chest (4 coal ore were in the pit), status " + site.status());
			for (int i = 0; i < 4; i++) {
				helper.assertTrue(s.level().getBlockState(new BlockPos(pit.maxX() - i, pit.maxY(), pit.minZ() + 3)).isAir(), "an ore wasn't mined");
			}
			// Dug to the bottom: they wait for the pit to grow back (the guide: "they wait at the shed")
			helper.assertTrue(site.status() == QuarrySite.Status.REFILLING, "dug out, the status is " + site.status() + ", not REFILLING");
		});
	}
}
