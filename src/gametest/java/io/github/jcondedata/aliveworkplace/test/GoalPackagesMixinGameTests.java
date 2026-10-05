package io.github.jcondedata.aliveworkplace.test;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Gated;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * {@code VillagerGoalPackagesMixin}, the switchboard that gives every job its work (found by the full check's inventory,
 * ROADMAP 21.2): each of our professions gets its own package, each upgraded vanilla job gets our work first with
 * vanilla's routine gated behind it, and guards get their raid, pre-raid and combat packages. A wrong or missing branch
 * there leaves a worker standing at his block doing vanilla's nothing, which no feature test of another job would see.
 */
public class GoalPackagesMixinGameTests implements FabricGameTest {
	private static final float SPEED = 0.5F;

	private static String signature(ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super Villager>>> pkg) {
		List<String> out = new ArrayList<>();
		for (Pair<Integer, ? extends BehaviorControl<? super Villager>> entry : pkg) {
			out.add(entry.getFirst() + ":" + entry.getSecond().getClass().getName().replaceAll("\\$\\$Lambda.*", "$$Lambda"));
		}
		return String.join(", ", out);
	}

	private static Component none(Villager v) {
		return Component.empty();
	}

	/** Each of our professions gets exactly the package its job's class builds, never vanilla's. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachOfOurJobsGetsItsOwnWorkPackage(GameTestHelper helper) {
		Map<VillagerProfession, Supplier<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>>> jobs = new LinkedHashMap<>();
		jobs.put(ModVillagers.BUILDER, () -> io.github.jcondedata.aliveworkplace.build.BuilderPackages.work(SPEED));
		jobs.put(ModVillagers.MINER, () -> io.github.jcondedata.aliveworkplace.mine.MinerPackages.work(SPEED));
		jobs.put(ModVillagers.LUMBERJACK, () -> io.github.jcondedata.aliveworkplace.wood.LumberjackPackages.work(SPEED));
		jobs.put(ModVillagers.BALL_SMITH, () -> io.github.jcondedata.aliveworkplace.smith.BallSmithPackages.work(SPEED));
		jobs.put(ModVillagers.COMPOSTER, () -> io.github.jcondedata.aliveworkplace.compost.CompostWork.packages(SPEED));
		jobs.put(ModVillagers.CAMP_COOK, () -> io.github.jcondedata.aliveworkplace.camp.CampCookWork.packages(SPEED));
		jobs.put(ModVillagers.HABITAT_KEEPER, () -> io.github.jcondedata.aliveworkplace.habitat.HabitatKeeperWork.packages(SPEED));
		jobs.put(ModVillagers.BERRY_BREEDER, () -> io.github.jcondedata.aliveworkplace.berry.BerryBreederWork.packages(SPEED));
		jobs.put(ModVillagers.NETHERWORKER, () -> io.github.jcondedata.aliveworkplace.nether.NetherworkerWork.packages(SPEED));
		jobs.put(ModVillagers.TINKERER, () -> io.github.jcondedata.aliveworkplace.craft.CarpenterPackages.tinkerer(SPEED));
		jobs.put(ModVillagers.SIFTER, () -> io.github.jcondedata.aliveworkplace.sift.SifterPackages.work(SPEED));
		jobs.put(ModVillagers.SCHOLAR, () -> io.github.jcondedata.aliveworkplace.research.ScholarPackages.work(SPEED));
		jobs.put(ModVillagers.UNDERTAKER, () -> io.github.jcondedata.aliveworkplace.grave.UndertakerPackages.work(SPEED));
		jobs.put(ModVillagers.INNKEEPER, () -> io.github.jcondedata.aliveworkplace.inn.InnkeeperPackages.work(SPEED));
		jobs.put(ModVillagers.TEACHER, () -> io.github.jcondedata.aliveworkplace.school.TeacherPackages.work(SPEED));
		jobs.put(ModVillagers.RANCHER, () -> io.github.jcondedata.aliveworkplace.ranch.RancherPackages.work(SPEED));
		jobs.put(ModVillagers.FLORIST, () -> io.github.jcondedata.aliveworkplace.flower.FloristPackages.work(SPEED));
		jobs.put(ModVillagers.BEEKEEPER, () -> io.github.jcondedata.aliveworkplace.bee.BeekeeperPackages.work(SPEED));
		jobs.put(ModVillagers.ORCHARD_KEEPER, () -> io.github.jcondedata.aliveworkplace.orchard.OrchardPackages.work(SPEED));
		jobs.put(ModVillagers.FOSSIL_SCIENTIST, () -> io.github.jcondedata.aliveworkplace.fossil.FossilPackages.work(SPEED));
		jobs.put(ModVillagers.CHEF, () -> io.github.jcondedata.aliveworkplace.craft.CarpenterPackages.chef(SPEED));
		jobs.put(ModVillagers.CARPENTER, () -> io.github.jcondedata.aliveworkplace.craft.CarpenterPackages.work(SPEED));
		jobs.put(ModVillagers.PORTER, () -> io.github.jcondedata.aliveworkplace.store.PorterPackages.work(SPEED));
		jobs.put(ModVillagers.POSTMAN, () -> io.github.jcondedata.aliveworkplace.mail.PostmanPackages.work(SPEED));
		jobs.put(ModVillagers.GUARD, () -> io.github.jcondedata.aliveworkplace.guard.GuardPackages.work(SPEED));
		jobs.put(ModVillagers.NURSE, () -> io.github.jcondedata.aliveworkplace.nurse.NursePackages.work(SPEED));
		jobs.put(ModVillagers.SHOPKEEPER, () -> io.github.jcondedata.aliveworkplace.shop.ShopkeeperPackages.work(SPEED));
		jobs.put(ModVillagers.FERRYMAN, () -> io.github.jcondedata.aliveworkplace.travel.FerrymanPackages.work(SPEED));
		jobs.put(ModVillagers.BARD, () -> io.github.jcondedata.aliveworkplace.bard.BardPackages.work(SPEED));
		jobs.put(ModVillagers.TRAINER, () -> io.github.jcondedata.aliveworkplace.trainer.TrainerPackages.work(SPEED));
		jobs.put(ModVillagers.TRAINER_LEADER, () -> io.github.jcondedata.aliveworkplace.trainer.TrainerPackages.work(SPEED));
		jobs.put(ModVillagers.TUTOR, () -> io.github.jcondedata.aliveworkplace.work.DeskPackages.work(SPEED, GoalPackagesMixinGameTests::none, GoalPackagesMixinGameTests::none));
		jobs.put(ModVillagers.POKEMON_TRADER, () -> io.github.jcondedata.aliveworkplace.work.DeskPackages.work(SPEED, GoalPackagesMixinGameTests::none, GoalPackagesMixinGameTests::none));

		String vanilla = signature(VillagerGoalPackages.getWorkPackage(VillagerProfession.NITWIT, SPEED));
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<VillagerProfession, Supplier<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>>> job : jobs.entrySet()) {
			String got = signature(VillagerGoalPackages.getWorkPackage(job.getKey(), SPEED));
			String expected = signature(job.getValue().get());
			if (got.equals(vanilla)) {
				wrong.add(job.getKey().name() + " gets vanilla's work");
			} else if (!got.equals(expected)) {
				wrong.add(job.getKey().name() + " gets [" + got + "], not [" + expected + "]");
			}
		}
		// Every worker profession we register has a branch (a new job without one would stand idle).
		for (VillagerProfession profession : net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION) {
			if (ModVillagers.isWorker(profession) && !jobs.containsKey(profession)) {
				wrong.add(profession.name() + " is one of our jobs but this test doesn't know its package");
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * The vanilla jobs we upgrade: our work runs first (priority 0) and vanilla's routine waits behind a gate, except
	 * the always-run schedule update (priority 99 and later).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void upgradedVanillaJobsPutTheirWorkFirstAndGateVanillas(GameTestHelper helper) {
		Object[][] cases = {
			{VillagerProfession.MASON, new Class<?>[] {io.github.jcondedata.aliveworkplace.craft.MasonWork.class}},
			{VillagerProfession.ARMORER, new Class<?>[] {io.github.jcondedata.aliveworkplace.smelt.SmelterWork.class}},
			{VillagerProfession.TOOLSMITH, new Class<?>[] {io.github.jcondedata.aliveworkplace.craft.ToolsmithWork.class}},
			{VillagerProfession.WEAPONSMITH, new Class<?>[] {io.github.jcondedata.aliveworkplace.mend.MendingWork.class,
				io.github.jcondedata.aliveworkplace.craft.WeaponsmithWork.class}},
			{VillagerProfession.FLETCHER, new Class<?>[] {io.github.jcondedata.aliveworkplace.craft.FletcherWork.class}},
			{VillagerProfession.SHEPHERD, new Class<?>[] {io.github.jcondedata.aliveworkplace.ranch.ShepherdWork.class}},
			{VillagerProfession.BUTCHER, new Class<?>[] {io.github.jcondedata.aliveworkplace.ranch.HerderWork.class}},
			{VillagerProfession.LEATHERWORKER, new Class<?>[] {io.github.jcondedata.aliveworkplace.craft.DyerWork.class}},
			{VillagerProfession.CLERIC, new Class<?>[] {io.github.jcondedata.aliveworkplace.brew.AlchemistWork.class}},
			{VillagerProfession.LIBRARIAN, new Class<?>[] {io.github.jcondedata.aliveworkplace.scribe.EnchantWork.class,
				io.github.jcondedata.aliveworkplace.craft.ScribeWork.class}},
			{VillagerProfession.CARTOGRAPHER, new Class<?>[] {io.github.jcondedata.aliveworkplace.explore.ExplorerWork.class}},
			{VillagerProfession.FISHERMAN, new Class<?>[] {io.github.jcondedata.aliveworkplace.fish.FisherWork.class}},
		};
		int vanillaSize = VillagerGoalPackages.getWorkPackage(VillagerProfession.NITWIT, SPEED).size();
		List<String> wrong = new ArrayList<>();
		for (Object[] c : cases) {
			VillagerProfession profession = (VillagerProfession) c[0];
			Class<?>[] ours = (Class<?>[]) c[1];
			ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> pkg = VillagerGoalPackages.getWorkPackage(profession, SPEED);
			if (pkg.size() != vanillaSize + ours.length) {
				wrong.add(profession.name() + " has " + pkg.size() + " behaviours, not vanilla's " + vanillaSize + " + " + ours.length);
				continue;
			}
			for (int i = 0; i < ours.length; i++) {
				Pair<Integer, ? extends BehaviorControl<? super Villager>> entry = pkg.get(i);
				if (entry.getFirst() != 0 || entry.getSecond().getClass() != ours[i]) {
					wrong.add(profession.name() + "'s behaviour " + i + " is " + entry.getFirst() + ":"
						+ entry.getSecond().getClass().getSimpleName() + ", not 0:" + ours[i].getSimpleName());
				}
			}
			int gated = 0;
			for (int i = ours.length; i < pkg.size(); i++) {
				Pair<Integer, ? extends BehaviorControl<? super Villager>> entry = pkg.get(i);
				if (entry.getSecond() instanceof Gated<?>) {
					gated++;
				} else if (entry.getFirst() < 99) {
					wrong.add(profession.name() + "'s vanilla " + entry.getSecond().getClass().getSimpleName()
						+ " (priority " + entry.getFirst() + ") runs ungated");
				}
			}
			if (gated == 0) {
				wrong.add(profession.name() + " gates none of vanilla's routine");
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** Guards patrol in a raid and before it instead of hiding, and their combat sits in CORE; other villagers keep vanilla's. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void guardsGetTheirRaidAndCombatPackages(GameTestHelper helper) {
		String raid = signature(io.github.jcondedata.aliveworkplace.guard.GuardPackages.raid(SPEED, Activity.RAID));
		String preRaid = signature(io.github.jcondedata.aliveworkplace.guard.GuardPackages.raid(SPEED, Activity.PRE_RAID));
		String guardCore = signature(io.github.jcondedata.aliveworkplace.guard.GuardPackages.core(
			VillagerGoalPackages.getCorePackage(VillagerProfession.FARMER, SPEED)));
		String[][] checks = {
			{"the guard's raid package", signature(VillagerGoalPackages.getRaidPackage(ModVillagers.GUARD, SPEED)), raid},
			{"the guard's pre-raid package", signature(VillagerGoalPackages.getPreRaidPackage(ModVillagers.GUARD, SPEED)), preRaid},
			{"the guard's core package", signature(VillagerGoalPackages.getCorePackage(ModVillagers.GUARD, SPEED)), guardCore},
		};
		List<String> wrong = new ArrayList<>();
		for (String[] check : checks) {
			if (!check[1].equals(check[2])) {
				wrong.add(check[0] + " is [" + check[1] + "], not [" + check[2] + "]");
			}
		}
		if (signature(VillagerGoalPackages.getRaidPackage(VillagerProfession.FARMER, SPEED)).equals(raid)) {
			wrong.add("a farmer patrols in a raid like a guard");
		}
		if (signature(VillagerGoalPackages.getPreRaidPackage(ModVillagers.BUILDER, SPEED)).equals(preRaid)) {
			wrong.add("a builder patrols before a raid like a guard");
		}
		if (signature(VillagerGoalPackages.getCorePackage(VillagerProfession.FARMER, SPEED)).equals(guardCore)) {
			wrong.add("a farmer has the guard's combat");
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
