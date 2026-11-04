package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.guard.MilitiaCombat;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Conscription;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA for Conscription and The Militia Drill (ROADMAP 30.10), from the spec, lane d 2026-11-04: the 24-block reach from
 * both sides (fighting, and the reformed work stop), a blow's damage plain and Strong, a vanilla pillager raid counts,
 * conscripts never panic, the sword is gone when the raid is fought off (none left on the ground), the guards' rules for
 * who's a foe, and a conscript killed by a raider gets a grave and a funeral like anyone (no sword dropped or revived).
 */
public class QaConscription1104dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String COLONY = "aliveworkplace_test:colony_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos MID = new BlockPos(22, 2, 22);
	private static final ResourceLocation CONSCRIPTION = AliveWorkplace.id("conscription");

	/**
	 * Two conscripts up on stone pillars (so neither can walk off), each with one raider on the ground: 23 blocks across
	 * (23.5 counting the height) draws the first into the fight; 26 across (26.5) leaves the second out of it, unarmed of
	 * blows, the whole time.
	 */
	//$ gametest_ticks_batch COLONY '500' '"qaConscriptReach1104d"'
	@GameTest(template = COLONY, timeoutTicks = 500, batch = "qaConscriptReach1104d")
	public void aRaiderWithin24BlocksDrawsAConscriptOneBeyondDoesNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 32);
		Leftovers.halls(helper);
		helper.setBlock(MID, ModBlocks.VILLAGE_HALL);
		pillar(helper, new BlockPos(4, 2, 10), 5);
		pillar(helper, new BlockPos(4, 2, 34), 5);
		Runnable[] ticks = eachTick(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = ready(helper, helper.absolutePos(MID));
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			night(level);
			Villager near = grown(helper, VillagerProfession.FARMER, new BlockPos(4, 7, 10));
			Villager far = grown(helper, VillagerProfession.FARMER, new BlockPos(4, 7, 34));
			Zombie nearRaider = raider(helper, new BlockPos(27, 2, 10));
			Zombie farRaider = raider(helper, new BlockPos(30, 2, 34));
			VillageRaids.track(level, helper.absolutePos(MID), 2);
			forget();
			AtomicBoolean nearFought = new AtomicBoolean();
			AtomicBoolean farFought = new AtomicBoolean();
			AtomicReference<String> farSeen = new AtomicReference<>("");
			ticks[0] = () -> {
				if (MilitiaCombat.isFighting(near)) {
					nearFought.set(true);
				}
				if (MilitiaCombat.isFighting(far) && !farFought.getAndSet(true)) {
					farSeen.set(String.format("at %.1f blocks from its raider", Math.sqrt(far.distanceToSqr(farRaider))));
				}
			};
			helper.runAfterDelay(300, () -> {
				helper.assertTrue(Conscription.militia(near) != null && Conscription.militia(far) != null, "not both conscripts in the raid");
				helper.assertTrue(nearFought.get(), String.format("a raider %.1f blocks away never drew the conscript (%s, holds %s)",
					Math.sqrt(near.distanceToSqr(nearRaider)), near.getBrain().getActiveNonCoreActivity(), near.getMainHandItem()));
				helper.assertTrue(!farFought.get(), "a raider beyond 24 blocks drew the conscript " + farSeen.get());
				helper.assertTrue(farRaider.getHealth() == farRaider.getMaxHealth(), "the far raider was struck: " + farRaider.getHealth());
				nearRaider.discard();
				farRaider.discard();
				VillageRaids.forget();
				helper.succeed();
			});
		});
	}

	/**
	 * Reformed (The Militia Drill): in a raid a villager with a raider 23 blocks away has his work stopped, one with the
	 * nearest raider 26 blocks away works on: "work stops only for villagers with a raider within 24 blocks", both sides.
	 */
	//$ gametest_ticks_batch COLONY '300' '"qaConscriptReformedReach1104d"'
	@GameTest(template = COLONY, timeoutTicks = 300, batch = "qaConscriptReformedReach1104d")
	public void reformedWorkStopsForARaiderWithin24BlocksNotBeyond(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 32);
		Leftovers.halls(helper);
		helper.setBlock(MID, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = ready(helper, helper.absolutePos(MID));
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			entity.setReforms(List.of(new Reforms.Progress(CONSCRIPTION.toString(), 3, true, 0)));
			night(level);
			Villager near = still(helper, new BlockPos(2, 2, 4));
			Villager far = still(helper, new BlockPos(2, 2, 40));
			Zombie nearRaider = raider(helper, new BlockPos(25, 2, 4));
			Zombie farRaider = raider(helper, new BlockPos(28, 2, 40));
			VillageRaids.track(level, helper.absolutePos(MID), 2);
			forget();
			helper.runAfterDelay(20, () -> {
				double n = Math.sqrt(near.distanceToSqr(nearRaider));
				double f = Math.sqrt(far.distanceToSqr(farRaider));
				helper.assertTrue(VillageRaids.raided(level, helper.absolutePos(MID), near.blockPosition()), "no raid on the village");
				helper.assertTrue(Conscription.workStopped(near), String.format("reformed, a villager %.1f blocks from a raider works on", n));
				helper.assertTrue(!Conscription.workStopped(far), String.format("reformed, a villager %.1f blocks from the nearest raider stopped work", f));
				// The reform taken away: the far one stops too (the raid is real for him).
				entity.setReforms(List.of());
				forget();
				helper.assertTrue(Conscription.workStopped(far), "unreformed, the far villager works on in the raid");
				nearRaider.discard();
				farRaider.discard();
				VillageRaids.forget();
				helper.succeed();
			});
		});
	}

	/**
	 * A plain conscript's first blow takes 3 health off a raider with no armor, a Strong one's 15% more (3.45); measured on
	 * two zombies whose armor is taken off, each penned with its conscript.
	 */
	//$ gametest_ticks_batch AREA '500' '"qaConscriptBlow1104d"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaConscriptBlow1104d")
	public void aBlowIsThreeAndAStrongOneFifteenPercentMore(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 16);
		Leftovers.halls(helper);
		boolean traits = Traits.ENABLED;
		Traits.ENABLED = true;
		Leftovers.after(helper, () -> Traits.ENABLED = traits);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		pen(helper, 1, 9);
		pen(helper, 15, 9);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = ready(helper, helper.absolutePos(HALL));
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			night(level);
			Villager plain = withTraits(helper, new BlockPos(2, 2, 11), Traits.Trait.DILIGENT);
			Villager strong = withTraits(helper, new BlockPos(16, 2, 11), Traits.Trait.STRONG);
			helper.assertTrue(Traits.strength(plain) == 1f && Traits.strength(strong) > 1f, "the traits: " + Traits.of(plain) + ", " + Traits.of(strong));
			Zombie one = raider(helper, new BlockPos(4, 2, 11));
			Zombie two = raider(helper, new BlockPos(18, 2, 11));
			for (Zombie z : List.of(one, two)) {
				z.getAttribute(Attributes.ARMOR).setBaseValue(0);
				z.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
				z.setHealth(z.getMaxHealth());
			}
			float full = one.getMaxHealth();
			VillageRaids.track(level, helper.absolutePos(HALL), 2);
			forget();
			float[] first = {-1, -1};
			helper.succeedWhen(() -> {
				if (first[0] < 0 && one.getHealth() < full) {
					first[0] = full - one.getHealth();
				}
				if (first[1] < 0 && two.getHealth() < full) {
					first[1] = full - two.getHealth();
				}
				helper.assertTrue(first[0] >= 0 && first[1] >= 0, "not both struck yet: " + first[0] + ", " + first[1]);
				helper.assertTrue(one.getLastHurtByMob() == plain && two.getLastHurtByMob() == strong, "struck by someone else");
				helper.assertTrue(Math.abs(first[0] - 3f) < 0.01f, "a plain conscript's blow took " + first[0] + ", not 3");
				helper.assertTrue(Math.abs(first[1] - 3.45f) < 0.01f, "a Strong conscript's blow took " + first[1] + ", not 3.45");
				one.discard();
				two.discard();
				VillageRaids.forget();
			});
		});
	}

	/**
	 * A vanilla pillager raid on the village (bad omen at its bell) counts as a raid: a penned farmer takes up the militia's
	 * sword; when the raid is stopped the sword is gone again.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaConscriptVanilla1104d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaConscriptVanilla1104d")
	public void aVanillaPillagerRaidCallsUpTheMilitia(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 16);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos bell = new BlockPos(15, 2, 4);
		helper.setBlock(bell, Blocks.BELL);
		pen(helper, 1, 9);
		Raid[] raid = new Raid[1];
		ServerPlayer[] player = new ServerPlayer[1];
		Leftovers.after(helper, () -> {
			if (raid[0] != null) {
				raid[0].stop();
			}
			if (player[0] != null) {
				player[0].discard();
			}
		});
		helper.runAfterDelay(5, () -> level.getPoiManager().take(h -> h.is(PoiTypes.MEETING), (h, p) -> true, helper.absolutePos(bell), 2)
			.orElseThrow(() -> new GameTestAssertException("the bell isn't a meeting point")));
		helper.runAfterDelay(10, () -> {
			VillageHallBlockEntity entity = ready(helper, helper.absolutePos(HALL));
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			helper.setDayTime(6000);
			Villager farmer = grown(helper, VillagerProfession.FARMER, new BlockPos(2, 2, 11));
			forget();
			helper.runAfterDelay(40, () -> {
				helper.assertTrue(!Conscription.isMilitiaSword(farmer.getMainHandItem()), "armed before the raid: " + farmer.getMainHandItem());
				helper.assertTrue(level.isVillage(helper.absolutePos(bell)), "the test area isn't a village");
				player[0] = helper.makeMockServerPlayerInLevel();
				raid[0] = level.getRaids().createOrExtendRaid(player[0], helper.absolutePos(bell));
				helper.assertTrue(raid[0] != null, "no vanilla raid");
				raid[0].setRaidOmenLevel(1);
				forget();
				AtomicBoolean armed = new AtomicBoolean();
				AtomicBoolean stopped = new AtomicBoolean();
				helper.succeedWhen(() -> {
					if (!armed.get()) {
						helper.assertTrue(raid[0].isActive(), "the vanilla raid ended before the farmer took up the sword (holds " + farmer.getMainHandItem() + ")");
						armed.set(Conscription.isMilitiaSword(farmer.getMainHandItem()));
					}
					helper.assertTrue(armed.get(), "not armed in a vanilla pillager raid: holds " + farmer.getMainHandItem()
						+ ", raided " + VillageRaids.raided(level, helper.absolutePos(HALL), farmer.blockPosition()));
					if (!stopped.getAndSet(true)) {
						raid[0].stop();
						forget();
					}
					helper.assertTrue(!Conscription.isMilitiaSword(farmer.getMainHandItem()) && !Conscription.isMilitiaSword(farmer.getOffhandItem()),
						"the sword stayed after the vanilla raid stopped: " + farmer.getMainHandItem());
				});
			});
		});
	}

	/**
	 * Hurt in a raid (vanilla's panic trigger, held 20 ticks), a conscript never panics, while a villager hurt the same way
	 * outside the village, with no edict over him, does.
	 */
	//$ gametest_ticks_batch COLONY '300' '"qaConscriptPanic1104d"'
	@GameTest(template = COLONY, timeoutTicks = 300, batch = "qaConscriptPanic1104d")
	public void aConscriptHurtInTheRaidNeverPanics(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 12);
		Leftovers.halls(helper);
		BlockPos hallPos = new BlockPos(6, 2, 6);
		helper.setBlock(hallPos, ModBlocks.VILLAGE_HALL);
		pen(helper, 2, 10);
		pen(helper, 36, 36);
		Zombie zombie = caged(helper, new BlockPos(26, 2, 6));
		Leftovers.after(helper, zombie::discard);
		Runnable[] ticks = eachTick(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(hallPos);
			VillageHallBlockEntity entity = ready(helper, hall);
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			helper.setDayTime(6000);
			Villager conscript = grown(helper, VillagerProfession.FARMER, new BlockPos(4, 2, 12));
			Villager outsider = grown(helper, VillagerProfession.FARMER, new BlockPos(38, 2, 38));
			VillageRaids.track(level, hall, 1);
			forget();
			AtomicBoolean conscriptPanicked = new AtomicBoolean();
			AtomicBoolean outsiderPanicked = new AtomicBoolean();
			AtomicInteger tick = new AtomicInteger();
			ticks[0] = () -> {
				// (hurt, held 20 ticks: vanilla's HurtBySensor erases a memory with no real damage behind it)
				int t = tick.incrementAndGet();
				if (t >= 25 && t < 45) {
					for (Villager v : List.of(conscript, outsider)) {
						v.getBrain().setMemory(MemoryModuleType.HURT_BY, level.damageSources().generic());
					}
				}
				if (conscript.getBrain().isActive(Activity.PANIC)) {
					conscriptPanicked.set(true);
				}
				if (outsider.getBrain().isActive(Activity.PANIC)) {
					outsiderPanicked.set(true);
				}
			};
			helper.runAfterDelay(150, () -> {
				helper.assertTrue(Conscription.militia(conscript) != null && Conscription.militia(outsider) == null,
					"the conscript isn't called up, or the outsider is");
				helper.assertTrue(outsiderPanicked.get(), "the outsider never panicked: the hurt wasn't staged");
				helper.assertTrue(!conscriptPanicked.get(), "a conscript panicked in the raid");
				VillageRaids.forget();
				helper.succeed();
			});
		});
	}

	/**
	 * Fought off for real: a conscript penned with the raid's one raider strikes it down; the raid then ends by itself (the
	 * hall's round), and within a moment the conscript's hands are empty and no stone sword lies on the ground.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaConscriptFoughtOff1104d"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaConscriptFoughtOff1104d")
	public void theSwordIsGoneWhenTheRaidIsFoughtOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 16);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		pen(helper, 1, 9);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper, hall);
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			night(level);
			Villager farmer = grown(helper, VillagerProfession.FARMER, new BlockPos(2, 2, 11));
			Zombie zombie = raider(helper, new BlockPos(4, 2, 11));
			VillageRaids.track(level, hall, 1);
			forget();
			AtomicBoolean armed = new AtomicBoolean();
			AtomicInteger overFor = new AtomicInteger();
			helper.succeedWhen(() -> {
				armed.compareAndSet(false, Conscription.isMilitiaSword(farmer.getMainHandItem()));
				helper.assertTrue(armed.get(), "never armed");
				helper.assertTrue(!zombie.isAlive(), "the raider still stands: " + zombie.getHealth());
				boolean over = VillageRaids.active(hall).isEmpty() && !VillageRaids.raided(level, hall, farmer.blockPosition());
				helper.assertTrue(over, "the raid isn't over with its raider dead");
				// (give the conscript a few ticks after the raid's end to put the sword away)
				helper.assertTrue(overFor.incrementAndGet() > 25, "the raid only just ended");
				helper.assertTrue(!Conscription.isMilitiaSword(farmer.getMainHandItem()) && !Conscription.isMilitiaSword(farmer.getOffhandItem()),
					"the sword stayed after the raid was fought off: " + farmer.getMainHandItem() + " / " + farmer.getOffhandItem());
				List<ItemEntity> swords = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), i -> i.getItem().is(Items.STONE_SWORD));
				helper.assertTrue(swords.isEmpty(), "a stone sword lies on the ground: " + swords);
			});
		});
	}

	/**
	 * In a raid a conscript penned with an iron golem, a creeper, another villager and a player strikes none of them (the
	 * guards' rules: never golems, villagers or players; creepers are left alone) and never starts a fight.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaConscriptFoes1104d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaConscriptFoes1104d")
	public void aConscriptStrikesNoGolemCreeperVillagerOrPlayer(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, 16);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		pen(helper, 1, 9);
		Zombie zombie = caged(helper, new BlockPos(16, 2, 4)); // the raid's raider, out of sight behind glass
		Leftovers.after(helper, zombie::discard);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, player::discard);
		Runnable[] ticks = eachTick(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = ready(helper, helper.absolutePos(HALL));
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			night(level);
			Villager farmer = grown(helper, VillagerProfession.FARMER, new BlockPos(2, 2, 11));
			IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 2, 10));
			golem.setNoAi(true);
			Creeper creeper = helper.spawn(EntityType.CREEPER, new BlockPos(4, 2, 12));
			creeper.setNoAi(true);
			creeper.setPersistenceRequired();
			Villager other = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 12));
			other.setNoAi(true);
			Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 9.5));
			player.teleportTo(at.x, at.y, at.z);
			VillageRaids.track(level, helper.absolutePos(HALL), 1);
			forget();
			AtomicBoolean armed = new AtomicBoolean();
			AtomicBoolean fought = new AtomicBoolean();
			ticks[0] = () -> {
				if (Conscription.isMilitiaSword(farmer.getMainHandItem())) {
					armed.set(true);
				}
				if (MilitiaCombat.isFighting(farmer)) {
					fought.set(true);
				}
				player.teleportTo(at.x, at.y, at.z);
			};
			helper.runAfterDelay(200, () -> {
				helper.assertTrue(armed.get(), "the conscript was never armed: the raid wasn't staged");
				helper.assertTrue(!fought.get(), "the conscript started a fight with no raider in sight");
				helper.assertTrue(golem.getLastHurtByMob() != farmer && golem.getHealth() == golem.getMaxHealth(), "the golem was struck: " + golem.getHealth());
				helper.assertTrue(creeper.getLastHurtByMob() != farmer && creeper.getHealth() == creeper.getMaxHealth(), "the creeper was struck: " + creeper.getHealth());
				helper.assertTrue(other.getLastHurtByMob() != farmer && other.getHealth() == other.getMaxHealth(), "the villager was struck: " + other.getHealth());
				helper.assertTrue(player.getLastHurtByMob() != farmer && player.getHealth() == player.getMaxHealth(), "the player was struck: " + player.getHealth());
				golem.discard();
				creeper.discard();
				other.discard();
				VillageRaids.forget();
				helper.succeed();
			});
		});
	}

	/**
	 * A conscript killed by a raider (a pillager's blow) leaves a grave with its funeral due, like anyone; the militia's
	 * sword isn't dropped, and a villager raised from that grave doesn't come back holding it.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaConscriptKilled1104d"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaConscriptKilled1104d")
	public void aConscriptKilledByARaiderGetsAGraveAndDropsNoSword(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		village(helper, 16);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Zombie zombie = caged(helper, new BlockPos(16, 2, 4)); // keeps the raid on after the pillager
		Leftovers.after(helper, zombie::discard);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity entity = ready(helper, helper.absolutePos(HALL));
			entity.setEdicts(List.of(new Edicts.InForce(CONSCRIPTION.toString(), Chronicle.day(level))));
			night(level);
			Villager farmer = grown(helper, VillagerProfession.FARMER, new BlockPos(5, 2, 15));
			Pillager pillager = helper.spawn(EntityType.PILLAGER, new BlockPos(5, 2, 17));
			pillager.setNoAi(true);
			pillager.setPersistenceRequired();
			pillager.addTag(VillageRaids.TAG);
			Leftovers.after(helper, pillager::discard);
			VillageRaids.track(level, helper.absolutePos(HALL), 2);
			forget();
			AtomicBoolean killed = new AtomicBoolean();
			helper.succeedWhen(() -> {
				if (!killed.get()) {
					helper.assertTrue(Conscription.isMilitiaSword(farmer.getMainHandItem()), "not armed yet: " + farmer.getMainHandItem());
					farmer.hurt(level.damageSources().mobAttack(pillager), 1000f);
					killed.set(true);
				}
				helper.assertTrue(farmer.isDeadOrDying(), "the conscript survived the blow");
				BlockPos died = farmer.blockPosition();
				List<BlockPos> graves = Graves.near(level, died, 4);
				helper.assertTrue(graves.size() == 1, "graves by the fallen conscript: " + graves);
				BlockPos grave = graves.get(0);
				helper.assertTrue(level.getBlockEntity(grave) instanceof GraveBlockEntity g && g.funeralDue(), "the grave waits for no funeral");
				List<ItemEntity> swords = level.getEntitiesOfClass(ItemEntity.class, new AABB(died).inflate(8), i -> i.getItem().is(Items.STONE_SWORD));
				helper.assertTrue(swords.isEmpty(), "the conscript dropped a stone sword: " + swords);
				Villager raised = Graves.revive(level, grave);
				helper.assertTrue(raised != null, "nobody raised from the grave");
				Leftovers.after(helper, raised::discard);
				helper.assertTrue(!Conscription.isMilitiaSword(raised.getMainHandItem()) && !Conscription.isMilitiaSword(raised.getOffhandItem()),
					"raised from the grave holding the militia's sword: " + raised.getMainHandItem() + " / " + raised.getOffhandItem());
				raised.discard();
				VillageRaids.forget();
			});
		});
	}

	// --- Helpers (the same setup as ConscriptionGameTests) --------------------------------------------------------

	/**
	 * A per-tick check set later (slot 0) from a delayed step. onEachTick schedules one run per tick left, so called from
	 * inside a runAfterDelay step it grows the very map GameTestInfo is iterating, which crashed the test server (fastutil's
	 * iterator NPE): it's registered here, at the test's start, instead.
	 */
	private static Runnable[] eachTick(GameTestHelper helper) {
		Runnable[] slot = new Runnable[1];
		helper.onEachTick(() -> {
			if (slot[0] != null) {
				slot[0].run();
			}
		});
		return slot;
	}

	/** A stone pillar {@code height} blocks tall from {@code base} up (a villager on top can't walk off it). */
	private static void pillar(GameTestHelper helper, BlockPos base, int height) {
		for (int y = 0; y < height; y++) {
			helper.setBlock(base.above(y), Blocks.STONE);
		}
	}

	/** A grown villager with exactly {@code traits} (their UUID decides them). */
	private static Villager withTraits(GameTestHelper helper, BlockPos at, Traits.Trait... traits) {
		ServerLevel level = helper.getLevel();
		Villager villager = EntityType.VILLAGER.create(level);
		UUID id = PeopleGameTests.withTraits(traits);
		villager.setUUID(id);
		villager.moveTo(helper.absoluteVec(Vec3.atBottomCenterOf(at)));
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		villager.setVillagerXp(10);
		helper.assertTrue(level.addFreshEntity(villager), "a villager with that UUID is already about");
		villager.refreshBrain(level);
		return villager;
	}

	/** A grown farmer that stands still (no AI), for the work gate. */
	private static Villager still(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		return villager;
	}

	private static Zombie caged(GameTestHelper helper, BlockPos at) {
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				for (int y = 0; y <= 2; y++) {
					if (x != 0 || z != 0 || y == 2) {
						helper.setBlock(at.offset(x, y, z), Blocks.GLASS);
					}
				}
			}
		}
		helper.setBlock(at.above(3), Blocks.STONE);
		return raider(helper, at);
	}

	private static Zombie raider(GameTestHelper helper, BlockPos at) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		zombie.addTag(VillageRaids.TAG);
		return zombie;
	}

	private static void pen(GameTestHelper helper, int x0, int z0) {
		for (int y = 2; y <= 3; y++) {
			for (int x = x0 - 1; x <= x0 + 5; x++) {
				for (int z = z0 - 1; z <= z0 + 4; z++) {
					if (x == x0 - 1 || x == x0 + 5 || z == z0 - 1 || z == z0 + 4) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
					}
				}
			}
		}
	}

	private static Villager grown(GameTestHelper helper, VillagerProfession job, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(job).setLevel(2));
		villager.setVillagerXp(10);
		villager.refreshBrain(helper.getLevel());
		return villager;
	}

	private static void night(ServerLevel level) {
		level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 14000);
	}

	private static void forget() {
		CivicEffects.forget();
		Conscription.forget();
	}

	private static void village(GameTestHelper helper, int size) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = size;
		long time = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			helper.getLevel().setDayTime(time);
			VillageRaids.forget();
			VillageNeeds.forget();
			CivicEffects.forget();
			Conscription.forget();
			Moods.forget();
		});
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
		entity.setRank(VillageRanks.Rank.VILLAGE);
		entity.setEdicts(List.of());
		entity.setReforms(List.of());
		entity.setRaidWorkUntil(0);
		entity.setFestivalDay(-1);
		VillageRaids.forget();
		Leftovers.after(helper, () -> {
			entity.setEdicts(List.of());
			entity.setReforms(List.of());
			entity.setRaidWorkUntil(0);
			helper.getLevel().setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
			VillageRaids.forget();
			forget();
		});
		VillageNeeds.forget();
		forget();
		Moods.forget();
		return entity;
	}
}
