package io.github.jcondedata.aliveworkplace.research;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * A Scholar's shift at their desk: the village's chosen research (see {@link Research}; picked on the scholar's screen)
 * is paid for from the chests by the desk — paper, books, emeralds — and then worked on at the desk, a point a tick
 * (quicker with pastured Psychic Pokémon). Several scholars in a village share the work. A village needs a Village Hall
 * for research: that's where it's kept.
 */
public class ScholarWork extends Behavior<Villager> {
	static final int EVERY = 20;

	private final Walker walker = new Walker(0.5f);
	private int timer;
	private String state = "idle";
	private Component detail = Component.empty();

	public ScholarWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	public static boolean isScholar(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.SCHOLAR;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && isScholar(villager) && Builders.benchPos(villager).isPresent();
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
		BlockPos desk = Builders.benchPos(villager).orElse(null);
		if (desk == null) {
			return;
		}
		boolean atDesk = walker.walkTo(level, villager, desk, 2.5);
		if (--timer > 0) {
			return;
		}
		timer = EVERY;
		work(level, villager, desk, atDesk);
		WorkerStatus.set(villager, Component.translatable("message.aliveworkplace.scholar.title",
				ModAttachments.RESEARCH_DONE.getOrElse(villager, 0)), -1f,
			Component.translatable("message.aliveworkplace.scholar.state." + state, detail)
				.withStyle(state.equals("needs") || state.equals("no_hall") ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}

	void work(ServerLevel level, Villager villager, BlockPos desk, boolean atDesk) {
		BlockPos hall = VillageHalls.nearest(level, desk).orElse(null);
		if (hall == null || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			state = "no_hall";
			return;
		}
		Research.State research = entity.research();
		Research.Topic topic = research.currentTopic();
		if (topic == null) {
			state = "idle";
			detail = Component.empty();
			Requests.clear(villager);
			return;
		}
		int next = research.level(topic) + 1;
		detail = Component.translatable("research.aliveworkplace.level", topic.title(), BuilderLevels.levelName(next));
		if (!research.paid()) {
			List<BlockPos> own = SupplyContainers.find(level, desk, null);
			Map<Item, Integer> cost = topic.cost(next).items();
			for (var e : cost.entrySet()) {
				if (SupplyContainers.count(level, own, e.getKey()) < e.getValue()) {
					state = "needs";
					detail = Research.describe(topic.cost(next));
					Requests.post(villager, new ItemStack(e.getKey()), (int) (e.getValue() - SupplyContainers.count(level, own, e.getKey())),
						e.getKey().getDescription(), s -> s.is(e.getKey()));
					return;
				}
			}
			Requests.clear(villager);
			for (var e : cost.entrySet()) {
				SupplyContainers.extract(level, own, e.getKey(), e.getValue());
			}
			entity.setResearch(research.paidFor());
			level.playSound(null, desk, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
			state = "researching";
			return;
		}
		if (!atDesk) {
			state = "researching";
			return;
		}
		state = "researching";
		int progress = research.progress() + Math.round(EVERY / Partners.factor(villager));
		villager.swing(InteractionHand.MAIN_HAND);
		level.sendParticles(ParticleTypes.ENCHANT, desk.getX() + 0.5, desk.getY() + 1.3, desk.getZ() + 0.5, 5, 0.3, 0.2, 0.3, 0.5);
		// A Psychic partner floats a book beside the desk, among enchanting glyphs (ROADMAP 28.6).
		io.github.jcondedata.aliveworkplace.work.PartnerShows.cue(villager, "study", desk);
		if (progress < topic.points(next)) {
			entity.setResearch(research.withProgress(progress));
			return;
		}
		finish(level, villager, desk, hall, entity, topic, next);
	}

	private static void finish(ServerLevel level, Villager villager, BlockPos desk, BlockPos hall, VillageHallBlockEntity entity,
							   Research.Topic topic, int lvl) {
		entity.setResearch(entity.research().finish());
		Research.forget();
		ModAttachments.RESEARCH_DONE.set(villager, ModAttachments.RESEARCH_DONE.getOrElse(villager, 0) + 1);
		BuilderLevels.addXp(level, villager, 5, null);
		level.playSound(null, desk, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 1.2f);
		Component text = Component.translatable("message.aliveworkplace.research.done", VillageHalls.name(level, hall),
			Component.translatable("research.aliveworkplace.level", topic.title(), BuilderLevels.levelName(lvl)), topic.effect())
			.withStyle(ChatFormatting.AQUA);
		for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(hall.getCenter()) < (double) VillageHalls.RADIUS * VillageHalls.RADIUS)) {
			Chat.chat(player, text);
		}
		io.github.jcondedata.aliveworkplace.hall.Chronicle.record(level, hall, io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.RESEARCH, Component.translatable("chronicle.aliveworkplace.research",
			Component.translatable("research.aliveworkplace.level", topic.title(), BuilderLevels.levelName(lvl)), villager.getDisplayName()));
		if (topic == Research.Topic.ARCHITECTURE) {
			List<BlockPos> own = SupplyContainers.find(level, desk, null);
			for (ResourceLocation id : Research.ARCHITECTURE_BLUEPRINTS) {
				BlueprintLibrary.get(level, id).ifPresent(b -> {
					ItemStack blueprint = BlueprintItem.create(id, b.size());
					ItemStack rest = own.isEmpty() ? blueprint : SupplyContainers.insert(level, own, blueprint);
					if (!rest.isEmpty()) {
						Block.popResource(level, desk.above(), rest);
					}
				});
			}
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}
}
