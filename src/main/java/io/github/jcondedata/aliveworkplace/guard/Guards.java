package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** What makes a guard a guard: who they fight, how hard they hit, which gear is better. */
public final class Guards {
	/** How far from the Guard Post a guard patrols and chases. */
	public static int RADIUS = 24;
	private static final ResourceLocation HEALTH_BONUS = AliveWorkplace.id("guard_health");
	/** Guards are tougher than other villagers: 40 health instead of 20. */
	private static final double EXTRA_HEALTH = 20;

	/** What kind of guard someone is, from what they hold in their off hand. */
	public enum Kind {
		/** A sword and nothing else. */
		GUARD,
		/** A bow or a crossbow: shoots, keeps creepers at a distance. */
		ARCHER,
		/** A shield: blocks blows from in front. */
		KNIGHT,
		/** A healing or regeneration potion in hand: tends the wounded with the potions they carry. */
		MEDIC;

		public net.minecraft.network.chat.Component title() {
			return net.minecraft.network.chat.Component.translatable("guard_kind.aliveworkplace." + name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	public static Kind kind(Villager guard) {
		ItemStack off = guard.getItemBySlot(EquipmentSlot.OFFHAND);
		if (isBow(off)) {
			return Kind.ARCHER;
		}
		if (isShield(off)) {
			return Kind.KNIGHT;
		}
		if (isMedicine(off)) {
			return Kind.MEDIC;
		}
		return Kind.GUARD;
	}

	public static boolean isShield(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.ShieldItem;
	}

	/** A healing or regeneration potion (what a medic holds and uses). */
	public static boolean isMedicine(ItemStack stack) {
		var contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && (stack.is(net.minecraft.world.item.Items.POTION) || stack.is(net.minecraft.world.item.Items.SPLASH_POTION))
			&& java.util.stream.StreamSupport.stream(contents.getAllEffects().spliterator(), false)
				.anyMatch(e -> e.is(net.minecraft.world.effect.MobEffects.HEAL) || e.is(net.minecraft.world.effect.MobEffects.REGENERATION));
	}

	/** The chance a knight blocks a blow from in front: 60%, 5% more a level, at most 85%. */
	public static float blockChance(Villager guard) {
		return Math.min(0.85f, 0.6f + 0.05f * (BuilderLevels.level(guard) - 1));
	}

	/**
	 * A knight's shield: a blow from in front (not one that goes through shields, like fire or a fall) is blocked, now
	 * and then — the shield takes the wear, the attacker is pushed back. Returns true if blocked.
	 */
	public static boolean block(Villager guard, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (!isGuard(guard) || !isShield(guard.getItemBySlot(EquipmentSlot.OFFHAND))
			|| source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD) || source.getSourcePosition() == null) {
			return false;
		}
		net.minecraft.world.phys.Vec3 to = source.getSourcePosition().subtract(guard.position()).multiply(1, 0, 1);
		net.minecraft.world.phys.Vec3 look = guard.getViewVector(1f).multiply(1, 0, 1);
		if (to.lengthSqr() < 1.0E-4 || look.lengthSqr() < 1.0E-4 || to.normalize().dot(look.normalize()) < 0.2) {
			return false;
		}
		if (guard.getRandom().nextFloat() >= blockChance(guard)) {
			return false;
		}
		ItemStack shield = guard.getItemBySlot(EquipmentSlot.OFFHAND);
		shield.hurtAndBreak(1 + (int) Math.floor(amount), guard, EquipmentSlot.OFFHAND);
		guard.level().playSound(null, guard.blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.NEUTRAL,
			1f, 0.8f + guard.getRandom().nextFloat() * 0.4f);
		if (source.getDirectEntity() instanceof LivingEntity attacker) {
			attacker.knockback(0.5, guard.getX() - attacker.getX(), guard.getZ() - attacker.getZ());
		}
		return true;
	}

	/** Potions a guard carries at most: a medic twice as many. */
	public static int potionsFor(Villager guard) {
		return kind(guard) == Kind.MEDIC ? POTIONS * 2 : POTIONS;
	}

	/** How far a medic looks for the wounded. */
	public static final int MEDIC_RANGE = 12;

	/**
	 * A medic tends the wounded: the most hurt guard or villager within {@link #MEDIC_RANGE} below 60% health gets a healing
	 * or regeneration potion from the medic's bag (never the one in hand). Returns who was treated, or null.
	 */
	@org.jetbrains.annotations.Nullable
	public static LivingEntity tendWounded(net.minecraft.server.level.ServerLevel level, Villager medic) {
		if (kind(medic) != Kind.MEDIC) {
			return null;
		}
		var bag = medic.getAttachedOrCreate(io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG);
		LivingEntity patient = level.getEntitiesOfClass(LivingEntity.class, medic.getBoundingBox().inflate(MEDIC_RANGE, 4, MEDIC_RANGE),
				e -> e.isAlive() && (e instanceof Villager || e instanceof net.minecraft.world.entity.animal.IronGolem) && e.getHealth() < e.getMaxHealth() * 0.6f)
			.stream().min(java.util.Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth())).orElse(null);
		if (patient == null) {
			return null;
		}
		ItemStack potion = bag.takeFirst(Guards::isMedicine);
		if (potion.isEmpty()) {
			return null;
		}
		var contents = potion.get(DataComponents.POTION_CONTENTS);
		if (contents != null) {
			contents.forEachEffect(effect -> {
				if (effect.getEffect().value().isInstantenous()) {
					effect.getEffect().value().applyInstantenousEffect(medic, medic, patient, effect.getAmplifier(), 1.0);
				} else {
					patient.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect));
				}
			});
		}
		medic.swing(net.minecraft.world.InteractionHand.OFF_HAND);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, patient.getX(), patient.getY() + patient.getBbHeight() + 0.3, patient.getZ(),
			4, 0.3, 0.2, 0.3, 0);
		level.playSound(null, patient.blockPosition(), net.minecraft.sounds.SoundEvents.SPLASH_POTION_BREAK, net.minecraft.sounds.SoundSource.NEUTRAL, 0.6f, 1.2f);
		return patient;
	}

	public static boolean isGuard(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.GUARD;
	}

	/**
	 * Monsters only: never players, villagers, golems, pets or Pokémon. Creepers are left alone (a guard
	 * with a sword would only set them off) — unless the guard has a bow (see {@link #isFoe(LivingEntity, Villager)}).
	 */
	public static boolean isFoe(LivingEntity entity) {
		return entity.isAlive() && (entity instanceof Monster || entity instanceof Slime) && !(entity instanceof Creeper);
	}

	/** A foe for this guard: any monster, and creepers too once the guard carries a bow. */
	public static boolean isFoe(LivingEntity entity, Villager guard) {
		return isFoe(entity) || entity.isAlive() && entity instanceof Creeper && hasBow(guard);
	}

	/** A bow a guard can shoot with (kept in their off hand). */
	/** A bow or a crossbow (anything that shoots arrows and wears out). */
	public static boolean isBow(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof net.minecraft.world.item.BowItem
			|| stack.getItem() instanceof net.minecraft.world.item.CrossbowItem) && stack.isDamageableItem();
	}

	public static boolean isCrossbow(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.CrossbowItem;
	}

	/** Crossbows hit harder than bows: a guard trades a bow for one. */
	static int rangedRank(ItemStack stack) {
		return !isBow(stack) ? 0 : isCrossbow(stack) ? 2 : 1;
	}

	/** Arrows better than plain ones — spectral or tipped — that a guard with a bow carries (up to {@link #QUIVER}). */
	public static boolean isSpecialArrow(ItemStack stack) {
		return !stack.isEmpty() && stack.is(net.minecraft.tags.ItemTags.ARROWS) && !stack.is(net.minecraft.world.item.Items.ARROW);
	}

	/** Special arrows a guard carries at most. */
	public static final int QUIVER = 16;

	/** How many special arrows the guard carries (in their bag). */
	public static int quiver(Villager guard) {
		int n = 0;
		for (ItemStack stack : guard.getAttachedOrCreate(io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG).stacks()) {
			if (isSpecialArrow(stack)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Healing, regeneration and strength potions the guard carries (in their bag). */
	public static int potions(Villager guard) {
		int n = 0;
		for (ItemStack stack : guard.getAttachedOrCreate(io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG).stacks()) {
			if (io.github.jcondedata.aliveworkplace.brew.AlchemistWork.isGuardPotion(stack)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Potions a guard carries at most. */
	public static final int POTIONS = 3;

	/**
	 * A guard below half health drinks a healing or regeneration potion they carry; at the start of a fight, a strength
	 * potion if they aren't strong already. Returns whether they drank.
	 */
	public static boolean drink(Villager guard, boolean fightStarting) {
		var bag = guard.getAttachedOrCreate(io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG);
		boolean hurt = guard.getHealth() < guard.getMaxHealth() / 2;
		ItemStack potion = ItemStack.EMPTY;
		if (hurt) {
			potion = bag.takeFirst(s -> io.github.jcondedata.aliveworkplace.brew.AlchemistWork.isGuardPotion(s) && !isStrength(s));
		}
		if (potion.isEmpty() && fightStarting && !guard.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST)) {
			potion = bag.takeFirst(Guards::isStrength);
		}
		if (potion.isEmpty()) {
			return false;
		}
		var contents = potion.get(DataComponents.POTION_CONTENTS);
		if (contents != null) {
			contents.forEachEffect(effect -> {
				if (effect.getEffect().value().isInstantenous()) {
					effect.getEffect().value().applyInstantenousEffect(guard, guard, guard, effect.getAmplifier(), 1.0);
				} else {
					guard.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect));
				}
			});
		}
		guard.level().playSound(null, guard, net.minecraft.sounds.SoundEvents.GENERIC_DRINK, net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f);
		return true;
	}

	private static boolean isStrength(ItemStack stack) {
		var contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && contents.potion().map(p -> p.is(net.minecraft.world.item.alchemy.Potions.STRENGTH)
			|| p.is(net.minecraft.world.item.alchemy.Potions.STRONG_STRENGTH) || p.is(net.minecraft.world.item.alchemy.Potions.LONG_STRENGTH)).orElse(false);
	}

	public static boolean hasBow(Villager guard) {
		return isBow(guard.getItemBySlot(EquipmentSlot.OFFHAND));
	}

	/** Damage of one swing with {@code weapon} (fists: 1), before enchantments. */
	public static float baseDamage(ItemStack weapon) {
		float[] damage = {1f};
		if (!weapon.isEmpty()) {
			weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
				if (attribute.is(Attributes.ATTACK_DAMAGE) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
					damage[0] += (float) modifier.amount();
				}
			});
		}
		return damage[0];
	}

	/** Levels make guards hit harder too: +10% per level above Novice, and +10% per level of the village's Drill research. */
	public static float levelBonus(Villager villager) {
		return (1f + 0.1f * (BuilderLevels.level(villager) - 1))
			* (1f + 0.1f * io.github.jcondedata.aliveworkplace.research.Research.level(villager, io.github.jcondedata.aliveworkplace.research.Research.Topic.DRILL))
			* io.github.jcondedata.aliveworkplace.people.Traits.strength(villager);
	}

	public static boolean isWeapon(ItemStack stack) {
		return !stack.isEmpty() && baseDamage(stack) > 1f && stack.isDamageableItem();
	}

	/** Armor points a piece gives in its slot (0 for anything else). */
	public static int armorValue(ItemStack stack) {
		return stack.getItem() instanceof ArmorItem armor ? armor.getDefense() : 0;
	}

	/** Keeps the extra health while a villager is a guard, and takes it away when they aren't. */
	public static void updateHealth(Villager villager) {
		AttributeInstance health = villager.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		boolean guard = isGuard(villager);
		boolean has = health.getModifier(HEALTH_BONUS) != null;
		if (guard && !has) {
			health.addPermanentModifier(new AttributeModifier(HEALTH_BONUS, EXTRA_HEALTH, AttributeModifier.Operation.ADD_VALUE));
			villager.setHealth(villager.getHealth() + (float) EXTRA_HEALTH); // a new guard starts with the extra health too
		} else if (!guard && has) {
			health.removeModifier(HEALTH_BONUS);
			villager.setHealth(Math.min(villager.getHealth(), villager.getMaxHealth()));
		}
	}

	private Guards() {
	}
}
