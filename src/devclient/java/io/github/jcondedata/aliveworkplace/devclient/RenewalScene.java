package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.OldHouses;
import io.github.jcondedata.aliveworkplace.city.Renewals;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=renewal (ROADMAP 27.21): a time-lapse of a vanilla plains house with two beds, in a Homes zone in Cherry whose
 * "renew old houses" switch is on, renewed by the Steward: the proposal approved, the old house taken down a few blocks
 * at a time (its scan's plan), then a Stone House (Cherry) going up on the plot the same way, and both villagers who
 * slept there given its beds. The builder's pace is sped up (several blocks a tick) so the whole swap fits the GIF.
 */
final class RenewalScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 0);
	private static final BlockPos HOUSE = HALL.offset(10, 0, -4);
	private static final BlockPos TABLE = HALL.offset(0, 0, 8);
	private static final int BLOCKS_PER_TICK = 4;
	private int tick;
	private final List<Villager> sleepers = new ArrayList<>();
	private Villager builder;
	private BuildSite site;
	private List<Runnable> steps = List.of();
	private volatile int step;
	private volatile int phase;
	private boolean half;
	private BoundingBox newBox;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> stage(server));
		}
		if (tick == 80) {
			ScreenshotHarness.shot(mc, "01_old_house");
			server.execute(() -> approve(server));
		}
		if (tick > 90 && phase > 0 && phase < 3) {
			server.execute(() -> work(server));
		}
		if (phase == 2 && !half && step >= steps.size() / 2 && !steps.isEmpty()) {
			half = true;
			ScreenshotHarness.shot(mc, "02_going_up");
		}
		if (phase == 3) {
			phase = 4;
			server.execute(() -> check(server));
		}
		if (phase == 5) {
			phase = 6;
			tick = 10_000;
		}
		if (tick == 10_030) {
			ScreenshotHarness.shot(mc, "03_stone_house_cherry");
		}
		if (tick == 10_040 || tick == 3000) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** The player's hall, a Homes zone in Cherry that renews old houses, a Steward, a builder, and a vanilla two-bed house. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(2000);
		ServerPlayer player = player(server);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState());
		level.setBlockAndUpdate(TABLE, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
		level.setBlockAndUpdate(TABLE.east(), Blocks.CHEST.defaultBlockState());
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 5; x <= 34; x++) {
			for (int z = -16; z <= 16; z++) {
				cells.set(CityPlan.cellAt(HALL, HALL.offset(x, 0, z)));
			}
		}
		hall.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "cherry").paint(0, cells).editZone(0, "homes", "Homes 1", "cherry", true));
		StructurePlaceSettings settings = new StructurePlaceSettings();
		settings.addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
		settings.addProcessor(JigsawReplacementProcessor.INSTANCE);
		level.getStructureManager().get(ResourceLocation.withDefaultNamespace("village/plains/houses/plains_medium_house_1")).orElseThrow()
			.placeInWorld(level, HOUSE, HOUSE, settings, net.minecraft.util.RandomSource.create(27_21L), 2);
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, plan, HALL);
		Villager steward = EntityType.VILLAGER.spawn(level, HALL.south(2), MobSpawnType.COMMAND);
		steward.setVillagerData(steward.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER)
			.setLevel(Stewards.MIN_BUILDER_LEVEL));
		steward.setVillagerXp(70);
		Stewards.appoint(player, steward, plan);
		ModAttachments.STEWARD_ROUND_DAY.set(steward, StewardWishes.day(level));
		builder = EntityType.VILLAGER.spawn(level, TABLE.west(), MobSpawnType.COMMAND);
		Builders.employ(level, builder, TABLE);
		BoundingBox area = new BoundingBox(HOUSE.getX(), HOUSE.getY(), HOUSE.getZ(), HOUSE.getX() + 13, HOUSE.getY() + 8, HOUSE.getZ() + 11);
		level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), area::isInside, HOUSE, 24, PoiManager.Occupancy.ANY).map(BlockPos::immutable)
			.forEach(bed -> {
				Villager v = EntityType.VILLAGER.spawn(level, HOUSE.offset(6, 0, -3), MobSpawnType.COMMAND);
				level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
				v.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
				sleepers.add(v);
			});
		Showcase.check(sleepers.size() == 2, "two villagers sleep in the old house (" + sleepers.size() + ")");
		Vec3 at = Vec3.atCenterOf(HOUSE.offset(6, 2, 5));
		ScreenshotHarness.hoverLookingAt(player, at.add(-16, 12, -14), at);
	}

	/** The Steward's proposal for the old house, approved by the owner: the take-down starts. */
	private void approve(MinecraftServer server) {
		ServerLevel level = server.overworld();
		OldHouses.Measure measure = new OldHouses.Measure(level, sleepers.isEmpty() ? HOUSE
			: sleepers.get(0).getBrain().getMemory(MemoryModuleType.HOME).orElseThrow().pos());
		while (!measure.done()) {
			measure.step(1 << 16);
		}
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		OldHouses.House house = measure.house(HALL, hall.plan());
		Showcase.check(house.renewable(), "the old house can be renewed (" + house.verdict() + ")");
		Optional<Renewals.Replacement> replacement = Renewals.replacement(level, HALL, house);
		Showcase.check(replacement.map(r -> r.blueprint().getPath().endsWith("cherry/aliveworkplace/stone_house")).orElse(false),
			"it becomes a Stone House in Cherry (" + replacement.map(Renewals.Replacement::blueprint) + ")");
		if (replacement.isEmpty()) {
			phase = 5;
			return;
		}
		Optional<StewardDesk.Proposal> proposal = StewardDesk.offerRenewal(level, HALL, house, replacement.get());
		Showcase.check(proposal.map(p -> p.name().getString().startsWith("Renew the old house")).orElse(false),
			"the desk reads " + proposal.map(p -> p.name().getString()).orElse("nothing"));
		StewardDesk.Outcome outcome = proposal.map(p -> StewardDesk.approve(level, HALL, player(server), p.id())).orElse(StewardDesk.Outcome.GONE);
		Showcase.check(outcome == StewardDesk.Outcome.STARTED, "approved: " + outcome);
		newBox = replacement.get().box();
		next(level);
	}

	/** The renewal's current site, as a list of block changes to play a few a tick. */
	private void next(ServerLevel level) {
		List<Renewals.Renewal> active = Renewals.active(level, HALL);
		site = active.isEmpty() ? null : BuildSiteManager.get(level).get(active.get(0).site());
		if (site == null) {
			phase = 3;
			return;
		}
		BuildPlan plan = site.plan(level);
		List<Runnable> list = new ArrayList<>();
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		if (site.isDeconstruction()) {
			for (BuildPlan.Step s : plan.steps(BuildPlan.Stage.DECONSTRUCT)) {
				list.add(() -> {
					level.setBlock(s.pos(), Blocks.AIR.defaultBlockState(), flags);
					if (s.secondaryPos() != null) {
						level.setBlock(s.secondaryPos(), Blocks.AIR.defaultBlockState(), flags);
					}
				});
			}
			phase = 1;
		} else {
			for (BuildPlan.Step s : plan.steps(BuildPlan.Stage.CLEAR)) {
				list.add(() -> level.setBlock(s.pos(), Blocks.AIR.defaultBlockState(), flags));
			}
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step s : plan.steps(stage)) {
					list.add(() -> {
						level.setBlock(s.pos(), s.state(), flags);
						if (s.secondaryPos() != null && s.secondaryState() != null) {
							level.setBlock(s.secondaryPos(), s.secondaryState(), flags);
						}
					});
				}
			}
			phase = 2;
		}
		steps = list;
		step = 0;
	}

	private void work(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (site == null) {
			return;
		}
		for (int i = 0; i < BLOCKS_PER_TICK && step < steps.size(); i++) {
			steps.get(step++).run();
		}
		if (step >= steps.size()) {
			BuildSite done = site;
			site = null;
			Builders.finish(level, builder, done);
			next(level);
		}
	}

	private void check(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Showcase.check(Renewals.active(level, HALL).isEmpty(), "the renewal is finished");
		for (Villager v : sleepers) {
			Showcase.check(newBox != null && v.getBrain().getMemory(MemoryModuleType.HOME).map(g -> newBox.isInside(g.pos())).orElse(false),
				v.getName().getString() + " sleeps in the new house");
		}
		Showcase.check(newBox != null && BlueprintLibrary.get(level, ResourceLocation.fromNamespaceAndPath("aliveworkplace", "stone_house")).isPresent(),
			"the Stone House stands");
		if (newBox != null) {
			Vec3 at = Vec3.atCenterOf(new BlockPos(newBox.getCenter()));
			ScreenshotHarness.hoverLookingAt(player(server), at.add(-16, 10, -14), at);
		}
		phase = 5;
	}
}
