package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.fossil.Revival;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.nether.Netherworkers;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.platform.Attachment;
import io.github.jcondedata.aliveworkplace.ranch.Daycare;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 22.2: every value we save on a villager survives the villager being saved and loaded again, the way a chunk
 * saves and loads its entities ({@code saveWithoutId}, then {@code EntityType.loadEntityRecursive}). Each value is set to
 * something other than its default, so a codec that drops a field, or a value that isn't saved at all, fails here.
 * Values that are removed again (an expedition over, a villager well again) stay removed after a reload.
 */
public class SaveReloadGameTests implements FabricGameTest {
	private static final UUID ALEX = UUID.fromString("0f6c1a2e-5b8d-4c3a-9e71-2d4b6a8c0e13");
	private static final UUID STEVE = UUID.fromString("7a3e9c41-2f60-4b8d-a1c5-93e2d70b4f86");

	/** The builder's site, bag, employer and planned path; the miner's quarry; the lumberjack's farm and counts. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void builderMinerAndLumberjackDataSurviveAReload(GameTestHelper helper) {
		Villager builder = villager(helper);
		BuilderJob job = new BuilderJob(UUID.fromString("3c1d5e7f-9a2b-4c6d-8e0f-1a3b5c7d9e2f"), true);
		ModAttachments.BUILDER_JOB.set(builder, job);
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		pickaxe.set(DataComponents.DAMAGE, 17);
		pickaxe.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
		ModAttachments.BUILDER_BAG.set(builder, BuilderBag.of(List.of(new ItemStack(Items.OAK_PLANKS, 40),
			new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 3), pickaxe)));
		Employer employer = new Employer(ALEX, "Alex");
		ModAttachments.BUILDER_EMPLOYER.set(builder, employer);
		List<BlockPos> path = List.of(new BlockPos(10, 64, -3), new BlockPos(11, 64, -3), new BlockPos(11, 65, -4));
		ModAttachments.PATH.set(builder, path);
		ModAttachments.ITEMS_CRAFTED.set(builder, 31);
		Villager builderAfter = reload(helper, builder);
		expect(helper, builderAfter, ModAttachments.BUILDER_JOB, job);
		BuilderBag bag = ModAttachments.BUILDER_BAG.get(builderAfter);
		helper.assertTrue(bag != null, "the builder's bag was lost");
		helper.assertTrue(bag.count(Items.OAK_PLANKS) == 40, "planks in the bag after a reload: " + bag.count(Items.OAK_PLANKS));
		helper.assertTrue(bag.count(Items.COBBLESTONE) == 67, "cobblestone in the bag after a reload: " + bag.count(Items.COBBLESTONE));
		ItemStack pickaxeAfter = bag.stacks().stream().filter(s -> s.is(Items.IRON_PICKAXE)).findFirst().orElse(ItemStack.EMPTY);
		helper.assertTrue(ItemStack.isSameItemSameComponents(pickaxe, pickaxeAfter),
			"the pickaxe in the bag lost its wear or name: " + pickaxeAfter + " " + pickaxeAfter.getComponentsPatch());
		expect(helper, builderAfter, ModAttachments.BUILDER_EMPLOYER, employer);
		expect(helper, builderAfter, ModAttachments.PATH, path);
		expect(helper, builderAfter, ModAttachments.ITEMS_CRAFTED, 31);

		Villager miner = villager(helper);
		BuilderJob quarry = new BuilderJob(UUID.fromString("9e8d7c6b-5a49-4382-a1b0-c9d8e7f6a5b4"), false);
		ModAttachments.MINER_JOB.set(miner, quarry);
		expect(helper, reload(helper, miner), ModAttachments.MINER_JOB, quarry);

		Villager lumberjack = villager(helper);
		FieldJob farm = new FieldJob(new BoundingBox(-20, 63, 5, -9, 70, 16), true);
		ModAttachments.TREE_FARM.set(lumberjack, farm);
		ModAttachments.TREES_FELLED.set(lumberjack, 212);
		ModAttachments.SAPLINGS_PLANTED.set(lumberjack, 198);
		Villager lumberjackAfter = reload(helper, lumberjack);
		expect(helper, lumberjackAfter, ModAttachments.TREE_FARM, farm);
		expect(helper, lumberjackAfter, ModAttachments.TREES_FELLED, 212);
		expect(helper, lumberjackAfter, ModAttachments.SAPLINGS_PLANTED, 198);
		helper.succeed();
	}

	/** What the other workers keep: fields, orchards, orders, revivals, routes, boarders, trips, compost, guests. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void otherWorkersJobDataSurvivesAReload(GameTestHelper helper) {
		Villager v = villager(helper);
		FieldJob orchard = new FieldJob(new BoundingBox(0, 64, 0, 12, 68, 9), false);
		ModAttachments.ORCHARD.set(v, orchard);
		FieldJob field = new FieldJob(new BoundingBox(-5, 62, -5, 5, 63, 5), true);
		ModAttachments.FARM_FIELD.set(v, field);
		ModAttachments.NO_AUTO_FARM.set(v, true);
		ModAttachments.FISHER_JOB.set(v, true);
		List<ResourceLocation> orders = List.of(ResourceLocation.fromNamespaceAndPath("cobblemon", "great_ball"),
			ResourceLocation.fromNamespaceAndPath("cobblemon", "ultra_ball"));
		ModAttachments.BALL_ORDERS.set(v, orders);
		List<Revival> revivals = List.of(
			new Revival(ALEX, "Alex", ResourceLocation.fromNamespaceAndPath("cobblemon", "helix_fossil"),
				List.of(ResourceLocation.withDefaultNamespace("bone_meal")), 2400, 900),
			new Revival(STEVE, "Steve", ResourceLocation.fromNamespaceAndPath("cobblemon", "dome_fossil"), List.of(), 2400, 0));
		ModAttachments.FOSSIL_REVIVALS.set(v, revivals);
		List<RouteData> routes = List.of(
			new RouteData(Optional.of(new BlockPos(4, 64, 8)), Optional.of(new BlockPos(-40, 70, 120)), List.of(Items.WHEAT, Items.CARROT)),
			new RouteData(Optional.of(new BlockPos(1, 2, 3)), Optional.empty(), List.of()));
		ModAttachments.COURIER_ROUTES.set(v, routes);
		CompoundTag pokemon = new CompoundTag();
		pokemon.putString("Species", "cobblemon:eevee");
		pokemon.putInt("Level", 12);
		List<Daycare.Boarder> boarders = List.of(new Daycare.Boarder(ALEX, "Alex", pokemon, 48000L));
		ModAttachments.DAYCARE.set(v, boarders);
		Netherworkers.Trip trip = new Netherworkers.Trip(120000L, 126000L, new BlockPos(30, 65, -12), 2);
		ModAttachments.NETHER_TRIP.set(v, trip);
		ModAttachments.COMPOST_LAYERS.set(v, 0.65f);
		Traveller traveller = new Traveller(96000L, 3);
		ModAttachments.TRAVELLER.set(v, traveller);
		ModAttachments.HEAD_START.set(v, 2);
		ModAttachments.LAST_GUEST_DAY.set(v, 41L);
		Villager after = reload(helper, v);
		expect(helper, after, ModAttachments.ORCHARD, orchard);
		expect(helper, after, ModAttachments.FARM_FIELD, field);
		expect(helper, after, ModAttachments.NO_AUTO_FARM, true);
		expect(helper, after, ModAttachments.FISHER_JOB, true);
		expect(helper, after, ModAttachments.BALL_ORDERS, orders);
		expect(helper, after, ModAttachments.FOSSIL_REVIVALS, revivals);
		expect(helper, after, ModAttachments.COURIER_ROUTES, routes);
		expect(helper, after, ModAttachments.DAYCARE, boarders);
		expect(helper, after, ModAttachments.NETHER_TRIP, trip);
		expect(helper, after, ModAttachments.COMPOST_LAYERS, 0.65f);
		expect(helper, after, ModAttachments.TRAVELLER, traveller);
		expect(helper, after, ModAttachments.HEAD_START, 2);
		expect(helper, after, ModAttachments.LAST_GUEST_DAY, 41L);
		helper.succeed();
	}

	/** Every count shown above a worker's head (and the ones kept quietly), each a different number. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyWorkersCountSurvivesAReload(GameTestHelper helper) {
		List<Attachment<Integer>> counts = List.of(ModAttachments.BALLS_MADE, ModAttachments.FOSSILS_REVIVED,
			ModAttachments.INGOTS_SMELTED, ModAttachments.ARMOR_MADE, ModAttachments.ITEMS_MENDED,
			ModAttachments.ANIMALS_SHEARED, ModAttachments.MILK_COLLECTED, ModAttachments.POTIONS_BREWED,
			ModAttachments.ITEMS_ENCHANTED, ModAttachments.RESEARCH_DONE, ModAttachments.VILLAGERS_REVIVED,
			ModAttachments.GUESTS_HOSTED, ModAttachments.PUPILS_TAUGHT, ModAttachments.BLOCKS_SIFTED,
			ModAttachments.GOLEM_REPAIRS, ModAttachments.NETHER_TRIPS, ModAttachments.BONE_MEAL_MADE,
			ModAttachments.VILLAGERS_CURED, ModAttachments.HORSES_TAMED, ModAttachments.FLOWERS_GROWN,
			ModAttachments.HIVES_HARVESTED, ModAttachments.POKEMON_TENDED, ModAttachments.EXPEDITIONS,
			ModAttachments.MAPS_CHARTED, ModAttachments.ITEMS_CARRIED, ModAttachments.FRUIT_PICKED,
			ModAttachments.FARM_HARVESTED, ModAttachments.FISH_CAUGHT, ModAttachments.MAIL_DELIVERED,
			ModAttachments.GUARD_KILLS, ModAttachments.DUMMY_HITS, ModAttachments.SHOP_SALES,
			ModAttachments.TRAINER_BATTLES, ModAttachments.TUTOR_LESSONS, ModAttachments.POKEMON_TRADE_COUNT,
			ModAttachments.LESSONS);
		Villager v = villager(helper);
		for (int i = 0; i < counts.size(); i++) {
			counts.get(i).set(v, 1001 + 37 * i);
		}
		Villager after = reload(helper, v);
		for (int i = 0; i < counts.size(); i++) {
			expect(helper, after, counts.get(i), 1001 + 37 * i);
		}
		helper.succeed();
	}

	/** A villager's life: school, meals, partner, parents, festivals, sickness. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aVillagersLifeSurvivesAReload(GameTestHelper helper) {
		Villager v = villager(helper);
		ModAttachments.SCHOOLED.set(v, true);
		ModAttachments.SCHOOL_BONUS.set(v, true);
		ModAttachments.LAST_MEAL.set(v, 240123L);
		List<ResourceLocation> meals = List.of(ResourceLocation.withDefaultNamespace("bread"),
			ResourceLocation.withDefaultNamespace("baked_potato"), ResourceLocation.withDefaultNamespace("cooked_cod"));
		ModAttachments.RECENT_MEALS.set(v, meals);
		ModAttachments.PARTNER.set(v, new Couples.Partner(STEVE, Component.literal("Rosalind"), 33L, true));
		ModAttachments.WIDOWED_DAY.set(v, 57L);
		ModAttachments.FESTIVAL_DAY.set(v, 56L);
		ModAttachments.PARENTS.set(v, new Families.Parents(Component.literal("Marguerite"), Component.literal("Tobias"),
			"aliveworkplace:builder", "minecraft:farmer", true));
		ModAttachments.ILL_SINCE.set(v, 239000L);
		Villager after = reload(helper, v);
		expect(helper, after, ModAttachments.SCHOOLED, true);
		expect(helper, after, ModAttachments.SCHOOL_BONUS, true);
		expect(helper, after, ModAttachments.LAST_MEAL, 240123L);
		expect(helper, after, ModAttachments.RECENT_MEALS, meals);
		Couples.Partner partner = ModAttachments.PARTNER.get(after);
		helper.assertTrue(partner != null && partner.id().equals(STEVE) && partner.name().getString().equals("Rosalind")
			&& partner.since() == 33L && partner.married(), "the partner after a reload: " + partner);
		expect(helper, after, ModAttachments.WIDOWED_DAY, 57L);
		expect(helper, after, ModAttachments.FESTIVAL_DAY, 56L);
		Families.Parents parents = ModAttachments.PARENTS.get(after);
		helper.assertTrue(parents != null && parents.mother().getString().equals("Marguerite")
			&& parents.father().getString().equals("Tobias") && parents.motherJob().equals("aliveworkplace:builder")
			&& parents.fatherJob().equals("minecraft:farmer") && parents.grownUp(), "the parents after a reload: " + parents);
		expect(helper, after, ModAttachments.ILL_SINCE, 239000L);
		helper.succeed();
	}

	/** Guards' routes and contracts, and the trainers' per-player days. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void guardAndTrainerDataSurvivesAReload(GameTestHelper helper) {
		Villager v = villager(helper);
		List<BlockPos> route = List.of(new BlockPos(0, 64, 0), new BlockPos(16, 66, 0), new BlockPos(16, 66, 16), new BlockPos(0, 64, 16));
		ModAttachments.PATROL_ROUTE.set(v, route);
		ModAttachments.MERCENARY_UNTIL.set(v, 300000L);
		Map<UUID, Long> rewards = Map.of(ALEX, 12L, STEVE, 14L);
		Map<UUID, Long> challenges = Map.of(STEVE, 9L);
		Map<UUID, Long> trades = Map.of(ALEX, 3L);
		ModAttachments.TRAINER_REWARDS.set(v, rewards);
		ModAttachments.LEADER_CHALLENGES.set(v, challenges);
		ModAttachments.POKEMON_TRADES.set(v, trades);
		Villager after = reload(helper, v);
		expect(helper, after, ModAttachments.PATROL_ROUTE, route);
		expect(helper, after, ModAttachments.MERCENARY_UNTIL, 300000L);
		expect(helper, after, ModAttachments.TRAINER_REWARDS, rewards);
		expect(helper, after, ModAttachments.LEADER_CHALLENGES, challenges);
		expect(helper, after, ModAttachments.POKEMON_TRADES, trades);
		helper.succeed();
	}

	/**
	 * Values that are only there for a while (a trip, an illness, a contract, a stay, a job) are gone after a reload once
	 * removed, and a villager that never had any of our data (one saved before the mod) loads with none.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void removedValuesStayGoneAfterAReload(GameTestHelper helper) {
		Villager v = villager(helper);
		ModAttachments.NETHER_TRIP.set(v, new Netherworkers.Trip(1L, 2L, BlockPos.ZERO, 1));
		ModAttachments.ILL_SINCE.set(v, 5L);
		ModAttachments.MERCENARY_UNTIL.set(v, 9L);
		ModAttachments.TRAVELLER.set(v, new Traveller(3L, 1));
		ModAttachments.BUILDER_JOB.set(v, new BuilderJob(ALEX));
		ModAttachments.TREE_FARM.set(v, new FieldJob(new BoundingBox(0, 0, 0, 1, 1, 1)));
		Villager saved = reload(helper, v);
		List<Attachment<?>> removed = List.of(ModAttachments.NETHER_TRIP, ModAttachments.ILL_SINCE,
			ModAttachments.MERCENARY_UNTIL, ModAttachments.TRAVELLER, ModAttachments.BUILDER_JOB, ModAttachments.TREE_FARM);
		for (Attachment<?> a : removed) {
			helper.assertTrue(a.has(saved), "set but not saved: " + name(a));
			a.remove(saved);
		}
		Villager after = reload(helper, saved);
		for (Attachment<?> a : removed) {
			helper.assertFalse(a.has(after), "removed, but back after a reload: " + name(a));
		}
		Villager plain = reload(helper, villager(helper));
		List<String> present = new ArrayList<>();
		for (Attachment<?> a : List.of(ModAttachments.BUILDER_JOB, ModAttachments.MINER_JOB, ModAttachments.TREE_FARM,
			ModAttachments.ORCHARD, ModAttachments.FARM_FIELD, ModAttachments.PARTNER, ModAttachments.PARENTS,
			ModAttachments.NETHER_TRIP, ModAttachments.ILL_SINCE, ModAttachments.MERCENARY_UNTIL, ModAttachments.TRAVELLER,
			ModAttachments.PATROL_ROUTE, ModAttachments.COURIER_ROUTES, ModAttachments.DAYCARE, ModAttachments.TREES_FELLED)) {
			if (a.has(plain)) {
				present.add(name(a));
			}
		}
		helper.assertTrue(present.isEmpty(), "a plain villager came back with data it never had: " + present);
		helper.succeed();
	}

	private static Villager villager(GameTestHelper helper) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
		return v;
	}

	/** Saves the villager and loads a new one from the save, as a chunk does (it isn't added to the world). */
	private static Villager reload(GameTestHelper helper, Villager villager) {
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(villager.save(tag), "the villager wasn't saved");
		Entity loaded = EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e);
		helper.assertTrue(loaded instanceof Villager, "the save didn't load as a villager: " + loaded);
		helper.assertFalse(loaded == villager, "the reload gave back the same villager");
		return (Villager) loaded;
	}

	private static <T> void expect(GameTestHelper helper, Villager after, Attachment<T> attachment, T expected) {
		T value = attachment.get(after);
		helper.assertTrue(expected.equals(value), name(attachment) + " after a reload: " + value + ", saved " + expected);
	}

	/** The attachment's field name in ModAttachments, for readable failures. */
	private static String name(Attachment<?> attachment) {
		for (java.lang.reflect.Field f : ModAttachments.class.getFields()) {
			try {
				if (f.get(null) == attachment) {
					return f.getName();
				}
			} catch (IllegalAccessException e) {
				// public static fields: never happens
			}
		}
		return String.valueOf(attachment);
	}
}
