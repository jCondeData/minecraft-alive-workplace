package io.github.jcondedata.aliveworkplace.research;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * A Legend at their research tree (ROADMAP 29.11), as {@link ScholarWork} works the scholars' tree: once the village has
 * chosen a topic on the tree's tab, the Legend goes to a lectern within {@link #REACH} blocks of their home, pays the
 * level from the chests by it, and works on it there a point a tick at their pace. Never while on strike (their work is
 * the picket then), never without a home or a lectern near it. It sits in every trade's WORK and IDLE packages (through
 * {@code legend/Picket}), and starts only for a settled Legend whose tree has a topic chosen.
 */
public class TreeWork extends Behavior<Villager> {
	/** How far from their home a Legend's lectern may be. */
	public static final int REACH = 8;
	static final int EVERY = 20;

	private final Walker walker = new Walker(0.5f);
	private int timer;
	private long nextLook;

	public TreeWork() {
		super(ImmutableMap.of(
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	/** What a Legend would work on now: the tree, its hall and the lectern. */
	public record Job(ResearchTree tree, BlockPos hall, VillageHallBlockEntity entity, BlockPos lectern) {
	}

	/** The tree {@code legend} would work on now, or null: settled, not on strike, a topic chosen, a lectern near home. */
	@Nullable
	public static Job job(ServerLevel level, Villager legend) {
		if (!Legends.ENABLED || legend.isBaby() || legend.isSleeping()) {
			return null;
		}
		LegendData data = ModAttachments.LEGEND.get(legend);
		if (data == null || !data.settled() || data.onStrike()) {
			return null;
		}
		var trees = ResearchTrees.of(data.id());
		if (trees.isEmpty()) {
			return null;
		}
		BlockPos hall = data.hall().orElseGet(() -> VillageHalls.nearest(level, legend.blockPosition()).orElse(null));
		if (hall == null || !level.isLoaded(hall) || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return null;
		}
		ResearchTree chosen = null;
		for (ResearchTree tree : trees) {
			if (ResearchTrees.current(entity.research(), tree).isPresent()) {
				chosen = tree;
				break;
			}
		}
		if (chosen == null) {
			return null;
		}
		BlockPos lectern = lectern(level, legend);
		return lectern == null ? null : new Job(chosen, hall, entity, lectern);
	}

	/** The lectern nearest {@code legend}'s home, within {@link #REACH} blocks of it; null without a home or a lectern. */
	@Nullable
	public static BlockPos lectern(ServerLevel level, Villager legend) {
		BlockPos home = VillageNeeds.bed(level, legend);
		if (home == null) {
			return null;
		}
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(home.offset(-REACH, -REACH, -REACH), home.offset(REACH, REACH, REACH))) {
			if (level.getBlockState(pos).is(Blocks.LECTERN)) {
				double d = pos.distSqr(home);
				if (d < bestDist && d <= (double) REACH * REACH) {
					best = pos.immutable();
					bestDist = d;
				}
			}
		}
		return best;
	}

	/** The job, looked up at most once a second (the lectern search). */
	@Nullable
	private Job kept(ServerLevel level, Villager villager) {
		if (level.getGameTime() >= nextLook || level.getGameTime() < nextLook - EVERY) {
			nextLook = level.getGameTime() + EVERY;
			kept = job(level, villager);
		}
		return kept;
	}

	@Nullable
	private Job kept;

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return ModAttachments.LEGEND.has(villager) && kept(level, villager) != null;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return ModAttachments.LEGEND.has(villager) && kept(level, villager) != null;
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		timer = 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		Job job = kept(level, villager);
		if (job == null) {
			return;
		}
		boolean atLectern = walker.walkTo(level, villager, job.lectern(), 2.5);
		if (--timer > 0) {
			return;
		}
		timer = EVERY;
		step(level, villager, job, atLectern);
	}

	/** One round at the lectern (every second): pay for the topic, or work on it when there. */
	public static void step(ServerLevel level, Villager legend, Job job, boolean atLectern) {
		Research.State state = job.entity().research();
		Optional<ResearchTrees.Current> now = ResearchTrees.current(state, job.tree());
		if (now.isEmpty()) {
			return;
		}
		ResearchTree.Topic topic = now.get().topic();
		int next = ResearchTrees.level(state, job.tree(), topic) + 1;
		Component levelText = Component.translatable("research.aliveworkplace.level", topic.name(), BuilderLevels.levelName(next));
		if (!now.get().paid()) {
			Map<Item, Integer> missing = new LinkedHashMap<>();
			if (!ResearchTrees.pay(level, job.entity(), job.tree(), job.lectern(), missing)) {
				Map.Entry<Item, Integer> first = missing.entrySet().iterator().next();
				Requests.post(legend, new ItemStack(first.getKey()), first.getValue(), first.getKey().getDescription(), s -> s.is(first.getKey()));
				status(legend, job, Component.translatable("message.aliveworkplace.scholar.state.needs", Research.describe(topic.cost(next))), true);
				return;
			}
			Requests.clear(legend);
		}
		status(legend, job, Component.translatable("message.aliveworkplace.scholar.state.researching", levelText), false);
		if (!atLectern) {
			return;
		}
		legend.swing(InteractionHand.MAIN_HAND);
		level.sendParticles(ParticleTypes.ENCHANT, job.lectern().getX() + 0.5, job.lectern().getY() + 1.3, job.lectern().getZ() + 0.5, 5, 0.3, 0.2, 0.3, 0.5);
		ResearchTrees.addProgress(level, job.hall(), job.entity(), job.tree(), ScholarWork.progress(legend), legend);
	}

	private static void status(Villager legend, Job job, Component line, boolean wants) {
		WorkerStatus.set(legend, job.tree().name(), -1f, line.copy().withStyle(wants ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}
}
