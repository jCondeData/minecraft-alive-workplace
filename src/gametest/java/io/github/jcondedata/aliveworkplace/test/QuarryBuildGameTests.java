package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryBuilds;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The Quarry build (ROADMAP 36.12): the blueprint lists in the Blueprint Table with its Jobs line; the Miner whose job
 * block is its blast furnace works its pit without a marker; what they dug grows back slowly from the bottom up in a
 * weighted mix (a fixed random), as fast as {@code quarryRegrowth} says, and a full pit takes days; nothing outside the
 * pit is touched and a block a player put in it stays; it survives a save; {@code quarryBuild} off stops it all; and the
 * Quarry Marker still works for a free area.
 */
public class QuarryBuildGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	/** Where the build's y 0 is in the test area: its pit runs y 2-11. */
	private static final BlockPos ORIGIN = new BlockPos(2, 12, 2);

	private record Setup(ServerLevel level, BlueprintData.Placement placement, BoundingBox pit, BlockPos furnace, BlockPos chest) {
	}

	/**
	 * The ground of a Quarry: the pit full of stone ({@code full}) or dug out, polished granite round it on every layer
	 * and at ground level (what isn't the pit), the shed's floor, blast furnace and chest, and the build recorded as
	 * finished. Everything is undone when the test ends.
	 */
	private static Setup quarry(GameTestHelper helper, boolean full) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BoundingBox pit = QuarryBuilds.pit(placement);
		BlockPos o = placement.origin();
		// The ground at the build's y -1 (the pit's top) round it all, and the granite ring down the pit's sides
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
		// The shed's floor (the build's y 0)
		for (BlockPos p : BlockPos.betweenClosed(o.getX() + 11, o.getY(), o.getZ() + 3, o.getX() + 15, o.getY(), o.getZ() + 9)) {
			level.setBlockAndUpdate(p, Blocks.SMOOTH_STONE.defaultBlockState());
		}
		level.setBlockAndUpdate(furnace, Blocks.BLAST_FURNACE.defaultBlockState());
		level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
		BuildSiteManager.get(level).recordFinished(QuarryBuilds.BLUEPRINT, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			QuarrySiteManager manager = QuarrySiteManager.get(level);
			manager.all().stream().filter(s -> placement.origin().equals(s.refillBuild())).map(QuarrySite::id).toList().forEach(manager::remove);
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

	/** Every block of the granite ring round the pit is still granite. */
	private static void ringUntouched(GameTestHelper helper, Setup s) {
		BoundingBox pit = s.pit();
		for (BlockPos p : BlockPos.betweenClosed(pit.minX() - 1, pit.minY() - 1, pit.minZ() - 1, pit.maxX() + 1, pit.maxY(), pit.maxZ() + 1)) {
			if (!pit.isInside(p)) {
				helper.assertTrue(s.level().getBlockState(p).is(Blocks.POLISHED_GRANITE), "a block outside the pit changed at " + helper.relativePos(p.immutable())
					+ ": " + s.level().getBlockState(p));
			}
		}
	}

	/** The blueprint: listed in the Table with the Miner on its Jobs line, nothing drawn in its pit, the furnace where the code looks. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theQuarryIsInTheTableWithItsJobsLine(GameTestHelper helper) {
		TablePayloads.Details details = TableServer.details(helper.getLevel(), null, QuarryBuilds.BLUEPRINT).orElseThrow();
		helper.assertTrue(details.jobs().stream().anyMatch(b -> b.jobs().contains(BuiltInRegistries.VILLAGER_PROFESSION.getKey(io.github.jcondedata.aliveworkplace.registry.ModVillagers.MINER))),
			"the Quarry's Jobs line has no Miner: " + details.jobs());
		helper.assertTrue(StarterBlueprints.ONE_TIER.contains(StarterBlueprints.QUARRY) && StarterBlueprints.offered(QuarryBuilds.BLUEPRINT), "the Quarry isn't offered");
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), QuarryBuilds.BLUEPRINT).orElseThrow();
		BoundingBox pit = BoundingBox.fromCorners(QuarryBuilds.PIT_MIN, QuarryBuilds.PIT_MAX);
		boolean furnace = false;
		for (Blueprint.Entry e : blueprint.blocks()) {
			helper.assertFalse(pit.isInside(e.pos()), "the blueprint draws " + e.state() + " in the pit at " + e.pos());
			furnace |= e.pos().equals(QuarryBuilds.FURNACE) && e.state().is(Blocks.BLAST_FURNACE);
		}
		helper.assertTrue(furnace, "no blast furnace at " + QuarryBuilds.FURNACE);
		helper.succeed();
	}

	/** The Miner whose job block is the Quarry's blast furnace takes its pit by themselves, and mines it; nothing outside is touched. */
	//$ gametest_ticks_batch HUGE_AREA '1600' '"quarry_build_mines"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1600, batch = "quarry_build_mines")
	public void theMinerMinesTheQuarrysPit(GameTestHelper helper) {
		Setup s = quarry(helper, true);
		QuarryBuilds.PER_DAY = 0;
		Villager villager = miner(helper, s);
		helper.runAfterDelay(5, () -> {
			QuarryBuilds.hire(s.level());
			QuarrySite site = siteOf(s);
			helper.assertTrue(site != null && villager.getUUID().equals(site.miner()) && site.refills(), "the Miner wasn't given the pit");
			helper.assertTrue(site.box().equals(s.pit()), "the quarry isn't the pit: " + site.box());
		});
		helper.succeedWhen(() -> {
			QuarrySite site = siteOf(s);
			helper.assertTrue(site != null && site.mined() >= 6, "mined " + (site == null ? "-" : site.mined()));
			ringUntouched(helper, s);
		});
	}

	/**
	 * A dug-out pit grows back from the bottom up as fast as {@code quarryRegrowth} says (every 10 ticks at 2400 a day),
	 * the same blocks for the same site (a fixed random); a block a player put in the pit stays, nothing outside changes.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"quarry_build_grows"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "quarry_build_grows")
	public void minedBlocksGrowBackFromTheBottomUp(GameTestHelper helper) {
		Setup s = quarry(helper, false);
		BlockPos planks = new BlockPos(s.pit().minX() + 2, s.pit().minY(), s.pit().minZ() + 2);
		s.level().setBlockAndUpdate(planks, Blocks.OAK_PLANKS.defaultBlockState());
		QuarryBuilds.PER_DAY = 2400;
		QuarrySite site = QuarrySiteManager.get(s.level()).create(UUID.randomUUID(), "", s.level().dimension().location(), s.pit(), s.pit().getYSpan());
		site.setRefillBuild(s.placement().origin());
		long now = s.level().getGameTime();
		QuarryBuilds.regrow(s.level(), site, now);
		site.setLastGrowth(now - 200); // 20 blocks due
		int grown = QuarryBuilds.regrow(s.level(), site, now);
		helper.assertTrue(grown == 20, grown + " grew back, 20 were due");
		int bottom = 0;
		for (BlockPos p : BlockPos.betweenClosed(s.pit().minX(), s.pit().minY(), s.pit().minZ(), s.pit().maxX(), s.pit().maxY(), s.pit().maxZ())) {
			BlockState state = s.level().getBlockState(p);
			if (!state.isAir() && !p.equals(planks)) {
				helper.assertTrue(p.getY() == s.pit().minY(), "grew at " + helper.relativePos(p.immutable()) + " above the bottom layer first");
				helper.assertTrue(state.is(Blocks.DEEPSLATE) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("ore"), "grew " + state);
				bottom++;
			}
		}
		helper.assertTrue(bottom == 20, bottom + " blocks in the bottom layer");
		helper.assertTrue(s.level().getBlockState(planks).is(Blocks.OAK_PLANKS), "the player's planks were replaced");
		ringUntouched(helper, s);
		// The same site grows the same blocks: what grew is fixed by the site and the spot
		Map<BlockPos, Block> first = new HashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(s.pit().minX(), s.pit().minY(), s.pit().minZ(), s.pit().maxX(), s.pit().minY(), s.pit().maxZ())) {
			first.put(p.immutable(), s.level().getBlockState(p).getBlock());
		}
		helper.assertTrue(first.values().stream().anyMatch(b -> b == Blocks.DEEPSLATE), "no deepslate in the bottom layer");
		// Over time, by the level's own tick (every 10 ticks at 2400 a day)
		int before = site.grownTotal();
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(site.grownTotal() >= before + 10 && site.grownTotal() <= before + 25,
				"in 200 ticks " + (site.grownTotal() - before) + " grew back (about 20 expected)");
			QuarrySiteManager.get(s.level()).remove(site.id());
			helper.succeed();
		});
	}

	/** At the default rate a pit dug out to the bottom takes days to fill: a whole day's due grows back only a day's worth. */
	//$ gametest_ticks_batch HUGE_AREA '200' '"quarry_build_days"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "quarry_build_days")
	public void aFullPitTakesDays(GameTestHelper helper) {
		Setup s = quarry(helper, false);
		int volume = s.pit().getXSpan() * s.pit().getYSpan() * s.pit().getZSpan();
		helper.assertTrue(new io.github.jcondedata.aliveworkplace.WorkplaceConfig().quarryRegrowth == 100, "the default rate isn't 100 a day");
		helper.assertTrue(QuarryBuilds.daysToRefill(volume, 100) >= 5, "a " + volume + "-block pit refills in " + QuarryBuilds.daysToRefill(volume, 100) + " days");
		QuarrySite site = QuarrySiteManager.get(s.level()).create(UUID.randomUUID(), "", s.level().dimension().location(), s.pit(), s.pit().getYSpan());
		site.setRefillBuild(s.placement().origin());
		long now = s.level().getGameTime();
		site.setLastGrowth(now - QuarryBuilds.TICKS_PER_DAY);
		int total = 0;
		for (int i = 0; i < 20; i++) {
			total += QuarryBuilds.regrow(s.level(), site, now);
		}
		helper.assertTrue(total == 100, "a day grew back " + total + " of " + volume + " (100 a day)");
		helper.assertTrue(QuarryBuilds.regrow(s.level(), site, now) == 0, "more than a day's worth grew");
		QuarrySiteManager.get(s.level()).remove(site.id());
		helper.succeed();
	}

	/** The mix (a fixed random): mostly stone, then coal, copper, iron; a diamond is rare; deep down it's deepslate. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theRockGrowsBackInAWeightedMix(GameTestHelper helper) {
		RandomSource random = RandomSource.create(3612);
		Map<Block, Integer> counts = new HashMap<>();
		for (int i = 0; i < 4000; i++) {
			counts.merge(QuarryBuilds.pick(random, false).getBlock(), 1, Integer::sum);
		}
		int stone = counts.getOrDefault(Blocks.STONE, 0);
		helper.assertTrue(stone > 3000 && stone < 3500, stone + " stone of 4000");
		int coal = counts.getOrDefault(Blocks.COAL_ORE, 0);
		int copper = counts.getOrDefault(Blocks.COPPER_ORE, 0);
		int iron = counts.getOrDefault(Blocks.IRON_ORE, 0);
		int diamond = counts.getOrDefault(Blocks.DIAMOND_ORE, 0);
		helper.assertTrue(coal > copper && copper > iron && iron > diamond && diamond > 0 && diamond < 60,
			"coal " + coal + ", copper " + copper + ", iron " + iron + ", diamond " + diamond);
		helper.assertTrue(counts.getOrDefault(Blocks.GOLD_ORE, 0) > 0 && counts.getOrDefault(Blocks.REDSTONE_ORE, 0) > 0, "no gold or redstone: " + counts);
		for (int i = 0; i < 500; i++) {
			BlockState deep = QuarryBuilds.pick(random, true);
			helper.assertTrue(BuiltInRegistries.BLOCK.getKey(deep.getBlock()).getPath().startsWith("deepslate"), "deep down grew " + deep);
		}
		helper.succeed();
	}

	/** A quarry build's site saves its build and growth; a quarry saved before 36.12 loads as a plain one. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aQuarryBuildSurvivesASave(GameTestHelper helper) {
		QuarrySite site = new QuarrySite(UUID.randomUUID(), UUID.randomUUID(), "Jesse", helper.getLevel().dimension().location(),
			new BoundingBox(0, 50, 0, 7, 59, 8), 10);
		site.setRefillBuild(new BlockPos(5, 61, 7));
		site.setLastGrowth(1234L);
		site.countGrown();
		site.countGrown();
		CompoundTag tag = site.save();
		QuarrySite back = QuarrySite.load(tag);
		helper.assertTrue(back != null && back.refills() && new BlockPos(5, 61, 7).equals(back.refillBuild()), "the build was lost");
		helper.assertTrue(back.lastGrowth() == 1234L && back.grownTotal() == 2 && back.grownSincePass() == 2, "the growth was lost");
		tag.remove("refill_build");
		tag.remove("last_growth");
		tag.remove("grown");
		tag.remove("grown_since_pass");
		QuarrySite old = QuarrySite.load(tag);
		helper.assertTrue(old != null && !old.refills() && old.lastGrowth() == QuarrySite.NEVER && old.grownTotal() == 0, "an old quarry loads as a quarry build");
		helper.succeed();
	}

	/** Mid-dig, saved and loaded again (the site and the miner): the Miner carries on with the same pit. */
	//$ gametest_ticks_batch HUGE_AREA '1600' '"quarry_build_reload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1600, batch = "quarry_build_reload")
	public void theQuarryBuildCarriesOnAfterAReload(GameTestHelper helper) {
		Setup s = quarry(helper, true);
		QuarryBuilds.PER_DAY = 0;
		Villager[] miner = {miner(helper, s)};
		int[] minedBefore = {-1};
		helper.runAfterDelay(5, () -> QuarryBuilds.hire(s.level()));
		helper.onEachTick(() -> {
			QuarrySite site = siteOf(s);
			if (minedBefore[0] < 0 && site != null && site.mined() >= 2) {
				ServerLevel level = s.level();
				CompoundTag saved = new CompoundTag();
				helper.assertTrue(miner[0].save(saved), "the miner couldn't be saved");
				miner[0].remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
				QuarrySite loaded = QuarrySite.load(site.save());
				QuarrySiteManager.get(level).remove(site.id());
				QuarrySiteManager.get(level).restore(loaded);
				net.minecraft.world.entity.Entity back = EntityType.loadEntityRecursive(saved, level, e -> e);
				helper.assertTrue(back instanceof Villager && level.addFreshEntity(back), "the miner couldn't be loaded again");
				miner[0] = (Villager) back;
				minedBefore[0] = loaded.mined();
			}
		});
		helper.succeedWhen(() -> {
			QuarrySite site = siteOf(s);
			helper.assertTrue(minedBefore[0] >= 0 && site != null && site.refills() && site.mined() >= minedBefore[0] + 3,
				"after the reload: " + (site == null ? "no site" : site.mined() + " mined, before " + minedBefore[0]));
			helper.assertTrue(QuarrySiteManager.get(s.level()).all().stream().filter(x -> s.placement().origin().equals(x.refillBuild())).count() == 1,
				"a second quarry was started for the same build");
			ringUntouched(helper, s);
		});
	}

	/** {@code quarryBuild} off: the Quarry isn't offered, no Miner is given its pit, nothing grows back. */
	//$ gametest_ticks_batch HUGE_AREA '300' '"quarry_build_off"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 300, batch = "quarry_build_off")
	public void switchedOffNothingHappens(GameTestHelper helper) {
		Setup s = quarry(helper, false);
		QuarryBuilds.ENABLED = false;
		QuarryBuilds.PER_DAY = 24000;
		helper.assertFalse(StarterBlueprints.offered(QuarryBuilds.BLUEPRINT), "the Quarry is offered with the switch off");
		miner(helper, s);
		QuarrySite site = QuarrySiteManager.get(s.level()).create(UUID.randomUUID(), "", s.level().dimension().location(), s.pit(), s.pit().getYSpan());
		site.setRefillBuild(s.placement().origin().above(100)); // a quarry build's site, not the one the Miner would be given
		site.setLastGrowth(s.level().getGameTime());
		helper.runAfterDelay(220, () -> {
			helper.assertTrue(site.grownTotal() == 0, site.grownTotal() + " grew back with the switch off");
			helper.assertTrue(siteOf(s) == null, "the Miner was given the pit with the switch off");
			QuarrySiteManager.get(s.level()).remove(site.id());
			QuarryBuilds.ENABLED = true;
			helper.assertTrue(StarterBlueprints.offered(QuarryBuilds.BLUEPRINT), "the Quarry isn't offered with the switch on");
			helper.succeed();
		});
	}

	/** A Quarry Marker handed to the Quarry's Miner still works: they dig the marked free area, then go back to the pit. */
	//$ gametest_ticks_batch HUGE_AREA '400' '"quarry_build_marker"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "quarry_build_marker")
	public void theQuarryMarkerStillWorks(GameTestHelper helper) {
		Setup s = quarry(helper, true);
		QuarryBuilds.PER_DAY = 0;
		Villager villager = miner(helper, s);
		QuarryBuilds.hire(s.level());
		helper.assertTrue(siteOf(s) != null, "the Miner wasn't given the pit");
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
		helper.assertTrue(free != null && !free.refills() && free.box().isInside(a), "the marker's quarry wasn't started: " + (free == null ? "none" : free.box()));
		helper.assertTrue(siteOf(s) == null, "the pit's quarry is still there beside the marker's");
		QuarryBuilds.hire(s.level());
		helper.assertTrue(siteOf(s) == null, "the busy Miner was given the pit again");
		Miners.cancel(s.level(), free);
		QuarryBuilds.hire(s.level());
		helper.assertTrue(siteOf(s) != null, "the Miner didn't go back to the pit after the marker's quarry");
		helper.succeed();
	}

	/** The words a player reads. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void quarryBuildWordsAreWritten(GameTestHelper helper) {
		Language lang = Language.getInstance();
		helper.assertTrue(lang.getOrDefault("blueprint.aliveworkplace.quarry").equals("Quarry"), "the build's name");
		helper.assertTrue(lang.getOrDefault("message.aliveworkplace.quarry.state.refilling").contains("grow back"), "the status line");
		String took = lang.getOrDefault("message.aliveworkplace.quarry.build_started");
		helper.assertTrue(took.split("%s", -1).length == 2 && took.contains("Quarry"), "the message when the Miner takes the pit: " + took);
		helper.assertTrue(lang.has("aliveworkplace.config.quarryBuild") && lang.getOrDefault("aliveworkplace.config.quarryBuild.tooltip").contains("Off:"), "the switch's words");
		helper.assertTrue(lang.has("aliveworkplace.config.quarryRegrowth") && lang.getOrDefault("aliveworkplace.config.quarryRegrowth.tooltip").contains("day"), "the rate's words");
		helper.assertTrue(new io.github.jcondedata.aliveworkplace.WorkplaceConfig().quarryBuild, "the switch should default on");
		helper.succeed();
	}
}
