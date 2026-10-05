package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Services;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Services nearby (ROADMAP 34.3, docs/design/M34.md): the six service files, each service found by its worker (at their
 * job site) and by its finished build (an upgrade or a style counts as its base), the 48-block reach and the village-wide
 * market, a teacher who quits counting until the next dawn, a data pack adding a seventh, the list worked out at most
 * once a day and kept through a save. Each test runs in a batch of its own and puts the shared settings back.
 */
public class ServiceGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	/** A day no test's hall has counted yet (each check below uses a later one to make the hall count again). */
	private static final long DAY = 1000;

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	/** A Village Hall at {@link #HALL} (reach 16) with no finished builds near; the shared settings put back when the test ends. */
	private static VillageHallBlockEntity village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			ClassNeeds.services = Services.HOOK;
			Services.load(Services.files(level.getServer().getResourceManager()));
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
	}

	private static BlockPos hall(GameTestHelper helper) {
		return helper.absolutePos(HALL);
	}

	/** A worker of {@code job} whose job site is {@code site} (no AI: nothing changes their job while the test runs). */
	private static Villager worker(GameTestHelper helper, String job, BlockPos site) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		v.setNoAi(true);
		v.setCustomName(Component.literal(job));
		VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(job));
		helper.assertTrue(profession != VillagerProfession.NONE, "the job " + job + " exists");
		v.setVillagerData(v.getVillagerData().setProfession(profession));
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), site));
		return v;
	}

	/** Records a finished build of {@code id} at {@code origin}; returns how to forget it (also forgotten when the test ends). */
	private static Runnable finished(GameTestHelper helper, ResourceLocation id, BlockPos origin) {
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		sites.recordFinished(id, placement, UUID.randomUUID());
		Runnable forget = () -> sites.forgetFinished(placement);
		Leftovers.after(helper, forget);
		return forget;
	}

	/** The six service files load, with the design's ranges. */
	//$ gametest_batch AREA '"serviceFiles"'
	@GameTest(template = AREA, batch = "serviceFiles")
	public void theSixServiceFilesLoad(GameTestHelper helper) {
		village(helper);
		Map<ResourceLocation, Services.Service> all = Services.all();
		for (String id : List.of("chapel", "school", "clinic", "library", "market", "tavern")) {
			Services.Service s = all.get(ours(id));
			helper.assertTrue(s != null, "the " + id + " service loads");
			helper.assertTrue(s.range() == (id.equals("market") ? -1 : 48), id + " reaches " + s.range());
			helper.assertTrue(!Component.translatable("service.aliveworkplace." + id).getString().startsWith("service."), id + " has a name");
		}
		helper.assertTrue(all.get(ours("market")).villageWide(), "the market is village-wide");
		helper.assertTrue(all.get(ours("chapel")).jobs().isEmpty() && all.get(ours("market")).jobs().isEmpty(),
			"the chapel and the market come only from their builds");
		helper.succeed();
	}

	/** Each service with a worker is found by that worker, at their job site: Teacher, Nurse, Scholar, Librarian, Innkeeper. */
	//$ gametest_batch AREA '"serviceWorkers"'
	@GameTest(template = AREA, batch = "serviceWorkers")
	public void eachServiceIsFoundByItsWorker(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos site = helper.absolutePos(new BlockPos(4, 2, 4));
		BlockPos home = site.offset(20, 0, 0);
		helper.assertTrue(Services.reaching(level, hall(helper), home, DAY).isEmpty(), "no services with nobody working");
		Map<String, String> jobs = new TreeMap<>(Map.of(
			"aliveworkplace:teacher", "school",
			"aliveworkplace:nurse", "clinic",
			"aliveworkplace:scholar", "library",
			"minecraft:librarian", "library",
			"aliveworkplace:innkeeper", "tavern"));
		long day = DAY + 1;
		for (Map.Entry<String, String> e : jobs.entrySet()) {
			Villager v = worker(helper, e.getKey(), site);
			Set<ResourceLocation> reaching = Services.reaching(level, hall(helper), home, day++);
			helper.assertTrue(reaching.equals(Set.of(ours(e.getValue()))), "a " + e.getKey() + " gives " + e.getValue() + ", found " + reaching);
			v.discard();
		}
		helper.succeed();
	}

	/** Each of the six is found by its finished build, an upgrade counting as its base (Library II, Inn II). */
	//$ gametest_batch AREA '"serviceBuilds"'
	@GameTest(template = AREA, batch = "serviceBuilds")
	public void eachServiceIsFoundByItsBuild(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(20, 2, 4));
		BlockPos home = origin.offset(-30, 0, 0);
		Map<String, String> builds = new TreeMap<>(Map.of(
			"chapel", "chapel",
			"schoolhouse", "school",
			"school", "school",
			"healing_center", "clinic",
			"clinic", "clinic",
			"library_2", "library",
			"market_square", "market",
			"inn_2", "tavern"));
		long day = DAY + 1;
		for (Map.Entry<String, String> e : builds.entrySet()) {
			Runnable forget = finished(helper, ours(e.getKey()), origin);
			Set<ResourceLocation> reaching = Services.reaching(level, hall(helper), home, day++);
			helper.assertTrue(reaching.equals(Set.of(ours(e.getValue()))), "a finished " + e.getKey() + " gives " + e.getValue() + ", found " + reaching);
			forget.run();
		}
		finished(helper, ours("well"), origin);
		helper.assertTrue(Services.reaching(level, hall(helper), home, day).isEmpty(), "a well is no service");
		helper.succeed();
	}

	/** A Chapel built in Stonework counts as a Chapel. */
	//$ gametest_batch AREA '"serviceStyled"'
	@GameTest(template = AREA, batch = "serviceStyled")
	public void aStoneworkChapelCounts(GameTestHelper helper) {
		village(helper);
		ResourceLocation styled = BlueprintStyles.styled(ours("chapel"), "stonework");
		helper.assertTrue(!styled.equals(ours("chapel")), "the styled id differs from the base: " + styled);
		BlockPos origin = helper.absolutePos(new BlockPos(20, 2, 20));
		finished(helper, styled, origin);
		helper.assertTrue(Services.reaching(helper.getLevel(), hall(helper), origin.offset(10, 0, 10), DAY).contains(ours("chapel")),
			"a Chapel in Stonework is a chapel");
		helper.succeed();
	}

	/** A Chapel 60 blocks from home doesn't reach it (one 40 away does); nor does a Teacher whose desk is 60 away; the market reaches everywhere. */
	//$ gametest_batch AREA '"serviceRange"'
	@GameTest(template = AREA, batch = "serviceRange")
	public void aServiceSixtyBlocksAwayDoesNotReach(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos chapel = helper.absolutePos(new BlockPos(4, 2, 4));
		finished(helper, ours("chapel"), chapel);
		finished(helper, ours("market_square"), helper.absolutePos(new BlockPos(24, 2, 24)));
		worker(helper, "aliveworkplace:teacher", chapel);
		BlockPos far = chapel.offset(60, 0, 0);
		BlockPos near = chapel.offset(40, 0, 0);
		Set<ResourceLocation> atFar = Services.reaching(level, hall(helper), far, DAY);
		Set<ResourceLocation> atNear = Services.reaching(level, hall(helper), near, DAY);
		helper.assertTrue(atFar.equals(Set.of(ours("market"))), "60 blocks away only the market reaches, found " + atFar);
		helper.assertTrue(atNear.equals(Set.of(ours("chapel"), ours("school"), ours("market"))), "40 blocks away all three reach, found " + atNear);
		helper.assertTrue(Services.reaching(level, hall(helper), null, DAY).equals(Set.of(ours("market"))), "with no home, only the village-wide market");
		helper.succeed();
	}

	/** A teacher who quits still counts that day (the list is the day's), and stops counting at the next dawn. */
	//$ gametest_batch AREA '"serviceQuits"'
	@GameTest(template = AREA, batch = "serviceQuits")
	public void aTeacherWhoQuitsStopsCountingAtTheNextDawn(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos desk = helper.absolutePos(new BlockPos(4, 2, 4));
		BlockPos home = desk.offset(5, 0, 5);
		Villager teacher = worker(helper, "aliveworkplace:teacher", desk);
		helper.assertTrue(Services.reaching(level, hall(helper), home, DAY).contains(ours("school")), "the teacher gives a school");
		teacher.setVillagerData(teacher.getVillagerData().setProfession(VillagerProfession.NONE));
		teacher.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		helper.assertTrue(Services.reaching(level, hall(helper), home, DAY).contains(ours("school")), "still a school the day they quit");
		helper.assertTrue(!Services.reaching(level, hall(helper), home, DAY + 1).contains(ours("school")), "no school from the next dawn");
		helper.succeed();
	}

	/** A data pack file adds a seventh service (a bathhouse at a Well), found like ours; switching one off leaves it out. */
	//$ gametest_batch AREA '"serviceDatapack"'
	@GameTest(template = AREA, batch = "serviceDatapack")
	public void aDatapackFileAddsASeventhService(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		Map<ResourceLocation, JsonElement> files = new TreeMap<>(Services.files(level.getServer().getResourceManager()));
		helper.assertTrue(files.size() == 6, "six service files, found " + files.keySet());
		ResourceLocation bathhouse = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "bathhouse");
		files.put(bathhouse, JsonParser.parseString("{\"blueprints\": [\"aliveworkplace:well\"], \"range\": 20, \"icon\": \"minecraft:water_bucket\"}"));
		Services.load(files);
		helper.assertTrue(Services.all().size() == 7 && Services.all().containsKey(bathhouse), "seven services, found " + Services.all().keySet());
		BlockPos well = helper.absolutePos(new BlockPos(20, 2, 20));
		finished(helper, ours("well_2"), well);
		helper.assertTrue(Services.reaching(level, hall(helper), well.offset(10, 0, 0), DAY).equals(Set.of(bathhouse)), "the Well gives a bathhouse");
		helper.assertTrue(Services.reaching(level, hall(helper), well.offset(30, 0, 0), DAY + 1).isEmpty(), "a bathhouse reaches 20 blocks");
		files.put(ours("chapel"), JsonParser.parseString("{\"blueprints\": [\"aliveworkplace:chapel\"], \"enabled\": false}"));
		files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "broken"), JsonParser.parseString("{\"range\": 20}"));
		Services.load(files);
		helper.assertTrue(Services.all().size() == 6 && !Services.all().containsKey(ours("chapel")), "the chapel switched off, the broken file skipped");
		helper.succeed();
	}

	/**
	 * The list is worked out at most once a day, however many homes ask (the {@code services} need for several households),
	 * and kept through a save; a hall saved before 34.3 loads with an empty list.
	 */
	//$ gametest_batch AREA '"serviceOncePerDay"'
	@GameTest(template = AREA, batch = "serviceOncePerDay")
	public void theListIsWorkedOutAtMostOnceADay(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(20, 2, 20));
		finished(helper, ours("library"), origin);
		int before = Services.countings();
		for (int i = 0; i < 20; i++) {
			Services.reaching(level, hall(helper), origin.offset(i, 0, 0), DAY);
		}
		Services.listOf(level, hall(helper), hall, DAY);
		helper.assertTrue(Services.countings() - before == 1, "20 homes asked on one day: worked out " + (Services.countings() - before) + " times");
		for (int i = 0; i < 5; i++) {
			Services.reaching(level, hall(helper), origin, DAY + 1);
		}
		helper.assertTrue(Services.countings() - before == 2, "the next day: once more, found " + (Services.countings() - before));
		helper.assertTrue(hall.servicesDay() == DAY + 1 && hall.services().size() == 1, "the hall keeps the day's list");

		CompoundTag tag = hall.saveWithFullMetadata(level.registryAccess());
		VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(hall(helper), level.getBlockState(hall(helper)), tag, level.registryAccess());
		helper.assertTrue(copy != null && copy.servicesDay() == DAY + 1 && copy.services().equals(hall.services()), "the list is saved with its day");
		tag.remove("services");
		tag.remove("servicesDay");
		VillageHallBlockEntity old = (VillageHallBlockEntity) BlockEntity.loadStatic(hall(helper), level.getBlockState(hall(helper)), tag, level.registryAccess());
		helper.assertTrue(old != null && old.servicesDay() == 0 && old.services().isEmpty(), "a hall saved before 34.3 has no list yet");
		helper.succeed();
	}
}
