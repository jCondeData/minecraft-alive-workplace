package io.github.jcondedata.aliveworkplace.grave;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * An Undertaker's shift at their table: with a golden apple, a healing potion or a totem of undying in the chests, they
 * take it to the nearest grave within {@link #RADIUS} blocks and, after a little while at the graveside, bring back
 * whoever lies there. Without one they post a request for a golden apple (the Storehouse board shows it).
 */
public class UndertakerWork extends Behavior<Villager> {
	/** How far from the table graves are tended. */
	public static int RADIUS = 32;
	/** Ticks at the graveside before they're back. */
	static final int RITES = 100;
	static final int LOOK_EVERY = 40;

	private enum Task { NONE, FETCH, GO, RITES }

	private final Walker walker = new Walker(0.5f);
	private Task task = Task.NONE;
	@Nullable
	private BlockPos grave;
	private int timer;
	private String state = "none";

	public UndertakerWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	public static boolean isUndertaker(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.UNDERTAKER;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && isUndertaker(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		task = Task.NONE;
		timer = 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos table = Builders.benchPos(villager).orElse(null);
		if (table == null) {
			return;
		}
		List<BlockPos> own = SupplyContainers.find(level, table, null);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		if (grave != null && !level.getBlockState(grave).is(ModBlocks.GRAVE)) {
			grave = null;
			task = Task.NONE;
		}
		switch (task) {
			case NONE -> choose(level, villager, table, own, bag);
			case FETCH -> {
				if (walker.walkTo(level, villager, own.get(0), 3.0)) {
					ItemStack item = SupplyContainers.takeOne(level, own, Graves::isRevivalItem);
					if (item.isEmpty()) {
						task = Task.NONE;
					} else {
						bag.add(item);
						task = Task.GO;
					}
					walker.reset();
				}
			}
			case GO -> {
				if (walker.walkTo(level, villager, grave, 2.5)) {
					task = Task.RITES;
					timer = RITES;
					walker.reset();
				}
			}
			case RITES -> rites(level, villager, bag);
		}
		status(villager);
	}

	private void choose(ServerLevel level, Villager villager, BlockPos table, List<BlockPos> own, BuilderBag bag) {
		walker.walkTo(level, villager, table, 2.5);
		if (--timer > 0) {
			return;
		}
		timer = LOOK_EVERY;
		List<BlockPos> graves = Graves.near(level, table, RADIUS);
		if (graves.isEmpty()) {
			state = "none";
			Requests.clear(villager);
			return;
		}
		boolean inBag = bag.stacks().stream().anyMatch(Graves::isRevivalItem);
		if (!inBag && (own.isEmpty() || SupplyContainers.firstMatching(level, own, Graves::isRevivalItem) == null)) {
			state = own.isEmpty() ? "no_chest" : "needs";
			Requests.post(villager, new ItemStack(Items.GOLDEN_APPLE), 1, Component.translatable("request.aliveworkplace.revival"), Graves::isRevivalItem);
			return;
		}
		Requests.clear(villager);
		grave = graves.get(0);
		task = inBag ? Task.GO : Task.FETCH;
		state = "going";
		walker.reset();
	}

	private void rites(ServerLevel level, Villager villager, BuilderBag bag) {
		state = "rites";
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new net.minecraft.world.entity.ai.behavior.BlockPosTracker(grave));
		if (timer % 20 == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			level.sendParticles(ParticleTypes.SOUL, grave.getX() + 0.5, grave.getY() + 0.8, grave.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, grave, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 0.8f);
		}
		if (--timer > 0) {
			return;
		}
		ItemStack item = bag.takeFirst(Graves::isRevivalItem);
		if (item.isEmpty()) {
			task = Task.NONE;
			return;
		}
		Component name = level.getBlockEntity(grave) instanceof GraveBlockEntity g ? g.name() : null;
		Villager back = Graves.revive(level, grave);
		if (back == null) {
			bag.add(item); // couldn't: keep what they'd have used
		} else {
			item.shrink(1);
			if (item.is(Items.POTION) || item.is(Items.SPLASH_POTION)) {
				bag.add(new ItemStack(Items.GLASS_BOTTLE));
			} else if (!item.isEmpty()) {
				bag.add(item);
			}
			ModAttachments.VILLAGERS_REVIVED.set(villager, ModAttachments.VILLAGERS_REVIVED.getOrElse(villager, 0) + 1);
			BuilderLevels.addXp(level, villager, 5, null);
			for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(villager) < 48 * 48)) {
				Chat.chat(player, Component.translatable("message.aliveworkplace.grave.revived", name != null ? name : back.getDisplayName())
					.withStyle(ChatFormatting.GREEN));
			}
		}
		grave = null;
		task = Task.NONE;
		timer = LOOK_EVERY;
	}

	private void status(Villager villager) {
		WorkerStatus.set(villager, Component.translatable("message.aliveworkplace.undertaker.title",
				ModAttachments.VILLAGERS_REVIVED.getOrElse(villager, 0)), -1f,
			Component.translatable("message.aliveworkplace.undertaker.state." + state)
				.withStyle(state.equals("needs") || state.equals("no_chest") ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		task = Task.NONE;
	}
}
