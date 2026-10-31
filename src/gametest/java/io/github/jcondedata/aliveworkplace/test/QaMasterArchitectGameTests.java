package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.GrandRebuild;
import io.github.jcondedata.aliveworkplace.legend.GrandRebuildPower;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA for 29.12, the Master Architect, written from the spec: the pace reaches a builder 32 blocks away and not one 33
 * away; a City qualifies like a Town, but three buildings in one style, or in two, don't; rebuilds come every 3 days, not
 * after 2; a village with only decorations gets none; with no builder nothing is handed and the 3 days aren't spent; a
 * pause survives a save and reload.
 */
public class QaMasterArchitectGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final ResourceLocation ID = AliveWorkplace.id("master_architect");

	private static void setUp(GameTestHelper helper, List<BlueprintData.Placement> recorded) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		LegendRecord stale = LegendRecord.get(helper.getLevel());
		stale.entries().stream().filter(e -> e.id().equals(ID)).map(LegendRecord.Entry::villager).toList().forEach(stale::forget);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			recorded.forEach(p -> BuildSiteManager.get(level).forgetFinished(p));
			LegendRecord record = LegendRecord.get(level);
			Set<UUID> ours = new HashSet<>();
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(64), v -> true)) {
				record.forget(v.getUUID());
				ours.add(v.getUUID());
				v.discard();
			}
			for (BuildSite s : List.copyOf(BuildSiteManager.get(level).all())) {
				if (s.builder() != null && ours.contains(s.builder())) {
					BuildSiteManager.get(level).remove(s.id());
				}
			}
			LegendPowers.forget();
		});
	}

	private static void staged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		long time = level.getDayTime();
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.reload(level.getServer().getResourceManager());
			Legend architect = Legends.get(ID).orElseThrow(() -> new AssertionError("master_architect.json didn't load"));
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(ID, architect);
			Legends.setForTest(map);
			FriendPerksGameTests.titled(level, helper.absolutePos(HALL), 300);
			body.accept(architect);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			level.setDayTime(time);
			Moods.forget();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static BlueprintData.Placement finished(GameTestHelper helper, ResourceLocation id, BlockPos origin, List<BlueprintData.Placement> recorded) {
		BlueprintData.Placement p = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(helper.getLevel()).recordFinished(id, p, UUID.randomUUID());
		recorded.add(p);
		return p;
	}

	private static Villager architect(GameTestHelper helper, Legend legend, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		Legends.make(helper.getLevel(), v, legend, "test");
		return v;
	}

	private static Villager builder(GameTestHelper helper, BlockPos bench) {
		helper.setBlock(bench, ModBlocks.BLUEPRINT_TABLE);
		Villager v = helper.spawn(EntityType.VILLAGER, bench.south());
		v.setNoAi(true);
		Builders.employ(helper.getLevel(), v, helper.absolutePos(bench));
		return v;
	}

	private static BuildSite round(GameTestHelper helper, Villager architect, Legend legend) {
		return GrandRebuild.round(helper.getLevel(), architect, ModAttachments.LEGEND.get(architect), legend.powers(GrandRebuildPower.class).get(0));
	}

	private static long legendLines(GameTestHelper helper) {
		return hall(helper).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.LEGEND).count();
	}

	/** The pace's edge: a builder exactly 32 blocks from the Architect is twice as fast; one 33 blocks away isn't. */
	//$ gametest_ticks_batch AREA '100' '"qaArchitectPaceEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaArchitectPaceEdge")
	public void paceReaches32BlocksNot33(GameTestHelper helper) {
		setUp(helper, new ArrayList<>());
		helper.runAfterDelay(2, () -> staged(helper, legend -> {
			architect(helper, legend, new BlockPos(5, 2, 5));
			Villager at32 = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 37));
			Villager at33 = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 38));
			Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
			for (Villager v : List.of(at32, at33, farmer)) {
				v.setNoAi(true);
			}
			at32.setVillagerData(at32.getVillagerData().setProfession(ModVillagers.BUILDER));
			at33.setVillagerData(at33.getVillagerData().setProfession(ModVillagers.BUILDER));
			farmer.setVillagerData(farmer.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
			LegendPowers.forget();
			helper.assertTrue(LegendPowers.pace(at32) == 2f, "a builder 32 blocks away: " + LegendPowers.pace(at32));
			helper.assertTrue(LegendPowers.pace(at33) == 1f, "a builder 33 blocks away: " + LegendPowers.pace(at33));
			helper.assertTrue(LegendPowers.pace(farmer) == 1f, "a farmer beside the Architect is sped up: " + LegendPowers.pace(farmer));
			helper.succeed();
		}));
	}

	/** A City qualifies like a Town; three buildings all as drawn (one style), or in two styles, don't. */
	//$ gametest_ticks_batch AREA '100' '"qaArchitectStyles"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaArchitectStyles")
	public void aCityQualifiesButOneOrTwoStylesDont(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, legend -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			long day = Chronicle.day(level);
			hall(helper).setRank(VillageRanks.Rank.TOWN);
			// three buildings, all as drawn: one style
			List<BlueprintData.Placement> plain = new ArrayList<>();
			plain.add(finished(helper, StarterBlueprints.STONE_HOUSE.id(), new BlockPos(10, 2, 3), recorded));
			plain.add(finished(helper, StarterBlueprints.STARTER_COTTAGE.id(), new BlockPos(10, 2, 12), recorded));
			plain.add(finished(helper, StarterBlueprints.INN.id(), new BlockPos(20, 2, 3), recorded));
			helper.assertTrue(LegendGuests.candidates(level, hall, "inn", day).isEmpty(), "came for three buildings in one style");
			plain.forEach(p -> BuildSiteManager.get(level).forgetFinished(p));
			// three buildings in two styles
			List<BlueprintData.Placement> two = new ArrayList<>();
			two.add(finished(helper, StarterBlueprints.STONE_HOUSE.id(), new BlockPos(10, 2, 3), recorded));
			two.add(finished(helper, BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "cherry"), new BlockPos(10, 2, 12), recorded));
			two.add(finished(helper, BlueprintStyles.styled(StarterBlueprints.INN.id(), "cherry"), new BlockPos(20, 2, 3), recorded));
			helper.assertTrue(LegendGuests.candidates(level, hall, "inn", day).isEmpty(), "came for three buildings in two styles");
			BuildSiteManager.get(level).forgetFinished(two.get(2));
			finished(helper, BlueprintStyles.styled(StarterBlueprints.INN.id(), "stonework"), new BlockPos(20, 2, 3), recorded);
			helper.assertTrue(LegendGuests.candidates(level, hall, "inn", day).contains(legend), "didn't come to a Town with 3 styles");
			hall(helper).setRank(VillageRanks.Rank.CITY);
			helper.assertTrue(LegendGuests.candidates(level, hall, "inn", day).contains(legend), "didn't come to a City with 3 styles");
			helper.succeed();
		}));
	}

	/** Every 3 days: none 2 days after a rebuild, the next one on the third day. */
	//$ gametest_ticks_batch AREA '100' '"qaArchitectDays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaArchitectDays")
	public void theNextRebuildComesOnTheThirdDayNotTheSecond(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, legend -> {
			ServerLevel level = helper.getLevel();
			finished(helper, StarterBlueprints.STONE_HOUSE_3.id(), new BlockPos(12, 2, 2), recorded);
			Villager b = builder(helper, new BlockPos(4, 2, 20));
			Villager arch = architect(helper, legend, new BlockPos(5, 2, 8));
			BuildSite first = round(helper, arch, legend);
			helper.assertTrue(first != null, "no first rebuild");
			BuildSiteManager.get(level).remove(first.id());
			ModAttachments.BUILDER_JOB.remove(b);
			long start = level.getDayTime();
			level.setDayTime(start + 2 * 24000L);
			helper.assertTrue(round(helper, arch, legend) == null, "a rebuild 2 days after the last");
			level.setDayTime(start + 3 * 24000L);
			helper.assertTrue(round(helper, arch, legend) != null, "no rebuild 3 days after the last");
			helper.succeed();
		}));
	}

	/**
	 * Only decorations finished: no rebuild. A house but no builder: none handed, no chronicle line, and the 3 days
	 * aren't spent, so a builder hired the same day gets it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaArchitectNothing"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaArchitectNothing")
	public void decorationsOnlyOrNoBuilderHandsNothing(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, legend -> {
			ServerLevel level = helper.getLevel();
			BlueprintData.Placement well = finished(helper, StarterBlueprints.WELL.id(), new BlockPos(12, 2, 2), recorded);
			BlueprintData.Placement bench = finished(helper, StarterBlueprints.PARK_BENCH.id(), new BlockPos(20, 2, 2), recorded);
			Villager arch = architect(helper, legend, new BlockPos(5, 2, 8));
			long before = legendLines(helper);
			Villager b = builder(helper, new BlockPos(4, 2, 20));
			helper.assertTrue(round(helper, arch, legend) == null, "a decoration rebuilt");
			BuildSiteManager.get(level).forgetFinished(well);
			BuildSiteManager.get(level).forgetFinished(bench);
			ModAttachments.BUILDER_JOB.remove(b);
			b.discard();
			helper.setBlock(new BlockPos(4, 2, 20), net.minecraft.world.level.block.Blocks.AIR);
			finished(helper, StarterBlueprints.STONE_HOUSE_3.id(), new BlockPos(12, 2, 14), recorded);
			helper.assertTrue(round(helper, arch, legend) == null, "a rebuild with no builder");
			helper.assertTrue(legendLines(helper) == before, "a chronicle line with nothing handed");
			helper.assertTrue(GrandRebuild.state(arch).lastDay() < 0, "the 3 days spent on nothing: " + GrandRebuild.state(arch));
			builder(helper, new BlockPos(8, 2, 20));
			helper.assertTrue(round(helper, arch, legend) != null, "no rebuild once a builder was hired the same day");
			helper.succeed();
		}));
	}

	/** A pause survives a save and reload: the reloaded Architect starts no rebuild until carried on. */
	//$ gametest_ticks_batch AREA '100' '"qaArchitectPauseSaved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaArchitectPauseSaved")
	public void aPauseSurvivesASaveAndReload(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, legend -> {
			ServerLevel level = helper.getLevel();
			finished(helper, StarterBlueprints.STONE_HOUSE_3.id(), new BlockPos(12, 2, 2), recorded);
			builder(helper, new BlockPos(4, 2, 20));
			Villager arch = architect(helper, legend, new BlockPos(5, 2, 8));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setShiftKeyDown(true);
			GrandRebuild.togglePause(player, arch);
			CompoundTag tag = arch.saveWithoutId(new CompoundTag());
			arch.discard();
			Villager loaded = EntityType.VILLAGER.create(level);
			loaded.load(tag);
			level.addFreshEntity(loaded);
			helper.assertTrue(GrandRebuild.state(loaded).paused(), "the pause was lost in a reload");
			helper.assertTrue(round(helper, loaded, legend) == null, "a rebuild by a paused Architect after a reload");
			GrandRebuild.togglePause(player, loaded);
			helper.assertTrue(round(helper, loaded, legend) != null, "no rebuild once carried on");
			Leftovers.after(helper, loaded::discard);
			helper.succeed();
		}));
	}
}
