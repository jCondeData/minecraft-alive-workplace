package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane C (2026-11-22, lane-c-1122-1553): three landed job fixes, tested from their bug text rather than their code.
 * <ul>
 * <li>B1471: a finished Smithy II gives each of its four workers (armorer, miner, toolsmith, weaponsmith) a job block
 * of his own inside it; workers who arrive after the working day (as CI's did, at day ~9,600) still get theirs the next
 * morning, the armorer included.</li>
 * <li>B1494: an archer sent to a Lookout Tower station gets up it within 500 ticks of the siege though a raider stands
 * near his way (he shoots on the move); one who is still in the air when the siege starts gets there too; two archers
 * both get up; and on the other side of the boundary, a tower walled in so nobody can reach it is passed over within
 * 450 ticks rather than kept all siege.</li>
 * <li>B1466: a Builder killed mid-refresh of the owner's furnished old Stone House leaves it to a new Builder who
 * finishes it as a fresh build: killed late, with the new Builder at a bench of his own elsewhere; and with the world
 * saved and loaded between the death and the new Builder.</li>
 * </ul>
 */
public class QaJobFixes1122cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// --- B1471: the Smithy II's workers take its blocks ------------------------------------------------------------

	/** The Smithy II's origin and the street in front of it (its front is the z = 0 side), as in CraftWorkplacesGameTests. */
	private static final BlockPos SMITHY = new BlockPos(12, 2, 8);
	private static final String[] SMITHS = {"minecraft:armorer", "aliveworkplace:miner", "minecraft:toolsmith", "minecraft:weaponsmith"};
	private static final Block[] SMITH_BLOCKS = {Blocks.BLAST_FURNACE, Blocks.BLAST_FURNACE, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE};

	private record Smithy(ServerLevel level, BlockPos min, BlockPos max, List<Villager> workers) {
	}

	/** A finished Smithy II stood where a builder would have put it, recorded finished; nobody hired yet. */
	private static Smithy smithy(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		ResourceLocation id = StarterBlueprints.SMITHY_2.id();
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("missing " + id));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(SMITHY), Rotation.NONE, Mirror.NONE);
		for (Map.Entry<BlockPos, BlockState> e : BuildPlan.create(blueprint, placement).targets().entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
		}
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BlockPos min = helper.absolutePos(SMITHY);
		BlockPos max = min.offset(blueprint.size().getX() - 1, blueprint.size().getY() - 1, blueprint.size().getZ() - 1);
		// below its floor too: a basement goes under the clicked block
		min = min.below(StarterBlueprints.SMITHY_2.basement());
		for (int i = 0; i < SMITH_BLOCKS.length; i++) {
			Block block = SMITH_BLOCKS[i];
			long want = java.util.Arrays.stream(SMITH_BLOCKS).filter(b -> b == block).count();
			long have = BlockPos.betweenClosedStream(min, max).filter(p -> level.getBlockState(p).is(block)).count();
			helper.assertTrue(have == want, "(setup) the Smithy II stands with " + have + " " + block + ", its workers need " + want);
		}
		return new Smithy(level, min.immutable(), max.immutable(), new ArrayList<>());
	}

	/** The four smiths, jobless at their trade, walk up from the street in front. */
	private static void hire(GameTestHelper helper, Smithy s) {
		for (int i = 0; i < SMITHS.length; i++) {
			Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(13 + 3 * i, 2, 5));
			VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(SMITHS[i]));
			worker.setVillagerData(worker.getVillagerData().setProfession(job));
			worker.setVillagerXp(1);
			worker.refreshBrain(s.level());
			s.workers().add(worker);
		}
	}

	/** Why not yet, or null when each smith works at a block of his trade inside the Smithy II, no two at one. */
	private static String unsettled(GameTestHelper helper, Smithy s) {
		Set<BlockPos> taken = new HashSet<>();
		List<String> out = new ArrayList<>();
		for (int i = 0; i < s.workers().size(); i++) {
			Villager w = s.workers().get(i);
			if (!w.isAlive() || !BuiltInRegistries.VILLAGER_PROFESSION.getKey(w.getVillagerData().getProfession()).toString().equals(SMITHS[i])) {
				return "the " + SMITHS[i] + " is now " + (w.isAlive() ? w.getVillagerData().getProfession() : "dead");
			}
			BlockPos site = w.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
			boolean inside = site != null && site.getX() >= s.min().getX() && site.getX() <= s.max().getX() && site.getY() >= s.min().getY()
				&& site.getY() <= s.max().getY() && site.getZ() >= s.min().getZ() && site.getZ() <= s.max().getZ();
			if (site == null || !inside || !s.level().getBlockState(site).is(SMITH_BLOCKS[i]) || !taken.add(site)) {
				out.add(SMITHS[i] + " works at " + (site == null ? "null" : helper.relativePos(site).toShortString()) + " (at "
					+ w.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + ", potential "
					+ w.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(g -> g.pos().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()).orElse("none")
					+ ", activity " + w.getBrain().getActiveNonCoreActivity().orElse(null) + ")");
			}
		}
		return out.isEmpty() ? null : String.join("; ", out) + ", day time " + s.level().getDayTime() % 24000;
	}

	/**
	 * B1471's happy path, without a build's clock in the way: the smiths hired at day 2,000 at a finished Smithy II each
	 * take a block of their own inside it within 1,500 ticks (the fix's probe: 672). The armorer gets one of the two blast
	 * furnaces, the miner the other.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaJobFixes1122cSmithyDay"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaJobFixes1122cSmithyDay")
	public void qaTheFourSmithsTakeTheSmithyIIsBlocksInAWorkingDay(GameTestHelper helper) {
		Smithy s = smithy(helper);
		helper.setDayTime(2000);
		hire(helper, s);
		helper.onEachTick(() -> {
			String why = unsettled(helper, s);
			if (why == null) {
				helper.succeed();
			} else if (helper.getTick() > 1500) {
				helper.fail("not settled within 1,500 ticks of a working day: " + why);
			}
		});
	}

	/**
	 * B1471's failure as CI had it: the smiths arrive at day 9,600, after the working day, and night falls before they
	 * settle. Nobody is stranded: the next morning each has a block of his own inside the Smithy II within 2,500 ticks,
	 * the armorer included, and no job block's ticket is left held by a smith who works elsewhere.
	 */
	//$ gametest_ticks_batch HUGE '4200' '"qaJobFixes1122cSmithyNight"'
	@GameTest(template = HUGE, timeoutTicks = 4200, batch = "qaJobFixes1122cSmithyNight")
	public void qaSmithsWhoArriveAfterWorkTakeTheSmithyIIsBlocksNextMorning(GameTestHelper helper) {
		Smithy s = smithy(helper);
		helper.setDayTime(9600);
		hire(helper, s);
		long[] morning = {-1};
		helper.runAfterDelay(500, () -> helper.setDayTime(13000)); // night: they go to bed wherever they got to
		helper.runAfterDelay(1000, () -> {
			helper.setDayTime(1000); // the next morning
			morning[0] = helper.getTick();
		});
		helper.onEachTick(() -> {
			if (morning[0] < 0) {
				return;
			}
			String why = unsettled(helper, s);
			if (why == null) {
				for (int i = 0; i < SMITH_BLOCKS.length; i++) {
					BlockPos site = s.workers().get(i).getBrain().getMemory(MemoryModuleType.JOB_SITE).orElseThrow().pos();
					helper.assertTrue(s.level().getPoiManager().getFreeTickets(site) == 0, "the " + SMITHS[i] + "'s block at "
						+ helper.relativePos(site).toShortString() + " still has a free ticket");
				}
				helper.succeed();
			} else if (helper.getTick() > morning[0] + 2500) {
				helper.fail("not settled 2,500 ticks into the next morning: " + why);
			}
		});
	}

	// --- B1494: archers get up the Lookout Tower --------------------------------------------------------------------

	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_job_fixes_1122c");
	/** Lookout Tower I as drawn stands on x 10..18, z 4..12 (its south face at z 12); raiders come from the south. */
	private static final int BACK = 4;

	private record Tower(ServerLevel level, BlockPos origin, int face, Set<BlockPos> top, BlockPos hall, Culture culture) {
	}

	/**
	 * A village (hall at (1, 2, 1), a ram culture, a Palisade at (0, 2, 25) for its wall) with Lookout Tower I stood
	 * finished, unturned, centred in x with its footprint from z = {@link #BACK}: the reporter's village.
	 */
	private static Tower tower(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry build = StarterBlueprints.LOOKOUT_TOWER;
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 40;
		helper.setDayTime(6000);
		BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1));
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		List<BlueprintData.Placement> placed = new ArrayList<>();
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			BattleStations.forget();
			placed.forEach(p -> BuildSiteManager.get(level).forgetFinished(p));
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		int left = (30 - build.size().getX()) / 2;
		BlockPos origin = helper.absolutePos(new BlockPos(left, 2, BACK));
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(build.id(), placement, UUID.randomUUID());
		placed.add(placement);
		BlockPos wall = helper.absolutePos(new BlockPos(0, 2, 25));
		level.getStructureManager().get(StarterBlueprints.PALISADE.id()).orElseThrow().placeInWorld(level, wall, wall, new StructurePlaceSettings(),
			RandomSource.create(1L), 2);
		BlueprintData.Placement palisade = new BlueprintData.Placement(level.dimension().location(), wall, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.PALISADE.id(), palisade, UUID.randomUUID());
		placed.add(palisade);
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
		Set<BlockPos> top = new HashSet<>();
		for (BlockPos s : BattleStations.spots(BlueprintLibrary.get(level, build.id()).orElseThrow())) {
			top.add(origin.offset(s).above());
		}
		helper.assertTrue(!top.isEmpty(), "(setup) Lookout Tower I has no stations");
		return new Tower(level, origin, BACK + build.size().getZ() - 1, top, hall, culture);
	}

	/** Archers (bow in the off hand) with Guard Posts east of the tower, standing a block south of them. */
	private static List<Villager> archers(GameTestHelper helper, Tower tower, int count) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			BlockPos post = new BlockPos(27 - 2 * i, 2, 22);
			helper.setBlock(post, ModBlocks.GUARD_POST);
			Villager v = helper.spawn(EntityType.VILLAGER, post.south());
			Jobs.employ(tower.level(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
			v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
			out.add(v);
		}
		return out;
	}

	/** A still, tough raider {@code out} blocks south of the tower's south face, in line with its stations: the siege starts. */
	private static Mob raider(GameTestHelper helper, Tower tower, int out) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		int fx = (int) Math.round(tower.top().stream().mapToInt(BlockPos::getX).average().orElseThrow()) - base.getX();
		int fz = tower.face() + out;
		ServerLevel level = tower.level();
		Mob foe = EntityType.HUSK.create(level);
		foe.moveTo(base.getX() + fx + 0.5, base.getY() + 2, base.getZ() + fz + 0.5, 0f, 0f);
		foe.addTag(VillageRaids.TAG);
		foe.setNoAi(true);
		foe.setPersistenceRequired();
		foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
		foe.setHealth(500);
		level.addFreshEntity(foe);
		VillageRaids.track(level, tower.hall(), tower.culture(), List.of(foe));
		helper.assertTrue(Sieges.siege(level, tower.hall()).isPresent(), "(setup) no siege on the village");
		return foe;
	}

	private static String rel(GameTestHelper helper, BlockPos pos) {
		return pos.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	private static long arrows(GameTestHelper helper, Villager archer) {
		return helper.getLevel().getEntitiesOfClass(AbstractArrow.class, helper.getBounds().inflate(24), a -> a.getOwner() == archer).size();
	}

	/**
	 * Within 500 ticks every archer stands on a station, no two on one, at least one on the tower's top (with two, the
	 * second may take the Palisade: sieges.md sends an archer to the build with the fewest archers); then within 600 more
	 * the raider is hit from a station: by each archer on the tower's top when {@code eachOnTopHits}, else by any of them.
	 */
	private static void allUpAndHit(GameTestHelper helper, Tower tower, List<Villager> archers, Mob foe, String what, boolean eachOnTopHits) {
		Set<Villager> hit = new HashSet<>();
		long[] up = {-1};
		float[] health = {foe.getHealth()};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (up[0] < 0) {
				if (archers.stream().allMatch(BattleStations::onStation)) {
					up[0] = tick;
					Set<BlockPos> posts = new HashSet<>();
					for (Villager a : archers) {
						BlockPos post = BattleStations.post(a).orElseThrow();
						helper.assertTrue(posts.add(post), what + ": two archers on the station " + rel(helper, post));
					}
					BlockPos first = BattleStations.post(archers.get(0)).orElseThrow();
					helper.assertTrue(archers.stream().anyMatch(a -> tower.top().contains(BattleStations.post(a).orElseThrow())),
						what + ": no archer on the tower's top; stations " + archers.stream().map(a -> rel(helper, BattleStations.post(a).orElseThrow())).toList());
					helper.assertTrue(archers.size() > 1 || tower.top().contains(first), what + ": the station " + rel(helper, first) + " isn't on the tower's top");
				} else if (tick > 500) {
					helper.fail(what + ": archers not on a station within 500 ticks: " + archers.stream().filter(a -> !BattleStations.onStation(a))
						.map(a -> "at " + helper.relativeVec(a.position()) + ", sent to " + BattleStations.post(a).map(p -> rel(helper, p))).toList());
				}
				return;
			}
			if (foe.getHealth() < health[0]) {
				LivingEntity by = foe.getLastHurtByMob();
				if (by instanceof Villager v && archers.contains(v) && BattleStations.onStation(v)) {
					hit.add(v);
				}
				health[0] = foe.getHealth();
			}
			List<Villager> onTop = archers.stream().filter(a -> BattleStations.post(a).map(tower.top()::contains).orElse(false)).toList();
			if (eachOnTopHits ? hit.containsAll(onTop) : !hit.isEmpty()) {
				helper.succeed();
			} else if (tick > up[0] + 600) {
				helper.fail(what + ": the raider at " + helper.relativeVec(foe.position()) + " was hit by " + hit.size() + " archers from a station ("
					+ onTop.size() + " on the tower's top)");
			}
		});
	}

	/**
	 * B1494 with a raider just 3 blocks out from the tower's south face, well inside the 8 blocks an archer fights on his
	 * way the whole last stretch of it: he still gets up within 500 ticks, and then hits it from the top.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobFixes1122cTowerFoot"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobFixes1122cTowerFoot")
	public void qaAnArcherGetsUpLookoutTowerIPastARaiderAtItsFoot(GameTestHelper helper) {
		Tower tower = tower(helper);
		List<Villager> guards = archers(helper, tower, 1);
		Mob foe = raider(helper, tower, 3);
		allUpAndHit(helper, tower, guards, foe, "raider 3 out", true);
	}

	/**
	 * B1494's reach half: the archer is still in the air (8 blocks up, falling) when the siege starts, so the first look
	 * at his station finds no path. He gets up anyway within 500 ticks, and hits the raider from there.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobFixes1122cTowerFalling"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobFixes1122cTowerFalling")
	public void qaAnArcherStillFallingWhenTheSiegeStartsGetsUpLookoutTowerI(GameTestHelper helper) {
		Tower tower = tower(helper);
		List<Villager> guards = archers(helper, tower, 1);
		Villager archer = guards.get(0);
		archer.teleportTo(archer.getX(), archer.getY() + 8, archer.getZ());
		Mob foe = raider(helper, tower, 6);
		allUpAndHit(helper, tower, guards, foe, "falling at the start, raider 6 out", true);
	}

	/**
	 * B1494 with two archers and the reporter's raider 6 out: neither is left on the ground; within 500 ticks both are on
	 * stations of their own, one of them on the tower's top, and the raider is hit from a station. (Which of them hits it
	 * is B1469's sight question, not this bug's.)
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobFixes1122cTowerTwo"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobFixes1122cTowerTwo")
	public void qaTwoArchersGetUpLookoutTowerIPastANearRaider(GameTestHelper helper) {
		Tower tower = tower(helper);
		List<Villager> guards = archers(helper, tower, 2);
		Mob foe = raider(helper, tower, 6);
		allUpAndHit(helper, tower, guards, foe, "two archers, raider 6 out", false);
	}

	/**
	 * The other side of B1494's boundary (sieges.md, B1427): Lookout Tower I walled round with stone three blocks high,
	 * so nobody can get to it. Its stations are out of reach and the archer is not kept on one all siege: within 450 ticks
	 * he is no longer sent to the tower's top (the Palisade's walk is his to take instead), and he shoots at the raider.
	 */
	//$ gametest_ticks_batch HUGE '600' '"qaJobFixes1122cTowerWalled"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaJobFixes1122cTowerWalled")
	public void qaAnArcherIsNotKeptOnAWalledInLookoutTowersStation(GameTestHelper helper) {
		Tower tower = tower(helper);
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		int x0 = tower.origin().getX() - base.getX() - 2;
		int x1 = x0 + StarterBlueprints.LOOKOUT_TOWER.size().getX() + 3;
		int z0 = BACK - 2;
		int z1 = tower.face() + 2;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (x == x0 || x == x1 || z == z0 || z == z1) {
					for (int y = 2; y <= 4; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
					}
				}
			}
		}
		List<Villager> guards = archers(helper, tower, 1);
		Villager archer = guards.get(0);
		Mob foe = raider(helper, tower, 8);
		helper.onEachTick(() -> {
			if (BattleStations.onStation(archer) && BattleStations.post(archer).map(tower.top()::contains).orElse(false)) {
				helper.fail("the archer got onto a station of the walled-in tower at " + helper.relativeVec(archer.position()));
			}
			boolean sentUp = BattleStations.post(archer).map(tower.top()::contains).orElse(false);
			if (helper.getTick() > 450) {
				helper.assertFalse(sentUp, "450 ticks in, the archer at " + helper.relativeVec(archer.position()) + " is still sent to the walled-in tower's "
					+ BattleStations.post(archer).map(p -> rel(helper, p)).orElse("?"));
				helper.assertTrue(arrows(helper, archer) > 0 || foe.getHealth() < foe.getMaxHealth(),
					"passed over, he never shot at the raider " + archer.distanceTo(foe) + " blocks off");
				helper.succeed();
			}
		});
	}

	// --- B1466: a Builder killed mid-refresh of the furnished old Stone House --------------------------------------

	/** The house's origin: the middle of the area, so it fits however it's turned. */
	private static final BlockPos HOUSE = new BlockPos(15, 2, 15);

	private record Stage(ServerLevel level, ServerPlayer owner, ResourceLocation structure, BlueprintData.Placement placement, Blueprint asBuilt,
						 BlockPos hall, BlockPos corner, List<BlockPos> stores, Villager builder) {
	}

	/**
	 * The owner's furnished pre-B260 Stone House standing as built, turned by {@code rotation}; a hall, a Builder, his
	 * bench and four stores in the corner the house leaves free (as QaRefreshFurnishedShortGameTests stages it).
	 */
	private static Stage house(GameTestHelper helper, Rotation rotation) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation structure = AliveWorkplace.id("stone_house");
		FurnitureChoice furniture = RefreshMatchesFreshBuildGameTests.oldFurniture();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 40;
		helper.setDayTime(2000);
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000);
			}
		});
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			level.getServer().getPlayerList().remove(owner);
		});
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), rotation, Mirror.NONE);
		Blueprint old = Refreshes.olderDrawing(level.getServer(), structure, RefreshMatchesFreshBuildGameTests.BEFORE_B260)
			.orElseThrow(() -> new IllegalStateException("the Stone House's pre-B260 drawing isn't shipped"));
		Blueprint asBuilt = furniture.applyAsBuilt(old);
		Map<BlockPos, BlockState> built = BuildPlan.create(asBuilt, placement).targets();
		for (Map.Entry<BlockPos, BlockState> e : built.entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		BlockPos o = helper.absolutePos(HOUSE);
		double sx = built.keySet().stream().mapToInt(p -> p.getX() - o.getX()).average().orElse(0);
		double sz = built.keySet().stream().mapToInt(p -> p.getZ() - o.getZ()).average().orElse(0);
		int x0 = sx > 0 ? 1 : 22;
		int z0 = sz > 0 ? 1 : 22;
		BlockPos corner = new BlockPos(x0, 2, z0);
		helper.setBlock(corner, ModBlocks.VILLAGE_HALL);
		List<BlockPos> stores = List.of(new BlockPos(x0, 2, z0 + 4), new BlockPos(x0, 2, z0 + 6), new BlockPos(x0 + 4, 2, z0 + 4), new BlockPos(x0 + 4, 2, z0 + 6));
		stores.forEach(c -> helper.setBlock(c, Blocks.CHEST));
		BlockPos bench = new BlockPos(x0 + 2, 2, z0 + 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(x0 + 2, 2, z0 + 2));
		Builders.employ(level, builder, helper.absolutePos(bench));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(structure, placement, owner.getUUID());
		if (!furniture.isVanilla()) {
			manager.setFurniture(placement, furniture);
		}
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Stage(level, owner, structure, placement, asBuilt, helper.absolutePos(corner), corner, stores, builder);
	}

	/** The owner presses Refresh for the house on the hall's Repairs page; false when the page doesn't offer it. */
	private static boolean pressRefresh(GameTestHelper helper, Stage s) {
		ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), s.hall());
		menu.press(HallPages.slot(RepairsPage.PAGE), s.owner());
		int slot = -1;
		for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE && slot < 0; i++) {
			if (menu.icon(i).is(Items.PAINTING) || menu.icon(i).is(Items.SCAFFOLDING)) {
				slot = i;
			}
		}
		if (slot < 0) {
			return false;
		}
		menu.press(slot, s.owner());
		helper.assertTrue(menu.icon(RepairsPage.REFRESH).is(Items.BRUSH), "no Refresh button: " + menu.icon(RepairsPage.REFRESH));
		menu.press(RepairsPage.REFRESH, s.owner());
		return true;
	}

	private static Blueprint currentFor(Stage s) {
		Blueprint base = BlueprintLibrary.get(s.level(), s.structure()).orElseThrow();
		return Refreshes.refreshedFurniture(s.level(), s.structure(), BuildSiteManager.get(s.level()).furniture(s.placement())).apply(base, s.structure());
	}

	/** Every material the refresh needs, twice over, into the stores. */
	private static void stock(GameTestHelper helper, Stage s, Map<net.minecraft.world.item.Item, Integer> needs) {
		int at = 0;
		for (Map.Entry<net.minecraft.world.item.Item, Integer> need : needs.entrySet()) {
			for (int left = need.getValue() * 2 + 2; left > 0 && at < 27 * s.stores().size(); at++) {
				int n = Math.min(left, need.getKey().getDefaultMaxStackSize());
				Container store = helper.getBlockEntity(s.stores().get(at / 27));
				store.setItem(at % 27, new ItemStack(need.getKey(), n));
				left -= n;
			}
		}
	}

	/** The house afterwards is a fresh build of today's drawing (beds a villager claimed aside), nothing floating, no Refresh left offered. */
	private static void assertFresh(GameTestHelper helper, Stage s, Map<BlockPos, BlockState> was, Map<BlockPos, BlockState> now) {
		helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) == null, "still refreshing");
		helper.assertTrue(BuildSiteManager.get(s.level()).all().stream().noneMatch(b -> b.placement().equals(s.placement())), "a site left on the house");
		List<String> left = new ArrayList<>(RefreshMatchesFreshBuildGameTests.differences(s.level(), s.placement(), s.asBuilt(), currentFor(s)));
		Set<String> claimed = new HashSet<>();
		for (BlockPos pos : was.keySet()) {
			BlockState world = s.level().getBlockState(pos);
			if (world.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
				BlockPos head = world.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
					? pos : pos.relative(net.minecraft.world.level.block.BedBlock.getConnectedDirection(world));
				if (s.level().getPoiManager().getFreeTickets(head) == 0) {
					claimed.add(pos.toShortString());
					BlockState other = now.get(pos);
					if (other != null && other.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
						claimed.add(pos.relative(net.minecraft.world.level.block.BedBlock.getConnectedDirection(other)).toShortString());
					}
				}
			}
		}
		helper.assertTrue(left.stream().noneMatch(d -> claimed.stream().noneMatch(c -> d.startsWith(c + " "))),
			left.size() + " spots differ from a fresh build: " + left.subList(0, Math.min(10, left.size())) + "; claimed beds: " + claimed);
		Set<BlockPos> spots = new HashSet<>(was.keySet());
		spots.addAll(now.keySet());
		List<String> loose = new ArrayList<>();
		for (BlockPos pos : spots) {
			BlockState world = s.level().getBlockState(pos);
			if (!world.isAir() && !world.canSurvive(s.level(), pos)) {
				loose.add(pos.toShortString() + " " + world.getBlock().getDescriptionId());
			}
		}
		helper.assertTrue(loose.isEmpty(), "floating after the refresh: " + loose.subList(0, Math.min(10, loose.size())));
		helper.assertTrue(Refreshes.entries(s.level(), s.hall()).stream().noneMatch(e -> e.building().placement().equals(s.placement())),
			"the refreshed house still offers a refresh on the Repairs page");
		helper.assertTrue(BuildSiteManager.get(s.level()).refreshBegun(s.placement()) == null, "the finished refresh is still remembered as begun"); // B1500
	}

	/**
	 * The kill-and-replace run: the refresh pressed, the Builder killed {@code killAfter} ticks into it, then
	 * {@code between} (a save and load, or nothing), then a new Builder at {@code newBench} (spawned where the first Builder
	 * started, inside the area) takes it up, through the Repairs page if the house is offered there again, and finishes it as a
	 * fresh build. Fails at once if the new Builder dies.
	 */
	private static void killedAndReplaced(GameTestHelper helper, Stage s, int killAfter, BlockPos newBench, boolean reload) {
		Map<BlockPos, BlockState> was = BuildPlan.create(s.asBuilt(), s.placement()).targets();
		Map<BlockPos, BlockState> now = BuildPlan.create(currentFor(s), s.placement()).targets();
		Villager[] second = {null};
		boolean[] killed = {false};
		boolean[] repressed = {false};
		int[] ticks = {0};
		String[] from = {null};
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(pressRefresh(helper, s), "the old Stone House has no Refresh on the Repairs page");
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(site != null, "no Builder took the refresh up");
			from[0] = site.refreshFrom();
			stock(helper, s, site.plan(s.level()).materials());
		});
		helper.onEachTick(() -> {
			if (killed[0]) {
				helper.assertTrue(second[0] == null || second[0].isAlive(), "the new Builder died at " + (second[0] == null ? "?" : rel(helper, second[0].blockPosition())));
				if (!repressed[0] && second[0] != null && Refreshes.siteOf(s.level(), s.placement()) == null
					&& !BuildSiteManager.get(s.level()).refreshAsked(s.placement())) {
					repressed[0] = true;
					helper.assertTrue(pressRefresh(helper, s), "the half-refreshed house isn't on the Repairs page any more");
					helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) != null, "the new Builder didn't take the refresh up");
				}
				BuildSite taken = Refreshes.siteOf(s.level(), s.placement());
				// B1500: taken up again from the drawing it was begun from, not one guessed from the half-cleared house
				helper.assertTrue(taken == null || from[0].equals(taken.refreshFrom()),
					"begun from the older drawing " + from[0] + ", taken up again from " + (taken == null ? "?" : taken.refreshFrom()));
				return;
			}
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			if (site == null || ++ticks[0] < killAfter) {
				return;
			}
			killed[0] = true;
			io.github.jcondedata.aliveworkplace.build.BuildPlan.Stage stage = site.stage();
			s.builder().kill();
			BuildSite after = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(after == null || !s.builder().getUUID().equals(after.builder()), "the refresh is still set to the dead Builder");
			if (reload) { // the world saved and loaded again before anyone comes
				HolderLookup.Provider registries = s.level().registryAccess();
				s.level().getDataStorage().set(BuildSiteManager.NAME,
					BuildSiteManager.load(BuildSiteManager.get(s.level()).save(new CompoundTag(), registries), registries));
			}
			helper.assertTrue(after != null || from[0].equals(BuildSiteManager.get(s.level()).refreshBegun(s.placement())),
				"the drawing the refresh was begun from (" + from[0] + ") is forgotten" + (reload ? " after the load: " : ": ")
					+ BuildSiteManager.get(s.level()).refreshBegun(s.placement())); // B1500
			BuildSite loaded = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(loaded == null || !s.builder().getUUID().equals(loaded.builder()), "after the load the refresh is set to the dead Builder again");
			if (!s.level().getBlockState(helper.absolutePos(newBench)).is(ModBlocks.BUILDERS_BENCH)) {
				helper.setBlock(newBench, ModBlocks.BUILDERS_BENCH);
			}
			Villager next = helper.spawn(EntityType.VILLAGER, s.corner().offset(2, 0, 2)); // where the first one started, inside the area
			Builders.employ(s.level(), next, helper.absolutePos(newBench));
			ModAttachments.BUILDER_EMPLOYER.set(next, new Employer(s.owner().getUUID(), s.owner().getGameProfile().getName()));
			second[0] = next;
			AliveWorkplace.LOG.info("QA job fixes 1122c: Builder killed {} ticks into the refresh, in {} (site {}, reload {})", ticks[0], stage,
				after == null ? "gone" : "kept", reload);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(killed[0] && second[0] != null, "the Builder was never killed mid-way");
			assertFresh(helper, s, was, now);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(second[0]), "the new Builder is still at it");
		});
	}

	/**
	 * B1466 late in the job: the Builder killed 2,500 ticks into the refresh (far later than the original test's 200),
	 * and the new Builder hired at a bench of his own between the far stores, not the dead one's: he takes the refresh up
	 * and the house ends as a fresh build of today's drawing.
	 */
	//$ gametest_ticks_batch HUGE '20000' '"qaJobFixes1122cRefreshLate"'
	@GameTest(template = HUGE, timeoutTicks = 20000, batch = "qaJobFixes1122cRefreshLate")
	public void qaABuilderKilledLateInARefreshLeavesItToANewBuilderAtAnotherBench(GameTestHelper helper) {
		Stage s = house(helper, Rotation.COUNTERCLOCKWISE_90);
		killedAndReplaced(helper, s, 2500, s.corner().offset(2, 0, 6), false);
	}

	/**
	 * B1466 across a save: the Builder killed 300 ticks into the refresh, the world's build sites saved and loaded, and
	 * only then a new Builder at the same bench: the refresh is not handed back to the dead one, the new one finishes it,
	 * a fresh build.
	 */
	//$ gametest_ticks_batch HUGE '20000' '"qaJobFixes1122cRefreshReload"'
	@GameTest(template = HUGE, timeoutTicks = 20000, batch = "qaJobFixes1122cRefreshReload")
	public void qaABuilderKilledMidRefreshThenASaveAndLoadStillLeavesItToANewBuilder(GameTestHelper helper) {
		Stage s = house(helper, Rotation.COUNTERCLOCKWISE_90);
		killedAndReplaced(helper, s, 300, s.corner().offset(2, 0, 4), true);
	}

	/**
	 * B1500: the same on the Stone House turned half round (CLOCKWISE_180), its Builder killed 2,500 ticks into the
	 * refresh (still clearing), a new Builder at the same bench: he finishes it as a fresh build of today's drawing, with
	 * none of the old drawing's furniture left by the front door.
	 */
	//$ gametest_ticks_batch HUGE '20000' '"qaJobFixes1122cRefreshHalfRound"'
	@GameTest(template = HUGE, timeoutTicks = 20000, batch = "qaJobFixes1122cRefreshHalfRound")
	public void qaABuilderKilledLateInARefreshOfAStoneHouseTurnedHalfRoundLeavesItToANewBuilder(GameTestHelper helper) {
		Stage s = house(helper, Rotation.CLOCKWISE_180);
		killedAndReplaced(helper, s, 2500, s.corner().offset(2, 0, 4), false);
	}
}
