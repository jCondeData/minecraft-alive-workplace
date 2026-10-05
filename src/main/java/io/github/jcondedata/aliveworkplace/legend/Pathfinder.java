package io.github.jcondedata.aliveworkplace.legend;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.explore.Explorers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Damage;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Pathfinder's expeditions with a player (ROADMAP 29.13, the {@code expedition} power). A sneak-right-click with
 * {@link ExpeditionPower#food} rations in the chests by their table offers three places in chat: a Stronghold, an Ancient
 * City or Trial Chambers ({@code #aliveworkplace:expedition/<kind>}). The one picked is looked up once
 * ({@code findNearestMapStructure}, within {@link ExpeditionPower#reach} blocks); the player gets an explorer map to it
 * and the Pathfinder leads: walking ahead, waiting when the player is more than {@link #WAIT} blocks behind, catching
 * up at once beyond {@link #CATCH_UP} (as Rally Banner guards do), and fighting whatever attacks either of them. At the
 * place they say so and plant a banner. A right-click there asks for <b>Home</b>: after {@link #HOME_TICKS} of both
 * standing still they are back beside the Village Hall. One a day; a logout or a death cancels it, and the Pathfinder
 * is home the next morning. The chronicle records each one, with its distance and direction from the hall. The state
 * lives on the villager (the {@code PATHFINDER} attachment, every field with a default), so a reload mid-way carries on.
 */
public final class Pathfinder {
	public static boolean ENABLED = true;
	public static final List<String> KINDS = List.of("stronghold", "ancient_city", "trial_chambers");
	/** The extra table the Pathfinder's own expeditions roll ({@link FarExpeditionsPower}'s default). */
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, AliveWorkplace.id("explorer/pathfinder"));
	/** Beyond this many blocks behind, the Pathfinder stops and waits for the player. */
	public static int WAIT = 24;
	/** Beyond this many blocks, they catch up at once. */
	public static int CATCH_UP = 64;
	/** How close (across) to the place counts as there. */
	public static int ARRIVE = 6;
	/** How far ahead each leg of the walk goes. */
	public static int LEG = 12;
	/** How long both stand still before Home takes them back (5 seconds). */
	public static int HOME_TICKS = 100;
	/** How long a chat offer stays open (60 seconds). */
	public static final int OFFER_TICKS = 1200;
	private static final float SPEED = 0.65f;
	private static final float DAMAGE = 5f;
	private static final int FIGHT_RANGE = 12;

	/**
	 * A Pathfinder's expeditions: the last Chronicle day they set out (-1: never), the player led, the place and its kind,
	 * where they set out, whether they're there, and the day a cancelled one left them out (-1: not stranded).
	 */
	public record State(long lastDay, Optional<UUID> player, Optional<BlockPos> target, String kind, Optional<BlockPos> start,
						boolean arrived, long strandedDay) {
		public static final State EMPTY = new State(-1, Optional.empty(), Optional.empty(), "", Optional.empty(), false, -1);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(State::lastDay),
			UUIDUtil.CODEC.optionalFieldOf("player").forGetter(State::player),
			BlockPos.CODEC.optionalFieldOf("target").forGetter(State::target),
			Codec.STRING.optionalFieldOf("kind", "").forGetter(State::kind),
			BlockPos.CODEC.optionalFieldOf("start").forGetter(State::start),
			Codec.BOOL.optionalFieldOf("arrived", false).forGetter(State::arrived),
			Codec.LONG.optionalFieldOf("stranded_day", -1L).forGetter(State::strandedDay)
		).apply(i, State::new));

		public boolean active() {
			return player.isPresent() && target.isPresent();
		}

		State ended(long stranded) {
			return new State(lastDay, Optional.empty(), Optional.empty(), "", Optional.empty(), false, stranded);
		}
	}

	private record Offer(UUID villager, long until) {
	}

	/** A Home asked for: where the player stood, and how long they have stood still (the Pathfinder is held still). */
	private static final class HomeWait {
		Vec3 player;
		int still;

		HomeWait(Vec3 player) {
			this.player = player;
		}
	}

	private static final Map<UUID, Offer> OFFERS = new HashMap<>();
	private static final Map<Villager, HomeWait> HOME = new WeakHashMap<>();
	private static final Map<Villager, Integer> COOLDOWN = new WeakHashMap<>();

	public static State state(Villager villager) {
		State s = ModAttachments.PATHFINDER.get(villager);
		return s == null ? State.EMPTY : s;
	}

	private static void save(Villager villager, State state) {
		ModAttachments.PATHFINDER.set(villager, state);
	}

	public static TagKey<Structure> tag(String kind) {
		return TagKey.create(Registries.STRUCTURE, AliveWorkplace.id("expedition/" + kind));
	}

	/** The settled Legend's power of {@code kind}, when they have one and aren't on strike. */
	private static <P extends Power> Optional<P> power(Villager villager, Class<P> kind) {
		if (!ENABLED) {
			return Optional.empty();
		}
		LegendData data = ModAttachments.LEGEND.get(villager);
		if (data == null || !data.settled() || data.onStrike()) {
			return Optional.empty();
		}
		return Legends.get(data.id()).flatMap(l -> l.powers(kind).stream().findFirst());
	}

	/** Whether {@code villager} is a settled Legend who leads expeditions (on strike or not: they still answer). */
	public static boolean isPathfinder(Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		return ENABLED && data != null && data.settled() && Legends.get(data.id()).map(l -> !l.powers(ExpeditionPower.class).isEmpty()).orElse(false);
	}

	/** How many times as far {@code explorer}'s own expeditions range (1 for anyone but a Pathfinder at work). */
	public static float rangeFactor(Villager explorer) {
		return power(explorer, FarExpeditionsPower.class).map(FarExpeditionsPower::factor).orElse(1f);
	}

	/** The extra loot table {@code explorer}'s own expeditions roll at each stop, if any. */
	public static Optional<ResourceKey<LootTable>> extraLoot(Villager explorer) {
		return power(explorer, FarExpeditionsPower.class).map(p -> ResourceKey.create(Registries.LOOT_TABLE, p.loot()));
	}

	/** Whether {@code villager} is leading an expedition now (their own work and vanilla's routine wait). */
	public static boolean isLeading(Villager villager) {
		State s = ModAttachments.PATHFINDER.get(villager);
		return s != null && s.active();
	}

	// ---- The player's side ----

	/** A sneak-right-click: offers the three places in chat, or says why not. */
	public static void offer(ServerPlayer player, Villager villager) {
		ServerLevel level = Players.level(player);
		Optional<ExpeditionPower> power = power(villager, ExpeditionPower.class);
		State state = state(villager);
		if (power.isEmpty()) {
			Chat.system(player, Component.translatable("message.aliveworkplace.pathfinder.on_strike", villager.getDisplayName()));
			return;
		}
		if (state.active()) {
			Chat.system(player, Component.translatable("message.aliveworkplace.pathfinder.busy", villager.getDisplayName()));
			return;
		}
		if (state.lastDay() == Chronicle.day(level)) {
			Chat.system(player, Component.translatable("message.aliveworkplace.pathfinder.tomorrow", villager.getDisplayName()));
			return;
		}
		int food = food(level, villager);
		if (food < power.get().food()) {
			Chat.system(player, Component.translatable("message.aliveworkplace.pathfinder.food", villager.getDisplayName(), power.get().food(), food));
			return;
		}
		OFFERS.put(player.getUUID(), new Offer(villager.getUUID(), level.getGameTime() + OFFER_TICKS));
		MutableComponent line = Component.translatable("message.aliveworkplace.pathfinder.offer", villager.getDisplayName()).append(" ");
		for (String kind : KINDS) {
			line.append(Component.literal("[").append(Component.translatable("expedition.aliveworkplace." + kind)).append("]")
				.withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
					.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/workplace expedition " + kind))
					.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace.pathfinder.pick")))))
				.append(" ");
		}
		Chat.system(player, line);
	}

	/** {@code /workplace expedition <kind>}: the player picked a place from the offer. Returns whether they set out. */
	public static boolean choose(ServerPlayer player, String kind) {
		ServerLevel level = Players.level(player);
		Offer offer = OFFERS.remove(player.getUUID());
		Entity entity = offer == null || offer.until() < level.getGameTime() ? null : level.getEntity(offer.villager());
		if (!(entity instanceof Villager villager) || !villager.isAlive() || villager.distanceTo(player) > 16 || !KINDS.contains(kind)) {
			Chat.system(player, Component.translatable("message.aliveworkplace.pathfinder.no_offer"));
			return false;
		}
		int reach = power(villager, ExpeditionPower.class).map(ExpeditionPower::reach).orElse(3000);
		Pair<BlockPos, Holder<Structure>> found = find(level, villager.blockPosition(), kind, reach);
		if (found == null) {
			Chat.system(player, Component.translatable("message.aliveworkplace.pathfinder.none", villager.getDisplayName(),
				Component.translatable("expedition.aliveworkplace." + kind), reach));
			return false;
		}
		return start(level, player, villager, kind, found.getFirst(), found.getSecond());
	}

	/** The nearest {@code kind} within {@code reach} blocks of {@code from}, looked up once; null with none (or no structures). */
	@Nullable
	public static Pair<BlockPos, Holder<Structure>> find(ServerLevel level, BlockPos from, String kind, int reach) {
		if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) {
			return null;
		}
		Optional<HolderSet.Named<Structure>> places = Lookup.tag(Lookup.registry(level.registryAccess(), Registries.STRUCTURE), tag(kind));
		if (places.isEmpty() || places.get().size() == 0) {
			return null;
		}
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
			.findNearestMapStructure(level, places.get(), from, Math.max(1, reach / 16), false);
		if (found == null) {
			return null;
		}
		double dx = found.getFirst().getX() - from.getX();
		double dz = found.getFirst().getZ() - from.getZ();
		return dx * dx + dz * dz <= (double) reach * reach ? found : null;
	}

	/**
	 * Sets out for {@code target} (a {@code kind} place, {@code structure} for the map's marker; tests stage their own):
	 * takes the food, hands the player the map, and records the day. Returns false when it isn't allowed now.
	 */
	public static boolean start(ServerLevel level, ServerPlayer player, Villager villager, String kind, BlockPos target, @Nullable Holder<Structure> structure) {
		Optional<ExpeditionPower> power = power(villager, ExpeditionPower.class);
		State state = state(villager);
		if (power.isEmpty() || state.active() || state.lastDay() == Chronicle.day(level) || food(level, villager) < power.get().food()) {
			return false;
		}
		List<BlockPos> chests = chests(level, villager);
		for (int i = 0; i < power.get().food(); i++) {
			SupplyContainers.takeOne(level, chests, Explorers::isFood);
		}
		Holder<Structure> marker = structure != null ? structure : anyOf(level, kind);
		if (marker != null) {
			ItemStack map = Explorers.mapTo(level, new Explorers.Place(target, marker));
			if (!player.getInventory().add(map)) {
				player.drop(map, false);
			}
		}
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
		save(villager, new State(Chronicle.day(level), Optional.of(player.getUUID()), Optional.of(target.immutable()), kind,
			Optional.of(villager.blockPosition()), false, -1));
		HOME.remove(villager);
		Chat.system(player, Component.translatable("message.aliveworkplace.pathfinder.set_out", villager.getDisplayName(),
			Component.translatable("expedition.aliveworkplace." + kind), distance(villager.blockPosition(), target),
			direction(villager.blockPosition(), target)));
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		return true;
	}

	/** A right-click on the Pathfinder: Home, when they're there with this player. Returns whether it was taken. */
	public static boolean askHome(ServerPlayer player, Villager villager) {
		State state = state(villager);
		if (!state.active() || !state.player().map(player.getUUID()::equals).orElse(false)) {
			return false;
		}
		if (!state.arrived()) {
			Chat.system(player, Component.translatable("message.aliveworkplace.pathfinder.not_yet", villager.getDisplayName()));
			return true;
		}
		HOME.put(villager, new HomeWait(player.position()));
		Chat.system(player, Component.translatable("message.aliveworkplace.pathfinder.home_wait", villager.getDisplayName(), HOME_TICKS / 20));
		return true;
	}

	// ---- The Pathfinder's side ----

	/** Every tick of a Legend's life ({@link Legends#tick}); works every 5th tick, only with an expedition on. */
	static void tick(Villager villager) {
		if (villager.tickCount % 5 != 0 || !(villager.level() instanceof ServerLevel level) || !ModAttachments.PATHFINDER.has(villager)) {
			return;
		}
		State state = state(villager);
		if (!state.active()) {
			if (state.strandedDay() >= 0 && Chronicle.day(level) > state.strandedDay()) {
				bringHome(level, villager, null); // a cancelled trip: home the next morning
				save(villager, state.ended(-1));
			}
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(state.player().get());
		if (player == null || !player.isAlive() || player.isRemoved()) {
			cancel(level, villager, state);
			return;
		}
		if (player.level() != level) {
			stop(villager);
			return; // through a portal: they wait where they are
		}
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
		if (fight(level, villager, player)) {
			return;
		}
		double away = villager.distanceTo(player);
		if (away > CATCH_UP) {
			catchUp(level, villager, player);
			return;
		}
		BlockPos target = state.target().get();
		if (state.arrived()) {
			homeRound(level, villager, player, state);
			return;
		}
		if (away > WAIT) {
			stop(villager);
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
			return;
		}
		double dx = target.getX() + 0.5 - villager.getX();
		double dz = target.getZ() + 0.5 - villager.getZ();
		double across = Math.sqrt(dx * dx + dz * dz);
		if (across <= ARRIVE) {
			arrive(level, villager, player, state);
			return;
		}
		double step = Math.min(LEG, across);
		BlockPos leg = surface(level, BlockPos.containing(villager.getX() + dx / across * step, villager.getY(), villager.getZ() + dz / across * step));
		villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(leg, SPEED, 1));
	}

	/** Fights whatever is after the player or the Pathfinder; true while there is something to fight. */
	private static boolean fight(ServerLevel level, Villager villager, ServerPlayer player) {
		LivingEntity foe = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(FIGHT_RANGE, 6, FIGHT_RANGE),
			e -> e.isAlive() && e != villager && e != player)) {
			boolean after = e instanceof Mob mob && (mob.getTarget() == player || mob.getTarget() == villager) && e instanceof Enemy
				|| e == villager.getLastHurtByMob() || e == player.getLastHurtByMob();
			if (after && villager.distanceToSqr(e) < best) {
				best = villager.distanceToSqr(e);
				foe = e;
			}
		}
		if (foe == null) {
			return false;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(foe, true));
		int cooldown = COOLDOWN.getOrDefault(villager, 0);
		if (cooldown > 0) {
			COOLDOWN.put(villager, cooldown - 5);
		}
		if (best > 6.25) {
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(foe, false), 0.8f, 1));
			return true;
		}
		stop(villager);
		if (cooldown <= 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			if (Damage.hurt(foe, level.damageSources().mobAttack(villager), DAMAGE)) {
				foe.knockback(0.4, villager.getX() - foe.getX(), villager.getZ() - foe.getZ());
			}
			COOLDOWN.put(villager, 20);
		}
		return true;
	}

	private static void catchUp(ServerLevel level, Villager villager, ServerPlayer player) {
		BlockPos spot = player.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(spot.offset(-2, -1, -2), spot.offset(2, 1, 2))) {
			if (!p.equals(spot) && io.github.jcondedata.aliveworkplace.work.Walker.canStand(level, p)) {
				spot = p.immutable();
				break;
			}
		}
		stop(villager);
		villager.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		level.playSound(null, spot, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.4f, 1.4f);
	}

	private static void arrive(ServerLevel level, Villager villager, ServerPlayer player, State state) {
		BlockPos target = state.target().get();
		stop(villager);
		BlockPos banner = surface(level, target);
		if (level.getBlockState(banner).canBeReplaced() && level.getBlockState(banner.below()).isFaceSturdy(level, banner.below(), net.minecraft.core.Direction.UP)) {
			level.setBlockAndUpdate(banner, Blocks.LIGHT_BLUE_BANNER.defaultBlockState());
		}
		save(villager, new State(state.lastDay(), state.player(), state.target(), state.kind(), state.start(), true, -1));
		Component place = Component.translatable("expedition.aliveworkplace." + state.kind());
		Chat.system(player, Component.translatable("message.aliveworkplace.pathfinder.arrived", villager.getDisplayName(), place));
		level.playSound(null, banner, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6f, 1.2f);
		BlockPos from = home(level, villager, state).orElse(state.start().orElse(villager.blockPosition()));
		Chronicle.record(level, from, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.legend.expedition",
			villager.getDisplayName(), player.getDisplayName(), place, distance(from, target), direction(from, target)));
	}

	private static void homeRound(ServerLevel level, Villager villager, ServerPlayer player, State state) {
		HomeWait wait = HOME.get(villager);
		if (wait == null) {
			stop(villager);
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
			return;
		}
		stop(villager);
		if (player.position().distanceToSqr(wait.player) > 0.25) { // the player moved: the count starts again
			wait.player = player.position();
			wait.still = 0;
			return;
		}
		wait.still += 5;
		if (wait.still < HOME_TICKS) {
			return;
		}
		HOME.remove(villager);
		BlockPos spot = bringHome(level, villager, state);
		if (spot != null) {
			BlockPos beside = VillageHalls.besideHall(level, home(level, villager, state).orElse(spot), 1);
			BlockPos p = beside != null ? beside : spot;
			Players.teleport(player, level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
			Chat.system(player, Component.translatable("message.aliveworkplace.pathfinder.home", villager.getDisplayName()));
		}
		save(villager, state.ended(-1));
	}

	/** Puts the Pathfinder beside their hall (or where they set out); returns the spot, or null with nowhere to go. */
	@Nullable
	private static BlockPos bringHome(ServerLevel level, Villager villager, @Nullable State state) {
		Optional<BlockPos> hall = home(level, villager, state);
		BlockPos spot = hall.map(h -> VillageHalls.besideHall(level, h, 0)).orElse(null);
		if (spot == null && state != null) {
			spot = state.start().orElse(null);
		}
		if (spot == null) {
			return null;
		}
		stop(villager);
		villager.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		return spot;
	}

	private static Optional<BlockPos> home(ServerLevel level, Villager villager, @Nullable State state) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		if (data != null && data.hall().isPresent()) {
			return data.hall();
		}
		return VillageHalls.nearest(level, state != null && state.start().isPresent() ? state.start().get() : villager.blockPosition());
	}

	/** A logout or a death: the trip is off, and the Pathfinder is home the next morning. */
	private static void cancel(ServerLevel level, Villager villager, State state) {
		stop(villager);
		HOME.remove(villager);
		save(villager, state.ended(Chronicle.day(level)));
		Chronicle.record(level, home(level, villager, state).orElse(villager.blockPosition()), Chronicle.Kind.LEGEND,
			Component.translatable("chronicle.aliveworkplace.legend.expedition_cancelled", villager.getDisplayName(),
				Component.translatable("expedition.aliveworkplace." + state.kind())));
	}

	private static void stop(Villager villager) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getNavigation().stop();
	}

	// ---- Helpers ----

	private static List<BlockPos> chests(ServerLevel level, Villager villager) {
		return Builders.benchPos(villager).map(b -> SupplyContainers.find(level, b, null)).orElse(List.of());
	}

	/** The rations in the chests by the Pathfinder's table. */
	public static int food(ServerLevel level, Villager villager) {
		int n = 0;
		for (BlockPos chest : chests(level, villager)) {
			for (ItemStack s : SupplyContainers.peekMatching(level, chest, Explorers::isFood)) {
				n += s.getCount();
			}
		}
		return n;
	}

	@Nullable
	private static Holder<Structure> anyOf(ServerLevel level, String kind) {
		return Lookup.tag(Lookup.registry(level.registryAccess(), Registries.STRUCTURE), tag(kind))
			.filter(set -> set.size() > 0).map(set -> set.get(0)).orElse(null);
	}

	/** The top of the ground at {@code pos}'s column, where it's loaded; else {@code pos}. */
	private static BlockPos surface(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return pos;
		}
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
	}

	public static int distance(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
	}

	/** "north-east": as the hall's map page names directions. */
	public static Component direction(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		int eighth = Math.floorMod((int) Math.round(Math.atan2(-dx, dz) / (Math.PI / 4)), 8);
		String[] directions = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
		return Component.translatable("screen.aliveworkplace.hall.dir." + directions[eighth]);
	}

	/** Forgets the open chat offers of a player who left. */
	public static void forgetOffer(UUID player) {
		OFFERS.remove(player);
	}

	private Pathfinder() {
	}
}
