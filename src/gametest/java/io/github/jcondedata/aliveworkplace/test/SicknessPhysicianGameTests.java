package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.herb.HerbBlock;
import io.github.jcondedata.aliveworkplace.people.RemedyItem;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PhysicianWork;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Bug B135: the Sickness arc (31.18) counts the village's Physician (32.18) as its healer when there's no Nurse. The
 * Physician is the healer and Doctor Wren isn't sent for; they name the herbs, brew the tonic at their brewing stand and
 * take it round, their own rounds waiting meanwhile; their Remedy only eases the Grey Cough for a day; a Nurse still
 * comes first; with config {@code physicians} off, Doctor Wren comes as before; and the arc carries on after a save and
 * reload with the Physician as its healer.
 */
public class SicknessPhysicianGameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos STAND = new BlockPos(20, 2, 20);
	private static final BlockPos CHEST = new BlockPos(20, 2, 21);
	private static final long DAY = VillageNeeds.DAY;

	/** Small village areas, nothing far built, illness and festivals on, Physicians on, three herbs found east. */
	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean sick = Sickness.ENABLED;
		boolean festivals = Festivals.ENABLED;
		boolean physicians = PhysicianWork.ENABLED;
		SicknessStory.Locator locator = SicknessStory.LOCATOR;
		VillageHalls.RADIUS = 12;
		Arcs.NEAR = 0;
		Sickness.ENABLED = true;
		Festivals.ENABLED = true;
		PhysicianWork.ENABLED = true;
		Map<String, Integer> east = Map.of("sea_lavender", 200, "mirewort", 400, "glowcap", 600);
		SicknessStory.LOCATOR = (level, from, herb) -> {
			Integer d = east.get(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(herb).getPath());
			return d == null ? null : from.offset(d, 0, 0);
		};
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			Sickness.ENABLED = sick;
			Festivals.ENABLED = festivals;
			PhysicianWork.ENABLED = physicians;
			SicknessStory.LOCATOR = locator;
		});
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
	}

	private static Villager villager(GameTestHelper helper, int x, int z, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static List<Villager> village(GameTestHelper helper, int n) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(villager(helper, 11 + (i % 4) * 3, 12 + (i / 4) * 3, "Villager " + (i + 1)));
		}
		return out;
	}

	/** A Physician at a brewing stand, as a player makes one: a glistering melon slice at the stand (Stations). */
	private static Villager physician(GameTestHelper helper) {
		helper.setBlock(STAND, Blocks.BREWING_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager physician = villager(helper, STAND.getX() + 1, STAND.getZ(), "Physician Mara");
		Stations.assign(helper.getLevel(), physician, helper.absolutePos(STAND), ModVillagers.PHYSICIAN);
		helper.assertTrue(PhysicianWork.isPhysician(physician), "no Physician at the brewing stand");
		return physician;
	}

	private static Villager nurse(GameTestHelper helper, BlockPos station) {
		helper.setBlock(station, Blocks.BREWING_STAND);
		Villager nurse = villager(helper, station.getX() + 1, station.getZ(), "Nurse Ada");
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(station), ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE);
		nurse.setVillagerXp(70);
		return nurse;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(HALL.offset(0, 0, 2));
		player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	private static void setDay(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	private static Arcs.Arc arc(GameTestHelper helper) {
		Arcs.Arc arc = Arcs.find("the_sickness").orElse(null);
		helper.assertTrue(arc != null, "the Sickness didn't load");
		return arc;
	}

	private static long grey(List<Villager> vs) {
		return vs.stream().filter(Sickness::isGrey).count();
	}

	private static boolean wrenAbout(ArcState s) {
		return s.mobs.stream().anyMatch(m -> m.key.equals(SicknessStory.WREN)) || SicknessStory.stamp(s, "wren_due") >= 0;
	}

	/**
	 * A village with a Physician and no Nurse: the Physician is the healer and Doctor Wren isn't sent for, nor comes the
	 * next morning. The right-click names the herbs; the hand-in starts the brew at the Physician's brewing stand, and
	 * their own rounds wait ("Brewing the cure" over their head); the Remedy they held goes back to the chest by the stand
	 * when they take the tonic in hand; they give it to every coughing villager and the story ends well.
	 */
	//$ gametest_batch TALL '"thePhysicianIsTheHealer"'
	@GameTest(template = TALL, batch = "thePhysicianIsTheHealer")
	public void thePhysicianIsTheHealer(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		hall(helper);
		List<Villager> people = village(helper, 8);
		Villager physician = physician(helper);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(ModItems.REMEDY, 4));
		ServerPlayer player = player(helper);
		long day = Chronicle.day(level) + 1;
		setDay(level, day, 1000);

		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null && s.started && grey(people) == 3, "the arc didn't start");
		helper.assertTrue(SicknessStory.healer(level, s) == physician, "the healer isn't the Physician");
		helper.assertTrue(!Sickness.isIll(physician), "the Physician caught it");
		helper.assertTrue(!wrenAbout(s), "Doctor Wren was sent for with a Physician in the village");
		setDay(level, day + 1, 1000);
		SicknessStory.tick(level);
		helper.assertTrue(!wrenAbout(s) && SicknessStory.healer(level, s) == physician, "Doctor Wren came, or the healer changed");

		helper.assertTrue(SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, physician) == InteractionResult.SUCCESS,
			"the Physician didn't listen");
		helper.assertTrue(s.chapter == 1 && SicknessStory.herbs(s).size() == 3, "the herbs weren't named: chapter " + s.chapter);
		// Before the brew, the Physician's own rounds go on.
		PhysicianWork work = new PhysicianWork();
		helper.assertTrue(work.tryStart(level, physician, level.getGameTime()), "the Physician's rounds didn't start before the brew");
		work.doStop(level, physician, level.getGameTime());
		helper.assertTrue(RemedyItem.isRemedy(physician.getMainHandItem()), "no Remedy in the Physician's hand");

		for (HerbBlock herb : SicknessStory.herbs(s)) {
			player.getInventory().add(new ItemStack(herb, 3));
		}
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, physician);
		helper.assertTrue(s.chapter == 2 && s.started && SicknessStory.busy(level, physician), "the Physician isn't brewing");
		PhysicianWork waits = new PhysicianWork();
		helper.assertTrue(!waits.tryStart(level, physician, level.getGameTime() + 1000), "the Physician went on their rounds while brewing");

		long from = SicknessStory.stamp(s, "brew_from");
		level.setDayTime(from + SicknessStory.BREW * 2 / 5);
		SicknessStory.tick(level);
		WorkerStatus.Entry status = WorkerStatus.get(physician, level.getGameTime());
		helper.assertTrue(status != null && status.title().getString().equals("Brewing the cure"),
			"the status: " + (status == null ? null : status.title().getString()));

		int remedies = chest.countItem(ModItems.REMEDY);
		level.setDayTime(from + SicknessStory.BREW);
		SicknessStory.tick(level);
		helper.assertTrue(s.flags.containsKey(SicknessStory.BREWED) && physician.getMainHandItem().is(ModItems.THREE_HERB_TONIC),
			"the tonic isn't ready in the Physician's hand");
		helper.assertTrue(chest.countItem(ModItems.REMEDY) == remedies + 1, "the Remedy in hand was lost: " + chest.countItem(ModItems.REMEDY));
		for (int i = 0; i < 30 && Arcs.running(level, hall) == s; i++) {
			SicknessStory.tick(level);
			level.setDayTime(level.getDayTime() + 401);
		}
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc isn't over");
		helper.assertTrue(people.stream().noneMatch(Sickness::isIll), "still coughing: " + grey(people));
		helper.assertTrue(physician.getMainHandItem().isEmpty() && !SicknessStory.busy(level, physician), "the Physician kept the tonic");
		helper.succeed();
	}

	/**
	 * The Physician's Remedy only eases the Grey Cough: the slowness goes for a day and the cough stays, from their visit
	 * or from a player's hand ("eased for a day"); an eased cough isn't on their round until the day passes; an ordinary
	 * illness they still cure. A Nurse in the village comes before a Physician as the healer.
	 */
	//$ gametest_batch TALL '"aPhysiciansRemedyOnlyEasesTheGreyCough"'
	@GameTest(template = TALL, batch = "aPhysiciansRemedyOnlyEasesTheGreyCough")
	public void aPhysiciansRemedyOnlyEasesTheGreyCough(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = village(helper, 8);
		Villager physician = physician(helper);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(ModItems.REMEDY, 2));
		Villager coughing = people.get(0);
		Villager ordinary = people.get(1);
		Sickness.greyCough(level, coughing);
		Sickness.fallIll(level, ordinary);

		Component done = PhysicianWork.visit(level, physician, coughing);
		helper.assertTrue(Sickness.isGrey(coughing) && Sickness.eased(coughing, level.getGameTime()), "the Remedy cured the Grey Cough, or didn't ease it");
		helper.assertTrue(!coughing.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "still slow once eased");
		helper.assertTrue(done.getString().contains("eased") && done.getString().contains("gray cough"), "the status: " + done.getString());
		helper.assertTrue(PhysicianWork.patients(level, physician, helper.absolutePos(STAND)).stream().noneMatch(v -> v == coughing),
			"the eased cough is still on the round");
		PhysicianWork.visit(level, physician, ordinary);
		helper.assertTrue(!Sickness.isIll(ordinary), "the Remedy didn't cure an ordinary illness");

		// From a player's hand: eased, not cured, and the action bar says so.
		Villager other = people.get(2);
		Sickness.greyCough(level, other);
		ServerPlayer player = player(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMEDY));
		helper.assertTrue(RemedyItem.give(player, level, InteractionHand.MAIN_HAND, other) == InteractionResult.SUCCESS, "the Remedy wasn't given");
		helper.assertTrue(Sickness.isGrey(other) && Sickness.eased(other, level.getGameTime()), "a player's Remedy cured the Grey Cough");
		helper.assertTrue(!Sickness.isImmune(other, level.getGameTime()), "a coughing villager was 'kept well'");
		helper.assertTrue(Component.translatable("message.aliveworkplace.remedy.eased", other.getDisplayName()).getString().contains("Three-Herb Tonic"),
			"the eased line");
		// The villager cured of the ordinary illness is kept well: the cough doesn't take them for 10 days.
		helper.assertTrue(!Sickness.greyCough(level, ordinary), "a villager kept well by a Remedy caught the Grey Cough");

		// A Nurse in the village comes first.
		Villager nurse = nurse(helper, new BlockPos(6, 2, 6));
		setDay(level, Chronicle.day(level) + 1, 1000);
		Sickness.recover(level, coughing);
		Sickness.recover(level, other);
		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null && SicknessStory.healer(level, s) == nurse, "the Nurse isn't the healer with a Physician about too");
		helper.succeed();
	}

	/** With config {@code physicians} off, the Physician doesn't count: Doctor Wren is sent for and comes the next morning. */
	//$ gametest_batch TALL '"physiciansSwitchedOffWrenComes"'
	@GameTest(template = TALL, batch = "physiciansSwitchedOffWrenComes")
	public void physiciansSwitchedOffWrenComes(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		village(helper, 8);
		Villager physician = physician(helper);
		PhysicianWork.ENABLED = false;
		long day = Chronicle.day(level) + 1;
		setDay(level, day, 1000);

		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null && SicknessStory.healer(level, s) == null && wrenAbout(s), "Doctor Wren wasn't sent for with Physicians off");
		setDay(level, day + 1, 1000);
		SicknessStory.tick(level);
		Villager wren = SicknessStory.healer(level, s);
		helper.assertTrue(wren != null && wren != physician && wren.getCustomName() != null && wren.getCustomName().getString().equals("Doctor Wren"),
			"no Doctor Wren");
		helper.assertTrue(SicknessStory.onUse(helper.makeMockServerPlayerInLevel(), level, InteractionHand.MAIN_HAND, physician) == InteractionResult.PASS,
			"the switched-off Physician took the healer's click");
		helper.succeed();
	}

	/**
	 * Save and reload mid-arc: with the herbs named, and again with the brew under way, the saved story reloads the same,
	 * the Physician is still the healer (no Doctor Wren), the hand-in goes on and the brew carries on at their stand.
	 */
	//$ gametest_batch TALL '"thePhysicianHealerSurvivesAReload"'
	@GameTest(template = TALL, batch = "thePhysicianHealerSurvivesAReload")
	public void thePhysicianHealerSurvivesAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		village(helper, 8);
		Villager physician = physician(helper);
		ServerPlayer player = player(helper);
		setDay(level, Chronicle.day(level) + 1, 1000);
		ArcState first = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(first != null, "the arc didn't start");
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, physician);
		helper.assertTrue(first.chapter == 1, "the herbs weren't named");

		ArcState s = reload(helper, level, hall);
		helper.assertTrue(s != first && s.chapter == 1 && SicknessStory.herbs(s).size() == 3, "after the reload: chapter " + s.chapter);
		helper.assertTrue(SicknessStory.healer(level, s) == physician && !wrenAbout(s), "the healer after the reload: " + SicknessStory.healer(level, s));
		SicknessStory.tick(level);
		helper.assertTrue(SicknessStory.healer(level, s) == physician && !wrenAbout(s), "a tick after the reload changed the healer");
		for (HerbBlock herb : SicknessStory.herbs(s)) {
			player.getInventory().add(new ItemStack(herb, 3));
		}
		helper.assertTrue(SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, physician) == InteractionResult.SUCCESS && s.chapter == 2,
			"the hand-in after the reload");
		long from = SicknessStory.stamp(s, "brew_from");

		s = reload(helper, level, hall);
		helper.assertTrue(s.chapter == 2 && SicknessStory.stamp(s, "brew_from") == from && SicknessStory.busy(level, physician),
			"the brew after the second reload");
		level.setDayTime(from + SicknessStory.BREW);
		SicknessStory.tick(level);
		helper.assertTrue(s.flags.containsKey(SicknessStory.BREWED) && physician.getMainHandItem().is(ModItems.THREE_HERB_TONIC),
			"the tonic after the reload isn't in the Physician's hand");
		helper.succeed();
	}

	private static ArcState reload(GameTestHelper helper, ServerLevel level, BlockPos hall) {
		CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
		Stories.Data copy = Stories.Data.load(saved, level.registryAccess());
		CompoundTag again = copy.save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.equals(again), "reloaded differs");
		level.getDataStorage().set("aliveworkplace_stories", copy);
		ArcState s = Arcs.running(level, hall);
		helper.assertTrue(s != null, "no arc after the reload");
		return s;
	}
}
