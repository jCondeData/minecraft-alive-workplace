package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MIN;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;

import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import java.util.Map;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, 2026-11-11, run 2212), from 45.3 ("the building comes back as it was: nothing dropped, nothing lost") with
 * Create 6: a Redstone Link, whose two frequency items live in its block entity's behaviour, not an inventory. A house
 * holds a transmitting link set to diamond + emerald, powered by a redstone block beside it; a receiving link with the
 * same frequency stands outside the house. After the Mover moves the house a quarter turned, the link still has its
 * frequency and still powers the receiver; while the house is taken down nothing transmits; and with its redstone
 * block broken at the new spot the receiver goes dark (no transmitter left behind at the old spot).
 */
public class QaCreateMoverLinks1111eCompatTests implements FabricGameTest {
	static final String AREA = QaCreateMoverContents1110eCompatTests.AREA;
	/** The receiver: outside the house (x 2-8, z 2-8) and its new spot (x 16-22, z 16-22), on the area's floor. */
	private static final BlockPos RECEIVER = new BlockPos(24, 2, 4);

	private static BlockState link(boolean receiver) {
		return with(with(block("create:redstone_link").defaultBlockState(), "facing", "up"), "receiver", Boolean.toString(receiver));
	}

	/**
	 * Puts a link at {@code abs}, its block entity reading its data before its first tick, as one placed from an item or
	 * loaded with its chunk does: Create adds a link's frequency behaviour only on that first read.
	 */
	private static void place(ServerLevel level, BlockPos abs, BlockState state) {
		set(level, abs, state);
		BlockEntity be = level.getBlockEntity(abs);
		if (be != null) {
			be.loadWithComponents(be.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
		}
	}

	private static LinkBehaviour behaviour(ServerLevel level, BlockPos abs) {
		BlockEntity be = level.getBlockEntity(abs);
		return be == null ? null : BlockEntityBehaviour.get(be, LinkBehaviour.TYPE);
	}

	private static void tune(GameTestHelper helper, BlockPos abs, net.minecraft.world.item.Item first, net.minecraft.world.item.Item last) {
		LinkBehaviour link = behaviour(helper.getLevel(), abs);
		helper.assertTrue(link != null, "setup: no Redstone Link at " + abs + ": " + helper.getLevel().getBlockState(abs));
		link.setFrequency(true, new ItemStack(first));
		link.setFrequency(false, new ItemStack(last));
	}

	private static String frequency(LinkBehaviour link) {
		if (link == null) {
			return "no link";
		}
		Couple<RedstoneLinkNetworkHandler.Frequency> key = link.getNetworkKey();
		return key.getFirst().getStack().getItem() + " + " + key.getSecond().getStack().getItem();
	}

	private static String describe(ServerLevel level, BlockPos abs) {
		LinkBehaviour link = behaviour(level, abs);
		return frequency(link) + (link == null ? "" : ", listening " + link.isListening() + ", sends " + link.getTransmittedStrength())
			+ (level.getBlockEntity(abs) instanceof com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity be ? ", signal " + be.getSignal() : "");
	}

	private static boolean lit(ServerLevel level, BlockPos abs) {
		BlockState state = level.getBlockState(abs);
		return state.hasProperty(RedstoneLinkBlock.POWERED) && state.getValue(RedstoneLinkBlock.POWERED);
	}

	/**
	 * The transmitter moves with its frequency, nothing transmits while it is packed, nothing is dropped; at the new spot,
	 * its redstone block broken turns the receiver off (no transmitter is left behind at the old spot), and put back
	 * (as a player does) turns it on again: the moved link still sends on its frequency.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1111_mover_redstone_link"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1111_mover_redstone_link")
	public void aMovedRedstoneLinkKeepsItsFrequencyAndStillSends(GameTestHelper helper) {
		moveTheLink(helper, "redstone_link", Items.DIAMOND, Items.EMERALD, false);
	}

	/**
	 * The house is moved with the link and its redstone block side by side: {@code litAtOnce} also asks that the receiver
	 * is lit right after the move, before anything next to the link changes (QaCreateMoverLinks1111eBugCompatTests). Each
	 * test has its own frequency ({@code first} + {@code last}): links on one frequency hear each other across the level.
	 */
	static void moveTheLink(GameTestHelper helper, String name, net.minecraft.world.item.Item first, net.minecraft.world.item.Item last, boolean litAtOnce) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos power = SPOT.east();
		helper.runAfterDelay(1, () -> {
			place(level, helper.absolutePos(RECEIVER), link(true));
			set(level, helper.absolutePos(SPOT.below()), Blocks.COBBLESTONE.defaultBlockState()); // the house's floor, which it stands on
			place(level, helper.absolutePos(SPOT), link(false));
			helper.runAfterDelay(5, () -> {
				tune(helper, helper.absolutePos(RECEIVER), first, last);
				tune(helper, helper.absolutePos(SPOT), first, last);
				level.setBlock(helper.absolutePos(power), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
				helper.runAfterDelay(10, () -> {
					helper.assertTrue(lit(level, helper.absolutePos(RECEIVER)), "setup: the receiver isn't lit by the transmitter in the house: "
						+ level.getBlockState(helper.absolutePos(RECEIVER)) + "; receiver " + describe(level, helper.absolutePos(RECEIVER))
						+ "; transmitter " + level.getBlockState(helper.absolutePos(SPOT)) + " " + describe(level, helper.absolutePos(SPOT)));
					Setup s = setupAround(helper, name);
					helper.runAfterDelay(2, () -> {
						MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
						helper.runAfterDelay(10, () -> {
							boolean litWhilePacked = lit(level, helper.absolutePos(RECEIVER));
							Map<String, Integer> afterDown = loose(helper);
							work(level, s, x -> false);
							MoveSite.Stage end = site.stage();
							int skipped = site.skipped();
							BlockPos newLink = s.before().at(SPOT.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
							BlockPos newPower = s.before().at(power.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
							helper.runAfterDelay(10, () -> {
								String freq = frequency(behaviour(level, newLink));
								boolean litAfter = lit(level, helper.absolutePos(RECEIVER));
								String linkAfter = level.getBlockState(newLink) + " " + describe(level, newLink) + "; receiver " + describe(level, helper.absolutePos(RECEIVER))
									+ "; its neighbours' signal " + level.getBestNeighborSignal(newLink);
								BlockState powerThere = level.getBlockState(newPower);
								Map<String, Integer> looseNow = loose(helper);
								level.setBlock(newPower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); // the player breaks the redstone block
								helper.runAfterDelay(10, () -> {
									boolean litBroken = lit(level, helper.absolutePos(RECEIVER));
									level.setBlock(newPower, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL); // and puts it back
									helper.runAfterDelay(10, () -> {
										boolean litBack = lit(level, helper.absolutePos(RECEIVER));
										try {
											helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
											helper.assertTrue(afterDown.isEmpty() && looseNow.isEmpty(), "loose items: after the take-down " + afterDown + ", at the end " + looseNow);
											helper.assertTrue(!litWhilePacked, "with the house taken down (the transmitter packed) the receiver is still lit");
											helper.assertTrue(level.getBlockState(newLink).is(block("create:redstone_link")) && powerThere.is(Blocks.REDSTONE_BLOCK),
												"at the new spot: " + level.getBlockState(newLink) + " and " + powerThere);
											helper.assertTrue(freq.equals(first + " + " + last),
												"the transmitter was set to " + first + " + " + last + "; after the move: " + freq);
											if (litAtOnce) {
												helper.assertTrue(litAfter, "right after the move the transmitter, still beside its redstone block, doesn't light the receiver: " + linkAfter);
											}
											helper.assertTrue(!litBroken, "its redstone block broken at the new spot, the receiver is still lit (something still transmits)");
											helper.assertTrue(litBack, "its redstone block put back at the new spot, the receiver stays dark: " + describe(level, newLink));
										} finally {
											level.setBlock(helper.absolutePos(RECEIVER), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
											tidy(helper, s);
										}
										helper.succeed();
									});
								});
							});
						});
					});
				});
			});
		});
	}
}
