package io.github.jcondedata.aliveworkplace.compost;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Village;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ComposterBlock;

/**
 * A Composter's shift at the Compost Bin: the village's scraps — seeds, saplings, leaves, crop waste, spoiled food and
 * rotten flesh from the chests by the bin (or, when those are empty, the storehouse's) — go into the bin one at a time,
 * and every {@link #LEVELS_PER_BONE_MEAL} layers of compost come out as a bone meal in the chests (a vanilla composter
 * needs seven). Each scrap adds a layer with the vanilla composter's chance, as a share rather than a dice roll, so
 * nothing's wasted; rotten flesh counts half. The bone meal is for the florists, lumberjacks, orchard keepers and farmers.
 */
public class CompostWork extends Behavior<Villager> {
	/** Ticks a scrap takes a Novice. */
	static final int COMPOST_TICKS = 30;
	/** Layers of compost that make a bone meal. */
	public static final float LEVELS_PER_BONE_MEAL = 5f;

	private final Walker walker = new Walker(0.5f);
	private int timer;
	private String state = "idle";

	public CompostWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	/** The Composter's WORK activity. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new CompostWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	public static boolean isComposter(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.COMPOSTER;
	}

	/** Layers a bone meal takes this composter: fewer with Green Thumb researched. */
	public static float layersPerBoneMeal(Villager villager) {
		return LEVELS_PER_BONE_MEAL - io.github.jcondedata.aliveworkplace.research.Research.level(villager, io.github.jcondedata.aliveworkplace.research.Research.Topic.GREEN_THUMB);
	}

	/** How much of a layer of compost {@code stack} makes (0: it can't be composted). */
	public static float layers(ItemStack stack) {
		if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.POISONOUS_POTATO) || stack.is(Items.SPIDER_EYE)) {
			return 0.5f;
		}
		return ComposterBlock.COMPOSTABLES.getOrDefault(stack.getItem(), 0f);
	}

	public static boolean isCompostable(ItemStack stack) {
		return layers(stack) > 0 && stack.getComponentsPatch().isEmpty();
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && isComposter(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		timer = 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos bin = Builders.benchPos(villager).orElse(null);
		if (bin == null) {
			return;
		}
		boolean atBin = walker.walkTo(level, villager, bin, 2.5);
		WorkerStatus.set(villager, Component.translatable("message.aliveworkplace.composter.title", ModAttachments.BONE_MEAL_MADE.getOrElse(villager, 0)),
			ModAttachments.COMPOST_LAYERS.getOrElse(villager, 0f) / layersPerBoneMeal(villager),
			Component.translatable("message.aliveworkplace.composter.state." + state).withStyle(state.equals("needs") ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		if (!atBin || --timer > 0) {
			return;
		}
		timer = Math.max(8, BuilderLevels.delay(COMPOST_TICKS, villager)); // partners counted once, in the pace (ROADMAP 30.2)
		state = compost(level, villager, bin) ? "composting" : "needs";
	}

	/** Chests the scraps may come from: the bin's own, and the storehouse's when the village shares. */
	static List<BlockPos> sources(ServerLevel level, Villager villager, BlockPos bin) {
		List<BlockPos> sources = new ArrayList<>(SupplyContainers.find(level, bin, null));
		if (Village.RADIUS > 0) {
			for (Village.Stash stash : Village.stashes(level, villager, bin, null)) {
				if (stash.job() == ModVillagers.PORTER) {
					sources.addAll(stash.chests());
				}
			}
		}
		return sources;
	}

	/** Puts one scrap in the bin (a bone meal into the chests once there's compost enough); false if there was nothing to put in. */
	public static boolean compost(ServerLevel level, Villager villager, BlockPos bin) {
		List<BlockPos> own = SupplyContainers.find(level, bin, null);
		ItemStack scrap = SupplyContainers.takeOne(level, sources(level, villager, bin), CompostWork::isCompostable);
		if (scrap.isEmpty()) {
			Requests.post(villager, new ItemStack(Items.ROTTEN_FLESH), 16, Component.translatable("request.aliveworkplace.compost"), CompostWork::isCompostable);
			return false;
		}
		Requests.clear(villager);
		float layers = ModAttachments.COMPOST_LAYERS.getOrElse(villager, 0f) + layers(scrap);
		int made = 0;
		float perMeal = layersPerBoneMeal(villager);
		while (layers >= perMeal) {
			layers -= perMeal;
			made++;
		}
		ModAttachments.COMPOST_LAYERS.set(villager, layers);
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, bin, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 0.7f, 1f);
		// A Poison or Grass partner stirs the compost: green bubbles (ROADMAP 28.6).
		io.github.jcondedata.aliveworkplace.work.PartnerShows.cue(villager, "compost", bin);
		if (made > 0) {
			ItemStack meal = new ItemStack(Items.BONE_MEAL, made);
			ItemStack rest = own.isEmpty() ? meal : SupplyContainers.insert(level, own, meal);
			if (!rest.isEmpty()) {
				villager.spawnAtLocation(rest);
			}
			level.playSound(null, bin, SoundEvents.COMPOSTER_READY, SoundSource.BLOCKS, 0.8f, 1f);
			level.sendParticles(ParticleTypes.COMPOSTER, bin.getX() + 0.5, bin.getY() + 1.0, bin.getZ() + 0.5, 10, 0.3, 0.1, 0.3, 0);
			int total = ModAttachments.BONE_MEAL_MADE.getOrElse(villager, 0) + made;
			ModAttachments.BONE_MEAL_MADE.set(villager, total);
			BuilderLevels.addXp(level, villager, made, null);
		}
		return true;
	}
}
