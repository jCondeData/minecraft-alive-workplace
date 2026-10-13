package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.guard.Gates;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.wonder.GreatWallPowers;
import io.github.jcondedata.aliveworkplace.wonder.GreatWallSentries;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.powers.RaidBarrierPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.SentriesPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderPowers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The Great Wall's powers (ROADMAP 35.16): 50 raids started with a fixed random never gather inside the arc the Wall
 * covers from the hall (its margin included), and do without it; the bandits' raids toward a camp behind it gather on
 * the open side; its Great Gate shuts at night; no ladder leans on it and its parts are no siege's breach; a sentry
 * pushed off the walk climbs back to its post; a dead one is replaced from the stockpile the next morning, and not
 * without the iron; a damaged Wall, or Wonders off, turns both powers off; a sentry is still one after a save and load.
 */
public class GreatWallPowerGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation GREAT_WALL = AliveWorkplace.id("great_wall");
	/** A little wall of the test's own: one post on a walk the test builds, the barrier too. */
	private static final ResourceLocation LITTLE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "little_wall");
	private static final BlockPos LITTLE_POST = new BlockPos(10, 9, 10);
	private static final long DAY = VillageNeeds.DAY;

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** A finished, standing Wonder {@code id} of the village round {@code hall}, placed at {@code origin}; never counted in the test. */
	private static WonderSite wonder(GameTestHelper helper, ResourceLocation id, BlockPos hall, BlockPos origin, Rotation rotation, BlockPos stone) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Wonders.loaded().containsKey(id), id + " isn't loaded: " + Wonders.problems());
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(id, hall, null, "", 1);
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, rotation, Mirror.NONE), stone);
		manager.finish(site.id(), Chronicle.day(level));
		standing(level, site.id(), true);
		UUID uuid = site.id();
		Leftovers.after(helper, () -> {
			manager.remove(uuid);
			Wonders.forgetPowers();
			GreatWallPowers.forget();
			GreatWallSentries.forget();
		});
		return manager.site(uuid).orElseThrow();
	}

	private static void standing(ServerLevel level, UUID id, boolean whole) {
		WonderManager.get(level).setKept(id, new WonderSite.Kept(whole ? 100 : 70, Chronicle.day(level) + 10_000, !whole, 0));
		Wonders.forgetPowers();
	}

	/** The test's little wall loaded beside ours: its stage the Great Wall's first part, its one post at {@link #LITTLE_POST}. */
	private static void little(GameTestHelper helper) {
		Map<ResourceLocation, Wonder> before = Wonders.loaded();
		JsonObject json = JsonParser.parseString("""
			{"name": "wonder.aliveworkplace.great_wall.name", "description": "wonder.aliveworkplace.great_wall.description",
			 "icon": "minecraft:stone_brick_wall",
			 "stages": [{"name": "wonder.aliveworkplace.great_wall.stage.1", "parts": [{"part": "aliveworkplace:wonder/great_wall/stage_1_0"}]}],
			 "powers": [{"type": "raid_barrier", "margin_degrees": 10}, {"type": "sentries", "range": 16, "posts": [[10, 9, 10]]}]}
			""").getAsJsonObject();
		Map<ResourceLocation, Wonder> with = new LinkedHashMap<>(before);
		with.put(LITTLE, Wonders.read(LITTLE, json));
		Wonders.setForTest(with);
		Leftovers.after(helper, () -> Wonders.setForTest(before));
	}

	/** The walk the little wall's sentry keeps: solid stone x 5-15, z 9-11, its top at y 8 (a golem stands on it at 9). */
	private static void walk(GameTestHelper helper) {
		for (int x = 5; x <= 15; x++) {
			for (int z = 9; z <= 11; z++) {
				for (int y = 1; y <= 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS);
				}
			}
		}
	}

	private static BlockPos hall(GameTestHelper helper, BlockPos rel) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(rel, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(rel);
	}

	private static double bearing(BlockPos hall, BlockPos at) {
		return Math.atan2(at.getZ() + 0.5 - (hall.getZ() + 0.5), at.getX() + 0.5 - (hall.getX() + 0.5));
	}

	/** How many of 50 raids started with the seed's random gathered behind {@code arcs}; each raid called off at once. */
	private static int behind(GameTestHelper helper, BlockPos hall, Culture culture, List<GreatWallPowers.Arc> arcs, long seed) {
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(seed);
		int inside = 0;
		for (int i = 0; i < 50; i++) {
			VillageRaids.lastGathering = null;
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 0, 0, Double.NaN, culture, random);
			helper.assertTrue(raid != null && VillageRaids.lastGathering != null, "raid " + i + " found nowhere to gather");
			if (GreatWallPowers.walled(arcs, bearing(hall, VillageRaids.lastGathering))) {
				inside++;
			}
			VillageRaids.callOff(level, hall);
			VillageRaids.raiders(level, hall).forEach(Mob::discard);
		}
		return inside;
	}

	/**
	 * No raid gathers behind it: the Great Wall placed north of the hall covers about 170 degrees of its compass and 10
	 * more either side; 50 raids with a fixed random gather outside that, and their raiders stand outside the Wall's own
	 * arc; a raid sent toward a camp behind the Wall gathers on the open side; the same 50 without it, damaged, or with
	 * Wonders off gather inside it too. Its Great Gate shuts at night and opens by day; ladders never lean on it, and its
	 * parts are no siege's breach.
	 */
	//$ gametest_ticks_batch HUGE '200' '"greatWallBarrier"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "greatWallBarrier")
	public void noRaidGathersBehindTheGreatWall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, new BlockPos(15, 2, 15));
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20; // the raiders gather 12 out
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			VillageRaids.forget(hall);
			Wonders.ENABLED = true;
		});
		Culture monsters = Threats.on(Threats.MONSTERS).orElse(null);
		helper.assertTrue(monsters != null, "the monsters' raids are off");
		// the Wall turned to face the hall (its front, the village side, 2 north of it), its middle on the hall's line
		BlockPos origin = hall.offset(48, 0, -2);
		WonderSite site = wonder(helper, GREAT_WALL, hall, origin, Rotation.CLOCKWISE_180, hall.offset(0, 0, -1));
		BoundingBox box = GreatWallPowers.footprint(level, site).orElseThrow();
		helper.assertTrue(box.getXSpan() == 97 && box.maxZ() == origin.getZ(), "its footprint: " + box);
		List<GreatWallPowers.Arc> arcs = GreatWallPowers.arcs(level, hall);
		helper.assertTrue(arcs.size() == 1, "its arcs: " + arcs);
		double degrees = Math.toDegrees(arcs.get(0).width());
		helper.assertTrue(degrees > 175 && degrees < 200, "it covers " + degrees + " degrees");
		helper.assertTrue(GreatWallPowers.walled(arcs, -Math.PI / 2) && !GreatWallPowers.walled(arcs, Math.PI / 2),
			"north is behind it, south isn't");
		List<GreatWallPowers.Arc> own = List.of(GreatWallPowers.arc(hall, box, 0).orElseThrow());
		RandomSource random = RandomSource.create(1616L);
		for (int i = 0; i < 50; i++) {
			VillageRaids.lastGathering = null;
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 0, 0, Double.NaN, monsters, random);
			helper.assertTrue(raid != null && VillageRaids.lastGathering != null, "raid " + i + " found nowhere to gather");
			double b = bearing(hall, VillageRaids.lastGathering);
			helper.assertFalse(GreatWallPowers.walled(arcs, b), "raid " + i + " gathered behind the Wall, at " + Math.toDegrees(b) + " degrees");
			for (Mob m : VillageRaids.raiders(level, hall)) {
				helper.assertFalse(GreatWallPowers.walled(own, bearing(hall, m.blockPosition())), "a raider of raid " + i + " stands behind the Wall: " + m.blockPosition());
			}
			VillageRaids.callOff(level, hall);
			VillageRaids.raiders(level, hall).forEach(Mob::discard);
		}
		// toward a camp behind it (the bandits' and the lairs' raids come from their camp's side): the open side nearest it
		RandomSource toward = RandomSource.create(7L);
		for (int i = 0; i < 50; i++) {
			double b = GreatWallPowers.bearing(arcs, -Math.PI / 2 + 0.3, 0.5, toward);
			helper.assertFalse(GreatWallPowers.walled(arcs, b), "toward a camp behind it they gathered behind it: " + Math.toDegrees(b));
		}
		helper.assertTrue(GreatWallPowers.bearing(List.of(), 1.0, 0.5, RandomSource.create(3L)) == 1.0 + (RandomSource.create(3L).nextDouble() * 2 - 1) * 0.5,
			"without walls the draw isn't the plain one");

		// the Great Gate: shut at night, open by day (guards or none)
		List<BlockPos> gates = GreatWallPowers.gates(level, site);
		helper.assertTrue(gates.size() >= 4, "the Great Gate's fence gates: " + gates);
		for (BlockPos g : gates) {
			helper.assertTrue(box.isInside(g) && Math.abs(g.getX() - hall.getX()) <= 2, "a fence gate outside the gateway: " + g);
			level.setBlock(g, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true), 2);
		}
		at(level, Chronicle.day(level) + 1, 14000);
		Gates.round(level, hall, 0);
		helper.assertTrue(gates.stream().noneMatch(g -> level.getBlockState(g).getValue(FenceGateBlock.OPEN)), "the Great Gate stood open at night");
		at(level, Chronicle.day(level) + 1, 1000);
		Gates.round(level, hall, 0);
		helper.assertTrue(gates.stream().allMatch(g -> level.getBlockState(g).getValue(FenceGateBlock.OPEN)), "the Great Gate stayed shut by day");
		// no ladder leans on it, and no siege picks it for a breach
		helper.assertTrue(GreatWallPowers.shields(level, hall, gates.get(0)) && !GreatWallPowers.shields(level, hall, hall.offset(0, 0, 10)),
			"what it shields");
		for (Wonder.Stage stage : Wonders.loaded().get(GREAT_WALL).stages()) {
			for (Wonder.Part part : stage.parts()) {
				helper.assertFalse(Sieges.isDefence(part.part()) || Sieges.isGated(part.part()), part.part() + " is a siege's wall or gate");
			}
		}

		// damaged: down; repaired: up; Wonders off: down
		standing(level, site.id(), false);
		helper.assertTrue(GreatWallPowers.arcs(level, hall).isEmpty() && !GreatWallPowers.shields(level, hall, gates.get(0)), "a damaged Wall still bars raids");
		int damagedInside = behind(helper, hall, monsters, arcs, 1616L);
		helper.assertTrue(damagedInside > 0, "with the Wall damaged none gathered on its side");
		standing(level, site.id(), true);
		helper.assertTrue(GreatWallPowers.arcs(level, hall).size() == 1, "repaired, its barrier isn't back");
		Wonders.ENABLED = false;
		helper.assertTrue(GreatWallPowers.arcs(level, hall).isEmpty(), "Wonders off, it still bars raids");
		Wonders.ENABLED = true;
		// without it
		WonderManager.get(level).remove(site.id());
		Wonders.forgetPowers();
		int without = behind(helper, hall, monsters, arcs, 1616L);
		helper.assertTrue(without > 0, "without the Wall none of 50 gathered on its side");
		helper.succeed();
	}

	private static IronGolem sentry(GameTestHelper helper, WonderSite site) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(GreatWallSentries.man(level, site) == 1, "the sentry didn't take up its post");
		GreatWallSentries.Post post = GreatWallSentries.posts(level, site.hall()).get(0);
		return GreatWallSentries.sentry(level, post).orElseThrow();
	}

	/**
	 * A sentry pushed off the walk climbs back: the little wall's sentry stands at its post on the walk (y 9), a Wall
	 * Sentry of the Golem Smith's with twice a golem's health; put down on the field side at the wall's foot, with no
	 * stair up, it climbs the face and is back at its post.
	 */
	//$ gametest_ticks_batch HUGE '600' '"greatWallClimb"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "greatWallClimb")
	public void aSentryPushedOffTheWalkClimbsBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, new BlockPos(5, 2, 24));
		little(helper);
		walk(helper);
		WonderSite site = wonder(helper, LITTLE, hall, helper.absolutePos(BlockPos.ZERO), Rotation.NONE, hall.offset(2, 0, 0));
		IronGolem golem = sentry(helper, site);
		BlockPos post = helper.absolutePos(LITTLE_POST);
		helper.assertTrue(ModAttachments.GOLEM_POST.get(golem).equals(post) && golem.getTags().contains(GreatWallSentries.TAG)
			&& golem.getMaxHealth() >= 200 && GreatWallSentries.duty(level, golem) != null, "the sentry: " + golem.getTags() + " " + golem.getMaxHealth());
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(golem.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(post)) < 1.5, "the sentry left its post: " + golem.position());
			BlockPos below = helper.absolutePos(new BlockPos(10, 2, 14));
			golem.teleportTo(below.getX() + 0.5, below.getY(), below.getZ() + 0.5);
			helper.runAfterDelay(20, () -> {
				helper.assertTrue(golem.getY() < post.getY() - 3, "it wasn't put off the walk: " + golem.position());
				helper.succeedWhen(() -> helper.assertTrue(golem.isAlive() && golem.getY() >= post.getY() - 0.1
					&& golem.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(post)) < 1.5, "not back at its post: " + golem.position()));
			});
		});
	}

	private static Container chest(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel, Blocks.CHEST);
		return (Container) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	/**
	 * A dead sentry is replaced the next morning: by the village's Tinkerer (the Golem Smith's stand-in), from 4 iron
	 * blocks and a pumpkin in the stockpile, which are used up, and the chronicle says so; not the same evening, and not
	 * without the iron; without a Golem Smith or a Tinkerer, nobody.
	 */
	//$ gametest_ticks_batch HUGE '100' '"greatWallReplace"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "greatWallReplace")
	public void aLostSentryIsReplacedTheNextMorning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, new BlockPos(5, 2, 24));
		little(helper);
		walk(helper);
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		BlockPos stoneRel = new BlockPos(12, 2, 24);
		helper.setBlock(stoneRel, Blocks.STONE);
		WonderSite site = wonder(helper, LITTLE, hall, helper.absolutePos(BlockPos.ZERO), Rotation.NONE, helper.absolutePos(stoneRel));
		Container chest = chest(helper, new BlockPos(13, 2, 24));
		chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
		chest.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 1));
		IronGolem first = sentry(helper, site);
		GreatWallSentries.Post post = GreatWallSentries.posts(level, hall).get(0);
		first.discard();
		long day = Chronicle.day(level) + 1;
		// the evening it died: nobody yet
		at(level, day, 15000);
		GreatWallSentries.morning(level, hall);
		helper.assertTrue(GreatWallSentries.sentry(level, post).isEmpty(), "replaced the same evening");
		// the morning: nobody to make one
		at(level, day + 1, 500);
		GreatWallSentries.morning(level, hall);
		helper.assertTrue(GreatWallSentries.sentry(level, post).isEmpty(), "replaced without a Golem Smith or a Tinkerer");
		GreatWallSentries.forget();
		Villager tinkerer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 20));
		tinkerer.setNoAi(true);
		tinkerer.setVillagerData(tinkerer.getVillagerData().setType(VillagerType.PLAINS).setProfession(ModVillagers.TINKERER).setLevel(3));
		tinkerer.setCustomName(net.minecraft.network.chat.Component.literal("Tibbs"));
		GreatWallSentries.morning(level, hall);
		IronGolem second = GreatWallSentries.sentry(level, post).orElse(null);
		helper.assertTrue(second != null && second != first, "not replaced in the morning");
		helper.assertTrue(chest.getItem(0).isEmpty() && chest.getItem(1).isEmpty(), "the iron and the pumpkin weren't used: " + chest.getItem(0) + ", " + chest.getItem(1));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.text().getString().equals("Tibbs made a new sentry for the Great Wall from four iron blocks and a pumpkin")),
			"the chronicle: " + entity.chronicle().stream().map(e -> e.text().getString()).toList());
		// lost again: the next morning, no iron left, nobody
		second.discard();
		at(level, day + 2, 500);
		GreatWallSentries.morning(level, hall);
		helper.assertTrue(GreatWallSentries.sentry(level, post).isEmpty(), "replaced without the iron");
		// iron again, but only three blocks
		chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 3));
		chest.setItem(1, new ItemStack(Items.PUMPKIN, 1));
		GreatWallSentries.forget();
		helper.assertTrue(GreatWallSentries.replace(level, hall) == 0, "replaced from three iron blocks");
		chest.setItem(2, new ItemStack(Items.IRON_BLOCK, 1));
		helper.assertTrue(GreatWallSentries.replace(level, hall) == 1 && chest.isEmpty(), "not replaced from four iron blocks and a plain pumpkin");
		helper.succeed();
	}

	/**
	 * A damaged Wall turns both off: its barrier is down, and its sentry is a plain golem (no duty: pushed off, it stays
	 * off) and isn't replaced; repaired, it goes back up to its post; Wonders off, the same. A sentry saved and loaded is
	 * still one. The types' numbers and refusals, and the Great Wall's own: four posts, 16 blocks.
	 */
	//$ gametest_ticks_batch HUGE '700' '"greatWallDamaged"'
	@GameTest(template = HUGE, timeoutTicks = 700, batch = "greatWallDamaged")
	public void aDamagedGreatWallTurnsBothOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, new BlockPos(5, 2, 24));
		little(helper);
		walk(helper);
		Leftovers.after(helper, () -> Wonders.ENABLED = true);
		WonderSite site = wonder(helper, LITTLE, hall, helper.absolutePos(BlockPos.ZERO), Rotation.NONE, hall.offset(2, 0, 0));
		IronGolem golem = sentry(helper, site);
		BlockPos post = helper.absolutePos(LITTLE_POST);
		// the types
		Wonder wall = Wonders.loaded().get(GREAT_WALL);
		SentriesPower sentries = (SentriesPower) wall.powers().stream().filter(p -> p.type().equals(SentriesPower.TYPE)).findFirst().orElseThrow().power();
		RaidBarrierPower barrier = (RaidBarrierPower) wall.powers().stream().filter(p -> p.type().equals(RaidBarrierPower.TYPE)).findFirst().orElseThrow().power();
		helper.assertTrue(sentries.count() == 4 && sentries.posts().size() == 4 && sentries.range() == 16 && barrier.marginDegrees() == 10,
			"the Great Wall's numbers: " + sentries + ", " + barrier);
		for (String bad : new String[] {"{\"type\": \"sentries\", \"count\": 2, \"posts\": [[1, 2, 3]]}", "{\"type\": \"sentries\", \"range\": 2}",
			"{\"type\": \"sentries\", \"posts\": [[1, 2]]}", "{\"type\": \"raid_barrier\", \"margin_degrees\": 90}"}) {
			try {
				WonderPowers.parse(JsonParser.parseString(bad).getAsJsonObject());
				helper.fail("read: " + bad);
			} catch (IllegalArgumentException expected) {
				// refused, as it should be
			}
		}
		helper.assertFalse(WonderPowers.DECLARED.contains(SentriesPower.TYPE) || WonderPowers.DECLARED.contains(RaidBarrierPower.TYPE), "still declared");
		// saved and loaded, a sentry is still one
		CompoundTag saved = golem.saveWithoutId(new CompoundTag());
		IronGolem copy = EntityType.IRON_GOLEM.create(level);
		copy.load(saved);
		helper.assertTrue(GreatWallSentries.isSentry(copy) && post.equals(ModAttachments.GOLEM_POST.get(copy)), "a loaded sentry: " + copy.getTags());

		standing(level, site.id(), false);
		helper.assertTrue(GreatWallSentries.duty(level, golem) == null && GreatWallSentries.posts(level, hall).isEmpty()
			&& GreatWallPowers.arcs(level, hall).isEmpty(), "a damaged Wall's powers are on");
		BlockPos below = helper.absolutePos(new BlockPos(10, 2, 14));
		golem.teleportTo(below.getX() + 0.5, below.getY(), below.getZ() + 0.5);
		golem.discard();
		GreatWallSentries.forget();
		helper.assertTrue(GreatWallSentries.replace(level, hall) == 0, "a damaged Wall's sentry replaced");
		IronGolem plain = EntityType.IRON_GOLEM.create(level);
		plain.load(saved);
		plain.setUUID(UUID.randomUUID());
		plain.moveTo(below.getX() + 0.5, below.getY(), below.getZ() + 0.5);
		level.addFreshEntity(plain);
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(plain.getY() < post.getY() - 3, "a damaged Wall's sentry climbed back: " + plain.position());
			Wonders.ENABLED = false;
			standing(level, site.id(), true);
			helper.assertTrue(GreatWallSentries.duty(level, plain) == null && GreatWallPowers.arcs(level, hall).isEmpty(), "Wonders off, its powers are on");
			helper.runAfterDelay(100, () -> {
				helper.assertTrue(plain.getY() < post.getY() - 3, "Wonders off, the sentry climbed back: " + plain.position());
				Wonders.ENABLED = true;
				Wonders.forgetPowers();
				helper.assertTrue(GreatWallSentries.duty(level, plain) != null && GreatWallPowers.arcs(level, hall).size() == 1, "repaired, its powers aren't back");
				helper.succeedWhen(() -> helper.assertTrue(plain.getY() >= post.getY() - 0.1
					&& plain.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(post)) < 1.5, "repaired, not back at its post: " + plain.position()));
			});
		});
	}
}
