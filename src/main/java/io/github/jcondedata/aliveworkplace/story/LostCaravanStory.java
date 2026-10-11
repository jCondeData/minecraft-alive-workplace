package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.people.Names;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * The Lost Caravan's story (ROADMAP 31.20), what {@code arcs/lost_caravan.json} can't say in effects alone: a caravan
 * on a long road going missing ({@link #lose}, called as it sets off), its goods held by the arc, the wreck found, the
 * carter caged in the raiders' stockade with the goods in its chest, the carter following whoever opened his cage and
 * walking into a village, the lost goods delivered ({@code deliver_goods}), the ending (the carter joins the village he
 * reached at Journeyman) and his finding his own way home after {@link #RESCUE_DAYS} days. What it keeps is in the arc's
 * flags, roles and {@link ArcState#extra}, so it carries on after a restart.
 */
public final class LostCaravanStory {
	public static final ResourceLocation ARC = AliveWorkplace.id("lost_caravan");
	/** A caravan on a road at least this long may go missing. */
	public static final int LONG_ROAD = 300;
	/** One caravan in this many on a long road goes missing (config {@code lostCaravanChance}); 0: none. */
	public static int CHANCE = 20;
	/** Days the carter waits for a rescue before he finds his own way home. */
	public static final int RESCUE_DAYS = 7;
	/** How near the wreck a player finds it, and how near a village the carter has reached it. */
	public static final int FIND = 12;
	public static final int REACH = 24;
	/** How near the open cage the player who opened it stands. */
	static final int FREE_RANGE = 8;
	/** The carter catches up from this far, and waits further than this (as a rallied guard, {@code guard/Escorts}). */
	public static final double CATCH_UP = 32;
	public static final double WAIT = 64;
	/** The level he joins at: Journeyman. */
	public static final int JOURNEYMAN = 3;
	/** The delivery's standing in the receiving village. */
	public static final int DELIVERY_REPUTATION = 50;
	public static final int ENDING_REPUTATION = 150;
	public static final int FRIENDSHIP = 100;
	/** Place keys, flags and the carter's role. */
	public static final String WRECK = "wreck";
	public static final String STOCKADE = "stockade";
	public static final String WRECK_FOUND = "wreck_found";
	public static final String TOBIN_HOME = "tobin_home";
	public static final String CARTER = "carter";
	public static final ResourceLocation GOODS_QUEST = AliveWorkplace.id("arc/lost_caravan/the_goods");
	/** {@link ArcState#extra} keys. */
	static final String TO = "to";
	static final String GOODS = "goods";
	static final String SET = "carter_set";
	static final String LEADER = "leader";
	static final String HOME = "home";
	static final String DELIVERED = "delivered";

	/** The caravan going missing now (read by the arc's first effect, {@code overdue}); null otherwise. */
	@Nullable
	private static Pending pending;

	record Pending(BlockPos from, BlockPos to, List<ItemStack> goods) {
	}

	public static void init() {
		Platform.get().onServerTick(server -> {
			if (server.getTickCount() % 10 == 3) {
				for (ServerLevel level : server.getAllLevels()) {
					tick(level);
				}
			}
		});
	}

	static void validate(String step) {
		if (!List.of("overdue", "ending").contains(step)) {
			throw new IllegalArgumentException("lost_caravan: 'step' must be overdue or ending");
		}
	}

	/** One running Lost Caravan: its sending village and state. */
	public record Running(BlockPos hall, Stories.Entry entry, ArcState state) {
	}

	public static List<Running> running(ServerLevel level) {
		List<Running> out = new ArrayList<>();
		for (Map.Entry<BlockPos, Stories.Entry> en : List.copyOf(Stories.Data.get(level).halls.entrySet())) {
			ArcState s = en.getValue().arc;
			if (s != null && s.id.equals(ARC.toString())) {
				out.add(new Running(en.getKey(), en.getValue(), s));
			}
		}
		return out;
	}

	// ---- going missing ---------------------------------------------------------------------------------------------

	/**
	 * A caravan from {@code from} to {@code to} with {@code goods} sets off: on a road longer than {@link #LONG_ROAD}
	 * blocks, one in {@link #CHANCE} goes missing when neither village has an arc running (and the arc can start). True
	 * when it did: the goods are the arc's now and the caravan isn't put on the road.
	 */
	public static boolean lose(ServerLevel level, BlockPos from, BlockPos to, List<ItemStack> goods, RandomSource random) {
		if (CHANCE <= 0 || !Arcs.ENABLED || goods.isEmpty() || from.distSqr(to) <= (double) LONG_ROAD * LONG_ROAD
			|| Arcs.running(level, from) != null || Arcs.running(level, to) != null || Arcs.runningCount(level.getServer()) >= Arcs.AT_ONCE) {
			return false;
		}
		Arcs.Arc arc = Arcs.get(ARC).orElse(null);
		if (arc == null || Arcs.disabled(ARC) || random.nextInt(CHANCE) != 0) {
			return false;
		}
		pending = new Pending(from.immutable(), to.immutable(), List.copyOf(goods));
		try {
			return Arcs.start(level, from, arc) != null;
		} finally {
			pending = null;
		}
	}

	/** An arc effect {@code lost_caravan}: {@code overdue} or {@code ending}. */
	static void effect(ServerLevel level, BlockPos hall, ArcState s, String step) {
		switch (step) {
			case "overdue" -> overdue(level, hall, s);
			case "ending" -> ending(level, hall, s);
			default -> {
			}
		}
	}

	/** The caravan doesn't arrive: the arc holds its goods, and both halls are told. */
	static void overdue(ServerLevel level, BlockPos hall, ArcState s) {
		Pending p = pending;
		if (p != null && p.from().equals(hall) && !s.extra.contains(TO)) {
			hold(s, p.to(), p.goods());
			Stories.Data.get(level).setDirty();
		}
		BlockPos to = to(s);
		if (to == null) {
			return;
		}
		Component message = Component.translatable("message.aliveworkplace.lost_caravan.overdue", villageName(level, hall), villageName(level, to));
		for (BlockPos at : List.of(hall, to)) {
			tell(level, at, message.copy().withStyle(ChatFormatting.GOLD));
			Chronicle.atHall(level, at, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.lost_caravan.overdue",
				villageName(level, hall), villageName(level, to), Caravans.describe(goods(s))));
		}
	}

	/** Gives {@code s} a lost caravan's destination and goods (as {@link #lose} does through the arc's first effect). */
	public static void hold(ArcState s, BlockPos to, List<ItemStack> goods) {
		s.extra.putLong(TO, to.asLong());
		s.extra.put(GOODS, saveGoods(goods));
	}

	/** The village the caravan was going to; null for an arc started without one (the operators' command). */
	@Nullable
	public static BlockPos to(ArcState s) {
		return s.extra.contains(TO) ? BlockPos.of(Nbt.getLong(s.extra, TO)) : null;
	}

	/** The goods the arc holds (the lost shipment's own). */
	public static List<ItemStack> goods(ArcState s) {
		List<ItemStack> out = new ArrayList<>();
		ListTag list = Nbt.getList(s.extra, GOODS, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag c = Nbt.compoundAt(list, i);
			ResourceLocation id = ResourceLocation.tryParse(Nbt.getString(c, "id"));
			Item item = id == null ? null : Lookup.value(BuiltInRegistries.ITEM, id);
			int count = Nbt.getInt(c, "count");
			if (item != null && count > 0) {
				out.add(new ItemStack(item, count));
			}
		}
		return out;
	}

	/** Goods as the arc saves them: each stack's item and count (what a caravan carries has no data of its own). */
	static ListTag saveGoods(List<ItemStack> goods) {
		ListTag list = new ListTag();
		for (ItemStack stack : goods) {
			if (stack.isEmpty()) {
				continue;
			}
			CompoundTag c = new CompoundTag();
			c.putString("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
			c.putInt("count", stack.getCount());
			list.add(c);
		}
		return list;
	}

	static Component villageName(ServerLevel level, BlockPos hall) {
		Caravans.Village v = Caravans.Data.get(level).village(hall);
		return v != null ? v.name() : VillageHalls.name(level, hall);
	}

	private static void tell(ServerLevel level, BlockPos hall, Component message) {
		for (ServerPlayer player : ArcEffects.audience(level, hall)) {
			Chat.chat(player, message);
		}
	}

	// ---- the stockade: the carter and the goods --------------------------------------------------------------------

	/** A place of the arc was built: the stockade gets the carter in its cage and the goods in its chest. */
	static void built(ServerLevel level, ArcState s, ArcState.ArcPlace p) {
		if (s.id.equals(ARC.toString()) && p.key.equals(STOCKADE) && p.placed == 1) {
			setUp(level, s, p);
		}
	}

	/** The stockade stands: its chest holds the lost goods, and the carter (a villager of the sending village, named) is locked in the cage. */
	@Nullable
	public static Villager setUp(ServerLevel level, ArcState s, ArcState.ArcPlace p) {
		if (Nbt.getBoolean(s.extra, SET)) {
			return carter(level, s);
		}
		s.extra.putBoolean(SET, true);
		Stories.Data.get(level).setDirty();
		BlockPos chest = find(level, p.spot, st -> st.is(Blocks.CHEST));
		if (chest != null && level.getBlockEntity(chest) instanceof Container box) {
			if (box instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity r) {
				r.setLootTable(null); // the bandits' own loot isn't rolled: the chest holds the goods
			}
			box.clearContent();
			List<ItemStack> goods = goods(s);
			for (int i = 0; i < goods.size() && i < box.getContainerSize(); i++) {
				box.setItem(i, goods.get(i).copy());
			}
			box.setChanged();
		}
		BlockPos door = find(level, p.spot, st -> st.is(Blocks.IRON_DOOR) && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER);
		BlockPos hay = door == null ? null : find(level, door, st -> st.is(Blocks.HAY_BLOCK), 4);
		BlockPos inside = p.spot;
		if (door != null && hay != null) {
			int dx = hay.getX() - door.getX();
			int dz = hay.getZ() - door.getZ();
			inside = Math.abs(dz) >= Math.abs(dx) ? door.offset(0, 0, 2 * Integer.signum(dz)) : door.offset(2 * Integer.signum(dx), 0, 0);
		}
		Villager v = EntityType.VILLAGER.create(level);
		if (v == null) {
			return null;
		}
		v.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		v.finalizeSpawn(level, level.getCurrentDifficultyAt(inside), MobSpawnType.EVENT, null);
		v.setBaby(false);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		Component name = Names.name(level.random.nextInt(Names.COUNT));
		v.setCustomName(name);
		v.setPersistenceRequired();
		v.addTag(ArcEffects.arcTag(s.id));
		v.addTag(ArcEffects.roleTag(CARTER));
		v.restrictTo(inside, 1);
		s.roles.put(CARTER, new ArcState.Role(Optional.of(v.getUUID()), name));
		level.addFreshEntityWithPassengers(v);
		AliveWorkplace.LOG.info("Lost Caravan: {} is caged at {}", name.getString(), inside);
		return v;
	}

	private static BlockPos find(ServerLevel level, BlockPos around, java.util.function.Predicate<BlockState> what) {
		return find(level, around, what, 11);
	}

	@Nullable
	private static BlockPos find(ServerLevel level, BlockPos around, java.util.function.Predicate<BlockState> what, int r) {
		BlockPos best = null;
		for (BlockPos q : BlockPos.betweenClosed(around.offset(-r, -2, -r), around.offset(r, 3, r))) {
			if (what.test(level.getBlockState(q)) && (best == null || q.distSqr(around) < best.distSqr(around))) {
				best = q.immutable();
			}
		}
		return best;
	}

	/** The carter, when loaded. */
	@Nullable
	public static Villager carter(ServerLevel level, ArcState s) {
		ArcState.Role role = s.roles.get(CARTER);
		Entity e = role == null ? null : role.who().map(level::getEntity).orElse(null);
		return e instanceof Villager v && v.isAlive() ? v : null;
	}

	/** Who the carter follows (whoever opened his cage), if anyone. */
	@Nullable
	public static UUID leader(ArcState s) {
		return Nbt.hasUuid(s.extra, LEADER) ? Nbt.getUuid(s.extra, LEADER) : null;
	}

	// ---- every half second -----------------------------------------------------------------------------------------

	/** The wreck found, the cage opened, the carter following and coming into a village, and the days running out. */
	public static void tick(ServerLevel level) {
		long today = Chronicle.day(level);
		for (Running r : running(level)) {
			ArcState s = r.state();
			if (!s.started && s.chapter == 0) {
				continue;
			}
			if (today >= s.beganDay + RESCUE_DAYS && !s.flags.containsKey(TOBIN_HOME)) {
				ownWay(level, r);
				continue;
			}
			findWreck(level, r);
			ArcState.ArcPlace stockade = s.place(STOCKADE);
			if (stockade != null && stockade.placed > 0 && !Nbt.getBoolean(s.extra, SET)) {
				setUp(level, s, stockade); // (built before this story knew to fill it)
			}
			Villager carter = carter(level, s);
			if (carter == null || s.flags.containsKey(TOBIN_HOME)) {
				continue;
			}
			if (leader(s) == null) {
				freed(level, r, carter);
			}
			follow(level, s, carter);
			reached(level, r, carter);
		}
	}

	/** A player within {@link #FIND} blocks of the wreck: it's found, and the first chapter is done. */
	static void findWreck(ServerLevel level, Running r) {
		ArcState s = r.state();
		ArcState.ArcPlace wreck = s.place(WRECK);
		if (s.chapter != 0 || s.flags.containsKey(WRECK_FOUND) || wreck == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && CaravanTrail.within(player, wreck.spot, FIND)) {
				s.flags.put(WRECK_FOUND, ArcState.FOREVER);
				credit(s, player);
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.lost_caravan.wreck_found").withStyle(ChatFormatting.GOLD));
				Stories.Data.get(level).setDirty();
				Arcs.advance(level, r.hall(), r.entry(), s);
				return;
			}
		}
	}

	private static void credit(ArcState s, ServerPlayer player) {
		s.helpers.merge(player.getUUID(), 1, Integer::sum);
		s.chapterHelpers.merge(player.getUUID(), 1, Integer::sum);
		s.last = player.getUUID();
	}

	/** The cage's door stands open: the carter follows the player nearest it. */
	static void freed(ServerLevel level, Running r, Villager carter) {
		ArcState s = r.state();
		BlockPos door = find(level, carter.blockPosition(), st -> st.is(Blocks.IRON_DOOR) && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER, 4);
		if (door == null || !level.getBlockState(door).getValue(DoorBlock.OPEN)) {
			return;
		}
		ServerPlayer by = null;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && player.blockPosition().distSqr(door) <= FREE_RANGE * FREE_RANGE
				&& (by == null || player.distanceToSqr(door.getCenter()) < by.distanceToSqr(door.getCenter()))) {
				by = player;
			}
		}
		if (by == null) {
			return;
		}
		Nbt.putUuid(s.extra, LEADER, by.getUUID());
		credit(s, by);
		carter.clearRestriction();
		Stories.Data.get(level).setDirty();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, carter.getX(), carter.getY() + 1, carter.getZ(), 8, 0.4, 0.5, 0.4, 0.02);
		Chat.chat(by, Component.translatable("message.aliveworkplace.arc.says", carter.getDisplayName(),
			Component.translatable("message.aliveworkplace.lost_caravan.freed")).withStyle(ChatFormatting.WHITE));
		Chronicle.atHall(level, r.hall(), Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.lost_caravan.freed",
			carter.getDisplayName(), by.getDisplayName()));
	}

	/** The carter keeps up with his leader: walks after them, catches up from {@link #CATCH_UP} blocks, and waits further than {@link #WAIT}. */
	static void follow(ServerLevel level, ArcState s, Villager carter) {
		UUID id = leader(s);
		ServerPlayer leader = id == null ? null : level.getPlayerByUUID(id) instanceof ServerPlayer p && p.isAlive() && !p.isSpectator() ? p : null;
		if (leader == null) {
			return;
		}
		double d = carter.distanceTo(leader);
		if (d > WAIT) {
			carter.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			carter.getNavigation().stop();
			return;
		}
		if (d > CATCH_UP) {
			BlockPos spot = io.github.jcondedata.aliveworkplace.work.Walker.standingSpot(level, leader.blockPosition(), leader.blockPosition(), 4.0);
			BlockPos at = spot != null ? spot : leader.blockPosition(); // (where he can stand next to them, as a rallied guard)
			level.sendParticles(ParticleTypes.CLOUD, carter.getX(), carter.getY() + 0.5, carter.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
			carter.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			carter.getNavigation().stop();
			carter.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			return;
		}
		if (d > 3) {
			carter.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(leader, false), d > 10 ? 0.8f : 0.6f, 2));
		}
	}

	/** The carter is within {@link #REACH} blocks of either village's hall: he's home, and the last chapter is done. */
	static void reached(ServerLevel level, Running r, Villager carter) {
		ArcState s = r.state();
		if (leader(s) == null) {
			return;
		}
		BlockPos to = to(s);
		for (BlockPos hall : to == null ? List.of(r.hall()) : List.of(r.hall(), to)) {
			double dx = carter.getX() - (hall.getX() + 0.5);
			double dz = carter.getZ() - (hall.getZ() + 0.5);
			if (dx * dx + dz * dz <= REACH * REACH) {
				s.extra.putLong(HOME, hall.asLong());
				s.flags.put(TOBIN_HOME, ArcState.FOREVER);
				ServerPlayer leader = level.getPlayerByUUID(leader(s)) instanceof ServerPlayer p ? p : null;
				if (leader != null) {
					credit(s, leader);
				}
				Stories.Data.get(level).setDirty();
				Arcs.advance(level, r.hall(), r.entry(), s);
				return;
			}
		}
	}

	// ---- the lost goods (deliver_goods) ------------------------------------------------------------------------------

	/**
	 * {@code deliver_goods} (31.20): bring the lost caravan's goods, every stack of them, to the village they were going
	 * to; they go into its store when a player carrying them comes within {@link #REACH} blocks of its hall. Pays the
	 * quest's rewards and {@link #DELIVERY_REPUTATION} standing there.
	 */
	public record DeliverGoods(String village) implements Objectives.Objective {
		static DeliverGoods read(JsonObject json) {
			return new DeliverGoods(net.minecraft.util.GsonHelper.getAsString(json, "village", ""));
		}

		@Override
		public String type() {
			return "deliver_goods";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return village.isEmpty() ? Component.translatable("quest.aliveworkplace.deliver_goods.somewhere")
				: Component.translatable("quest.aliveworkplace.deliver_goods", village);
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("village", village);
			return o;
		}

		@Override
		public Objectives.Objective resolve(Objectives.Context context) {
			if (!village.isEmpty()) {
				return this;
			}
			ArcState s = Arcs.running(context.level, context.hall);
			BlockPos to = s == null ? null : to(s);
			return to == null ? this : new DeliverGoods(villageName(context.level, to).getString());
		}
	}

	/** Every 2 seconds for each player: the lost goods they carry into the receiving village count. */
	public static void check(ServerPlayer player) {
		if (player.isSpectator()) {
			return;
		}
		ServerLevel level = Players.level(player);
		for (Running r : running(level)) {
			ArcState s = r.state();
			BlockPos to = to(s);
			if (to == null || Nbt.getBoolean(s.extra, DELIVERED)) {
				continue;
			}
			double dx = player.getX() - (to.getX() + 0.5);
			double dz = player.getZ() - (to.getZ() + 0.5);
			if (dx * dx + dz * dz > REACH * REACH) {
				continue;
			}
			for (Quest q : List.copyOf(r.entry().quests)) {
				int i = q.current();
				if (i < 0 || !(q.objectives.get(i) instanceof DeliverGoods) || !s.id.equals(q.arc)
					|| !(level.getBlockEntity(r.hall()) instanceof VillageHallBlockEntity entity) || !Stories.mayHelp(level, r.hall(), q, player)) {
					continue;
				}
				if (!carries(player, goods(s))) {
					continue;
				}
				deliver(level, to, player, goods(s));
				s.extra.putBoolean(DELIVERED, true);
				Reputation.add(level, to, player, DELIVERY_REPUTATION);
				Chat.chat(player, Component.translatable("message.aliveworkplace.lost_caravan.delivered", villageName(level, to)).withStyle(ChatFormatting.GREEN));
				Chronicle.atHall(level, to, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.lost_caravan.delivered",
					player.getDisplayName(), Caravans.describe(goods(s))));
				Stories.Data.get(level).setDirty();
				Stories.progress(level, r.hall(), entity, q, i, 1, player);
				return;
			}
		}
	}

	/** Whether {@code player} carries every item of {@code goods}, as many of each. */
	static boolean carries(ServerPlayer player, List<ItemStack> goods) {
		Map<Item, Integer> need = new LinkedHashMap<>();
		goods.forEach(g -> need.merge(g.getItem(), g.getCount(), Integer::sum));
		return !need.isEmpty() && need.entrySet().stream().allMatch(en -> player.getInventory().countItem(en.getKey()) >= en.getValue());
	}

	/** Takes {@code goods} from {@code player} into the village's store (what doesn't fit is set down on its hall). */
	static void deliver(ServerLevel level, BlockPos to, ServerPlayer player, List<ItemStack> goods) {
		Map<Item, Integer> need = new LinkedHashMap<>();
		goods.forEach(g -> need.merge(g.getItem(), g.getCount(), Integer::sum));
		List<BlockPos> store = Caravans.storehouse(level, to);
		for (Map.Entry<Item, Integer> en : need.entrySet()) {
			int left = en.getValue();
			for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (stack.is(en.getKey())) {
					int take = Math.min(left, stack.getCount());
					ItemStack moved = stack.split(take);
					left -= take;
					ItemStack rest = store.isEmpty() ? moved : SupplyContainers.insert(level, store, moved);
					if (!rest.isEmpty()) {
						level.addFreshEntity(new ItemEntity(level, to.getX() + 0.5, to.getY() + 1.1, to.getZ() + 0.5, rest)); // (on the hall)
					}
				}
			}
		}
	}

	// ---- the endings -----------------------------------------------------------------------------------------------

	/** The carter joins the village he reached, at Journeyman; both villages thank whoever helped; both chronicles tell it. */
	static void ending(ServerLevel level, BlockPos hall, ArcState s) {
		BlockPos home = s.extra.contains(HOME) ? BlockPos.of(Nbt.getLong(s.extra, HOME)) : hall;
		Villager carter = carter(level, s);
		Component name = s.roles.containsKey(CARTER) ? s.roles.get(CARTER).name() : Component.translatable("chronicle.aliveworkplace.someone");
		if (carter != null) {
			join(level, carter, s, true);
			for (UUID who : Reputation.paid("helpers", s)) {
				Friendship.add(carter, who, "", FRIENDSHIP);
			}
		}
		BlockPos to = to(s);
		if (to != null) {
			// the arc's own effects thank them in the sending village; the receiving one does here
			for (UUID who : Reputation.paid("helpers", s)) {
				Reputation.add(level, to, who, "", ENDING_REPUTATION);
				Reputation.honour(level, to, who, "", "wayfinder");
			}
		}
		Component village = villageName(level, home);
		for (BlockPos at : to == null ? List.of(hall) : List.of(hall, to)) {
			tell(level, at, Component.translatable("message.aliveworkplace.lost_caravan.joins", name, village).withStyle(ChatFormatting.GREEN));
			Chronicle.atHall(level, at, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.lost_caravan.ending", name, village));
		}
	}

	/** The carter is the village's now: no longer the arc's, free to take a workstation ({@code journeyman}: at that level, as a hired traveller). */
	static void join(ServerLevel level, Villager carter, ArcState s, boolean journeyman) {
		for (String tag : List.copyOf(carter.getTags())) {
			if (tag.startsWith(Arcs.TAG) || tag.startsWith("aliveworkplace_role_")) {
				carter.removeTag(tag);
			}
		}
		s.roles.remove(CARTER); // (so the arc's end doesn't touch him)
		carter.clearRestriction();
		carter.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		if (journeyman) {
			ModAttachments.HEAD_START.set(carter, JOURNEYMAN); // his first job starts at Journeyman (Schools.headStart)
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, carter.getX(), carter.getY() + 1, carter.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
	}

	/** Seven days and no rescue: the carter finds his own way home with nothing, the goods are lost, and the arc fails. */
	static void ownWay(ServerLevel level, Running r) {
		ArcState s = r.state();
		BlockPos hall = r.hall();
		Villager carter = carter(level, s); // (caged where nobody is, he's gone when his chunk loads: onLoad)
		Component name = s.roles.containsKey(CARTER) ? s.roles.get(CARTER).name() : Names.name(level.random.nextInt(Names.COUNT));
		ArcState.ArcPlace stockade = s.place(STOCKADE);
		if (stockade != null && stockade.placed > 0 && level.isLoaded(stockade.spot)) {
			BlockPos chest = find(level, stockade.spot, st -> st.is(Blocks.CHEST));
			if (chest != null && level.getBlockEntity(chest) instanceof Container box) {
				box.clearContent(); // the raiders made off with them
				box.setChanged();
			}
		}
		s.extra.remove(GOODS);
		if (carter != null) {
			carter.discard(); // (he leaves the stockade; he comes home below)
		}
		// He comes home by the hall (also when never caged, the stockade unbuilt: he got away on the road).
		Villager home = EntityType.VILLAGER.create(level);
		if (home != null) {
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.offset(2, 0, 2));
			home.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
			home.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
			home.setBaby(false);
			home.setVillagerData(home.getVillagerData().setProfession(VillagerProfession.NONE));
			home.setCustomName(name);
			home.setPersistenceRequired();
			level.addFreshEntityWithPassengers(home);
		}
		s.roles.remove(CARTER);
		BlockPos to = to(s);
		for (BlockPos at : to == null ? List.of(hall) : List.of(hall, to)) {
			tell(level, at, Component.translatable("message.aliveworkplace.lost_caravan.own_way", name).withStyle(ChatFormatting.RED));
			Chronicle.atHall(level, at, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.lost_caravan.own_way", name));
		}
		Arcs.Arc arc = Arcs.get(ARC).orElse(null);
		if (arc != null) {
			Arcs.failed(level, hall, r.entry(), s, arc);
		} else {
			Arcs.end(level, hall, r.entry(), s, "failed");
		}
	}

	/** An entity loads: a carter whose story is over (or who went home on his own) and who is still the arc's is gone. */
	static boolean onLoad(ServerLevel level, Entity entity) {
		if (!entity.getTags().contains(ArcEffects.roleTag(CARTER)) || !entity.getTags().contains(ArcEffects.arcTag(ARC.toString()))) {
			return false;
		}
		for (Running r : running(level)) {
			ArcState.Role role = r.state().roles.get(CARTER);
			if (role != null && role.who().filter(entity.getUUID()::equals).isPresent()) {
				return false;
			}
		}
		entity.discard();
		return true;
	}

	private LostCaravanStory() {
	}
}
