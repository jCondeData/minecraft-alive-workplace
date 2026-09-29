package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Bandit camps: now and then bandits make camp out beyond a village of Village rank or more ({@link #NEAR} to
 * {@link #FAR} blocks from its hall) — tents round a fire, a Bandit Chief and a few of his men. While the camp stands,
 * the village's night raids come from it, bandits (pillagers and vindicators) rather than monsters, twice as often, and
 * the villagers feel less safe. Kill the chief and the camp is broken up: the rest of the band scatters, the chest in
 * the chief's tent is yours, and the chronicle remembers who did it. {@code banditCamps} in the config turns them off.
 */
public final class BanditCamps {
	public static boolean ENABLED = true;
	public static final ResourceLocation CAMP = AliveWorkplace.id("camp/bandit_camp");
	public static final String TAG = "aliveworkplace_bandit";
	public static final String CHIEF_TAG = "aliveworkplace_bandit_chief";
	public static final int NEAR = 80;
	public static final int FAR = 104;
	/** Days after a camp is broken up before another comes. */
	public static final int REST_DAYS = 5;
	/** The chance a day that bandits make camp near a village that could have one. */
	public static final float DAILY_CHANCE = 0.12f;
	/** How far from the camp its bandits keep. */
	static final int KEEP = 14;
	/** How much less safe the villagers feel while a camp stands (share of the safety score kept). */
	public static final float SAFETY = 0.6f;
	/** The chief's extra health. */
	static final double CHIEF_HEALTH = 36;

	/** In the camp's blueprint: the fire, where the chief stands (in his tent) and where his men do. */
	static final BlockPos FIRE = new BlockPos(7, 1, 6);
	static final BlockPos CHIEF_SPOT = new BlockPos(7, 1, 9);
	static final List<BlockPos> BAND_SPOTS = List.of(new BlockPos(4, 1, 7), new BlockPos(10, 1, 7), new BlockPos(7, 1, 3), new BlockPos(6, 1, 4));

	/** A camp: where it stands, the village it preys on, its chief, the day it was made. */
	public record Camp(BlockPos pos, BlockPos hall, UUID chief, long day) {
	}

	/** The camp preying on the village round {@code hall}, if any. */
	public static Optional<Camp> near(ServerLevel level, BlockPos hall) {
		return Optional.ofNullable(Data.get(level).camps.get(hall));
	}

	/** Every camp in the dimension. */
	public static List<Camp> all(ServerLevel level) {
		return List.copyOf(Data.get(level).camps.values());
	}

	/** The hall's round: a camp whose chief is gone is broken up; with none, bandits may come. */
	public static void round(ServerLevel level, BlockPos hall) {
		Data data = Data.get(level);
		Camp camp = data.camps.get(hall);
		if (camp != null) {
			if (level.isPositionEntityTicking(camp.pos())) {
				Entity chief = level.getEntity(camp.chief());
				if (!(chief instanceof LivingEntity living) || !living.isAlive()) {
					breakUp(level, camp, null);
				} else {
					keepHome(level, camp);
				}
			}
			return;
		}
		if (!ENABLED || VillageRanks.of(level, hall).ordinal() < VillageRanks.Rank.VILLAGE.ordinal()) {
			return;
		}
		long day = Chronicle.day(level);
		Long cleared = data.lastBrokenUp.get(hall);
		if (cleared != null && day - cleared < REST_DAYS) {
			return;
		}
		int rounds = (int) Math.max(1, VillageNeeds.DAY / VillageNeeds.CHECK_EVERY);
		if (level.random.nextFloat() < DAILY_CHANCE / rounds) {
			BlockPos site = site(level, hall);
			if (site != null) {
				found(level, hall, site);
			}
		}
	}

	/** Somewhere out beyond the village for a camp: loaded, dry, fairly flat and open; null if there's nowhere. */
	@Nullable
	static BlockPos site(ServerLevel level, BlockPos hall) {
		for (int tries = 0; tries < 12; tries++) {
			double angle = level.random.nextDouble() * Math.PI * 2;
			int distance = NEAR + level.random.nextInt(FAR - NEAR + 1);
			BlockPos column = hall.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
			if (!level.isLoaded(column) || !level.isLoaded(column.offset(16, 0, 16)) || !level.isLoaded(column.offset(-16, 0, -16))) {
				continue;
			}
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
			if (Math.abs(ground.getY() - hall.getY()) > 32 || !fits(level, ground)) {
				continue;
			}
			if (!VillageHalls.nearest(level, ground).map(other -> other.distSqr(ground) > (double) NEAR * NEAR / 2).orElse(true)) {
				continue; // too close to another village
			}
			return ground;
		}
		return null;
	}

	/** The ground round {@code ground} is flat enough, dry and open for a camp. */
	static boolean fits(ServerLevel level, BlockPos ground) {
		int uneven = 0;
		int blocked = 0;
		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ground.offset(dx, 0, dz)).below();
				if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.above()).isEmpty()) {
					return false;
				}
				if (Math.abs(top.getY() - ground.getY()) > 1) {
					uneven++;
				}
				if (!level.getBlockState(ground.offset(dx, 1, dz)).canBeReplaced() || !level.getBlockState(ground.offset(dx, 2, dz)).canBeReplaced()) {
					blocked++;
				}
			}
		}
		return uneven <= 20 && blocked <= 20;
	}

	/** Sets the camp down with its middle at {@code ground} and its band in it; null if the camp's blueprint is missing. */
	@Nullable
	public static Camp found(ServerLevel level, BlockPos hall, BlockPos ground) {
		StructureTemplate template = level.getStructureManager().get(CAMP).orElse(null);
		if (template == null) {
			return null;
		}
		Rotation rotation = Rotation.getRandom(level.random);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
		var size = template.getSize(rotation);
		BlockPos origin = template.getZeroPositionWithTransform(ground.offset(-size.getX() / 2, 0, -size.getZ() / 2), net.minecraft.world.level.block.Mirror.NONE, rotation);
		// (getZeroPositionWithTransform turns the corner so the rotated camp covers the same box)
		BoundingBox box = template.getBoundingBox(settings, origin);
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY() + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.getBlockState(p).isAir()) {
				level.removeBlock(p, false); // the grass, a bush, a sapling
			}
		}
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y > box.minY() - 4; y--) { // no floating ground
					BlockPos p = new BlockPos(x, y, z);
					if (!level.getBlockState(p).canBeReplaced()) {
						break;
					}
					level.setBlock(p, Blocks.DIRT.defaultBlockState(), 2);
				}
			}
		}
		template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
		BlockPos middle = origin.offset(StructureTemplate.calculateRelativePosition(settings, FIRE));
		Mob chief = spawn(level, standOn(level, origin.offset(StructureTemplate.calculateRelativePosition(settings, CHIEF_SPOT))), middle, true, true);
		if (chief == null) {
			return null;
		}
		int band = 3 + level.random.nextInt(2);
		for (int i = 0; i < band; i++) {
			BlockPos at = origin.offset(StructureTemplate.calculateRelativePosition(settings, BAND_SPOTS.get(i)));
			spawn(level, standOn(level, at), middle, false, i % 2 == 0);
		}
		Camp camp = new Camp(middle, hall.immutable(), chief.getUUID(), Chronicle.day(level));
		Data data = Data.get(level);
		data.camps.put(camp.hall(), camp);
		data.setDirty();
		Component where = VillageHallScreen.where(hall, middle);
		for (ServerPlayer player : players(level, hall)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.bandits.camp", VillageHalls.name(level, hall), where)
				.withStyle(ChatFormatting.RED));
		}
		Chronicle.record(level, hall, Chronicle.Kind.RAID, Component.translatable("chronicle.aliveworkplace.bandit_camp", where));
		AliveWorkplace.LOG.info("Bandits made camp at {} near the village hall at {}", middle, hall);
		return camp;
	}

	/** A bandit (a pillager or, if {@code axe}, a vindicator), or the chief (a vindicator in iron), staying round {@code camp}. */
	@Nullable
	static Mob spawn(ServerLevel level, BlockPos at, BlockPos camp, boolean chief, boolean axe) {
		Mob mob = (axe ? EntityType.VINDICATOR : EntityType.PILLAGER).create(level);
		if (mob == null) {
			return null;
		}
		mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		mob.setPersistenceRequired();
		mob.addTag(TAG);
		if (mob instanceof Raider raider) {
			raider.setCanJoinRaid(false);
		}
		mob.setCustomName(Component.translatable(chief ? "entity.aliveworkplace.bandit_chief" : "entity.aliveworkplace.bandit"));
		if (chief) {
			mob.addTag(CHIEF_TAG);
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			mob.setDropChance(EquipmentSlot.HEAD, 0f);
			mob.setDropChance(EquipmentSlot.CHEST, 0f);
			var health = mob.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.addPermanentModifier(new AttributeModifier(AliveWorkplace.id("bandit_chief"), CHIEF_HEALTH, AttributeModifier.Operation.ADD_VALUE));
				mob.setHealth(mob.getMaxHealth());
			}
		}
		if (mob instanceof PathfinderMob pathfinder) {
			pathfinder.restrictTo(camp, KEEP);
		}
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** A bandit raider for a night raid on the village (see {@link VillageRaids}): named, but not kept to the camp. */
	static Mob raider(ServerLevel level) {
		Mob mob = (level.random.nextFloat() < 0.5f ? EntityType.VINDICATOR : EntityType.PILLAGER).create(level);
		if (mob != null) {
			mob.addTag(TAG);
			if (mob instanceof Raider raider) {
				raider.setCanJoinRaid(false);
			}
			mob.setCustomName(Component.translatable("entity.aliveworkplace.bandit"));
		}
		return mob;
	}

	/** Bandits keep to their camp (a chunk reload forgets it). */
	static void keepHome(ServerLevel level, Camp camp) {
		for (Mob mob : band(level, camp)) {
			if (mob instanceof PathfinderMob pathfinder && !pathfinder.hasRestriction()) {
				pathfinder.restrictTo(camp.pos(), KEEP);
			}
		}
	}

	/** The camp's bandits about now. */
	static List<Mob> band(ServerLevel level, Camp camp) {
		return level.getEntitiesOfClass(Mob.class, new AABB(camp.pos()).inflate(48, 24, 48),
			m -> m.isAlive() && m.getTags().contains(TAG) && !m.getTags().contains(VillageRaids.TAG));
	}

	/** Every death: the chief falling breaks up his camp. */
	public static void onDeath(ServerLevel level, LivingEntity entity, DamageSource source) {
		if (!entity.getTags().contains(CHIEF_TAG)) {
			return;
		}
		for (Camp camp : List.copyOf(Data.get(level).camps.values())) {
			if (camp.chief().equals(entity.getUUID())) {
				breakUp(level, camp, source.getEntity());
			}
		}
	}

	/** The camp is broken up: the band scatters, the village is told, the chronicle remembers who did it. */
	static void breakUp(ServerLevel level, Camp camp, @Nullable Entity by) {
		Data data = Data.get(level);
		data.camps.remove(camp.hall());
		data.lastBrokenUp.put(camp.hall(), Chronicle.day(level));
		data.setDirty();
		for (Mob mob : band(level, camp)) {
			level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
			mob.discard();
		}
		Component who = by instanceof ServerPlayer || by instanceof net.minecraft.world.entity.npc.Villager ? by.getDisplayName() : null;
		Component name = VillageHalls.name(level, camp.hall());
		for (ServerPlayer player : players(level, camp.hall())) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.bandits.broken_up", name).withStyle(ChatFormatting.GREEN));
		}
		Chronicle.record(level, camp.hall(), Chronicle.Kind.RAID, who != null
			? Component.translatable("chronicle.aliveworkplace.bandit_camp_broken_by", who)
			: Component.translatable("chronicle.aliveworkplace.bandit_camp_broken"));
		level.playSound(null, camp.hall(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5f, 1f);
	}

	private static List<ServerPlayer> players(ServerLevel level, BlockPos hall) {
		double r = VillageHalls.RADIUS + FAR;
		return level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r * r);
	}

	private static BlockPos standOn(ServerLevel level, BlockPos at) {
		BlockPos p = at;
		for (int i = 0; i < 4 && !level.getBlockState(p).canBeReplaced(); i++) {
			p = p.above();
		}
		return p;
	}

	/** Forgets every camp (tests). */
	public static void forget(ServerLevel level) {
		Data data = Data.get(level);
		data.camps.clear();
		data.lastBrokenUp.clear();
		data.setDirty();
	}

	/** The camps of one dimension, and when each village last broke one up. */
	static final class Data extends SavedData {
		private static final String NAME = "aliveworkplace_bandit_camps";
		final Map<BlockPos, Camp> camps = new LinkedHashMap<>();
		final Map<BlockPos, Long> lastBrokenUp = new LinkedHashMap<>();

		static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			for (Camp camp : camps.values()) {
				CompoundTag c = new CompoundTag();
				c.putLong("pos", camp.pos().asLong());
				c.putLong("hall", camp.hall().asLong());
				Nbt.putUuid(c, "chief", camp.chief());
				c.putLong("day", camp.day());
				list.add(c);
			}
			tag.put("camps", list);
			ListTag cleared = new ListTag();
			for (Map.Entry<BlockPos, Long> e : lastBrokenUp.entrySet()) {
				CompoundTag c = new CompoundTag();
				c.putLong("hall", e.getKey().asLong());
				c.putLong("day", e.getValue());
				cleared.add(c);
			}
			tag.put("broken_up", cleared);
			return tag;
		}

		static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			ListTag list = Nbt.getList(tag, "camps", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag c = Nbt.compoundAt(list, i);
				if (Nbt.hasUuid(c, "chief")) {
					Camp camp = new Camp(BlockPos.of(Nbt.getLong(c, "pos")), BlockPos.of(Nbt.getLong(c, "hall")), Nbt.getUuid(c, "chief"), Nbt.getLong(c, "day"));
					data.camps.put(camp.hall(), camp);
				}
			}
			ListTag cleared = Nbt.getList(tag, "broken_up", Tag.TAG_COMPOUND);
			for (int i = 0; i < cleared.size(); i++) {
				data.lastBrokenUp.put(BlockPos.of(Nbt.getLong(Nbt.compoundAt(cleared, i), "hall")), Nbt.getLong(Nbt.compoundAt(cleared, i), "day"));
			}
			return data;
		}
	}

	private BanditCamps() {
	}
}
