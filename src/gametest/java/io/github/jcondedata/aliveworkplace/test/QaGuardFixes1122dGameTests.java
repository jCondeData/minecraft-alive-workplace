package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.GuardCombat;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane D (2026-11-22, lane-d-1122-1909): an adversarial check of B1474 and B1475, written from the bugs' texts and the
 * wiki rather than the fixes.
 *
 * <p>B1475 (raids.md, "Guards fight anything that carries the raider tag"; config-switches.md, {@code guardRadius}: "how
 * far from their workstation guards patrol and fight"): in a raid a guard turns out to a raider within guardRadius of
 * his post even where walls hide it; monsters, and anything outside a raid, still have to be seen (or be within 4 blocks).
 * The mobs here stand still, each shut in a stone cell, so the guard sees none of them wherever he walks (a first
 * try shut the guard in a room instead, but his patrol hops a stuck guard out of it); they stand more than 4 blocks
 * beyond his night patrol's square. Who he takes for his foe ({@link GuardCombat#foe}) is checked, each case both ways:
 * the same setup shows the guard does pick a foe once the one thing the rule hangs on changes. Also: a guard who went out
 * to a raider far beyond his night patrol is back at his post after the raid.
 *
 * <p>B1474 (sieges.md, "On a station he never climbs down to chase"): an archer on his station stays there while the
 * siege lasts, also across a reload of the archer; and the flip side, once the siege is over (stations are for sieges)
 * he is free again and answers the village bell from the walk.
 */
public class QaGuardFixes1122dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation RAIDERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_guard_fixes_1122d");
	private static final BlockPos HALL = new BlockPos(27, 2, 1);
	/**
	 * The Guard Post, with his night patrol's square (6 each way, and a step) inside the test area: a guard whose patrol
	 * took him over its edge stood in a chunk the GameTest server doesn't tick, and froze (no foe picked, at random).
	 */
	private static final BlockPos POST = new BlockPos(14, 2, 7);
	private static final BlockPos STAND = new BlockPos(13, 2, 6);
	private static final int NIGHT = 14000;
	/** How long a guard is watched for a foe he mustn't pick (his search runs every 10 ticks). */
	private static final int QUIET = 300;
	/** How long he has to pick the foe he must. */
	private static final int PICK = 200;

	// ---- Shared setup ----

	/** A village hall, its raid culture registered, night; everything reset after (as QaBarracksTurnOut1122dGameTests). */
	private static ServerLevel village(GameTestHelper helper, Integer guardRadius) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		if (guardRadius != null) {
			WorkplaceConfig config = new WorkplaceConfig();
			config.guardRadius = guardRadius;
			config.apply();
		}
		VillageHalls.RADIUS = 32;
		helper.setDayTime(NIGHT);
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = culture();
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(RAIDERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			BattleStations.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return level;
	}

	private static Culture culture() {
		return Culture.read(RAIDERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": []}").getAsJsonObject());
	}

	/** A guard with a sword at his Guard Post, out in the open. */
	private static Villager guard(GameTestHelper helper) {
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, STAND);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Leftovers.after(helper, v::discard);
		return v;
	}

	/** Shuts {@code at} in a stone cell (walls and a roof round a space two tall), so nobody outside sees what stands in it. */
	private static void cell(GameTestHelper helper, BlockPos at) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 2, 1))) {
			if (p.getX() != at.getX() || p.getZ() != at.getZ() || p.getY() == at.getY() + 2) {
				helper.setBlock(p, Blocks.STONE);
			}
		}
	}

	/** Takes the cell round {@code at} away. */
	private static void open(GameTestHelper helper, BlockPos at) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 2, 1))) {
			if (p.getX() != at.getX() || p.getZ() != at.getZ() || p.getY() == at.getY() + 2) {
				helper.setBlock(p, Blocks.AIR);
			}
		}
	}

	/** A still mob of {@code type} at {@code at}, with the raid tag when {@code raider}; with {@code shut}, in a {@link #cell}. */
	private static Mob mob(GameTestHelper helper, EntityType<? extends Mob> type, BlockPos at, boolean raider, boolean shut) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(at);
		Mob mob = type.create(level);
		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
		if (raider) {
			mob.addTag(VillageRaids.TAG);
		}
		if (shut) {
			cell(helper, at);
		}
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		Leftovers.after(helper, mob::discard);
		return mob;
	}

	private static void raid(GameTestHelper helper, List<Mob> raiders) {
		BlockPos hall = helper.absolutePos(HALL);
		VillageRaids.track(helper.getLevel(), hall, culture(), raiders);
		helper.assertTrue(VillageRaids.active(hall).isPresent(), "(setup) no raid on the village");
	}

	private static String state(GameTestHelper helper, Villager guard) {
		LivingEntity foe = GuardCombat.foe(guard);
		return "guard at " + helper.relativeVec(guard.position()) + ", fighting " + GuardCombat.isFighting(guard) + ", foe "
			+ (foe == null ? "none" : foe.getType().toShortString() + " at " + helper.relativeVec(foe.position())) + ", sleeping " + guard.isSleeping() + ", doing "
			+ guard.getBrain().getRunningBehaviors().stream().map(b -> b.debugString()).toList();
	}

	/**
	 * For {@link #QUIET} ticks the guard must take nobody for his foe; then {@code change} runs (the one thing the
	 * rule hangs on), and within {@link #PICK} ticks he must take {@code then} for it.
	 */
	private static void quietThenPick(GameTestHelper helper, Villager guard, List<Mob> alive, String quietWhy, Runnable change, Mob then, String pickWhy) {
		long[] changed = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			for (Mob m : alive) {
				helper.assertTrue(m.isAlive(), "(setup) a " + m.getType().toShortString() + " is gone at tick " + tick);
			}
			helper.assertTrue(guard.isAlive(), "(setup) the guard died");
			if (changed[0] < 0) {
				if (GuardCombat.foe(guard) != null || GuardCombat.isFighting(guard)) {
					helper.fail(quietWhy + ": at tick " + tick + " he took a foe: " + state(helper, guard));
				}
				if (tick >= QUIET) {
					changed[0] = tick;
					change.run();
				}
				return;
			}
			if (GuardCombat.foe(guard) == then) {
				helper.succeed();
			} else if (tick > changed[0] + PICK) {
				BlockPos post = io.github.jcondedata.aliveworkplace.build.Builders.benchPos(guard).orElse(null);
				helper.fail(pickWhy + ": " + PICK + " ticks later he still hasn't taken it for his foe: " + state(helper, guard) + "; raid on "
					+ VillageRaids.active(helper.absolutePos(HALL)).isPresent() + "; his post " + (post == null ? "none" : helper.relativePos(post).toShortString())
					+ ", raid area round it " + (post != null && VillageRaids.raidArea(helper.getLevel(), post).isPresent()) + ", the foe "
					+ (post == null ? "?" : String.format("%.1f", Math.sqrt(then.blockPosition().distSqr(post)))) + " from it, guardRadius "
					+ io.github.jcondedata.aliveworkplace.guard.Guards.RADIUS + ", hall radius " + VillageHalls.RADIUS + ", alive " + then.isAlive() + ", tags " + then.getTags() + ", findFoe now " + findFoe(helper, guard) + ", isFoe " + io.github.jcondedata.aliveworkplace.guard.Guards.isFoe(then, guard)
					+ ", brain " + guard.getBrain().getClass().getSimpleName() + " core behaviors " + coreBehaviors(guard));
			}
		});
	}

	private static String findFoe(GameTestHelper helper, Villager guard) {
		try {
			java.lang.reflect.Method m = GuardCombat.class.getDeclaredMethod("findFoe", ServerLevel.class, Villager.class);
			m.setAccessible(true);
			Object foe = m.invoke(null, helper.getLevel(), guard);
			return foe == null ? "none" : ((LivingEntity) foe).getType().toShortString();
		} catch (ReflectiveOperationException e) {
			return e.toString();
		}
	}

	private static String coreBehaviors(Villager guard) {
		try {
			java.lang.reflect.Field f = net.minecraft.world.entity.ai.Brain.class.getDeclaredField("availableBehaviorsByPriority");
			f.setAccessible(true);
			StringBuilder out = new StringBuilder();
			Map<?, ?> byPriority = (Map<?, ?>) f.get(guard.getBrain());
			for (Object perActivity : byPriority.values()) {
				Map<?, ?> m = (Map<?, ?>) perActivity;
				for (Map.Entry<?, ?> e : m.entrySet()) {
					for (Object b : (java.util.Set<?>) e.getValue()) {
						if (b instanceof GuardCombat c) {
							out.append(e.getKey()).append(":GuardCombat=").append(c.getStatus());
							for (String name : List.of("calm", "searchTimer", "target")) {
								java.lang.reflect.Field x = GuardCombat.class.getDeclaredField(name);
								x.setAccessible(true);
								out.append(' ').append(name).append('=').append(x.get(c));
							}
							out.append(", game time ").append(guard.level().getGameTime()).append(' ');
						}
					}
				}
			}
			return out.toString();
		} catch (ReflectiveOperationException e) {
			return e.toString();
		}
	}

	// ---- B1475: who a guard takes for his foe ----

	/**
	 * guardRadius set to 16 (config): in a night raid a raider 17 blocks out from the Guard Post, shut in its cell, is
	 * outside guardRadius and not his foe; moved to 15 out (inside it), still shut in, he takes it.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaGuardFixes1122dRadiusEdge"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaGuardFixes1122dRadiusEdge")
	public void qaAHiddenRaiderJustOutsideGuardRadiusIsNoFoeAndJustInsideIs(GameTestHelper helper) {
		village(helper, 16);
		Villager guard = guard(helper);
		BlockPos outside = POST.offset(0, 0, 17);
		BlockPos inside = POST.offset(0, 0, 15);
		Mob raider = mob(helper, EntityType.HUSK, outside, true, true);
		cell(helper, inside);
		raid(helper, List.of(raider));
		BlockPos to = helper.absolutePos(inside);
		quietThenPick(helper, guard, List.of(raider), "a hidden raider 17 out of guardRadius 16 (outside it)",
			() -> raider.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5), raider,
			"the hidden raider moved to 15 out of guardRadius 16 (inside it)");
	}

	/**
	 * The default guardRadius (24): a raider 23.3 out of the post (inside it, the far edge of what he must answer), shut
	 * in its cell, is his foe at once.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaGuardFixes1122dRadiusFar"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaGuardFixes1122dRadiusFar")
	public void qaAHiddenRaiderAtTheFarEdgeOfTheDefaultGuardRadiusIsAFoe(GameTestHelper helper) {
		village(helper, null);
		Villager guard = guard(helper);
		Mob raider = mob(helper, EntityType.HUSK, POST.offset(-12, 0, 20), true, true); // 23.3 out
		raid(helper, List.of(raider));
		helper.onEachTick(() -> {
			if (GuardCombat.foe(guard) == raider) {
				helper.succeed();
			} else if (helper.getTick() > PICK) {
				helper.fail("a hidden raider 23.3 out (guardRadius 24) isn't his foe after " + PICK + " ticks: " + state(helper, guard));
			}
		});
	}

	/**
	 * No raid: a zombie 13 out, shut in its cell, is no foe (the sight rule is kept); once the cell is taken away (he sees
	 * it), it is.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaGuardFixes1122dNoRaidMonster"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaGuardFixes1122dNoRaidMonster")
	public void qaOutsideARaidAHiddenZombieDoesntPullTheGuardOut(GameTestHelper helper) {
		village(helper, null);
		Villager guard = guard(helper);
		BlockPos at = POST.offset(0, 0, 13);
		Mob zombie = mob(helper, EntityType.ZOMBIE, at, false, true);
		quietThenPick(helper, guard, List.of(zombie), "no raid, a zombie 13 out shut in its cell", () -> open(helper, at), zombie,
			"the zombie's cell taken away (in his sight)");
	}

	/**
	 * No raid: a mob carrying the raider tag (left over, no raid on the village) 13 out, shut in, is no foe either
	 * ("anything outside a raid keeps the old rule"); once a raid on the village is under way with it, it is.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaGuardFixes1122dNoRaidTagged"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaGuardFixes1122dNoRaidTagged")
	public void qaOutsideARaidAHiddenTaggedMobIsNoFoeUntilTheRaidBegins(GameTestHelper helper) {
		village(helper, null);
		Villager guard = guard(helper);
		Mob tagged = mob(helper, EntityType.HUSK, POST.offset(0, 0, 13), true, true);
		quietThenPick(helper, guard, List.of(tagged), "no raid, a hidden husk with the raider tag 13 out", () -> raid(helper, List.of(tagged)), tagged,
			"a raid on the village began with that husk");
	}

	/**
	 * In a raid, a plain zombie (no raider) 13 out and shut in is still no foe: only raiders are exempt from the sight
	 * rule. The raid's own raider stands shut in far beyond guardRadius. Once the zombie joins the raiders (the tag), it is.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaGuardFixes1122dRaidMonster"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaGuardFixes1122dRaidMonster")
	public void qaInARaidAHiddenZombieThatIsNoRaiderIsNoFoe(GameTestHelper helper) {
		village(helper, null);
		Villager guard = guard(helper);
		Mob zombie = mob(helper, EntityType.ZOMBIE, POST.offset(0, 0, 13), false, true);
		Mob far = mob(helper, EntityType.HUSK, new BlockPos(1, 2, 28), true, true); // 24.7 from the post, beyond guardRadius
		raid(helper, List.of(far));
		quietThenPick(helper, guard, List.of(zombie, far), "in a raid, a hidden zombie (no raider) 13 out", () -> zombie.addTag(VillageRaids.TAG), zombie,
			"the zombie given the raider tag in the raid");
	}

	/**
	 * In a night raid a raider 18 out, behind a wall the guard can't see past, draws him out of his night patrol's square
	 * (6); once it is dead and the raid over, he walks back and is within his square (and a step) of his post again
	 * within 1,200 ticks.
	 */
	//$ gametest_ticks_batch AREA '2600' '"qaGuardFixes1122dGoesBack"'
	@GameTest(template = AREA, timeoutTicks = 2600, batch = "qaGuardFixes1122dGoesBack")
	public void qaAGuardWhoWentFarOutToAHiddenRaiderIsBackAtHisPostAfterTheRaid(GameTestHelper helper) {
		village(helper, null);
		Villager guard = guard(helper);
		for (BlockPos p : BlockPos.betweenClosed(POST.offset(-4, 0, 3), POST.offset(4, 3, 3))) {
			helper.setBlock(p, Blocks.STONE);
		}
		Mob raider = mob(helper, EntityType.HUSK, POST.offset(0, 0, 18), true, false);
		raider.setHealth(6f);
		raid(helper, List.of(raider));
		BlockPos post = helper.absolutePos(POST);
		helper.assertFalse(guard.hasLineOfSight(raider), "(setup) the guard sees the raider past the wall");
		long[] dead = {-1};
		double[] farthest = {0};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			helper.assertTrue(guard.isAlive(), "(setup) the guard died");
			double away = Math.max(Math.abs(guard.getX() - (post.getX() + 0.5)), Math.abs(guard.getZ() - (post.getZ() + 0.5)));
			farthest[0] = Math.max(farthest[0], away);
			if (dead[0] < 0) {
				if (!raider.isAlive()) {
					dead[0] = tick;
				} else if (tick > 1000) {
					helper.fail("the guard didn't go out and kill the hidden raider 18 out within 1000 ticks: " + state(helper, guard) + ", raider health " + raider.getHealth());
				}
				return;
			}
			if (tick > dead[0] + 20 && away <= 7 && Math.abs(guard.getY() - post.getY()) <= 2) {
				helper.assertTrue(farthest[0] > 10, "(setup) he never went far out: at most " + farthest[0]);
				helper.succeed();
			} else if (tick > dead[0] + 1200) {
				helper.fail("1200 ticks after the raider died the guard isn't back within his night square of his post: " + String.format("%.1f", away) + " away, "
					+ state(helper, guard) + ", raid on " + VillageRaids.active(helper.absolutePos(HALL)).isPresent());
			}
		});
	}

	// ---- B1474: an archer on his station ----

	private record Siege(ServerLevel level, BlockPos hall, Villager archer, Mob seen, Mob hidden) {
	}

	/** ArcherStationPatrolB1474GameTests' night siege on Palisade I: a raider 12 out in his sight, a second walled in beyond guardRadius. */
	private static Siege palisadeSiege(GameTestHelper helper) {
		QaPalisadeNight1122dGameTests.Village village = QaPalisadeNight1122dGameTests.village(helper, QaPalisadeNight1122dGameTests.NIGHT_RAMS, "ram_gates");
		ServerLevel level = village.level();
		Blueprint blueprint = QaPalisadeNight1122dGameTests.stand(helper, StarterBlueprints.PALISADE);
		BlockPos wall = QaPalisadeNight1122dGameTests.WALL;
		int face = wall.getZ() + blueprint.size().getZ() - 1;
		Villager archer = QaPalisadeNight1122dGameTests.archer(helper);
		Mob seen = QaPalisadeNight1122dGameTests.raider(helper, new BlockPos(wall.getX() + 5, 2, face + 12));
		BlockPos cell = new BlockPos(wall.getX() + 5, 2, 27);
		for (BlockPos p : BlockPos.betweenClosed(cell.offset(-1, 0, -1), cell.offset(1, 2, 1))) {
			if (p.getX() != cell.getX() || p.getZ() != cell.getZ() || p.getY() == cell.getY() + 2) {
				helper.setBlock(p, Blocks.STONE);
			}
		}
		Mob hidden = QaPalisadeNight1122dGameTests.raider(helper, cell);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(seen, hidden));
		helper.assertTrue(Sieges.siege(level, village.hall()).isPresent(), "(setup) a night attack on a village behind a Palisade is no siege");
		return new Siege(level, village.hall(), archer, seen, hidden);
	}

	private static boolean siegeOn(Siege s) {
		return Sieges.siege(s.level(), s.hall()).map(x -> !x.over).orElse(false);
	}

	/**
	 * The archer is reloaded (saved and read back, as when his chunk unloads and loads) 40 ticks after his last raider in
	 * sight died. The siege goes on: 480 ticks later he must still be on the walk.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaGuardFixes1122dStationReload"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaGuardFixes1122dStationReload")
	public void qaAStationedArcherReloadedAfterHisFoeDiedStaysOnTheWalk(GameTestHelper helper) {
		Siege s = palisadeSiege(helper);
		Villager[] archer = {s.archer()};
		long[] on = {-1};
		long[] alone = {-1};
		long[] reloaded = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			Villager a = archer[0];
			if (on[0] < 0) {
				if (BattleStations.onStation(a)) {
					on[0] = tick;
				} else if (tick > 500) {
					helper.fail("(setup) the archer isn't on the walk within 500 ticks of the attack: at " + helper.relativeVec(a.position()));
				}
				return;
			}
			if (alone[0] < 0) {
				if (!s.seen().isAlive()) {
					alone[0] = tick;
				} else if (tick > 1100) {
					helper.fail("(setup) the archer didn't kill the raider 12 out by tick 1100");
				}
			}
			if (alone[0] >= 0 && reloaded[0] < 0 && tick >= alone[0] + 40) {
				CompoundTag tag = a.saveWithoutId(new CompoundTag());
				a.discard();
				Villager back = EntityType.VILLAGER.create(s.level());
				back.load(tag);
				s.level().addFreshEntity(back);
				Leftovers.after(helper, back::discard);
				archer[0] = back;
				reloaded[0] = tick;
				return;
			}
			if (reloaded[0] >= 0 && tick > reloaded[0] + 5) {
				BlockPos place = BattleStations.post(a).orElse(null);
				if (!BattleStations.onStation(a) || place == null || a.getY() < place.getY() - 0.1) {
					helper.fail("the reloaded archer left the walk at tick " + tick + " (reloaded at " + reloaded[0] + "): at " + helper.relativeVec(a.position())
						+ ", stationed " + BattleStations.stationed(a) + ", siege on " + siegeOn(s) + ", doing "
						+ a.getBrain().getRunningBehaviors().stream().map(b -> b.debugString()).toList());
				}
				if (tick >= reloaded[0] + 480) {
					helper.assertTrue(s.hidden().isAlive() && siegeOn(s), "(setup) the siege didn't last");
					helper.succeed();
				}
			}
		});
	}

	/**
	 * The flip side: stations are for sieges. Once the last raider is dead and the siege over, the archer is no longer held
	 * on the walk: when a player rings the village bell inside the palisade, he comes down to it.
	 */
	//$ gametest_ticks_batch AREA '3200' '"qaGuardFixes1122dStationEnds"'
	@GameTest(template = AREA, timeoutTicks = 3200, batch = "qaGuardFixes1122dStationEnds")
	public void qaOnceTheSiegeIsOverTheArcherLeavesTheWalkForTheBell(GameTestHelper helper) {
		Siege s = palisadeSiege(helper);
		Villager archer = s.archer();
		BlockPos bell = helper.absolutePos(new BlockPos(6, 2, 1));
		long[] on = {-1};
		long[] alone = {-1};
		long[] over = {-1};
		long[] rung = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			helper.assertTrue(archer.isAlive(), "(setup) the archer died");
			if (on[0] < 0) {
				if (BattleStations.onStation(archer)) {
					on[0] = tick;
				} else if (tick > 500) {
					helper.fail("(setup) the archer isn't on the walk within 500 ticks of the attack: at " + helper.relativeVec(archer.position()));
				}
				return;
			}
			if (alone[0] < 0) {
				if (!s.seen().isAlive()) {
					alone[0] = tick;
				} else if (tick > 1100) {
					helper.fail("(setup) the archer didn't kill the raider 12 out by tick 1100");
				}
				return;
			}
			if (over[0] < 0) {
				if (tick == alone[0] + 100) {
					helper.assertTrue(BattleStations.onStation(archer), "he left the walk while the siege was on (100 ticks after his last raider in sight died)");
					s.hidden().kill(); // the last raider falls: the raid, and so the siege, is over
				}
				if (tick > alone[0] + 100 && !siegeOn(s) && !BattleStations.stationed(archer)) {
					over[0] = tick;
					helper.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
					helper.assertTrue(((BellBlock) Blocks.BELL).attemptToRing(null, helper.getLevel(), bell, Direction.NORTH), "(setup) the bell didn't ring");
					rung[0] = tick;
				} else if (tick > alone[0] + 800) {
					helper.fail("700 ticks after the last raider died he still holds his station: stationed " + BattleStations.stationed(archer) + ", siege on " + siegeOn(s)
						+ ", raid on " + VillageRaids.active(s.hall()).isPresent());
				}
				return;
			}
			double dx = archer.getX() - (bell.getX() + 0.5);
			double dz = archer.getZ() - (bell.getZ() + 0.5);
			if (dx * dx + dz * dz <= 4 * 4 && Math.abs(archer.getY() - bell.getY()) <= 1.5) {
				helper.succeed();
			} else if (tick > rung[0] + 900) {
				helper.fail("900 ticks after the bell rang (the siege over) the archer hasn't come to it: at " + helper.relativeVec(archer.position()) + ", on his old station "
					+ BattleStations.onStation(archer) + ", stationed " + BattleStations.stationed(archer) + ", doing "
					+ archer.getBrain().getRunningBehaviors().stream().map(b -> b.debugString()).toList());
			}
		});
	}
}
