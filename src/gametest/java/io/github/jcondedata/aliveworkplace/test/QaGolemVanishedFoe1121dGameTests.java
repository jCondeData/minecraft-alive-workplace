package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.guard.PatrolMapItem;
import io.github.jcondedata.aliveworkplace.guard.WallSentries;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.mixin.MobAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-21) for B769 (commit e55ef2c14) against the spec of 29.15's Golem Smith golems (legend/GolemSmith,
 * GolemRoleGoal, guard/WallSentries): a golem fights first when it has a foe, and a foe that is gone (vanished,
 * despawned, dead) is no fight, so it goes back to its job at once. The fix's own test covers a Hauler whose foe was
 * discarded; these cover the Farmhand, a dead (not yet removed) foe, a live foe kept (the boundary), and the Wall
 * Sentry, whose post is held by a goal of its own. Each test has a batch of its own and clears leftovers first.
 */
public class QaGolemVanishedFoe1121dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos STOREHOUSE = new BlockPos(22, 2, 22);
	private static final BlockPos STORE_CHEST = new BlockPos(23, 2, 22);
	private static final BlockPos POST = new BlockPos(15, 2, 15);

	/** The golem's running goals and target goals, for a failure message. */
	private static String running(IronGolem golem) {
		MobAccessor mob = (MobAccessor) golem;
		List<String> goals = mob.aliveworkplace$goals().getAvailableGoals().stream().filter(WrappedGoal::isRunning)
			.map(w -> w.getGoal().getClass().getSimpleName()).toList();
		List<String> targets = mob.aliveworkplace$targets().getAvailableGoals().stream().filter(WrappedGoal::isRunning)
			.map(w -> w.getGoal().getClass().getSimpleName()).toList();
		return goals + " targets " + targets;
	}

	/** Where the golem is, what it targets and what runs, for every assertion's message. */
	private static String state(GameTestHelper helper, IronGolem golem) {
		return "golem at " + helper.relativePos(golem.blockPosition()) + " (" + String.format("%.2f %.2f %.2f", golem.getX(), golem.getY(), golem.getZ())
			+ "), target " + golem.getTarget() + (golem.getTarget() == null ? "" : " (alive " + golem.getTarget().isAlive() + ", removed "
			+ golem.getTarget().isRemoved() + ")") + ", running " + running(golem);
	}

	private static Zombie zombie(GameTestHelper helper, BlockPos at, IronGolem golem) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the sun
		zombie.setTarget(golem);
		golem.setTarget(zombie);
		Leftovers.after(helper, zombie::discard);
		return zombie;
	}

	// ---- Hauler ----

	private static Container round(GameTestHelper helper, BlockPos boxPos, int stacks) {
		GolemHaulerFightGameTests.strayStorehouses(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		helper.setBlock(boxPos, ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(boxPos);
		for (int i = 0; i < stacks; i++) {
			box.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		return box;
	}

	private static IronGolem build(GameTestHelper helper, BlockPos near, GolemSmith.Role role) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(near), role);
		helper.assertTrue(golem != null, "setup: no " + role + " golem was built");
		Leftovers.after(helper, golem::discard);
		return golem;
	}

	private static int carried(IronGolem golem) {
		return HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
	}

	/** "Back at work at once": 10 ticks after the foe went, the golem has no target and its role goal runs. */
	private static boolean[] backAtWorkWithin10(GameTestHelper helper, IronGolem golem, String what) {
		boolean[] checked = {false};
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(golem.getTarget() == null, "10 ticks after " + what + " the golem still has a target: " + state(helper, golem));
			helper.assertTrue(running(golem).contains("GolemRoleGoal"), "10 ticks after " + what + " the golem isn't back at work: " + state(helper, golem));
			checked[0] = true;
		});
		return checked;
	}

	/**
	 * The foe dies (killed, so not alive but still in the world for its death animation) before any of the golem's own
	 * target goals took it up: the hauler drops it within 10 ticks, is back at work, and hauls the Drop Box empty.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaVanishedFoeHaulerDead"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaVanishedFoeHaulerDead")
	public void aHaulerDropsAFoeThatDiedBeforeItsTargetGoalsTookItUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Container box = round(helper, new BlockPos(6, 2, 6), 3);
		IronGolem golem = build(helper, new BlockPos(14, 1, 14), GolemSmith.Role.HAULER);
		Zombie zombie = zombie(helper, new BlockPos(18, 2, 14), golem);
		zombie.kill();
		helper.assertTrue(!zombie.isAlive() && !zombie.isRemoved(), "setup: the zombie should be dead but not yet removed (alive "
			+ zombie.isAlive() + ", removed " + zombie.isRemoved() + ")");
		helper.assertTrue(golem.getTarget() == zombie, "setup: the golem's foe is the dead zombie: " + state(helper, golem));
		boolean[] checked = backAtWorkWithin10(helper, golem, "its foe died");
		helper.succeedWhen(() -> {
			helper.assertTrue(checked[0], "waiting for the 10-tick check");
			Container store = helper.getBlockEntity(STORE_CHEST);
			helper.assertTrue(store.countItem(Items.COBBLESTONE) == 3 * 64 && carried(golem) == 0, "the store has " + store.countItem(Items.COBBLESTONE)
				+ " of 192 cobblestone, the box " + box.countItem(Items.COBBLESTONE) + ", the golem carries " + carried(golem) + "; " + state(helper, golem));
		});
	}

	/**
	 * The boundary: a foe that is alive is a fight, so the hauler keeps it (fights rather than hauls). The zombie is
	 * given 1000 health so it outlives the 60-tick window; while it lives, the golem's target stays the zombie, its role
	 * goal doesn't run and it takes nothing from the Drop Box.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaVanishedFoeHaulerLive"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVanishedFoeHaulerLive")
	public void aHaulerKeepsAFoeThatIsStillAlive(GameTestHelper helper) {
		Leftovers.clear(helper);
		Container box = round(helper, new BlockPos(6, 2, 6), 3);
		IronGolem golem = build(helper, new BlockPos(14, 1, 14), GolemSmith.Role.HAULER);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(18, 2, 14));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		zombie.setHealth(1000f);
		zombie.setNoAi(true);
		Leftovers.after(helper, zombie::discard);
		golem.setTarget(zombie);
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			helper.assertTrue(zombie.isAlive(), "setup: the 1000-health zombie died within " + ticks[0] + " ticks");
			helper.assertTrue(golem.getTarget() == zombie, "tick " + ticks[0] + ": the hauler let go of a live foe (zombie health "
				+ zombie.getHealth() + "): " + state(helper, golem));
			helper.assertFalse(running(golem).contains("GolemRoleGoal"), "tick " + ticks[0] + ": the hauler works with a live foe: " + state(helper, golem));
			helper.assertTrue(carried(golem) == 0 && box.countItem(Items.COBBLESTONE) == 3 * 64, "tick " + ticks[0] + ": the hauler hauls with a live foe: carries "
				+ carried(golem) + ", the box has " + box.countItem(Items.COBBLESTONE) + "; " + state(helper, golem));
			if (ticks[0] >= 60) {
				helper.succeed();
			}
		});
	}

	// ---- Farmhand ----

	/**
	 * A Farmhand whose foe vanished before any of its target goals took it up drops it within 10 ticks, is back at work,
	 * and harvests a farmer's 3x3 field of ripe wheat into the farmer's chest.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaVanishedFoeFarmhand"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaVanishedFoeFarmhand")
	public void aFarmhandDropsAFoeThatVanishedAndTendsTheField(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int ticking = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
		level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(ticking, level.getServer()));
		for (int x = 4; x <= 6; x++) {
			for (int z = 4; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
			}
		}
		BlockPos composter = new BlockPos(16, 2, 5);
		BlockPos chestPos = new BlockPos(17, 2, 5);
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.setBlock(chestPos, Blocks.CHEST);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 7));
		farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
		farmer.refreshBrain(level);
		farmer.setNoAi(true);
		farmer.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composter)));
		ModAttachments.FARM_FIELD.set(farmer, new FieldJob(BoundingBox.fromCorners(helper.absolutePos(new BlockPos(4, 1, 4)), helper.absolutePos(new BlockPos(6, 1, 6)))));
		Leftovers.after(helper, farmer::discard);
		IronGolem golem = build(helper, new BlockPos(12, 1, 10), GolemSmith.Role.FARMHAND);
		Zombie zombie = zombie(helper, new BlockPos(16, 2, 12), golem);
		zombie.discard(); // gone before the golem's first tick: no target goal of its own ever holds this foe
		helper.assertTrue(golem.getTarget() == zombie, "setup: the farmhand's foe is the vanished zombie: " + state(helper, golem));
		boolean[] checked = backAtWorkWithin10(helper, golem, "its foe vanished");
		helper.succeedWhen(() -> {
			helper.assertTrue(checked[0], "waiting for the 10-tick check");
			Container chest = helper.getBlockEntity(chestPos);
			helper.assertTrue(chest.countItem(Items.WHEAT) == 9, "the farmer's chest has " + chest.countItem(Items.WHEAT) + " of 9 wheat; " + state(helper, golem));
		});
	}

	// ---- Wall Sentry ----

	private static ItemStack map(ServerLevel level, List<BlockPos> points) {
		ItemStack map = new ItemStack(ModItems.PATROL_MAP);
		map.set(ModComponents.PATROL, new PatrolMapItem.Route(Optional.of(level.dimension().location()), points));
		return map;
	}

	private static double off(GameTestHelper helper, IronGolem golem) {
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(POST));
		double dx = golem.getX() - at.x;
		double dz = golem.getZ() - at.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * A Wall Sentry posted by a player's sneak-click with a Patrol Map (all its own goals kept) stands 2.5 blocks off its
	 * post (inside the 3-block leash) set on a husk 4 blocks from the post (inside the 6-block range); the husk vanishes
	 * at once. A gone foe is no fight, so within 200 ticks the sentry is back within 1.5 blocks of its post (as
	 * QaSentryGameTests' walk-back) with no target, not standing off-post until its anger runs out (400 to 780 ticks).
	 */
	//$ gametest_ticks_batch AREA '400' '"qaVanishedFoeSentry"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaVanishedFoeSentry")
	public void aSentryWhoseFoeVanishedIsBackOnItsPost(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		IronGolem golem = build(helper, POST, GolemSmith.Role.SENTRY);
		player.setItemInHand(InteractionHand.MAIN_HAND, map(level, List.of(helper.absolutePos(POST))));
		player.setShiftKeyDown(true);
		UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, golem, null);
		helper.assertTrue(helper.absolutePos(POST).equals(WallSentries.post(golem)), "setup: not posted: " + WallSentries.post(golem));
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(POST));
		Husk husk = EntityType.HUSK.create(level);
		helper.assertTrue(husk != null, "setup: no husk");
		husk.moveTo(at.x + 4, at.y, at.z, 0f, 0f);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		level.addFreshEntity(husk);
		Leftovers.after(helper, husk::discard);
		golem.setPos(at.x, at.y, at.z + 2.5);
		golem.setDeltaMovement(Vec3.ZERO);
		golem.setTarget(husk);
		husk.discard();
		helper.assertTrue(golem.getTarget() == husk, "setup: the sentry's foe is the vanished husk: " + state(helper, golem));
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (off(helper, golem) <= 1.5 && golem.getTarget() == null) {
				helper.succeed();
			} else if (ticks[0] >= 200) {
				helper.fail(String.format("200 ticks after its foe vanished the sentry is %.2f blocks off its post (want <= 1.5, no target): ", off(helper, golem))
					+ state(helper, golem));
			}
		});
	}
}
