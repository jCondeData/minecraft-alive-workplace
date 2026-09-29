package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Names;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.people.Traits.Trait;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

/** Villagers as people: names in a village with a hall, and traits. */
public class PeopleGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** A UUID whose owner has exactly {@code traits}. */
	static UUID withTraits(Trait... traits) {
		List<Trait> want = List.of(traits);
		RandomSource random = RandomSource.create(want.hashCode());
		for (int i = 0; i < 100000; i++) {
			UUID id = new UUID(random.nextLong(), random.nextLong());
			if (Traits.of(id).equals(want)) {
				return id;
			}
		}
		throw new IllegalStateException("nobody is " + want);
	}

	/** A villager (not in the world) with exactly {@code traits}. */
	private static Villager villager(GameTestHelper helper, Trait... traits) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.setUUID(withTraits(traits));
		v.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5, 2, 5)));
		return v;
	}

	private static void traitsOn(GameTestHelper helper) {
		boolean was = Traits.ENABLED;
		Traits.ENABLED = true;
		Leftovers.after(helper, () -> Traits.ENABLED = was);
	}

	/** Everyone has one trait or two; two never clash (diligent and lazy, glutton and frugal); the same villager always has the same. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyoneHasTraitsThatNeverClash(GameTestHelper helper) {
		RandomSource random = RandomSource.create(7);
		Set<Trait> seen = EnumSet.noneOf(Trait.class);
		int two = 0;
		for (int i = 0; i < 2000; i++) {
			UUID id = new UUID(random.nextLong(), random.nextLong());
			List<Trait> traits = Traits.of(id);
			helper.assertTrue(traits.size() == 1 || traits.size() == 2, "traits: " + traits);
			helper.assertFalse(traits.contains(Trait.DILIGENT) && traits.contains(Trait.LAZY), "diligent and lazy");
			helper.assertFalse(traits.contains(Trait.GLUTTON) && traits.contains(Trait.FRUGAL), "glutton and frugal");
			seen.addAll(traits);
			two += traits.size() == 2 ? 1 : 0;
			Traits.forget();
			helper.assertTrue(Traits.of(id).equals(traits), "traits changed");
		}
		helper.assertTrue(seen.equals(EnumSet.allOf(Trait.class)), "never seen: " + EnumSet.complementOf(EnumSet.copyOf(seen)));
		helper.assertTrue(two > 600 && two < 1200, "two traits: " + two + " of 2000");
		helper.succeed();
	}

	/** Diligent villagers work faster and lazy ones slower; nimble ones walk faster, clever ones learn faster, strong ones hit harder. */
	@GameTest(template = AREA)
	public void traitsChangeHowVillagersWork(GameTestHelper helper) {
		traitsOn(helper);
		Villager plain = villager(helper, Trait.CHEERFUL);
		Villager diligent = villager(helper, Trait.DILIGENT);
		Villager lazy = villager(helper, Trait.LAZY);
		int base = BuilderLevels.delay(100, plain);
		helper.assertTrue(BuilderLevels.delay(100, diligent) < base && BuilderLevels.delay(100, lazy) > base,
			"delays: diligent " + BuilderLevels.delay(100, diligent) + ", plain " + base + ", lazy " + BuilderLevels.delay(100, lazy));

		Villager nimble = villager(helper, Trait.NIMBLE);
		Walker.requestWalk(nimble, helper.absolutePos(new BlockPos(9, 2, 9)), 0.5f, 1, 0);
		Walker.requestWalk(plain, helper.absolutePos(new BlockPos(9, 2, 9)), 0.5f, 1, 0);
		float fast = nimble.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow().getSpeedModifier();
		float usual = plain.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow().getSpeedModifier();
		helper.assertTrue(Math.abs(fast - 0.575f) < 0.001f && Math.abs(usual - 0.5f) < 0.001f, "speeds: " + fast + ", " + usual);

		Villager clever = villager(helper, Trait.CLEVER);
		BuilderLevels.addXp(helper.getLevel(), clever, 4, null);
		BuilderLevels.addXp(helper.getLevel(), plain, 4, null);
		helper.assertTrue(clever.getVillagerXp() == 5 && plain.getVillagerXp() == 4, "xp: clever " + clever.getVillagerXp() + ", plain " + plain.getVillagerXp());

		Villager strong = villager(helper, Trait.STRONG);
		helper.assertTrue(Math.abs(Guards.levelBonus(strong) / Guards.levelBonus(plain) - 1.15f) < 0.001f, "strength");

		// Traits off: everyone the same.
		Traits.ENABLED = false;
		helper.assertTrue(BuilderLevels.delay(100, diligent) == base && Traits.of(diligent).isEmpty(), "traits off");
		Traits.ENABLED = true;
		helper.succeed();
	}

	/** Gluttons are hungry again after half a day, the frugal only after two. */
	@GameTest(template = AREA)
	public void gluttonsEatMoreOften(GameTestHelper helper) {
		traitsOn(helper);
		long now = 100000;
		Villager glutton = villager(helper, Trait.GLUTTON);
		Villager frugal = villager(helper, Trait.FRUGAL);
		Villager plain = villager(helper, Trait.NIMBLE);
		for (Villager v : List.of(glutton, frugal, plain)) {
			v.setAttached(ModAttachments.LAST_MEAL, now - 13000);
		}
		helper.assertTrue(VillageNeeds.isHungry(glutton, now) && !VillageNeeds.isHungry(plain, now) && !VillageNeeds.isHungry(frugal, now), "after half a day");
		for (Villager v : List.of(glutton, frugal, plain)) {
			v.setAttached(ModAttachments.LAST_MEAL, now - 30000);
		}
		helper.assertTrue(VillageNeeds.isHungry(plain, now) && !VillageNeeds.isHungry(frugal, now), "after a day and a quarter");
		helper.succeed();
	}

	/** In a village with a hall everyone gets a name of their own (a Name Tag's name stays); a cheerful villager brightens it. */
	@GameTest(template = AREA, timeoutTicks = 100, batch = "villagersInAHallVillageGetNames")
	public void villagersInAHallVillageGetNames(GameTestHelper helper) {
		Leftovers.clear(helper);
		traitsOn(helper);
		boolean names = Names.ENABLED;
		int radius = VillageHalls.RADIUS;
		Names.ENABLED = true;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			Names.ENABLED = names;
			VillageHalls.RADIUS = radius;
		});
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> villagers = new java.util.ArrayList<>();
		for (int i = 0; i < 4; i++) {
			villagers.add(helper.spawn(EntityType.VILLAGER, new BlockPos(4 + i * 3, 2, 5)));
		}
		villagers.get(3).setCustomName(Component.literal("Bob"));
		Villager baby = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 14));
		baby.setAge(-24000);
		VillageNeeds.check(level, hall);
		Set<String> seen = new HashSet<>();
		for (Villager v : List.of(villagers.get(0), villagers.get(1), villagers.get(2), baby)) {
			helper.assertTrue(v.hasCustomName(), "no name");
			helper.assertTrue(seen.add(v.getCustomName().getString()), "two villagers called " + v.getCustomName().getString());
		}
		helper.assertTrue(villagers.get(3).getCustomName().getString().equals("Bob"), "the name tag's name went");
		String first = villagers.get(0).getCustomName().getString();
		VillageNeeds.check(level, hall);
		helper.assertTrue(villagers.get(0).getCustomName().getString().equals(first), "renamed");

		// Cheer: the same village with a cheerful grown-up instead of a lazy one is 1% happier.
		Villager dour = EntityType.VILLAGER.create(level);
		dour.setUUID(withTraits(Trait.LAZY));
		dour.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(8.5, 2, 8.5)));
		level.addFreshEntity(dour);
		float without = VillageNeeds.count(level, hall).wellbeing();
		dour.discard();
		Villager cheerful = EntityType.VILLAGER.create(level);
		cheerful.setUUID(withTraits(Trait.CHEERFUL));
		cheerful.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(8.5, 2, 8.5)));
		level.addFreshEntity(cheerful);
		float with = VillageNeeds.count(level, hall).wellbeing();
		helper.assertTrue(Math.abs(with - without - 0.01f) < 0.001f, "wellbeing " + without + " -> " + with);
		helper.succeed();
	}

	/** The ill work at half pace and walk slowly; they get well by themselves after a few days. Hunger and no bed make it likelier. */
	@GameTest(template = AREA)
	public void illVillagersWorkSlowlyAndGetWell(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		int well = BuilderLevels.delay(100, villager);
		float chance = io.github.jcondedata.aliveworkplace.people.Sickness.dailyChance(level, villager);
		villager.setAttached(ModAttachments.LAST_MEAL, level.getGameTime() - 2 * VillageNeeds.DAY);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Sickness.dailyChance(level, villager) > chance, "hunger doesn't matter");
		io.github.jcondedata.aliveworkplace.people.Sickness.fallIll(level, villager);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Sickness.isIll(villager), "not ill");
		helper.assertTrue(BuilderLevels.delay(100, villager) == 2 * well, "ill pace: " + BuilderLevels.delay(100, villager) + " vs " + well);
		helper.assertTrue(villager.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "not slowed");
		io.github.jcondedata.aliveworkplace.people.Sickness.round(level, villager, 40);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Sickness.isIll(villager), "got well at once");
		villager.setAttached(ModAttachments.ILL_SINCE, level.getGameTime() - io.github.jcondedata.aliveworkplace.people.Sickness.RECOVERY);
		io.github.jcondedata.aliveworkplace.people.Sickness.round(level, villager, 40);
		helper.assertFalse(io.github.jcondedata.aliveworkplace.people.Sickness.isIll(villager), "still ill after the illness ran its course");
		helper.assertTrue(BuilderLevels.delay(100, villager) == well, "still slow");
		helper.succeed();
	}

	/** A nurse cures the ill with a remedy from her chest (the empty bottle goes back), or asks for one. */
	@GameTest(template = AREA, timeoutTicks = 400)
	public void aNurseCuresTheIll(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager nurse = NurseGameTests.nurse(helper);
		helper.setBlock(new BlockPos(2, 2, 4), net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		Villager patient = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		io.github.jcondedata.aliveworkplace.people.Sickness.fallIll(level, patient);
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Sickness.isIll(patient), "cured without a remedy");
			boolean asked = io.github.jcondedata.aliveworkplace.work.Requests.of(level, nurse).stream()
				.anyMatch(r -> r.accepts().test(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE)));
			helper.assertTrue(asked, "the nurse didn't ask for a remedy: " + io.github.jcondedata.aliveworkplace.work.Requests.of(level, nurse));
			chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE));
		});
		helper.runAfterDelay(125, () -> helper.succeedWhen(() -> {
			helper.assertFalse(io.github.jcondedata.aliveworkplace.people.Sickness.isIll(patient), "still ill");
			helper.assertTrue(chest.countItem(net.minecraft.world.item.Items.GLASS_BOTTLE) == 1 && chest.countItem(net.minecraft.world.item.Items.HONEY_BOTTLE) == 0,
				"chest: " + chest.countItem(net.minecraft.world.item.Items.HONEY_BOTTLE) + " honey, " + chest.countItem(net.minecraft.world.item.Items.GLASS_BOTTLE) + " bottles");
			helper.assertTrue(nurse.getAttachedOrElse(ModAttachments.VILLAGERS_CURED, 0) == 1, "cured count");
		}));
	}
}
