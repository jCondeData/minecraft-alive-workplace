package io.github.jcondedata.aliveworkplace.colony;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Founder;
import io.github.jcondedata.aliveworkplace.mail.Mail;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.travel.TravelPostBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * A colony is born (ROADMAP 33.10). When the settlers' journey ({@link Settlers}) is over, the spot's chunks are loaded
 * for a minute ({@link #TICKET}, like {@code KeepLoaded}'s) and the order is {@code arriving}; once the spot is awake the
 * settlers make camp on the nearest open ground within {@link #ROOM} blocks (the Settler's Wagon's own checks), with a
 * Village Hall and a Travel Post in front of it:
 * <ul>
 * <li>the hall is named from the charter (or made up), owned by the mother village's owner, and its chronicle opens
 * with the founding; the mother's chronicle notes it too;</li>
 * <li>the post joins the travel network under the colony's name, known to the owner and their friends;</li>
 * <li>the settlers step out as they were: the builder employed at the camp's Blueprint Table, the others keeping their
 * trade; the chest holds the supplies, and the Starter Cottage and Storehouse blueprints in the mother's style wait
 * there unless the builder could start them at once on either side of the camp;</li>
 * <li>mother and colony get a sister route ({@link Caravans.Data#addSisters}); the owner gets a letter, and a chat line
 * if online.</li>
 * </ul>
 * With no room (all water, a cliff) the order is {@code returning}: when the mother village is next awake the settlers
 * are back by its hall, the supplies in its Storehouses and the cost in its treasury.
 */
public final class ColonyArrival {
	/** How far from the spot the settlers look for open ground. */
	public static int ROOM = 48;
	/** How long the spot's chunks stay loaded once the settlers get there: a minute. */
	public static final int KEEP_TICKS = 1200;
	/** The ticket's radius in chunks: the spot's chunk and four around it awake, two more loaded. */
	private static final int TICKET_RADIUS = 6;
	/** How long the settlers wait for the spot's chunks before making do with what has loaded. */
	private static final int WAIT_FOR_CHUNKS = 600;
	/** Between the places the settlers try for the camp. */
	private static final int STEP = 3;
	/** Where the hall and the post stand: two blocks in front of the camp, left and right of the campfire (camp template space). */
	private static final BlockPos HALL_AT = new BlockPos(2, 0, -2);
	private static final BlockPos POST_AT = new BlockPos(8, 0, -2);
	/** The colony's own camp (33.14): two wagons, the hall under an awning on a timber platform, the post at the front. */
	public static final ResourceLocation COLONY_CAMP = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("camp/colony_camp");
	/** Where the hall and the post stand in the Colony Camp (template space): on the platform, and at the front right. */
	static final BlockPos HALL_IN_CAMP = new BlockPos(7, 1, 7);
	static final BlockPos POST_IN_CAMP = new BlockPos(10, 0, 3);
	/** Room left between the camp and a build beside it, nearest first. */
	private static final int[] GAPS = {2, 4, 7};
	/** Most a footprint's ground may rise or fall from where the build stands, and how many of its columns may do so. */
	private static final int UNEVEN = 2;
	private static final double UNEVEN_SHARE = 0.15;

	static final TicketType<ChunkPos> TICKET = TicketType.create("aliveworkplace_colony", Comparator.comparingLong(ChunkPos::toLong), KEEP_TICKS);

	/** A build the colony's builder starts: the blueprint (in the mother's style) and where it stands. */
	public record Build(ResourceLocation structure, Vec3i size, BlueprintData.Placement placement) {
		BoundingBox box() {
			return BlueprintOutline.bounds(placement, size);
		}
	}

	/** Where the camp goes: its front's middle and which way it faces. */
	public record Camp(BlockPos front, Direction facing, BoundingBox box) {
	}

	private ColonyArrival() {
	}

	/** An order after its journey: arriving when the time is up, the camp once the spot is awake, home again with no room. */
	static void tick(MinecraftServer server, RealmData data, RealmData.Order order) {
		ServerLevel level = server.getLevel(order.mother().dimension());
		if (level == null) {
			return;
		}
		long now = level.getGameTime();
		switch (order.state()) {
			case RealmData.ON_ROAD -> {
				if (now >= order.arrives()) {
					keepLoaded(level, order.spot());
					data.putOrder(order.withState(RealmData.ARRIVING, order.leaves(), now));
				}
			}
			case RealmData.ARRIVING -> {
				if (now % 20 == 0) {
					keepLoaded(level, order.spot()); // (tickets aren't saved: a reload mid-arrival asks again)
				}
				if (level.isPositionEntityTicking(order.spot()) || now - order.arrives() >= WAIT_FOR_CHUNKS) {
					arrive(level, data, order);
				}
			}
			case RealmData.RETURNING -> {
				if (level.isPositionEntityTicking(order.mother().pos())) {
					comeHome(level, data, order);
				}
			}
			default -> {
			}
		}
	}

	/** Loads the chunks round {@code spot} for a minute. */
	static void keepLoaded(ServerLevel level, BlockPos spot) {
		ChunkPos chunk = new ChunkPos(spot);
		level.getChunkSource().addRegionTicket(TICKET, chunk, TICKET_RADIUS, chunk);
	}

	// ---- the camp --------------------------------------------------------------------------------------------------------

	/** The way the camp faces: toward the mother village, so the hall's front looks home. */
	static Direction facing(RealmData.Order order) {
		int dx = order.mother().pos().getX() - order.spot().getX();
		int dz = order.mother().pos().getZ() - order.spot().getZ();
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** The top of the ground in the column {@code x, z}: the first open block over it, past trees and plants (null: water there). */
	@Nullable
	static BlockPos ground(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		while (y > level.getMinBuildHeight()) {
			BlockPos below = new BlockPos(x, y - 1, z);
			BlockState state = level.getBlockState(below);
			if (!level.getFluidState(below).isEmpty()) {
				return null;
			}
			if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.isAir() || state.canBeReplaced()) {
				y--;
				continue;
			}
			return new BlockPos(x, y, z);
		}
		return null;
	}

	/** The nearest open ground within {@link #ROOM} blocks of the order's spot where the camp fits, or null. */
	@Nullable
	public static Camp findCamp(ServerLevel level, RealmData.Order order) {
		Direction facing = facing(order);
		Rotation rotation = BlueprintItem.rotationFacing(facing);
		BlockPos spot = order.spot();
		List<int[]> tries = new ArrayList<>();
		for (int dx = -ROOM; dx <= ROOM; dx += STEP) {
			for (int dz = -ROOM; dz <= ROOM; dz += STEP) {
				if (dx * dx + dz * dz <= ROOM * ROOM) {
					tries.add(new int[] {dx, dz});
				}
			}
		}
		tries.sort(Comparator.comparingInt((int[] t) -> t[0] * t[0] + t[1] * t[1]).thenComparingInt(t -> t[0]).thenComparingInt(t -> t[1]));
		for (int[] t : tries) {
			int x = spot.getX() + t[0];
			int z = spot.getZ() + t[1];
			if (!level.hasChunkAt(new BlockPos(x, spot.getY(), z))) {
				continue;
			}
			BlockPos front = ground(level, x, z);
			if (front == null) {
				continue;
			}
			boolean colonyCamp = hasColonyCamp(level);
			BoundingBox box = SettlersWagonItem.campBox(level, colonyCamp ? COLONY_CAMP : SettlersWagonItem.CAMP, front, rotation);
			if (box == null) {
				return null; // no camp to place at all
			}
			if (loaded(level, box) && dry(level, box) && SettlersWagonItem.roomFor(level, box)
				&& (colonyCamp || standsOn(level, at(box, front, rotation, HALL_AT)) && standsOn(level, at(box, front, rotation, POST_AT)))) {
				return new Camp(front, facing, box);
			}
		}
		return null;
	}

	/** Whether the Colony Camp's template is there (it ships with the mod; an older settlers' camp stands in without it). */
	static boolean hasColonyCamp(ServerLevel level) {
		return level.getStructureManager().get(COLONY_CAMP).isPresent();
	}

	/** Whether every chunk under {@code box} is loaded. */
	private static boolean loaded(ServerLevel level, BoundingBox box) {
		return level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ());
	}

	/** Whether no water or lava stands in or under {@code box}. */
	private static boolean dry(ServerLevel level, BoundingBox box) {
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY() - 1, box.minZ(), box.maxX(), box.minY() + 1, box.maxZ())) {
			if (!level.getFluidState(p).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** A point given in the camp template's space, in the world, for the camp whose front's middle is {@code front}. */
	static BlockPos at(BoundingBox box, BlockPos front, Rotation rotation, BlockPos local) {
		BlockPos anchor = BlueprintItem.anchor(new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan()));
		// (the box is the rotated template: its spans along x and z swap when the camp is turned a quarter)
		if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
			anchor = BlueprintItem.anchor(new Vec3i(box.getZSpan(), box.getYSpan(), box.getXSpan()));
		}
		return front.offset(StructureTemplate.transform(local.subtract(anchor), Mirror.NONE, rotation, BlockPos.ZERO));
	}

	/** Whether a hall or a post at {@code pos} has dry ground under it within a block of the camp's floor. */
	private static boolean standsOn(ServerLevel level, BlockPos pos) {
		BlockPos ground = ground(level, pos.getX(), pos.getZ());
		return ground != null && Math.abs(ground.getY() - pos.getY()) <= 1;
	}

	// ---- arriving --------------------------------------------------------------------------------------------------------

	/** The settlers are at the spot: the camp, hall, post, settlers and builds, or (no room) the way home. */
	static void arrive(ServerLevel level, RealmData data, RealmData.Order order) {
		Camp camp = findCamp(level, order);
		if (camp == null) {
			data.putOrder(order.withState(RealmData.RETURNING, order.leaves(), order.arrives()));
			if (level.isPositionEntityTicking(order.mother().pos())) {
				comeHome(level, data, order);
			}
			return;
		}
		found(level, data, order, camp);
	}

	/** Founds the colony at {@code camp}; the new hall. */
	static BlockPos found(ServerLevel level, RealmData data, RealmData.Order order, Camp camp) {
		MinecraftServer server = level.getServer();
		BlockPos motherHall = order.mother().pos();
		level.getChunkAt(motherHall); // the mother's hall, for its owner, name and chronicle (one chunk, once)
		VillageHallBlockEntity mother = level.getBlockEntity(motherHall) instanceof VillageHallBlockEntity e ? e : null;
		Component motherName = VillageHalls.name(level, motherHall);
		UUID owner = mother == null ? null : mother.owner();
		String ownerName = mother == null ? "" : mother.ownerName();
		Rotation rotation = BlueprintItem.rotationFacing(camp.facing());

		// The camp, its hall and its post.
		boolean colonyCamp = hasColonyCamp(level);
		BoundingBox box = SettlersWagonItem.placeCamp(level, colonyCamp ? COLONY_CAMP : SettlersWagonItem.CAMP, camp.front(), rotation);
		BlockState hallState = ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, camp.facing());
		BlockState postState = ModBlocks.TRAVEL_POST.defaultBlockState().setValue(TravelPostBlock.FACING, camp.facing());
		// the Colony Camp has their places: the hall under the awning on the platform, the post by its lantern (33.14)
		BlockPos hall = colonyCamp ? placeExactly(level, at(box, camp.front(), rotation, HALL_IN_CAMP), hallState)
			: place(level, at(box, camp.front(), rotation, HALL_AT), hallState);
		BlockPos post = colonyCamp ? placeExactly(level, at(box, camp.front(), rotation, POST_IN_CAMP), postState)
			: place(level, at(box, camp.front(), rotation, POST_AT), postState);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		order.name().ifPresent(n -> entity.setCustomName(Component.literal(n.getString())));
		if (owner != null) {
			entity.setOwner(owner, ownerName);
		}
		Component colony = VillageHalls.name(level, hall);
		TravelNetwork network = TravelNetwork.get(server);
		TravelNetwork.Post onNetwork = network.add(GlobalPos.of(level.dimension(), post), colony.getString());
		if (owner != null) {
			network.visit(owner, onNetwork);
			for (UUID friend : Friends.get(server).of(owner).keySet()) {
				network.visit(friend, onNetwork);
			}
		}

		// The settlers, as they were.
		List<Villager> settlers = new ArrayList<>();
		Villager builder = null;
		for (CompoundTag saved : order.settlers()) {
			BlockPos stand = SettlersWagonItem.standingSpot(level, box, settlers.size());
			Villager v = restore(level, saved, stand != null ? stand : box.getCenter());
			if (v == null) {
				continue;
			}
			settlers.add(v);
			if (builder == null && order.builder().isPresent() && saved.hasUUID("UUID") && saved.getUUID("UUID").equals(order.builder().get())) {
				builder = v;
			}
		}
		if (builder == null && !settlers.isEmpty()) {
			builder = settlers.get(0);
		}
		Component names = Settlers.names(settlers.stream().map(Villager::getDisplayName).toList());

		// The chest: the supplies and the two blueprints in the mother's style.
		List<String> styles = Founder.styles(level, motherHall);
		List<Build> builds = new ArrayList<>();
		ChestBlockEntity chest = chest(level, box);
		List<ItemStack> load = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : Settlers.load(server, order).entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				ItemStack stack = new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize()));
				left -= stack.getCount();
				load.add(stack);
			}
		}
		List<BoundingBox> taken = new ArrayList<>(List.of(box.inflatedBy(1)));
		boolean left = true;
		for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.STARTER_COTTAGE, StarterBlueprints.STOREHOUSE)) {
			ResourceLocation id = styled(level, entry.id(), styles);
			Vec3i size = BlueprintLibrary.get(level, id).map(Blueprint::size).orElse(entry.size());
			Build build = beside(level, box, camp, rotation, id, size, left, taken);
			left = !left;
			if (build != null) {
				builds.add(build);
				taken.add(build.box().inflatedBy(1));
			}
			load.add(BlueprintItem.create(id, size));
		}
		// 33.23: with the Founder's blessing, his statue goes up beside the camp (past the starter builds)
		if (order.blessed()) {
			raiseStatue(level, box, camp, rotation, left, taken);
		}
		fill(level, box, chest, load);

		// The builder at the camp's Blueprint Table, and at work at once (the table is a workstation from the next tick).
		BlockPos table = SettlersWagonItem.table(level, box);
		if (builder != null && table != null) {
			Villager b = builder;
			UUID siteOwner = owner != null ? owner : b.getUUID();
			String siteOwnerName = owner != null ? ownerName : "";
			server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1, () -> startWork(level, b, table, siteOwner, siteOwnerName, builds, box)));
		}

		// Mother and colony: the record, the sister route, both chronicles.
		long day = Chronicle.day(level);
		GlobalPos colonyAt = GlobalPos.of(level.dimension(), hall);
		data.removeOrder(order.mother());
		data.recordFounded(order.mother(), colonyAt, day);
		io.github.jcondedata.aliveworkplace.realm.Realms.colonyFounded(level, order.mother(), colonyAt); // a colony joins its mother's realm (33.11)
		Caravans.Data caravans = Caravans.Data.get(level);
		caravans.setWants(hall, colony, List.of());
		caravans.addSisters(motherHall, hall);
		// 33.23: "founded with the Founder's blessing" in both chronicles
		String blessed = order.blessed() ? "_blessed" : "";
		Chronicle.atHall(level, hall, Chronicle.Kind.FOUNDED, Component.translatable("chronicle.aliveworkplace.colony.founded" + blessed, day, motherName, names));
		Component where = VillageHallScreen.where(motherHall, hall);
		Chronicle.atHall(level, motherHall, Chronicle.Kind.FOUNDED, Component.translatable("chronicle.aliveworkplace.colony.mother" + blessed, names, colony, where));

		// The owner hears of it.
		if (owner != null) {
			ServerPlayer online = server.getPlayerList().getPlayer(owner);
			if (online != null) {
				Chat.chat(online, Component.translatable("message.aliveworkplace.colony.arrived", names, colony, where, motherName).withStyle(ChatFormatting.GOLD));
			}
			String work = builder == null ? Component.translatable("message.aliveworkplace.colony.letter_no_builder").getString()
				: builds.isEmpty() ? Component.translatable("message.aliveworkplace.colony.letter_waiting").getString()
				: Component.translatable("message.aliveworkplace.colony.letter_building", builder.getDisplayName(),
					Settlers.names(builds.stream().map(b -> Blueprints.displayName(b.structure())).toList())).getString();
			String text = Component.translatable("message.aliveworkplace.colony.letter", colony, where, motherName, work, names).getString();
			String from = Component.translatable("message.aliveworkplace.colony.letter_from", colony).getString();
			letter(server, owner, ownerName, from, text, colonyAt);
		}
		keepLoaded(level, hall);
		level.playSound(null, hall, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		level.playSound(null, hall, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 1f);
		return hall;
	}

	/**
	 * The Founder's statue ({@link Founder#STATUE}, M29's build) raised beside the camp, on the {@code left} first, clear of
	 * {@code taken}; where it stands, or null when there's no template or no room.
	 */
	@Nullable
	static BoundingBox raiseStatue(ServerLevel level, BoundingBox box, Camp camp, Rotation rotation, boolean left, List<BoundingBox> taken) {
		net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template = level.getStructureManager().get(Founder.STATUE).orElse(null);
		if (template == null) {
			return null;
		}
		Build spot = beside(level, box, camp, rotation, Founder.STATUE, template.getSize(), left, taken);
		if (spot == null) {
			spot = near(level, box, rotation, Founder.STATUE, template.getSize(), taken); // no room either side: the nearest open ground round the camp
		}
		if (spot == null) {
			return null;
		}
		net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings settings =
			new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(rotation);
		BoundingBox at = template.getBoundingBox(settings, spot.placement().origin());
		for (BlockPos p : BlockPos.betweenClosed(at.minX(), at.minY(), at.minZ(), at.maxX(), at.maxY(), at.maxZ())) {
			if (!level.getBlockState(p).isAir()) {
				level.removeBlock(p, false);
			}
		}
		template.placeInWorld(level, spot.placement().origin(), spot.placement().origin(), settings, level.getRandom(), 2);
		taken.add(at.inflatedBy(1));
		return at;
	}

	/** Places {@code state} at {@code pos}, clearing the way and putting dirt under it if there's no ground; where it went. */
	private static BlockPos place(ServerLevel level, BlockPos pos, BlockState state) {
		BlockPos ground = ground(level, pos.getX(), pos.getZ());
		BlockPos at = ground != null && Math.abs(ground.getY() - pos.getY()) <= 1 ? ground : pos;
		if (!level.getBlockState(at).isAir()) {
			level.removeBlock(at, false);
		}
		if (level.getBlockState(at.below()).canBeReplaced()) {
			level.setBlockAndUpdate(at.below(), Blocks.DIRT.defaultBlockState());
		}
		level.setBlockAndUpdate(at, state);
		return at.immutable();
	}

	/** Places {@code state} at {@code pos} in a camp that left room for it, with dirt under it if there's no ground; where it went. */
	private static BlockPos placeExactly(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.getBlockState(pos.below()).canBeReplaced()) {
			level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
		}
		level.setBlockAndUpdate(pos, state);
		return pos.immutable();
	}

	/** The camp's chest, emptied of what its loot table would bring (the settlers brought their own). */
	@Nullable
	private static ChestBlockEntity chest(ServerLevel level, BoundingBox box) {
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockEntity(p) instanceof ChestBlockEntity chest) {
				chest.unpackLootTable(null);
				chest.clearContent();
				return chest;
			}
		}
		return null;
	}

	/** Puts {@code load} in the chest; what finds no room (or no chest) drops beside it. */
	private static void fill(ServerLevel level, BoundingBox box, @Nullable ChestBlockEntity chest, List<ItemStack> load) {
		int slot = 0;
		for (ItemStack stack : load) {
			if (chest != null && slot < chest.getContainerSize()) {
				chest.setItem(slot++, stack);
			} else {
				Block.popResource(level, chest != null ? chest.getBlockPos().above() : box.getCenter(), stack);
			}
		}
		if (chest != null) {
			chest.setChanged();
		}
	}

	/** {@code base} in the first of the mother's styles it comes in (none: as drawn). */
	public static ResourceLocation styled(ServerLevel level, ResourceLocation base, List<String> styles) {
		for (String style : styles) {
			ResourceLocation id = BlueprintStyles.styled(base, style);
			if (BlueprintLibrary.get(level, id).isPresent()) {
				return id;
			}
		}
		return base;
	}

	/**
	 * Where {@code structure} stands beside the camp, facing the same way, its front in line with the camp's: on the
	 * {@code left} first (then the other side), a little further out each try, on dry ground that doesn't rise or fall
	 * much and out of everything in {@code taken}. Null: nowhere the ground allows.
	 */
	@Nullable
	static Build beside(ServerLevel level, BoundingBox box, Camp camp, Rotation rotation, ResourceLocation structure, Vec3i size,
						boolean left, List<BoundingBox> taken) {
		int width = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90 ? box.getZSpan() : box.getXSpan();
		for (boolean side : new boolean[] {left, !left}) {
			for (int gap : GAPS) {
				// in the camp template's space: the build's front-middle, its x span just past the camp's on that side
				int x = side ? -gap - size.getX() + size.getX() / 2 : width + gap + size.getX() / 2;
				BlockPos guess = at(box, camp.front(), rotation, new BlockPos(x, 0, 0));
				BlockPos ground = ground(level, guess.getX(), guess.getZ());
				if (ground == null || !level.hasChunkAt(guess)) {
					continue;
				}
				BlueprintData.Placement placement = BlueprintItem.placementAt(Ids.of(level.dimension()), size, ground, rotation);
				Build build = new Build(structure, size, placement);
				BoundingBox footprint = build.box();
				if (taken.stream().anyMatch(t -> t.intersects(footprint)) || !level.hasChunksAt(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ())) {
					continue;
				}
				if (even(level, footprint, ground.getY())) {
					return build;
				}
			}
		}
		return null;
	}

	/**
	 * The nearest spot round the camp {@code box} (out to 16 blocks past its middle's reach) where {@code structure}
	 * stands on dry, even, loaded ground clear of everything in {@code taken}, facing the camp's way; null: none.
	 */
	@Nullable
	static Build near(ServerLevel level, BoundingBox box, Rotation rotation, ResourceLocation structure, Vec3i size, List<BoundingBox> taken) {
		BlockPos centre = box.getCenter();
		int reach = Math.max(box.getXSpan(), box.getZSpan()) / 2 + 16;
		List<int[]> tries = new ArrayList<>();
		for (int dx = -reach; dx <= reach; dx += 2) {
			for (int dz = -reach; dz <= reach; dz += 2) {
				tries.add(new int[] {dx, dz});
			}
		}
		tries.sort(Comparator.comparingInt((int[] t) -> t[0] * t[0] + t[1] * t[1]).thenComparingInt(t -> t[0]).thenComparingInt(t -> t[1]));
		for (int[] t : tries) {
			BlockPos ground = ground(level, centre.getX() + t[0], centre.getZ() + t[1]);
			if (ground == null || !level.hasChunkAt(ground)) {
				continue;
			}
			Build build = new Build(structure, size, BlueprintItem.placementAt(Ids.of(level.dimension()), size, ground, rotation));
			BoundingBox footprint = build.box();
			if (footprint.intersects(box.inflatedBy(1)) || taken.stream().anyMatch(b -> b.intersects(footprint))
				|| !level.hasChunksAt(footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ())) {
				continue;
			}
			if (even(level, footprint, ground.getY())) {
				return build;
			}
		}
		return null;
	}

	/** Whether the ground under {@code footprint} is dry and within {@link #UNEVEN} blocks of {@code y} nearly everywhere. */
	private static boolean even(ServerLevel level, BoundingBox footprint, int y) {
		int uneven = 0;
		int columns = footprint.getXSpan() * footprint.getZSpan();
		for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
			for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
				BlockPos ground = ground(level, x, z);
				if (ground == null) {
					return false; // water
				}
				if (Math.abs(ground.getY() - y) > UNEVEN) {
					uneven++;
				}
			}
		}
		return uneven <= columns * UNEVEN_SHARE;
	}

	/**
	 * The builder takes the camp's Blueprint Table, works for the owner, and starts the first build at once with the
	 * second after it; each blueprint started comes out of the chest (the others wait there).
	 */
	static void startWork(ServerLevel level, Villager builder, BlockPos table, UUID owner, String ownerName, List<Build> builds, BoundingBox camp) {
		if (!builder.isAlive() || !(level.getBlockState(table).is(ModBlocks.BLUEPRINT_TABLE) || level.getBlockState(table).is(ModBlocks.BUILDERS_BENCH))) {
			return;
		}
		Builders.employ(level, builder, table);
		if (ModAttachments.BUILDER_EMPLOYER.get(builder) == null && !ownerName.isEmpty()) {
			ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner, ownerName));
		}
		ChestBlockEntity chest = null;
		for (BlockPos p : BlockPos.betweenClosed(camp.minX(), camp.minY(), camp.minZ(), camp.maxX(), camp.maxY(), camp.maxZ())) {
			if (level.getBlockEntity(p) instanceof ChestBlockEntity c) {
				chest = c;
				break;
			}
		}
		boolean first = true;
		for (Build build : builds) {
			if (first) {
				Builders.start(level, builder, owner, ownerName, build.structure(), build.placement());
			} else {
				Builders.enqueue(level, builder, owner, ownerName, build.structure(), build.placement());
			}
			first = false;
			if (chest != null) {
				for (int i = 0; i < chest.getContainerSize(); i++) {
					Optional<BlueprintData> data = BlueprintItem.data(chest.getItem(i));
					if (data.isPresent() && data.get().structure().equals(build.structure())) {
						chest.setItem(i, ItemStack.EMPTY);
						break;
					}
				}
			}
		}
		if (chest != null) {
			chest.setChanged();
		}
	}

	/**
	 * A settler saved on the road, back in the world at {@code at} as they were (name, trade, level, trades, partner): no
	 * bed or workstation yet, and one with a trade keeps it until there's a workstation for it.
	 */
	@Nullable
	static Villager restore(ServerLevel level, CompoundTag saved, BlockPos at) {
		CompoundTag tag = saved.copy();
		tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.VILLAGER).toString());
		for (String gone : new String[] {"Pos", "Motion", "FallDistance", "Fire", "HurtTime", "DeathTime"}) {
			tag.remove(gone);
		}
		Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
			e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, e.getYRot(), 0f);
			return e;
		});
		if (!(entity instanceof Villager villager)) {
			return null;
		}
		if (level.getEntity(villager.getUUID()) != null) {
			villager.setUUID(UUID.randomUUID());
		}
		for (MemoryModuleType<GlobalPos> poi : List.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE,
			MemoryModuleType.MEETING_POINT)) {
			villager.getBrain().eraseMemory(poi);
		}
		ModAttachments.BUILDER_JOB.remove(villager);
		VillagerProfession job = villager.getVillagerData().getProfession();
		if (job != VillagerProfession.NONE && job != VillagerProfession.NITWIT && villager.getVillagerXp() == 0) {
			villager.setVillagerXp(1); // (vanilla takes the trade of a villager with no workstation and no experience)
		}
		if (!level.addFreshEntity(villager)) {
			return null;
		}
		villager.refreshBrain(level);
		return villager;
	}

	// ---- no room: home again ---------------------------------------------------------------------------------------------

	/** The settlers are back by the mother's hall, the supplies in its Storehouses and the cost in its treasury. */
	static void comeHome(ServerLevel level, RealmData data, RealmData.Order order) {
		BlockPos hall = order.mother().pos();
		List<Villager> back = new ArrayList<>();
		for (CompoundTag saved : order.settlers()) {
			BlockPos stand = VillageHalls.besideHall(level, hall, back.size());
			Villager v = restore(level, saved, stand != null ? stand : hall.above());
			if (v != null) {
				back.add(v);
			}
		}
		data.removeOrder(order.mother());
		Settlers.putBack(level, order);
		Component colony = order.name().orElse(Component.translatable("screen.aliveworkplace.colonies.unnamed"));
		Component village = VillageHalls.name(level, hall);
		Component names = Settlers.names(back.stream().map(Villager::getDisplayName).toList());
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			entity.setTreasury(entity.treasury() + order.cost());
			Chronicle.atHall(level, hall, Chronicle.Kind.ARRIVED, Component.translatable("chronicle.aliveworkplace.colony.no_room", names, colony));
			ServerPlayer owner = entity.owner() == null ? null : level.getServer().getPlayerList().getPlayer(entity.owner());
			if (owner != null) {
				Chat.chat(owner, Component.translatable("message.aliveworkplace.colony.no_room", colony, ROOM, names, village,
					Colonies.money(order.cost() / 100)).withStyle(ChatFormatting.YELLOW));
			}
		}
		level.playSound(null, hall, SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1f, 1f);
	}

	// ---- the letter ------------------------------------------------------------------------------------------------------

	/**
	 * A letter to {@code to}: into their mailbox if they have one (now, or at dawn if it can't be reached), otherwise
	 * into their hands if they're online, otherwise waiting for them at any Postal Desk.
	 */
	static void letter(MinecraftServer server, UUID to, String toName, String from, String text, GlobalPos origin) {
		Mail.sendLetter(server, UUID.nameUUIDFromBytes(("colony:" + origin).getBytes(java.nio.charset.StandardCharsets.UTF_8)), from, to, toName, text, origin);
	}
}
