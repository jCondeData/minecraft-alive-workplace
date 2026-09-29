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
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
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
	//$ gametest AREA
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
	//$ gametest AREA
	@GameTest(template = AREA)
	public void gluttonsEatMoreOften(GameTestHelper helper) {
		traitsOn(helper);
		long now = 100000;
		Villager glutton = villager(helper, Trait.GLUTTON);
		Villager frugal = villager(helper, Trait.FRUGAL);
		Villager plain = villager(helper, Trait.NIMBLE);
		for (Villager v : List.of(glutton, frugal, plain)) {
			ModAttachments.LAST_MEAL.set(v, now - 13000);
		}
		helper.assertTrue(VillageNeeds.isHungry(glutton, now) && !VillageNeeds.isHungry(plain, now) && !VillageNeeds.isHungry(frugal, now), "after half a day");
		for (Villager v : List.of(glutton, frugal, plain)) {
			ModAttachments.LAST_MEAL.set(v, now - 30000);
		}
		helper.assertTrue(VillageNeeds.isHungry(plain, now) && !VillageNeeds.isHungry(frugal, now), "after a day and a quarter");
		helper.succeed();
	}

	/** In a village with a hall everyone gets a name of their own (a Name Tag's name stays); a cheerful villager brightens it. */
	//$ gametest_ticks_batch AREA '100' '"villagersInAHallVillageGetNames"'
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
	//$ gametest AREA
	@GameTest(template = AREA)
	public void illVillagersWorkSlowlyAndGetWell(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		int well = BuilderLevels.delay(100, villager);
		float chance = io.github.jcondedata.aliveworkplace.people.Sickness.dailyChance(level, villager);
		ModAttachments.LAST_MEAL.set(villager, level.getGameTime() - 2 * VillageNeeds.DAY);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Sickness.dailyChance(level, villager) > chance, "hunger doesn't matter");
		io.github.jcondedata.aliveworkplace.people.Sickness.fallIll(level, villager);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Sickness.isIll(villager), "not ill");
		helper.assertTrue(BuilderLevels.delay(100, villager) == 2 * well, "ill pace: " + BuilderLevels.delay(100, villager) + " vs " + well);
		helper.assertTrue(villager.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "not slowed");
		io.github.jcondedata.aliveworkplace.people.Sickness.round(level, villager, 40);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Sickness.isIll(villager), "got well at once");
		ModAttachments.ILL_SINCE.set(villager, level.getGameTime() - io.github.jcondedata.aliveworkplace.people.Sickness.RECOVERY);
		io.github.jcondedata.aliveworkplace.people.Sickness.round(level, villager, 40);
		helper.assertFalse(io.github.jcondedata.aliveworkplace.people.Sickness.isIll(villager), "still ill after the illness ran its course");
		helper.assertTrue(BuilderLevels.delay(100, villager) == well, "still slow");
		helper.succeed();
	}

	/** A nurse cures the ill with a remedy from her chest (the empty bottle goes back), or asks for one. */
	//$ gametest_ticks AREA '400'
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
			helper.assertTrue(ModAttachments.VILLAGERS_CURED.getOrElse(nurse, 0) == 1, "cured count");
		}));
	}

	/** A child remembers its parents; grown up and without a job, they take up a parent's trade at a free workstation. */
	//$ gametest_ticks_batch AREA '100' '"familiesKeepTheTrade"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "familiesKeepTheTrade")
	public void familiesKeepTheTrade(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(17, 2, 3), ModBlocks.BUILDERS_BENCH);
		BlockPos hall = helper.absolutePos(HALL);
		Villager mother = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		Villager father = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 4));
		father.setVillagerData(father.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NONE));
		helper.runAfterDelay(2, () -> {
			io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, mother, helper.absolutePos(new BlockPos(3, 2, 3)),
				io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDERS_BENCH_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER);
			mother.setCustomName(Component.literal("Wren"));
			father.setCustomName(Component.literal("Bram"));
			// The vanilla way: the baby knows its parents.
			Villager baby = mother.getBreedOffspring(level, father);
			helper.assertTrue(baby != null && io.github.jcondedata.aliveworkplace.people.Families.parents(baby) != null, "the baby doesn't know its parents");
			// A grown child without a job.
			Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 9));
			child.setVillagerData(child.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NONE));
			io.github.jcondedata.aliveworkplace.people.Families.born(child, mother, father);
			VillageNeeds.check(level, hall);
			var parents = io.github.jcondedata.aliveworkplace.people.Families.parents(child);
			helper.assertTrue(parents.grownUp() && parents.mother().getString().equals("Wren"), "parents: " + parents);
			helper.assertTrue(child.getVillagerData().getProfession() == io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER,
				"the child is a " + child.getVillagerData().getProfession());
			var chronicle = ((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall)).chronicle();
			helper.assertTrue(chronicle.size() >= 2, "chronicle: " + chronicle);
			helper.succeed();
		});
	}

	/**
	 * Couples: two villagers who aren't family start courting (never a mother and her son), marry at a wedding that puts
	 * the whole village in a good mood, and when one dies the other mourns.
	 */
	//$ gametest_ticks_batch AREA '100' '"villagersMarry"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "villagersMarry")
	public void villagersCourtMarryAndMourn(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		boolean moods = io.github.jcondedata.aliveworkplace.people.Moods.ENABLED;
		VillageHalls.RADIUS = 16;
		io.github.jcondedata.aliveworkplace.people.Moods.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			io.github.jcondedata.aliveworkplace.people.Moods.ENABLED = moods;
			io.github.jcondedata.aliveworkplace.people.Moods.forget();
		});
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager mother = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		Villager son = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 4));
		Villager neighbour = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 14));
		mother.setCustomName(net.minecraft.network.chat.Component.literal("Ada"));
		son.setCustomName(net.minecraft.network.chat.Component.literal("Ben"));
		neighbour.setCustomName(net.minecraft.network.chat.Component.literal("Cora"));
		ModAttachments.PARENTS.set(son, new io.github.jcondedata.aliveworkplace.people.Families.Parents(
			net.minecraft.network.chat.Component.literal("Ada"), net.minecraft.network.chat.Component.literal("Dan"), "", "", true));
		helper.runAfterDelay(2, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			var couple = io.github.jcondedata.aliveworkplace.people.Couples.court(level, hall, java.util.List.of(mother, son, neighbour));
			helper.assertTrue(couple.size() == 2 && !(couple.contains(mother) && couple.contains(son)), "courting: " + couple);
			Villager a = couple.get(0);
			Villager b = couple.get(1);
			var partner = io.github.jcondedata.aliveworkplace.people.Couples.partner(a);
			helper.assertTrue(partner != null && partner.id().equals(b.getUUID()) && !partner.married(), "a's partner: " + partner);
			io.github.jcondedata.aliveworkplace.people.Couples.wed(level, hall, a, b);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Couples.partner(b).married(), "not married");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.Festivals.enjoyedLately(level, neighbour == a || neighbour == b ? mother : neighbour),
				"the village didn't celebrate");
			var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(entity.chronicle().stream().filter(e -> e.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.WEDDING).count() == 2,
				"the chronicle: " + entity.chronicle());
			var glad = io.github.jcondedata.aliveworkplace.people.Moods.work(level, b);
			helper.assertTrue(glad.good().stream().anyMatch(c -> c.getString().equals("married")), "b's mood: " + glad.good());
			io.github.jcondedata.aliveworkplace.people.Couples.onDeath(level, a);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Couples.partner(b) == null
				&& io.github.jcondedata.aliveworkplace.people.Couples.isMourning(level, b), "b isn't mourning");
			var sad = io.github.jcondedata.aliveworkplace.people.Moods.work(level, b);
			helper.assertTrue(sad.bad().stream().anyMatch(c -> c.getString().equals("mourning")), "b's mood: " + sad.bad());
			helper.succeed();
		});
	}

	/** Villagers talk about their day: a hungry one about food, anyone about bandits camped nearby, and hello. */
	//$ gametest_ticks_batch AREA '100' '"villagersChatter"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "villagersChatter")
	public void villagersChatterAboutTheirDay(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		boolean moods = io.github.jcondedata.aliveworkplace.people.Moods.ENABLED;
		VillageHalls.RADIUS = 16;
		io.github.jcondedata.aliveworkplace.people.Moods.ENABLED = true;
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			io.github.jcondedata.aliveworkplace.people.Moods.ENABLED = moods;
			io.github.jcondedata.aliveworkplace.people.Moods.forget();
			io.github.jcondedata.aliveworkplace.guard.BanditCamps.forget(level);
		});
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL); // in a corner: the camp goes in the far one
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.runAfterDelay(2, () -> {
			BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1));
			ModAttachments.LAST_MEAL.set(villager, level.getGameTime() - 2 * VillageNeeds.DAY);
			var topics = io.github.jcondedata.aliveworkplace.people.Chatter.topics(level, villager, hall);
			helper.assertTrue(topics.contains("hungry") && topics.contains("hello") && !topics.contains("bandits"), "topics: " + topics);
			var line = io.github.jcondedata.aliveworkplace.people.Chatter.line(level, villager, hall, player);
			helper.assertTrue(line != null && line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
				&& t.getKey().startsWith("chatter.aliveworkplace."), "line: " + line);
			io.github.jcondedata.aliveworkplace.guard.BanditCamps.found(level, hall, helper.absolutePos(new BlockPos(13, 1, 13)));
			io.github.jcondedata.aliveworkplace.people.Moods.forget();
			helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Chatter.topics(level, villager, hall).contains("bandits"), "no talk of bandits");
			level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(hall).inflate(40),
				m -> m.getTags().contains(io.github.jcondedata.aliveworkplace.guard.BanditCamps.TAG)).forEach(m -> m.discard());
			helper.succeed();
		});
	}

	/** A villager's mood follows their day; the unhappy work slower, the happy faster. */
	//$ gametest_ticks_batch AREA '100' '"moodsFollowTheirDay"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moodsFollowTheirDay")
	public void moodsFollowTheirDay(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		boolean moods = io.github.jcondedata.aliveworkplace.people.Moods.ENABLED;
		VillageHalls.RADIUS = 16;
		io.github.jcondedata.aliveworkplace.people.Moods.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			io.github.jcondedata.aliveworkplace.people.Moods.ENABLED = moods;
			io.github.jcondedata.aliveworkplace.people.Moods.forget();
		});
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		villager.setVillagerData(villager.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NONE));
		helper.runAfterDelay(2, () -> {
			ModAttachments.LAST_MEAL.set(villager, level.getGameTime() - 2 * VillageNeeds.DAY);
			var sad = io.github.jcondedata.aliveworkplace.people.Moods.work(level, villager);
			helper.assertTrue(sad.score() < io.github.jcondedata.aliveworkplace.people.Moods.UNHAPPY, "hungry, homeless, jobless: " + sad);
			io.github.jcondedata.aliveworkplace.people.Moods.forget();
			float slow = io.github.jcondedata.aliveworkplace.people.Moods.pace(villager);
			helper.assertTrue(slow > 1f, "unhappy pace: " + slow);
			ModAttachments.LAST_MEAL.set(villager, level.getGameTime());
			villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME,
				net.minecraft.core.GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(6, 2, 6))));
			villager.setVillagerData(villager.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
			var glad = io.github.jcondedata.aliveworkplace.people.Moods.work(level, villager);
			helper.assertTrue(glad.score() >= io.github.jcondedata.aliveworkplace.people.Moods.HAPPY, "fed, a bed and a job: " + glad);
			io.github.jcondedata.aliveworkplace.people.Moods.forget();
			helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Moods.pace(villager) < 1f, "happy pace");
			helper.succeed();
		});
	}

	/**
	 * Villagers pick a meal they haven't had lately: three meals from a store of bread, baked potatoes and cooked cod are
	 * one of each, a varied diet (a better mood); bread every time is the same food every day (a worse one).
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void villagersLikeAVariedDiet(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos chestPos = new BlockPos(2, 2, 2);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 5));
		chest.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BAKED_POTATO, 5));
		chest.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_COD, 5));
		java.util.List<BlockPos> store = java.util.List.of(helper.absolutePos(chestPos));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(VillageNeeds.eat(level, villager, store), "nothing to eat at meal " + i);
		}
		helper.assertTrue(chest.countItem(net.minecraft.world.item.Items.BREAD) == 4 && chest.countItem(net.minecraft.world.item.Items.BAKED_POTATO) == 4
			&& chest.countItem(net.minecraft.world.item.Items.COOKED_COD) == 4, "three meals should be one of each");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Diet.of(villager) == io.github.jcondedata.aliveworkplace.people.Diet.Kind.VARIED, "not varied");
		helper.assertTrue(hasReason(io.github.jcondedata.aliveworkplace.people.Moods.work(level, villager).good(), "varied_diet"), "no varied-diet reason");
		Villager dull = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 4));
		for (int i = 0; i < 3; i++) {
			io.github.jcondedata.aliveworkplace.people.Diet.ate(dull, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
		}
		helper.assertTrue(io.github.jcondedata.aliveworkplace.people.Diet.of(dull) == io.github.jcondedata.aliveworkplace.people.Diet.Kind.SAME, "not the same food");
		helper.assertTrue(hasReason(io.github.jcondedata.aliveworkplace.people.Moods.work(level, dull).bad(), "same_food"), "no same-food reason");
		helper.succeed();
	}

	private static boolean hasReason(java.util.List<net.minecraft.network.chat.Component> reasons, String key) {
		return reasons.stream().anyMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
			&& t.getKey().equals("mood.aliveworkplace.reason." + key));
	}
}
