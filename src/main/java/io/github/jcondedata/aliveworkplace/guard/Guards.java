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
			* (1f + 0.1f * io.github.jcondedata.aliveworkplace.research.Research.level(villager, io.github.jcondedata.aliveworkplace.research.Research.Topic.DRILL));
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
