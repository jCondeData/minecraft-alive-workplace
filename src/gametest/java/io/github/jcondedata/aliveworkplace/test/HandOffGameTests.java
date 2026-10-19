package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuilderWork;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.fish.FisherWork;
import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * B220: a villager handed the item for their job (a fishing rod, a blueprint) gets on with it at once, through the
 * player's own right-click; outside the work day they say when they start instead.
 */
public class HandOffGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	/** How soon a fresh hire must be seen at work: half a second (it takes them one or two ticks). */
	private static final int PROMPT = 10;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);

	/** Mid-shift, a fisherman handed a rod is out fishing (walking to the water, the line cast) within two seconds. */
	//$ gametest_ticks_batch BIG_AREA '400' '"handOffFisher"'
	@GameTest(template = BIG_AREA, timeoutTicks = 400, batch = "handOffFisher")
	public void fishermanHandedARodStartsAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager villager = fisherman(helper, 5000);
		ServerPlayer player = player(helper, new ItemStack(Items.FISHING_ROD));
		InteractionResult result = rightClick(player, villager);
		helper.assertTrue(result == InteractionResult.SUCCESS, "the rod wasn't taken: " + result);
		helper.assertTrue(Fishers.isHired(villager), "the fisherman wasn't hired");
		measure(helper, villager, FisherWork.class);
	}

	/**
	 * Late in the shift (when vanilla's fishermen are done for the day and gossip), a fisherman handed a rod takes our
	 * longer hours at once: before B220's fix the new brain kept vanilla's gossip for a second.
	 */
	//$ gametest_ticks_batch BIG_AREA '400' '"handOffLateShift"'
	@GameTest(template = BIG_AREA, timeoutTicks = 400, batch = "handOffLateShift")
	public void fishermanHandedARodLateInTheShiftStartsAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager villager = fisherman(helper, 9500);
		ServerPlayer player = player(helper, new ItemStack(Items.FISHING_ROD));
		rightClick(player, villager);
		helper.assertTrue(Fishers.isHired(villager), "the fisherman wasn't hired");
		measure(helper, villager, FisherWork.class);
	}

	/** Mid-shift, a builder handed a placed blueprint is at work (off to the chests or the site) within two seconds. */
	//$ gametest_ticks_batch AREA '400' '"handOffBuilder"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "handOffBuilder")
	public void builderHandedABlueprintStartsAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager villager = builder(helper, 5000);
		ServerPlayer player = player(helper, blueprint(helper));
		InteractionResult result = rightClick(player, villager);
		helper.assertTrue(result == InteractionResult.SUCCESS, "the blueprint wasn't taken: " + result);
		helper.assertTrue(Builders.activeSite(helper.getLevel(), villager) != null, "no build started");
		measure(helper, villager, BuilderWork.class);
	}

	/**
	 * Handed a blueprint just before the work day (the schedule's idle hour at dawn), a builder says when they start, and
	 * starts with the shift.
	 */
	//$ gametest_ticks_batch AREA '400' '"handOffBeforeShift"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "handOffBeforeShift")
	public void builderHandedABlueprintAtDawnSaysWhenAndStartsWithTheShift(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager villager = builder(helper, 940);
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = WonderFinishedGameTests.listener(helper, heard, "Jesse");
		player.setItemInHand(InteractionHand.MAIN_HAND, blueprint(helper));
		rightClick(player, villager);
		helper.assertTrue(Builders.activeSite(helper.getLevel(), villager) != null, "the blueprint should be taken before the shift");
		String name = villager.getDisplayName().getString();
		helper.assertTrue(heard.stream().anyMatch(c -> c.getString().equals(name + " starts work when their work day begins, in under a minute.")),
			"not told when the work starts: " + heard);
		// the shift begins at 1000; a brain looks at its schedule once a second, and a builder starting a shift takes up to
		// a second to set off (83 ticks measured, 2026-10-19)
		measure(helper, villager, BuilderWork.class, 1000 - 940 + 20 + 20);
	}

	/** At night a fisherman handed a rod stays in bed: told they start in the morning, and no fishing runs. */
	//$ gametest_ticks_batch BIG_AREA '200' '"handOffAtNight"'
	@GameTest(template = BIG_AREA, timeoutTicks = 200, batch = "handOffAtNight")
	public void fishermanHandedARodAtNightStartsInTheMorning(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager villager = fisherman(helper, 15000);
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = WonderFinishedGameTests.listener(helper, heard, "Jesse");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
		rightClick(player, villager);
		helper.assertTrue(Fishers.isHired(villager), "the rod should be taken at night too");
		String name = villager.getDisplayName().getString();
		// 15000 to 1000 the next morning: 10000 ticks, about 8 minutes (rounded)
		helper.assertTrue(heard.stream().anyMatch(c -> c.getString().equals(name + " starts work when their work day begins, in about 8 minutes.")),
			"not told when the work starts: " + heard);
		helper.runAfterDelay(60, () -> {
			helper.assertFalse(running(villager, FisherWork.class), "the fisherman fishes through the night");
			helper.assertTrue(villager.getBrain().isActive(Activity.REST), "not resting: " + villager.getBrain().getActiveNonCoreActivity());
			helper.succeed();
		});
	}

	// --- helpers -------------------------------------------------------------------------------

	/**
	 * Fails unless {@code villager}'s job behaviour runs and they are seen at it (walking somewhere, or a step done) within
	 * {@link #PROMPT} ticks of the hand-off.
	 */
	private static void measure(GameTestHelper helper, Villager villager, Class<?> work) {
		measure(helper, villager, work, 0);
	}

	/** {@link #measure(GameTestHelper, Villager, Class)} for a shift that begins {@code shiftIn} ticks after the hand-off. */
	private static void measure(GameTestHelper helper, Villager villager, Class<?> work, int shiftIn) {
		long handedAt = helper.getTick();
		int[] seen = {-1, -1, -1}; // at work; the WORK activity; the job's behaviour running
		helper.onEachTick(() -> {
			int now = (int) (helper.getTick() - handedAt);
			if (seen[1] < 0 && villager.getBrain().isActive(Activity.WORK)) {
				seen[1] = now;
			}
			if (seen[2] < 0 && running(villager, work)) {
				seen[2] = now;
			}
			if (seen[0] < 0 && running(villager, work) && busy(villager)) {
				seen[0] = now;
			}
		});
		helper.runAfterDelay(shiftIn + PROMPT + 1, () -> {
			helper.assertTrue(seen[0] >= 0 && seen[0] <= shiftIn + PROMPT, "not at work " + (shiftIn + PROMPT) + " ticks after the hand-off (at work after " + seen[0] + ", WORK after " + seen[1]
				+ ", the job's behaviour after " + seen[2] + "; activity="
				+ villager.getBrain().getActiveNonCoreActivity() + ", day time " + helper.getLevel().getDayTime() % 24000
				+ ", running=" + villager.getBrain().getRunningBehaviors().stream().map(BehaviorControl::debugString).toList() + ")");
			helper.succeed();
		});
	}

	private static boolean running(Villager villager, Class<?> work) {
		for (BehaviorControl<?> behavior : villager.getBrain().getRunningBehaviors()) {
			// the job's behaviour, or a gate around it (work/Gated, which names what it wraps)
			if ((work.isInstance(behavior) || behavior.debugString().equals(work.getSimpleName()))
				&& behavior.getStatus() == Behavior.Status.RUNNING) {
				return true;
			}
		}
		return false;
	}

	/** Seen at work: walking somewhere, or the job's item in hand. */
	private static boolean busy(Villager villager) {
		return villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET) || !villager.getNavigation().isDone()
			|| !villager.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && !villager.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.FISHING_ROD)
			|| !villager.level().getEntitiesOfClass(io.github.jcondedata.aliveworkplace.fish.FishingBobber.class,
				villager.getBoundingBox().inflate(16), b -> b.owner() == villager).isEmpty();
	}

	private static InteractionResult rightClick(ServerPlayer player, Villager villager) {
		player.moveTo(villager.getX() + 1, villager.getY(), villager.getZ(), 90f, 0f);
		return UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
	}

	private static ServerPlayer player(GameTestHelper helper, ItemStack held) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		return player;
	}

	private static Villager fisherman(GameTestHelper helper, long dayTime) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime((int) dayTime);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(STATION, Blocks.BARREL);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(11, 1, 11))) {
			helper.setBlock(p, Blocks.WATER);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Jobs.employ(level, villager, helper.absolutePos(STATION), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
		return villager;
	}

	private static Villager builder(GameTestHelper helper, long dayTime) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime((int) dayTime);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(STATION, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		chest.setItem(2, new ItemStack(Items.OAK_DOOR));
		chest.setItem(3, new ItemStack(Items.TORCH));
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(STATION));
		return villager;
	}

	private static ItemStack blueprint(GameTestHelper helper) {
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, new Vec3i(5, 4, 5));
		blueprint.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow()
			.withPlacement(Optional.of(new BlueprintData.Placement(helper.getLevel().dimension().location(),
				helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE))));
		return blueprint;
	}
}
