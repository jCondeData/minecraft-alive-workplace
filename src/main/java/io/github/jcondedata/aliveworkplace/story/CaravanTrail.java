package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The Lost Caravan's trail (ROADMAP 31.19): the wreck of the settlers' wagon ({@code story/caravan_wreck}), the raiders'
 * stockade ({@code story/raiders_stockade}) and five marks between them ({@code story/trail_1} to {@code trail_5}), and
 * the objective {@code follow_trail} that walks a player from mark to mark.
 *
 * <p>The arc places them with three rules of {@code place} ({@link ArcEffects#spot}): the wreck a third of the way along
 * the caravan's road ({@code "road": 0.333, "or": 150}: 150 blocks out when that isn't fit ground), the stockade 120 to
 * 180 blocks on from the wreck away from the villages ({@code "after": "wreck", "distance": [120, 180], "away": true})
 * and the marks evenly between the two ({@code "between": ["wreck", "stockade"]}, with {@code "each"} naming one template
 * per mark). Each is built once a player comes within {@link Arcs#NEAR} blocks, on ground that is fairly flat, dry, open
 * and nobody's: as flat as a bandit camp's ground, no tree trunk, nothing a player set in its box ({@link #fits}). The hump of a
 * hill in its footprint is cut away and a hollow filled, so it lies on uneven ground as a camp does.
 */
public final class CaravanTrail {
	public static final ResourceLocation WRECK = AliveWorkplace.id("story/caravan_wreck");
	public static final ResourceLocation STOCKADE = AliveWorkplace.id("story/raiders_stockade");
	public static final List<ResourceLocation> MARKS = List.of(AliveWorkplace.id("story/trail_1"), AliveWorkplace.id("story/trail_2"),
		AliveWorkplace.id("story/trail_3"), AliveWorkplace.id("story/trail_4"), AliveWorkplace.id("story/trail_5"));
	/** How close (across the ground) a player must come to a mark for it to count. */
	public static final int RADIUS = 6;
	/** The stockade's loot chest and the cage's iron gate, in its blueprint (front z 0). */
	public static final BlockPos LOOT_CHEST = new BlockPos(7, 1, 14);
	public static final BlockPos CAGE_DOOR = new BlockPos(14, 1, 10);

	/** Whether {@code stages} are one of the trail's builds (which take uneven ground, {@link #fits}). */
	public static boolean isTrail(List<String> stages) {
		if (stages.isEmpty()) {
			return false;
		}
		ResourceLocation id = ResourceLocation.tryParse(stages.get(0));
		return id != null && (id.equals(WRECK) || id.equals(STOCKADE) || MARKS.contains(id));
	}

	// ---- the ground --------------------------------------------------------------------------------------------------

	/**
	 * Whether a build with the box {@code box} may lie with its middle on {@code ground}: dry, fairly flat (as the bandit
	 * camps' test: no more columns over a block above or below its ground than a camp allows for its size, and at least
	 * one; a step of one is fine, it's cut or filled), no tree trunk in it, no block a player set (27.19) in it or under
	 * it, and only natural ground, plants and trees from its ground up.
	 */
	public static boolean fits(ServerLevel level, BlockPos ground, BoundingBox box) {
		int columns = (box.maxX() - box.minX() + 1) * (box.maxZ() - box.minZ() + 1);
		int allowed = Math.max(1, columns * 20 / 195); // a camp: 20 of its 195 columns
		int uneven = 0;
		int trunks = 0;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, ground.getY(), z)).below();
				if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.above()).isEmpty()) {
					return false;
				}
				if (Math.abs(top.getY() - ground.getY()) > 1) {
					uneven++;
				}
				BlockPos at = new BlockPos(x, ground.getY() + 1, z);
				if (level.getBlockState(at).is(BlockTags.LOGS) || level.getBlockState(at.above()).is(BlockTags.LOGS)) {
					trunks++;
				}
			}
		}
		if (uneven > allowed || trunks > allowed) {
			return false;
		}
		BoundingBox wide = new BoundingBox(box.minX(), box.minY() - 4, box.minZ(), box.maxX(), box.maxY(), box.maxZ());
		if (PlayerBuilt.get(level).marked(wide)) {
			return false;
		}
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!BanditFort.natural(level.getBlockState(p))) {
				return false;
			}
		}
		return true;
	}

	/** Whether the ground round {@code column} would take a build about {@code half} blocks each way; true when it isn't loaded (it's tested again when built). */
	static boolean fitAround(ServerLevel level, BlockPos column, int half) {
		if (!level.isLoaded(column.offset(-half, 0, -half)) || !level.isLoaded(column.offset(half, 0, half))
			|| !level.isLoaded(column.offset(-half, 0, half)) || !level.isLoaded(column.offset(half, 0, -half))) {
			return true;
		}
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
		return fits(level, ground, new BoundingBox(ground.getX() - half, ground.getY(), ground.getZ() - half, ground.getX() + half, ground.getY() + 6,
			ground.getZ() + half));
	}

	// ---- the rules ---------------------------------------------------------------------------------------------------

	/** {@code road} with {@code or}: the road's spot when it's fit ground (or not loaded yet), else {@code or} blocks out its way. */
	@Nullable
	public static BlockPos road(ServerLevel level, BlockPos hall, @Nullable BlockPos onRoad, @Nullable BlockPos to, int or, RandomSource random) {
		if (onRoad != null && fitAround(level, onRoad, 6)) {
			return onRoad;
		}
		double angle = to != null && !to.equals(hall) ? Math.atan2(to.getZ() - hall.getZ(), to.getX() - hall.getX()) : random.nextDouble() * Math.PI * 2;
		return hall.offset((int) Math.round(Math.cos(angle) * or), 0, (int) Math.round(Math.sin(angle) * or));
	}

	/**
	 * {@code after} with {@code away}: {@code near} to {@code far} blocks from {@code from}, on the side away from every
	 * village it knows ({@code hall}, the villages it has caravan roads to, its neighbours): of sixteen ways round, the
	 * one whose spot is furthest from the nearest of them.
	 */
	public static BlockPos away(ServerLevel level, BlockPos hall, BlockPos from, int near, int far, RandomSource random) {
		List<BlockPos> villages = new ArrayList<>();
		villages.add(hall);
		villages.addAll(Caravans.Data.get(level).routesFrom(hall));
		Caravans.neighbours(level, hall).forEach(v -> villages.add(v.hall()));
		int distance = near + random.nextInt(Math.max(1, far - near + 1));
		double offset = random.nextDouble() * Math.PI / 8;
		BlockPos best = null;
		double bestScore = -1;
		for (int i = 0; i < 16; i++) {
			double angle = offset + Math.PI / 8 * i;
			BlockPos p = from.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
			double score = Double.MAX_VALUE;
			for (BlockPos v : villages) {
				double dx = p.getX() - v.getX();
				double dz = p.getZ() - v.getZ();
				score = Math.min(score, dx * dx + dz * dz);
			}
			if (score > bestScore) {
				bestScore = score;
				best = p;
			}
		}
		return best;
	}

	/** {@code between}: the {@code index}-th of {@code count} spots evenly between two places (the ends left out). */
	public static BlockPos between(BlockPos a, BlockPos b, int index, int count) {
		double t = (index + 1) / (double) (count + 1);
		return new BlockPos((int) Math.round(a.getX() + (b.getX() - a.getX()) * t), a.getY(), (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t));
	}

	// ---- follow_trail ------------------------------------------------------------------------------------------------

	/**
	 * {@code follow_trail} (31.19): reach the arc's marks (its places {@code trail}, {@code trail#2}...) in order, each
	 * within {@link #RADIUS} blocks; a mark reached out of turn counts nothing. At each, the action bar says where the
	 * tracks go next: the way to the next mark, after the last the way to the place {@code then} (the stockade).
	 */
	public record FollowTrail(String trail, Optional<String> then, int count) implements Objectives.Objective {
		static FollowTrail read(JsonObject json) {
			String trail = GsonHelper.getAsString(json, "trail", "trail");
			if (trail.isEmpty()) {
				throw new IllegalArgumentException("'trail' names no place");
			}
			Optional<String> then = json.has("then") ? Optional.of(GsonHelper.getAsString(json, "then")) : Optional.empty();
			int count = GsonHelper.getAsInt(json, "count", MARKS.size());
			if (count < 1) {
				throw new IllegalArgumentException("'count' below 1");
			}
			return new FollowTrail(trail, then, count);
		}

		@Override
		public String type() {
			return "follow_trail";
		}

		@Override
		public int need() {
			return count;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.follow_trail");
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("trail", trail);
			then.ifPresent(t -> o.addProperty("then", t));
			o.addProperty("count", count);
			return o;
		}
	}

	/** {@code o} with the arc's own count of marks (as many as it placed). */
	static Objectives.Objective cast(ArcState s, FollowTrail o) {
		int marks = ArcEffects.places(s, o.trail()).size();
		return marks > 0 && marks != o.count() ? new FollowTrail(o.trail(), o.then(), marks) : o;
	}

	/** Every 2 seconds for each player: the next mark of each open {@code follow_trail} of their dimension they stand at counts. */
	public static void check(ServerPlayer player) {
		if (player.isSpectator()) {
			return;
		}
		ServerLevel level = Players.level(player);
		Stories.Data data = Stories.Data.get(level);
		for (Map.Entry<BlockPos, Stories.Entry> en : List.copyOf(data.halls.entrySet())) {
			for (Quest q : List.copyOf(en.getValue().quests)) {
				int i = q.current();
				if (i < 0 || !(q.objectives.get(i) instanceof FollowTrail trail) || q.arc == null) {
					continue;
				}
				ArcState s = Arcs.running(en.getValue()).stream().filter(a -> a.id.equals(q.arc)).findFirst().orElse(null);
				if (s == null) {
					continue;
				}
				List<ArcState.ArcPlace> marks = ArcEffects.places(s, trail.trail());
				int next = q.progress[i];
				if (next >= marks.size() || !within(player, marks.get(next).spot)
					|| !(level.getBlockEntity(en.getKey()) instanceof VillageHallBlockEntity entity) || !Stories.mayHelp(level, en.getKey(), q, player)) {
					continue;
				}
				BlockPos onward = next + 1 < marks.size() ? marks.get(next + 1).spot
					: trail.then().map(s::place).filter(p -> !p.skipped || p.placed > 0).map(p -> p.spot).orElse(null);
				Chat.actionBar(player, (onward == null ? Component.translatable("message.aliveworkplace.trail.end")
					: Component.translatable("message.aliveworkplace.trail.next", direction(marks.get(next).spot, onward))).withStyle(ChatFormatting.GOLD));
				Stories.progress(level, en.getKey(), entity, q, i, 1, player);
			}
		}
	}

	/** Whether {@code player} stands within {@link #RADIUS} blocks of {@code mark}, across the ground (and not far above or below it). */
	static boolean within(ServerPlayer player, BlockPos mark) {
		double dx = player.getX() - (mark.getX() + 0.5);
		double dz = player.getZ() - (mark.getZ() + 0.5);
		return dx * dx + dz * dz <= (double) RADIUS * RADIUS && Math.abs(player.getY() - mark.getY()) <= 24;
	}

	/** The way from {@code from} to {@code to}, of the eight: "north-east". */
	public static Component direction(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		// 0 = south, counting clockwise in eighths (Minecraft's +z is south), as the hall's screen tells it.
		int eighth = Math.floorMod((int) Math.round(Math.atan2(-dx, dz) / (Math.PI / 4)), 8);
		String[] directions = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
		return Component.translatable("screen.aliveworkplace.hall.dir." + directions[eighth]);
	}

	private CaravanTrail() {
	}
}
