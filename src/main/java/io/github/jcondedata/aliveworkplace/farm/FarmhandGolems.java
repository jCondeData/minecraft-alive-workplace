package io.github.jcondedata.aliveworkplace.farm;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.legend.GolemRoleGoal;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Farmhand Golems (ROADMAP 29.15): a Golem Smith's golem with the role {@code farmhand} tends the village's fields —
 * the fields farmers were given with a Field Marker and the farms village farmers took on themselves ({@link FieldJob}) —
 * harvesting ripe crops and planting the same crop right back from what it picked, and carries the harvest to the
 * chests by that field's composter ({@link SupplyContainers#find}), {@link #STACKS} stacks at most a trip. Only crops are
 * picked (wheat, carrots, potatoes, beetroot, nether wart and other mods' crops): it never breaks any other block. The
 * load is saved on the golem ({@link ModAttachments#HAULER_LOAD}).
 */
public final class FarmhandGolems {
	public static final int STACKS = 9;
	/** How far from the golem a farmer's field may be. */
	public static final int RANGE = Fields.MAX_DISTANCE;
	private static final double REACH = 2.5;
	private static final double CHEST_REACH = 3.0;
	private static final int RESCAN = 20;

	/** A field to tend: its box and the station (composter) whose chests take the harvest. */
	public record Field(BoundingBox box, BlockPos station) {
	}

	private record State(@Nullable Field field, @Nullable BlockPos crop, int rescan) {
	}

	private static final Map<IronGolem, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

	/** One step: carry the harvest to the chests when full or done, else go to the nearest ripe crop and pick it. */
	public static void work(ServerLevel level, IronGolem golem) {
		State state = STATES.getOrDefault(golem, new State(null, null, 0));
		Map<Item, Integer> load = HaulerGolems.load(golem);
		Field field = state.field();
		BlockPos crop = state.crop();
		if (crop != null && (field == null || !isRipe(level, crop))) {
			crop = null;
		}
		if (crop == null && !full(load)) {
			if (state.rescan() > 0) {
				STATES.put(golem, new State(field, null, state.rescan() - 1));
			} else {
				Field nearest = null;
				BlockPos best = null;
				double bestDistance = Double.MAX_VALUE;
				for (Field f : fields(level, golem)) {
					for (BlockPos p : ripe(level, f.box())) {
						double d = p.distSqr(golem.blockPosition());
						if (d < bestDistance) {
							bestDistance = d;
							best = p;
							nearest = f;
						}
					}
				}
				crop = best;
				if (nearest != null) {
					field = nearest;
				}
				STATES.put(golem, new State(field, crop, crop == null ? RESCAN / GolemRoleGoal.EVERY : 0));
			}
		}
		if (crop == null) {
			if (!load.isEmpty()) {
				deposit(level, golem, field);
			}
			return;
		}
		if (full(load)) {
			deposit(level, golem, field);
			return;
		}
		if (GolemRoleGoal.walkTo(golem, crop, REACH)) {
			harvest(level, golem, crop);
			STATES.put(golem, new State(field, null, 0));
		}
	}

	/** Takes the load to the chests by {@code field}'s station (or the nearest field's, after a reload). */
	static void deposit(ServerLevel level, IronGolem golem, @Nullable Field field) {
		if (field == null) {
			List<Field> all = fields(level, golem);
			if (all.isEmpty()) {
				return;
			}
			field = all.get(0);
			STATES.put(golem, new State(field, null, 0));
		}
		List<BlockPos> chests = SupplyContainers.find(level, field.station(), null);
		if (chests.isEmpty()) {
			return;
		}
		if (GolemRoleGoal.walkTo(golem, chests.get(0), CHEST_REACH)) {
			HaulerGolems.unload(level, golem, chests, HaulerGolems.load(golem));
			level.playSound(null, chests.get(0), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.1f);
		}
	}

	/** The village's fields round {@code golem}, nearest first: every farmer's within {@link #RANGE}. */
	public static List<Field> fields(ServerLevel level, IronGolem golem) {
		List<Field> out = new ArrayList<>();
		for (Villager farmer : level.getEntitiesOfClass(Villager.class, new AABB(golem.blockPosition()).inflate(RANGE), Villager::isAlive)) {
			FieldJob job = ModAttachments.FARM_FIELD.get(farmer);
			BlockPos station = Builders.benchPos(farmer).orElse(null);
			if (job != null && station != null) {
				out.add(new Field(job.box(), station));
			}
		}
		out.sort((a, b) -> Double.compare(a.box().getCenter().distSqr(golem.blockPosition()), b.box().getCenter().distSqr(golem.blockPosition())));
		return out;
	}

	/** The ripe crops on {@code box}, scanned as a farmer scans their field (from a little above it to just below). */
	public static List<BlockPos> ripe(ServerLevel level, BoundingBox box) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY() - 1, box.minZ(), box.maxX(), box.maxY() + 3, box.maxZ())) {
			if (isRipe(level, p)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** A full-grown crop (the bottom half of a two-block one) or nether wart. */
	public static boolean isRipe(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof CropBlock crop) {
			return !(state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) && crop.isMaxAge(state);
		}
		return state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
	}

	/** Picks the crop at {@code pos} into the golem's load and plants one of its seeds right back. */
	static void harvest(ServerLevel level, IronGolem golem, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		Item seed = state.getBlock().getCloneItemStack(level, pos, state).getItem();
		List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos));
		level.destroyBlock(pos, false, golem);
		boolean replanted = false;
		Map<Item, Integer> load = new LinkedHashMap<>(HaulerGolems.load(golem));
		for (ItemStack drop : drops) {
			if (!replanted && drop.is(seed) && seed instanceof BlockItem item) {
				BlockState sown = item.getBlock().defaultBlockState();
				if (level.getBlockState(pos).isAir() && sown.canSurvive(level, pos)) {
					level.setBlockAndUpdate(pos, sown);
					drop.shrink(1);
					replanted = true;
				}
			}
			if (!drop.isEmpty()) {
				load.merge(drop.getItem(), drop.getCount(), Integer::sum);
			}
		}
		ModAttachments.HAULER_LOAD.set(golem, load.isEmpty() ? null : Map.copyOf(load));
		golem.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8f, 1f);
	}

	/** Whether the load fills {@link #STACKS} stacks. */
	static boolean full(Map<Item, Integer> load) {
		int stacks = 0;
		for (Map.Entry<Item, Integer> e : load.entrySet()) {
			int per = Math.max(1, e.getKey().getDefaultMaxStackSize());
			stacks += (e.getValue() + per - 1) / per;
		}
		return stacks >= STACKS;
	}

	private FarmhandGolems() {
	}
}
