package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mixin.MobAccessor;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Caravans you can see (ROADMAP 33.7). When a caravan leaves or arrives in a village with a player within
 * {@link #SEE_RANGE} blocks of its hall, a party shows it: a carter (a villager in the porter's outfit, named
 * "Thornholm's caravan") leading two llamas with chests and carpets in the caravan's village's colour
 * ({@link #colour}). Leaving, they walk from the Storehouse to the edge of the village toward the other village and
 * are gone there; arriving, they come in from that edge to the Storehouse, the llamas stand while the goods are
 * unloaded (the chest sounds), and they walk back out the way they came.
 *
 * <p>They are only a sight. The goods travel the saved way ({@link Caravans}), whether a party is shown or not, so
 * nothing they do changes what arrives. The carter has no villager brain (it never looks for a job, a bed or a
 * partner, see {@code VillagerMixin}), no trades and nothing in its pockets; the llamas only follow their leads. None
 * of them can be hurt, clicked, bred or robbed. One party per village at a time; every party is gone after
 * {@link #LIFE_TICKS} wherever it is; and its members carry {@link #TAG}, so any found after a restart are taken
 * away (like the ferry's ride boats). {@code visibleCaravans} in the config.
 */
public final class CaravanSights {
	/** {@code visibleCaravans} in the config (with M33's gate; off in gametests unless a test turns it on). Off: no party is shown, and any out walking is taken away. */
	public static boolean ENABLED = true;
	/** A party is shown only with a player this near the village's hall. */
	public static final int SEE_RANGE = 96;
	/** Every party is gone after this long (2 minutes), wherever it is. */
	public static final int LIFE_TICKS = 2400;
	/** How long the llamas stand at the Storehouse while the goods are unloaded. */
	public static final int UNLOAD_TICKS = 120;
	/** Marks a party's carter and llamas, so any left behind by a restart can be taken away. */
	public static final String TAG = "aliveworkplace_caravan";
	/** How far from the Storehouse the party's spot may be (it prefers one under the open sky). */
	static final int YARD_REACH = 6;
	/** The carter's walking pace (a villager's stroll). */
	static final double WALK = 0.5;
	/** A leash snaps past 10 blocks; a llama this far behind is brought up to the carter instead. */
	static final double LEASH_KEEP = 8;

	/** What a party is doing. */
	public enum Phase {
		/** Walking from the Storehouse to the edge of the village; gone when it gets there. */
		LEAVING,
		/** Walking in from the edge to the Storehouse. */
		ARRIVING,
		/** At the Storehouse: the llamas stand while the goods are unloaded. */
		UNLOADING,
		/** Unloaded, walking back out to the edge; gone when it gets there. */
		RETURNING
	}

	/** A caravan out walking in one village: its carter and two llamas, where they load and where they leave. */
	public static final class Party {
		final ServerLevel level;
		final BlockPos hall;
		final BlockPos store;
		final BlockPos yard;
		final BlockPos edge;
		final Villager carter;
		final List<Llama> llamas;
		final long born;
		Phase phase;
		long unloadingSince;

		Party(ServerLevel level, BlockPos hall, BlockPos store, BlockPos yard, BlockPos edge, Villager carter, List<Llama> llamas, Phase phase) {
			this.level = level;
			this.hall = hall;
			this.store = store;
			this.yard = yard;
			this.edge = edge;
			this.carter = carter;
			this.llamas = List.copyOf(llamas);
			this.phase = phase;
			this.born = level.getGameTime();
		}

		public Villager carter() {
			return carter;
		}

		public List<Llama> llamas() {
			return llamas;
		}

		public Phase phase() {
			return phase;
		}

		/** The spot by the Storehouse where the party loads and unloads. */
		public BlockPos yard() {
			return yard;
		}

		/** The spot at the edge of the village, toward the other village, where the party goes out of sight or comes in. */
		public BlockPos edge() {
			return edge;
		}

		/** The village this party is seen in. */
		public BlockPos hall() {
			return hall;
		}

		private List<Mob> members() {
			List<Mob> all = new ArrayList<>(llamas);
			all.add(carter);
			return all;
		}

		/** One tick of the walk; false when the party is over. */
		private boolean step() {
			long now = level.getGameTime();
			if (!ENABLED || now - born >= LIFE_TICKS) {
				return false;
			}
			for (Mob m : members()) {
				if (m.isRemoved() || !m.isAlive() || m.level() != level) {
					return false; // unloaded, gone through a portal, struck by lightning...: the sight is over
				}
			}
			for (Llama llama : llamas) {
				if (llama.getLeashHolder() != carter) {
					llama.setLeashedTo(carter, true);
				}
				if (llama.distanceToSqr(carter) > LEASH_KEEP * LEASH_KEEP) {
					llama.teleportTo(carter.getX(), carter.getY(), carter.getZ()); // before the lead snaps and drops
				}
			}
			switch (phase) {
				case LEAVING, RETURNING -> {
					if (at(edge)) {
						return false;
					}
					walk(edge);
				}
				case ARRIVING -> {
					if (at(yard)) {
						phase = Phase.UNLOADING;
						unloadingSince = now;
						carter.getNavigation().stop();
					} else {
						walk(yard);
					}
				}
				case UNLOADING -> unload(now - unloadingSince);
			}
			return true;
		}

		/** The llamas stand, the chests sound, and after {@link #UNLOAD_TICKS} the party turns for the edge again. */
		private void unload(long t) {
			carter.getLookControl().setLookAt(llamas.get(t < UNLOAD_TICKS / 2 ? 0 : 1));
			for (Llama llama : llamas) {
				// a llama still catching up comes on for a moment, then stands like the other
				if (!llama.isNoAi() && (t >= 40 || llama.distanceToSqr(carter) <= 3.5 * 3.5)) {
					llama.getNavigation().stop();
					llama.setNoAi(true);
				}
			}
			if (t == 20) {
				level.playSound(null, llamas.get(0).blockPosition(), SoundEvents.LLAMA_CHEST, SoundSource.NEUTRAL, 1f, 1f);
				level.playSound(null, store, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6f, 1f);
			} else if (t == 60) {
				level.playSound(null, llamas.get(1).blockPosition(), SoundEvents.LLAMA_CHEST, SoundSource.NEUTRAL, 1f, 1.1f);
			} else if (t == 100) {
				level.playSound(null, store, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.6f, 1f);
			}
			if (t >= UNLOAD_TICKS) {
				llamas.forEach(l -> l.setNoAi(false));
				phase = Phase.RETURNING;
				walk(edge, true);
			}
		}

		private boolean at(BlockPos spot) {
			double dx = carter.getX() - (spot.getX() + 0.5);
			double dz = carter.getZ() - (spot.getZ() + 0.5);
			return dx * dx + dz * dz <= 1.5 * 1.5 && Math.abs(carter.getY() - spot.getY()) <= 3;
		}

		private void walk(BlockPos spot) {
			walk(spot, false);
		}

		/** Sends the carter on toward {@code spot}; asked again twice a second, so a way that opens up is taken. */
		private void walk(BlockPos spot, boolean now) {
			if (now || (carter.tickCount + carter.getId()) % 10 == 0) {
				carter.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, WALK);
			}
		}

		/** Takes the party away; with {@code puff}, in a puff of smoke (it was still in view). */
		private void end(boolean puff) {
			for (Mob m : members()) {
				LIVE.remove(m);
				if (m.isRemoved()) {
					continue;
				}
				if (puff && m.level() instanceof ServerLevel here) {
					here.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 0.8, m.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
				}
				if (m instanceof Llama llama) {
					llama.dropLeash(true, false); // no lead is left on the ground
				}
				m.discard();
			}
		}
	}

	private static final List<Party> PARTIES = new ArrayList<>();
	/** The members of the parties out now; a tagged entity that isn't one of these was left by a restart. */
	private static final Set<Entity> LIVE = Collections.newSetFromMap(new IdentityHashMap<>());

	private CaravanSights() {
	}

	public static void init() {
		Platform.get().onServerStarting(server -> {
			PARTIES.clear();
			LIVE.clear();
		});
		Platform.get().onServerTick(server -> tick());
		Platform.get().onEntityLoad((entity, level) -> {
			if (isParty(entity) && !LIVE.contains(entity)) {
				entity.discard(); // (left behind when the server stopped with a caravan out walking)
			}
		});
		// Only a sight: a click does nothing (no gift, no job item, no City Plan; CaravanPartyMixin stops the game's own
		// trade, ride and hay), and nothing hurts them.
		Platform.get().onUseEntity((player, level, hand, entity, hit) -> isParty(entity) ? InteractionResult.CONSUME : InteractionResult.PASS);
		Platform.get().allowDamage((entity, source, amount) -> !isParty(entity) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
	}

	/** Whether {@code entity} is a caravan party's carter or llama. */
	public static boolean isParty(Entity entity) {
		return entity.getTags().contains(TAG);
	}

	/** The party out walking in {@code hall}'s village, or null. */
	@Nullable
	public static Party party(ServerLevel level, BlockPos hall) {
		for (Party p : PARTIES) {
			if (p.level == level && p.hall.equals(hall)) {
				return p;
			}
		}
		return null;
	}

	/** Every party out walking in {@code level}. */
	public static List<Party> parties(ServerLevel level) {
		return PARTIES.stream().filter(p -> p.level == level).toList();
	}

	/** One tick for every party: on they walk, and those that are done (or two minutes old) are taken away. */
	public static void tick() {
		for (Iterator<Party> it = PARTIES.iterator(); it.hasNext();) {
			Party p = it.next();
			boolean atEdge = p.phase != Phase.ARRIVING && p.phase != Phase.UNLOADING && !p.carter.isRemoved() && p.at(p.edge);
			if (!p.step()) {
				p.end(!atEdge);
				it.remove();
			}
		}
	}

	/** Takes away the party in {@code hall}'s village, if one is out (its hall was broken; tests clean up). */
	public static void clear(ServerLevel level, BlockPos hall) {
		for (Iterator<Party> it = PARTIES.iterator(); it.hasNext();) {
			Party p = it.next();
			if (p.level == level && p.hall.equals(hall)) {
				p.end(false);
				it.remove();
			}
		}
	}

	/** {@code hall}'s caravan has just left for the village at {@code to}: its party sets out from the Storehouse. */
	@Nullable
	static Party leave(ServerLevel level, BlockPos hall, BlockPos to, Caravans.Data data) {
		return start(level, hall, hall, to, Phase.LEAVING, data);
	}

	/**
	 * The caravan {@code shipment} has just come to {@code hall}: its party comes in from the edge. It is the sending
	 * village's caravan, or this village's own coming home with what wasn't bought.
	 */
	@Nullable
	static Party come(ServerLevel level, BlockPos hall, Caravans.Shipment shipment, Caravans.Data data) {
		return start(level, hall, shipment.back() ? shipment.to() : shipment.from(), shipment.from(), Phase.ARRIVING, data);
	}

	/**
	 * Shows the caravan of the village {@code owner} in {@code hall}'s village, on the side toward {@code other}. Null
	 * (and nothing is shown) with the switch off, a party already out here, no player near, no Storehouse, or nowhere
	 * to walk.
	 */
	@Nullable
	private static Party start(ServerLevel level, BlockPos hall, BlockPos owner, BlockPos other, Phase phase, Caravans.Data data) {
		if (!ENABLED || party(level, hall) != null || !playerNear(level, hall)) {
			return null;
		}
		BlockPos store = level.getPoiManager().findClosest(h -> h.is(ModVillagers.STOREHOUSE_POI), hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY).orElse(null);
		if (store == null) {
			return null;
		}
		double dx = other.getX() - hall.getX();
		double dz = other.getZ() - hall.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1) {
			dx = 1;
			dz = 0;
		} else {
			dx /= length;
			dz /= length;
		}
		BlockPos yard = yard(level, store, dx, dz);
		if (yard == null) {
			return null;
		}
		BlockPos edge = edge(level, hall, yard, dx, dz);
		if (edge.distSqr(yard) < 9) {
			return null; // walled in: no walk to show
		}
		BlockPos from = phase == Phase.LEAVING ? yard : edge;
		BlockPos toward = phase == Phase.LEAVING ? edge : yard;
		float yaw = (float) (Math.atan2(-(toward.getX() - from.getX()), toward.getZ() - from.getZ()) * 180 / Math.PI);
		Caravans.Village village = data.village(owner);
		Component name = Component.translatable("entity.aliveworkplace.caravan_carter", village != null ? village.name() : VillageHalls.name(level, owner));
		Villager carter = carter(level, from, yaw, name);
		if (carter == null) {
			return null;
		}
		Item carpet = carpet(colour(level, data, owner));
		List<Llama> llamas = new ArrayList<>();
		// the llamas stand in a line behind the carter, where there's room; else with him
		double bx = from.getX() - toward.getX();
		double bz = from.getZ() - toward.getZ();
		double back = Math.max(1, Math.sqrt(bx * bx + bz * bz));
		for (int i = 0; i < 2; i++) {
			BlockPos behind = BlockPos.containing(from.getX() + 0.5 + bx / back * 1.6 * (i + 1), from.getY(), from.getZ() + 0.5 + bz / back * 1.6 * (i + 1));
			Llama llama = llama(level, standable(level, behind) ? behind : from, yaw, carpet, Math.floorMod(owner.hashCode() + i, Llama.Variant.values().length));
			if (llama == null) {
				LIVE.remove(carter);
				llamas.forEach(l -> {
					LIVE.remove(l);
					l.discard();
				});
				carter.discard();
				return null;
			}
			llama.setLeashedTo(carter, true);
			llamas.add(llama);
		}
		Party party = new Party(level, hall.immutable(), store.immutable(), yard, edge, carter, llamas, phase);
		PARTIES.add(party);
		party.walk(toward, true);
		return party;
	}

	private static boolean playerNear(ServerLevel level, BlockPos hall) {
		return level.players().stream().anyMatch(p -> p.isAlive() && !p.isSpectator() && p.blockPosition().distSqr(hall) <= (double) SEE_RANGE * SEE_RANGE);
	}

	/** The carter: a villager in the porter's outfit that is nothing but a walker (see the class comment). */
	@Nullable
	private static Villager carter(ServerLevel level, BlockPos at, float yaw, Component name) {
		Villager carter = EntityType.VILLAGER.create(level);
		if (carter == null) {
			return null;
		}
		carter.addTag(TAG);
		carter.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
		carter.setYHeadRot(yaw);
		carter.setVillagerData(carter.getVillagerData().setType(VillagerType.byBiome(level.getBiome(at))).setProfession(ModVillagers.PORTER));
		carter.setOffers(new MerchantOffers()); // nothing to trade
		carter.setVillagerXp(1);
		carter.setCustomName(name);
		carter.setInvulnerable(true);
		carter.setPersistenceRequired();
		carter.setCanPickUpLoot(false);
		carter.setAge(LIFE_TICKS * 2); // grown, and never ready for a family
		carter.refreshBrain(level); // tagged: the new brain has no behaviours (VillagerMixin)
		if (carter.getNavigation() instanceof GroundPathNavigation ground) {
			ground.setCanOpenDoors(false); // nobody opens doors for him: his way goes round houses
		}
		LIVE.add(carter);
		level.addFreshEntity(carter);
		return carter;
	}

	/** A pack llama: a chest, a carpet, and no will of its own but to follow its lead. */
	@Nullable
	private static Llama llama(ServerLevel level, BlockPos at, float yaw, Item carpet, int variant) {
		Llama llama = EntityType.LLAMA.create(level);
		if (llama == null) {
			return null;
		}
		llama.addTag(TAG);
		llama.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
		llama.setVariant(Llama.Variant.values()[variant]);
		llama.setTamed(true);
		llama.setChest(true);
		llama.setBodyArmorItem(new ItemStack(carpet));
		llama.setDropChance(EquipmentSlot.BODY, 0f);
		llama.setInvulnerable(true);
		llama.setPersistenceRequired();
		llama.setAge(LIFE_TICKS * 2); // grown, and never in the mood
		// no strolling, panicking, spitting, breeding or following hay: the lead moves it
		((MobAccessor) llama).aliveworkplace$goals().removeAllGoals(goal -> !(goal instanceof FloatGoal));
		((MobAccessor) llama).aliveworkplace$targets().removeAllGoals(goal -> true);
		LIVE.add(llama);
		level.addFreshEntity(llama);
		return llama;
	}

	/**
	 * The colour of {@code village}'s caravans: its Village Banner's base colour when it has one (its hall's, or as its
	 * hall's last round wrote it on the caravans' list when the hall isn't loaded), otherwise one picked from the
	 * hall's position.
	 */
	public static DyeColor colour(ServerLevel level, Caravans.Data data, BlockPos village) {
		DyeColor colour = null;
		if (VillageBanners.ENABLED) {
			if (level.isLoaded(village) && level.getBlockEntity(village) instanceof VillageHallBlockEntity entity) {
				VillageBanners.Colours colours = VillageBanners.of(entity);
				colour = colours == null ? null : colours.base();
			} else {
				colour = data.colour(village);
			}
		}
		return colour != null ? colour : picked(village);
	}

	/** The colour a village without a banner gets, from where its hall stands. */
	public static DyeColor picked(BlockPos hall) {
		return DyeColor.byId(Math.floorMod(hall.hashCode(), DyeColor.values().length));
	}

	/** The carpet of {@code colour}. */
	public static Item carpet(DyeColor colour) {
		return Lookup.value(BuiltInRegistries.ITEM, ResourceLocation.withDefaultNamespace(colour.getName() + "_carpet"));
	}

	/** Where a party stands by the Storehouse: the nearest free spot, under the open sky and on the way out if there is one. */
	@Nullable
	private static BlockPos yard(ServerLevel level, BlockPos store, double dx, double dz) {
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (int x = -YARD_REACH; x <= YARD_REACH; x++) {
			for (int z = -YARD_REACH; z <= YARD_REACH; z++) {
				if (x == 0 && z == 0) {
					continue;
				}
				for (int y = -2; y <= 2; y++) {
					BlockPos p = store.offset(x, y, z);
					if (!standable(level, p)) {
						continue;
					}
					double score = x * x + z * z + y * y * 2 + (level.canSeeSky(p) ? 0 : 200) - (x * dx + z * dz) * 0.5;
					if (score < bestScore) {
						bestScore = score;
						best = p;
					}
				}
			}
		}
		return best;
	}

	/**
	 * Where a party leaves the village: from {@code yard}, block by block toward the other village over the ground
	 * (up or down 3 at a step), as far as the village reaches from its hall and the world is awake. A step with no
	 * footing (a pond, a wall) is passed over; the carter finds his own way round.
	 */
	private static BlockPos edge(ServerLevel level, BlockPos hall, BlockPos yard, double dx, double dz) {
		BlockPos last = yard;
		double x = yard.getX() + 0.5;
		double z = yard.getZ() + 0.5;
		int y = yard.getY();
		for (int i = 0; i < VillageHalls.RADIUS * 2 + 16; i++) {
			x += dx;
			z += dz;
			BlockPos column = BlockPos.containing(x, y, z);
			if (!level.isPositionEntityTicking(column)) {
				break;
			}
			BlockPos step = stand(level, column);
			if (step != null) {
				last = step;
				y = step.getY();
			}
			double hx = column.getX() - hall.getX();
			double hz = column.getZ() - hall.getZ();
			if (hx * hx + hz * hz >= (double) VillageHalls.RADIUS * VillageHalls.RADIUS) {
				break;
			}
		}
		return last;
	}

	/** The footing in {@code near}'s column within 3 blocks up or down, nearest first; null if there's none. */
	@Nullable
	private static BlockPos stand(ServerLevel level, BlockPos near) {
		for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
			BlockPos p = near.above(dy);
			if (standable(level, p)) {
				return p;
			}
		}
		return null;
	}

	/** Something to stand on, and two blocks of room without water or lava. */
	private static boolean standable(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState below = level.getBlockState(pos.below());
		if (below.getCollisionShape(level, pos.below()).isEmpty() || !below.getFluidState().isEmpty()) {
			return false;
		}
		for (BlockPos p : List.of(pos, pos.above())) {
			BlockState state = level.getBlockState(p);
			if (!state.getCollisionShape(level, p).isEmpty() || !state.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
