package io.github.jcondedata.aliveworkplace.colony;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * The settlers set out (ROADMAP 33.9). Right-clicking a village's hall with its Colony Charter, the spot chosen,
 * gives the order ({@link #send}), kept in {@code aliveworkplace_realms} ({@link RealmData.Order}) and nowhere else, so
 * it goes on from any tick after a save and reload:
 * <ol>
 * <li><b>gathering</b>: two settlers volunteer ({@link #volunteers}) and go on living in the village; the supplies
 * ({@link Supply}) come out of the Storehouses as they turn up, and what's missing is on the village's wants and the
 * Storehouse board. With everything there, or after {@link #WAIT_TICKS} (3 days), the leaving is set for the next
 * morning. Until then the Colonies tab can call it off ({@link #callOff}): supplies and charter go back;</li>
 * <li><b>muster</b>: the settlers walk to the hall; with all of them there (or after {@link #MUSTER_TICKS}) the bell
 * rings;</li>
 * <li><b>leaving</b>: they walk out toward the spot, and once out of sight (nobody within {@link #SIGHT} blocks, or
 * {@link #OUT} blocks from the hall) or after {@link #LEAVE_TICKS} (30 seconds) they are saved into the order, the way
 * a grave keeps a villager, and taken out of the world;</li>
 * <li><b>on the road</b> for {@link #JOURNEY_TICKS} (3 minutes) plus a tick a block; 33.10 makes the camp when the
 * time is up.</li>
 * </ol>
 */
public final class Settlers {
	/** How long the village waits for missing supplies before the settlers leave with what there is: 3 days. */
	public static int WAIT_TICKS = 72000;
	/** Ticks from ready to the gathering at the hall; -1: the next morning. */
	public static int MUSTER_DELAY = -1;
	/** How long the settlers have to reach the hall before the bell rings anyway. */
	public static int MUSTER_TICKS = 300;
	/** How long after the bell the settlers are on the road, seen or not: 30 seconds. */
	public static int LEAVE_TICKS = 600;
	/** The journey: 3 minutes, plus a tick a block. */
	public static int JOURNEY_TICKS = 3600;
	/** A settler is out of sight with no player this near, or this far from the hall. */
	public static final int SIGHT = 48;
	public static final int OUT = 40;
	/** The Colonies tab's call-off button, beside the order. */
	public static final int CALL_OFF = Colonies.ON_ROAD + 1;
	private static final float WALK = 0.6f;

	/** What the settlers take with them. */
	public enum Supply {
		LOGS(64, Items.OAK_LOG, ItemTags.LOGS),
		PLANKS(64, Items.OAK_PLANKS, ItemTags.PLANKS),
		COBBLESTONE(64, Items.COBBLESTONE, null),
		BREAD(32, Items.BREAD, null),
		TORCHES(16, Items.TORCH, null),
		GLASS_PANES(12, Items.GLASS_PANE, null),
		BEDS(3, Items.WHITE_BED, ItemTags.BEDS);

		public final int count;
		/** The item it is asked for as (on the wants, where a caravan brings one item) and shown as. */
		public final Item icon;
		@Nullable
		private final TagKey<Item> tag;

		Supply(int count, Item icon, @Nullable TagKey<Item> tag) {
			this.count = count;
			this.icon = icon;
			this.tag = tag;
		}

		/** Any log is a log, any bed a bed; the rest are the plain item. */
		public boolean matches(ItemStack stack) {
			return tag == null ? stack.is(icon) : stack.is(tag);
		}

		/** "logs", or "log" for one. */
		public Component text(int n) {
			return Component.translatable("colony_supply.aliveworkplace." + name().toLowerCase(java.util.Locale.ROOT) + (n == 1 ? ".one" : ""));
		}
	}

	/** The settlers a village can spare: its colony's builder, the second volunteer, and all who go (partners too). */
	public record Volunteers(Villager builder, Villager second, List<Villager> all) {
	}

	/** What came of sending the settlers: whether the order stands, what to tell the player, and who volunteered. */
	public record Sent(boolean ok, Component message, List<Villager> settlers) {
	}

	private Settlers() {
	}

	public static void init() {
		Platform.get().onServerTick(Settlers::tick);
	}

	// ---- who goes -------------------------------------------------------------------------------------------------------

	/**
	 * The two settlers the village round {@code hall} can spare, or null. The first is the lowest-levelled builder if it
	 * has two or more (one not in the middle of a build), otherwise a jobless grown-up who becomes the colony's builder.
	 * The second is a jobless villager, otherwise the lowest-levelled worker of a job the village has three or more of.
	 * Never a guard, the only worker of a job, a Legend or a child. A married volunteer brings their partner, so one
	 * whose partner can't be spared (or isn't in the village) doesn't volunteer.
	 */
	@Nullable
	public static Volunteers volunteers(ServerLevel level, BlockPos hall) {
		VillageHalls.Census census = VillageHalls.census(level, hall);
		Map<VillagerProfession, Integer> had = new HashMap<>();
		for (Villager w : census.workers()) {
			had.merge(w.getVillagerData().getProfession(), 1, Integer::sum);
		}
		Map<VillagerProfession, Integer> left = new HashMap<>(had);
		List<Villager> all = new ArrayList<>();
		Comparator<Villager> lowest = Comparator.comparingInt((Villager v) -> v.getVillagerData().getLevel())
			.thenComparingDouble(v -> v.distanceToSqr(hall.getCenter())).thenComparing(Villager::getUUID);

		Villager builder = null;
		List<Villager> builders = census.workers().stream().filter(v -> v.getVillagerData().getProfession() == ModVillagers.BUILDER).sorted(lowest).toList();
		if (builders.size() >= 2) {
			for (Villager v : builders) {
				if (Builders.activeSite(level, v) == null && take(level, census, v, left, 2, all)) {
					builder = v;
					break;
				}
			}
		}
		if (builder == null) {
			List<Villager> jobless = new ArrayList<>(census.jobless());
			// (someone with no trade at all first: a nitwit or a worker who lost their workstation is the second choice)
			jobless.sort(Comparator.comparingInt(v -> v.getVillagerData().getProfession() == VillagerProfession.NONE ? 0 : 1));
			for (Villager v : jobless) {
				if (v.getVillagerData().getProfession() != VillagerProfession.NITWIT && take(level, census, v, left, 0, all)) {
					builder = v;
					break;
				}
			}
		}
		if (builder == null) {
			return null;
		}
		Villager second = null;
		for (Villager v : census.jobless()) {
			if (take(level, census, v, left, 0, all)) {
				second = v;
				break;
			}
		}
		if (second == null) {
			for (Villager v : census.workers().stream().sorted(lowest).toList()) {
				VillagerProfession job = v.getVillagerData().getProfession();
				if (had.getOrDefault(job, 0) >= 3 && take(level, census, v, left, 2, all)) {
					second = v;
					break;
				}
			}
		}
		return second == null ? null : new Volunteers(builder, second, List.copyOf(all));
	}

	/** Whether {@code v} may go at all: never a guard, a Legend or a child, and nobody twice. */
	private static boolean mayGo(Villager v, List<Villager> all) {
		return v.isAlive() && !v.isBaby() && !all.contains(v) && !Guards.isGuard(v) && Legends.of(v).isEmpty();
	}

	/** Whether the village keeps a worker of {@code v}'s job with {@code v} gone (a jobless villager is always spared). */
	private static boolean spared(VillageHalls.Census census, Villager v, Map<VillagerProfession, Integer> left, int atLeast) {
		return !census.workers().contains(v) || left.getOrDefault(v.getVillagerData().getProfession(), 0) >= Math.max(2, atLeast);
	}

	/** Puts {@code v}, and their partner if married, among the settlers if both can be spared. */
	private static boolean take(ServerLevel level, VillageHalls.Census census, Villager v, Map<VillagerProfession, Integer> left, int atLeast, List<Villager> all) {
		if (!mayGo(v, all) || !spared(census, v, left, atLeast)) {
			return false;
		}
		Villager partner = null;
		Couples.Partner with = Couples.partner(v);
		if (with != null && with.married()) {
			if (!(level.getEntity(with.id()) instanceof Villager p) || !mayGo(p, all)) {
				return false;
			}
			Map<VillagerProfession, Integer> without = new HashMap<>(left);
			if (census.workers().contains(v)) {
				without.merge(v.getVillagerData().getProfession(), -1, Integer::sum);
			}
			if (!spared(census, p, without, 2)) {
				return false;
			}
			partner = p;
		}
		for (Villager go : partner == null ? List.of(v) : List.of(v, partner)) {
			all.add(go);
			if (census.workers().contains(go)) {
				left.merge(go.getVillagerData().getProfession(), -1, Integer::sum);
			}
		}
		return true;
	}

	// ---- the order -----------------------------------------------------------------------------------------------------

	/**
	 * {@code player} right-clicked the hall at {@code hall} with the charter {@code stack}: if it is this village's, its
	 * spot is chosen, they rule the village, the village may found a colony ({@link Colonies#limit}) and two settlers can
	 * be spared, the order is given and the charter used up.
	 */
	public static Sent send(ServerLevel level, BlockPos hall, ServerPlayer player, ItemStack stack) {
		ColonyCharterItem.Charter charter = stack.get(ModComponents.COLONY_CHARTER);
		VillageHallBlockEntity entity = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		Component village = VillageHalls.name(level, hall);
		Component no = null;
		if (charter == null) {
			no = Component.translatable("message.aliveworkplace.colony.unbound");
		} else if (!Colonies.ENABLED) {
			no = Component.translatable("message.aliveworkplace.colony.off");
		} else if (entity == null || !charter.hall().equals(GlobalPos.of(level.dimension(), hall))) {
			no = Component.translatable("message.aliveworkplace.colony.wrong_hall", charter.name());
		} else if (charter.spot().isEmpty()) {
			no = Component.translatable("message.aliveworkplace.colony.send_no_spot");
		} else if (entity.owner() == null) {
			no = Component.translatable("message.aliveworkplace.colony.no_owner", village);
		} else if (!VillageProtection.mayRule(level, entity, player)) {
			no = Component.translatable("message.aliveworkplace.colony.send_not_yours", village, entity.ownerName());
		} else {
			Colonies.Refusal limit = Colonies.limit(level, hall);
			if (limit != null) {
				no = Colonies.refusalMessage(level, hall, limit, entity, player);
			}
		}
		Volunteers volunteers = no == null ? volunteers(level, hall) : null;
		if (no == null && volunteers == null) {
			no = Component.translatable("message.aliveworkplace.colony.no_settlers", village);
		}
		if (no != null) {
			level.playSound(null, hall, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1f);
			return new Sent(false, no.copy().withStyle(ChatFormatting.RED), List.of());
		}
		Optional<Component> name = Optional.ofNullable(ColonyCharterItem.colonyName(stack));
		RealmData.Order order = new RealmData.Order(charter.hall(), charter.spot().get(), name, RealmData.GATHERING, 0, 0, Colonies.COST * 100,
			level.getGameTime(), volunteers.all().stream().map(Villager::getUUID).toList(), Optional.of(volunteers.builder().getUUID()), Map.of(), List.of());
		RealmData data = RealmData.get(level.getServer());
		data.putOrder(order);
		stack.shrink(1);
		step(level, data, order); // the supplies that are there come out at once
		order = data.orderOf(charter.hall());
		level.playSound(null, hall, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
		Component names = names(volunteers.all().stream().map(Villager::getDisplayName).toList());
		Component colony = name.orElse(Component.translatable("screen.aliveworkplace.colonies.unnamed"));
		Map<Supply, Integer> missing = order == null ? Map.of() : missing(order);
		Component message = missing.isEmpty() ? Component.translatable("message.aliveworkplace.colony.sent", names, colony)
			: Component.translatable("message.aliveworkplace.colony.sent_missing", names, colony, list(missing));
		return new Sent(true, message.copy().withStyle(ChatFormatting.GREEN), volunteers.all());
	}

	/** "Dara, Tomas and Mira". */
	static Component names(List<Component> names) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) {
				out.append(i == names.size() - 1 ? Component.translatable("message.aliveworkplace.colony.and") : Component.literal(", "));
			}
			out.append(names.get(i));
		}
		return out;
	}

	/** "40 logs, 3 beds". */
	static Component list(Map<Supply, Integer> supplies) {
		MutableComponent out = Component.empty();
		boolean first = true;
		for (Map.Entry<Supply, Integer> e : supplies.entrySet()) {
			if (!first) {
				out.append(", ");
			}
			first = false;
			out.append(Component.translatable("message.aliveworkplace.colony.supply", e.getValue(), e.getKey().text(e.getValue())));
		}
		return out;
	}

	/** How many of {@code supply} the order holds. */
	public static int held(RealmData.Order order, Supply supply) {
		int n = 0;
		for (Map.Entry<Item, Integer> e : order.supplies().entrySet()) {
			if (supply.matches(new ItemStack(e.getKey()))) {
				n += e.getValue();
			}
		}
		return n;
	}

	/** What the order still waits for, in the list's order. */
	public static Map<Supply, Integer> missing(RealmData.Order order) {
		Map<Supply, Integer> out = new LinkedHashMap<>();
		for (Supply s : Supply.values()) {
			int need = s.count - held(order, s);
			if (need > 0) {
				out.put(s, need);
			}
		}
		return out;
	}

	/** The order of the village at {@code hall} while its settlers still wait for supplies (or the morning), or null. */
	@Nullable
	private static RealmData.Order waiting(ServerLevel level, BlockPos hall) {
		RealmData.Order order = RealmData.get(level.getServer()).orderOf(GlobalPos.of(level.dimension(), hall));
		return order != null && order.state().equals(RealmData.GATHERING) ? order : null;
	}

	/** What the settlers of the village at {@code hall} still wait for, as the village's wants (partners' caravans bring it). */
	public static Map<Item, Integer> wants(ServerLevel level, BlockPos hall) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		RealmData.Order order = waiting(level, hall);
		if (order != null) {
			missing(order).forEach((s, n) -> out.put(s.icon, n));
		}
		return out;
	}

	/**
	 * The same as requests on the board of {@code storehouse}, in a settler's name: what a player hands over there goes
	 * into the Storehouse's chests, where the settlers take it.
	 */
	public static List<Requests.Request> requests(ServerLevel level, BlockPos storehouse) {
		BlockPos hall = VillageHalls.nearest(level, storehouse).orElse(null);
		RealmData.Order order = hall == null ? null : waiting(level, hall);
		if (order == null) {
			return List.of();
		}
		Villager settler = null;
		for (UUID id : order.volunteers()) {
			if (level.getEntity(id) instanceof Villager v && v.isAlive()) {
				settler = v;
				break;
			}
		}
		List<Requests.Request> out = new ArrayList<>();
		if (settler != null) {
			for (Map.Entry<Supply, Integer> e : missing(order).entrySet()) {
				Supply s = e.getKey();
				out.add(s.tag == null ? Requests.forItem(settler, storehouse, s.icon, e.getValue())
					: new Requests.Request(settler, storehouse, new ItemStack(s.icon), e.getValue(),
						Component.translatable("message.aliveworkplace.colony.supply", e.getValue(), s.text(e.getValue())), s::matches));
			}
		}
		return out;
	}

	/** Takes what the order still misses out of the village's Storehouses; the order with it (the same one if nothing came). */
	private static RealmData.Order collect(ServerLevel level, RealmData.Order order) {
		Map<Supply, Integer> missing = missing(order);
		if (missing.isEmpty()) {
			return order;
		}
		List<BlockPos> chests = Caravans.storehouse(level, order.mother().pos());
		if (chests.isEmpty()) {
			return order;
		}
		Map<Item, Integer> supplies = new LinkedHashMap<>(order.supplies());
		boolean took = false;
		Map<Item, Long> stock = SupplyContainers.contents(level, chests);
		for (Map.Entry<Supply, Integer> e : missing.entrySet()) {
			int need = e.getValue();
			for (Map.Entry<Item, Long> have : stock.entrySet()) {
				if (need <= 0) {
					break;
				}
				if (have.getValue() <= 0 || !e.getKey().matches(new ItemStack(have.getKey()))) {
					continue;
				}
				int got = SupplyContainers.extract(level, chests, have.getKey(), (int) Math.min(need, have.getValue()));
				if (got > 0) {
					supplies.merge(have.getKey(), got, Integer::sum);
					need -= got;
					took = true;
				}
			}
		}
		return took ? order.withSupplies(supplies) : order;
	}

	/** Puts the order's supplies back in the Storehouses; what finds no room drops at the hall. */
	private static void putBack(ServerLevel level, RealmData.Order order) {
		BlockPos hall = order.mother().pos();
		List<BlockPos> chests = Caravans.storehouse(level, hall);
		for (Map.Entry<Item, Integer> e : order.supplies().entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				ItemStack stack = new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize()));
				left -= stack.getCount();
				ItemStack rest = chests.isEmpty() ? stack : SupplyContainers.insert(level, chests, stack);
				if (!rest.isEmpty()) {
					Block.popResource(level, hall.above(), rest);
				}
			}
		}
	}

	/** The charter the order was given with: bound to the mother village, its spot chosen, named if the colony is. */
	public static ItemStack charter(ServerLevel level, RealmData.Order order) {
		ItemStack stack = ColonyCharterItem.of(level, order.mother().pos());
		ColonyCharterItem.Charter charter = stack.get(ModComponents.COLONY_CHARTER);
		if (charter != null) {
			stack.set(ModComponents.COLONY_CHARTER, charter.withSpot(order.spot()));
		}
		order.name().ifPresent(n -> stack.set(DataComponents.CUSTOM_NAME, n));
		return stack;
	}

	/**
	 * Calls off the order of the village at {@code hall} while its settlers haven't left: they stay, the supplies go back
	 * into the Storehouses and the charter to {@code player} (with none, it drops at the hall). False: there was nothing
	 * to call off (no order, or the settlers are gone).
	 */
	public static boolean callOff(ServerLevel level, BlockPos hall, @Nullable ServerPlayer player) {
		RealmData data = RealmData.get(level.getServer());
		GlobalPos mother = GlobalPos.of(level.dimension(), hall);
		RealmData.Order order = data.orderOf(mother);
		if (order == null || order.gone()) {
			return false;
		}
		data.removeOrder(mother);
		putBack(level, order);
		ItemStack charter = charter(level, order);
		if (player == null) {
			Block.popResource(level, hall.above(), charter);
		} else if (!player.getInventory().add(charter)) {
			player.drop(charter, false);
		}
		return true;
	}

	// ---- every tick ----------------------------------------------------------------------------------------------------

	/** Every order moves on; one whose village isn't awake waits (its settlers and chests aren't there to be moved). */
	public static void tick(MinecraftServer server) {
		RealmData data = RealmData.get(server);
		for (RealmData.Order order : data.orders()) {
			if (order.state().equals(RealmData.ON_ROAD)) {
				continue; // (33.10 makes the camp when the time is up)
			}
			ServerLevel level = server.getLevel(order.mother().dimension());
			if (level == null || !level.isPositionEntityTicking(order.mother().pos())) {
				continue;
			}
			boolean waiting = order.state().equals(RealmData.GATHERING);
			if (!waiting || level.getGameTime() % 20 == 0) {
				step(level, data, order);
			}
		}
	}

	private static void step(ServerLevel level, RealmData data, RealmData.Order order) {
		BlockPos hall = order.mother().pos();
		long now = level.getGameTime();
		if (!level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
			if (!order.gone()) {
				callOff(level, hall, null); // the hall was broken: nobody leaves, nothing is lost
			} else {
				data.putOrder(depart(level, order, here(level, order)));
			}
			return;
		}
		switch (order.state()) {
			case RealmData.GATHERING -> {
				RealmData.Order next = collect(level, order);
				if (next.leaves() == 0 && (missing(next).isEmpty() || now - next.ordered() >= WAIT_TICKS)) {
					long wait = MUSTER_DELAY >= 0 ? MUSTER_DELAY : 24000 - Math.floorMod(level.getDayTime(), 24000L);
					next = next.withState(RealmData.GATHERING, now + wait, 0);
				}
				if (next.leaves() > 0 && now >= next.leaves()) {
					next = next.withState(RealmData.MUSTER, now, 0);
				}
				if (next != order) {
					data.putOrder(next);
				}
			}
			case RealmData.MUSTER -> {
				List<Villager> here = here(level, order);
				if (here.isEmpty()) {
					callOff(level, hall, null); // nobody is left to go
					return;
				}
				boolean gathered = here.stream().allMatch(v -> v.distanceToSqr(hall.getCenter()) <= 5 * 5);
				if (gathered || now - order.leaves() >= MUSTER_TICKS) {
					level.playSound(null, hall, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 1f);
					Component colony = order.name().orElse(Component.translatable("screen.aliveworkplace.colonies.unnamed"));
					Component names = names(here.stream().map(Villager::getDisplayName).toList());
					double r = VillageHalls.RADIUS + 32;
					for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r * r)) {
						Chat.chat(player, Component.translatable("message.aliveworkplace.colony.set_out", names, colony, VillageHalls.name(level, hall))
							.withStyle(ChatFormatting.GOLD));
					}
					data.putOrder(order.withVolunteers(here.stream().map(Villager::getUUID).toList()).withState(RealmData.LEAVING, now, 0));
					walk(here, edge(level, order), true);
				} else {
					walk(here, hall, now % 10 == 0);
				}
			}
			case RealmData.LEAVING -> {
				List<Villager> here = here(level, order);
				boolean late = now - order.leaves() >= LEAVE_TICKS;
				boolean unseen = !here.isEmpty() && here.stream().allMatch(v -> v.distanceToSqr(hall.getCenter()) >= OUT * OUT
					|| level.getNearestPlayer(v.getX(), v.getY(), v.getZ(), SIGHT, false) == null);
				if (!here.isEmpty() && (late || unseen)) {
					data.putOrder(depart(level, order, here));
				} else {
					walk(here, edge(level, order), now % 10 == 0);
				}
			}
			default -> {
			}
		}
	}

	/** The order's volunteers who are in the world, alive. */
	public static List<Villager> here(ServerLevel level, RealmData.Order order) {
		List<Villager> out = new ArrayList<>();
		for (UUID id : order.volunteers()) {
			if (level.getEntity(id) instanceof Villager v && v.isAlive()) {
				out.add(v);
			}
		}
		return out;
	}

	private static void walk(List<Villager> settlers, BlockPos to, boolean now) {
		if (!now) {
			return;
		}
		for (Villager v : settlers) {
			if (v.isSleeping()) {
				v.stopSleeping();
			}
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, WALK, 1));
		}
	}

	/** Where the settlers walk out to: {@link #OUT} blocks and a few more from the hall toward the spot, on the ground there. */
	public static BlockPos edge(ServerLevel level, RealmData.Order order) {
		BlockPos hall = order.mother().pos();
		double dx = order.spot().getX() - hall.getX();
		double dz = order.spot().getZ() - hall.getZ();
		double length = Math.max(1, Math.sqrt(dx * dx + dz * dz));
		double far = Math.min(length, OUT + 8);
		BlockPos column = BlockPos.containing(hall.getX() + 0.5 + dx / length * far, hall.getY(), hall.getZ() + 0.5 + dz / length * far);
		return level.isLoaded(column) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column) : column;
	}

	/**
	 * The settlers in {@code here} take to the road: each is saved into the order as they are (name, job, level, trades,
	 * partner), gives up their bed and workstation in the mother village, and is taken out of the world. The order with
	 * them, on the road, its arrival time set.
	 */
	private static RealmData.Order depart(ServerLevel level, RealmData.Order order, List<Villager> here) {
		List<CompoundTag> saved = new ArrayList<>(order.settlers());
		for (Villager v : here) {
			CompoundTag tag = new CompoundTag();
			v.saveWithoutId(tag);
			saved.add(tag);
			for (MemoryModuleType<GlobalPos> poi : List.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE,
				MemoryModuleType.MEETING_POINT)) {
				if (v.getBrain().hasMemoryValue(poi)) {
					v.releasePoi(poi);
				}
			}
			v.discard();
		}
		long now = level.getGameTime();
		return order.withSettlers(saved).withVolunteers(List.of()).withState(RealmData.ON_ROAD, order.leaves(), now + journey(order));
	}

	/** How long the journey takes: 3 minutes plus a tick a block from the hall to the spot. */
	public static long journey(RealmData.Order order) {
		BlockPos hall = order.mother().pos();
		double dx = order.spot().getX() - hall.getX();
		double dz = order.spot().getZ() - hall.getZ();
		return JOURNEY_TICKS + Math.round(Math.sqrt(dx * dx + dz * dz));
	}

	// ---- the Colonies tab ----------------------------------------------------------------------------------------------

	/** "On the road to Newbrook, there in 2 minutes" and the like, for the order's row on the Colonies tab. */
	public static Component status(ServerLevel level, RealmData.Order order) {
		Component colony = order.name().orElse(Component.translatable("screen.aliveworkplace.colonies.unnamed"));
		long now = level.getGameTime();
		switch (order.state()) {
			case RealmData.ON_ROAD -> {
				long left = order.arrives() - now;
				if (left <= 0) {
					return Component.translatable("screen.aliveworkplace.colonies.arriving", colony);
				}
				long minutes = (left + 1199) / 1200;
				return minutes <= 1 ? Component.translatable("screen.aliveworkplace.colonies.road_minute", colony)
					: Component.translatable("screen.aliveworkplace.colonies.road_minutes", colony, minutes);
			}
			case RealmData.MUSTER, RealmData.LEAVING -> {
				return Component.translatable("screen.aliveworkplace.colonies.setting_out", colony);
			}
			default -> {
				return order.leaves() > 0 ? Component.translatable("screen.aliveworkplace.colonies.ready", colony)
					: Component.translatable("screen.aliveworkplace.colonies.getting_ready", colony);
			}
		}
	}

	/** The order's lines under its status: who goes, what they carry and wait for, and when they leave. */
	public static List<Component> lines(ServerLevel level, RealmData.Order order) {
		List<Component> out = new ArrayList<>();
		List<Component> who = new ArrayList<>();
		for (Villager v : here(level, order)) {
			who.add(v.getDisplayName());
		}
		int settlers = order.settlers().size() + who.size();
		out.add(VillageHallScreen.line(who.isEmpty() ? Component.translatable("screen.aliveworkplace.colonies.settlers", settlers)
			: Component.translatable("screen.aliveworkplace.colonies.settlers_named", names(who)), ChatFormatting.WHITE));
		Map<Supply, Integer> held = new LinkedHashMap<>();
		for (Supply s : Supply.values()) {
			int n = held(order, s);
			if (n > 0) {
				held.put(s, n);
			}
		}
		if (!held.isEmpty()) {
			out.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.colonies.packed", list(held)), ChatFormatting.GRAY));
		}
		if (!order.gone()) {
			Map<Supply, Integer> missing = missing(order);
			if (!missing.isEmpty()) {
				out.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.colonies.waiting_for", list(missing)), ChatFormatting.YELLOW));
			}
			if (order.leaves() > 0) {
				out.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.colonies.leave_morning"), ChatFormatting.GRAY));
			} else {
				long days = Math.max(1, (WAIT_TICKS - (level.getGameTime() - order.ordered()) + 23999) / 24000);
				out.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.colonies.leave_anyway", days), ChatFormatting.GRAY));
			}
		}
		return out;
	}
}
