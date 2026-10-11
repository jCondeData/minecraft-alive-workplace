package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * The Bandit King's fort (ROADMAP 31.15): three builds, each standing on the one before like a blueprint upgrade —
 * {@code story/bandit_fort} (a palisade round a yard, a gate, a fire and tents), {@code story/bandit_fort_2} (two
 * watchtowers and a stable) and {@code story/bandit_fort_3} (the King's keep, its treasure chest and a postern at the
 * back, set down open or shut). The Bandit King's arc places one stage each dawn ({@code place} with the rule
 * {@code fort}, see {@code story/ArcEffects}) on fit ground {@link #NEAR} to {@link #FAR} blocks from the hall: the
 * bandit camps' test of the ground widened to the fort's size, nothing a player built in the way, the ground filled
 * underneath as a camp's is.
 *
 * <p>Each stage brings its men, who keep to the fort: four bandits in the yard, a pillager on each watchtower, and with
 * the keep the King himself ({@link BanditKing}). Where a man keeps to is written in his tags, so a chunk reload or a
 * restart doesn't let him wander.
 */
public final class BanditFort {
	public static final List<ResourceLocation> STAGES = List.of(AliveWorkplace.id("story/bandit_fort"), AliveWorkplace.id("story/bandit_fort_2"),
		AliveWorkplace.id("story/bandit_fort_3"));
	public static final int NEAR = 110;
	public static final int FAR = 150;
	/** Half the fort's footprint, each way (it is 29 x 29). */
	public static final int HALF = 14;
	/** The fort's blueprints: 29 x {@link #HEIGHT} x 29. */
	public static final int SIZE = 29;
	public static final int HEIGHT = 18;
	/** The treasure chest's loot. */
	public static final ResourceKey<net.minecraft.world.level.storage.loot.LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
		AliveWorkplace.id("chests/bandit_king"));
	/** The arc flag that leaves the keep's postern open (the deserter told of it, 31.16). */
	public static final String POSTERN_FLAG = "postern_open";
	/** Every man of the fort, and the King. */
	public static final String TAG = "aliveworkplace_fort";
	/** Where a man of the fort keeps to: {@code <prefix><pos as long>.<radius>}. */
	static final String HOME_PREFIX = "aliveworkplace_fort_home.";
	/** How far from the middle of the yard the fort's men go: the whole yard, not beyond the palisade much. */
	public static final int KEEP = 17;

	// In the blueprints (front z 0): the middle of the yard, the keep's door (outside), the King's place before his
	// throne, the treasure chest, the postern, the yard's men and the watchtowers' platforms.
	static final BlockPos YARD = new BlockPos(14, 1, 14);
	static final BlockPos KEEP_DOOR = new BlockPos(14, 1, 15);
	static final BlockPos THRONE = new BlockPos(14, 1, 21);
	public static final BlockPos TREASURE = new BlockPos(18, 1, 24);
	public static final BlockPos POSTERN = new BlockPos(14, 1, 28);
	static final List<BlockPos> YARD_MEN = List.of(new BlockPos(12, 1, 8), new BlockPos(16, 1, 8), new BlockPos(11, 1, 14), new BlockPos(17, 1, 14));
	static final List<BlockPos> TOWER_MEN = List.of(new BlockPos(2, 6, 2), new BlockPos(26, 6, 2));

	/** The fort's men loaded now (a weak set: the game forgets them, so does this). */
	private static final Set<Mob> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

	public static void init() {
		Platform.get().onEntityLoad((entity, level) -> onLoad(entity));
		Platform.get().onServerTick(server -> {
			if (server.getTickCount() % BanditKing.CHECK_EVERY == 0) {
				for (ServerLevel level : server.getAllLevels()) {
					tick(level);
				}
			}
		});
	}

	/** Which stage {@code template} is (0 to 2), or -1 if it isn't one of the fort's. */
	public static int stageOf(String template) {
		ResourceLocation id = ResourceLocation.tryParse(template);
		return id == null ? -1 : STAGES.indexOf(id);
	}

	/** Whether a placement's stages are the fort's. */
	public static boolean isFort(List<String> stages) {
		return !stages.isEmpty() && stageOf(stages.get(0)) == 0;
	}

	// ---- the ground --------------------------------------------------------------------------------------------------

	/** Fit ground for the fort {@code near} to {@code far} blocks from {@code hall}, its chunks loaded; null if there's none. */
	@Nullable
	public static BlockPos site(ServerLevel level, BlockPos hall, int near, int far, RandomSource random) {
		for (int tries = 0; tries < 16; tries++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int distance = near + random.nextInt(Math.max(1, far - near + 1));
			BlockPos column = hall.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
			BlockPos ground = ground(level, column);
			if (ground != null && Math.abs(ground.getY() - hall.getY()) <= 32 && fits(level, ground)) {
				return ground;
			}
		}
		return null;
	}

	/** The ground at {@code column} when the fort's whole footprint round it is loaded; else null. */
	@Nullable
	public static BlockPos ground(ServerLevel level, BlockPos column) {
		for (int dx : new int[] {-HALF - 1, HALF + 1}) {
			for (int dz : new int[] {-HALF - 1, HALF + 1}) {
				if (!level.isLoaded(column.offset(dx, 0, dz))) {
					return null;
				}
			}
		}
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
	}

	/**
	 * Whether the fort may stand with its middle at {@code ground}: the bandit camps' test widened to its footprint
	 * (fairly flat, dry and open: {@link BanditCamps#fits(ServerLevel, BlockPos, int, int)}) and nothing a player built
	 * in its box, from the ground up to its top (only natural ground, plants and trees, and no block a player set there).
	 */
	public static boolean fits(ServerLevel level, BlockPos ground) {
		if (!BanditCamps.fits(level, ground, HALF, HALF)) {
			return false;
		}
		BoundingBox box = BoundingBox.fromCorners(ground.offset(-HALF, -4, -HALF), ground.offset(HALF, HEIGHT, HALF));
		if (PlayerBuilt.get(level).marked(box)) {
			return false; // a block a player set (27.19), even of earth
		}
		for (BlockPos p : BlockPos.betweenClosed(ground.offset(-HALF, 0, -HALF), ground.offset(HALF, HEIGHT, HALF))) {
			if (!natural(level.getBlockState(p))) {
				return false;
			}
		}
		return true;
	}

	/** Ground, a plant or a tree: something nobody built. */
	static boolean natural(BlockState state) {
		return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(BlockTags.FLOWERS)
			|| state.is(BlockTags.SAPLINGS) || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
			|| state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.SNOW) || state.is(Blocks.GRAVEL)
			|| state.is(Blocks.CLAY) || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO) || state.is(Blocks.PUMPKIN)
			|| state.is(Blocks.MELON) || state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.COPPER_ORES);
	}

	// ---- placing -----------------------------------------------------------------------------------------------------

	/** Where a fort stands: its blueprint's corner and its turn. */
	public record Fort(BlockPos origin, Rotation rotation) {
		public StructurePlaceSettings settings() {
			return new StructurePlaceSettings().setRotation(rotation);
		}

		/** The world position of {@code local}, a position in the blueprints. */
		public BlockPos at(BlockPos local) {
			return origin.offset(StructureTemplate.calculateRelativePosition(settings(), local));
		}

		public BlockPos yard() {
			return at(YARD);
		}
	}

	/** The fort turned {@code rotation} with its middle at {@code ground} (where its first stage goes). */
	public static Fort at(BlockPos ground, Rotation rotation) {
		BlockPos corner = ground.offset(-HALF, 0, -HALF);
		return new Fort(StructureTemplate.getZeroPositionWithTransform(corner, Mirror.NONE, rotation, SIZE, SIZE), rotation);
	}

	/**
	 * Sets stage {@code stage} (0 to 2) of the fort down: the first clears its box of grass and bushes and fills the
	 * ground under it, as a bandit camp is set down; each later one stands on the one before. Then its men come, and with
	 * the keep its treasure chest is filled and its postern set {@code posternOpen}. False if the blueprint is missing.
	 */
	public static boolean place(ServerLevel level, Fort fort, int stage, boolean posternOpen) {
		StructureTemplate template = level.getStructureManager().get(STAGES.get(stage)).orElse(null);
		if (template == null) {
			AliveWorkplace.LOG.warn("No blueprint {} for the Bandit King's fort", STAGES.get(stage));
			return false;
		}
		StructurePlaceSettings settings = fort.settings();
		if (stage == 0) {
			Lairs.clearAndPlace(level, template, fort.origin(), settings, template.getBoundingBox(settings, fort.origin()));
		} else {
			template.placeInWorld(level, fort.origin(), fort.origin(), settings, level.getRandom(), 2);
		}
		placed(level, fort, stage, posternOpen);
		return true;
	}

	/** After stage {@code stage} is down (by {@link #place} or the arc): the keep's chest and postern, and the stage's men. */
	public static void placed(ServerLevel level, Fort fort, int stage, boolean posternOpen) {
		if (stage == 2) {
			if (level.getBlockEntity(fort.at(TREASURE)) instanceof RandomizableContainerBlockEntity chest) {
				chest.setLootTable(LOOT, level.random.nextLong());
			}
			setPostern(level, fort, posternOpen);
		}
		BlockPos yard = fort.yard();
		switch (stage) {
			case 0 -> {
				for (int i = 0; i < YARD_MEN.size(); i++) {
					man(level, i % 2 == 0 ? EntityType.VINDICATOR : EntityType.PILLAGER, fort.at(YARD_MEN.get(i)), yard, KEEP);
				}
			}
			case 1 -> {
				for (BlockPos spot : TOWER_MEN) {
					BlockPos at = fort.at(spot);
					man(level, EntityType.PILLAGER, at, at, 2);
				}
			}
			case 2 -> BanditKing.spawn(level, fort.at(THRONE), yard, fort.at(KEEP_DOOR));
			default -> {
			}
		}
	}

	/** Opens or shuts the keep's postern (the door in the palisade behind it). */
	public static void setPostern(ServerLevel level, Fort fort, boolean open) {
		BlockPos lower = fort.at(POSTERN);
		for (BlockPos p : List.of(lower, lower.above())) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof DoorBlock) {
				level.setBlock(p, state.setValue(DoorBlock.OPEN, open), 2);
			}
		}
	}

	/** One of the fort's men: a bandit of {@code type} at {@code at}, keeping within {@code radius} of {@code home}. */
	@Nullable
	static Mob man(ServerLevel level, EntityType<? extends Mob> type, BlockPos at, BlockPos home, int radius) {
		Mob mob = type.create(level);
		if (mob == null) {
			return null;
		}
		mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		mob.setPersistenceRequired();
		mob.setCustomName(Component.translatable("entity.aliveworkplace.bandit"));
		if (mob instanceof Raider raider) {
			raider.setCanJoinRaid(false);
		}
		keepTo(mob, home, radius);
		level.addFreshEntityWithPassengers(mob);
		LOADED.add(mob);
		return mob;
	}

	/** {@code mob} is one of the fort's and keeps within {@code radius} of {@code home}, now and after a reload. */
	static void keepTo(Mob mob, BlockPos home, int radius) {
		mob.addTag(TAG);
		for (String old : List.copyOf(mob.getTags())) {
			if (old.startsWith(HOME_PREFIX)) {
				mob.removeTag(old);
			}
		}
		mob.addTag(HOME_PREFIX + home.asLong() + "." + radius);
		mob.restrictTo(home, radius);
	}

	/** Where {@code mob} keeps to, from its tags, or null if it isn't one of the fort's. */
	@Nullable
	static BlockPos home(Mob mob) {
		for (String tag : mob.getTags()) {
			if (tag.startsWith(HOME_PREFIX)) {
				String[] parts = tag.substring(HOME_PREFIX.length()).split("\\.");
				try {
					return BlockPos.of(Long.parseLong(parts[0]));
				} catch (NumberFormatException e) {
					return null;
				}
			}
		}
		return null;
	}

	static int radius(Mob mob) {
		for (String tag : mob.getTags()) {
			if (tag.startsWith(HOME_PREFIX)) {
				String[] parts = tag.substring(HOME_PREFIX.length()).split("\\.");
				try {
					return parts.length > 1 ? Integer.parseInt(parts[1]) : KEEP;
				} catch (NumberFormatException e) {
					return KEEP;
				}
			}
		}
		return KEEP;
	}

	static void onLoad(Entity entity) {
		if (entity instanceof Mob mob && mob.getTags().contains(TAG)) {
			LOADED.add(mob);
		}
	}

	static void track(Mob mob) {
		LOADED.add(mob);
	}

	/** Every {@link BanditKing#CHECK_EVERY} ticks: the fort's men keep home; the King's bar and horn. */
	public static void tick(ServerLevel level) {
		if (LOADED.isEmpty()) {
			BanditKing.dropStaleBars(level);
			return;
		}
		for (Mob mob : new ArrayList<>(LOADED)) {
			if (mob.level() != level) {
				continue;
			}
			if (mob.isRemoved() || !mob.isAlive()) {
				LOADED.remove(mob);
				BanditKing.gone(mob);
				continue;
			}
			if (!mob.hasRestriction()) {
				BlockPos home = home(mob);
				if (home != null) {
					mob.restrictTo(home, radius(mob)); // a chunk reload forgets it
				}
			}
			if (mob.getTags().contains(BanditKing.TAG)) {
				BanditKing.tick(level, mob);
			}
		}
	}

	/** The fort's men about {@code around} (tests). */
	public static List<Mob> men(ServerLevel level, BlockPos around, int range) {
		return level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(around).inflate(range), m -> m.isAlive() && m.getTags().contains(TAG));
	}

	private BanditFort() {
	}
}
