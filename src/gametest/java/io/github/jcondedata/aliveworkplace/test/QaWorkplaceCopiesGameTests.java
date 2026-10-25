package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for ROADMAP 27.11 (a workplace for every worker), from the spec: the 12 copies are in the Blueprint Table under the
 * spec's names (the five Cobblemon ones only with Cobblemon); the workplace reason reads right for one job and for two;
 * {@code no_builder} asks for a Blueprint Table only while nobody can build; and a worker already at his workstation, a
 * nitwit or a villager with no job asks for no workplace.
 */
public class QaWorkplaceCopiesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	private static final Map<String, String> NAMES = Map.ofEntries(
		Map.entry("builders_workshop", "Builder's Workshop"), Map.entry("carpenters_workshop", "Carpenter's Workshop"),
		Map.entry("kitchen", "Kitchen"), Map.entry("post_office", "Post Office"), Map.entry("guard_house", "Guard House"),
		Map.entry("clinic", "Clinic"), Map.entry("ferry_house", "Ferry House"), Map.entry("trainers_house", "Trainer's House"),
		Map.entry("leaders_hall", "Leader's Hall"), Map.entry("ball_workshop", "Ball Workshop"), Map.entry("trade_hall", "Trade Hall"),
		Map.entry("school", "School"));

	/** The 12 copies: in the table's list by the spec's names; the five Cobblemon ones only with Cobblemon. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theTwelveCopiesAreInTheTableByTheirNames(GameTestHelper helper) {
		helper.assertTrue(StarterBlueprints.WORKPLACES.size() + StarterBlueprints.COBBLEMON_WORKPLACES.size() == 12, "not 12 copies");
		List<ResourceLocation> listed = BlueprintLibrary.list(helper.getLevel().getServer(), false);
		boolean cobblemon = Platform.get().isModLoaded("cobblemon");
		for (StarterBlueprints.Entry e : StarterBlueprints.WORKPLACES) {
			helper.assertTrue(listed.contains(e.id()), e.id() + " isn't in the Blueprint Table");
		}
		for (StarterBlueprints.Entry e : StarterBlueprints.COBBLEMON_WORKPLACES) {
			helper.assertTrue(listed.contains(e.id()) == cobblemon, e.id() + (cobblemon ? " isn't in the table with Cobblemon" : " is in the table without Cobblemon"));
		}
		for (StarterBlueprints.Entry e : java.util.stream.Stream.concat(StarterBlueprints.WORKPLACES.stream(), StarterBlueprints.COBBLEMON_WORKPLACES.stream()).toList()) {
			String want = NAMES.get(e.id().getPath());
			helper.assertTrue(want != null, "a copy the spec doesn't name: " + e.id());
			String name = Blueprints.displayName(e.id()).getString();
			helper.assertTrue(name.equals(want), e.id() + " is called \"" + name + "\", the spec says \"" + want + "\"");
			helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), e.id()).isPresent(), e.id() + " doesn't load");
		}
		helper.succeed();
	}

	/** The workplace reason for one job and for two, and no_builder's question as the spec words it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theWorkplaceSentencesReadRight(GameTestHelper helper) {
		String one = Words.countedFirst("steward.aliveworkplace.why.workplace", 1L).getString();
		String two = Words.countedFirst("steward.aliveworkplace.why.workplace", 2L).getString();
		helper.assertTrue(one.equals("1 job here has no workplace of its trade"), "one: " + one);
		helper.assertTrue(two.equals("2 jobs here have no workplace of their trade"), "two: " + two);
		String ask = Language.getInstance().getOrDefault("steward.aliveworkplace.ask.blueprint_table");
		helper.assertTrue(ask.startsWith("place a Blueprint Table and give a villager the job"), "the ask: " + ask);
		StewardRules.Rule builder = rule("builder");
		helper.assertTrue(builder.effect().kind() == StewardRules.Kind.ASK
			&& builder.effect().key().map(k -> k.equals("steward.aliveworkplace.ask.blueprint_table")).orElse(false), "no_builder's rule doesn't ask: " + builder.effect());
		helper.succeed();
	}

	private static StewardRules.Rule rule(String id) {
		return StewardRules.all().stream().filter(r -> r.id().equals(AliveWorkplace.id(id))).findFirst()
			.orElseThrow(() -> new GameTestAssertException("rule " + id + " didn't load"));
	}

	private static BlockPos hamlet(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> level.removeBlock(hall, false));
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.HAMLET);
		return hall;
	}

	private static StewardWishes.Status judge(GameTestHelper helper, BlockPos hall, String rule) {
		ServerLevel level = helper.getLevel();
		return StewardWishes.judge(rule(rule), StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, StewardWishes.day(level)).status();
	}

	/** no_builder holds while nobody can build, and not once a villager works the Blueprint Table. */
	//$ gametest_ticks_batch AREA '40' '"qaWorkplaceNoBuilder"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWorkplaceNoBuilder")
	public void noBuilderAsksOnlyWhileNobodyCanBuild(GameTestHelper helper) {
		BlockPos hall = hamlet(helper);
		helper.assertTrue(judge(helper, hall, "builder") == StewardWishes.Status.HELD, "no builder, but no_builder doesn't hold: " + judge(helper, hall, "builder"));
		helper.setBlock(new BlockPos(3, 2, 18), ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 17));
		builder.setNoAi(true);
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(new BlockPos(3, 2, 18)));
		helper.assertTrue(builder.getVillagerData().getProfession() == ModVillagers.BUILDER, "setup: no builder");
		helper.assertTrue(judge(helper, hall, "builder") != StewardWishes.Status.HELD, "a builder works the table, but no_builder still holds");
		helper.succeed();
	}

	/** A chef at his smoker, a nitwit and a villager with no job ask for no Kitchen; the chef without his smoker does. */
	//$ gametest_ticks_batch AREA '40' '"qaWorkplaceAtWork"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWorkplaceAtWork")
	public void aWorkerAtHisWorkstationWantsNoWorkplace(GameTestHelper helper) {
		BlockPos hall = hamlet(helper);
		ServerLevel level = helper.getLevel();
		Villager nitwit = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 20));
		nitwit.setNoAi(true);
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		Villager none = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 20));
		none.setNoAi(true);
		helper.assertTrue(judge(helper, hall, "workplace_kitchen") != StewardWishes.Status.HELD, "a nitwit or a jobless villager asks for a Kitchen");
		helper.setBlock(new BlockPos(5, 2, 4), Blocks.SMOKER);
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		chef.setNoAi(true);
		VillagerProfession job = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse("aliveworkplace:chef"));
		chef.setVillagerData(chef.getVillagerData().setProfession(job));
		chef.setVillagerXp(1);
		chef.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(5, 2, 4))));
		helper.assertTrue(judge(helper, hall, "workplace_kitchen") != StewardWishes.Status.HELD, "a chef at his smoker asks for a Kitchen");
		chef.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		helper.assertTrue(judge(helper, hall, "workplace_kitchen") == StewardWishes.Status.HELD, "a chef without a workstation asks for no Kitchen: "
			+ judge(helper, hall, "workplace_kitchen"));
		helper.succeed();
	}
}
