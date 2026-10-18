package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderCrews;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Crews (ROADMAP 35.5): every builder of the village on one Wonder. Eight builders raise the folly with no two in one
 * column on any tick, no structure block put up with the block the drawing puts under it missing, in at most a quarter
 * of the time one builder alone takes; a save and reload mid-stage keeps the crew together and they finish; the
 * crew holds to {@code wonderCrew}, forms only with Wonders switched on, and closes up when a member is lost; calling
 * every builder brings in the busy ones; the stone names the lead and the crew.
 */
public class WonderCrewGameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation WHOLE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder/test_folly");
	private static final ResourceLocation DRAWN = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder_drawn/test_folly");
	private static final BlockPos HALL = new BlockPos(2, 2, 27);
	private static final BlockPos MIDDLE = new BlockPos(14, 2, 14);

	private record Village(ServerLevel level, ServerPlayer owner, BlockPos hall, List<Villager> builders) {
	}

	/** A Town owned by a player, with {@code count} builders employed at benches in a row by the hall. */
	private static Village village(GameTestHelper helper, int count, int delay) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		List<Villager> builders = new ArrayList<>();
		for (int i = 0; i < count; i++) { // (moreBuilders adds more along the row)
			BlockPos bench = new BlockPos(5 + 2 * i, 2, 28);
			helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
			Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(5 + 2 * i, 2, 26));
			Builders.employ(level, builder, helper.absolutePos(bench));
			builders.add(builder);
		}
		return new Village(level, player, helper.absolutePos(HALL), builders);
	}

	/** The folly's plan placed with its middle at {@link #MIDDLE}, handed to the village's first builder. */
	private static WonderSite begin(GameTestHelper helper, Village v) {
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		ItemStack plan = WonderPlanItem.of(v.level(), v.hall(), folly, Component.literal("Testholm"));
		BlueprintData data = BlueprintItem.data(plan).orElseThrow();
		BlockPos origin = helper.absolutePos(MIDDLE).subtract(StructureTemplate.transform(new BlockPos(4, 0, 4), Mirror.NONE, Rotation.NONE, BlockPos.ZERO));
		plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(new BlueprintData.Placement(v.level().dimension().location(), origin, Rotation.NONE, Mirror.NONE))));
		BlueprintData.Placement where = BlueprintItem.data(plan).flatMap(BlueprintData::placement).orElseThrow();
		WonderGoodsKit.stockAll(v.level(), FOLLY, where); // 35.7: every stage's goods in the stockpile
		if (WonderStages.handOver(v.owner(), v.builders().get(0), plan) != InteractionResult.SUCCESS) {
			helper.fail("not handed over: cap " + Wonders.capRefusal(v.level(), v.hall(), FOLLY).map(Component::getString)
				+ ", place " + io.github.jcondedata.aliveworkplace.wonder.WonderPlans.placeRefusal(v.level(), WonderPlanItem.plan(plan).orElseThrow(), where,
				BlueprintItem.data(plan).flatMap(BlueprintData::size).orElseThrow()).map(Component::getString)
				+ ", stone " + WonderStages.stoneSpot(v.level(), where) + ", bench " + Builders.benchPos(v.builders().get(0)));
		}
		WonderSite site = WonderManager.get(v.level()).in(v.hall()).get(0);
		UUID id = site.id();
		Leftovers.after(helper, () -> forget(v.level(), id));
		return site;
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

	/** Fills chests round the stone with all the folly takes, a few spare of each. */
	private static void stock(GameTestHelper helper, ServerLevel level, BlockPos stone) {
		BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, new BlueprintData(WHOLE,
			BlueprintLibrary.get(level, WHOLE).map(Blueprint::size), Optional.empty())).orElseThrow();
		int dx = Integer.signum(stone.getX() - helper.absolutePos(MIDDLE).getX());
		int dz = Integer.signum(stone.getZ() - helper.absolutePos(MIDDLE).getZ());
		List<BlockPos> chests = List.of(stone.offset(2 * dx, 0, 0), stone.offset(0, 0, 2 * dz), stone.offset(2 * dx, 0, 2 * dz), stone.offset(3 * dx, 0, 0));
		chests.forEach(p -> level.setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState()));
		int c = 0;
		int slot = 0;
		for (BlueprintSupplies.Line line : list.lines()) {
			Item item = line.item();
			for (int left = line.need() + 4; left > 0; left -= item.getDefaultMaxStackSize()) {
				if (slot == 27) {
					c++;
					slot = 0;
				}
				helper.assertTrue(c < chests.size(), "the folly needs more than " + chests.size() + " chests");
				((Container) level.getBlockEntity(chests.get(c))).setItem(slot++, new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
			}
		}
	}

	/** Blocks the finished folly lacks, but for the lantern on its finial (see WonderSiteGameTests#unlikeButTheFinial). */
	private static List<BlockPos> unlikeButTheFinial(ServerLevel level, BlueprintData.Placement at) {
		BlockPos finial = at.origin().offset(StructureTemplate.transform(new BlockPos(4, 18, 4), at.mirror(), at.rotation(), BlockPos.ZERO));
		Blueprint drawn = BlueprintLibrary.get(level, DRAWN).orElseThrow();
		return BuildPlan.create(drawn, at, level, 0).unfinished(level).stream().filter(p -> !p.equals(finial)).toList();
	}

	/**
	 * The checks every tick of a crew at work: no two members on one column (by their columns and by the blocks they're
	 * on), and every structure block that went up has what the drawing puts under it. Counts the blocks the parts place.
	 */
	private static final class Watch {
		final Set<UUID> counted = new HashSet<>();
		final Set<BlockPos> up = new HashSet<>();
		int placeable;
		int most;
		/** Ticks each member was on the crew, and of those, ticks it had a block to work on; the tick each stage began. */
		long memberTicks;
		long busyTicks;
		final List<String> stages = new ArrayList<>();

		void tick(GameTestHelper helper, ServerLevel level, WonderSite now) {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (UUID id : now.parts()) {
				BuildSite part = sites.get(id);
				if (part == null || part.isDone()) {
					continue;
				}
				BuildPlan plan = part.plan(level);
				if (plan == null) {
					continue;
				}
				if (counted.add(id)) {
					placeable += plan.placeableCount();
				}
				Map<UUID, Long> columns = part.crewColumns();
				helper.assertTrue(new HashSet<>(columns.values()).size() == columns.size(), "two members on one column: " + columns);
				Map<Long, UUID> on = new HashMap<>();
				List<UUID> members = new ArrayList<>(part.helpers(level.getGameTime()));
				for (UUID m : members) {
					BlockPos claim = part.claim(m);
					if (claim != null) {
						UUID other = on.put(plan.column(claim), m);
						helper.assertTrue(other == null, "two members working one column at " + claim + " (stage " + part.stage() + ")");
					}
				}
				if (part.leadClaim() != null && !members.isEmpty()) {
					UUID other = on.put(plan.column(part.leadClaim()), part.builder());
					helper.assertTrue(other == null, "a member works the lead's column at " + part.leadClaim());
				}
				if (part.stage() == BuildPlan.Stage.STRUCTURE) {
					for (BuildPlan.Step s : plan.steps(BuildPlan.Stage.STRUCTURE)) {
						if (!level.getBlockState(s.pos()).isAir() && up.add(s.pos())) {
							BlockPos below = s.pos().below();
							helper.assertTrue(!plan.solidPlannedAt(below) || !level.getBlockState(below).canBeReplaced(),
								"a structure block went up at " + s.pos().toShortString() + " with nothing under it (" + level.getBlockState(below) + ")");
						}
					}
				}
			}
			most = Math.max(most, WonderCrews.crew(level, now).size());
			String stage = now.stage() + ":" + now.parts().stream().map(sites::get).map(p -> p == null ? "-" : p.stage().name()).toList();
			if (stages.isEmpty() || !stages.get(stages.size() - 1).startsWith(stage + "@")) {
				stages.add(stage + "@" + level.getGameTime());
			}
			for (UUID id : now.parts()) {
				BuildSite part = sites.get(id);
				if (part != null) {
					for (UUID m : part.helpers(level.getGameTime())) {
						memberTicks++;
						busyTicks += part.claim(m) != null ? 1 : 0;
					}
				}
			}
		}
	}

	/** {@code count} more builders for {@code v}'s village, at benches further along the row. */
	private static void moreBuilders(GameTestHelper helper, Village v, int count) {
		for (int i = v.builders().size(), end = v.builders().size() + count; i < end; i++) {
			BlockPos bench = new BlockPos(5 + 2 * i, 2, 28);
			helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
			Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(5 + 2 * i, 2, 26));
			Builders.employ(v.level(), builder, helper.absolutePos(bench));
			v.builders().add(builder);
		}
	}

	/** Takes the finished folly, its stone and chests and anything dropped down again, back to the bare floor. */
	private static void clearSite(GameTestHelper helper, ServerLevel level) {
		for (int x = 3; x <= 25; x++) {
			for (int z = 3; z <= 24; z++) {
				for (int y = 2; y <= 26; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, net.minecraft.world.phys.AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)),
			helper.absolutePos(new BlockPos(30, 30, 30)))).forEach(net.minecraft.world.entity.Entity::discard);
	}

	/**
	 * One builder raises the folly alone, and its time is taken; then, the site cleared, eight raise it again: one
	 * column each, nothing hung in the air, and in about a quarter of the lone builder's time (a tenth over it at most).
	 */
	//$ gametest_ticks_batch TALL '48000' '"eightBuildersRaiseTheFolly"'
	@GameTest(template = TALL, timeoutTicks = 48000, batch = "eightBuildersRaiseTheFolly")
	public void eightBuildersRaiseTheFolly(GameTestHelper helper) {
		Village v = village(helper, 1, 20);
		ServerLevel level = v.level();
		WonderSite alone = begin(helper, v);
		stock(helper, level, alone.stone().orElseThrow());
		BlueprintData.Placement at = alone.placement().orElseThrow();
		UUID[] id = {alone.id()};
		long[] start = {level.getGameTime()};
		long[] soloTicks = {-1};
		long[] crewTicks = {-1};
		Watch watch = new Watch();
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			WonderSite now = WonderManager.get(level).site(id[0]).orElse(null);
			if (now == null || crewTicks[0] >= 0) {
				return;
			}
			if (soloTicks[0] < 0) {
				helper.assertTrue(WonderCrews.crew(level, now).size() <= 1, "a crew on the lone builder's folly");
				if (now.done()) {
					// the lone builder's time; then the folly again from bare ground, with eight
					soloTicks[0] = level.getGameTime() - start[0];
					forget(level, id[0]);
					clearSite(helper, level);
					Leftovers.finished(helper); // the folly's parts were recorded as finished buildings
					moreBuilders(helper, v, 7);
					((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setRank(VillageRanks.Rank.TOWN); // the hall's rounds re-rank a test Town
					WonderSite crew = begin(helper, v);
					stock(helper, level, crew.stone().orElseThrow());
					id[0] = crew.id();
					start[0] = level.getGameTime();
				}
				return;
			}
			if (now.done()) {
				crewTicks[0] = level.getGameTime() - start[0];
				return;
			}
			watch.tick(helper, level, now);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(soloTicks[0] >= 0, "the lone builder hasn't finished the folly");
			WonderSite now = WonderManager.get(level).site(id[0]).orElse(null);
			helper.assertTrue(now != null, "the crew's folly wasn't begun");
			helper.assertTrue(now.done() && crewTicks[0] >= 0, "the crew is still at stage " + now.stage() + ", "
				+ Math.round(WonderStages.stageProgress(level, now) * 100) + "%, crew " + WonderCrews.crew(level, now).size());
			helper.assertTrue(watch.most == 8, "all eight builders on the crew at once: at most " + watch.most);
			// B171: a quarter, with a tenth of it for the crew's jostling. Eight builders walking round each other don't
			// take the same ticks twice: 2,500 to 2,900 most runs, but 3,060 once against a lone 12,100 (25.3%)
			helper.assertTrue(crewTicks[0] * 40 <= soloTicks[0] * 11, "the crew of eight took " + crewTicks[0] + " ticks, the lone builder " + soloTicks[0]
				+ " (a quarter and a tenth of it: " + soloTicks[0] * 11 / 40 + "; " + watch.placeable + " blocks, the least one builder could take " + watch.placeable * 20L
				+ "); members busy " + watch.busyTicks + " of " + watch.memberTicks + " ticks; stages " + watch.stages);
			List<BlockPos> left = unlikeButTheFinial(level, at);
			helper.assertTrue(left.isEmpty(), left.size() + " blocks unlike the drawing, e.g. " + left.stream().limit(4)
				.map(p -> helper.relativePos(p).toShortString() + "=" + level.getBlockState(p).getBlock()).toList());
		});
	}

	/**
	 * A builder on its job takes no fall damage (35.5: a crew's lead fell off the folly's dome, and its part ended
	 * unbuilt); a villager with no build still does.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBuilderOnItsJobTakesNoFallDamage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		Villager idle = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 1));
		builder.setNoAi(true);
		idle.setNoAi(true);
		ModAttachments.BUILDER_JOB.set(builder, new io.github.jcondedata.aliveworkplace.build.BuilderJob(UUID.randomUUID(), false));
		builder.hurt(level.damageSources().fall(), 6f);
		idle.hurt(level.damageSources().fall(), 6f);
		helper.assertTrue(builder.getHealth() == builder.getMaxHealth(), "a builder on its job was hurt by a fall: " + builder.getHealth());
		helper.assertTrue(idle.getHealth() < idle.getMaxHealth(), "a villager with no build took no fall damage");
		helper.succeed();
	}

	/** Saved and loaded mid-stage II with every member, the crew comes back together and finishes the folly as drawn. */
	//$ gametest_ticks_batch TALL '24000' '"crewReload"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "crewReload")
	public void aCrewReloadedMidStageFinishes(GameTestHelper helper) {
		Village v = village(helper, 4, 4);
		ServerLevel level = v.level();
		var registries = level.registryAccess();
		WonderSite begun = begin(helper, v);
		stock(helper, level, begun.stone().orElseThrow());
		BlueprintData.Placement at = begun.placement().orElseThrow();
		UUID id = begun.id();
		AtomicBoolean reloaded = new AtomicBoolean(false);
		AtomicInteger crewBefore = new AtomicInteger();
		AtomicInteger crewAfter = new AtomicInteger();
		List<CompoundTag> saved = new ArrayList<>();
		Watch watch = new Watch();
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			if (!saved.isEmpty()) { // the builders come back the tick after leaving, as the world loads them
				for (CompoundTag tag : saved) {
					Villager back = EntityType.VILLAGER.create(level);
					back.load(tag);
					level.addFreshEntity(back);
				}
				saved.clear();
				return;
			}
			WonderSite now = WonderManager.get(level).site(id).orElse(null);
			if (now == null || now.done()) {
				return;
			}
			watch.tick(helper, level, now);
			if (reloaded.get()) {
				crewAfter.set(Math.max(crewAfter.get(), WonderCrews.crew(level, now).size()));
				return;
			}
			if (now.stage() != 2) {
				return;
			}
			BuildSite part = BuildSiteManager.get(level).get(now.parts().get(0));
			int crew = WonderCrews.crew(level, now).size();
			if (part == null || part.isQueued() || part.placed() < 20 || crew < 3) {
				return;
			}
			crewBefore.set(crew);
			WonderManager wonders = WonderManager.get(level);
			level.getDataStorage().set(WonderManager.NAME, WonderManager.load(wonders.save(new CompoundTag(), registries), registries));
			BuildSiteManager sites = BuildSiteManager.get(level);
			level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(sites.save(new CompoundTag(), registries), registries));
			for (Villager b : v.builders()) {
				Villager live = (Villager) level.getEntity(b.getUUID());
				if (live != null) {
					saved.add(live.saveWithoutId(new CompoundTag()));
					live.discard();
				}
			}
			reloaded.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded.get(), "never reloaded mid-stage II with a crew");
			WonderSite now = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(now.done(), "still at stage " + now.stage() + ", " + Math.round(WonderStages.stageProgress(level, now) * 100) + "%" + parts(level, now));
			helper.assertTrue(crewAfter.get() >= crewBefore.get(), "the crew of " + crewBefore.get() + " came back as " + crewAfter.get());
			List<BlockPos> left = unlikeButTheFinial(level, at);
			helper.assertTrue(left.isEmpty(), left.size() + " blocks unlike the drawing after the reload, e.g. " + left.stream().limit(3).toList());
		});
	}

	/**
	 * With Wonders switched off nobody joins; switched on, the crew fills to {@code wonderCrew} (3) and never past it;
	 * a member lost mid-job is replaced by an idle builder, and the folly is finished as drawn.
	 */
	//$ gametest_ticks_batch TALL '24000' '"crewLimit"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "crewLimit")
	public void theCrewHoldsToItsLimitAndClosesUp(GameTestHelper helper) {
		Village v = village(helper, 5, 4);
		ServerLevel level = v.level();
		int limit = WonderCrews.LIMIT;
		WonderCrews.LIMIT = 3;
		Leftovers.after(helper, () -> {
			WonderCrews.LIMIT = limit;
			Wonders.ENABLED = true;
		});
		WonderSite begun = begin(helper, v);
		stock(helper, level, begun.stone().orElseThrow());
		BlueprintData.Placement at = begun.placement().orElseThrow();
		UUID id = begun.id();
		Wonders.ENABLED = false;
		long start = level.getGameTime();
		AtomicBoolean lost = new AtomicBoolean(false);
		AtomicInteger after = new AtomicInteger();
		Watch watch = new Watch();
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			WonderSite now = WonderManager.get(level).site(id).orElse(null);
			if (now == null || now.done()) {
				return;
			}
			int crew = WonderCrews.crew(level, now).size();
			if (!Wonders.ENABLED) {
				helper.assertTrue(crew <= 1, "a crew formed with Wonders switched off: " + crew);
				if (level.getGameTime() - start >= 100) {
					Wonders.ENABLED = true;
				}
				return;
			}
			watch.tick(helper, level, now);
			helper.assertTrue(crew <= 3, "a crew of " + crew + " over wonderCrew 3");
			if (!lost.get() && crew == 3) {
				// a member is lost mid-job (killed, or taken away)
				BuildSite part = BuildSiteManager.get(level).get(now.parts().get(0));
				UUID member = part == null ? null : part.helpers(level.getGameTime()).stream().findFirst().orElse(null);
				if (member != null && level.getEntity(member) instanceof Villager gone) {
					gone.discard();
					lost.set(true);
				}
			} else if (lost.get()) {
				after.set(Math.max(after.get(), crew));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(lost.get(), "no member was lost");
			WonderSite now = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(now.done(), "still at stage " + now.stage() + ", " + Math.round(WonderStages.stageProgress(level, now) * 100) + "%" + parts(level, now));
			helper.assertTrue(watch.most == 3 && after.get() == 3, "the crew filled to 3 (" + watch.most + ") and closed up after the loss (" + after.get() + ")");
			List<BlockPos> left = unlikeButTheFinial(level, at);
			helper.assertTrue(left.isEmpty(), left.size() + " blocks unlike the drawing, e.g. " + left.stream().limit(3).toList());
		});
	}

	/**
	 * The lead of stage I's part is killed mid-part: the part isn't lost (a part whose site is gone counts as built), it
	 * goes to a member of its crew, who leads it from then on, and the crew finishes the folly as drawn, its later stages
	 * led by a living builder.
	 */
	//$ gametest_ticks_batch TALL '24000' '"crewLeadDies"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "crewLeadDies")
	public void aLeadKilledMidPartHandsItOn(GameTestHelper helper) {
		Village v = village(helper, 4, 4);
		ServerLevel level = v.level();
		WonderSite begun = begin(helper, v);
		stock(helper, level, begun.stone().orElseThrow());
		BlueprintData.Placement at = begun.placement().orElseThrow();
		UUID id = begun.id();
		UUID[] dead = {null};
		UUID[] part = {null};
		UUID[] heir = {null};
		List<String> later = new ArrayList<>();
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			WonderSite now = WonderManager.get(level).site(id).orElse(null);
			if (now == null || now.done()) {
				return;
			}
			BuildSiteManager sites = BuildSiteManager.get(level);
			if (dead[0] == null) {
				BuildSite first = now.stage() == 1 ? sites.get(now.parts().get(0)) : null;
				if (first != null && first.placed() >= 30 && first.helpers(level.getGameTime()).size() >= 2
					&& level.getEntity(first.builder()) instanceof Villager lead) {
					dead[0] = lead.getUUID();
					part[0] = first.id();
					lead.kill(); // not a fall: builders on a job take none
				}
				return;
			}
			if (heir[0] == null) {
				// the owner gathers up the bag the lead dropped as it died and puts it in a chest by the stone
				BlockPos stone = now.stone().orElseThrow();
				BlockPos chest = stone.offset(0, 0, 3 * Integer.signum(stone.getZ() - helper.absolutePos(MIDDLE).getZ()));
				level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
				Container box = (Container) level.getBlockEntity(chest);
				int slot = 0;
				for (net.minecraft.world.entity.item.ItemEntity drop : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
						net.minecraft.world.phys.AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(30, 30, 30))))) {
					if (slot < box.getContainerSize()) {
						box.setItem(slot++, drop.getItem().copy());
					}
					drop.discard();
				}
				BuildSite first = sites.get(part[0]);
				helper.assertTrue(first != null, "the part went with its lead");
				if (!first.isDone()) {
					helper.assertTrue(!dead[0].equals(first.builder()), "the part is still the dead lead's");
					helper.assertTrue(first.builder() != null && level.getEntity(first.builder()) instanceof Villager v2 && v2.isAlive()
						&& ModAttachments.BUILDER_JOB.get(v2) != null && !ModAttachments.BUILDER_JOB.get(v2).helper(),
						"the part wasn't handed to a living member as its lead: " + first.builder());
				}
				heir[0] = first.builder();
			}
			for (UUID p : now.parts()) {
				BuildSite s = sites.get(p);
				if (now.stage() > 1 && s != null && s.builder() != null && !later.contains(now.stage() + ":" + s.builder())) {
					later.add(now.stage() + ":" + s.builder());
					helper.assertTrue(!dead[0].equals(s.builder()), "stage " + now.stage() + " went to the dead lead");
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(dead[0] != null, "the lead was never killed mid-part");
			helper.assertTrue(heir[0] != null, "nobody took the part on");
			WonderSite now = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(now.done(), "still at stage " + now.stage() + ", " + Math.round(WonderStages.stageProgress(level, now) * 100) + "%" + parts(level, now));
			List<BlockPos> left = unlikeButTheFinial(level, at);
			helper.assertTrue(left.isEmpty(), left.size() + " blocks unlike the drawing, e.g. " + left.stream().limit(3).toList());
		});
	}

	/**
	 * B139: a wall block of stage II the lead gives up on (someone stood on it all along; on the Colossus, a crewmate on
	 * top of the half-built robe) doesn't hold up the rest: the wall above it goes on, and the crew finishes the folly
	 * with only those blocks missing. The time-lapses stood still for good there, the block over the given-up one never
	 * "supported" and the crew's layers waiting on it.
	 */
	//$ gametest_ticks_batch TALL '24000' '"crewGaveUpUnder"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "crewGaveUpUnder")
	public void aBlockGivenUpMidWallDoesNotStallTheCrew(GameTestHelper helper) {
		gaveUpMidWall(helper, false);
	}

	/** B139: the same, saved and loaded the moment the blocks are given up: they stay given up, and the crew finishes. */
	//$ gametest_ticks_batch TALL '24000' '"crewGaveUpReload"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "crewGaveUpReload")
	public void aBlockGivenUpMidWallStaysGivenUpAfterAReload(GameTestHelper helper) {
		gaveUpMidWall(helper, true);
	}

	/**
	 * B139: a block the crew placed that then changes by itself (on the Hanging Gardens, the first terrace's farmland
	 * drying out to dirt before its water comes) is left as it is: the crew used to break it and lay it again, over and
	 * over, and the stage never ended. The folly is finished with only that block unlike the drawing.
	 */
	//$ gametest_ticks_batch TALL '24000' '"crewChangedBlock"'
	@GameTest(template = TALL, timeoutTicks = 24000, batch = "crewChangedBlock")
	public void aBlockThatChangesByItselfIsNotLaidAgainAndAgain(GameTestHelper helper) {
		Village v = village(helper, 4, 4);
		ServerLevel level = v.level();
		WonderSite begun = begin(helper, v);
		stock(helper, level, begun.stone().orElseThrow());
		BlueprintData.Placement at = begun.placement().orElseThrow();
		UUID id = begun.id();
		BlockPos[] changing = {null};
		long[] since = {-1};
		AtomicInteger changes = new AtomicInteger();
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			WonderSite now = WonderManager.get(level).site(id).orElse(null);
			if (now == null || now.done() || now.stage() != 2 || now.parts().isEmpty()) {
				return;
			}
			BuildSite part = BuildSiteManager.get(level).get(now.parts().get(0));
			BuildPlan plan = part == null ? null : part.plan(level);
			if (plan == null || part.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			List<BuildPlan.Step> walls = plan.steps(BuildPlan.Stage.STRUCTURE);
			if (changing[0] == null) {
				int low = walls.stream().mapToInt(s -> s.pos().getY()).min().orElse(0);
				walls.stream().filter(s -> s.pos().getY() == low + 1 && level.getBlockState(s.pos()).isAir()).findFirst()
					.ifPresent(s -> changing[0] = s.pos());
				return;
			}
			BuildPlan.Step step = walls.stream().filter(s -> s.pos().equals(changing[0])).findFirst().orElseThrow();
			if (!io.github.jcondedata.aliveworkplace.build.MaterialRules.matches(level.getBlockState(changing[0]), step.state())) {
				since[0] = -1;
				return;
			}
			// laid as drawn: a little later it changes by itself, every time (as farmland with no water dries out)
			if (since[0] < 0) {
				since[0] = level.getGameTime();
			} else if (level.getGameTime() - since[0] >= 40) {
				level.setBlockAndUpdate(changing[0], Blocks.COBBLESTONE.defaultBlockState());
				changes.incrementAndGet();
				since[0] = -1;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(changes.get() >= 1, "the chosen block was never laid and changed (" + changing[0] + ")");
			WonderSite now = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(now.done(), "still at stage " + now.stage() + ", " + Math.round(WonderStages.stageProgress(level, now) * 100) + "%, the block changed "
				+ changes.get() + " times" + parts(level, now));
			helper.assertTrue(changes.get() <= 2, "the crew laid the changed block again " + changes.get() + " times");
			List<BlockPos> left = unlikeButTheFinial(level, at);
			helper.assertTrue(left.stream().allMatch(p -> p.equals(changing[0])), left.size() + " blocks unlike the drawing besides the changed one, e.g. "
				+ left.stream().filter(p -> !p.equals(changing[0])).limit(4).toList());
		});
	}

	/**
	 * B139: a crop's farmland dried out to dirt while the build went up (its water not poured yet): the builder tills it
	 * again and plants the crop, rather than leave the field bare.
	 */
	//$ gametest_ticks_batch BIG '6000' '"cropOnDriedFarmland"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "cropOnDriedFarmland")
	public void aCropOnFarmlandThatDriedOutIsStillPlanted(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		// a row of five wheat on farmland, saved as a structure the way a player saves one, then taken up again
		BlockPos from = new BlockPos(3, 2, 6);
		for (int i = 0; i < 5; i++) {
			helper.setBlock(from.offset(i, 0, 0), Blocks.FARMLAND);
			helper.setBlock(from.offset(i, 1, 0), Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
		}
		ResourceLocation field = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "b139_field");
		StructureTemplate template = level.getStructureManager().getOrCreate(field);
		template.fillFromWorld(level, helper.absolutePos(from), new net.minecraft.core.Vec3i(5, 2, 1), false, null);
		for (int i = 0; i < 5; i++) {
			helper.setBlock(from.offset(i, 1, 0), Blocks.AIR);
			helper.setBlock(from.offset(i, 0, 0), Blocks.AIR);
		}
		BlockPos bench = new BlockPos(1, 2, 1);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, builder, null, field,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(from), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the field's build didn't start");
		UUID siteId = site.id();
		AtomicInteger dried = new AtomicInteger();
		helper.onEachTick(() -> {
			BuildSite now = BuildSiteManager.get(level).get(siteId);
			if (now == null || now.stage() != BuildPlan.Stage.DECORATION) {
				return;
			}
			for (int i = 0; i < 5; i++) { // farmland with no water and no crop on it dries out to dirt, as in vanilla
				BlockPos soil = helper.absolutePos(from.offset(i, 0, 0));
				if (level.getBlockState(soil).is(Blocks.FARMLAND) && level.getBlockState(soil.above()).isAir()) {
					level.setBlockAndUpdate(soil, Blocks.DIRT.defaultBlockState());
					dried.incrementAndGet();
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(dried.get() >= 1, "no farmland dried out before its crop");
			helper.assertTrue(BuildSiteManager.get(level).get(siteId) == null, "the field isn't finished");
			for (int i = 0; i < 5; i++) {
				BlockPos soil = from.offset(i, 0, 0);
				helper.assertBlockPresent(Blocks.FARMLAND, soil);
				helper.assertBlockPresent(Blocks.WHEAT, soil.above());
			}
		});
	}

	private static void gaveUpMidWall(GameTestHelper helper, boolean reload) {
		Village v = village(helper, 4, 4);
		ServerLevel level = v.level();
		var registries = level.registryAccess();
		WonderSite begun = begin(helper, v);
		stock(helper, level, begun.stone().orElseThrow());
		BlueprintData.Placement at = begun.placement().orElseThrow();
		UUID id = begun.id();
		ServerPlayer stander = ProtectionSpecGameTests.player(helper);
		List<BlockPos> holes = new ArrayList<>();
		AtomicBoolean placedStander = new AtomicBoolean(false);
		AtomicBoolean gone = new AtomicBoolean(false);
		List<CompoundTag> saved = new ArrayList<>();
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			if (!saved.isEmpty()) { // the builders come back the tick after leaving, as the world loads them
				for (CompoundTag tag : saved) {
					Villager back = EntityType.VILLAGER.create(level);
					back.load(tag);
					level.addFreshEntity(back);
				}
				saved.clear();
				return;
			}
			WonderSite now = WonderManager.get(level).site(id).orElse(null);
			if (now == null || now.done() || now.stage() != 2 || now.parts().isEmpty() || gone.get()) {
				return;
			}
			BuildSite part = BuildSiteManager.get(level).get(now.parts().get(0));
			BuildPlan plan = part == null ? null : part.plan(level);
			if (plan == null || part.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			if (!placedStander.get()) {
				// a wall's lowest course of stage II with two more courses over it: someone stands in its two lowest blocks
				Set<BlockPos> wall = new HashSet<>();
				plan.steps(BuildPlan.Stage.STRUCTURE).forEach(s -> wall.add(s.pos()));
				BlockPos spot = plan.steps(BuildPlan.Stage.STRUCTURE).stream().map(BuildPlan.Step::pos)
					.filter(p -> wall.contains(p.above()) && wall.contains(p.above(2)) && !wall.contains(p.below())
						&& !level.getBlockState(p.below()).canBeReplaced() && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir())
					.findFirst().orElse(null);
				if (spot == null) {
					return;
				}
				holes.add(spot);
				holes.add(spot.above());
				stander.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				placedStander.set(true);
				return;
			}
			if (!holes.stream().allMatch(part::gaveUp)) {
				stander.teleportTo(holes.get(0).getX() + 0.5, holes.get(0).getY(), holes.get(0).getZ() + 0.5);
				return;
			}
			// the lead gave both up: they step away, and the wall over them is the crew's to finish
			level.getServer().getPlayerList().remove(stander);
			gone.set(true);
			if (reload) {
				WonderManager wonders = WonderManager.get(level);
				level.getDataStorage().set(WonderManager.NAME, WonderManager.load(wonders.save(new CompoundTag(), registries), registries));
				BuildSiteManager sites = BuildSiteManager.get(level);
				level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(sites.save(new CompoundTag(), registries), registries));
				BuildSite back = BuildSiteManager.get(level).get(part.id());
				helper.assertTrue(back != null && holes.stream().allMatch(back::gaveUp), "the given-up blocks weren't kept through the reload");
				for (Villager b : v.builders()) {
					Villager live = (Villager) level.getEntity(b.getUUID());
					if (live != null) {
						saved.add(live.saveWithoutId(new CompoundTag()));
						live.discard();
					}
				}
			}
		});
		helper.succeedWhen(() -> {
			WonderSite now = WonderManager.get(level).site(id).orElseThrow();
			helper.assertTrue(gone.get(), "no stage II wall block was given up (stander placed " + placedStander.get() + ", at " + holes + " "
				+ holes.stream().map(p -> level.getBlockState(p).getBlock().toString()).toList() + "; stage " + now.stage() + ", done " + now.done() + parts(level, now) + ")");
			helper.assertTrue(now.done(), "still at stage " + now.stage() + ", " + Math.round(WonderStages.stageProgress(level, now) * 100) + "%" + parts(level, now));
			List<BlockPos> left = unlikeButTheFinial(level, at);
			helper.assertTrue(holes.containsAll(left), left.size() + " blocks unlike the drawing besides the given-up " + holes + ", e.g. "
				+ left.stream().filter(p -> !holes.contains(p)).limit(4).toList());
		});
	}

	/** The parts of {@code now}'s stage, for a failure's message: each one's stage, status and builder (and their job). */
	private static String parts(ServerLevel level, WonderSite now) {
		return ", lead " + now.builder() + ", parts " + now.parts().stream().map(p -> BuildSiteManager.get(level).get(p)).map(s -> s == null ? "done"
			: s.stage() + "/" + s.status() + " queued=" + s.isQueued() + " builder=" + s.builder() + " there=" + (s.builder() != null && level.getEntity(s.builder()) != null)
			+ " missing=" + s.missing() + " job=" + (s.builder() != null && level.getEntity(s.builder()) instanceof Villager b ? ModAttachments.BUILDER_JOB.get(b) : null)).toList();
	}

	private static List<String> keys(Component c) {
		List<String> out = new ArrayList<>();
		if (c.getContents() instanceof TranslatableContents t) {
			out.add(t.getKey());
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component a) {
					out.addAll(keys(a));
				}
			}
		}
		for (Component s : c.getSiblings()) {
			out.addAll(keys(s));
		}
		return out;
	}

	/**
	 * A builder with a build of its own queued doesn't join until every builder is called; only the owner may call; the
	 * stone names the lead and the crew, offers the call and then says it's made; the lead is the highest-level builder.
	 */
	//$ gametest_ticks_batch TALL '200' '"crewCall"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "crewCall")
	public void callingEveryBuilderBringsTheBusyOnes(GameTestHelper helper) {
		Village v = village(helper, 3, 4);
		ServerLevel level = v.level();
		WonderSite begun = begin(helper, v);
		UUID id = begun.id();
		BlockPos stone = begun.stone().orElseThrow();
		Villager lead = v.builders().get(0);
		Villager busy = v.builders().get(1);
		helper.assertTrue(begun.builder().equals(Optional.of(lead.getUUID())), "with no Master Architect the builder handed the plan leads");
		BuildSite part = Builders.activeSite(level, lead);
		helper.assertTrue(part != null && id.equals(part.wonder()), "the lead started stage I: " + part);
		// the busy builder: a build of its own waiting in its queue
		BuildSite own = BuildSiteManager.get(level).create(v.owner().getUUID(), "Owner", WHOLE,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, Mirror.NONE));
		own.setBuilder(busy.getUUID());
		own.setQueued(true);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(own.id()));
		for (int i = 0; i < 20; i++) {
			busy.tickCount = i; // whatever second it is
			helper.assertTrue(WonderCrews.join(level, busy) == null, "a builder with its own build waiting joined before the call");
		}
		Component status = WonderStages.statusText(level, stone);
		helper.assertTrue(keys(status).contains("message.aliveworkplace.wonder.status.crew") && keys(status).contains("message.aliveworkplace.wonder.status.call"),
			"the stone names the crew and offers the call: " + keys(status));
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		stranger.teleportTo(stone.getX() + 0.5, stone.getY() + 1, stone.getZ() + 0.5);
		helper.assertTrue(WonderCrews.call(stranger) == 0 && !WonderManager.get(level).site(id).orElseThrow().called(), "a stranger called the builders");
		v.owner().teleportTo(stone.getX() + 0.5, stone.getY() + 1, stone.getZ() + 0.5);
		helper.assertTrue(WonderCrews.call(v.owner()) == 1 && WonderManager.get(level).site(id).orElseThrow().called(), "the owner's call wasn't made");
		BuildSite joined = WonderCrews.join(level, busy);
		helper.assertTrue(joined != null && joined.id().equals(part.id()) && ModAttachments.BUILDER_JOB.get(busy).helper(), "the called builder didn't join: " + joined);
		helper.assertTrue(keys(WonderStages.statusText(level, stone)).contains("message.aliveworkplace.wonder.status.called"), "the stone says the call is made");
		// the lead of a next stage: the highest-level builder of the crew
		Villager expert = v.builders().get(2);
		expert.setVillagerData(expert.getVillagerData().setLevel(4));
		WonderSite now = WonderManager.get(level).site(id).orElseThrow();
		helper.assertTrue(expert.getUUID().equals(WonderCrews.lead(level, now, List.of(lead.getUUID(), busy.getUUID(), expert.getUUID()), lead.getUUID())),
			"the Expert doesn't lead the next stage");
		helper.assertTrue(lead.getUUID().equals(WonderCrews.lead(level, now, List.of(lead.getUUID(), busy.getUUID()), lead.getUUID())),
			"of equals the lead so far keeps it");
		helper.succeed();
	}
}
