package io.github.jcondedata.aliveworkplace.hall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Money;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Village quests: a village with a Village Hall posts a quest a day (up to {@link #MAX_OPEN} open) for players — bring
 * what a worker is waiting for (or food when the store is low, or something the village could use), clear out the
 * monsters round the village, or beat one of the village's Pokémon trainers (with Cobblemon). Items are handed in at the
 * hall; monsters and battles count wherever they happen within the village. Whoever finishes a quest gets its reward in
 * emeralds (or CobbleDollars). Quests nobody finishes are taken down after {@link #LASTS} ticks.
 */
public final class VillageQuests {
	/** Open quests a village has at once. */
	public static int MAX_OPEN = 3;
	/** How long a quest stays up (three days). */
	public static long LASTS = 72000;
	/** Monsters a clearing-out quest asks for. */
	static final int MONSTERS = 8;

	public enum Kind { BRING, SLAY, BATTLE }

	/**
	 * A quest: what kind, the item and how many (for BRING; monsters or battles otherwise), how far along, the reward in
	 * emeralds, when it went up, who asked (a villager's name) and where a BRING quest's items go (a worker's station).
	 */
	public record Quest(UUID id, Kind kind, String item, int count, int progress, int reward, long posted, String poster,
						Optional<BlockPos> deliverTo) {
		public static final Codec<Quest> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Quest::id),
			Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(Quest::kind),
			Codec.STRING.optionalFieldOf("item", "minecraft:air").forGetter(Quest::item),
			Codec.INT.fieldOf("count").forGetter(Quest::count),
			Codec.INT.optionalFieldOf("progress", 0).forGetter(Quest::progress),
			Codec.INT.fieldOf("reward").forGetter(Quest::reward),
			Codec.LONG.fieldOf("posted").forGetter(Quest::posted),
			Codec.STRING.optionalFieldOf("poster", "").forGetter(Quest::poster),
			BlockPos.CODEC.optionalFieldOf("deliver_to").forGetter(Quest::deliverTo)
		).apply(i, Quest::new));

		public Item itemType() {
			return BuiltInRegistries.ITEM.get(ResourceLocation.parse(item));
		}

		public int left() {
			return Math.max(0, count - progress);
		}

		Quest withProgress(int p) {
			return new Quest(id, kind, item, count, p, reward, posted, poster, deliverTo);
		}
	}

	/** Things a village can always use, how many it asks for and what it pays (emeralds). */
	private record Want(Item item, int count, int reward) {
	}

	private static final List<Want> WANTS = List.of(
		new Want(Items.WHITE_WOOL, 16, 3), new Want(Items.IRON_INGOT, 8, 5), new Want(Items.OAK_LOG, 32, 3), new Want(Items.GLASS, 16, 3),
		new Want(Items.TORCH, 32, 2), new Want(Items.LEATHER, 8, 3), new Want(Items.COAL, 16, 3), new Want(Items.BREAD, 16, 3),
		new Want(Items.BOOK, 4, 4), new Want(Items.GOLD_INGOT, 4, 5));

	/** The hall's daily round: old quests come down, and in the morning a new one goes up if there's room. */
	static void tick(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		long now = level.getGameTime();
		List<Quest> quests = new ArrayList<>(entity.quests());
		boolean changed = quests.removeIf(q -> now - q.posted() >= LASTS);
		long day = level.getDayTime() / 24000;
		if (quests.size() < MAX_OPEN && level.getDayTime() % 24000 < 3000 && entity.lastQuestDay() < day) {
			Quest quest = make(level, hall);
			if (quest != null) {
				quests.add(quest);
				entity.setLastQuestDay(day);
				changed = true;
				announce(level, hall, quest);
			}
		}
		if (changed) {
			entity.setQuests(quests);
		}
	}

	/** A new quest for the village round {@code hall}: what a worker's waiting for, food, monsters, a battle, or a want. */
	@Nullable
	public static Quest make(ServerLevel level, BlockPos hall) {
		long now = level.getGameTime();
		VillageHalls.Census census = VillageHalls.census(level, hall);
		List<Villager> people = census.workers();
		String someone = people.isEmpty() ? "" : name(people.get(level.random.nextInt(people.size())));
		// What a worker is waiting for (plain items only: those can be counted and handed in)
		for (Requests.Request request : census.requests()) {
			if (request.item() != null && request.item() != Items.AIR) {
				int count = Math.min(64, Math.max(1, request.count()));
				return new Quest(UUID.randomUUID(), Kind.BRING, key(request.item()), count, 0, Math.max(2, Math.min(10, 2 + count / 8)), now,
					name(request.worker()), Optional.of(request.station()));
			}
		}
		if (census.food() < 16) {
			return new Quest(UUID.randomUUID(), Kind.BRING, key(Items.BREAD), 16, 0, 4, now, someone, Optional.empty());
		}
		List<Kind> kinds = new ArrayList<>(List.of(Kind.BRING, Kind.SLAY));
		if (io.github.jcondedata.aliveworkplace.trainer.Trainers.COBBLEMON && people.stream().anyMatch(io.github.jcondedata.aliveworkplace.trainer.Trainers::isTrainer)) {
			kinds.add(Kind.BATTLE);
		}
		return switch (kinds.get(level.random.nextInt(kinds.size()))) {
			case SLAY -> new Quest(UUID.randomUUID(), Kind.SLAY, "minecraft:air", MONSTERS, 0, 6, now, someone, Optional.empty());
			case BATTLE -> new Quest(UUID.randomUUID(), Kind.BATTLE, "minecraft:air", 1, 0, 8, now, someone, Optional.empty());
			default -> {
				Want want = WANTS.get(level.random.nextInt(WANTS.size()));
				yield new Quest(UUID.randomUUID(), Kind.BRING, key(want.item()), want.count(), 0, want.reward(), now, someone, Optional.empty());
			}
		};
	}

	private static String key(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	private static String name(Villager villager) {
		return villager.getDisplayName().getString();
	}

	private static void announce(ServerLevel level, BlockPos hall, Quest quest) {
		Component text = Component.translatable("message.aliveworkplace.quest.posted", VillageHalls.name(level, hall), describe(quest))
			.withStyle(ChatFormatting.GOLD);
		for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(hall.getCenter()) < (double) VillageHalls.RADIUS * VillageHalls.RADIUS)) {
			player.displayClientMessage(text, false);
		}
	}

	/** "Bring 16 Bread", "Clear out 8 monsters", "Beat one of the village's trainers". */
	public static Component describe(Quest quest) {
		return switch (quest.kind()) {
			case BRING -> Component.translatable("quest.aliveworkplace.bring", quest.count(), quest.itemType().getDescription());
			case SLAY -> Component.translatable("quest.aliveworkplace.slay", quest.count());
			case BATTLE -> Component.translatable("quest.aliveworkplace.battle");
		};
	}

	/**
	 * {@code player} hands in what they have towards a BRING quest (up to what's left): it goes to the worker who asked,
	 * or the village store. Returns how many were handed in; completes the quest (and pays) when that's the last of it.
	 */
	public static int handIn(ServerPlayer player, BlockPos hall, UUID questId) {
		ServerLevel level = player.serverLevel();
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return 0;
		}
		Quest quest = entity.quests().stream().filter(q -> q.id().equals(questId)).findFirst().orElse(null);
		if (quest == null || quest.kind() != Kind.BRING) {
			return 0;
		}
		Item item = quest.itemType();
		List<BlockPos> chests = quest.deliverTo().map(p -> SupplyContainers.find(level, p, null)).filter(l -> !l.isEmpty())
			.orElseGet(() -> VillageNeeds.store(level, hall));
		Inventory inventory = player.getInventory();
		int given = 0;
		for (int i = 0; i < inventory.getContainerSize() && given < quest.left(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.is(item)) {
				continue;
			}
			ItemStack part = stack.split(Math.min(stack.getCount(), quest.left() - given));
			given += part.getCount();
			ItemStack rest = chests.isEmpty() ? part : SupplyContainers.insert(level, chests, part);
			if (!rest.isEmpty()) {
				Block.popResource(level, hall.above(), rest);
			}
		}
		if (given > 0) {
			inventory.setChanged();
			progress(level, hall, entity, quest, given, player);
		}
		return given;
	}

	/** Counts {@code amount} towards a quest; when it's done, pays {@code player} and takes it down. */
	static void progress(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Quest quest, int amount, @Nullable ServerPlayer player) {
		List<Quest> quests = new ArrayList<>(entity.quests());
		int index = quests.indexOf(quest);
		if (index < 0) {
			return;
		}
		Quest now = quest.withProgress(quest.progress() + amount);
		if (now.left() > 0) {
			quests.set(index, now);
			entity.setQuests(quests);
			return;
		}
		quests.remove(index);
		entity.setQuests(quests);
		entity.questDone();
		Chronicle.record(level, hall, Chronicle.Kind.QUEST, Component.translatable("chronicle.aliveworkplace.quest",
			player != null ? player.getDisplayName() : Component.translatable("chronicle.aliveworkplace.someone"), describe(quest)), true);
		if (player != null) {
			Money.pay(player, (long) quest.reward() * Money.DOLLARS_PER_EMERALD, quest.reward());
			player.displayClientMessage(Component.translatable("message.aliveworkplace.quest.done", describe(quest), Money.describe(
				(long) quest.reward() * Money.DOLLARS_PER_EMERALD, quest.reward())).withStyle(ChatFormatting.GREEN), false);
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.3f);
		}
	}

	/** A player killed a monster: it counts towards the nearest village's clearing-out quest. */
	public static void onKill(ServerLevel level, LivingEntity killed, DamageSource source) {
		if (!(killed instanceof Enemy) || !(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		count(level, killed.blockPosition(), Kind.SLAY, player);
	}

	/** A player beat a village trainer: it counts towards that village's battle quest. */
	public static void onTrainerBeaten(ServerLevel level, Villager trainer, ServerPlayer player) {
		count(level, trainer.blockPosition(), Kind.BATTLE, player);
	}

	private static void count(ServerLevel level, BlockPos where, Kind kind, ServerPlayer player) {
		VillageHalls.nearest(level, where).ifPresent(hall -> {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
				entity.quests().stream().filter(q -> q.kind() == kind).findFirst()
					.ifPresent(q -> progress(level, hall, entity, q, 1, player));
			}
		});
	}

	private VillageQuests() {
	}
}
