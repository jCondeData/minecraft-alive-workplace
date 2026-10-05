package io.github.jcondedata.aliveworkplace.nether;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.explore.Explorers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Netherworkers: a villager at a Nether Brazier who goes through a Nether portal near it on expeditions and comes back
 * with the Nether's goods. The trip itself isn't walked: the netherworker steps into the portal and is away (out of
 * sight, taking no harm, the brain paused) for {@link #TRIP_TICKS}, then steps back out with what they found — rolled
 * from loot tables by what they took along ({@link Kit}). The villager never really changes dimension (a netherworker's
 * portal cooldown is kept up, so vanilla's portals leave them alone too). Adapted in spirit from MineColonies' Nether
 * Worker; the code is ours.
 */
public final class Netherworkers {
	/** How long an expedition lasts at Novice (shorter with levels, partners, a good mood...). */
	public static int TRIP_TICKS = 6000;
	/** How far from the brazier the portal may be. */
	public static int PORTAL_RANGE = 32;
	/** Rations eaten on a trip. */
	public static final int RATIONS = 3;
	/** The chance (in 100) of coming back hurt, without armor and with it. */
	static final int HURT_UNARMORED = 50;
	static final int HURT_ARMORED = 15;
	/** Durability a tool loses on a trip (at most). */
	static final int WEAR = 24;

	/** What turns up anywhere in the Nether: netherrack, soul sand, basalt, glowstone, wart... */
	public static final ResourceKey<LootTable> WASTES = table("nether/wastes");
	/** With a pickaxe: quartz, gold, magma. */
	public static final ResourceKey<LootTable> MINING = table("nether/mining");
	/** With an axe: crimson and warped stems, fungi, shroomlights. */
	public static final ResourceKey<LootTable> FOREST = table("nether/forest");
	/** With a sword and armor: a fortress's blaze rods, nether bricks, bones and coal. */
	public static final ResourceKey<LootTable> FORTRESS = table("nether/fortress");
	/** With a diamond (or netherite) pickaxe: now and then ancient debris. */
	public static final ResourceKey<LootTable> DEBRIS = table("nether/debris");
	/** Fire and Dusk Stones, Magmarizers... (the table only loads with Cobblemon). */
	public static final ResourceKey<LootTable> COBBLEMON = table("nether/cobblemon");

	private static ResourceKey<LootTable> table(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, AliveWorkplace.id(path));
	}

	/** What a netherworker takes along (bits). */
	public static final class Kit {
		public static final int PICKAXE = 1;
		public static final int AXE = 2;
		public static final int SWORD = 4;
		public static final int ARMOR = 8;
		public static final int FIRE_RESISTANCE = 16;
		public static final int DIAMOND_PICKAXE = 32;

		private Kit() {
		}
	}

	/** An expedition under way: when they're back, the portal they went through, what they took ({@link Kit}). */
	public record Trip(long startedAt, long returnAt, BlockPos portal, int kit) {
		public static final Codec<Trip> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("started").forGetter(Trip::startedAt),
			Codec.LONG.fieldOf("returns").forGetter(Trip::returnAt),
			BlockPos.CODEC.fieldOf("portal").forGetter(Trip::portal),
			Codec.INT.fieldOf("kit").forGetter(Trip::kit)
		).apply(i, Trip::new));
	}

	public static boolean isNetherworker(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.NETHERWORKER;
	}

	/** Whether {@code villager} is away in the Nether. */
	public static boolean isAway(Villager villager) {
		return ModAttachments.NETHER_TRIP.has(villager);
	}

	/** Damage an away netherworker doesn't take (anything but /kill and the void). */
	public static boolean shields(Villager villager, DamageSource source) {
		return isAway(villager) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	/** The nearest Nether portal within {@link #PORTAL_RANGE} of {@code brazier}, or null. */
	@Nullable
	public static BlockPos portalNear(ServerLevel level, BlockPos brazier) {
		return level.getPoiManager().findClosest(h -> h.is(PoiTypes.NETHER_PORTAL), brazier, PORTAL_RANGE, PoiManager.Occupancy.ANY).orElse(null);
	}

	/** Food worth packing (as the explorer's). */
	public static boolean isRation(ItemStack stack) {
		return Explorers.isFood(stack);
	}

	public static boolean isPickaxe(ItemStack stack) {
		return stack.is(ItemTags.PICKAXES) && usable(stack);
	}

	public static boolean isAxe(ItemStack stack) {
		return stack.is(ItemTags.AXES) && usable(stack);
	}

	public static boolean isSword(ItemStack stack) {
		return stack.is(ItemTags.SWORDS) && usable(stack);
	}

	public static boolean isArmor(ItemStack stack) {
		return stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.CHEST && usable(stack);
	}

	public static boolean isFireResistance(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return stack.is(Items.POTION) && contents != null && contents.hasEffects()
			&& java.util.stream.StreamSupport.stream(contents.getAllEffects().spliterator(), false).anyMatch(e -> e.is(MobEffects.FIRE_RESISTANCE));
	}

	/** Anything a netherworker takes along besides food. */
	public static boolean isGear(ItemStack stack) {
		return isPickaxe(stack) || isAxe(stack) || isSword(stack) || isArmor(stack) || isFireResistance(stack);
	}

	private static boolean usable(ItemStack stack) {
		return !stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > WEAR;
	}

	/** The kit in {@code bag}. */
	public static int kitOf(BuilderBag bag) {
		int kit = 0;
		for (ItemStack stack : bag.stacks()) {
			if (isPickaxe(stack)) {
				kit |= Kit.PICKAXE;
				if (stack.is(Items.DIAMOND_PICKAXE) || stack.is(Items.NETHERITE_PICKAXE)) {
					kit |= Kit.DIAMOND_PICKAXE;
				}
			} else if (isAxe(stack)) {
				kit |= Kit.AXE;
			} else if (isSword(stack)) {
				kit |= Kit.SWORD;
			} else if (isArmor(stack)) {
				kit |= Kit.ARMOR;
			} else if (isFireResistance(stack)) {
				kit |= Kit.FIRE_RESISTANCE;
			}
		}
		return kit;
	}

	/**
	 * {@code villager} steps into the portal at {@code portal}: the rations in the bag are eaten on the way, they're out of
	 * sight until the trip's over. False (nothing happens) without enough rations.
	 */
	public static boolean setOut(ServerLevel level, Villager villager, BlockPos portal) {
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		int rations = 0;
		for (ItemStack stack : bag.stacks()) {
			if (isRation(stack)) {
				rations += stack.getCount();
			}
		}
		if (rations < RATIONS) {
			return false;
		}
		int left = RATIONS;
		while (left > 0) {
			ItemStack food = bag.takeFirst(Netherworkers::isRation);
			int eaten = Math.min(left, food.getCount());
			food.shrink(eaten);
			left -= eaten;
			if (!food.isEmpty()) {
				bag.add(food);
			}
		}
		long now = level.getGameTime();
		int ticks = tripTicks(villager);
		ModAttachments.NETHER_TRIP.set(villager, new Trip(now, now + ticks, portal.immutable(), kitOf(bag)));
		puff(level, villager);
		// A Fire or Dark partner walks them to the portal, flames at its feet (ROADMAP 28.6).
		io.github.jcondedata.aliveworkplace.work.PartnerShows.cue(villager, "depart", portal);
		level.playSound(null, portal, SoundEvents.PORTAL_TRIGGER, SoundSource.NEUTRAL, 0.4f, 1.2f);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getNavigation().stop();
		park(villager, portal);
		return true;
	}

	/** Ticks a trip takes this netherworker: the level's share at their pace, Expeditions included (ROADMAP 30.2). */
	public static int tripTicks(Villager villager) {
		return Math.max(20, BuilderLevels.delay(TRIP_TICKS, villager));
	}

	/**
	 * Every server tick of a netherworker (before the brain): keeps vanilla's portals off them, and while they're away
	 * keeps them parked and out of sight, and brings them back when the trip's over. True while away (the brain waits).
	 */
	public static boolean tick(Villager villager) {
		if (villager.getVillagerData().getProfession() != ModVillagers.NETHERWORKER && !isAway(villager)) {
			return false;
		}
		villager.setPortalCooldown(); // they go through portals our way only
		Trip trip = ModAttachments.NETHER_TRIP.get(villager);
		if (trip == null || !(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		if (level.getGameTime() >= trip.returnAt()) {
			comeBack(level, villager, trip);
			return false;
		}
		park(villager, trip.portal());
		WorkerStatus.set(villager, title(villager), (float) (level.getGameTime() - trip.startedAt()) / Math.max(1, trip.returnAt() - trip.startedAt()),
			Component.translatable("message.aliveworkplace.netherworker.state.away", (trip.returnAt() - level.getGameTime() + 19) / 20)
				.withStyle(ChatFormatting.GRAY));
		return true;
	}

	/** Out of sight in the portal, not moving. */
	private static void park(Villager villager, BlockPos portal) {
		villager.setInvisible(true);
		villager.setSilent(true);
		villager.setDeltaMovement(Vec3.ZERO);
		Vec3 spot = Vec3.atBottomCenterOf(portal);
		if (villager.position().distanceToSqr(spot) > 0.01) {
			villager.teleportTo(spot.x, spot.y, spot.z);
		}
	}

	/** Back from the Nether: out of the portal with what they found, their tools worn, now and then hurt. */
	static void comeBack(ServerLevel level, Villager villager, Trip trip) {
		ModAttachments.NETHER_TRIP.remove(villager);
		villager.setInvisible(false);
		villager.setSilent(false);
		BlockPos out = Walker.standingSpot(level, trip.portal(), trip.portal(), 3.0);
		if (out != null) {
			villager.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
		}
		puff(level, villager);
		level.playSound(null, villager.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.NEUTRAL, 0.15f, 1.4f);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		wear(level, villager, bag);
		for (ItemStack found : finds(level, villager, trip.kit())) {
			ItemStack rest = bag.add(found);
			if (!rest.isEmpty()) {
				Block.popResource(level, villager.blockPosition(), rest);
			}
		}
		boolean armored = (trip.kit() & Kit.ARMOR) != 0;
		int hurt = armored ? HURT_ARMORED : HURT_UNARMORED;
		if ((trip.kit() & Kit.FIRE_RESISTANCE) != 0) {
			hurt /= 2;
		}
		if (level.random.nextInt(100) < hurt) {
			villager.setHealth(Math.max(1f, villager.getHealth() * 0.4f));
			level.sendParticles(ParticleTypes.SMOKE, villager.getX(), villager.getY() + 1, villager.getZ(), 8, 0.3, 0.4, 0.3, 0.01);
		}
		ModAttachments.NETHER_TRIPS.set(villager, ModAttachments.NETHER_TRIPS.getOrElse(villager, 0) + 1);
		BuilderLevels.addXp(level, villager, 4, null);
	}

	/** Tools and armor lose some durability; a fire resistance potion is drunk (the bottle comes back). */
	private static void wear(ServerLevel level, Villager villager, BuilderBag bag) {
		List<ItemStack> kept = new ArrayList<>();
		for (ItemStack stack : bag.takeAll()) {
			if (isFireResistance(stack)) {
				stack.shrink(1);
				kept.add(new ItemStack(Items.GLASS_BOTTLE));
				if (!stack.isEmpty()) {
					kept.add(stack);
				}
				continue;
			}
			if (stack.isDamageableItem()) {
				// The Miners' Guild: half as much (30.18).
				int worn = io.github.jcondedata.aliveworkplace.hall.Guilds.wear(villager, WEAR / 2 + level.random.nextInt(WEAR / 2 + 1));
				stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + worn));
			}
			kept.add(stack);
		}
		for (ItemStack stack : kept) {
			bag.add(stack);
		}
	}

	/** What an expedition with {@code kit} brings back: the wastes' finds, and the mines', forests' and fortresses' with the right kit. */
	public static List<ItemStack> finds(ServerLevel level, Villager villager, int kit) {
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, villager.position())
			.withOptionalParameter(LootContextParams.THIS_ENTITY, villager)
			.withLuck(BuilderLevels.level(villager) - 1 + io.github.jcondedata.aliveworkplace.legend.Gifted.lootLuck(villager)
				+ io.github.jcondedata.aliveworkplace.research.TreeEffects.lootLuck(villager))
			.create(LootContextParamSets.CHEST);
		List<ItemStack> out = new ArrayList<>(roll(level, WASTES, params));
		if ((kit & Kit.FIRE_RESISTANCE) != 0) {
			out.addAll(roll(level, WASTES, params)); // further out, safely
		}
		if ((kit & Kit.PICKAXE) != 0) {
			out.addAll(roll(level, MINING, params));
		}
		if ((kit & Kit.DIAMOND_PICKAXE) != 0) {
			out.addAll(roll(level, DEBRIS, params));
		}
		if ((kit & Kit.AXE) != 0) {
			out.addAll(roll(level, FOREST, params));
		}
		if ((kit & Kit.SWORD) != 0 && (kit & Kit.ARMOR) != 0) {
			out.addAll(roll(level, FORTRESS, params));
		}
		out.addAll(roll(level, COBBLEMON, params)); // an empty table without Cobblemon
		out.removeIf(ItemStack::isEmpty);
		return out;
	}

	private static List<ItemStack> roll(ServerLevel level, ResourceKey<LootTable> key, LootParams params) {
		return level.getServer().reloadableRegistries().getLootTable(key).getRandomItems(params);
	}

	private static void puff(ServerLevel level, Villager villager) {
		level.sendParticles(ParticleTypes.PORTAL, villager.getX(), villager.getY() + 1, villager.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
	}

	static Component title(Villager villager) {
		return Component.translatable("message.aliveworkplace.netherworker.title", ModAttachments.NETHER_TRIPS.getOrElse(villager, 0));
	}

	private Netherworkers() {
	}
}
