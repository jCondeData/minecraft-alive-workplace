package io.github.jcondedata.aliveworkplace.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Pathfinder;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.work.Stations;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Old villages renewed (ROADMAP 27.21). One old house at a time ({@link OldHouses}, renewable), at most one every
 * {@link #DAYS_BETWEEN} days in a village, the Steward proposes to rebuild in its zone's style: "Renew the old house 14
 * blocks west as a Stone House (Cherry)". What it becomes comes from its {@link RenewalLists renewal list}: a home's (the
 * first building with at least as many beds) or its job's (the building 27.11-27.14 give that job), the first that fits
 * the old footprint plus {@link #MARGIN} blocks on each side, inside the renew zones, its front where the old door was.
 *
 * <p>Approved (by a player, or by the Steward himself in Run the village), the old house is scanned into a blueprint
 * ({@code renewal/<hall>/<n>}, saved as the Scan Tool saves) and the builder takes it down as a deconstruction, its
 * blocks going to the store; once that is done the new one goes up on the cleared plot ({@link #siteFinished}). The
 * villagers who slept there get the new beds and its worker keeps the job and takes the new workstation. Everything is
 * saved on the hall ({@link Book}); cancelling either site ends the renewal and leaves the plot free. Config
 * {@code stewardRenewal}.
 */
public final class Renewals {
	/** Config {@code stewardRenewal}: off, the Steward never proposes to renew an old house (those under way finish). */
	public static boolean ENABLED = true;
	/** The rule a renewal proposal stands for (declining it holds renewals back {@link StewardDesk#DECLINE_DAYS} days). */
	public static final ResourceLocation RULE = AliveWorkplace.id("steward/renewal");
	/** At most one renewal started every this many days in a village. */
	public static final int DAYS_BETWEEN = 2;
	/** How far past the old footprint, on each side, the new building may reach. */
	public static final int MARGIN = 3;
	/** Where the scans of old houses are saved: {@code aliveworkplace:renewal/<hall>/<n>}. */
	public static final String FOLDER = "renewal";

	/** Someone who lived or worked in the old house: the villager, and for a worker their job's id ("" for a sleeper). */
	public record Occupant(UUID villager, String job) {
		public static final Codec<Occupant> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("villager").forGetter(Occupant::villager),
			Codec.STRING.optionalFieldOf("job", "").forGetter(Occupant::job)
		).apply(i, Occupant::new));
	}

	/**
	 * One renewal under way: the old house's scan and where it stood, the new building and where it goes, who slept and
	 * worked there, the site going now and whether that is the new building ({@code building}) or the take-down.
	 */
	public record Renewal(ResourceLocation scan, BlueprintData.Placement old, ResourceLocation blueprint, BlueprintData.Placement placement,
						  List<Occupant> sleepers, List<Occupant> workers, UUID site, boolean building) {
		public static final Codec<Renewal> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("scan").forGetter(Renewal::scan),
			BlueprintData.Placement.CODEC.fieldOf("old").forGetter(Renewal::old),
			ResourceLocation.CODEC.fieldOf("blueprint").forGetter(Renewal::blueprint),
			BlueprintData.Placement.CODEC.fieldOf("placement").forGetter(Renewal::placement),
			Occupant.CODEC.listOf().optionalFieldOf("sleepers", List.of()).forGetter(Renewal::sleepers),
			Occupant.CODEC.listOf().optionalFieldOf("workers", List.of()).forGetter(Renewal::workers),
			UUIDUtil.CODEC.fieldOf("site").forGetter(Renewal::site),
			Codec.BOOL.optionalFieldOf("building", false).forGetter(Renewal::building)
		).apply(i, Renewal::new));

		public Renewal {
			sleepers = List.copyOf(sleepers);
			workers = List.copyOf(workers);
		}

		Renewal next(UUID newSite) {
			return new Renewal(scan, old, blueprint, placement, sleepers, workers, newSite, true);
		}
	}

	/** The hall's renewals under way and the day the last one started. Saves from before 27.21 load as {@link #EMPTY}. */
	public record Book(List<Renewal> active, long lastDay) {
		public static final Book EMPTY = new Book(List.of(), -100L);
		public static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
			Renewal.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(Book::active),
			Codec.LONG.optionalFieldOf("last_day", -100L).forGetter(Book::lastDay)
		).apply(i, Book::new));

		public Book {
			active = List.copyOf(active);
		}

		Book with(List<Renewal> list) {
			return new Book(list, lastDay);
		}
	}

	/** What an old house would become: the blueprint (in the zone's style), where it goes and the box it fills. */
	public record Replacement(ResourceLocation blueprint, BlueprintData.Placement placement, BoundingBox box, String zone) {
	}

	/** Houses already found not to fit anything (kept until the survey that found them is redone). */
	private static final Set<OldHouses.House> UNFIT = new HashSet<>();

	// ---- proposing -----------------------------------------------------------------------------------------------

	/**
	 * At the hall each second (from {@link StewardDesk#plan}): with no renewal on the desk or under way, none started in
	 * the last {@link #DAYS_BETWEEN} days and the rule not declined, the nearest renewable old house that fits something
	 * becomes a proposal.
	 */
	static void propose(ServerLevel level, BlockPos hall, Villager steward) {
		if (!ENABLED || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity)) {
			return;
		}
		StewardDesk.State state = StewardDesk.of(level, hall);
		long day = StewardWishes.day(level);
		if (state.proposals().stream().anyMatch(StewardDesk.Proposal::isRenewal) || !state.renewals().active().isEmpty()
			|| day - state.renewals().lastDay() < DAYS_BETWEEN || StewardDesk.declined(state, RULE, day)
			|| state.proposals().size() >= StewardDesk.MAX_PROPOSALS) {
			return;
		}
		if (UNFIT.size() > 512) {
			UNFIT.clear();
		}
		for (OldHouses.House house : OldHouses.result(level, hall).orElse(List.of())) {
			if (!house.renewable() || UNFIT.contains(house)) {
				continue;
			}
			Optional<Replacement> replacement = replacement(level, hall, house);
			if (replacement.isEmpty()) {
				UNFIT.add(house);
				continue;
			}
			if (StewardDesk.offerRenewal(level, hall, house, replacement.get()).isPresent()) {
				StewardDesk.tell(level, hall, steward);
			}
			return;
		}
	}

	/** The proposal's name: "Renew the old house 14 blocks west as a Stone House (Cherry)". */
	static Component title(StewardDesk.Proposal p) {
		long distance = p.numbers().isEmpty() ? 0 : p.numbers().get(0);
		int eighth = p.numbers().size() < 2 ? 0 : (int) (long) p.numbers().get(1);
		String[] directions = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
		return Component.translatable("steward.aliveworkplace.renew.title", distance,
			Component.translatable("screen.aliveworkplace.hall.dir." + directions[Math.floorMod(eighth, 8)]), Blueprints.displayName(p.blueprint()));
	}

	/** How far and which way (an eighth, as {@link Pathfinder#direction} counts them) the house stands from the hall. */
	static List<Long> where(BlockPos hall, BoundingBox box) {
		BlockPos centre = box.getCenter();
		double dx = centre.getX() - hall.getX();
		double dz = centre.getZ() - hall.getZ();
		long eighth = Math.floorMod((int) Math.round(Math.atan2(-dx, dz) / (Math.PI / 4)), 8);
		return List.of(Math.round(Math.sqrt(dx * dx + dz * dz)), eighth);
	}

	// ---- what it becomes -----------------------------------------------------------------------------------------

	/** What the old house measured whole: its blocks, beds, job (the first profession with a renewal list) and door. */
	record Inside(LongSet blocks, int beds, @Nullable ResourceLocation job, @Nullable BlockPos door) {
	}

	static Inside inside(ServerLevel level, OldHouses.Measure measure) {
		while (!measure.done()) {
			measure.step(1 << 16);
		}
		int beds = 0;
		ResourceLocation job = null;
		BlockPos door = null;
		BoundingBox box = measure.box();
		for (long p : measure.accepted()) {
			BlockPos pos = BlockPos.of(p);
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
				beds++;
			}
			if (state.is(BlockTags.DOORS) && state.hasProperty(DoorBlock.HALF) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
				&& (door == null || edge(box, pos) < edge(box, door))) {
				door = pos;
			}
			if (job == null) {
				Optional<Holder<PoiType>> poi = PoiTypes.forState(state);
				if (poi.isPresent()) {
					job = jobOf(poi.get());
				}
			}
		}
		return new Inside(measure.accepted(), beds, job, door);
	}

	/** The job whose workstation {@code poi} is, among those with a renewal list (vanilla's first). */
	@Nullable
	static ResourceLocation jobOf(Holder<PoiType> poi) {
		for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
			ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
			if (profession != VillagerProfession.NONE && profession.heldJobSite().test(poi) && RenewalLists.forJob(id).isPresent()) {
				return id;
			}
		}
		return null;
	}

	/** How far {@code pos} is from the nearest side of {@code box}. */
	private static int edge(BoundingBox box, BlockPos pos) {
		return Math.min(Math.min(pos.getX() - box.minX(), box.maxX() - pos.getX()), Math.min(pos.getZ() - box.minZ(), box.maxZ() - pos.getZ()));
	}

	/** The side of {@code box} nearest {@code door}; with no door, the side towards the hall. */
	static Direction front(BoundingBox box, @Nullable BlockPos door, BlockPos hall) {
		BlockPos at = door != null ? door : hall;
		int[] d = {at.getZ() - box.minZ(), box.maxX() - at.getX(), box.maxZ() - at.getZ(), at.getX() - box.minX()};
		Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
		int best = 0;
		for (int i = 1; i < 4; i++) {
			if (d[i] < d[best]) {
				best = i;
			}
		}
		return sides[best];
	}

	/** The renewal list for the house, its kind told by its workstation (a list for that job) or else a home. */
	static Optional<RenewalLists.RenewalList> listFor(Inside inside) {
		if (inside.job() != null) {
			Optional<RenewalLists.RenewalList> list = RenewalLists.forJob(inside.job());
			if (list.isPresent()) {
				return list;
			}
		}
		return RenewalLists.homes();
	}

	/** What {@code house} becomes, if anything on its list fits. */
	public static Optional<Replacement> replacement(ServerLevel level, BlockPos hall, OldHouses.House house) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Optional.empty();
		}
		OldHouses.Measure measure = new OldHouses.Measure(level, house.seed());
		Inside inside = inside(level, measure);
		return replacement(level, hall, entity.plan(), measure.box(), inside);
	}

	static Optional<Replacement> replacement(ServerLevel level, BlockPos hall, CityPlan plan, BoundingBox old, Inside inside) {
		Optional<RenewalLists.RenewalList> list = listFor(inside);
		Optional<CityPlan.Zone> zone = plan.zoneAt(hall, new BlockPos(old.getCenter().getX(), old.minY(), old.getCenter().getZ()));
		if (list.isEmpty() || zone.isEmpty()) {
			return Optional.empty();
		}
		Direction front = front(old, inside.door(), hall);
		for (ResourceLocation base : list.get().buildings()) {
			if (list.get().home() && StewardConditions.beds(level, base) < inside.beds()) {
				continue;
			}
			ResourceLocation id = BlueprintStyles.styled(base, zone.get().style());
			Optional<Blueprint> blueprint = BlueprintLibrary.get(level, id);
			if (blueprint.isEmpty()) {
				id = base;
				blueprint = BlueprintLibrary.get(level, base);
			}
			if (blueprint.isEmpty()) {
				continue;
			}
			Optional<BlueprintData.Placement> placement = fit(level, old, blueprint.get().size(), front, inside.door());
			if (placement.isEmpty()) {
				continue;
			}
			BoundingBox box = BlueprintOutline.bounds(placement.get(), blueprint.get().size());
			if (!OldHouses.insideRenewZones(level, hall, plan, box) || StewardSafety.check(level, hall, box, false).isPresent()) {
				continue;
			}
			return Optional.of(new Replacement(id, placement.get(), box, zone.get().name()));
		}
		return Optional.empty();
	}

	/**
	 * Where a building of {@code size} goes on the old house's plot: its front (the template's z = 0 side) on the old
	 * front's line, centred on the old door, within the old footprint plus {@link #MARGIN} on every side; y = 0 where the
	 * old house's lowest blocks were. Empty if it doesn't fit.
	 */
	static Optional<BlueprintData.Placement> fit(ServerLevel level, BoundingBox old, Vec3i size, Direction front, @Nullable BlockPos door) {
		Rotation turn = switch (front) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		ResourceLocation dim = Ids.of(level.dimension());
		BoundingBox at0 = BlueprintOutline.bounds(new BlueprintData.Placement(dim, BlockPos.ZERO, turn, Mirror.NONE), size);
		int w = at0.getXSpan();
		int d = at0.getZSpan();
		int minX;
		int minZ;
		if (front.getAxis() == Direction.Axis.Z) {
			int centre = door != null ? door.getX() : (old.minX() + old.maxX()) / 2;
			Optional<Integer> x = clamp(centre - w / 2, old.minX() - MARGIN, old.maxX() + MARGIN - w + 1);
			minZ = front == Direction.NORTH ? old.minZ() : old.maxZ() - d + 1;
			if (x.isEmpty() || minZ < old.minZ() - MARGIN || minZ + d - 1 > old.maxZ() + MARGIN) {
				return Optional.empty();
			}
			minX = x.get();
		} else {
			int centre = door != null ? door.getZ() : (old.minZ() + old.maxZ()) / 2;
			Optional<Integer> z = clamp(centre - d / 2, old.minZ() - MARGIN, old.maxZ() + MARGIN - d + 1);
			minX = front == Direction.WEST ? old.minX() : old.maxX() - w + 1;
			if (z.isEmpty() || minX < old.minX() - MARGIN || minX + w - 1 > old.maxX() + MARGIN) {
				return Optional.empty();
			}
			minZ = z.get();
		}
		BlockPos origin = new BlockPos(minX - at0.minX(), old.minY(), minZ - at0.minZ());
		return Optional.of(new BlueprintData.Placement(dim, origin, turn, Mirror.NONE));
	}

	private static Optional<Integer> clamp(int value, int lo, int hi) {
		return lo > hi ? Optional.empty() : Optional.of(Math.max(lo, Math.min(hi, value)));
	}

	// ---- approving -----------------------------------------------------------------------------------------------

	/**
	 * Approves a renewal: the house measured again (gone or changed: the proposal goes), scanned into a blueprint and
	 * given to the builder to take down; the new building follows once that is done ({@link #siteFinished}).
	 */
	static StewardDesk.Outcome approve(ServerLevel level, BlockPos hall, @Nullable ServerPlayer player, Villager steward,
									   VillageHallBlockEntity entity, StewardDesk.Proposal proposal) {
		if (StewardDesk.openSites(level, hall).size() >= Stewards.maxOpenBuilds(level, steward)) {
			return StewardDesk.Outcome.FULL;
		}
		Optional<BoundingBox> oldBox = proposal.houseBox();
		Optional<BlockPos> seed = proposal.houseSeed();
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, proposal.blueprint());
		if (oldBox.isEmpty() || seed.isEmpty() || blueprint.isEmpty()) {
			StewardDesk.drop(level, hall, proposal.id());
			return StewardDesk.Outcome.GONE;
		}
		OldHouses.Measure measure = new OldHouses.Measure(level, seed.get());
		Inside inside = inside(level, measure);
		OldHouses.House house = measure.house(hall, entity.plan());
		if (!house.renewable() || !house.box().equals(oldBox.get())) {
			StewardDesk.drop(level, hall, proposal.id());
			return StewardDesk.Outcome.GONE;
		}
		BoundingBox box = BlueprintOutline.bounds(proposal.placement(), blueprint.get().size());
		if (StewardSafety.check(level, hall, box, player != null).isPresent()) {
			return StewardDesk.Outcome.UNSAFE;
		}
		for (BuildSite other : BuildSiteManager.get(level).all()) {
			if (other.placement().dimension().equals(proposal.placement().dimension()) && BlueprintLibrary.get(level, other.structure())
				.map(b -> {
					BoundingBox theirs = BlueprintOutline.bounds(other.placement(), b.size());
					return theirs.intersects(box) || theirs.intersects(oldBox.get());
				}).orElse(false)) {
				return StewardDesk.Outcome.OVERLAPS;
			}
		}
		// B86: the old house stands in the village even past every bench's reach (54 blocks out in the City run)
		Optional<StewardDesk.Builder> builder = StewardDesk.builderFor(level, hall, proposal, true);
		if (builder.isEmpty()) {
			return StewardDesk.Outcome.NO_BUILDER;
		}
		ResourceLocation scan = scan(level, hall, oldBox.get(), inside.blocks());
		if (scan == null) {
			return StewardDesk.Outcome.GONE;
		}
		List<Occupant> sleepers = new ArrayList<>();
		List<Occupant> workers = new ArrayList<>();
		for (Villager v : level.getEntities(EntityType.VILLAGER, new AABB(hall).inflate(VillageHalls.RADIUS * 2), Villager::isAlive)) {
			v.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> g.dimension().equals(level.dimension()) && oldBox.get().isInside(g.pos()))
				.ifPresent(g -> sleepers.add(new Occupant(v.getUUID(), "")));
			v.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g -> g.dimension().equals(level.dimension()) && oldBox.get().isInside(g.pos()))
				.ifPresent(g -> workers.add(new Occupant(v.getUUID(), BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString())));
		}
		Villager villager = builder.get().villager();
		UUID owner = entity.owner() != null ? entity.owner() : player != null ? player.getUUID() : villager.getUUID();
		String ownerName = entity.owner() != null ? entity.ownerName() : player != null ? player.getGameProfile().getName() : "";
		boolean busy = Builders.activeSite(level, villager) != null;
		BlueprintData.Placement old = new BlueprintData.Placement(Ids.of(level.dimension()),
			new BlockPos(oldBox.get().minX(), oldBox.get().minY(), oldBox.get().minZ()), Rotation.NONE, Mirror.NONE);
		BuildSite site = busy ? Builders.enqueue(level, villager, owner, ownerName, scan, old) : Builders.start(level, villager, owner, ownerName, scan, old);
		site.setDeconstruction();
		site.setStewardHall(hall);
		StewardDesk.State state = StewardDesk.of(level, hall);
		List<Renewal> active = new ArrayList<>(state.renewals().active());
		active.add(new Renewal(scan, old, proposal.blueprint(), proposal.placement(), sleepers, workers, site.id(), false));
		StewardDesk.save(level, hall, state.withRenewals(new Book(active, StewardWishes.day(level)))
			.withProposals(state.proposals().stream().filter(p -> p.id() != proposal.id()).toList()));
		UNFIT.remove(house);
		Chronicle.record(level, hall, Chronicle.Kind.PLANS, Component.translatable("chronicle.aliveworkplace.plans_renew",
			steward.getDisplayName(), Blueprints.displayName(proposal.blueprint()), villager.getDisplayName()));
		return busy ? StewardDesk.Outcome.QUEUED : StewardDesk.Outcome.STARTED;
	}

	/**
	 * Saves the old house as a blueprint ({@code aliveworkplace:renewal/<hall>/<n>}), as the Scan Tool saves: only its own
	 * blocks (not the ground or the air round it). Null if it can't be saved.
	 */
	@Nullable
	static ResourceLocation scan(ServerLevel level, BlockPos hall, BoundingBox box, LongSet blocks) {
		StructureTemplateManager manager = level.getServer().getStructureManager();
		String folder = FOLDER + "/" + hall.getX() + "_" + hall.getY() + "_" + hall.getZ();
		ResourceLocation id = null;
		for (int n = 1; id == null; n++) {
			ResourceLocation candidate = AliveWorkplace.id(folder + "/" + n);
			if (manager.get(candidate).isEmpty()) {
				id = candidate;
			}
		}
		BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
		Vec3i size = new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan());
		StructureTemplate template = manager.getOrCreate(id);
		template.fillFromWorld(level, min, size, false, Blocks.STRUCTURE_VOID);
		CompoundTag tag = template.save(new CompoundTag());
		ListTag all = Nbt.getList(tag, "blocks", Tag.TAG_COMPOUND);
		ListTag kept = new ListTag();
		for (int i = 0; i < all.size(); i++) {
			CompoundTag b = Nbt.compoundAt(all, i);
			ListTag p = Nbt.getList(b, "pos", Tag.TAG_INT);
			if (blocks.contains(min.offset(Nbt.intAt(p, 0), Nbt.intAt(p, 1), Nbt.intAt(p, 2)).asLong())) {
				kept.add(b);
			}
		}
		tag.put("blocks", kept);
		template.load(Lookup.lookup(BuiltInRegistries.BLOCK), tag);
		template.setAuthor("Steward");
		if (!manager.save(id)) {
			manager.remove(id);
			AliveWorkplace.LOG.warn("Could not save the scan of the old house at {}", min);
			return null;
		}
		AliveWorkplace.LOG.info("Scanned old house {} ({}x{}x{}, {} blocks)", id, size.getX(), size.getY(), size.getZ(), kept.size());
		return id;
	}

	/** Whether {@code structure} is the scan of an old house (no blueprint is handed back when it's taken down). */
	public static boolean isScan(ResourceLocation structure) {
		return structure.getNamespace().equals(AliveWorkplace.MOD_ID) && structure.getPath().startsWith(FOLDER + "/");
	}

	// ---- the swap ------------------------------------------------------------------------------------------------

	/** The renewal the site belongs to, if any. */
	static Optional<Renewal> of(ServerLevel level, BlockPos hall, UUID site) {
		return StewardDesk.of(level, hall).renewals().active().stream().filter(r -> r.site().equals(site)).findFirst();
	}

	/** The renewals under way at the hall. */
	public static List<Renewal> active(ServerLevel level, BlockPos hall) {
		return StewardDesk.of(level, hall).renewals().active();
	}

	/**
	 * A Steward's site finished (Builders.finish): the old house down, the new one starts on the cleared plot by the same
	 * builder; the new one up, its villagers move in and the chronicle notes it.
	 */
	public static void siteFinished(ServerLevel level, Villager villager, BuildSite site) {
		BlockPos hall = site.stewardHall();
		if (hall == null || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity)) {
			return;
		}
		Optional<Renewal> found = of(level, hall, site.id());
		if (found.isEmpty()) {
			return;
		}
		Renewal renewal = found.get();
		StewardDesk.State state = StewardDesk.of(level, hall);
		List<Renewal> active = new ArrayList<>(state.renewals().active());
		active.remove(renewal);
		if (!renewal.building()) {
			boolean busy = Builders.activeSite(level, villager) != null;
			BuildSite next = busy
				? Builders.enqueue(level, villager, site.owner(), site.ownerName(), renewal.blueprint(), renewal.placement())
				: Builders.start(level, villager, site.owner(), site.ownerName(), renewal.blueprint(), renewal.placement());
			next.setStewardHall(hall);
			active.add(renewal.next(next.id()));
			StewardDesk.save(level, hall, state.withRenewals(state.renewals().with(active)));
			return;
		}
		StewardDesk.save(level, hall, state.withRenewals(state.renewals().with(active)));
		moveIn(level, renewal);
		Chronicle.record(level, hall, Chronicle.Kind.BUILT, Component.translatable("chronicle.aliveworkplace.renewed",
			villager.getDisplayName(), Blueprints.displayName(renewal.blueprint())));
	}

	/** A Steward's site cancelled: the renewal it belonged to ends, and the plot is free to be proposed again. */
	public static void siteCancelled(ServerLevel level, BuildSite site) {
		BlockPos hall = site.stewardHall();
		if (hall == null || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity)) {
			return;
		}
		of(level, hall, site.id()).ifPresent(r -> forget(level, hall, r));
	}

	private static void forget(ServerLevel level, BlockPos hall, Renewal renewal) {
		StewardDesk.State state = StewardDesk.of(level, hall);
		List<Renewal> active = new ArrayList<>(state.renewals().active());
		active.remove(renewal);
		StewardDesk.save(level, hall, state.withRenewals(state.renewals().with(active)));
	}

	/** Renewals whose site is gone (cancelled while its hall was unloaded, or removed by a command) end. */
	static void tidy(ServerLevel level, BlockPos hall) {
		for (Renewal r : active(level, hall)) {
			if (BuildSiteManager.get(level).get(r.site()) == null) {
				forget(level, hall, r);
			}
		}
	}

	/**
	 * The villagers who slept in the old house take the new beds (one each, in order; a bed someone else took while it
	 * went up is given back to them), and each worker keeps their job and takes the new workstation of it.
	 */
	static void moveIn(ServerLevel level, Renewal renewal) {
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, renewal.blueprint());
		if (blueprint.isEmpty()) {
			return;
		}
		BoundingBox box = BlueprintOutline.bounds(renewal.placement(), blueprint.get().size());
		BlockPos centre = box.getCenter();
		int reach = Math.max(box.getXSpan(), Math.max(box.getYSpan(), box.getZSpan()));
		PoiManager poi = level.getPoiManager();
		Set<UUID> ours = new HashSet<>();
		renewal.sleepers().forEach(o -> ours.add(o.villager()));
		List<BlockPos> beds = poi.findAll(h -> h.is(PoiTypes.HOME), box::isInside, centre, reach, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
		int next = 0;
		for (Occupant sleeper : renewal.sleepers()) {
			if (!(level.getEntity(sleeper.villager()) instanceof Villager v) || next >= beds.size()) {
				continue;
			}
			BlockPos bed = beds.get(next++);
			GlobalPos at = GlobalPos.of(level.dimension(), bed);
			if (poi.getFreeTickets(bed) <= 0) {
				takeBack(level, MemoryModuleType.HOME, at, ours);
			}
			if (poi.getFreeTickets(bed) > 0) {
				poi.take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
			}
			v.getBrain().setMemory(MemoryModuleType.HOME, at);
		}
		Set<UUID> workers = new HashSet<>();
		renewal.workers().forEach(o -> workers.add(o.villager()));
		for (Occupant worker : renewal.workers()) {
			ResourceLocation jobId = ResourceLocation.tryParse(worker.job());
			VillagerProfession profession = jobId == null ? null : BuiltInRegistries.VILLAGER_PROFESSION.getOptional(jobId).orElse(null);
			if (!(level.getEntity(worker.villager()) instanceof Villager v) || profession == null || profession == VillagerProfession.NONE) {
				continue;
			}
			Optional<BlockPos> station = poi.findAll(profession.heldJobSite(), box::isInside, centre, reach, PoiManager.Occupancy.ANY)
				.map(BlockPos::immutable).min(Comparator.comparingLong(BlockPos::asLong));
			if (station.isEmpty()) {
				continue;
			}
			GlobalPos at = GlobalPos.of(level.dimension(), station.get());
			if (poi.getFreeTickets(station.get()) <= 0 && v.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(at::equals).isEmpty()) {
				takeBack(level, MemoryModuleType.JOB_SITE, at, workers);
			}
			if (v.getVillagerData().getProfession() != profession) {
				v.setVillagerData(v.getVillagerData().setProfession(profession)); // lost it while the house was down
			}
			Stations.assign(level, v, station.get(), profession);
		}
	}

	/** Someone else, not one of {@code ours}, took the place at {@code at} while the house went up: they let it go. */
	private static void takeBack(ServerLevel level, MemoryModuleType<GlobalPos> memory, GlobalPos at, Set<UUID> ours) {
		boolean released = false;
		for (Villager other : level.getEntities(EntityType.VILLAGER, new AABB(at.pos()).inflate(VillageHalls.RADIUS * 2),
				o -> !ours.contains(o.getUUID()) && o.getBrain().getMemory(memory).filter(at::equals).isPresent())) {
			other.getBrain().eraseMemory(memory);
			released = true;
		}
		if (released || level.getPoiManager().getFreeTickets(at.pos()) <= 0) {
			level.getPoiManager().release(at.pos());
		}
	}

	private Renewals() {
	}
}
