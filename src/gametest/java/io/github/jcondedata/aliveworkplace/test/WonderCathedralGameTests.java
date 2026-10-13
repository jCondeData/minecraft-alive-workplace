package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.grave.UndertakerWork;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.BornGifts;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.powers.BeautyPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.UndertakersPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WeddingsPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderBells;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.WonderPowers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jetbrains.annotations.Nullable;

/**
 * The Great Cathedral's powers (ROADMAP 35.10). While it stands: an Undertaker with empty chests brings back a grave 40
 * blocks from the table (outside the 32 they look without it), and doesn't without the Cathedral; whoever still lies in
 * the village's graves at dawn is brought back by morning; a wedding is held at its altar and blessed (the whole village
 * 15 happier for 3 days, the first baby the next day a bed is free, past the store and the daily wait, and the children
 * twice as likely to grow up Gifted); it counts 8 beauty. Damaged, both powers are off; repaired, back. The file's
 * numbers, the altar before the gold, the belfries' bells, and every new line a player reads.
 */
public class WonderCathedralGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:colony_area";
	private static final ResourceLocation CATHEDRAL = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "great_cathedral");
	private static final BlockPos HALL = new BlockPos(22, 2, 22);
	/** The Cathedral's origin, so that its altar (19, 2, 49 in its blueprint) falls in the test area at (21, 4, 9). */
	private static final BlockPos ORIGIN = new BlockPos(2, 2, -40);
	private static final long DAY = VillageNeeds.DAY;

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** A finished, standing Great Cathedral of the village round {@code hall}, placed at {@code origin} (or nowhere); never counted in the test. */
	private static UUID cathedral(GameTestHelper helper, BlockPos hall, @Nullable BlockPos origin) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Wonders.loaded().containsKey(CATHEDRAL), "the Great Cathedral isn't loaded: " + Wonders.problems());
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(CATHEDRAL, hall, null, "", 1);
		if (origin != null) {
			manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
		}
		manager.finish(site.id(), Chronicle.day(level));
		standing(level, site.id(), true);
		UUID id = site.id();
		Leftovers.after(helper, () -> {
			manager.remove(id);
			WonderBells.forget();
		});
		return id;
	}

	/** Sets the Cathedral standing or damaged, counted far ahead so that no count runs in the test. */
	private static void standing(ServerLevel level, UUID id, boolean whole) {
		WonderManager.get(level).setKept(id, new WonderSite.Kept(whole ? 100 : 80, Chronicle.day(level) + 10_000, !whole, 0));
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static Villager undertaker(GameTestHelper helper, BlockPos table, String name) {
		helper.setBlock(table, ModBlocks.UNDERTAKERS_TABLE);
		Villager u = villager(helper, table.offset(1, 0, 1), name);
		Jobs.employ(helper.getLevel(), u, helper.absolutePos(table), ModVillagers.UNDERTAKERS_TABLE_POI, ModVillagers.UNDERTAKER);
		return u;
	}

	/** {@code name} dies at {@code pos}: their grave. */
	private static BlockPos grave(GameTestHelper helper, BlockPos pos, String name, List<UUID> ids) {
		Villager dead = villager(helper, pos, name);
		ids.add(dead.getUUID());
		dead.kill();
		List<BlockPos> graves = Graves.near(helper.getLevel(), helper.absolutePos(pos), 4);
		helper.assertTrue(graves.size() == 1, name + " left " + graves.size() + " graves");
		return graves.get(0);
	}

	private static void bed(GameTestHelper helper, BlockPos foot) {
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	private static void court(ServerLevel level, Villager a, Villager b) {
		long today = Chronicle.day(level);
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), today - 2, false));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), today - 2, false));
	}

	private static List<String> strings(List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	private static void translated(GameTestHelper helper, List<String> lines) {
		for (String line : lines) {
			helper.assertTrue(!line.contains("aliveworkplace.") && !line.contains("%"), "untranslated: " + line);
		}
	}

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.players(helper);
		Moods.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	/**
	 * An Undertaker with empty chests and a grave 40 blocks from the table: without the Cathedral nothing happens (it's past
	 * their 32 blocks, and they have nothing to bring anyone back with); with it standing they walk there and bring Mira back.
	 */
	//$ gametest_ticks_batch AREA '4000' '"cathedralUndertaker"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "cathedralUndertaker")
	public void anUndertakerBringsBackAFarGraveOnlyWithTheCathedral(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		at(level, 40, 3000);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		Villager uma = undertaker(helper, new BlockPos(2, 2, 2), "Uma");
		List<UUID> ids = new ArrayList<>();
		BlockPos grave = grave(helper, new BlockPos(2, 2, 42), "Mira", ids);
		BlockPos table = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.assertTrue(Math.sqrt(grave.distSqr(table)) >= 38 && UndertakerWork.radius(uma) == 32, "the grave is " + Math.sqrt(grave.distSqr(table)) + " away");
		helper.onEachTick(() -> {
			if (level.getDayTime() % DAY > 8000) {
				at(level, Chronicle.day(level), 3000); // working hours, the same day
			}
		});
		AtomicBoolean built = new AtomicBoolean();
		helper.runAfterDelay(800, () -> {
			helper.assertTrue(level.getBlockState(grave).is(ModBlocks.GRAVE) && ModAttachments.VILLAGERS_REVIVED.getOrElse(uma, 0) == 0,
				"Mira came back without the Cathedral");
			helper.assertTrue(!UndertakerWork.needsNothing(level, uma) && UndertakersPower.graves(level, table, UndertakerWork.radius(uma)).isEmpty(),
				"without the Cathedral the grave 40 blocks away was tended");
			cathedral(helper, helper.absolutePos(HALL), null);
			helper.assertTrue(UndertakerWork.needsNothing(level, uma) && UndertakersPower.graves(level, table, UndertakerWork.radius(uma)).equals(List.of(grave)),
				"with the Cathedral: " + UndertakersPower.graves(level, table, UndertakerWork.radius(uma)));
			built.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(built.get(), "the Cathedral isn't up yet");
			helper.assertTrue(!level.getBlockState(grave).is(ModBlocks.GRAVE), "the grave is still there");
			helper.assertTrue(level.getEntity(ids.get(0)) instanceof Villager back && back.isAlive(), "Mira isn't back");
			helper.assertTrue(ModAttachments.VILLAGERS_REVIVED.getOrElse(uma, 0) == 1, "revived: " + ModAttachments.VILLAGERS_REVIVED.getOrElse(uma, 0));
			helper.assertTrue(chest.isEmpty() && ModAttachments.BUILDER_BAG.getOrCreate(uma).stacks().stream().allMatch(s -> s.isEmpty()), "something was used");
		});
	}

	/**
	 * Whoever died before this morning and still lies in the village's graves is brought back at dawn, the Undertaker
	 * credited and the players near told; not a grave from after dawn, not later in the day, not without an Undertaker.
	 */
	//$ gametest_ticks_batch AREA '100' '"cathedralMorning"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "cathedralMorning")
	public void theNightsDeadAreBackByMorning(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			at(level, 41, 3000);
			List<Component> heard = new ArrayList<>();
			ServerPlayer player = WonderFinishedGameTests.listener(helper, heard, "cathedral-morning");
			player.teleportTo(hall.getX(), hall.getY(), hall.getZ());
			Villager uma = undertaker(helper, new BlockPos(2, 2, 2), "Uma");
			cathedral(helper, hall, null);
			List<UUID> ids = new ArrayList<>();
			BlockPos night = grave(helper, new BlockPos(40, 2, 40), "Mira", ids);
			BlockPos today = grave(helper, new BlockPos(10, 2, 40), "Bo", ids);
			GraveBlockEntity g = (GraveBlockEntity) level.getBlockEntity(night);
			g.fill(g.villager(), g.name(), g.profession(), g.villagerLevel(), g.died() - DAY); // died in the night
			helper.assertTrue(UndertakersPower.morning(level, hall) == 0 && level.getBlockState(night).is(ModBlocks.GRAVE), "brought back at 3000, past the morning");
			at(level, 42, 1000);
			int back = UndertakersPower.morning(level, hall);
			// (back under a new id: Mira's body is still falling over, 20 ticks)
			helper.assertTrue(back == 1 && !level.getBlockState(night).is(ModBlocks.GRAVE) && !level.getEntitiesOfClass(Villager.class, helper.getBounds(),
				v -> v.isAlive() && v.getCustomName() != null && v.getCustomName().getString().equals("Mira")).isEmpty(), "the night's grave by morning: " + back);
			helper.assertTrue(level.getBlockState(today).is(ModBlocks.GRAVE), "a grave from after dawn emptied");
			helper.assertTrue(ModAttachments.VILLAGERS_REVIVED.getOrElse(uma, 0) == 1, "Uma isn't credited");
			String told = "Uma brought Mira back in the night: by the Great Cathedral's grace, nobody lies in the graveyard past morning";
			helper.assertTrue(strings(heard).contains(told), "the player heard: " + strings(heard));
			translated(helper, strings(heard));
			// Without an Undertaker nobody is brought back.
			GraveBlockEntity bo = (GraveBlockEntity) level.getBlockEntity(today);
			bo.fill(bo.villager(), bo.name(), bo.profession(), bo.villagerLevel(), bo.died() - DAY);
			uma.discard();
			helper.assertTrue(UndertakersPower.morning(level, hall) == 0 && level.getBlockState(today).is(ModBlocks.GRAVE), "brought back with no Undertaker");
			helper.succeed();
		});
	}

	/**
	 * A wedding in the Cathedral's village is held at its altar and blessed: the chronicle and the players say so, the whole
	 * village is 15 happier for 3 days, the first baby comes the next day there's a free bed (past the store and the daily
	 * wait, once), and the baby is twice as likely to grow up Gifted.
	 */
	//$ gametest_ticks_batch AREA '100' '"cathedralWedding"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "cathedralWedding")
	public void aWeddingGoesToTheCathedralWithItsMoodAndBaby(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		bed(helper, new BlockPos(30, 2, 30));
		helper.runAfterDelay(2, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			at(level, 30, 7000);
			List<Component> heard = new ArrayList<>();
			ServerPlayer player = WonderFinishedGameTests.listener(helper, heard, "cathedral-wedding");
			player.teleportTo(hall.getX(), hall.getY(), hall.getZ());
			BlockPos chapelOrBell = Couples.venue(level, hall);
			cathedral(helper, hall, helper.absolutePos(ORIGIN));
			BlockPos altar = helper.absolutePos(ORIGIN).offset(19, 2, 49);
			helper.assertTrue(Couples.venue(level, hall).equals(altar) && !chapelOrBell.equals(altar), "weddings aren't at the altar: " + Couples.venue(level, hall));
			Villager a = villager(helper, new BlockPos(20, 2, 20), "Dara");
			Villager b = villager(helper, new BlockPos(21, 2, 20), "Tom");
			Villager guest = villager(helper, new BlockPos(25, 2, 25), "Ilse");
			court(level, a, b);
			Couples.wed(level, hall, a, b);

			// Blessed: the couple, the hall, the chronicle, the players near.
			WeddingsPower.Blessed blessed = ModAttachments.WONDER_BLESSING.get(a);
			helper.assertTrue(blessed != null && blessed.babyDue() && blessed.day() == 30 && blessed.giftedFactor() == 2.0 && ModAttachments.WONDER_BLESSING.has(b),
				"the couple: " + blessed);
			WeddingsPower.VillageBlessing village = entity.wonderWedding();
			helper.assertTrue(village != null && village.day() == 30 && village.mood() == 15 && village.days() == 3 && village.wonder().equals(CATHEDRAL), "the hall: " + village);
			String chronicle = "Dara and Tom were wed at the altar of the Great Cathedral, and the wedding was blessed";
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.text().getString().equals(chronicle)), "the chronicle: "
				+ entity.chronicle().stream().map(e -> e.text().getString()).toList());
			String name = VillageHalls.name(level, hall).getString();
			String told = "The bells of the Great Cathedral ring out: Dara and Tom are wed at its altar, and the wedding is blessed. All of " + name
				+ " is 15 happier for 3 days";
			helper.assertTrue(strings(heard).contains(told), "the player heard: " + strings(heard));
			translated(helper, strings(heard));

			// The whole village's mood: a guest 15 happier, with its reason, for 3 days.
			Moods.forget();
			Moods.Mood happy = Moods.work(level, guest);
			entity.setWonderWedding(null);
			Moods.forget();
			Moods.Mood plain = Moods.work(level, guest);
			entity.setWonderWedding(village);
			Moods.forget();
			helper.assertTrue(happy.score() - plain.score() == 15, "the guest: " + happy.score() + " blessed, " + plain.score() + " not");
			helper.assertTrue(strings(happy.good()).contains("a blessed wedding at the Great Cathedral"), "the reason: " + strings(happy.good()));
			at(level, 32, 7000);
			helper.assertTrue(WeddingsPower.mood(level, guest) != null, "the mood ended before 3 days");
			at(level, 33, 7000);
			helper.assertTrue(WeddingsPower.mood(level, guest) == null, "the mood outlasted 3 days");

			// The baby: not on the wedding day, then the next day past the store and the wait; once.
			VillageNeeds.Needs needs = new VillageNeeds.Needs(4, 4, 5, 4, 4, 0, 0, 0.1f);
			at(level, 30, 9000);
			long justNow = level.getGameTime();
			helper.assertTrue(VillageGrowth.blocker(level, hall, needs, justNow) != VillageGrowth.Blocker.NONE, "the village could grow anyway");
			helper.assertTrue(VillageGrowth.grow(level, hall, needs, justNow) == null, "the baby came on the wedding day");
			at(level, 31, 1000);
			Villager baby = VillageGrowth.grow(level, hall, needs, justNow);
			helper.assertTrue(baby != null && baby.isBaby(), "no blessed baby the next day");
			Families.Parents parents = Families.parents(baby);
			helper.assertTrue(parents != null && List.of(parents.mother().getString(), parents.father().getString()).containsAll(List.of("Dara", "Tom")),
				"the baby's parents: " + parents);
			helper.assertTrue(!ModAttachments.WONDER_BLESSING.get(a).babyDue() && !ModAttachments.WONDER_BLESSING.get(b).babyDue(), "the baby is still due");
			bed(helper, new BlockPos(33, 2, 30));
			helper.assertTrue(VillageGrowth.grow(level, hall, needs, justNow) == null, "a second early baby");

			// Twice as likely Gifted: the same dice make the blessed child Gifted and another not.
			helper.assertTrue(WeddingsPower.giftedFactor(baby) == 2.0 && WeddingsPower.giftedFactor(guest) == 1.0, "the Gifted factor: " + WeddingsPower.giftedFactor(baby));
			long seed = 0;
			while (true) {
				RandomSource r = RandomSource.create(seed);
				if (r.nextInt(BornGifts.LEGEND_ONE_IN) != 0 && r.nextInt(BornGifts.GIFTED_ONE_IN) == 1) {
					break;
				}
				seed++;
			}
			Families.Parents masters = new Families.Parents(Component.literal("Dara"), Component.literal("Tom"), "minecraft:farmer", "minecraft:farmer", false, true, true);
			BornGifts.Outcome blessedChild = BornGifts.grownUp(level, hall, baby, masters, RandomSource.create(seed));
			BornGifts.Outcome other = BornGifts.grownUp(level, hall, guest, masters, RandomSource.create(seed));
			helper.assertTrue(blessedChild.gift() != null && other.gift() == null, "seed " + seed + ": blessed " + blessedChild + ", other " + other);
			ModAttachments.GIFTED.remove(baby);
			baby.discard();
			helper.succeed();
		});
	}

	/**
	 * The Cathedral damaged (under wonderStanding): Undertakers need their apple again and look 32 blocks out, nobody is
	 * brought back by morning, weddings go back to the bell and aren't blessed, and the 8 beauty is gone; repaired, all back.
	 */
	//$ gametest_ticks_batch AREA '100' '"cathedralDamaged"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "cathedralDamaged")
	public void aDamagedCathedralTurnsBothOff(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			at(level, 50, 1000);
			int beautyBefore = Decorations.beauty(level, hall);
			Villager uma = undertaker(helper, new BlockPos(2, 2, 2), "Uma");
			BlockPos table = helper.absolutePos(new BlockPos(2, 2, 2));
			UUID id = cathedral(helper, hall, helper.absolutePos(ORIGIN));
			BlockPos altar = helper.absolutePos(ORIGIN).offset(19, 2, 49);
			helper.assertTrue(UndertakerWork.needsNothing(level, uma) && Couples.venue(level, hall).equals(altar), "the powers aren't on");
			helper.assertTrue(Decorations.beauty(level, hall) == beautyBefore + 8 && BeautyPower.beauty(level, hall) == 8, "its 8 beauty: "
				+ beautyBefore + " → " + Decorations.beauty(level, hall));
			List<UUID> ids = new ArrayList<>();
			BlockPos night = grave(helper, new BlockPos(40, 2, 40), "Mira", ids);
			GraveBlockEntity g = (GraveBlockEntity) level.getBlockEntity(night);
			g.fill(g.villager(), g.name(), g.profession(), g.villagerLevel(), g.died() - DAY);

			standing(level, id, false);
			helper.assertTrue(Wonders.power(level, hall, UndertakersPower.TYPE).isEmpty() && Wonders.power(level, hall, WeddingsPower.TYPE).isEmpty(), "powers while damaged");
			helper.assertTrue(!UndertakerWork.needsNothing(level, uma) && UndertakersPower.graves(level, table, UndertakerWork.radius(uma)).isEmpty(),
				"a damaged Cathedral's Undertaker");
			helper.assertTrue(UndertakersPower.morning(level, hall) == 0 && level.getBlockState(night).is(ModBlocks.GRAVE), "brought back by morning while damaged");
			helper.assertTrue(!Couples.venue(level, hall).equals(altar) && Decorations.beauty(level, hall) == beautyBefore, "the altar and beauty while damaged");
			Villager a = villager(helper, new BlockPos(20, 2, 20), "Dara");
			Villager b = villager(helper, new BlockPos(21, 2, 20), "Tom");
			court(level, a, b);
			Couples.wed(level, hall, a, b);
			helper.assertTrue(!ModAttachments.WONDER_BLESSING.has(a) && entity.wonderWedding() == null, "a wedding blessed while it's damaged");

			standing(level, id, true);
			helper.assertTrue(UndertakerWork.needsNothing(level, uma) && Couples.venue(level, hall).equals(altar) && Decorations.beauty(level, hall) == beautyBefore + 8,
				"the powers didn't come back");
			helper.assertTrue(UndertakersPower.morning(level, hall) == 1, "nobody brought back once repaired");
			helper.succeed();
		});
	}

	/**
	 * The file: undertakers (the whole village, by morning), weddings (15 for 3 days, Gifted twice, the altar, bells), 8
	 * beauty; bad numbers refused. The altar stands before the gold in its drawing, the belfries hold their bells, and the
	 * Wonders page's lines read.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCathedralsPowersAltarAndBells(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Wonder cathedral = Wonders.loaded().get(CATHEDRAL);
		helper.assertTrue(cathedral != null, "not loaded: " + Wonders.problems());
		List<WonderPower> powers = cathedral.powers().stream().map(Wonder.Power::power).toList();
		helper.assertTrue(powers.size() == 3 && powers.get(0).equals(new UndertakersPower(true, true))
			&& powers.get(1).equals(new WeddingsPower(15, 3, 2.0, Optional.of(new BlockPos(19, 2, 49)), true)) && powers.get(2).equals(new BeautyPower(8)),
			"the Cathedral's powers: " + powers);
		for (String bad : List.of("{\"type\": \"undertakers\", \"search\": \"world\"}", "{\"type\": \"undertakers\", \"revive_by\": \"noon\"}",
			"{\"type\": \"weddings\", \"gifted_factor\": 5}", "{\"type\": \"weddings\", \"mood\": 99}", "{\"type\": \"weddings\", \"altar\": [1, 2]}")) {
			try {
				WonderPowers.parse(JsonParser.parseString(bad).getAsJsonObject());
				helper.fail("read: " + bad);
			} catch (IllegalArgumentException expected) {
				// refused, with the reason the file is logged with
			}
		}
		helper.assertTrue(!WonderPowers.DECLARED.contains("undertakers") && !WonderPowers.DECLARED.contains("weddings"), "still declared");
		List<String> lines = strings(powers.stream().map(WonderPower::describe).toList());
		helper.assertTrue(lines.get(0).equals("Undertakers search the whole village and bring the dead back by morning")
			&& lines.get(1).equals("Weddings held here are blessed: everyone in the village is 15 happier for 3 days, and the couple's first baby comes the next day a bed is free"),
			"the page's lines: " + lines);
		translated(helper, lines);

		// The altar, as drawn: the gold before it, the sanctuary's floor under it; the belfries' bells.
		BlockPos hall = new BlockPos(6300, 70, 6000);
		BlockPos origin = hall.offset(10, 0, 0);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(CATHEDRAL, hall, null, "", 1);
		try {
			manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
			manager.finish(site.id(), 1);
			WonderSite placed = manager.site(site.id()).orElseThrow();
			Map<BlockPos, BlockState> drawn = new HashMap<>();
			for (WonderUpkeep.Block block : WonderUpkeep.blocks(level, placed)) {
				drawn.put(block.pos(), block.state());
			}
			BlockPos altar = origin.offset(19, 2, 49);
			helper.assertTrue(drawn.containsKey(altar.below()) && !drawn.containsKey(altar) && !drawn.containsKey(altar.above())
				&& drawn.getOrDefault(altar.south(), Blocks.AIR.defaultBlockState()).is(Blocks.GOLD_BLOCK),
				"the altar: under " + drawn.get(altar.below()) + ", at " + drawn.get(altar) + ", before " + drawn.get(altar.south()));
			Wonders.Granted granted = new Wonders.Granted(placed, cathedral, powers.get(1), true);
			helper.assertTrue(WeddingsPower.altarOf(granted).equals(Optional.of(altar)), "the altar placed: " + WeddingsPower.altarOf(granted));
			List<BlockPos> bells = WonderBells.bells(level, granted);
			helper.assertTrue(bells.size() >= 3 && bells.stream().allMatch(p -> p.getY() > origin.getY() + 30), "the belfries' bells: " + bells);
			// turned, the altar turns with it
			BlueprintData.Placement turned = new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.CLOCKWISE_180, Mirror.NONE);
			manager.place(site.id(), turned, origin.offset(1, 0, 1));
			Wonders.Granted turnedGrant = new Wonders.Granted(manager.site(site.id()).orElseThrow(), cathedral, powers.get(1), true);
			helper.assertTrue(WeddingsPower.altarOf(turnedGrant).equals(Optional.of(origin.offset(-19, 2, -49))), "turned: " + WeddingsPower.altarOf(turnedGrant));
		} finally {
			manager.remove(site.id());
			WonderBells.forget();
		}
		// the mood reason's line
		helper.assertTrue(Component.translatable("mood.aliveworkplace.reason.wonder_wedding", Wonders.name(CATHEDRAL)).getString()
			.equals("a blessed wedding at the Great Cathedral"), "the mood's reason");
		helper.succeed();
	}
}
