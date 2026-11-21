package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.wonder.StadiumDays;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-d-1121-1309) for B644, from its spec: a Stadium Day names a visitor from a linked village (here a caravan
 * partner 1,200 blocks off) without loading that village's chunk on the server thread. The name falls back in order:
 * the hall's own name where its chunk is loaded, else the realm record's name, else the caravans' list's, else the
 * made-up name. Each level gets names that differ (the hall "Hallton", the realm record "Realmby", the list
 * "Listford"), so the visitor's name shows which one was read, and the far chunk must still be unloaded after the day's
 * visitors arrive. The named visitor keeps that name and its home through a save and reload.
 */
public class QaStadiumNames1121dCompatTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 3);
	private static final BlockPos WEST_POST = new BlockPos(5, 2, 15);
	private static final BlockPos EAST_POST = new BlockPos(25, 2, 15);
	private static final UUID MARA = UUID.nameUUIDFromBytes("qa-mara-1121d".getBytes());

	/** A finished Stadium round the test's hall, its spots in the area (as QaStadiumVisitorsCompatTests stages it). */
	private static WonderSite stadium(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		helper.assertTrue(Wonders.loaded().containsKey(StadiumDays.STADIUM), "the Stadium's file didn't load with Cobblemon");
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 24;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(WEST_POST, ModBlocks.TRAINING_POST);
		helper.setBlock(EAST_POST, ModBlocks.TRAINING_POST);
		BlockPos hall = helper.absolutePos(HALL);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(StadiumDays.STADIUM, hall, UUID.randomUUID(), "Jesse", 3);
		manager.finish(site.id(), 9);
		List<BlockPos> seats = new java.util.ArrayList<>();
		for (int x = 9; x <= 21; x++) {
			helper.setBlock(new BlockPos(x, 2, 24), Blocks.RED_CARPET);
			seats.add(helper.absolutePos(new BlockPos(x, 2, 24)));
		}
		StadiumDays.setSpotsForTest(site.id(), new StadiumDays.Spots(helper.absolutePos(new BlockPos(15, 2, 15)),
			List.of(helper.absolutePos(new BlockPos(6, 2, 15)), helper.absolutePos(new BlockPos(24, 2, 15)),
				helper.absolutePos(new BlockPos(6, 2, 16)), helper.absolutePos(new BlockPos(24, 2, 14))),
			List.of(helper.absolutePos(WEST_POST), helper.absolutePos(EAST_POST)), List.copyOf(seats), helper.absolutePos(new BlockPos(15, 2, 28))));
		Wonders.forgetPowers();
		PartnerShowsCompatTests.after(helper, () -> {
			StadiumDays.setSpotsForTest(site.id(), null);
			manager.remove(site.id());
			Wonders.forgetPowers();
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			Moods.forget();
		});
		return manager.site(site.id()).orElseThrow();
	}

	/**
	 * The partner village 1,200 blocks east: its hall named {@code hallName}, the caravans' list naming it
	 * {@code listName}, the realm record {@code realmName} (none when null); its chunk kept loaded when {@code keepLoaded}.
	 */
	private static BlockPos partner(GameTestHelper helper, WonderSite site, int offset, String hallName, String listName, String realmName,
			boolean keepLoaded) {
		ServerLevel level = helper.getLevel();
		BlockPos far = helper.absolutePos(HALL).offset(offset, 0, 0);
		level.setBlock(far, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		if (!(level.getBlockEntity(far) instanceof VillageHallBlockEntity hall)) {
			throw new GameTestAssertException("no hall at the partner village");
		}
		hall.setCustomName(Component.literal(hallName));
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(far, Component.literal(listName), List.of());
		data.toggleRoute(site.hall(), far, 10);
		data.setLeader(far, new Caravans.Leader(MARA, "Mara", 4, 150, false));
		RealmData realms = RealmData.get(level.getServer());
		GlobalPos at = GlobalPos.of(level.dimension(), far);
		if (realmName != null) {
			realms.putKnown(new RealmData.Known(at, Component.literal(realmName), Optional.empty(), "", "hamlet", 8, 50, level.getGameTime()));
		} else {
			realms.forgetKnown(at);
		}
		int cx = far.getX() >> 4;
		int cz = far.getZ() >> 4;
		if (keepLoaded) {
			level.setChunkForced(cx, cz, true);
		}
		PartnerShowsCompatTests.after(helper, () -> {
			level.setChunkForced(cx, cz, false);
			realms.forgetKnown(at);
			data.remove(far);
			level.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
		});
		return far;
	}

	/** Waits for the far chunk to unload (or, kept loaded, goes on at once), then holds the day and checks the visitor. */
	private static void day(GameTestHelper helper, WonderSite site, BlockPos far, boolean keepLoaded, int tries,
			Function<ServerLevel, String> expected, boolean reload) {
		ServerLevel level = helper.getLevel();
		if (!keepLoaded && level.isLoaded(far) && tries > 0) {
			helper.runAfterDelay(5, () -> day(helper, site, far, false, tries - 1, expected, reload));
			return;
		}
		helper.assertTrue(level.isLoaded(far) == keepLoaded, "setup: the partner's chunk is " + (level.isLoaded(far) ? "loaded" : "unloaded"));
		helper.assertTrue(Wonders.linked(level, site.hall()).stream().anyMatch(g -> g.pos().equals(far)), "setup: the partner isn't linked");
		long today = Chronicle.day(level);
		long day = today % 2 == 0 ? today : today + 1;
		StadiumDays.Spots spots = StadiumDays.spots(level, site).orElseThrow();
		StadiumDays.tick(level, day, StadiumDays.AFTERNOON);
		boolean loadedNow = level.isLoaded(far);
		List<Villager> visitors = StadiumDays.visitors(level, site, spots);
		List<String> names = visitors.stream().map(v -> v.getDisplayName().getString()).toList();
		List<Villager> fromPartner = visitors.stream().filter(v -> StadiumDays.visitor(v).home().equals(Optional.of(far))).toList();
		helper.assertTrue(fromPartner.size() == 1, "not one visitor from the partner village: " + names);
		Villager v = fromPartner.get(0);
		String want = "Mara of " + expected.apply(level);
		helper.assertValueEqual(v.getDisplayName().getString(), want, "the partner's visitor's name");
		if (!keepLoaded) {
			helper.assertFalse(loadedNow, "the Stadium Day loaded the far partner's chunk to name its visitor (B644); visitors " + names);
		}
		if (reload) {
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(v.save(tag), "the visitor wasn't saved");
			v.discard();
			Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
			if (!(back instanceof Villager again)) {
				throw new GameTestAssertException("the visitor didn't load as a villager: " + back);
			}
			level.addFreshEntity(again);
			helper.assertValueEqual(again.getDisplayName().getString(), want, "the visitor's name after a reload");
			StadiumDays.Visitor kept = ModAttachments.STADIUM_VISITOR.get(again);
			helper.assertTrue(kept != null && kept.home().equals(Optional.of(far)), "the reloaded visitor lost its home: " + kept);
			helper.assertFalse(!keepLoaded && level.isLoaded(far), "reloading the visitor loaded the far partner's chunk");
		}
		StadiumDays.tick(level, day, StadiumDays.DUSK);
		helper.succeed();
	}

	/** Level 1: the partner's chunk is loaded, so its hall's own name is read, over the realm record and the list. */
	//$ gametest_ticks_batch HUGE '1200' '"qaStadiumNames1121dLoaded"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaStadiumNames1121dLoaded")
	public void aLoadedHallGivesItsOwnName(GameTestHelper helper) {
		WonderSite site = stadium(helper);
		BlockPos far = partner(helper, site, 1200, "Hallton", "Listford", "Realmby", true);
		// a loaded village sends its live Trainer (Cups.delegate), so Mara stands by her hall
		ServerLevel level = helper.getLevel();
		Villager mara = EntityType.VILLAGER.create(level);
		if (mara == null) {
			throw new GameTestAssertException("no villager");
		}
		mara.moveTo(far.getX() + 2.5, far.getY(), far.getZ() + 0.5, 0, 0);
		mara.setVillagerData(mara.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAINER).setLevel(4));
		mara.setCustomName(Component.literal("Mara"));
		mara.setPersistenceRequired();
		mara.setNoAi(true);
		level.addFreshEntity(mara);
		PartnerShowsCompatTests.after(helper, mara::discard);
		whenMaraIsSeen(helper, site, far, 200);
	}

	/** The forced chunk's entities take a few ticks to be seen; the day is held once Mara is the village's live Trainer. */
	private static void whenMaraIsSeen(GameTestHelper helper, WonderSite site, BlockPos far, int tries) {
		if (io.github.jcondedata.aliveworkplace.cup.Cups.liveDelegate(helper.getLevel(), far) == null) {
			helper.assertTrue(tries > 0, "setup: Mara by the loaded hall is never seen as its Trainer");
			helper.runAfterDelay(5, () -> whenMaraIsSeen(helper, site, far, tries - 1));
			return;
		}
		day(helper, site, far, true, 0, l -> "Hallton", false);
	}

	/** Level 2: unloaded, the realm record's name is used over the list's (and the hall isn't read); kept through a reload. */
	//$ gametest_ticks_batch HUGE '1200' '"qaStadiumNames1121dRealm"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaStadiumNames1121dRealm")
	public void anUnloadedHallIsNamedByTheRealmRecord(GameTestHelper helper) {
		WonderSite site = stadium(helper);
		BlockPos far = partner(helper, site, 1200, "Hallton", "Listford", "Realmby", false);
		day(helper, site, far, false, 200, level -> "Realmby", true);
	}

	/** Level 3: unloaded, a blank realm record name doesn't count, so the caravans' list names it. */
	//$ gametest_ticks_batch HUGE '1200' '"qaStadiumNames1121dList"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaStadiumNames1121dList")
	public void aBlankRealmRecordFallsBackToTheCaravansList(GameTestHelper helper) {
		WonderSite site = stadium(helper);
		BlockPos far = partner(helper, site, 1200, "Hallton", "Listford", " ", false);
		day(helper, site, far, false, 200, level -> "Listford", false);
	}

	/** Level 4: unloaded, no realm record and a blank name on the list: the made-up name for that spot. */
	//$ gametest_ticks_batch HUGE '1200' '"qaStadiumNames1121dMadeUp"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaStadiumNames1121dMadeUp")
	public void noNameAnywhereGivesTheMadeUpName(GameTestHelper helper) {
		WonderSite site = stadium(helper);
		BlockPos far = partner(helper, site, 1200, "Hallton", "", null, false);
		day(helper, site, far, false, 200, level -> VillageHalls.madeUpName(far).getString(), false);
	}
}
