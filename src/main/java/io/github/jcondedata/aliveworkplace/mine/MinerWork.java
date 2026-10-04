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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The miner's work shift: dig the quarry out layer by layer with a pickaxe from the supply chests,
 * drop off what comes out, light the pit with torches (if there are any in the chests) and keep
 * away from lava and water. Restartable at any tick; progress lives in {@link QuarrySite}.
 */
public class MinerWork extends Behavior<Villager> {
	/** The common {@code c:ores} tag (shared by mods; Fabric API fills it in). */
	private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> ORES = net.minecraft.tags.TagKey.create(
		net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "ores"));
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

	/** Ticks digging a block that takes {@code ticks} with the pickaxe in hand takes this miner: the level's share at their pace (ROADMAP 30.2). */
	public static int digTicks(Villager villager, int ticks) {
		return Math.max(2, BuilderLevels.delay(Math.max(2, ticks), villager));
	}

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
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);

		// 1. Next block that needs digging (or a step of the stairs that needs filling in, or a ladder in the shaft).
		BlockPos target = null;
		boolean fill = false;
		boolean ladder = false;
		ItemStack pick = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		for (int budget = SKIP_BUDGET; budget > 0 && !site.isDone(); budget--) {
			BlockPos pos = site.current();
			if (site.phase() != QuarrySite.Phase.PIT) {
				ShaftJob job = shaftJob(level, site, pos, pick, bag);
				if (job.kind() == Verdict.EMPTY) {
					site.advance(false, false);
					continue;
				}
				if (job.kind() == Verdict.LEAVE) {
					if (site.phase() == QuarrySite.Phase.SHAFT) {
						site.skipShaft();
					} else {
						site.advance(false, true);
					}
					continue;
				}
				target = job.pos();
				fill = job.kind() == Verdict.FILL;
				ladder = job.kind() == Verdict.LADDER;
				break;
			}
			Verdict v = verdict(level, site, pos, pick, bag);
			if (v == Verdict.DIG || v == Verdict.FILL) {
				target = pos;
				fill = v == Verdict.FILL;
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

		// 2. A pickaxe in hand (ladders for the shaft, blocks to seal it with).
		if (!fill && !ladder && !isPickaxe(pick)) {
			fetchPickaxe(level, villager, site, bench, bag);
			return;
		}
		if (ladder && !bag.has(Items.LADDER, 1)) {
			fetchLadders(level, villager, site, bench, bag);
			return;
		}
		if (fill && filler(bag) == null) {
			fetchFiller(level, villager, site, bench, bag);
			return;
		}

		// 3. Room in the bag.
		if (bag.freeSlots() < 2) {
			site.setStatus(QuarrySite.Status.DEPOSITING);
			if (walkTo(level, villager, containerNear(level, bench), CONTAINER_REACH)) {
				deposit(level, villager, site, bench, bag);
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
		if (ladder) {
			placeLadder(level, villager, site, bag, target);
		} else if (fill) {
			placeStep(level, villager, site, bag, target, site.phase() == QuarrySite.Phase.PIT);
		} else {
			dig(level, villager, site, bag, pick, target);
		}
	}

	// --- what to dig ---------------------------------------------------------------------------

	private enum Verdict { DIG, EMPTY, LEAVE, KEEP, FILL, LADDER }

	/** What to do for a step of the ladder shaft, and where (sealing it may mean filling in the block beside it). */
	private record ShaftJob(Verdict kind, BlockPos pos) {
	}

	/**
	 * A step of the ladder shaft down to a strip mine, dug top-down: first the block underneath is made solid (never
	 * dig out what you'd fall through: a cave under the shaft gets a block to stand on, dug out again next), water and
	 * lava beside it are sealed off and the wall the ladder hangs on filled in; then the block is dug and its ladder put
	 * up. At the foot of the shaft (after the tunnels) only the ladders go in. EMPTY = done, move on; LEAVE = can't be
	 * dug (the shaft is given up).
	 */
	private static ShaftJob shaftJob(ServerLevel level, QuarrySite site, BlockPos pos, ItemStack pick, BuilderBag bag) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof net.minecraft.world.level.block.LadderBlock) {
			return new ShaftJob(Verdict.EMPTY, pos);
		}
		boolean shaft = site.phase() == QuarrySite.Phase.SHAFT;
		Direction wall = site.ladderWall();
		if (shaft && !solid(level, pos.below())) {
			return new ShaftJob(Verdict.FILL, pos.below());
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (!shaft && d != wall) {
				continue; // in the tunnel: the tunnels are open on purpose
			}
			BlockPos side = pos.relative(d);
			BlockState at = level.getBlockState(side);
			if (!at.getFluidState().isEmpty() || d == wall && !solid(level, side)) {
				if (MaterialRules.isProtected(at, at.getDestroySpeed(level, side))) {
					return new ShaftJob(Verdict.LEAVE, pos);
				}
				return new ShaftJob(Verdict.FILL, side);
			}
		}
		if (!state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) {
			return new ShaftJob(shaft ? Verdict.FILL : Verdict.EMPTY, pos);
		}
		if (state.isAir() || state.canBeReplaced() || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
			return new ShaftJob(Verdict.LADDER, pos);
		}
		if (!shaft) {
			return new ShaftJob(Verdict.EMPTY, pos); // something the tunnel pass left standing: no ladder there
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0 || MaterialRules.isProtected(state, hardness) || state.is(ModBlocks.BUILDERS_BENCH) || state.is(ModBlocks.MINERS_BENCH) || state.is(ModBlocks.BLUEPRINT_TABLE)
			|| pos.equals(site.bench()) || isPickaxe(pick) && state.requiresCorrectToolForDrops() && !pick.isCorrectToolForDrops(state)) {
			return new ShaftJob(Verdict.LEAVE, pos);
		}
		return new ShaftJob(Verdict.DIG, pos);
	}

	/** Solid enough to stand on or hang a ladder on, and no liquid. */
	private static boolean solid(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isCollisionShapeFullBlock(level, pos) && state.getFluidState().isEmpty();
	}

	/** What the miner fills gaps in the stairs with, from the bag (the first one there is). */
	public static final List<Item> FILLERS = List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.STONE, Items.DEEPSLATE, Items.ANDESITE,
		Items.DIORITE, Items.GRANITE, Items.TUFF, Items.DIRT, Items.NETHERRACK, Items.BLACKSTONE, Items.END_STONE);

	/**
	 * DIG the block, move on because the spot is EMPTY (air, fluid), or LEAVE it standing: unbreakable,
	 * a container or workstation, too hard for the pickaxe, or next to lava or water. Steps of the stairs are
	 * KEPT when solid, FILLED in when there's a gap in the pit wall (sand and gravel are dug out first).
	 */
	private static Verdict verdict(ServerLevel level, QuarrySite site, BlockPos pos, ItemStack pick, BuilderBag bag) {
		BlockState state = level.getBlockState(pos);
		if (site.isStripMine() && !site.isTunnel(pos) && !state.is(ORES)) {
			return Verdict.KEEP; // the rock between the tunnels (ores in it are dug like anything else)
		}
		if (site.isStep(pos)) {
			if (state.isCollisionShapeFullBlock(level, pos) && !(state.getBlock() instanceof FallingBlock)) {
				return Verdict.KEEP;
			}
			if (state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty()) {
				return inPitWall(level, site, pos) && filler(bag) != null ? Verdict.FILL : Verdict.EMPTY;
			}
			// Sand, gravel, a torch...: dug out like any other block, then filled in.
		}
		if (state.isAir() || !state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) {
			return Verdict.EMPTY;
		}
		if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
			return Verdict.DIG; // our own light: taken back as we go down
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0 || MaterialRules.isProtected(state, hardness) || state.is(ModBlocks.BUILDERS_BENCH) || state.is(ModBlocks.MINERS_BENCH) || state.is(ModBlocks.BLUEPRINT_TABLE)
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

	/** A step with ground beside it outside the pit: a gap there would break the stairs. Steps above the ground aren't needed. */
	private static boolean inPitWall(ServerLevel level, QuarrySite site, BlockPos pos) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos next = pos.relative(d);
			if (site.box().isInside(next)) {
				continue;
			}
			BlockState outside = level.getBlockState(next);
			if (outside.blocksMotion() || !outside.getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	static Item filler(BuilderBag bag) {
		for (Item item : FILLERS) {
			if (bag.has(item, 1)) {
				return item;
			}
		}
		return null;
	}

	/**
	 * Puts a block from the bag where a step is missing (or to seal the shaft: then the cursor stays put). Anyone
	 * standing there is lifted onto it.
	 */
	private static void placeStep(ServerLevel level, Villager villager, QuarrySite site, BuilderBag bag, BlockPos pos, boolean advance) {
		Item item = filler(bag);
		if (item == null) {
			if (advance) {
				site.advance(false, false);
			}
			return;
		}
		AABB space = new AABB(pos);
		if (!level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, space).isEmpty()) {
			return; // wait for the player to move
		}
		BlockState state = ((BlockItem) item).getBlock().defaultBlockState();
		villager.swing(InteractionHand.MAIN_HAND);
		level.setBlockAndUpdate(pos, state);
		level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 1f);
		bag.remove(item, 1);
		for (LivingEntity standing : level.getEntitiesOfClass(LivingEntity.class, space)) {
			standing.teleportTo(standing.getX(), pos.getY() + 1, standing.getZ());
		}
		if (advance) {
			site.advance(false, false);
		}
	}

	/** Hangs a ladder from the bag in the shaft (a torch there goes back in the bag). */
	private static void placeLadder(ServerLevel level, Villager villager, QuarrySite site, BuilderBag bag, BlockPos pos) {
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, site.ladderWall().getOpposite());
		if (!ladder.canSurvive(level, pos)) {
			site.advance(false, true);
			return;
		}
		BlockState there = level.getBlockState(pos);
		if (there.is(Blocks.TORCH) || there.is(Blocks.WALL_TORCH)) {
			bag.addAll(Items.TORCH, 1);
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.setBlockAndUpdate(pos, ladder);
		level.playSound(null, pos, SoundEvents.LADDER_PLACE, SoundSource.BLOCKS, 1f, 1f);
		bag.remove(Items.LADDER, 1);
		if (site.phase() == QuarrySite.Phase.SHAFT && Math.floorMod(site.shaftTop() - pos.getY(), SHAFT_TORCH_EVERY) == SHAFT_TORCH_EVERY / 2) {
			lightShaft(level, villager, site, bag, pos);
		}
		site.advance(false, false);
	}

	/** Every so many blocks down the shaft, a torch in a niche cut into the wall across from the ladders. */
	static final int SHAFT_TORCH_EVERY = 8;

	private static void lightShaft(ServerLevel level, Villager villager, QuarrySite site, BuilderBag bag, BlockPos pos) {
		if (!bag.has(Items.TORCH, 1)) {
			return;
		}
		BlockPos niche = pos.relative(site.ladderWall().getOpposite());
		BlockState rock = level.getBlockState(niche);
		if (!solid(level, niche) || !solid(level, niche.below()) || site.box().isInside(niche)
			|| MaterialRules.isProtected(rock, rock.getDestroySpeed(level, niche)) || rock.hasBlockEntity()) {
			return;
		}
		for (Direction d : Direction.values()) {
			BlockPos next = niche.relative(d);
			if (!next.equals(pos) && !level.getFluidState(next).isEmpty()) {
				return; // cutting it would let water or lava in
			}
		}
		for (ItemStack drop : Block.getDrops(rock, level, niche, null, villager, villager.getMainHandItem())) {
			ItemStack rest = bag.add(drop);
			if (!rest.isEmpty()) {
				Block.popResource(level, pos, rest);
			}
		}
		level.setBlockAndUpdate(niche, Blocks.TORCH.defaultBlockState());
		bag.remove(Items.TORCH, 1);
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
			digTotal = digTicks(villager, ticks);
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
		// A Ground, Rock or Steel partner digs at the block alongside, in a shower of its crumbs (ROADMAP 28.6).
		io.github.jcondedata.aliveworkplace.work.PartnerShows.cue(villager, "dig", target, new ItemStack(state.getBlock()));
		level.destroyBlock(target, false, villager);
		for (ItemStack drop : drops) {
			ItemStack rest = bag.add(drop);
			if (!rest.isEmpty()) {
				Miners.store(level, List.of(), villager.blockPosition(), rest);
			}
		}
		pick.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
		boolean torch = state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH);
		if (site.awaitsLadder(target)) {
			site.countMined(); // the ladder goes in next, then the cursor moves on
		} else if (!site.isStep(target)) {
			site.advance(!torch, false);
		} // else a loose step (sand, gravel...): the same spot gets filled in next

		if (!torch && site.mined() % 10 == 0) {
			BuilderLevels.addXp(level, villager, 1, site.owner());
		}
		if (++sinceTorch >= TORCH_EVERY) {
			sinceTorch = 0;
			lightUp(level, villager, site, bag);
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

	/** Dark down here? Put a torch down where the miner stands (if it brought any; not in the ladder shaft). */
	private static void lightUp(ServerLevel level, Villager villager, QuarrySite site, BuilderBag bag) {
		BlockPos feet = villager.blockPosition();
		if (site.inShaft(feet) || !bag.has(Items.TORCH, 1) || level.getBrightness(LightLayer.BLOCK, feet) >= 8 || level.getBrightness(LightLayer.SKY, feet) >= 8) {
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
			// Another worker in the village may have a spare.
			io.github.jcondedata.aliveworkplace.work.Village.Find elsewhere = io.github.jcondedata.aliveworkplace.work.Village.find(level, villager, bench, site.box(), MinerWork::isPickaxe);
			if (elsewhere != null) {
				supplies = elsewhere.stash().chests();
				chest = elsewhere.chest();
			}
		}
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
		supplies = SupplyContainers.find(level, bench, site.box()); // the rest from our own chests
		topUpTorches(level, supplies, bag);
		topUpFiller(level, site, supplies, bag);
		topUpLadders(level, site, supplies, bag);
	}

	/** Ladders for the shaft from the chests; with none there, wait by the bench and tell the owner. */
	private void fetchLadders(ServerLevel level, Villager villager, QuarrySite site, BlockPos bench, BuilderBag bag) {
		List<BlockPos> supplies = SupplyContainers.find(level, bench, site.box());
		BlockPos chest = SupplyContainers.firstMatching(level, supplies, stack -> stack.is(Items.LADDER));
		if (chest == null) {
			site.setStatus(QuarrySite.Status.NEEDS_LADDERS);
			walkTo(level, villager, bench, 3);
			Miners.notifyNeedsLadders(level, villager, site);
			return;
		}
		if (walkTo(level, villager, chest, CONTAINER_REACH)) {
			topUpLadders(level, site, supplies, bag);
			topUpTorches(level, supplies, bag);
			level.playSound(null, chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		}
	}

	/** Blocks to seal the shaft with from the chests; with none there, the miner gives up on the shaft. */
	private void fetchFiller(ServerLevel level, Villager villager, QuarrySite site, BlockPos bench, BuilderBag bag) {
		List<BlockPos> supplies = SupplyContainers.find(level, bench, site.box());
		BlockPos chest = SupplyContainers.firstMatching(level, supplies, stack -> FILLERS.contains(stack.getItem()));
		if (chest == null) {
			if (site.phase() == QuarrySite.Phase.SHAFT) {
				site.skipShaft();
			} else {
				site.advance(false, true);
			}
			return;
		}
		if (walkTo(level, villager, chest, CONTAINER_REACH)) {
			topUpFiller(level, site, supplies, bag);
		}
	}

	private static void topUpLadders(ServerLevel level, QuarrySite site, List<BlockPos> supplies, BuilderBag bag) {
		if (!site.hasShaft() || site.phase() == QuarrySite.Phase.PIT) {
			return;
		}
		int have = bag.count(Items.LADDER);
		if (have < 16) {
			int got = SupplyContainers.extract(level, supplies, Items.LADDER, 64 - have);
			if (got > 0) {
				bag.addAll(Items.LADDER, got);
			}
		}
	}

	private static void deposit(ServerLevel level, Villager villager, QuarrySite site, BlockPos bench, BuilderBag bag) {
		List<BlockPos> supplies = SupplyContainers.find(level, bench, null);
		for (ItemStack stack : bag.takeAllExcept(java.util.Set.of(Items.TORCH, Items.LADDER))) {
			Miners.store(level, supplies, bench, stack);
		}
		topUpTorches(level, supplies, bag);
		topUpFiller(level, site, supplies, bag);
		topUpLadders(level, site, supplies, bag);
		io.github.jcondedata.aliveworkplace.work.Furnaces.tend(level, bench, supplies, io.github.jcondedata.aliveworkplace.work.Furnaces::isOre, villager);
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

	/** A few blocks to fill gaps in the stairs with, taken back out of the chests. */
	private static void topUpFiller(ServerLevel level, QuarrySite site, List<BlockPos> supplies, BuilderBag bag) {
		if (!site.hasStairs() && !site.hasShaft() || filler(bag) != null) {
			return;
		}
		for (Item item : FILLERS) {
			int got = SupplyContainers.extract(level, supplies, item, 8);
			if (got > 0) {
				bag.addAll(item, got);
				return;
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
			if (site.phase() == QuarrySite.Phase.SHAFT) {
				site.skipShaft(); // stuck in the shaft: make its own way down
			} else {
				site.advance(false, true); // nowhere to dig it from: leave it
			}
		}
		return false;
	}
}
