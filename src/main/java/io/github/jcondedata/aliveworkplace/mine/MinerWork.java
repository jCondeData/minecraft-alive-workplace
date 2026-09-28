package io.github.jcondedata.aliveworkplace.mine;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The miner's work shift: dig the quarry out layer by layer with a pickaxe from the supply chests,
 * drop off what comes out, light the pit with torches (if there are any in the chests) and keep
 * away from lava and water. Restartable at any tick; progress lives in {@link QuarrySite}.
 */
public class MinerWork extends Behavior<Villager> {
	static final double REACH = 4.5;
	private static final double CONTAINER_REACH = 3.0;
	private static final float SPEED = 0.6f;
	private static final int SKIP_BUDGET = 128;
	private static final int TORCH_EVERY = 6;
	private static final boolean DEBUG = Boolean.getBoolean("aliveworkplace.debug");

	private enum Errand { NONE, DEPOSIT, FETCH_PICKAXE }

	@Nullable
	private BlockPos digging;
	private int digProgress;
	private int digTotal;
	private int sinceTorch;
	private final io.github.jcondedata.aliveworkplace.work.Walker walker = new io.github.jcondedata.aliveworkplace.work.Walker(SPEED);

	public MinerWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Miners.activeSite(level, villager) != null;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !villager.isSleeping() && Miners.activeSite(level, villager) != null;
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		digging = null;
		villager.setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		if (digging != null) {
			level.destroyBlockProgress(villager.getId(), digging, -1);
			digging = null;
		}
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		QuarrySite site = Miners.activeSite(level, villager);
		if (site == null) {
			return;
		}
		BlockPos bench = site.bench();
		if (bench == null) {
			return;
		}
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);

		// 1. Next block that needs digging.
		BlockPos target = null;
		ItemStack pick = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		for (int budget = SKIP_BUDGET; budget > 0 && !site.isDone(); budget--) {
			BlockPos pos = site.current();
			Verdict v = verdict(level, site, pos, pick);
			if (v == Verdict.DIG) {
				target = pos;
				break;
			}
			site.advance(false, v == Verdict.LEAVE);
		}
		if (site.isDone()) {
			if (walkTo(level, villager, containerNear(level, bench), CONTAINER_REACH)) {
				Miners.finish(level, villager, site);
			}
			return;
		}
		if (target == null) {
			return;
		}

		// 2. A pickaxe in hand.
		if (!isPickaxe(pick)) {
			fetchPickaxe(level, villager, site, bench, bag);
			return;
		}

		// 3. Room in the bag.
		if (bag.freeSlots() < 2) {
			site.setStatus(QuarrySite.Status.DEPOSITING);
			if (walkTo(level, villager, containerNear(level, bench), CONTAINER_REACH)) {
				deposit(level, villager, bench, bag);
			}
			return;
		}

		// 4. Get within reach, then dig.
		site.setStatus(QuarrySite.Status.WORKING);
		if (DEBUG && gameTime % 40 == 0) {
			io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[miner {}] at {} target={} pick={}",
				villager.getId(), villager.position(), target.toShortString(), pick);
		}
		if (!approach(level, villager, site, target)) {
			return;
		}
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
		dig(level, villager, site, bag, pick, target);
	}

	// --- what to dig ---------------------------------------------------------------------------

	private enum Verdict { DIG, EMPTY, LEAVE }

	/**
	 * DIG the block, move on because the spot is EMPTY (air, fluid), or LEAVE it standing: unbreakable,
	 * a container or workstation, too hard for the pickaxe, or next to lava or water.
	 */
	private static Verdict verdict(ServerLevel level, QuarrySite site, BlockPos pos, ItemStack pick) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) {
			return Verdict.EMPTY;
		}
		if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
			return Verdict.DIG; // our own light: taken back as we go down
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0 || MaterialRules.isProtected(state, hardness) || state.is(ModBlocks.BUILDERS_BENCH) || state.is(ModBlocks.MINERS_BENCH)
			|| pos.equals(site.bench())) {
			return Verdict.LEAVE;
		}
		if (isPickaxe(pick) && state.requiresCorrectToolForDrops() && !pick.isCorrectToolForDrops(state)) {
			return Verdict.LEAVE; // e.g. obsidian with an iron pickaxe
		}
		for (Direction d : Direction.values()) {
			if (!level.getFluidState(pos.relative(d)).isEmpty()) {
				return Verdict.LEAVE; // keep lava and water out of the pit
			}
		}
		return Verdict.DIG;
	}

	static boolean isPickaxe(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ItemTags.PICKAXES);
	}

	// --- digging -------------------------------------------------------------------------------

	private void dig(ServerLevel level, Villager villager, QuarrySite site, BuilderBag bag, ItemStack pick, BlockPos target) {
		BlockState state = level.getBlockState(target);
		if (!target.equals(digging)) {
			digging = target;
			digProgress = 0;
			float hardness = state.getDestroySpeed(level, target);
			float speed = Math.max(1f, pick.getDestroySpeed(state));
			int ticks = (int) Math.ceil(hardness * 30f / speed);
			digTotal = Math.max(2, BuilderLevels.delay(Math.max(2, ticks), villager));
		}
		digProgress++;
		if (digProgress % 4 == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			level.destroyBlockProgress(villager.getId(), target, Math.min(9, digProgress * 10 / digTotal));
		}
		if (digProgress < digTotal) {
			return;
		}
		level.destroyBlockProgress(villager.getId(), target, -1);
		digging = null;

		takeTorchesAround(level, bag, target);
		BlockEntity blockEntity = level.getBlockEntity(target);
		List<ItemStack> drops = Block.getDrops(state, level, target, blockEntity, villager, pick);
		level.destroyBlock(target, false, villager);
		for (ItemStack drop : drops) {
			ItemStack rest = bag.add(drop);
			if (!rest.isEmpty()) {
				Miners.store(level, List.of(), villager.blockPosition(), rest);
			}
		}
		pick.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
		boolean torch = state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH);
		site.advance(!torch, false);
		if (!torch && site.mined() % 10 == 0) {
			BuilderLevels.addXp(level, villager, 1, site.owner());
		}
		if (++sinceTorch >= TORCH_EVERY) {
			sinceTorch = 0;
			lightUp(level, villager, bag);
		}
	}

	/** Torches standing on or hanging off the block we are about to dig come off first, back into the bag. */
	private static void takeTorchesAround(ServerLevel level, BuilderBag bag, BlockPos pos) {
		BlockPos above = pos.above();
		if (level.getBlockState(above).is(Blocks.TORCH)) {
			level.removeBlock(above, false);
			bag.addAll(Items.TORCH, 1);
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(d);
			BlockState s = level.getBlockState(side);
			if (s.is(Blocks.WALL_TORCH) && s.getValue(WallTorchBlock.FACING) == d) {
				level.removeBlock(side, false);
				bag.addAll(Items.TORCH, 1);
			}
		}
	}

	/** Dark down here? Put a torch down where the miner stands (if it brought any). */
	private static void lightUp(ServerLevel level, Villager villager, BuilderBag bag) {
		BlockPos feet = villager.blockPosition();
		if (!bag.has(Items.TORCH, 1) || level.getBrightness(LightLayer.BLOCK, feet) >= 8 || level.getBrightness(LightLayer.SKY, feet) >= 8) {
			return;
		}
		BlockState torch = Blocks.TORCH.defaultBlockState();
		if (level.getBlockState(feet).isAir() && torch.canSurvive(level, feet)) {
			level.setBlockAndUpdate(feet, torch);
			bag.remove(Items.TORCH, 1);
		}
	}

	// --- errands -------------------------------------------------------------------------------

	private void fetchPickaxe(ServerLevel level, Villager villager, QuarrySite site, BlockPos bench, BuilderBag bag) {
		List<BlockPos> supplies = SupplyContainers.find(level, bench, site.box());
		BlockPos chest = SupplyContainers.firstMatching(level, supplies, MinerWork::isPickaxe);
		if (chest == null) {
			site.setStatus(QuarrySite.Status.NEEDS_PICKAXE);
			walkTo(level, villager, bench, 3);
			Miners.notifyNeedsPickaxe(level, villager, site);
			return;
		}
		if (!walkTo(level, villager, chest, CONTAINER_REACH)) {
			return;
		}
		ItemStack pick = SupplyContainers.takeOne(level, supplies, MinerWork::isPickaxe);
		if (!pick.isEmpty()) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, pick);
			level.playSound(null, chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		}
		topUpTorches(level, supplies, bag);
	}

	private static void deposit(ServerLevel level, Villager villager, BlockPos bench, BuilderBag bag) {
		List<BlockPos> supplies = SupplyContainers.find(level, bench, null);
		for (ItemStack stack : bag.takeAllExcept(java.util.Set.of(Items.TORCH))) {
			Miners.store(level, supplies, bench, stack);
		}
		topUpTorches(level, supplies, bag);
		level.playSound(null, villager.blockPosition(), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.1f);
	}

	private static void topUpTorches(ServerLevel level, List<BlockPos> supplies, BuilderBag bag) {
		int have = bag.count(Items.TORCH);
		if (have < 8) {
			int got = SupplyContainers.extract(level, supplies, Items.TORCH, 16 - have);
			if (got > 0) {
				bag.addAll(Items.TORCH, got);
			}
		}
	}

	private static BlockPos containerNear(ServerLevel level, BlockPos bench) {
		List<BlockPos> supplies = SupplyContainers.find(level, bench, null);
		return supplies.isEmpty() ? bench : supplies.get(0);
	}

	// --- moving --------------------------------------------------------------------------------

	private boolean walkTo(ServerLevel level, Villager villager, BlockPos pos, double reach) {
		return walker.walkTo(level, villager, pos, reach);
	}

	/** Walks to a spot the target can be dug from; hops down into the pit when there is no way to walk. */
	private boolean approach(ServerLevel level, Villager villager, QuarrySite site, BlockPos target) {
		if (walker.reach(level, villager, target, REACH)) {
			return true;
		}
		if (walker.noSpot()) {
			site.advance(false, true); // nowhere to dig it from: leave it
		}
		return false;
	}
}
