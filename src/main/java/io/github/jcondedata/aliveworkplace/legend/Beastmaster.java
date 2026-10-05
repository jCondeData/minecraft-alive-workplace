package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.guard.GuardCombat;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Beastmaster (ROADMAP 29.20), a Rare Rancher Legend {@code legends/beastmaster.json}: a prisoner in a pillager
 * outpost (29.9) once the village keeps a ranch (10 animals within 16 blocks of a Rancher's, Butcher's or Shepherd's
 * workstation), or born to a Rancher (29.7). Likes clothes. Their powers, worked on the hall's round:
 * <ul>
 *   <li>{@code war_dogs} ({@link WarDogsPower}): with bones in the chests by their workstation they tame a wolf for each
 *   guard of the village without one (one of the village's free wolves; when only a pair is left, they breed the pair
 *   instead), fit it with wolf armour when the chests hold the scutes, and send it to the guard. The dog follows the
 *   guard on patrol and in a raid and attacks what the guard fights ({@link #dogTick}). One per guard; a lost dog is
 *   replaced {@link WarDogsPower#days} days after it fell.</li>
 *   <li>{@code horse_breeding} ({@link HorseBreedingPower}): once a day, with two golden carrots (or apples) in the
 *   chests, they breed the two best horses of a kind on the ranch; the foal takes the best speed, jump and health of its
 *   parents and a little more, never past vanilla's best ({@link #foalStat}). Their grown foals are saddled from the
 *   chests, and {@code guard/Cavalry} guards take them first.</li>
 * </ul>
 * Saved: the guard's dog on the guard ({@link ModAttachments#WAR_DOG}), whose dog a wolf is on the wolf
 * ({@link ModAttachments#WAR_DOG_OF}), a bred horse's day ({@link ModAttachments#BRED_HORSE}) and the Beastmaster's last
 * foal day ({@link ModAttachments#LAST_FOAL}).
 */
public final class Beastmaster {
	public static final ResourceLocation ID = AliveWorkplace.id("beastmaster");
	/** How far round the Beastmaster's workstation the ranch's horses are (as {@code animals_at_job} counts the ranch). */
	public static final int RANCH = 16;
	/**
	 * A dog further than this from its guard (and not fighting) comes to heel; further than {@link #TELEPORT}, it's
	 * fetched to the guard's side (as vanilla's pets are fetched to their owner: a wolf can't path much further).
	 */
	static final double FOLLOW = 4;
	static final double TELEPORT = 12;
	/** How far a dog goes for its guard's foe. */
	static final double CHASE = 32;
	/** How recently (ticks) a guard was hit for the dog to go for the one who hit them. */
	static final int HURT_RECENTLY = 100;

	// Vanilla's best horse (1.21.1's AbstractHorse.generateSpeed/JumpStrength/MaxHealth at their tops), and its worst.
	public static final double MAX_SPEED = (0.45 + 0.3 * 3) * 0.25;
	public static final double MIN_SPEED = 0.45 * 0.25;
	public static final double MAX_JUMP = 0.4 + 0.2 * 3;
	public static final double MIN_JUMP = 0.4;
	public static final double MAX_HEALTH = 15 + 7 + 8;
	public static final double MIN_HEALTH = 15;

	/** {@code war_dogs}: {@code bones} bones tame a dog, {@code scutes} armadillo scutes make its armour, {@code days} before a lost one is replaced. */
	public record WarDogsPower(int bones, int scutes, int days) implements Power {
		public static final WarDogsPower DEFAULT = new WarDogsPower(3, 6, 2);

		static WarDogsPower read(JsonObject json) {
			int bones = json.has("bones") ? json.get("bones").getAsInt() : 3;
			int scutes = json.has("scutes") ? json.get("scutes").getAsInt() : 6;
			int days = json.has("days") ? json.get("days").getAsInt() : 2;
			if (bones < 1 || scutes < 1 || days < 0) {
				throw new IllegalArgumentException("'bones' or 'scutes' below 1, or 'days' below 0");
			}
			return new WarDogsPower(bones, scutes, days);
		}

		@Override
		public String type() {
			return "war_dogs";
		}

		@Override
		public Component describe() {
			return Component.translatable("legend.aliveworkplace.power.war_dogs", bones, scutes, days);
		}
	}

	/** {@code horse_breeding}: foals {@code bonus} (of vanilla's range) past their best parent; no breeding with {@code horses} on the ranch. */
	public record HorseBreedingPower(float bonus, int horses) implements Power {
		public static final HorseBreedingPower DEFAULT = new HorseBreedingPower(0.05f, 8);

		static HorseBreedingPower read(JsonObject json) {
			float bonus = json.has("bonus") ? json.get("bonus").getAsFloat() : 0.05f;
			int horses = json.has("horses") ? json.get("horses").getAsInt() : 8;
			if (bonus < 0 || bonus > 1 || horses < 2) {
				throw new IllegalArgumentException("'bonus' outside 0..1, or 'horses' below 2");
			}
			return new HorseBreedingPower(bonus, horses);
		}

		@Override
		public String type() {
			return "horse_breeding";
		}

		@Override
		public Component describe() {
			return Component.translatable("legend.aliveworkplace.power.horse_breeding", horses);
		}
	}

	/** A guard's war dog: the dog (empty: none now) and the day the last one fell ({@code -1}: none fell). */
	public record Dog(Optional<UUID> dog, long lost) {
		public static final Codec<Dog> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.optionalFieldOf("dog").forGetter(Dog::dog),
			Codec.LONG.optionalFieldOf("lost", -1L).forGetter(Dog::lost)
		).apply(i, Dog::new));
	}

	static void register() {
		Powers.register("war_dogs", WarDogsPower::read);
		Powers.register("horse_breeding", HorseBreedingPower::read);
	}

	static void init() {
		Platform.get().afterDeath(Beastmaster::afterDeath);
		Platform.get().onEntityLoad((entity, level) -> {
			if (entity instanceof Wolf wolf) {
				onLoad(wolf);
			}
		});
	}

	/**
	 * A war dog loaded from a save: tame again. Vanilla only keeps a wolf tame through a reload when a player owns it,
	 * and a war dog is the guard's, not a player's.
	 */
	public static void onLoad(Wolf wolf) {
		if (isWarDog(wolf) && !wolf.isTame()) {
			wolf.setTame(true, true);
		}
	}

	private static boolean inVillage(ServerLevel level, LegendPowers.Active a, BlockPos hall) {
		return a.data().hall().map(hall::equals).orElseGet(() -> VillageHalls.nearest(level, a.villager().blockPosition()).map(hall::equals).orElse(false));
	}

	/** The settled Beastmasters of the village round {@code hall} (their powers working). */
	static List<LegendPowers.Active> of(ServerLevel level, BlockPos hall) {
		return LegendPowers.settled(level).stream()
			.filter(a -> a.villager().isAlive() && inVillage(level, a, hall)
				&& (!a.legend().powers(WarDogsPower.class).isEmpty() || !a.legend().powers(HorseBreedingPower.class).isEmpty()))
			.toList();
	}

	/** The chests by {@code villager}'s workstation. */
	public static List<BlockPos> chests(ServerLevel level, Villager villager) {
		return Builders.benchPos(villager).map(b -> SupplyContainers.find(level, b, null)).orElse(List.of());
	}

	/** The hall's round: each Beastmaster of the village sees to the guards' dogs and the ranch's horses. */
	public static void round(ServerLevel level, BlockPos hall) {
		if (!Legends.ENABLED) {
			return;
		}
		for (LegendPowers.Active a : of(level, hall)) {
			for (WarDogsPower p : a.legend().powers(WarDogsPower.class)) {
				warDogs(level, hall, a.villager(), p);
			}
			for (HorseBreedingPower p : a.legend().powers(HorseBreedingPower.class)) {
				breedHorses(level, a.villager(), p);
				saddle(level, a.villager());
			}
		}
	}

	// ---- War dogs ----

	/** {@code guard}'s war dog, if it's loaded and alive. */
	@Nullable
	public static Wolf dog(ServerLevel level, Villager guard) {
		Dog d = ModAttachments.WAR_DOG.get(guard);
		if (d == null || d.dog().isEmpty()) {
			return null;
		}
		return level.getEntity(d.dog().get()) instanceof Wolf wolf && wolf.isAlive() ? wolf : null;
	}

	/** Whether {@code wolf} is someone's war dog. */
	public static boolean isWarDog(Wolf wolf) {
		return ModAttachments.WAR_DOG_OF.has(wolf);
	}

	/** A wolf of the village a Beastmaster may take: grown, alive, nobody's (no player's pet, no guard's dog), off its lead. */
	static boolean free(Wolf wolf) {
		return wolf.isAlive() && !wolf.isBaby() && !isWarDog(wolf) && (!wolf.isTame() || wolf.getOwnerUUID() == null) && !wolf.isLeashed();
	}

	/**
	 * The war-dog round: every guard of the village without a dog (and past the wait after losing one) gets one, while the
	 * chests hold the bones and the village has a free wolf; dogs without armour get it while the chests hold the scutes.
	 * Returns the dogs sent out this round.
	 */
	public static List<Wolf> warDogs(ServerLevel level, BlockPos hall, Villager beastmaster, WarDogsPower power) {
		List<Wolf> sent = new ArrayList<>();
		List<BlockPos> chests = chests(level, beastmaster);
		long today = Chronicle.day(level);
		AABB area = VillageHalls.area(hall);
		List<Villager> guards = level.getEntitiesOfClass(Villager.class, area, v -> v.isAlive() && Guards.isGuard(v));
		Map<UUID, Villager> byId = new HashMap<>();
		for (Villager g : guards) {
			byId.put(g.getUUID(), g);
		}
		// Dogs back from somewhere their guard couldn't see: their own guard's again, else free.
		for (Wolf wolf : level.getEntitiesOfClass(Wolf.class, area, w -> w.isAlive() && isWarDog(w))) {
			Villager guard = byId.get(ModAttachments.WAR_DOG_OF.get(wolf));
			if (guard == null) {
				continue; // their guard is away: left be
			}
			Dog d = ModAttachments.WAR_DOG.get(guard);
			if (d == null || d.dog().isEmpty()) {
				ModAttachments.WAR_DOG.set(guard, new Dog(Optional.of(wolf.getUUID()), -1));
			} else if (!d.dog().get().equals(wolf.getUUID())) {
				release(wolf);
			}
		}
		for (Villager guard : guards) {
			Wolf dog = dog(level, guard);
			if (dog != null) {
				armour(level, chests, dog, power);
				continue;
			}
			Dog d = ModAttachments.WAR_DOG.get(guard);
			if (d != null && d.dog().isPresent()) {
				// A dog the guard can't find any more: lost, from today.
				d = new Dog(Optional.empty(), today);
				ModAttachments.WAR_DOG.set(guard, d);
			}
			if (d != null && d.lost() >= 0 && today - d.lost() < power.days()) {
				continue;
			}
			Wolf wolf = tame(level, hall, beastmaster, chests, power);
			if (wolf == null) {
				break; // no bones or no wolf for anyone else either
			}
			send(level, hall, beastmaster, guard, wolf);
			armour(level, chests, wolf, power);
			sent.add(wolf);
		}
		return sent;
	}

	/**
	 * A wolf tamed by {@code beastmaster} for a guard, the bones taken, or null: no bones, no free wolf, or only a pair
	 * left (then they breed the pair, if they can, and the pup is taken once it's grown).
	 */
	@Nullable
	static Wolf tame(ServerLevel level, BlockPos hall, Villager beastmaster, List<BlockPos> chests, WarDogsPower power) {
		if (SupplyContainers.count(level, chests, Items.BONE) < power.bones()) {
			return null;
		}
		List<Wolf> wolves = level.getEntitiesOfClass(Wolf.class, VillageHalls.area(hall), Beastmaster::free);
		if (wolves.isEmpty()) {
			return null;
		}
		if (wolves.size() == 2) {
			Wolf a = wolves.get(0);
			Wolf b = wolves.get(1);
			if (a.getAge() == 0 && b.getAge() == 0) {
				a.spawnChildFromBreeding(level, b);
				hearts(level, a);
				hearts(level, b);
			}
			return null;
		}
		Wolf wolf = wolves.stream().min(Comparator.comparingDouble(beastmaster::distanceToSqr)).orElseThrow();
		SupplyContainers.extract(level, chests, Items.BONE, power.bones());
		wolf.setTame(true, true);
		wolf.setOrderedToSit(false);
		wolf.setInSittingPose(false);
		wolf.setTarget(null);
		hearts(level, wolf);
		level.playSound(null, wolf.blockPosition(), SoundEvents.WOLF_PANT, SoundSource.NEUTRAL, 1f, 1f);
		return wolf;
	}

	/** {@code wolf} becomes {@code guard}'s war dog, named for them. */
	static void send(ServerLevel level, BlockPos hall, Villager beastmaster, Villager guard, Wolf wolf) {
		ModAttachments.WAR_DOG_OF.set(wolf, guard.getUUID());
		ModAttachments.WAR_DOG.set(guard, new Dog(Optional.of(wolf.getUUID()), -1));
		wolf.setCustomName(Component.translatable("entity.aliveworkplace.war_dog", Component.literal(guard.getName().getString())));
		wolf.setPersistenceRequired();
		Chronicle.record(level, hall, Chronicle.Kind.ARRIVED, Component.translatable("chronicle.aliveworkplace.war_dog.given",
			Component.literal(beastmaster.getName().getString()), Component.literal(guard.getName().getString())));
	}

	/** Wolf armour for {@code dog} from the scutes in the chests, if it has none and they're there. */
	static void armour(ServerLevel level, List<BlockPos> chests, Wolf dog, WarDogsPower power) {
		if (dog.hasArmor() || SupplyContainers.count(level, chests, Items.ARMADILLO_SCUTE) < power.scutes()) {
			return;
		}
		SupplyContainers.extract(level, chests, Items.ARMADILLO_SCUTE, power.scutes());
		dog.setBodyArmorItem(new ItemStack(Items.WOLF_ARMOR));
		level.playSound(null, dog.blockPosition(), SoundEvents.ARMOR_EQUIP_WOLF.value(), SoundSource.NEUTRAL, 1f, 1f);
	}

	/** {@code wolf} is nobody's war dog any more (a tame village wolf another guard may get). */
	static void release(Wolf wolf) {
		ModAttachments.WAR_DOG_OF.remove(wolf);
		wolf.setCustomName(null);
		wolf.setTarget(null);
	}

	/** What {@code guard} is fighting: their foe in a fight, else a foe that hit them lately; null: nothing. */
	@Nullable
	public static LivingEntity foeOf(Villager guard) {
		LivingEntity foe = GuardCombat.foe(guard);
		if (foe != null && foe.isAlive()) {
			return foe;
		}
		LivingEntity hit = guard.getLastHurtByMob();
		if (hit != null && hit.isAlive() && guard.tickCount - guard.getLastHurtByMobTimestamp() < HURT_RECENTLY && Guards.isFoe(hit, guard)) {
			return hit;
		}
		return null;
	}

	/**
	 * Every 10 ticks of a guard with a war dog (from {@link Legends#tick}): the dog goes for what the guard fights, else
	 * comes to heel (fetched to the guard's side when far behind). A guard who's no guard any more lets the dog go.
	 */
	public static void dogTick(Villager guard) {
		if (!(guard.level() instanceof ServerLevel level)) {
			return;
		}
		Wolf dog = dog(level, guard);
		if (dog == null) {
			return;
		}
		if (!Guards.isGuard(guard)) {
			release(dog);
			ModAttachments.WAR_DOG.remove(guard);
			return;
		}
		onLoad(dog);
		if (dog.isOrderedToSit() || dog.isInSittingPose()) {
			dog.setOrderedToSit(false);
			dog.setInSittingPose(false);
		}
		LivingEntity foe = foeOf(guard);
		if (foe != null && foe != dog.getTarget() && dog.distanceToSqr(foe) < CHASE * CHASE) {
			dog.setTarget(foe);
		}
		LivingEntity target = dog.getTarget();
		if (target != null && target.isAlive()) {
			return;
		}
		double d2 = dog.distanceToSqr(guard);
		if (d2 > TELEPORT * TELEPORT) {
			dog.getNavigation().stop();
			dog.moveTo(guard.getX(), guard.getY(), guard.getZ(), dog.getYRot(), dog.getXRot());
		} else if (d2 > FOLLOW * FOLLOW) {
			dog.getNavigation().moveTo(guard, 1.2);
		}
	}

	static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (entity instanceof Wolf wolf && isWarDog(wolf)) {
			UUID of = ModAttachments.WAR_DOG_OF.get(wolf);
			if (of != null && level.getEntity(of) instanceof Villager guard) {
				Dog d = ModAttachments.WAR_DOG.get(guard);
				if (d != null && d.dog().map(wolf.getUUID()::equals).orElse(false)) {
					ModAttachments.WAR_DOG.set(guard, new Dog(Optional.empty(), Chronicle.day(level)));
					Chronicle.record(level, guard.blockPosition(), Chronicle.Kind.DEATH, Component.translatable("chronicle.aliveworkplace.war_dog.lost",
						Component.literal(guard.getName().getString())));
				}
			}
		} else if (entity instanceof Villager guard && ModAttachments.WAR_DOG.has(guard)) {
			Wolf dog = dog(level, guard);
			if (dog != null) {
				release(dog);
			}
			ModAttachments.WAR_DOG.remove(guard);
		}
	}

	// ---- Fast horses ----

	/** A horse of the ranch: a horse, donkey or mule (no llamas), alive. */
	static boolean horse(AbstractHorse h) {
		return h.isAlive() && !(h instanceof Llama);
	}

	/** Whether {@code horse} was bred by a Beastmaster. */
	public static boolean bred(AbstractHorse horse) {
		return ModAttachments.BRED_HORSE.has(horse);
	}

	static AABB ranch(BlockPos bench) {
		return new AABB(bench).inflate(RANCH);
	}

	/** How good a horse is: its speed, jump and health each as a share of vanilla's range, added up. */
	public static double score(AbstractHorse h) {
		return (base(h, Attributes.MOVEMENT_SPEED) - MIN_SPEED) / (MAX_SPEED - MIN_SPEED)
			+ (base(h, Attributes.JUMP_STRENGTH) - MIN_JUMP) / (MAX_JUMP - MIN_JUMP)
			+ (base(h, Attributes.MAX_HEALTH) - MIN_HEALTH) / (MAX_HEALTH - MIN_HEALTH);
	}

	static double base(LivingEntity e, Holder<Attribute> attribute) {
		AttributeInstance a = e.getAttribute(attribute);
		return a == null ? 0 : a.getBaseValue();
	}

	/** A foal's stat: its better parent's and {@code bonus} of vanilla's range more, never past vanilla's best. */
	public static double foalStat(double a, double b, double min, double max, float bonus) {
		return Math.min(max, Math.max(a, b) + bonus * (max - min));
	}

	/**
	 * Once a day, with the ranch under the power's cap and two golden carrots (or apples) in the chests: the two best
	 * grown horses of one kind on the ranch that are ready to breed have a foal, tame and with the better stats. Returns
	 * the foal, or null.
	 */
	@Nullable
	public static AbstractHorse breedHorses(ServerLevel level, Villager beastmaster, HorseBreedingPower power) {
		BlockPos bench = Builders.benchPos(beastmaster).orElse(null);
		if (bench == null) {
			return null;
		}
		long today = Chronicle.day(level);
		Long last = ModAttachments.LAST_FOAL.get(beastmaster);
		if (last != null && last == today) {
			return null;
		}
		List<AbstractHorse> horses = level.getEntitiesOfClass(AbstractHorse.class, ranch(bench), Beastmaster::horse);
		if (horses.size() >= power.horses()) {
			return null;
		}
		List<BlockPos> chests = chests(level, beastmaster);
		if (SupplyContainers.count(level, chests, Items.GOLDEN_CARROT) + SupplyContainers.count(level, chests, Items.GOLDEN_APPLE) < 2) {
			return null;
		}
		Map<EntityType<?>, List<AbstractHorse>> ready = new HashMap<>();
		for (AbstractHorse h : horses) {
			if (!h.isBaby() && h.getAge() == 0 && !h.isVehicle()) {
				ready.computeIfAbsent(h.getType(), k -> new ArrayList<>()).add(h);
			}
		}
		AbstractHorse a = null;
		AbstractHorse b = null;
		double best = -1;
		for (List<AbstractHorse> kind : ready.values()) {
			if (kind.size() < 2) {
				continue;
			}
			List<AbstractHorse> sorted = kind.stream().sorted(Comparator.comparingDouble(Beastmaster::score).reversed()).toList();
			double s = score(sorted.get(0)) + score(sorted.get(1));
			if (s > best) {
				best = s;
				a = sorted.get(0);
				b = sorted.get(1);
			}
		}
		if (a == null) {
			return null;
		}
		AbstractHorse foal = breed(level, a, b, power.bonus());
		if (foal == null) {
			return null;
		}
		for (int i = 0; i < 2; i++) {
			SupplyContainers.takeOne(level, chests, s -> s.is(Items.GOLDEN_CARROT) || s.is(Items.GOLDEN_APPLE));
		}
		ModAttachments.LAST_FOAL.set(beastmaster, today);
		BlockPos hall = java.util.Optional.ofNullable(ModAttachments.LEGEND.get(beastmaster)).flatMap(LegendData::hall).orElse(bench);
		Chronicle.record(level, hall, Chronicle.Kind.BIRTH, Component.translatable("chronicle.aliveworkplace.beastmaster.foal",
			Component.literal(beastmaster.getName().getString())));
		return foal;
	}

	/** {@code a} and {@code b}'s foal (as vanilla breeds them), with the Beastmaster's stats, tame and marked as theirs. */
	@Nullable
	public static AbstractHorse breed(ServerLevel level, AbstractHorse a, AbstractHorse b, float bonus) {
		AgeableMob child = a.getBreedOffspring(level, b);
		if (!(child instanceof AbstractHorse foal)) {
			return null;
		}
		foal.setBaby(true);
		foal.moveTo(a.getX(), a.getY(), a.getZ(), 0f, 0f);
		a.finalizeSpawnChildFromBreeding(level, b, foal);
		set(foal, Attributes.MOVEMENT_SPEED, foalStat(base(a, Attributes.MOVEMENT_SPEED), base(b, Attributes.MOVEMENT_SPEED), MIN_SPEED, MAX_SPEED, bonus));
		set(foal, Attributes.JUMP_STRENGTH, foalStat(base(a, Attributes.JUMP_STRENGTH), base(b, Attributes.JUMP_STRENGTH), MIN_JUMP, MAX_JUMP, bonus));
		set(foal, Attributes.MAX_HEALTH, foalStat(base(a, Attributes.MAX_HEALTH), base(b, Attributes.MAX_HEALTH), MIN_HEALTH, MAX_HEALTH, bonus));
		foal.setHealth(foal.getMaxHealth());
		foal.setTamed(true);
		ModAttachments.BRED_HORSE.set(foal, Chronicle.day(level));
		level.addFreshEntityWithPassengers(foal);
		hearts(level, a);
		hearts(level, b);
		return foal;
	}

	private static void set(LivingEntity e, Holder<Attribute> attribute, double value) {
		AttributeInstance a = e.getAttribute(attribute);
		if (a != null) {
			a.setBaseValue(value);
		}
	}

	/** Saddles from the chests on their grown, unsaddled foals on the ranch, for the cavalry. */
	public static int saddle(ServerLevel level, Villager beastmaster) {
		BlockPos bench = Builders.benchPos(beastmaster).orElse(null);
		if (bench == null) {
			return 0;
		}
		List<BlockPos> chests = chests(level, beastmaster);
		int n = 0;
		for (AbstractHorse h : level.getEntitiesOfClass(AbstractHorse.class, ranch(bench), h -> horse(h) && bred(h) && !h.isBaby() && h.isSaddleable() && !h.isSaddled())) {
			ItemStack saddle = SupplyContainers.takeOne(level, chests, s -> s.is(Items.SADDLE));
			if (saddle.isEmpty()) {
				break;
			}
			h.setTamed(true);
			h.equipSaddle(saddle, SoundSource.NEUTRAL);
			n++;
		}
		return n;
	}

	private static void hearts(ServerLevel level, Entity e) {
		level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 4, 0.4, 0.3, 0.4, 0);
	}

	private Beastmaster() {
	}
}
