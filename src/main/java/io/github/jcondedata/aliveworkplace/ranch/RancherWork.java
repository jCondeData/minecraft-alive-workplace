package io.github.jcondedata.aliveworkplace.ranch;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.PokemonPartners;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Rancher's shift at their Feed Trough: wild horses, donkeys and llamas around it are broken in (a few tries each, the
 * horse rearing until it gives in), tamed ones get the saddles from the chests, and pairs are bred up to
 * {@link RanchWork#CAP} of a kind (and horse armor or llama carpets from the chests put on the tamed ones) — horses and donkeys with golden carrots or golden apples, llamas with hay bales,
 * camels with cactus. With Cobblemon, the Pokémon in Pasture Blocks nearby are groomed once a day (friendship up by
 * {@link #GROOM}, and {@link #TREAT} more when there's a berry for them in the chests), so a pasture by the ranch is where
 * a Pokémon that evolves by friendship grows fond of its trainer.
 */
public class RancherWork extends RanchWork {
	static final Set<EntityType<?>> HERD = Set.of(EntityType.HORSE, EntityType.DONKEY, EntityType.LLAMA, EntityType.CAMEL);
	/** Friendship a grooming gives a pastured Pokémon. */
	public static int GROOM = 4;
	/** Friendship a berry treat adds to a grooming. */
	public static int TREAT = 6;
	/** Game time between two groomings of the same Pokémon (a day). */
	public static long GROOM_EVERY = 24000;
	/** How much a failed try calms a wild horse down (out of its temper, 100 for a horse). */
	static final int CALMER = 20;
	private static final boolean COBBLEMON = Platform.get().isModLoaded("cobblemon");
	private static final TagKey<Item> BERRIES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cobblemon", "berries"));
	/** Berries that lower a Pokémon's EVs: players keep them for that, so they're never a treat. */
	private static final TagKey<Item> FRIENDSHIP_BERRIES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cobblemon", "berries/friendship"));
	private static final Map<UUID, Long> GROOMED = new HashMap<>();

	private enum Chore { TAME, SADDLE, ARMOR, GROOM }

	@Nullable
	private Chore chore;

	public static boolean isRancher(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.RANCHER;
	}

	/** A berry a pastured Pokémon gets as a treat. */
	public static boolean isTreat(ItemStack stack) {
		return stack.is(BERRIES) && !stack.is(FRIENDSHIP_BERRIES);
	}

	/** What horses, donkeys, llamas and camels are bred with. */
	public static boolean isBreedingFood(ItemStack stack) {
		return stack.is(Items.GOLDEN_CARROT) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.HAY_BLOCK) || stack.is(Items.CACTUS);
	}

	/** Forget when the Pokémon were last groomed (tests). */
	public static void forget() {
		GROOMED.clear();
	}

	@Override
	protected boolean isOurs(Villager villager) {
		return isRancher(villager);
	}

	@Override
	protected Set<EntityType<?>> herd() {
		return HERD;
	}

	@Override
	protected boolean isDrop(ItemStack stack) {
		return false;
	}

	@Override
	protected boolean breedsWith(Animal animal, ItemStack food) {
		if (animal instanceof Llama) {
			return food.is(Items.HAY_BLOCK);
		}
		if (animal instanceof Camel) {
			return animal.isFood(food);
		}
		if (animal instanceof AbstractHorse) {
			return food.is(Items.GOLDEN_CARROT) || food.is(Items.GOLDEN_APPLE);
		}
		return animal.isFood(food);
	}

	/** Horses only breed tamed, unridden and in full health. */
	@Override
	protected boolean mayBreed(Animal animal) {
		return !(animal instanceof AbstractHorse horse) || horse.isTamed() && !horse.isVehicle() && horse.getHealth() >= horse.getMaxHealth();
	}

	@Nullable
	@Override
	protected Entity tendTarget(ServerLevel level, Villager villager, BlockPos station, List<BlockPos> own) {
		chore = null;
		List<Animal> animals = animals(level, station);
		Comparator<Entity> nearest = Comparator.comparingDouble(villager::distanceToSqr);
		// Wild ones broken in.
		Animal wild = animals.stream().filter(a -> a instanceof AbstractHorse h && !h.isTamed() && !h.isBaby() && !h.isVehicle())
			.min(nearest).orElse(null);
		if (wild != null) {
			chore = Chore.TAME;
			return wild;
		}
		// Saddles on the tamed ones, while there are saddles in the chests.
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		if (bag.count(Items.SADDLE) > 0 || SupplyContainers.firstWith(level, own, Items.SADDLE) != null) {
			Animal bare = animals.stream().filter(a -> a instanceof AbstractHorse h && h.isSaddleable() && !h.isSaddled() && !h.isVehicle())
				.min(nearest).orElse(null);
			if (bare != null) {
				chore = Chore.SADDLE;
				return bare;
			}
		}
		// Horse armor (and carpets for the llamas) on the tamed ones without, while the chests have some.
		Set<Item> stocked = SupplyContainers.contents(level, own).keySet();
		for (Animal a : animals.stream().sorted(nearest).toList()) {
			if (a instanceof AbstractHorse h && h.isTamed() && !h.isBaby() && !h.isVehicle() && h.canUseSlot(EquipmentSlot.BODY) && !h.isWearingBodyArmor()
				&& (bag.stacks().stream().anyMatch(h::isBodyArmorItem) || stocked.stream().anyMatch(item -> h.isBodyArmorItem(new ItemStack(item))))) {
				chore = Chore.ARMOR;
				return h;
			}
		}
		// The pastured Pokémon, groomed once a day.
		if (COBBLEMON) {
			long now = level.getGameTime();
			Vec3 middle = Vec3.atCenterOf(station);
			Entity pokemon = PokemonPartners.EXTENSION.call(p -> p.pastured(level, station, RADIUS), List.<Entity>of()).stream()
				.filter(p -> due(p, now) && PokemonPartners.EXTENSION.call(partners -> partners.canBefriend(p), false))
				.min(Comparator.comparingDouble(p -> p.distanceToSqr(middle))).orElse(null);
			if (pokemon != null) {
				chore = Chore.GROOM;
				return pokemon;
			}
		}
		return null;
	}

	private static boolean due(Entity pokemon, long now) {
		Long last = GROOMED.get(pokemon.getUUID());
		return last == null || now - last >= GROOM_EVERY;
	}

	/** A saddle in the bag before saddling; a berry, if the chests have one, before grooming. */
	@Nullable
	@Override
	protected Boolean prepare(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		if (chore == Chore.SADDLE) {
			if (bag.count(Items.SADDLE) > 0) {
				return true;
			}
			BlockPos chest = SupplyContainers.firstWith(level, own, Items.SADDLE);
			if (chest == null) {
				return null;
			}
			if (!walker.walkTo(level, villager, chest, 3.0)) {
				return false;
			}
			walker.reset();
			bag.addAll(Items.SADDLE, SupplyContainers.extract(level, own, Items.SADDLE, 1));
			return bag.count(Items.SADDLE) > 0 ? true : null;
		}
		if (chore == Chore.ARMOR && target instanceof AbstractHorse horse) {
			if (bag.stacks().stream().anyMatch(horse::isBodyArmorItem)) {
				return true;
			}
			BlockPos chest = SupplyContainers.firstMatching(level, own, horse::isBodyArmorItem);
			if (chest == null) {
				return null;
			}
			if (!walker.walkTo(level, villager, chest, 3.0)) {
				return false;
			}
			walker.reset();
			ItemStack armor = SupplyContainers.takeOne(level, own, horse::isBodyArmorItem);
			if (armor.isEmpty()) {
				return null;
			}
			bag.add(armor);
			return true;
		}
		if (chore == Chore.GROOM && bag.stacks().stream().noneMatch(RancherWork::isTreat)) {
			BlockPos chest = SupplyContainers.firstMatching(level, own, RancherWork::isTreat);
			if (chest == null) {
				return true; // groomed without a treat
			}
			if (!walker.walkTo(level, villager, chest, 3.0)) {
				return false;
			}
			walker.reset();
			ItemStack treat = SupplyContainers.takeOne(level, own, RancherWork::isTreat);
			if (!treat.isEmpty()) {
				bag.add(treat);
			}
		}
		return true;
	}

	@Override
	protected boolean tend(ServerLevel level, Villager villager, Entity animal, List<BlockPos> own, BuilderBag bag) {
		villager.swing(InteractionHand.MAIN_HAND);
		if (chore == Chore.TAME && animal instanceof AbstractHorse horse && !horse.isTamed()) {
			if (level.random.nextInt(horse.getMaxTemper()) < horse.getTemper()) {
				horse.setTamed(true);
				Employer boss = ModAttachments.BUILDER_EMPLOYER.get(villager);
				if (boss != null) {
					horse.setOwnerUUID(boss.id());
				}
				level.broadcastEntityEvent(horse, (byte) 7); // hearts
				ModAttachments.HORSES_TAMED.set(villager, ModAttachments.HORSES_TAMED.getOrElse(villager, 0) + 1);
				BuilderLevels.addXp(level, villager, 2, null);
			} else {
				// Partners (Rapidash, Tauros...) calm them down quicker.
				horse.modifyTemper(Math.round(CALMER / Partners.factor(villager)));
				horse.makeMad();
			}
		} else if (chore == Chore.SADDLE && animal instanceof AbstractHorse horse && horse.isSaddleable() && !horse.isSaddled()
			&& bag.remove(Items.SADDLE, 1) == 1) {
			horse.equipSaddle(new ItemStack(Items.SADDLE), SoundSource.NEUTRAL);
			level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_SADDLE, SoundSource.NEUTRAL, 0.8f, 1f);
			BuilderLevels.addXp(level, villager, 1, null);
		} else if (chore == Chore.ARMOR && animal instanceof AbstractHorse horse && !horse.isWearingBodyArmor()) {
			ItemStack armor = bag.takeFirst(horse::isBodyArmorItem);
			if (!armor.isEmpty()) {
				horse.setBodyArmorItem(armor.split(1));
				if (!armor.isEmpty()) {
					bag.add(armor);
				}
				level.playSound(null, horse.blockPosition(), horse instanceof Llama ? SoundEvents.LLAMA_SWAG.value() : SoundEvents.HORSE_ARMOR,
					SoundSource.NEUTRAL, 0.8f, 1f);
				BuilderLevels.addXp(level, villager, 1, null);
			}
		} else if (chore == Chore.GROOM && COBBLEMON) {
			ItemStack treat = bag.takeFirst(RancherWork::isTreat);
			int amount = GROOM + (treat.isEmpty() ? 0 : TREAT);
			if (!treat.isEmpty()) {
				treat.shrink(1);
				if (!treat.isEmpty()) {
					bag.add(treat);
				}
				level.playSound(null, animal.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6f, 1.2f);
			}
			level.playSound(null, animal.blockPosition(), SoundEvents.BRUSH_GENERIC, SoundSource.NEUTRAL, 0.8f, 1f);
			if (PokemonPartners.EXTENSION.call(p -> p.befriend(level, animal, amount), false)) {
				ModAttachments.POKEMON_TENDED.set(villager, ModAttachments.POKEMON_TENDED.getOrElse(villager, 0) + 1);
				BuilderLevels.addXp(level, villager, 1, null);
			}
			GROOMED.put(animal.getUUID(), level.getGameTime());
		}
		chore = null;
		return false;
	}

	@Override
	protected void status(Villager villager, Task task, boolean noChest) {
		Component title = Component.translatable("message.aliveworkplace.rancher.title", ModAttachments.HORSES_TAMED.getOrElse(villager, 0));
		String state = noChest ? "no_chest" : task == Task.TEND && chore != null ? chore.name().toLowerCase() : task == Task.TEND ? "none"
			: task.name().toLowerCase();
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.rancher.state." + state)
			.withStyle(noChest ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}
}
