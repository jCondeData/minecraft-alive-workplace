package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderWork;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (qa-lane-b-1027-0338) for three landed builder bugs, written from their Expected lines, not from the fixes:
 * <ul>
 * <li>B120: "builders never die on their own build". B120GameTests builds the Palisade Tower with the builder by his
 * bench and QaHeightFreeUploadGameTests drops a builder mid-build by day. Here: the tower handed over by a player's
 * right-click while the builder stands in the middle of its footprint (every block of it goes up round him), and the
 * fall spared at night (a builder with a build, off shift) and after the builder is saved and loaded again.</li>
 * <li>B203: "a villager sent inside walks through the shut front door every time". DoorwayHeadroomGameTests walks one
 * villager straight in from the porch. Here: two villagers through the same shut door at once (vanilla shuts a door
 * behind a villager), and a villager coming at the shut door from far off to one side while it works out paths to a
 * spot it can never reach, for the Healing Center and the Manor.</li>
 * <li>B220: "the villager starts working promptly (within a second or two) after being handed the tool or blueprint".
 * HandOffGameTests hands a builder a blueprint mid-shift (5000), a fisherman a rod late in the shift, and before the
 * shift and at night. Here: a builder who has stood by his bench for a while handed a blueprint late in the shift
 * (10500) and on the shift's very first tick (1000), both at once and with no "starts later" line, and just after the
 * shift (11100), told the right wait and not at work.</li>
 * </ul>
 */
public class QaBuilderBugsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos TOWER = new BlockPos(12, 2, 12);
	private static final int DROP = 10; // 7 health of fall damage for a villager (20)
	/** How soon a fresh hire must be seen at work: half a second, well inside the Expected "a second or two". */
	private static final int PROMPT = 10;

	// --- B120: builders never die on their own build -------------------------------------------------------------

	/**
	 * A player hands a builder the Palisade Tower (right-click, blueprint placed) while he stands right in the middle of
	 * its footprint: every block of the tower goes up round him and he finishes it alive and unhurt by anything.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaBbTowerFromInside"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaBbTowerFromInside")
	public void aBuilderStandingInsideThePalisadeTowersFootprintBuildsItUnhurt(GameTestHelper helper) {
		ServerLevel level = rules(helper, true);
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, TOWER.offset(2, 0, 2));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		ServerPlayer player = player(helper, blueprint(helper, StarterBlueprints.PALISADE_TOWER.id(), StarterBlueprints.PALISADE_TOWER.size(), TOWER));
		InteractionResult result = rightClick(player, builder);
		helper.assertTrue(result == InteractionResult.SUCCESS, "the Palisade Tower blueprint wasn't taken: " + result);
		BlockPos away = helper.absolutePos(new BlockPos(27, 2, 2));
		player.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		BuildSite site = Builders.activeSite(level, builder);
		helper.assertTrue(site != null, "no build started");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for the Palisade Tower");
		BlockPos origin = helper.absolutePos(TOWER);
		List<String> hurts = new ArrayList<>();
		float[] health = {builder.getHealth()};
		helper.onEachTick(() -> {
			if (builder.getHealth() < health[0] || !builder.isAlive() && health[0] > 0) {
				DamageSource source = builder.getLastDamageSource();
				hurts.add("lost " + (health[0] - builder.getHealth()) + " health to " + (source == null ? "?" : source.getMsgId())
					+ " at blueprint " + builder.blockPosition().subtract(origin).toShortString() + " (tick " + helper.getTick()
					+ ", stage " + site.stage() + ", in " + level.getBlockState(builder.blockPosition()).getBlock() + ")");
			}
			health[0] = builder.isAlive() ? builder.getHealth() : 0;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(hurts.isEmpty(), "the builder was hurt on his own build: " + String.join("; ", hurts));
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"the Palisade Tower isn't finished: stage=" + site.stage() + " progress=" + Math.round(site.progress(plan) * 100) + "%");
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) of the tower wrong, e.g. "
				+ unfinished.stream().limit(20).map(p -> p.subtract(origin).toShortString()).toList() + " skipped=" + site.skipped());
			helper.assertTrue(builder.isAlive() && builder.getHealth() >= builder.getMaxHealth(), "the builder ended at " + builder.getHealth() + " health");
		});
	}

	/** At night, off shift but with his build still on (waiting for materials), a builder dropped ten blocks lands unhurt. */
	//$ gametest_ticks_batch HUGE '600' '"qaBbFallAtNight"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaBbFallAtNight")
	public void aBuilderWithABuildFallsUnhurtAtNight(GameTestHelper helper) {
		ServerLevel level = rules(helper, false);
		helper.setDayTime(15000);
		Villager builder = builderWithTower(helper);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(ModAttachments.BUILDER_JOB.has(builder), "the builder lost his build at night");
			drop(helper, builder, new BlockPos(24, 2, 24), hurt -> {
				helper.assertTrue(hurt == 0, "a builder with a build took " + hurt + " fall damage from " + DROP + " blocks at night (day time "
					+ level.getDayTime() % 24000 + ", " + builder.getBrain().getActiveNonCoreActivity() + ")");
				helper.succeed();
			});
		});
	}

	/** A builder mid-build saved and loaded again (a server restart) keeps his build and is still spared the fall. */
	//$ gametest_ticks_batch HUGE '600' '"qaBbFallAfterReload"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaBbFallAfterReload")
	public void aBuilderSavedAndLoadedMidBuildStillFallsUnhurt(GameTestHelper helper) {
		ServerLevel level = rules(helper, false);
		helper.setDayTime(2000);
		Villager builder = builderWithTower(helper);
		helper.runAfterDelay(20, () -> {
			CompoundTag saved = new CompoundTag();
			helper.assertTrue(builder.save(saved), "the builder wasn't saved");
			builder.discard();
			Entity loaded = EntityType.loadEntityRecursive(saved, level, e -> e);
			helper.assertTrue(loaded instanceof Villager, "the builder didn't load: " + loaded);
			Villager again = (Villager) loaded;
			helper.assertTrue(level.addFreshEntity(again), "the loaded builder couldn't be added back");
			helper.runAfterDelay(20, () -> {
				helper.assertTrue(ModAttachments.BUILDER_JOB.has(again), "the builder lost his build when saved and loaded");
				drop(helper, again, new BlockPos(24, 2, 24), hurt -> {
					helper.assertTrue(hurt == 0, "a builder saved and loaded mid-build took " + hurt + " fall damage from " + DROP + " blocks");
					helper.assertTrue(again.isAlive(), "the builder died");
					helper.succeed();
				});
			});
		});
	}

	// --- B203: in through a shut front door, every time ----------------------------------------------------------

	/**
	 * Two villagers walk into the Healing Center together by its shut front door (as built), each working out paths to
	 * another spot every tick as villagers in a village do: one opens it, and both must get in before it is shut.
	 */
	//$ gametest_ticks_batch HUGE '600' '"qaBbHealingCenterPair"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaBbHealingCenterPair")
	public void twoVillagersWalkInByTheHealingCentersShutDoorTogether(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		place(helper, "healing_center", new BlockPos(8, 1, 6));
		walkIn(helper, new BlockPos[]{new BlockPos(12, 2, 5), new BlockPos(14, 2, 4)},
			new BlockPos[]{new BlockPos(2, 2, 2), new BlockPos(27, 2, 2)}, new BlockPos(13, 2, 11), 10, 0.05);
	}

	/**
	 * A villager far off to one side of the Healing Center goes round to its shut front door and in, while working out
	 * paths every tick to a spot high in the air it can never reach.
	 */
	//$ gametest_ticks_batch HUGE '800' '"qaBbHealingCenterSide"'
	@GameTest(template = HUGE, timeoutTicks = 800, batch = "qaBbHealingCenterSide")
	public void aVillagerFromTheSideWalksInByTheHealingCentersShutDoor(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		place(helper, "healing_center", new BlockPos(8, 1, 6));
		walkIn(helper, new BlockPos[]{new BlockPos(3, 2, 3)}, new BlockPos[]{new BlockPos(2, 25, 27)}, new BlockPos(13, 2, 11), 10, 0.05);
	}

	/** The same for the Manor: from its far corner, round to its shut front door and up onto the grand stair. */
	//$ gametest_ticks_batch HUGE '800' '"qaBbManorSide"'
	@GameTest(template = HUGE, timeoutTicks = 800, batch = "qaBbManorSide")
	public void aVillagerFromTheSideWalksInByTheManorsShutDoor(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		place(helper, "manor", new BlockPos(5, 1, 6));
		walkIn(helper, new BlockPos[]{new BlockPos(2, 2, 2)}, new BlockPos[]{new BlockPos(28, 25, 28)}, new BlockPos(14, 2, 14), 14, 0.45);
	}

	/** Two villagers through the Manor's shut front door together, onto the grand stair. */
	//$ gametest_ticks_batch HUGE '600' '"qaBbManorPair"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaBbManorPair")
	public void twoVillagersWalkInByTheManorsShutDoorTogether(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		place(helper, "manor", new BlockPos(5, 1, 6));
		walkIn(helper, new BlockPos[]{new BlockPos(13, 2, 9), new BlockPos(15, 2, 8)},
			new BlockPos[]{new BlockPos(2, 2, 2), new BlockPos(28, 2, 2)}, new BlockPos(14, 2, 14), 14, 0.45);
	}

	// --- B220: a fresh hire starts promptly ----------------------------------------------------------------------

	/** Late in the shift (10500), a builder who has stood by his bench a while is handed a blueprint: at work at once, no wait announced. */
	//$ gametest_ticks_batch AREA '400' '"qaBbHandOffLateShift"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaBbHandOffLateShift")
	public void aSettledBuilderHandedABlueprintLateInTheShiftStartsAtOnce(GameTestHelper helper) {
		handOffInShift(helper, 10500, 40);
	}

	/** On the shift's very first tick (1000), a builder handed a blueprint starts at once, no wait announced. */
	//$ gametest_ticks_batch AREA '400' '"qaBbHandOffShiftStart"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaBbHandOffShiftStart")
	public void aBuilderHandedABlueprintAsTheShiftBeginsStartsAtOnce(GameTestHelper helper) {
		handOffInShift(helper, 1000, 0);
	}

	/**
	 * Just after the shift (11100), a builder handed a blueprint takes it and is told the wait to the next work day
	 * (13900 ticks: about 12 minutes), and doesn't start before then.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaBbHandOffAfterShift"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaBbHandOffAfterShift")
	public void aBuilderHandedABlueprintJustAfterTheShiftIsToldWhenHeStarts(GameTestHelper helper) {
		Villager builder = hutBuilder(helper, 11100);
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = WonderFinishedGameTests.listener(helper, heard, "Jesse");
		player.setItemInHand(InteractionHand.MAIN_HAND, blueprint(helper, TEST_HUT, new net.minecraft.core.Vec3i(5, 4, 5), new BlockPos(6, 2, 6)));
		InteractionResult result = rightClick(player, builder);
		helper.assertTrue(result == InteractionResult.SUCCESS, "the blueprint wasn't taken after the shift: " + result + " " + heard);
		helper.assertTrue(Builders.activeSite(helper.getLevel(), builder) != null, "no build started");
		String name = builder.getDisplayName().getString();
		String expected = name + " starts work when their work day begins, in about 12 minutes.";
		helper.assertTrue(heard.stream().anyMatch(c -> c.getString().equals(expected)), "not told \"" + expected + "\": " + heard);
		helper.runAfterDelay(100, () -> {
			helper.assertFalse(running(builder, BuilderWork.class), "the builder works after his shift");
			helper.assertFalse(builder.getBrain().isActive(Activity.WORK), "the builder is in WORK after his shift");
			helper.succeed();
		});
	}

	// --- helpers ------------------------------------------------------------------------------------------------

	private static void handOffInShift(GameTestHelper helper, int dayTime, int settle) {
		Villager builder = hutBuilder(helper, dayTime - settle);
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = WonderFinishedGameTests.listener(helper, heard, "Jesse");
		AtomicBoolean handed = new AtomicBoolean();
		Runnable handOff = () -> {
			// once: a delayed task that schedules more (measure does) was seen run twice, the second hand-off refused as an overlap
			if (!handed.compareAndSet(false, true)) {
				return;
			}
			helper.assertTrue(helper.getLevel().getDayTime() % 24000 >= dayTime, "handed over before the shift: " + helper.getLevel().getDayTime() % 24000);
			player.setItemInHand(InteractionHand.MAIN_HAND, blueprint(helper, TEST_HUT, new net.minecraft.core.Vec3i(5, 4, 5), new BlockPos(6, 2, 6)));
			InteractionResult result = rightClick(player, builder);
			helper.assertTrue(result == InteractionResult.SUCCESS, "the blueprint wasn't taken: " + result + " " + heard);
			helper.assertTrue(Builders.activeSite(helper.getLevel(), builder) != null, "no build started");
			helper.assertTrue(heard.stream().noneMatch(c -> c.getString().contains("starts work when")),
				"told to wait in the work day (day time " + helper.getLevel().getDayTime() % 24000 + "): " + heard);
			measure(helper, builder);
		};
		if (settle == 0) {
			handOff.run();
		} else {
			helper.runAfterDelay(settle, handOff);
		}
	}

	/** Fails unless the builder's job behaviour runs and he is seen at it within {@link #PROMPT} ticks of the hand-off. */
	private static void measure(GameTestHelper helper, Villager villager) {
		long handedAt = helper.getTick();
		int[] seen = {-1, -1};
		helper.onEachTick(() -> {
			int now = (int) (helper.getTick() - handedAt);
			if (seen[1] < 0 && villager.getBrain().isActive(Activity.WORK)) {
				seen[1] = now;
			}
			if (seen[0] < 0 && running(villager, BuilderWork.class) && busy(villager)) {
				seen[0] = now;
			}
		});
		helper.runAfterDelay(PROMPT + 1, () -> {
			helper.assertTrue(seen[0] >= 0 && seen[0] <= PROMPT, "not at work " + PROMPT + " ticks after the hand-off (at work after " + seen[0]
				+ ", WORK after " + seen[1] + "; activity=" + villager.getBrain().getActiveNonCoreActivity() + ", day time "
				+ helper.getLevel().getDayTime() % 24000 + ")");
			helper.succeed();
		});
	}

	private static boolean running(Villager villager, Class<?> work) {
		for (BehaviorControl<?> behavior : villager.getBrain().getRunningBehaviors()) {
			if ((work.isInstance(behavior) || behavior.debugString().equals(work.getSimpleName())) && behavior.getStatus() == Behavior.Status.RUNNING) {
				return true;
			}
		}
		return false;
	}

	private static boolean busy(Villager villager) {
		return villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET) || !villager.getNavigation().isDone()
			|| !villager.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
	}

	/** A builder by his bench with the test hut's materials in a chest, at {@code dayTime}. */
	private static Villager hutBuilder(GameTestHelper helper, int dayTime) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.buildSites(helper); // an earlier batch's site here would overlap this one and refuse the hand-off
		ServerLevel level = helper.getLevel();
		helper.setDayTime(dayTime);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		BlockPos chest = new BlockPos(2, 2, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chest, Blocks.CHEST);
		ChestBlockEntity box = helper.getBlockEntity(chest);
		box.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		box.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		box.setItem(2, new ItemStack(Items.OAK_DOOR));
		box.setItem(3, new ItemStack(Items.TORCH));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(bench));
		return villager;
	}

	/** A builder by his bench given the Palisade Tower at {@link #TOWER} (no chests: it waits for materials). */
	private static Villager builderWithTower(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.PALISADE_TOWER.id(), placement(helper, TOWER));
		helper.assertTrue(site != null && ModAttachments.BUILDER_JOB.has(builder), "the Palisade Tower didn't start");
		return builder;
	}

	private static ServerLevel rules(GameTestHelper helper, boolean free) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.buildSites(helper);
		ServerLevel level = helper.getLevel();
		boolean wasFree = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(wasFree, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		return level;
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	private static ItemStack blueprint(GameTestHelper helper, ResourceLocation id, net.minecraft.core.Vec3i size, BlockPos origin) {
		ItemStack blueprint = BlueprintItem.create(id, size);
		blueprint.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow()
			.withPlacement(Optional.of(placement(helper, origin))));
		return blueprint;
	}

	private static ServerPlayer player(GameTestHelper helper, ItemStack held) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		return player;
	}

	private static InteractionResult rightClick(ServerPlayer player, Villager villager) {
		player.moveTo(villager.getX() + 1, villager.getY(), villager.getZ(), 90f, 0f);
		return UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
	}

	/** Lifts the villager {@link #DROP} blocks above {@code over} and reports the damage once he lands. */
	private static void drop(GameTestHelper helper, Villager v, BlockPos over, java.util.function.IntConsumer landed) {
		v.setHealth(v.getMaxHealth());
		v.invulnerableTime = 0;
		v.fallDistance = 0;
		BlockPos abs = helper.absolutePos(over.above(DROP));
		v.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		v.setDeltaMovement(0, 0, 0);
		float full = v.getHealth();
		AtomicBoolean left = new AtomicBoolean();
		when(helper, () -> {
			if (!v.onGround()) {
				left.set(true);
			}
			return left.get() && v.onGround();
		}, 200, () -> "the villager never landed", () -> helper.runAfterDelay(2, () -> {
			helper.assertTrue(v.getY() < helper.absolutePos(over).getY() + 2, "landed on something at " + v.blockPosition().toShortString() + ", not the floor");
			landed.accept(Math.round(full - v.getHealth()));
		}));
	}

	private static void when(GameTestHelper helper, BooleanSupplier condition, int limit, java.util.function.Supplier<String> failure, Runnable then) {
		long deadline = helper.getTick() + limit;
		poll(helper, condition, deadline, failure, then);
	}

	private static void poll(GameTestHelper helper, BooleanSupplier condition, long deadline, java.util.function.Supplier<String> failure, Runnable then) {
		helper.runAfterDelay(1, () -> {
			if (condition.getAsBoolean()) {
				then.run();
			} else if (helper.getTick() > deadline) {
				helper.fail(failure.get());
			} else {
				poll(helper, condition, deadline, failure, then);
			}
		});
	}

	private static void place(GameTestHelper helper, String name, BlockPos at) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", name)).orElseThrow();
		BlockPos origin = helper.absolutePos(at);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(203L), 2);
	}

	/**
	 * Villagers (nitwits: nothing but the walk moves them) at {@code froms} walk to {@code goal} inside by the front door,
	 * shut as built, each working out a path to its {@code lookedAts} spot every tick and not following it (as vanilla's
	 * villagers check beds, bells and workstations). Passes once all are past {@code insideZ} with their feet at least
	 * {@code minFeet} over the floor.
	 */
	private static void walkIn(GameTestHelper helper, BlockPos[] froms, BlockPos[] lookedAts, BlockPos goal, int insideZ, double minFeet) {
		BlockPos target = helper.absolutePos(goal);
		List<Villager> villagers = new ArrayList<>();
		for (int i = 0; i < froms.length; i++) {
			Villager villager = helper.spawn(EntityType.VILLAGER, froms[i]);
			DoorwayHeadroomGameTests.nitwit(helper, villager);
			BlockPos spot = helper.absolutePos(lookedAts[i]);
			helper.onEachTick(() -> villager.getNavigation().createPath(spot, 1));
			villagers.add(villager);
		}
		double past = helper.absolutePos(new BlockPos(0, 0, insideZ)).getZ();
		double floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
		helper.onEachTick(() -> {
			for (Villager villager : villagers) {
				villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
				villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.6f, 0));
			}
		});
		helper.succeedWhen(() -> {
			for (int i = 0; i < villagers.size(); i++) {
				Villager villager = villagers.get(i);
				helper.assertTrue(villager.isAlive() && villager.getZ() > past && villager.getY() >= floor + minFeet,
					"villager " + i + " hasn't come in by the front door: at " + villager.position() + " (in at z > " + past
						+ ", feet over " + (floor + minFeet) + ")");
			}
		});
	}
}
