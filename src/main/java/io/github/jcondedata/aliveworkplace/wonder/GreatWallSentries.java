package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.powers.SentriesPower;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Great Wall's sentries (ROADMAP 35.16), {@code sentries}: while a finished, standing Wonder with the power guards a
 * village, iron golems keep its walk, one at each of its posts (the towers' alcoves). They are the Golem Smith's Wall
 * Sentries ({@link GolemSmith#build}, role {@code sentry}, twice a golem's health), marked {@link #TAG}, their post
 * their {@code golem_post}: so everything a sentry is lives on the golem and is saved with it.
 * <ul>
 * <li>Four come with the finished Wall ({@link #man}, from {@code WonderCelebration.finished}).</li>
 * <li>On duty ({@link WatchGoal}, ranked above everything a golem does) a sentry fights any foe ({@code Guards.isFoe})
 * within the power's {@code range} of the wall, as a golem does, and once none is left goes back to its post; while
 * foes are about further out, it steps out of its alcove onto the walk and watches them. Pushed or knocked off the
 * wall, it walks back up, or where there's no way it climbs the wall's face.</li>
 * <li>A lost sentry is replaced the next morning ({@link #morning}, the hall's round before {@link #MORNING_TO}): by the
 * village's Golem Smith, or a Tinkerer as stand-in, from {@link #IRON} iron blocks and a pumpkin in the Wonder's
 * stockpile (the chests round its Foundation Stone); not without them.</li>
 * </ul>
 * Damaged, or with Wonders off, the power is down: the sentries are plain golems (they keep their posts' marks for when
 * it's back) and nobody is replaced.
 */
public final class GreatWallSentries {
	/** The tag a Wonder's sentry carries. */
	public static final String TAG = "aliveworkplace_wall_sentry";
	/** Iron blocks a new sentry takes. */
	public static final int IRON = 4;
	/** Up to this time of day the hall's round counts as the morning, when lost sentries are replaced. */
	public static final long MORNING_TO = 2000;
	/** How far round its post a sentry is looked for. */
	static final double SEARCH = 64;
	/** Foes this near the hall but beyond the sentries' range are watched from the walk. */
	static final double WATCH = 80;
	/** The day each hall's sentries were last replaced (not saved: a restart may look again, and finds nobody missing). */
	private static final Map<BlockPos, Long> REPLACED = new ConcurrentHashMap<>();

	/** One post of a Wonder's walk, as placed: where its sentry stands, and the walk's spot beside it where it steps out to watch. */
	public record Post(WonderSite site, SentriesPower power, int index, BlockPos at, BlockPos walk, Vec3 wallFrom, Vec3 wallTo) {
		/** How far {@code pos} is from the wall (the line through its posts), across the ground. */
		public double fromWall(Vec3 pos) {
			double dx = wallTo.x - wallFrom.x;
			double dz = wallTo.z - wallFrom.z;
			double len = dx * dx + dz * dz;
			double t = len <= 0 ? 0 : Math.max(0, Math.min(1, ((pos.x - wallFrom.x) * dx + (pos.z - wallFrom.z) * dz) / len));
			double px = wallFrom.x + t * dx - pos.x;
			double pz = wallFrom.z + t * dz - pos.z;
			return Math.sqrt(px * px + pz * pz);
		}
	}

	/** The posts of every finished, standing Wonder with {@code sentries} of the village round {@code hall}. */
	public static List<Post> posts(ServerLevel level, BlockPos hall) {
		List<Post> out = new ArrayList<>();
		for (Wonders.Granted g : Wonders.powers(level, hall, SentriesPower.TYPE)) {
			if (g.home() && g.power() instanceof SentriesPower power) {
				out.addAll(posts(g.site(), power));
			}
		}
		return out;
	}

	/** The posts of {@code site} as placed; none while it isn't. */
	public static List<Post> posts(WonderSite site, SentriesPower power) {
		if (site.placement().isEmpty() || power.posts().isEmpty()) {
			return List.of();
		}
		var at = site.placement().get();
		List<BlockPos> world = new ArrayList<>();
		for (BlockPos local : power.posts()) {
			world.add(at.origin().offset(StructureTemplate.transform(local, at.mirror(), at.rotation(), BlockPos.ZERO)));
		}
		double mx = 0;
		double mz = 0;
		for (BlockPos p : world) {
			mx += p.getX() + 0.5;
			mz += p.getZ() + 0.5;
		}
		mx /= world.size();
		mz /= world.size();
		// the wall: from the first post to the last (the Great Wall's end towers')
		Vec3 from = Vec3.atBottomCenterOf(world.get(0));
		Vec3 to = Vec3.atBottomCenterOf(world.get(world.size() - 1));
		List<Post> out = new ArrayList<>();
		for (int i = 0; i < world.size(); i++) {
			BlockPos p = world.get(i);
			double dx = mx - (p.getX() + 0.5);
			double dz = mz - (p.getZ() + 0.5);
			// stepping out: two blocks along the wall toward its middle, out of the tower's alcove onto the walk
			BlockPos walk = Math.abs(dx) < 1 && Math.abs(dz) < 1 ? p
				: Math.abs(dx) >= Math.abs(dz) ? p.offset(dx > 0 ? 2 : -2, 0, 0) : p.offset(0, 0, dz > 0 ? 2 : -2);
			out.add(new Post(site, power, i, p, walk, from, to));
		}
		return out;
	}

	/** Whether {@code golem} is a Wonder's sentry (marked, with a post). */
	public static boolean isSentry(IronGolem golem) {
		return golem.getTags().contains(TAG) && ModAttachments.GOLEM_POST.get(golem) != null;
	}

	/** The post {@code golem} keeps while its Wonder's power is up; null when it isn't a sentry, or the power is down. */
	@Nullable
	public static Post duty(ServerLevel level, IronGolem golem) {
		if (!golem.getTags().contains(TAG)) {
			return null;
		}
		BlockPos at = ModAttachments.GOLEM_POST.get(golem);
		if (at == null) {
			return null;
		}
		Optional<BlockPos> hall = VillageHalls.nearest(level, at);
		if (hall.isEmpty()) {
			return null;
		}
		for (Post post : posts(level, hall.get())) {
			if (post.at().equals(at)) {
				return post;
			}
		}
		return null;
	}

	/** The living sentry of {@code post}, if one is about. */
	public static Optional<IronGolem> sentry(ServerLevel level, Post post) {
		return level.getEntitiesOfClass(IronGolem.class, new AABB(post.at()).inflate(SEARCH),
			g -> g.isAlive() && g.getTags().contains(TAG) && post.at().equals(ModAttachments.GOLEM_POST.get(g))).stream().findFirst();
	}

	/** Every living sentry of the village round {@code hall}'s Wonders. */
	public static List<IronGolem> sentries(ServerLevel level, BlockPos hall) {
		List<IronGolem> out = new ArrayList<>();
		for (Post post : posts(level, hall)) {
			sentry(level, post).ifPresent(out::add);
		}
		return out;
	}

	/** A new sentry standing at {@code post}. */
	@Nullable
	static IronGolem raise(ServerLevel level, Post post) {
		IronGolem golem = GolemSmith.build(level, post.at(), GolemSmith.Role.SENTRY);
		if (golem == null) {
			return null;
		}
		golem.addTag(TAG);
		ModAttachments.GOLEM_POST.set(golem, post.at());
		golem.moveTo(post.at().getX() + 0.5, post.at().getY(), post.at().getZ() + 0.5, golem.getYRot(), 0f);
		golem.setTarget(null);
		return golem;
	}

	/**
	 * The finished {@code site}'s sentries take up their posts (the Wall is finished): one at each post standing loaded
	 * and without one. Returns how many came.
	 */
	public static int man(ServerLevel level, WonderSite site) {
		Wonder wonder = Wonders.loaded().get(site.wonder());
		if (!Wonders.ENABLED || wonder == null || !site.empowered()) {
			return 0;
		}
		int came = 0;
		for (Wonder.Power p : wonder.powers()) {
			if (!(p.power() instanceof SentriesPower power)) {
				continue;
			}
			for (Post post : posts(site, power)) {
				if (level.isLoaded(post.at()) && sentry(level, post).isEmpty() && raise(level, post) != null) {
					came++;
				}
			}
		}
		if (came > 0) {
			Chronicle.atHall(level, site.hall(), Chronicle.Kind.WONDER, Component.translatable("chronicle.aliveworkplace.great_wall.sentries", came, Wonders.name(site.wonder())));
		}
		return came;
	}

	/** The hall's round: in the morning, once a day, each lost sentry of the village is replaced ({@link #replace}). */
	public static void morning(ServerLevel level, BlockPos hall) {
		if (!Wonders.ENABLED || level.getDayTime() % VillageNeeds.DAY >= MORNING_TO) {
			return;
		}
		long today = Chronicle.day(level);
		Long last = REPLACED.get(hall);
		if (last != null && last >= today) {
			return;
		}
		if (posts(level, hall).isEmpty()) {
			return;
		}
		REPLACED.put(hall.immutable(), today);
		replace(level, hall);
	}

	/**
	 * Replaces each sentry of the village round {@code hall} that is missing (its post loaded, nobody keeping it): by its
	 * Golem Smith, or a Tinkerer as stand-in, from {@link #IRON} iron blocks and a pumpkin in the Wonder's stockpile.
	 * Returns how many were made.
	 */
	public static int replace(ServerLevel level, BlockPos hall) {
		Optional<Villager> maker = maker(level, hall);
		if (maker.isEmpty()) {
			return 0;
		}
		int made = 0;
		for (Post post : posts(level, hall)) {
			if (!level.isPositionEntityTicking(post.at()) || sentry(level, post).isPresent()) {
				continue;
			}
			List<BlockPos> chests = post.site().stone().map(s -> SupplyContainers.find(level, s, null)).orElse(List.of());
			Item pumpkin = SupplyContainers.count(level, chests, Items.CARVED_PUMPKIN) > 0 ? Items.CARVED_PUMPKIN : Items.PUMPKIN;
			if (SupplyContainers.count(level, chests, Items.IRON_BLOCK) < IRON || SupplyContainers.count(level, chests, pumpkin) < 1) {
				continue;
			}
			SupplyContainers.extract(level, chests, Items.IRON_BLOCK, IRON);
			SupplyContainers.extract(level, chests, pumpkin, 1);
			if (raise(level, post) != null) {
				made++;
				Villager who = maker.get();
				Chronicle.atHall(level, hall, Chronicle.Kind.ARRIVED, Component.translatable("chronicle.aliveworkplace.great_wall.sentry_replaced",
					Component.literal((who.getCustomName() != null ? who.getCustomName() : who.getName()).getString()), Wonders.name(post.site().wonder())));
			}
		}
		return made;
	}

	/** Who makes a new sentry in the village round {@code hall}: its Golem Smith, or a Tinkerer. */
	static Optional<Villager> maker(ServerLevel level, BlockPos hall) {
		Optional<Villager> smith = Legends.ENABLED ? Legends.in(level, hall, GolemSmith.ID) : Optional.empty();
		if (smith.isPresent()) {
			return smith;
		}
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall),
			v -> v.isAlive() && !v.isBaby() && v.getVillagerData().getProfession() == ModVillagers.TINKERER).stream().findFirst();
	}

	/** The nearest foe within the power's range of the wall, for {@code golem} keeping {@code post}. */
	@Nullable
	static LivingEntity foe(ServerLevel level, IronGolem golem, Post post, double range) {
		Vec3 from = post.wallFrom();
		Vec3 to = post.wallTo();
		AABB around = new AABB(from, to).inflate(range, 24, range);
		return level.getEntitiesOfClass(Mob.class, around, m -> Guards.isFoe(m) && post.fromWall(m.position()) <= range).stream()
			.min(Comparator.comparingDouble(m -> m.distanceToSqr(golem))).orElse(null);
	}

	/**
	 * A sentry's duty, above everything a golem does: with a foe within range of the wall, it lets the golem's attack
	 * have it (the foe its target); with none, it goes back to its post, or steps out onto the walk to watch foes
	 * further out, and holds there; a foe beyond the range is let go. Off the wall with no way up, it climbs the face.
	 */
	public static class WatchGoal extends Goal {
		private final IronGolem golem;
		@Nullable
		private Post post;
		@Nullable
		private LivingEntity watched;
		private int recheck;
		private int repath;
		private double best = Double.MAX_VALUE;
		private int sinceBetter;
		private boolean climbing;

		public WatchGoal(IronGolem golem) {
			this.golem = golem;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		/** Whether the goal holds the golem now: on duty, no foe for it to fight. */
		private boolean holds() {
			if (!(golem.level() instanceof ServerLevel level) || golem.isPassenger() || !golem.getTags().contains(TAG)) {
				return false;
			}
			if (--recheck <= 0 || post == null) {
				recheck = 10;
				post = duty(level, golem);
				if (post == null) {
					return false;
				}
				double range = post.power().range();
				LivingEntity target = golem.getTarget();
				if (target != null && (!target.isAlive() || post.fromWall(target.position()) > range)) {
					golem.setTarget(null); // a foe beyond the wall's reach: let it go
				}
				if (golem.getTarget() == null) {
					LivingEntity foe = foe(level, golem, post, range);
					if (foe != null) {
						golem.setTarget(foe);
					}
				}
				watched = golem.getTarget() != null ? null : level.getEntitiesOfClass(Mob.class, new AABB(post.at()).inflate(WATCH, 24, WATCH), Guards::isFoe)
					.stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(golem))).orElse(null);
			}
			return post != null && golem.getTarget() == null;
		}

		@Override
		public boolean canUse() {
			return holds();
		}

		@Override
		public boolean canContinueToUse() {
			return holds();
		}

		@Override
		public void start() {
			best = Double.MAX_VALUE;
			sinceBetter = 0;
			repath = 0;
			climbing = false;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (post == null) {
				return;
			}
			BlockPos station = watched != null ? post.walk() : post.at();
			Vec3 target = Vec3.atBottomCenterOf(station);
			double away = golem.position().distanceTo(target);
			if (watched != null) {
				golem.getLookControl().setLookAt(watched, 30f, 30f);
			}
			if (away <= 0.8 && !climbing) {
				golem.getNavigation().stop();
				best = away;
				sinceBetter = 0;
				return;
			}
			if (away < best - 0.5) {
				best = away;
				sinceBetter = 0;
			} else {
				sinceBetter++;
			}
			boolean below = golem.getY() < station.getY() - 1.5;
			if (!climbing && below && (sinceBetter > 60 || golem.getNavigation().isDone() && away > 2)) {
				climbing = true; // no way up found: up the wall's face
			}
			if (climbing) {
				// straight for the walk's spot by its post, up the face where it bumps into it
				BlockPos landing = post.walk();
				golem.getNavigation().stop();
				golem.getMoveControl().setWantedPosition(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5, 1.0);
				if (golem.horizontalCollision && golem.getY() < landing.getY() + 1.5) {
					Vec3 v = golem.getDeltaMovement();
					golem.setDeltaMovement(v.x, 0.3, v.z);
				}
				golem.fallDistance = 0;
				if (golem.onGround() && golem.getY() >= landing.getY() - 0.1) {
					climbing = false; // back on the walk
					best = Double.MAX_VALUE;
					sinceBetter = 0;
				}
				return;
			}
			if (--repath <= 0 || golem.getNavigation().isDone()) {
				repath = 20;
				golem.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
			}
		}

		@Override
		public void stop() {
			golem.getNavigation().stop();
			climbing = false;
		}
	}

	/** Forgets the mornings (tests). */
	public static void forget() {
		REPLACED.clear();
	}

	private GreatWallSentries() {
	}
}
