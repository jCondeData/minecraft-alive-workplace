package io.github.jcondedata.aliveworkplace.colony;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Colonies (ROADMAP 33.8, design note M33): a City ({@link #RANK}) founds a sister village. This is the charter's
 * part: the Trade page's Colonies tab sells the hall's owner (their friends, operators) a
 * {@link ColonyCharterItem Colony Charter} for {@link #COST} emeralds, taken from the village's treasury first and the
 * rest from the buyer; the charter's map ({@link Open}) and a click on it ({@link Choose}) or on the ground choose the
 * spot ({@link #choose}), which must lie in the ring round the hall and clear of other halls ({@link ColonyMap}).
 * A village has one colony on the road at a time, waits {@link #COOLDOWN_DAYS} days between colonies and founds
 * {@link #PER_VILLAGE} at most ({@link #limit}); the orders and counts are kept in {@link RealmData}, which 33.9's
 * settlers and 33.10's founding fill. Off ({@code colonies}): no tab, no charters sold, and a charter chooses nothing.
 *
 * <p>The map is drawn on the server thread from chunks that are loaded already ({@link VillageMaps#colors}, which
 * skips every column whose chunk isn't): nothing is loaded or generated for it, and land the server doesn't have
 * stays blank for the screen to show as parchment.
 */
public final class Colonies {
	/** The {@code colonies} switch (with M33's gate). */
	public static boolean ENABLED = true;
	/** The rank a village needs to found a colony ({@code colonyRank}). */
	public static VillageRanks.Rank RANK = VillageRanks.Rank.CITY;
	/** Days between a village's colonies ({@code colonyCooldownDays}). */
	public static int COOLDOWN_DAYS = 7;
	/** Colonies a village may found ({@code coloniesPerVillage}). */
	public static int PER_VILLAGE = 3;
	/** What a charter costs, in emeralds. */
	public static final int COST = 32;

	/** The tab's slots: the charter for sale, the village's count, and the order on the road; the colonies founded fill the rows under them. */
	public static final int BUY = VillageHallScreen.FIRST_ROW + 1;
	public static final int COUNT = VillageHallScreen.FIRST_ROW + 4;
	public static final int ON_ROAD = VillageHallScreen.FIRST_ROW + 6;
	public static final int FOUNDED = VillageHallScreen.FIRST_ROW + 9;

	/** Why a charter isn't sold. */
	public enum Refusal {
		OFF, NO_HALL, NO_OWNER, NOT_YOURS, RANK, ON_ROAD, COOLDOWN, CAP, MONEY
	}

	/** What came of buying a charter: the refusal (null: bought), what to tell the player, and who paid how many emeralds. */
	public record Bought(@Nullable Refusal refusal, Component message, ItemStack charter, int fromTreasury, int fromPlayer) {
		public boolean ok() {
			return refusal == null;
		}
	}

	/** What came of choosing a spot: whether it was kept on the charter, the spot (or null), and what to tell the player. */
	public record Chosen(boolean ok, @Nullable BlockPos spot, Component message) {
	}

	/** A village on the charter's map: its hall and its name. */
	public record Mark(BlockPos hall, Component name) {
	}

	/** Opens the charter's map screen: the land round the hall as map colours (0: not loaded), every village's hall on it, and the spot chosen so far. */
	public record Open(boolean mainHand, BlockPos hall, Component village, byte[] colors, List<Mark> marks, Optional<BlockPos> spot)
		implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(AliveWorkplace.id("colony_charter_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of((buf, o) -> {
			buf.writeBoolean(o.mainHand());
			buf.writeBlockPos(o.hall());
			ComponentSerialization.STREAM_CODEC.encode(buf, o.village());
			buf.writeByteArray(o.colors());
			buf.writeVarInt(o.marks().size());
			for (Mark m : o.marks()) {
				buf.writeBlockPos(m.hall());
				ComponentSerialization.STREAM_CODEC.encode(buf, m.name());
			}
			buf.writeOptional(o.spot(), (b, p) -> b.writeBlockPos(p));
		}, buf -> {
			boolean mainHand = buf.readBoolean();
			BlockPos hall = buf.readBlockPos();
			Component village = ComponentSerialization.STREAM_CODEC.decode(buf);
			byte[] colors = buf.readByteArray(ColonyMap.PIXELS * ColonyMap.PIXELS);
			int n = buf.readVarInt();
			List<Mark> marks = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				marks.add(new Mark(buf.readBlockPos(), ComponentSerialization.STREAM_CODEC.decode(buf)));
			}
			return new Open(mainHand, hall, village, colors, marks, buf.readOptional(b -> b.readBlockPos()));
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A click on the charter's map: the charter in that hand should go to the column at {@code x}, {@code z}. */
	public record Choose(boolean mainHand, int x, int z) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = new Type<>(AliveWorkplace.id("colony_charter_choose"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = StreamCodec.of((buf, o) -> {
			buf.writeBoolean(o.mainHand());
			buf.writeInt(o.x());
			buf.writeInt(o.z());
		}, buf -> new Choose(buf.readBoolean(), buf.readInt(), buf.readInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The server's answer to a click: the charter's spot now, and what the screen says under the map. */
	public record Answer(boolean ok, Optional<BlockPos> spot, Component message) implements CustomPacketPayload {
		public static final Type<Answer> TYPE = new Type<>(AliveWorkplace.id("colony_charter_answer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = StreamCodec.of((buf, o) -> {
			buf.writeBoolean(o.ok());
			buf.writeOptional(o.spot(), (b, p) -> b.writeBlockPos(p));
			ComponentSerialization.STREAM_CODEC.encode(buf, o.message());
		}, buf -> new Answer(buf.readBoolean(), buf.readOptional(b -> b.readBlockPos()), ComponentSerialization.STREAM_CODEC.decode(buf)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		Platform.get().clientbound(Open.TYPE, Open.CODEC);
		Platform.get().clientbound(Answer.TYPE, Answer.CODEC);
		Platform.get().serverbound(Choose.TYPE, Choose.CODEC, Colonies::clicked);
		TradePage.register(TradePage.Tab.COLONIES, Colonies::tabIcon, Colonies::tab, () -> ENABLED);
		Settlers.init();
	}

	// ---- buying the charter ---------------------------------------------------------------------------------------------

	/** The rank the village at {@code hall} needs to found a colony (M29's Founder lowers it there). */
	public static VillageRanks.Rank rankNeeded(ServerLevel level, BlockPos hall) {
		return RANK;
	}

	/** Days until the village at {@code hall} may send its next colony (0: now). */
	public static long cooldownLeft(ServerLevel level, BlockPos hall) {
		RealmData.Cooldown count = RealmData.get(level.getServer()).cooldown(GlobalPos.of(level.dimension(), hall));
		if (count.founded() <= 0) {
			return 0;
		}
		return Math.max(0, COOLDOWN_DAYS - (Chronicle.day(level) - count.lastDay()));
	}

	/**
	 * What keeps the village at {@code hall} from founding a colony now, whoever asks: the switch, its rank, a colony on
	 * the road, the wait since the last one, or the most a village may found. Null: it may.
	 */
	@Nullable
	public static Refusal limit(ServerLevel level, BlockPos hall) {
		if (!ENABLED) {
			return Refusal.OFF;
		}
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Refusal.NO_HALL;
		}
		if (entity.rank().ordinal() < rankNeeded(level, hall).ordinal()) {
			return Refusal.RANK;
		}
		RealmData data = RealmData.get(level.getServer());
		GlobalPos mother = GlobalPos.of(level.dimension(), hall);
		if (data.orderOf(mother) != null) {
			return Refusal.ON_ROAD;
		}
		if (data.cooldown(mother).founded() >= PER_VILLAGE) {
			return Refusal.CAP;
		}
		return cooldownLeft(level, hall) > 0 ? Refusal.COOLDOWN : null;
	}

	/** Whole emeralds of a charter's price the treasury of {@code entity} pays: all it has, up to the price. */
	public static int treasuryShare(VillageHallBlockEntity entity) {
		return Math.max(0, Math.min(COST, entity.treasury() / 100));
	}

	/**
	 * {@code player} buys a Colony Charter of the village at {@code hall} (the Colonies tab's button): refused unless they
	 * rule the village ({@link VillageProtection#mayRule}), it may found a colony ({@link #limit}) and the treasury and
	 * the player together have the price. The treasury pays what it has, the player the rest (nothing in creative, and
	 * the message says so).
	 */
	public static Bought buy(ServerLevel level, BlockPos hall, ServerPlayer player) {
		Refusal refusal = limit(level, hall);
		VillageHallBlockEntity entity = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		if (refusal == null || (refusal != Refusal.OFF && refusal != Refusal.NO_HALL)) {
			if (entity.owner() == null) {
				refusal = Refusal.NO_OWNER;
			} else if (!VillageProtection.mayRule(level, entity, player)) {
				refusal = Refusal.NOT_YOURS;
			}
		}
		int fromTreasury = entity == null ? 0 : treasuryShare(entity);
		boolean free = player.getAbilities().instabuild; // creative: the treasury still pays its share, the rest costs nothing
		int fromPlayer = free ? 0 : COST - fromTreasury;
		if (refusal == null && fromPlayer > 0 && !Money.canAfford(player, (long) fromPlayer * Money.DOLLARS_PER_EMERALD, fromPlayer)) {
			refusal = Refusal.MONEY;
		}
		if (refusal == null && fromPlayer > 0 && !Money.charge(player, (long) fromPlayer * Money.DOLLARS_PER_EMERALD, fromPlayer)) {
			refusal = Refusal.MONEY;
		}
		if (refusal != null) {
			return new Bought(refusal, refusalMessage(level, hall, refusal, entity, player).copy().withStyle(ChatFormatting.RED), ItemStack.EMPTY, 0, 0);
		}
		entity.setTreasury(entity.treasury() - fromTreasury * 100);
		ItemStack charter = ColonyCharterItem.of(level, hall);
		ItemStack given = charter.copy();
		if (!player.getInventory().add(given)) {
			player.drop(given, false);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1f, 1f);
		Component village = VillageHalls.name(level, hall);
		Component message = free && fromTreasury < COST ? Component.translatable("message.aliveworkplace.colony.bought_creative", village, money(fromTreasury))
			: fromPlayer == 0 ? Component.translatable("message.aliveworkplace.colony.bought_treasury", village, money(fromTreasury))
			: fromTreasury == 0 ? Component.translatable("message.aliveworkplace.colony.bought_player", village, money(fromPlayer))
			: Component.translatable("message.aliveworkplace.colony.bought_both", village, money(fromTreasury), money(fromPlayer));
		return new Bought(null, message.copy().withStyle(ChatFormatting.GREEN), charter, fromTreasury, fromPlayer);
	}

	/** "32 emeralds", or their worth in CobbleDollars with the pack. */
	static Component money(int emeralds) {
		return Money.describe((long) emeralds * Money.DOLLARS_PER_EMERALD, emeralds);
	}

	/** Why the village at {@code hall} sells {@code player} no charter, in words. */
	static Component refusalMessage(ServerLevel level, BlockPos hall, Refusal refusal, @Nullable VillageHallBlockEntity entity, @Nullable ServerPlayer player) {
		Component village = VillageHalls.name(level, hall);
		return switch (refusal) {
			case OFF -> Component.translatable("message.aliveworkplace.colony.off");
			case NO_HALL -> Component.translatable("message.aliveworkplace.colony.hall_gone", village);
			case NO_OWNER -> Component.translatable("message.aliveworkplace.colony.no_owner", village);
			case NOT_YOURS -> Component.translatable("message.aliveworkplace.colony.not_yours", village, entity == null ? "" : entity.ownerName());
			case RANK -> Component.translatable("message.aliveworkplace.colony.rank", village, rankNeeded(level, hall).title(),
				entity == null ? VillageRanks.Rank.HAMLET.title() : entity.rank().title());
			case ON_ROAD -> Component.translatable("message.aliveworkplace.colony.on_road", village);
			case COOLDOWN -> Component.translatable("message.aliveworkplace.colony.cooldown", village, cooldownLeft(level, hall));
			case CAP -> Component.translatable("message.aliveworkplace.colony.cap", village, PER_VILLAGE);
			case MONEY -> {
				int fromTreasury = entity == null ? 0 : treasuryShare(entity);
				yield Component.translatable("message.aliveworkplace.colony.money", money(COST), money(fromTreasury), money(COST - fromTreasury));
			}
		};
	}

	// ---- the spot ------------------------------------------------------------------------------------------------------

	/** The halls of the other villages in {@code level} (those the caravans know: every hall that has had its round). */
	public static List<Mark> otherVillages(ServerLevel level, BlockPos hall) {
		List<Mark> out = new ArrayList<>();
		for (Caravans.Village v : Caravans.Data.get(level).villages()) {
			if (!v.hall().equals(hall)) {
				out.add(new Mark(v.hall(), v.name()));
			}
		}
		return out;
	}

	/**
	 * Why the charter in {@code stack} can't be used by {@code player} here, or null: it isn't bound, colonies are off,
	 * its village is in another dimension, or its hall is gone (seen only when the hall's chunk is loaded; far from
	 * home the charter is taken at its word).
	 */
	@Nullable
	static Component unusable(ServerPlayer player, ItemStack stack) {
		ColonyCharterItem.Charter charter = stack.get(ModComponents.COLONY_CHARTER);
		if (charter == null) {
			return Component.translatable("message.aliveworkplace.colony.unbound").withStyle(ChatFormatting.YELLOW);
		}
		if (!ENABLED) {
			return Component.translatable("message.aliveworkplace.colony.off").withStyle(ChatFormatting.YELLOW);
		}
		ServerLevel level = Players.level(player);
		if (!charter.hall().dimension().equals(level.dimension())) {
			return Component.translatable("message.aliveworkplace.colony.other_world", charter.name()).withStyle(ChatFormatting.YELLOW);
		}
		BlockPos hall = charter.hall().pos();
		if (level.isLoaded(hall) && !level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
			return Component.translatable("message.aliveworkplace.colony.hall_gone", charter.name()).withStyle(ChatFormatting.RED);
		}
		return null;
	}

	/**
	 * Chooses the column of {@code at} as the spot of the charter in {@code stack} for {@code player} standing there (a
	 * right-click on the ground): kept on the charter if it lies in the ring and clear of other halls.
	 */
	public static Chosen choose(ServerPlayer player, ItemStack stack, BlockPos at) {
		return choose(player, stack, at.getX(), at.getZ(), at.getY());
	}

	/** The same for a click on the map: the spot's height is the ground's there if the server has it loaded, the hall's otherwise. */
	public static Chosen choose(ServerPlayer player, ItemStack stack, int x, int z) {
		return choose(player, stack, x, z, null);
	}

	private static Chosen choose(ServerPlayer player, ItemStack stack, int x, int z, @Nullable Integer y) {
		Component problem = unusable(player, stack);
		if (problem != null) {
			return new Chosen(false, null, problem);
		}
		ColonyCharterItem.Charter charter = stack.get(ModComponents.COLONY_CHARTER);
		ServerLevel level = Players.level(player);
		BlockPos hall = charter.hall().pos();
		List<Mark> others = otherVillages(level, hall);
		List<BlockPos> halls = others.stream().map(Mark::hall).toList();
		ColonyMap.Verdict verdict = ColonyMap.verdict(hall, x, z, halls);
		if (verdict != ColonyMap.Verdict.OK) {
			Component why = switch (verdict) {
				case TOO_NEAR -> Component.translatable("message.aliveworkplace.colony.too_near", charter.name(), ColonyMap.MIN,
					(int) Math.round(ColonyMap.distance(hall, x, z)));
				case TOO_FAR -> Component.translatable("message.aliveworkplace.colony.too_far", charter.name(), ColonyMap.MAX,
					(int) Math.round(ColonyMap.distance(hall, x, z)));
				default -> {
					BlockPos near = ColonyMap.hallTooNear(x, z, halls);
					Component name = others.stream().filter(m -> m.hall().equals(near)).map(Mark::name).findFirst().orElse(Component.empty());
					yield Component.translatable("message.aliveworkplace.colony.near_hall", name, ColonyMap.CLEAR);
				}
			};
			return new Chosen(false, null, why.copy().withStyle(ChatFormatting.RED));
		}
		int height = y != null ? y : level.hasChunk(x >> 4, z >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : hall.getY();
		BlockPos spot = new BlockPos(x, height, z);
		stack.set(ModComponents.COLONY_CHARTER, charter.withSpot(spot));
		level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.8f, 1.2f);
		return new Chosen(true, spot, Component.translatable("message.aliveworkplace.colony.chosen", VillageHallScreen.where(hall, spot), charter.name())
			.withStyle(ChatFormatting.GREEN));
	}

	/** Everything the map screen of the charter in {@code stack} shows, or null when the charter isn't bound. */
	@Nullable
	public static Open screen(ServerLevel level, ItemStack stack, boolean mainHand) {
		ColonyCharterItem.Charter charter = stack.get(ModComponents.COLONY_CHARTER);
		if (charter == null) {
			return null;
		}
		BlockPos hall = charter.hall().pos();
		byte[] colors = VillageMaps.colors(level, hall, ColonyMap.HALF, ColonyMap.STEP);
		List<Mark> marks = new ArrayList<>();
		for (Mark m : otherVillages(level, hall)) {
			if (ColonyMap.onMap(hall, m.hall().getX(), m.hall().getZ())) {
				marks.add(m);
			}
		}
		return new Open(mainHand, hall, charter.name(), colors, marks, charter.spot());
	}

	/** Right-clicked in the air: the map screen of the charter in {@code hand}, or why not over the hotbar. */
	public static void open(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Component problem = unusable(player, stack);
		if (problem != null) {
			Chat.actionBar(player, problem);
			return;
		}
		if (!Platform.get().canSend(player, Open.TYPE)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.colony.no_screen").withStyle(ChatFormatting.YELLOW));
			return;
		}
		Platform.get().send(player, screen(Players.level(player), stack, hand == InteractionHand.MAIN_HAND));
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 0.9f);
	}

	/** A click on the map screen (the {@link Choose} packet): chooses the spot on the charter the player holds, and answers. */
	public static Chosen clicked(Choose choose, ServerPlayer player) {
		ItemStack stack = player.getItemInHand(choose.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
		Chosen chosen;
		if (!stack.is(ModItems.COLONY_CHARTER)) {
			chosen = new Chosen(false, null, Component.translatable("message.aliveworkplace.colony.unbound").withStyle(ChatFormatting.YELLOW));
		} else {
			chosen = choose(player, stack, choose.x(), choose.z());
		}
		ColonyCharterItem.Charter charter = stack.get(ModComponents.COLONY_CHARTER);
		if (Platform.get().canSend(player, Answer.TYPE)) {
			Platform.get().send(player, new Answer(chosen.ok(), charter == null ? Optional.empty() : charter.spot(), chosen.message()));
		}
		return chosen;
	}

	// ---- the Colonies tab ---------------------------------------------------------------------------------------------

	/** The tab's icon: a filled map, with how colonies work. */
	static ItemStack tabIcon(ServerLevel level, BlockPos hall) {
		return VillageHallScreen.icon(Items.FILLED_MAP, Component.translatable("screen.aliveworkplace.colonies.title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.colonies.about", rankNeeded(level, hall).title()), ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.colonies.about_spot", ColonyMap.MIN, ColonyMap.MAX), ChatFormatting.GRAY));
	}

	/** The tab: the charter for sale, the village's count and wait, the order on the road, and the colonies it founded. */
	static void tab(ChoiceMenu menu, ServerLevel level, BlockPos hall, @Nullable ServerPlayer viewer, Runnable again) {
		VillageHallBlockEntity entity = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		Component village = VillageHalls.name(level, hall);
		RealmData data = RealmData.get(level.getServer());
		GlobalPos mother = GlobalPos.of(level.dimension(), hall);

		List<Component> lore = new ArrayList<>();
		int fromTreasury = entity == null ? 0 : treasuryShare(entity);
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.colonies.cost", money(COST)), ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line(fromTreasury >= COST ? Component.translatable("screen.aliveworkplace.colonies.cost_treasury")
			: viewer != null && viewer.getAbilities().instabuild ? Component.translatable("screen.aliveworkplace.colonies.cost_creative", money(fromTreasury))
			: Component.translatable("screen.aliveworkplace.colonies.cost_split", money(fromTreasury), money(COST - fromTreasury)), ChatFormatting.GRAY));
		Refusal limit = limit(level, hall);
		if (entity != null && limit == null && entity.owner() == null) {
			limit = Refusal.NO_OWNER;
		} else if (entity != null && limit == null && viewer != null && !VillageProtection.mayRule(level, entity, viewer)) {
			limit = Refusal.NOT_YOURS;
		}
		if (limit != null) {
			lore.add(VillageHallScreen.line(refusalMessage(level, hall, limit, entity, viewer), ChatFormatting.RED));
		} else {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.colonies.buy_how", ChatFormatting.DARK_GRAY));
			lore.add(VillageHallScreen.line("screen.aliveworkplace.colonies.buy_then", ChatFormatting.DARK_GRAY));
		}
		menu.button(BUY, VillageHallScreen.icon(new ItemStack(ModItems.COLONY_CHARTER), Component.translatable("screen.aliveworkplace.colonies.buy"),
			limit == null ? ChatFormatting.GREEN : ChatFormatting.GRAY, lore.toArray(Component[]::new)), p -> {
			Bought bought = buy(level, hall, p);
			if (!bought.ok()) {
				level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1f);
			}
			Chat.chat(p, bought.message());
			again.run();
		});

		RealmData.Cooldown count = data.cooldown(mother);
		long wait = cooldownLeft(level, hall);
		menu.button(COUNT, VillageHallScreen.icon(Items.CLOCK, Component.translatable("screen.aliveworkplace.colonies.count", count.founded(), PER_VILLAGE),
			ChatFormatting.WHITE,
			VillageHallScreen.line(wait > 0 ? Component.translatable("screen.aliveworkplace.colonies.wait", wait)
				: Component.translatable("screen.aliveworkplace.colonies.no_wait"), wait > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.colonies.wait_about", COOLDOWN_DAYS), ChatFormatting.DARK_GRAY)), null);

		RealmData.Order order = data.orderOf(mother);
		if (order != null) {
			Component name = order.name().orElse(Component.translatable("screen.aliveworkplace.colonies.unnamed"));
			// 33.9: where the order has got to ("On the road to Newbrook, there in 2 minutes"), who goes and what they carry
			List<Component> about = new ArrayList<>();
			about.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.colonies.on_road_where", VillageHallScreen.where(hall, order.spot()), village),
				ChatFormatting.GRAY));
			about.add(VillageHallScreen.line(Settlers.status(level, order), ChatFormatting.GOLD));
			about.addAll(Settlers.lines(level, order));
			menu.button(ON_ROAD, VillageHallScreen.icon(Items.LEATHER_BOOTS, Component.translatable("screen.aliveworkplace.colonies.on_road", name),
				ChatFormatting.YELLOW, about.toArray(Component[]::new)), null);
			if (!order.gone()) {
				boolean may = entity != null && viewer != null && VillageProtection.mayRule(level, entity, viewer);
				menu.button(Settlers.CALL_OFF, VillageHallScreen.icon(Items.BARRIER, Component.translatable("screen.aliveworkplace.colonies.call_off"),
					may ? ChatFormatting.RED : ChatFormatting.GRAY,
					VillageHallScreen.line(may ? Component.translatable("screen.aliveworkplace.colonies.call_off_how")
						: Component.translatable("screen.aliveworkplace.colonies.call_off_owner", entity == null ? "" : entity.ownerName()), ChatFormatting.GRAY)), p -> {
					VillageHallBlockEntity now = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
					if (now == null || !VillageProtection.mayRule(level, now, p)) {
						level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1f);
						Chat.chat(p, Component.translatable("message.aliveworkplace.colony.call_off_not_yours", village, now == null ? "" : now.ownerName())
							.withStyle(ChatFormatting.RED));
					} else if (Settlers.callOff(level, hall, p)) {
						Chat.chat(p, Component.translatable("message.aliveworkplace.colony.called_off", name, village).withStyle(ChatFormatting.YELLOW));
					} else {
						Chat.chat(p, Component.translatable("message.aliveworkplace.colony.too_late", name).withStyle(ChatFormatting.RED));
					}
					again.run();
				});
			}
		}

		int slot = FOUNDED;
		for (RealmData.Founded f : data.foundedBy(mother)) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			ServerLevel there = level.getServer().getLevel(f.hall().dimension());
			Caravans.Village known = there == null ? null : Caravans.Data.get(there).village(f.hall().pos());
			Component name = known != null ? known.name() : VillageHalls.madeUpName(f.hall().pos());
			menu.button(slot++, VillageHallScreen.icon(Items.WHITE_BANNER, name, ChatFormatting.WHITE,
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.colonies.founded_day", f.day()), ChatFormatting.GRAY),
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.colonies.founded_where", VillageHallScreen.where(hall, f.hall().pos()), village),
					ChatFormatting.GRAY)), null);
		}
	}

	/** Whether the "What next?" page should suggest a colony for the village at {@code hall}: it could found one now. */
	public static boolean suggest(ServerLevel level, BlockPos hall) {
		return limit(level, hall) == null;
	}

	private Colonies() {
	}
}
