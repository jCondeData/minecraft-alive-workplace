package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Village;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A Tinkerer's shift at the Tinker's Bench. For the builders nearby: the redstone and iron parts they're waiting for
 * (pistons, rails, hoppers, lanterns, iron bars — whatever is in the item tag {@code aliveworkplace:tinkering}), made
 * with the crafting table's recipes and, when the iron isn't there yet, raw ore fired into ingots in the bench's little
 * forge first (a coal or charcoal for every {@link #SMELTS_PER_FUEL}). Between jobs: the village's iron golems, patched
 * up with iron ingots from the village's stores when they're badly hurt.
 */
public class TinkererWork extends CrafterWork {
	/** What a tinkerer makes for the builders (a data pack can add to it). */
	public static final TagKey<Item> TINKERING = TagKey.create(Registries.ITEM, AliveWorkplace.id("tinkering"));
	/** Ore fired per coal or charcoal (a blast furnace's rate). */
	static final int SMELTS_PER_FUEL = 8;
	/** A golem this hurt (share of its health) gets mended. */
	static final float MEND_BELOW = 0.75f;
	/** How far from the bench the tinkerer looks for golems. */
	public static int MEND_RADIUS = 32;
	/** Health one iron ingot gives back (as when a player does it). */
	static final float HEAL_PER_INGOT = 25f;
	private static final int MEND_LOOK_EVERY = 200;
	private static final int MEND_EVERY = 20;
	/** Golems a tinkerer is on its way to (so two don't go for the same one). */
	private static final Set<IronGolem> PATIENTS = Collections.newSetFromMap(new WeakHashMap<>());

	private final Walker mendWalker = new Walker(0.55f);
	@Nullable
	private IronGolem patient;
	private int mendLook;
	private int mendTimer;

	public TinkererWork() {
		super(Crafting.Kind.WORKSHOP, "tinkerer", "crafter", true);
	}

	public static boolean isTinkerer(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.TINKERER;
	}

	@Override
	protected boolean wants(Item item) {
		return item.builtInRegistryHolder().is(TINKERING);
	}

	/** Crafted from what's there if it can be; else with ore fired first (and the fuel for it). */
	@Nullable
	@Override
	protected Crafting.Plan planFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		Crafting.Plan crafted = Crafting.plan(level, Crafting.Kind.CRAFTING, item, count, usable);
		if (fits(crafted)) {
			return crafted;
		}
		Crafting.Plan plan = Crafting.plan(level, Crafting.Kind.WORKSHOP, item, count, usable);
		return plan == null ? null : withFuel(level, plan, usable);
	}

	/** {@code plan} with a coal or charcoal for every {@link #SMELTS_PER_FUEL} ore it fires; null if there's too little. */
	@Nullable
	static Crafting.Plan withFuel(ServerLevel level, Crafting.Plan plan, Map<Item, Long> usable) {
		return withFuel(level, plan, usable, Crafting.Kind.WORKSHOP, SMELTS_PER_FUEL);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		super.stop(level, villager, gameTime);
		letGo();
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos bench = Builders.benchPos(villager).orElse(null);
		if (bench == null) {
			return;
		}
		if (patient == null && !isBusy(villager) && ModAttachments.BUILDER_BAG.getOrCreate(villager).isEmpty() && --mendLook <= 0) {
			mendLook = MEND_LOOK_EVERY;
			patient = findPatient(level, bench);
			if (patient != null) {
				synchronized (PATIENTS) {
					PATIENTS.add(patient);
				}
				mendWalker.reset();
				mendTimer = 0;
			}
		}
		if (patient != null) {
			mend(level, villager, bench);
			return;
		}
		super.tick(level, villager, gameTime);
	}

	/** The most hurt iron golem near the bench that nobody's mending yet. */
	@Nullable
	static IronGolem findPatient(ServerLevel level, BlockPos bench) {
		List<IronGolem> golems = level.getEntitiesOfClass(IronGolem.class, new AABB(bench).inflate(MEND_RADIUS),
			g -> g.isAlive() && g.getHealth() < g.getMaxHealth() * MEND_BELOW);
		synchronized (PATIENTS) {
			golems.removeIf(PATIENTS::contains);
		}
		return golems.stream().min(Comparator.comparingDouble(g -> g.getHealth() / g.getMaxHealth())).orElse(null);
	}

	/** Where the tinkerer may take iron from: the chests by the bench, the storehouse's and the smelters'. */
	static List<BlockPos> ironSources(ServerLevel level, Villager villager, BlockPos bench) {
		List<BlockPos> sources = new ArrayList<>(SupplyContainers.find(level, bench, null));
		if (Village.RADIUS > 0) {
			for (Village.Stash stash : Village.stashes(level, villager, bench, null)) {
				VillagerProfession job = stash.job();
				if (job == ModVillagers.PORTER || job == VillagerProfession.ARMORER) {
					sources.addAll(stash.chests());
				}
			}
		}
		return sources;
	}

	/** Fetches iron ingots, walks to the golem and patches it up, an ingot at a time. */
	private void mend(ServerLevel level, Villager villager, BlockPos bench) {
		IronGolem golem = patient;
		if (golem == null || !golem.isAlive() || golem.getHealth() >= golem.getMaxHealth() || !golem.blockPosition().closerThan(bench, MEND_RADIUS + 8)) {
			letGo();
			return;
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		if (bag.count(Items.IRON_INGOT) == 0) {
			status(villager, "fetching", golem);
			List<BlockPos> sources = ironSources(level, villager, bench);
			BlockPos chest = SupplyContainers.firstMatching(level, sources, s -> s.is(Items.IRON_INGOT) && s.getComponentsPatch().isEmpty());
			if (chest == null) {
				letGo(); // nothing to mend it with: try again later
				return;
			}
			if (!mendWalker.walkTo(level, villager, chest, 3.0)) {
				return;
			}
			int wanted = Math.max(1, Math.min(4, (int) Math.ceil((golem.getMaxHealth() - golem.getHealth()) / HEAL_PER_INGOT)));
			int got = SupplyContainers.extract(level, List.of(chest), Items.IRON_INGOT, wanted);
			bag.addAll(Items.IRON_INGOT, got);
			villager.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
			mendWalker.reset();
			return;
		}
		status(villager, "mending", golem);
		if (villager.distanceTo(golem) > 2.5) {
			if (villager.distanceTo(golem) < 8) {
				golem.getNavigation().stop(); // hold still a moment
			}
			mendWalker.walkTo(level, villager, golem.blockPosition(), 2.5);
			mendTimer = 0;
			return;
		}
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(golem, true));
		golem.getNavigation().stop();
		if (++mendTimer < MEND_EVERY) {
			return;
		}
		mendTimer = 0;
		bag.remove(Items.IRON_INGOT, 1);
		golem.heal(HEAL_PER_INGOT);
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, golem.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 1f, 1f + (level.random.nextFloat() - level.random.nextFloat()) * 0.2f);
		ModAttachments.GOLEM_REPAIRS.set(villager, ModAttachments.GOLEM_REPAIRS.getOrElse(villager, 0) + 1);
		if (golem.getHealth() >= golem.getMaxHealth()) {
			letGo(); // what's left of the ingots goes back to the bench's chests
		}
	}

	private void letGo() {
		if (patient != null) {
			synchronized (PATIENTS) {
				PATIENTS.remove(patient);
			}
		}
		patient = null;
		mendWalker.reset();
	}

	private static void status(Villager villager, String state, IronGolem golem) {
		WorkerStatus.set(villager, Component.translatable("message.aliveworkplace.tinkerer.title", ModAttachments.ITEMS_CRAFTED.getOrElse(villager, 0)),
			golem.getHealth() / golem.getMaxHealth(), Component.translatable("message.aliveworkplace.tinkerer.state." + state).withStyle(ChatFormatting.GRAY));
	}
}
