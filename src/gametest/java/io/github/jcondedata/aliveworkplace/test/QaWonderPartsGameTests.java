package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * QA for B138 and B143 through the player's own path: the builder handed the Test Folly's plan raises stage I's part
 * through his own work loop. The owner hears a Wonder line, never "finished building ... The blueprint is back in the
 * supply chest"; no blueprint item comes back anywhere; the part writes no BUILT chronicle line, gets no finished record,
 * no upgrade offer and no path; the builder isn't left on the finished part and takes the next stage's. With two parts
 * and a save and reload between them, the second still says the stage line with the right count; with the owner
 * logged off, no blueprint goes into any chest or onto the ground.
 */
public class QaWonderPartsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String TALL = "aliveworkplace_test:tall_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final BlockPos HALL = new BlockPos(2, 2, 27);
	private static final BlockPos MIDDLE = new BlockPos(14, 2, 14);
	private static final String FINISHED = "message.aliveworkplace.finished";

	record Village(ServerLevel level, ServerPlayer owner, List<Component> heard, BlockPos hall, Villager builder) {
	}

	/** A Town owned by a player who keeps what they're told, with one builder at a bench by the hall (as WonderFinishedGameTests). */
	static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = WonderFinishedGameTests.listener(helper, heard, "qa-wonder-owner");
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		BlockPos bench = new BlockPos(5, 2, 28);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 26));
		Builders.employ(level, builder, helper.absolutePos(bench));
		return new Village(level, player, heard, helper.absolutePos(HALL), builder);
	}

	/** The folly's plan placed round {@link #MIDDLE}, its goods stocked, handed to the builder as a player does; stage I started. */
	static WonderSite begin(GameTestHelper helper, Village v) {
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		ItemStack plan = WonderPlanItem.of(v.level(), v.hall(), folly, Component.literal("Testholm"));
		BlueprintData data = BlueprintItem.data(plan).orElseThrow();
		BlockPos origin = helper.absolutePos(MIDDLE).subtract(StructureTemplate.transform(new BlockPos(4, 0, 4), Mirror.NONE, Rotation.NONE, BlockPos.ZERO));
		plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(new BlueprintData.Placement(v.level().dimension().location(), origin, Rotation.NONE, Mirror.NONE))));
		BlueprintData.Placement where = BlueprintItem.data(plan).flatMap(BlueprintData::placement).orElseThrow();
		WonderGoodsKit.stockAll(v.level(), FOLLY, where);
		helper.assertTrue(WonderStages.handOver(v.owner(), v.builder(), plan) == InteractionResult.SUCCESS, "the plan wasn't handed over");
		WonderSite site = WonderManager.get(v.level()).in(v.hall()).get(0);
		UUID id = site.id();
		Leftovers.after(helper, () -> forget(v.level(), id));
		for (int i = 0; i < 5 && WonderManager.get(v.level()).site(id).orElseThrow().parts().isEmpty(); i++) {
			WonderStages.tick(v.level());
		}
		WonderSite now = WonderManager.get(v.level()).site(id).orElseThrow();
		helper.assertTrue(now.stage() == 1 && now.parts().size() == 1, "stage I didn't start: " + now);
		return now;
	}

	private static void forget(ServerLevel level, UUID wonder) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite s : List.copyOf(sites.all())) {
			if (wonder.equals(s.wonder())) {
				sites.remove(s.id());
			}
		}
		WonderManager.get(level).remove(wonder);
	}

	/** A second part for stage I at the first's spot, queued for the same builder, as a stage of several parts has. */
	private static BuildSite secondPart(Village v, UUID wonder, BuildSite first) {
		BuildSite second = BuildSiteManager.get(v.level()).create(first.owner(), "Owner", first.structure(), first.placement());
		second.setWonder(wonder);
		WonderManager.get(v.level()).site(wonder).orElseThrow().stone().ifPresent(second::setBench);
		second.setFoundation(true);
		second.setBuilder(v.builder().getUUID());
		second.setQueued(true);
		WonderManager.get(v.level()).setParts(wonder, List.of(first.id(), second.id()));
		return second;
	}

	private static boolean said(List<Component> heard, String key) {
		return heard.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	static List<String> strings(List<Component> heard) {
		return heard.stream().map(Component::getString).toList();
	}

	/** Every blueprint (any stack with a blueprint component naming one of {@code parts}) in the area's containers, on its ground, or carried. */
	private static List<String> partBlueprints(GameTestHelper helper, Set<ResourceLocation> parts, ServerPlayer owner, Villager builder) {
		ServerLevel level = helper.getLevel();
		List<String> found = new ArrayList<>();
		AABB box = helper.getBounds().inflate(8);
		BlockPos.betweenClosedStream(box).forEach(p -> {
			BlockEntity be = level.getBlockEntity(p);
			if (be instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					check(c.getItem(i), parts, "chest at " + helper.relativePos(p).toShortString(), found);
				}
			}
		});
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
			check(e.getItem(), parts, "ground", found);
		}
		if (owner != null) {
			for (ItemStack s : owner.getInventory().items) {
				check(s, parts, "owner", found);
			}
		}
		Villager live = (Villager) level.getEntity(builder.getUUID());
		if (live != null) {
			for (int i = 0; i < live.getInventory().getContainerSize(); i++) {
				check(live.getInventory().getItem(i), parts, "builder", found);
			}
		}
		return found;
	}

	private static void check(ItemStack stack, Set<ResourceLocation> parts, String where, List<String> found) {
		if (stack.isEmpty() || stack.getItem() instanceof WonderPlanItem) {
			return;
		}
		BlueprintItem.data(stack).filter(d -> parts.contains(d.structure()) || d.structure().getPath().contains("stage_"))
			.ifPresent(d -> found.add(where + ": " + d.structure()));
	}

	private static List<String> built(ServerLevel level, BlockPos hall) {
		return ((VillageHallBlockEntity) level.getBlockEntity(hall)).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.BUILT)
			.map(e -> e.text().getString()).toList();
	}

	/** Dirt paths in the area outside {@code box} (the part's own footprint). */
	private static Set<BlockPos> paths(GameTestHelper helper, BoundingBox box) {
		Set<BlockPos> out = new HashSet<>();
		BlockPos.betweenClosedStream(helper.getBounds()).forEach(p -> {
			if (!box.isInside(p) && helper.getLevel().getBlockState(p).is(Blocks.DIRT_PATH)) {
				out.add(p.immutable());
			}
		});
		return out;
	}

	/**
	 * The builder raises stage I's part through his own work loop. At the moment it's done the owner has heard the stage
	 * line and never the ordinary "finished building" one; no blueprint of the part is anywhere; no BUILT chronicle line,
	 * no finished record, no upgrade offer, no new dirt path; and the builder goes on to stage II's part, not left on the old one.
	 */
	//$ gametest_ticks_batch TALL '20000' '"qaWonderPartLoop"'
	@GameTest(template = TALL, timeoutTicks = 20000, batch = "qaWonderPartLoop")
	public void theBuildersOwnLoopTellsStageOneAsAWonderStage(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		WonderSite site = begin(helper, v);
		UUID id = site.id();
		BuildSite part = BuildSiteManager.get(level).get(site.parts().get(0));
		UUID partId = part.id();
		ResourceLocation structure = part.structure();
		BlueprintData.Placement placement = part.placement();
		BuildPlan plan = part.plan(level);
		BoundingBox box = plan.bounds().inflatedBy(3);
		Set<BlockPos> pathsBefore = paths(helper, box);
		Set<ResourceLocation> parts = Set.of(structure);
		List<String> blueprintsBefore = partBlueprints(helper, parts, v.owner(), v.builder());
		helper.assertTrue(blueprintsBefore.isEmpty(), "a part's blueprint before the build: " + blueprintsBefore);
		String who = v.builder().getDisplayName().getString();
		String stageLine = who + " finished stage 1 of 3 of the Test Folly: The Ground Story.";
		AtomicInteger phase = new AtomicInteger();
		List<String> problems = new ArrayList<>();
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			if (phase.get() == 0 && BuildSiteManager.get(level).get(partId) == null) {
				// the part's job just ended: check everything the owner and the village see now
				phase.set(1);
				List<String> lines = strings(v.heard());
				if (!lines.contains(stageLine)) {
					problems.add("no stage line \"" + stageLine + "\" in " + lines);
				}
				if (said(v.heard(), FINISHED) || lines.stream().anyMatch(l -> l.contains("finished building") || l.contains("blueprint is back"))) {
					problems.add("told as an ordinary building: " + lines);
				}
				if (said(v.heard(), "message.aliveworkplace.upgrade_for_sale")) {
					problems.add("an upgrade offered for a Wonder part: " + lines);
				}
				List<String> blueprints = partBlueprints(helper, parts, v.owner(), v.builder());
				if (!blueprints.isEmpty()) {
					problems.add("a blueprint came back for the part: " + blueprints);
				}
				if (!built(level, v.hall()).isEmpty()) {
					problems.add("a BUILT chronicle line: " + built(level, v.hall()));
				}
				if (BuildSiteManager.get(level).finishedAt(level, structure, placement.origin()).isPresent()
					|| BuildSiteManager.get(level).finishedNear(level, v.hall(), 64).stream().anyMatch(f -> f.structure().getPath().contains("wonder/"))) {
					problems.add("a finished record for the part: " + BuildSiteManager.get(level).finishedNear(level, v.hall(), 64));
				}
				Set<BlockPos> newPaths = new HashSet<>(paths(helper, box));
				newPaths.removeAll(pathsBefore);
				if (!newPaths.isEmpty()) {
					problems.add(newPaths.size() + " dirt paths laid for the part, e.g. " + newPaths.stream().limit(3).map(p -> helper.relativePos(p).toShortString()).toList());
				}
				BuilderJob job = ModAttachments.BUILDER_JOB.get(v.builder());
				if (job != null && job.siteId().equals(partId)) {
					problems.add("the builder is still on the finished part");
				}
				io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA B138/B143 loop: stage I part done at {}, heard {}", level.getGameTime(), lines);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase.get() == 1, "stage I's part isn't done yet: " + (BuildSiteManager.get(level).get(partId) == null ? "-"
				: BuildSiteManager.get(level).get(partId).stage() + " " + BuildSiteManager.get(level).get(partId).placed() + " placed") + "; heard " + strings(v.heard()));
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			// the builder took stage II's part (its goods are in the stockpile): working it, not idle on the old one
			WonderSite now = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(now.stage() == 2 && !now.parts().isEmpty(), "stage II didn't start: " + now);
			BuildSite next = BuildSiteManager.get(level).get(now.parts().get(0));
			BuilderJob job = ModAttachments.BUILDER_JOB.get(v.builder());
			helper.assertTrue(next != null && job != null && job.siteId().equals(next.id()) && Builders.activeSite(level, v.builder()) == next,
				"the builder isn't on stage II's part: job " + job + ", part " + (next == null ? null : next.id() + " builder " + next.builder()));
		});
	}

	/**
	 * Stage I of two parts, both the builder's: the first done through his loop says "1 of 2 parts ... stand"; then a
	 * save and reload (the Wonders, the build sites and the builder himself); he takes the second and its finish says
	 * the stage line, never the ordinary building line.
	 */
	//$ gametest_ticks_batch TALL '20000' '"qaWonderPartReload"'
	@GameTest(template = TALL, timeoutTicks = 20000, batch = "qaWonderPartReload")
	public void theSecondPartAfterAReloadStillFinishesTheStage(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		var registries = level.registryAccess();
		WonderSite site = begin(helper, v);
		UUID id = site.id();
		BuildSite first = BuildSiteManager.get(level).get(site.parts().get(0));
		UUID firstId = first.id();
		UUID secondId = secondPart(v, id, first).id();
		String who = v.builder().getDisplayName().getString();
		String partLine = who + " finished a part of the Test Folly: 1 of 2 parts of stage 1 (The Ground Story) stand.";
		String stageLine = who + " finished stage 1 of 3 of the Test Folly: The Ground Story.";
		AtomicInteger phase = new AtomicInteger();
		List<CompoundTag> saved = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		List<Component> afterReload = new ArrayList<>();
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			if (!saved.isEmpty()) { // the builder comes back the tick after leaving, as the world loads him
				Villager back = EntityType.VILLAGER.create(level);
				back.load(saved.remove(0));
				level.addFreshEntity(back);
				return;
			}
			if (phase.get() == 0 && BuildSiteManager.get(level).get(firstId) == null) {
				List<String> lines = strings(v.heard());
				if (!lines.contains(partLine)) {
					problems.add("first part: no \"" + partLine + "\" in " + lines);
				}
				if (said(v.heard(), FINISHED)) {
					problems.add("first part told as a building: " + lines);
				}
				v.heard().clear();
				// save and reload mid-stage
				WonderManager wonders = WonderManager.get(level);
				level.getDataStorage().set(WonderManager.NAME, WonderManager.load(wonders.save(new CompoundTag(), registries), registries));
				BuildSiteManager sites = BuildSiteManager.get(level);
				level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(sites.save(new CompoundTag(), registries), registries));
				WonderSite loaded = WonderManager.get(level).site(id).orElseThrow();
				if (loaded.stage() != 1 || !loaded.parts().equals(List.of(firstId, secondId))) {
					problems.add("read back: stage " + loaded.stage() + ", parts " + loaded.parts());
				}
				Villager live = (Villager) level.getEntity(v.builder().getUUID());
				if (live != null) {
					saved.add(live.saveWithoutId(new CompoundTag()));
					live.discard();
				}
				phase.set(1);
				return;
			}
			if (phase.get() == 1 && BuildSiteManager.get(level).get(secondId) == null) {
				afterReload.addAll(v.heard());
				phase.set(2);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase.get() == 2, "phase " + phase.get() + ": second part " + (BuildSiteManager.get(level).get(secondId) == null ? "gone"
				: BuildSiteManager.get(level).get(secondId).stage() + " queued " + BuildSiteManager.get(level).get(secondId).isQueued()) + ", heard " + strings(v.heard()));
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			List<String> lines = strings(afterReload);
			helper.assertTrue(lines.contains(stageLine), "after the reload, the second part: \"" + stageLine + "\" not in " + lines);
			helper.assertTrue(!said(afterReload, FINISHED) && !said(afterReload, "message.aliveworkplace.wonder.part_built"),
				"the stage's last part after a reload told as a building or as one of several: " + lines);
			helper.assertTrue(built(level, v.hall()).isEmpty(), "BUILT chronicle lines: " + built(level, v.hall()));
		});
	}

	/**
	 * The owner logged off when a part is done: an ordinary build's blueprint would go into the supply chests or onto
	 * the ground, but a Wonder part has none, so nothing appears in any chest (the stockpile's included) or on the ground.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaWonderPartOffline"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaWonderPartOffline")
	public void aPartDoneWithTheOwnerAwayPutsNoBlueprintInAnyChest(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		WonderSite site = begin(helper, v);
		BuildSite part = BuildSiteManager.get(level).get(site.parts().get(0));
		Set<ResourceLocation> parts = Set.of(part.structure());
		// a chest by the builder's bench, as an ordinary build's supply chest
		helper.setBlock(new BlockPos(6, 2, 28), Blocks.CHEST);
		level.getServer().getPlayerList().remove(v.owner());
		helper.assertTrue(level.getServer().getPlayerList().getPlayer(v.owner().getUUID()) == null, "the owner is still online");
		for (int i = 0; i < 20 && !part.isDone(); i++) {
			part.finishList();
		}
		Builders.finish(level, v.builder(), part);
		List<String> blueprints = partBlueprints(helper, parts, null, v.builder());
		helper.assertTrue(blueprints.isEmpty(), "a Wonder part's blueprint came back with the owner away: " + blueprints);
		helper.assertTrue(built(level, v.hall()).isEmpty(), "BUILT chronicle lines: " + built(level, v.hall()));
		helper.assertTrue(BuildSiteManager.get(level).finishedAt(level, part.structure(), part.placement().origin()).isEmpty(), "a finished record for the part");
		helper.assertTrue(ModAttachments.BUILDER_JOB.get(v.builder()) == null || !ModAttachments.BUILDER_JOB.get(v.builder()).siteId().equals(part.id()),
			"the builder is still on the finished part");
		helper.succeed();
	}
}
