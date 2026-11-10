package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverArm1110eCompatTests.point;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverArm1110eCompatTests.targets;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MAX_STEPS;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MIN;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.fill;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane E, 2026-11-10, eighth run): B1137's fix from its spec, past the quarter turn its own test covers. B1137: when
 * the Mover (45.3) moves a building turned, a Create Mechanical Arm inside it still takes from and puts on the same
 * depots, as Create's own contraptions do; the fix turns a block entity's data with the building (the Mover's put-up, a
 * canceled move putting back data packed at the new spot, and a builder building a blueprint turned or mirrored). Each
 * test checks where the arm aims and that nothing dropped; the canceled moves and the builds also check what a player
 * sees, a diamond put on the take-depot ending on the put-depot (the arm, turned by a cogwheel on a Creative Motor, still
 * works). A Create Display Link (which keeps its target relative to itself too) is checked the same way. At the new spot
 * of a finished move the arm aims right but stands still, its cogwheel cut off from the motor under it:
 * QaCreateMoverTurns1110hBugCompatTests (fails on main).
 */
public class QaCreateMoverTurns1110hCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	/**
	 * Inside the house (floor y = 2): the arm (a cogwheel at its base, no shaft) turned by a cogwheel beside it on a Creative
	 * Motor facing up, a depot west of it and one north, on the floor.
	 */
	static final BlockPos ARM = new BlockPos(5, 4, 5);
	static final BlockPos COG = ARM.east();
	static final BlockPos MOTOR = COG.below();
	static final BlockPos IN = new BlockPos(3, 3, 5);
	static final BlockPos OUT = new BlockPos(5, 3, 3);
	/** Ticks the arm gets to carry one item across (a motor at its default speed takes a few seconds). */
	static final int CARRY = 600;

	static BlockPos moved(Setup s, BlockPos rel, Rotation rotation) {
		return s.before().at(rel.subtract(MIN), s.to(), rotation);
	}

	/** Runs {@code then} once {@code ok} holds or {@code left} ticks have gone by. */
	static void waitUntil(GameTestHelper helper, int left, BooleanSupplier ok, Runnable then) {
		if (left <= 0 || ok.getAsBoolean()) {
			then.run();
			return;
		}
		helper.runAfterDelay(5, () -> waitUntil(helper, left - 5, ok, then));
	}

	/** The arm's, cogwheel's and motor's saved data, for a failure's message. */
	static String rig(ServerLevel level, BlockPos arm, BlockPos cog, BlockPos motor) {
		StringBuilder out = new StringBuilder();
		for (BlockPos p : List.of(arm, cog, motor)) {
			BlockEntity be = level.getBlockEntity(p);
			out.append(level.getBlockState(p)).append(" at ").append(p.toShortString()).append(": ")
				.append(be == null ? "no block entity" : be.saveWithoutMetadata(level.registryAccess())).append("; ");
		}
		return out.toString();
	}

	/** The motor, the arm and its two depots placed; the arm aimed (take west, put north) a few ticks later. */
	static void placeArm(GameTestHelper helper, Runnable then) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		set(level, helper.absolutePos(IN), block("create:depot").defaultBlockState());
		set(level, helper.absolutePos(OUT), block("create:depot").defaultBlockState());
		set(level, helper.absolutePos(MOTOR), with(block("create:creative_motor").defaultBlockState(), "facing", "up"));
		set(level, helper.absolutePos(COG), with(block("create:cogwheel").defaultBlockState(), "axis", "y"));
		set(level, helper.absolutePos(ARM), block("create:mechanical_arm").defaultBlockState());
		helper.runAfterDelay(2, () -> {
			aim(helper, helper.absolutePos(ARM), helper.absolutePos(IN), helper.absolutePos(OUT));
			helper.runAfterDelay(5, then);
		});
	}

	/** Aims the arm at {@code arm} to take from {@code in} and put on {@code out}, as a player's clicks with it leave it. */
	static void aim(GameTestHelper helper, BlockPos arm, BlockPos in, BlockPos out) {
		ServerLevel level = helper.getLevel();
		BlockEntity be = level.getBlockEntity(arm);
		helper.assertTrue(be != null, "the arm has no block entity: " + level.getBlockState(arm));
		CompoundTag data = be.saveWithoutMetadata(level.registryAccess());
		ListTag points = new ListTag();
		points.add(point(in.subtract(arm), "TAKE"));
		points.add(point(out.subtract(arm), "DEPOSIT"));
		data.put("InteractionPoints", points);
		be.loadWithComponents(data, level.registryAccess());
		be.setChanged();
	}

	/**
	 * Puts a diamond on the depot at {@code in}; once the arm has carried it to {@code out} (or {@link #CARRY} ticks have gone),
	 * runs {@code then} with whether it arrived.
	 */
	static void carry(GameTestHelper helper, BlockPos in, BlockPos out, java.util.function.Consumer<String> then) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(fill(level, in, Items.DIAMOND, 1) != null, "couldn't put a diamond on the take-depot at " + in.toShortString()
			+ ": " + level.getBlockState(in));
		waitUntil(helper, CARRY, () -> held(level, out, Items.DIAMOND) == 1, () -> {
			long there = held(level, out, Items.DIAMOND);
			long left = held(level, in, Items.DIAMOND);
			then.accept(there == 1 && left == 0 ? null : "the diamond put on the take-depot at " + in.toShortString() + " is "
				+ (there == 1 ? "on the put-depot but also still on the take-depot" : left == 1 ? "still there" : "neither there nor on the put-depot")
				+ " after " + CARRY + " ticks (put-depot at " + out.toShortString() + " holds " + there + ")");
		});
	}

	/** The arm works before the move (the rig is right), and the diamond is taken off again so nothing rides along. */
	static void worksBefore(GameTestHelper helper, Runnable then) {
		ServerLevel level = helper.getLevel();
		carry(helper, helper.absolutePos(IN), helper.absolutePos(OUT), failed -> {
			helper.assertTrue(failed == null, "before the move: " + failed + "; the arm aims at " + targets(level, helper.absolutePos(ARM)) + "; arm data "
				+ level.getBlockEntity(helper.absolutePos(ARM)).saveWithoutMetadata(level.registryAccess()) + "; motor data "
				+ level.getBlockEntity(helper.absolutePos(MOTOR)).saveWithoutMetadata(level.registryAccess()) + "; in data " + level.getBlockEntity(helper.absolutePos(IN)).saveWithoutMetadata(level.registryAccess()));
			Clearable.tryClear(level.getBlockEntity(helper.absolutePos(OUT)));
			helper.assertTrue(held(level, helper.absolutePos(OUT), Items.DIAMOND) == 0, "couldn't take the diamond back off the put-depot");
			helper.runAfterDelay(20, then); // the arm back at rest
		});
	}

	/** The house with the arm moved turned {@code rotation}: the arm aims at its depots at the new spot, nothing dropped. */
	static void movedTurned(GameTestHelper helper, String name, Rotation rotation) {
		movedTurned(helper, name, rotation, false, 0);
	}

	/**
	 * The house with the arm moved turned {@code rotation}: the arm aims at its depots at the new spot, nothing dropped, and
	 * with {@code carryAfter} it carries a diamond across them. {@code perTick}: the Mover's steps a tick (0: the whole move
	 * in one tick, as the other Mover tests do).
	 */
	static void movedTurned(GameTestHelper helper, String name, Rotation rotation, boolean carryAfter, int perTick) {
		ServerLevel level = helper.getLevel();
		placeArm(helper, () -> worksBefore(helper, () -> {
			Setup s = setupAround(helper, name);
			PendingMoves.get(level).put(new PendingMoves.Move(s.hall(), s.from(), s.id(), s.to(), rotation, null));
			helper.runAfterDelay(2, () -> workOver(helper, s, perTick, site -> {
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				Rotation used = site.rotation();
				BlockPos arm = moved(s, ARM, rotation);
				BlockPos in = moved(s, IN, rotation);
				BlockPos out = moved(s, OUT, rotation);
				helper.runAfterDelay(10, () -> {
					List<BlockPos> after = targets(level, arm);
					Map<String, Integer> looseThen = loose(helper);
					String states = level.getBlockState(arm) + " at " + arm.toShortString() + ", " + level.getBlockState(in) + " at "
						+ in.toShortString() + ", " + level.getBlockState(out) + " at " + out.toShortString();
					try {
						helper.assertTrue(used == rotation, "the move went " + used + ", not " + rotation);
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						helper.assertTrue(after.equals(List.of(in, out)), "moved " + rotation + ", the arm aims at " + after + ", not its depots at "
							+ in.toShortString() + " (take) and " + out.toShortString() + " (put): " + states);
						helper.assertTrue(looseThen.isEmpty(), "moved " + rotation + ", items lie loose: " + looseThen);
					} catch (RuntimeException e) {
						tidy(helper, s);
						throw e;
					}
					if (!carryAfter) {
						tidy(helper, s);
						helper.succeed();
						return;
					}
					carry(helper, in, out, failed -> {
						Map<String, Integer> looseNow = loose(helper);
						String rigNow = rig(level, arm, moved(s, COG, rotation), moved(s, MOTOR, rotation));
						tidy(helper, s);
						helper.assertTrue(failed == null, "moved " + rotation + (perTick > 0 ? " (" + perTick + " Mover steps a tick)" : "") + ": " + failed
							+ "; the arm aims at " + targets(level, arm) + "; " + rigNow);
						helper.assertTrue(looseNow.isEmpty(), "moved " + rotation + ", items lie loose: " + looseNow);
						helper.succeed();
					});
				});
			}));
		}));
	}

	/** Lets the Mover work to the end, {@code perTick} steps a tick (0: all in this tick); then {@code then} with the site. */
	static void workOver(GameTestHelper helper, Setup s, int perTick, java.util.function.Consumer<MoveSite> then) {
		ServerLevel level = helper.getLevel();
		if (perTick <= 0) {
			then.accept(work(level, s, x -> false));
			return;
		}
		MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
		stepOver(helper, s, site, perTick, MAX_STEPS, then);
	}

	private static void stepOver(GameTestHelper helper, Setup s, MoveSite site, int perTick, int left, java.util.function.Consumer<MoveSite> then) {
		for (int i = 0; i < perTick && left > 0 && site.stage() != MoveSite.Stage.DONE; i++, left--) {
			io.github.jcondedata.aliveworkplace.move.MoverWork.work(helper.getLevel(), s.mover(), site);
		}
		if (left <= 0 || site.stage() == MoveSite.Stage.DONE) {
			then.accept(site);
			return;
		}
		int rest = left;
		helper.runAfterDelay(1, () -> stepOver(helper, s, site, perTick, rest, then));
	}

	/** Moved a half turn (180): the arm still aims at the same depots. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1110h_mover_arm_half"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1110h_mover_arm_half")
	public void anArmMovedAHalfTurnStillAimsAtItsDepots(GameTestHelper helper) {
		movedTurned(helper, "arm_half", Rotation.CLOCKWISE_180);
	}

	/** Moved three quarters (a quarter counterclockwise): the arm still aims at the same depots. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1110h_mover_arm_ccw"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1110h_mover_arm_ccw")
	public void anArmMovedAQuarterCounterclockwiseStillAimsAtItsDepots(GameTestHelper helper) {
		movedTurned(helper, "arm_ccw", Rotation.COUNTERCLOCKWISE_90);
	}

	/** Moved without a turn: its targets aren't turned (the data goes in as it was). */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1110h_mover_arm_none"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1110h_mover_arm_none")
	public void anArmMovedWithoutATurnKeepsItsTargetsAsTheyWere(GameTestHelper helper) {
		movedTurned(helper, "arm_none", Rotation.NONE);
	}

	/**
	 * A quarter-turned move canceled once the arm and its depots stand at the new spot (so the arm's data is packed there,
	 * turned, and must be turned back): back at the old spot it aims at its own depots and carries between them.
	 * {@code reload}: the move sites saved and read back while the arm is packed at the new spot, before it's put back.
	 */
	static void canceledAfterPutUp(GameTestHelper helper, String name, boolean reload) {
		ServerLevel level = helper.getLevel();
		placeArm(helper, () -> worksBefore(helper, () -> {
			Setup s = setupAround(helper, name);
			Rotation turn = Rotation.CLOCKWISE_90;
			helper.runAfterDelay(2, () -> {
				BlockPos armNew = moved(s, ARM, turn);
				BlockPos inNew = moved(s, IN, turn);
				BlockPos outNew = moved(s, OUT, turn);
				var armBlock = block("create:mechanical_arm");
				var depot = block("create:depot");
				MoveSite site = work(level, s, x -> level.getBlockState(armNew).is(armBlock) && level.getBlockState(inNew).is(depot)
					&& level.getBlockState(outNew).is(depot) && level.getBlockState(moved(s, MOTOR, turn)).is(block("create:creative_motor"))
					&& level.getBlockState(moved(s, COG, turn)).is(block("create:cogwheel")));
				MoveSite.Stage up = site.stage();
				helper.runAfterDelay(10, () -> { // the arm ticks at the new spot: it reads its targets there
					List<BlockPos> atNew = targets(level, armNew);
					MoveSites.get(level).cancel(level, s.from());
					MoveSite running = site;
					boolean[] packedSeen = {false};
					String reloaded = "no reload";
					if (reload) {
						// step until the arm has come down at the new spot and isn't back up at the old one yet
						BlockPos armOld = helper.absolutePos(ARM);
						for (int i = 0; i < MAX_STEPS && running.stage() != MoveSite.Stage.DONE; i++) {
							if (!level.getBlockState(armNew).is(armBlock) && !level.getBlockState(armOld).is(armBlock)) {
								packedSeen[0] = true;
								break;
							}
							MoveSites.get(level).step(level, running);
						}
						MoveSite.Stage mid = running.stage();
						CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
						level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
						running = MoveSites.get(level).forBuilding(s.from()).orElseThrow();
						reloaded = "reloaded in " + mid + " with the arm packed " + packedSeen[0] + (running != site ? "" : " (same site object!)");
					}
					for (int i = 0; i < MAX_STEPS && running.stage() != MoveSite.Stage.DONE; i++) {
						MoveSites.get(level).step(level, running);
					}
					MoveSite.Stage end = running.stage();
					String how = reloaded;
					helper.runAfterDelay(10, () -> {
						BlockPos arm = helper.absolutePos(ARM);
						BlockPos in = helper.absolutePos(IN);
						BlockPos out = helper.absolutePos(OUT);
						List<BlockPos> back = targets(level, arm);
						try {
							helper.assertTrue(up.rebuilding() && atNew.equals(List.of(inNew, outNew)), "the arm wasn't up and aimed at the new spot before "
								+ "the cancel: stage " + up + ", aims at " + atNew + ", depots there " + inNew.toShortString() + ", " + outNew.toShortString());
							helper.assertTrue(!reload || packedSeen[0], "the reload didn't happen with the arm packed: " + how);
							helper.assertTrue(end == MoveSite.Stage.DONE, "the canceled move ended " + end + " (" + how + ")");
							helper.assertTrue(level.getBlockState(arm).is(armBlock), "no arm back at the old spot " + arm.toShortString() + ": "
								+ level.getBlockState(arm));
							helper.assertTrue(back.equals(List.of(in, out)), "canceled after it was put up at the new spot (" + how + "), the arm back "
								+ "at the old spot aims at " + back + ", not its depots at " + in.toShortString() + " (take) and " + out.toShortString() + " (put)");
						} catch (RuntimeException e) {
							tidy(helper, s);
							throw e;
						}
						carry(helper, in, out, failed -> {
							Map<String, Integer> looseNow = loose(helper);
							tidy(helper, s);
							helper.assertTrue(failed == null, "canceled and put back (" + how + "): " + failed + "; the arm aims at " + targets(level, arm));
							helper.assertTrue(looseNow.isEmpty(), "canceled and put back (" + how + "), items lie loose: " + looseNow);
							helper.succeed();
						});
					});
				});
			});
		}));
	}

	/** Canceled with the arm already up at the new spot: back at the old spot it aims at its own depots. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1110h_mover_arm_cancel"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1110h_mover_arm_cancel")
	public void anArmPutUpThenCanceledAimsAtItsOwnDepotsAgain(GameTestHelper helper) {
		canceledAfterPutUp(helper, "arm_cancel", false);
	}

	/** The same, saved and read back while the arm is packed at the new spot: no double and no missing turn. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1110h_mover_arm_cancel_reload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1110h_mover_arm_cancel_reload")
	public void anArmPutUpThenCanceledAndReloadedAimsAtItsOwnDepotsAgain(GameTestHelper helper) {
		canceledAfterPutUp(helper, "arm_cancel_reload", true);
	}

	/** A Display Link linked to a Nixie Tube, moved a quarter turned: it still targets the tube. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110h_mover_display_link"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110h_mover_display_link")
	public void aMovedDisplayLinkStillTargetsItsTube(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos link = new BlockPos(5, 3, 5);
		BlockPos tube = new BlockPos(3, 3, 4);
		set(level, helper.absolutePos(link), with(block("create:display_link").defaultBlockState(), "facing", "up"));
		set(level, helper.absolutePos(tube), block("create:nixie_tube").defaultBlockState());
		helper.runAfterDelay(2, () -> {
			BlockEntity be = level.getBlockEntity(helper.absolutePos(link));
			helper.assertTrue(be instanceof DisplayLinkBlockEntity, "the link's block entity is " + be);
			((DisplayLinkBlockEntity) be).target(helper.absolutePos(tube));
			be.setChanged();
			helper.assertTrue(((DisplayLinkBlockEntity) be).getTargetPosition().equals(helper.absolutePos(tube)), "the link doesn't target the tube");
			Setup s = setupAround(helper, "display_link");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				BlockPos linkNow = moved(s, link, Rotation.CLOCKWISE_90);
				BlockPos tubeNow = moved(s, tube, Rotation.CLOCKWISE_90);
				helper.runAfterDelay(10, () -> {
					BlockEntity after = level.getBlockEntity(linkNow);
					Map<String, Integer> looseNow = loose(helper);
					String states = level.getBlockState(linkNow) + " at " + linkNow.toShortString() + ", " + level.getBlockState(tubeNow) + " at "
						+ tubeNow.toShortString();
					BlockPos target = after instanceof DisplayLinkBlockEntity d ? d.getTargetPosition() : null;
					tidy(helper, s);
					helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
					helper.assertTrue(after instanceof DisplayLinkBlockEntity, "no display link at the new spot: " + states);
					helper.assertTrue(tubeNow.equals(target), "moved a quarter turned, the display link targets " + (target == null ? null
						: target.toShortString()) + ", not its tube at " + tubeNow.toShortString() + ": " + states);
					helper.assertTrue(looseNow.isEmpty(), "items lie loose: " + looseNow);
					helper.succeed();
				});
			});
		});
	}

	// ---- a builder building a blueprint with an arm, turned and mirrored

	/** The rig drawn into a template at SRC: depot (0,0,2) take, depot (2,0,0) put, arm (2,1,2), cogwheel (3,1,2) on a motor (3,0,2). */
	static final BlockPos SRC = new BlockPos(20, 2, 20);

	/**
	 * Builds a blueprint of the arm rig (scanned with the arm aimed) placed {@code rotation} and {@code mirror}; when it's
	 * done the arm aims at the two depots as they stand in the built rig, and carries a diamond from one to the other.
	 */
	static void builtArm(GameTestHelper helper, String name, Rotation rotation, Mirror mirror) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos in = SRC.offset(0, 0, 2);
		BlockPos out = SRC.offset(2, 0, 0);
		BlockPos arm = SRC.offset(2, 1, 2);
		BlockPos cog = arm.east();
		BlockPos motor = cog.below();
		set(level, helper.absolutePos(in), block("create:depot").defaultBlockState());
		set(level, helper.absolutePos(out), block("create:depot").defaultBlockState());
		set(level, helper.absolutePos(motor), with(block("create:creative_motor").defaultBlockState(), "facing", "up"));
		set(level, helper.absolutePos(cog), with(block("create:cogwheel").defaultBlockState(), "axis", "y"));
		set(level, helper.absolutePos(arm), block("create:mechanical_arm").defaultBlockState());
		helper.runAfterDelay(2, () -> {
			aim(helper, helper.absolutePos(arm), helper.absolutePos(in), helper.absolutePos(out));
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(targets(level, helper.absolutePos(arm)).equals(List.of(helper.absolutePos(in), helper.absolutePos(out))),
					"the scanned arm isn't aimed: " + targets(level, helper.absolutePos(arm)));
				ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", "qa_turned_arm_" + name + "_"
					+ Long.toHexString(level.getGameTime()));
				level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(4, 2, 3), false, Blocks.STRUCTURE_VOID);
				for (int dx = 0; dx < 4; dx++) {
					for (int dy = 0; dy < 2; dy++) {
						for (int dz = 0; dz < 3; dz++) {
							set(level, helper.absolutePos(SRC.offset(dx, dy, dz)), Blocks.AIR.defaultBlockState());
						}
					}
				}
				Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
				// the blueprint's own spots for the rig (whatever offset its import gives them)
				List<Blueprint.Entry> depots = blueprint.blocks().stream().filter(e -> e.state().is(block("create:depot")))
					.sorted(java.util.Comparator.comparingInt(e -> e.pos().getX())).toList();
				Blueprint.Entry armEntry = blueprint.blocks().stream().filter(e -> e.state().is(block("create:mechanical_arm"))).findFirst().orElseThrow();
				helper.assertTrue(depots.size() == 2, "the blueprint has " + depots.size() + " depots");

				boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
				int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
				boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
				Runnable restore = () -> {
					level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
					level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
					level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
				};
				level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
				level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
				level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
				helper.setDayTime(2000);
				BlockPos bench = new BlockPos(2, 2, 2);
				helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
				Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
				Builders.employ(level, builder, helper.absolutePos(bench));
				BlockPos corner = helper.absolutePos(new BlockPos(10, 2, 10));
				BoundingBox box = BuildPlan.create(blueprint, new BlueprintData.Placement(level.dimension().location(), corner, rotation, mirror)).bounds();
				BlockPos origin = corner.offset(corner.getX() - box.minX(), 0, corner.getZ() - box.minZ());
				BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror);
				BlockPos armAt = origin.offset(StructureTemplate.transform(armEntry.pos(), mirror, rotation, BlockPos.ZERO));
				BlockPos inAt = origin.offset(StructureTemplate.transform(depots.get(0).pos(), mirror, rotation, BlockPos.ZERO));
				BlockPos outAt = origin.offset(StructureTemplate.transform(depots.get(1).pos(), mirror, rotation, BlockPos.ZERO));
				BuildSite site = Builders.start(level, builder, null, id, placement);
				helper.assertTrue(site != null, "the builder didn't take the blueprint");
				waitUntil(helper, 3000, () -> BuildSiteManager.get(level).get(site.id()) == null, () -> {
					String states = level.getBlockState(armAt) + " at " + armAt.toShortString() + ", " + level.getBlockState(inAt) + " at "
						+ inAt.toShortString() + ", " + level.getBlockState(outAt) + " at " + outAt.toShortString();
					try {
						helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status());
						helper.assertTrue(level.getBlockState(armAt).is(block("create:mechanical_arm")) && level.getBlockState(inAt).is(block("create:depot"))
							&& level.getBlockState(outAt).is(block("create:depot")), "the rig isn't built where expected: " + states);
					} catch (RuntimeException e) {
						restore.run();
						throw e;
					}
					helper.runAfterDelay(10, () -> {
						List<BlockPos> aims = targets(level, armAt);
						try {
							helper.assertTrue(aims.equals(List.of(inAt, outAt)), "built " + rotation + (mirror == Mirror.NONE ? "" : " mirrored " + mirror)
								+ ", the arm aims at " + aims + ", not its depots at " + inAt.toShortString() + " (take) and " + outAt.toShortString()
								+ " (put): " + states);
						} catch (RuntimeException e) {
							restore.run();
							throw e;
						}
						carry(helper, inAt, outAt, failed -> {
							restore.run();
							Map<String, Integer> looseNow = loose(helper);
							looseNow.remove("aliveworkplace:blueprint"); // the builder's own paper, done with
							PartnerShowsCompatTests.clearLeftovers(helper);
							helper.assertTrue(failed == null, "built " + rotation + " " + mirror + ": " + failed + "; the arm aims at " + targets(level, armAt));
							helper.assertTrue(looseNow.isEmpty(), "items lie loose: " + looseNow);
							helper.succeed();
						});
					});
				});
			});
		});
	}

	/** A blueprint with an arm and its depots built a quarter turned: the arm aims at the depots as built. */
	//$ gametest_ticks_batch AREA '6000' '"qa_e1110h_built_arm_turned"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa_e1110h_built_arm_turned")
	public void anArmBuiltAQuarterTurnedAimsAtItsDepots(GameTestHelper helper) {
		builtArm(helper, "turned", Rotation.CLOCKWISE_90, Mirror.NONE);
	}

	/** The same blueprint built mirrored front to back and turned a quarter: the arm aims at the depots as built. */
	//$ gametest_ticks_batch AREA '6000' '"qa_e1110h_built_arm_mirrored"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa_e1110h_built_arm_mirrored")
	public void anArmBuiltMirroredAndTurnedAimsAtItsDepots(GameTestHelper helper) {
		builtArm(helper, "mirrored", Rotation.CLOCKWISE_90, Mirror.FRONT_BACK);
	}

	/** Mirrored front to back without a turn: the arm aims at the depots as built. */
	//$ gametest_ticks_batch AREA '6000' '"qa_e1110h_built_arm_mirror_only"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa_e1110h_built_arm_mirror_only")
	public void anArmBuiltMirroredAimsAtItsDepots(GameTestHelper helper) {
		builtArm(helper, "mirror_only", Rotation.NONE, Mirror.FRONT_BACK);
	}
}
