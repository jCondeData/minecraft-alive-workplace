package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.BuildJobs;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableSearch;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * QA (qa-lane-b-1111-0037) of B454, from its Expected line: the Blueprint Table's search and its detail pane always name
 * the same jobs (a player who searches a job the pane shows finds the build, and a job the pane doesn't show never finds
 * it through the jobs), also after a job's config switch changes while the server runs. The earlier tests flip one job
 * (the Firewarden off, on, off; the Physician on, off, on) and read the listing before the pane; these flip every job
 * behind a switch, both ways round, and read the pane first in half the rounds (the order a player gets when the pane
 * was open before the switch changed).
 */
public class QaBlueprintTableJobs1111bGameTests {
	private record Switch(String key, VillagerProfession job, BooleanSupplier get, Consumer<Boolean> set) {
	}

	private static List<Switch> switches() {
		return List.of(
			new Switch("tailors", ModVillagers.TAILOR, () -> io.github.jcondedata.aliveworkplace.tailor.Tailors.ENABLED,
				b -> io.github.jcondedata.aliveworkplace.tailor.Tailors.ENABLED = b),
			new Switch("jewellers", ModVillagers.JEWELLER, () -> io.github.jcondedata.aliveworkplace.jeweller.Jewellers.ENABLED,
				b -> io.github.jcondedata.aliveworkplace.jeweller.Jewellers.ENABLED = b),
			new Switch("gemGrowers", ModVillagers.GEM_GROWER, () -> io.github.jcondedata.aliveworkplace.gem.GemGrowers.ENABLED,
				b -> io.github.jcondedata.aliveworkplace.gem.GemGrowers.ENABLED = b),
			new Switch("printers", ModVillagers.PRINTER, () -> io.github.jcondedata.aliveworkplace.printer.Printers.ENABLED,
				b -> io.github.jcondedata.aliveworkplace.printer.Printers.ENABLED = b),
			new Switch("vintners", ModVillagers.VINTNER, () -> io.github.jcondedata.aliveworkplace.vintner.Vintners.ENABLED,
				b -> io.github.jcondedata.aliveworkplace.vintner.Vintners.ENABLED = b),
			new Switch("physicians", ModVillagers.PHYSICIAN, () -> io.github.jcondedata.aliveworkplace.work.PhysicianWork.ENABLED,
				b -> io.github.jcondedata.aliveworkplace.work.PhysicianWork.ENABLED = b),
			new Switch("wellKeepers", ModVillagers.WELL_KEEPER, () -> io.github.jcondedata.aliveworkplace.work.WellKeeperWork.ENABLED,
				b -> io.github.jcondedata.aliveworkplace.work.WellKeeperWork.ENABLED = b),
			new Switch("firewardens", ModVillagers.FIREWARDEN, () -> io.github.jcondedata.aliveworkplace.work.FirewardenWork.ENABLED,
				b -> io.github.jcondedata.aliveworkplace.work.FirewardenWork.ENABLED = b));
	}

	/** Puts every switch back as it was when the test ends, whatever happens. */
	private static void restoreAfter(GameTestHelper helper, List<Switch> all) {
		List<Boolean> was = new ArrayList<>();
		for (Switch s : all) {
			was.add(s.get().getAsBoolean());
		}
		Leftovers.after(helper, () -> {
			for (int i = 0; i < all.size(); i++) {
				all.get(i).set().accept(was.get(i));
			}
		});
	}

	/**
	 * Each job behind a switch, on then off then on (and off then on then off), the pane read before the listing in one
	 * round and after it in the next: in every round, every build's listing names exactly the jobs its pane names, a
	 * search by each of those jobs (its name and its id) finds it, and a job off is in no pane and no listing.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '400' '"qaTableJobsEverySwitch1111b"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "qaTableJobsEverySwitch1111b")
	public void everySwitchFlippedBothWaysKeepsSearchAndPaneTogether(GameTestHelper helper) {
		List<Switch> all = switches();
		restoreAfter(helper, all);
		for (Switch s : all) {
			s.set().accept(true);
		}
		List<String> problems = new ArrayList<>();
		for (Switch s : all) {
			ResourceLocation job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(s.job());
			for (boolean[] rounds : new boolean[][] {{true, false, true}, {false, true, false}}) {
				boolean paneFirst = rounds[0];
				int[] named = new int[rounds.length];
				for (int r = 0; r < rounds.length; r++) {
					boolean on = rounds[r];
					s.set().accept(on);
					String when = s.key() + " " + (on ? "on" : "off") + (paneFirst ? ", pane first" : ", listing first");
					named[r] = check(helper, job, when, paneFirst, problems);
					paneFirst = !paneFirst;
				}
				// a job can only be named while it can be had (the Physician and the Well Keeper need disasters live,
				// the Firewarden village fires)
				for (int r = 0; r < rounds.length; r++) {
					s.set().accept(rounds[r]);
					boolean can = Stations.available(s.job());
					if (!rounds[r] && named[r] != 0 || rounds[r] && can && named[r] == 0) {
						problems.add(s.key() + " (" + (rounds[r] ? "on" : "off") + "): " + named[r] + " builds' panes name the " + job.getPath());
					}
				}
				s.set().accept(true);
			}
			if (problems.size() > 20) {
				break;
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems, first: " + String.join("; ", problems.subList(0, Math.min(4, problems.size()))));
		helper.succeed();
	}

	/**
	 * Every switch off at once, then all on again, the pane read before the listing each time: for every build and every
	 * job its pane shows, searching that job's name (in capitals, as typed) finds the build; no job the pane leaves out
	 * finds it through the jobs; and the listings' jobs come back as they were before the switches were touched.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '400' '"qaTableJobsAllAtOnce1111b"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "qaTableJobsAllAtOnce1111b")
	public void allSwitchesOffAndOnTogetherComeBackAsTheyWere(GameTestHelper helper) {
		List<Switch> all = switches();
		restoreAfter(helper, all);
		for (Switch s : all) {
			s.set().accept(true);
		}
		List<String> problems = new ArrayList<>();
		java.util.Map<ResourceLocation, Set<ResourceLocation>> before = new java.util.HashMap<>();
		for (TablePayloads.Entry e : TableServer.listing(helper.getLevel().getServer())) {
			before.put(e.id(), new HashSet<>(e.jobs()));
		}
		for (Switch s : all) {
			s.set().accept(false);
		}
		check(helper, null, "all off, pane first", true, problems);
		for (TablePayloads.Entry e : TableServer.listing(helper.getLevel().getServer())) {
			for (Switch s : all) {
				ResourceLocation job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(s.job());
				if (e.jobs().contains(job)) {
					problems.add("all off: " + e.id() + " is listed with the " + job.getPath());
				}
			}
		}
		for (Switch s : all) {
			s.set().accept(true);
		}
		check(helper, null, "all on again, pane first", true, problems);
		for (TablePayloads.Entry e : TableServer.listing(helper.getLevel().getServer())) {
			Set<ResourceLocation> was = before.get(e.id());
			if (was != null && !was.equals(new HashSet<>(e.jobs()))) {
				problems.add("all on again: " + e.id() + " is listed with " + e.jobs() + ", before the switches " + was);
			}
		}
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems, first: " + String.join("; ", problems.subList(0, Math.min(4, problems.size()))));
		helper.succeed();
	}

	/**
	 * One look at the Table: the panes (before or after the listing), then for every listed build its listing's jobs
	 * against its pane's, and the search by each job's name and id. With {@code watched} set, how many panes name it.
	 */
	private static int check(GameTestHelper helper, ResourceLocation watched, String when, boolean paneFirst, List<String> problems) {
		java.util.Map<ResourceLocation, Set<ResourceLocation>> panes = new java.util.HashMap<>();
		List<TablePayloads.Entry> all;
		if (paneFirst) {
			for (TablePayloads.Entry e : TableServer.listing(helper.getLevel().getServer())) {
				pane(helper, e.id()).ifPresent(p -> panes.put(e.id(), p));
			}
			// the panes were read for builds listed a moment ago; the listing now is the one the player searches
			all = TableServer.listing(helper.getLevel().getServer());
		} else {
			all = TableServer.listing(helper.getLevel().getServer());
			for (TablePayloads.Entry e : all) {
				pane(helper, e.id()).ifPresent(p -> panes.put(e.id(), p));
			}
		}
		int named = 0;
		for (TablePayloads.Entry e : all) {
			Set<ResourceLocation> pane = panes.get(e.id());
			if (e.hidden() || pane == null) {
				continue;
			}
			named += watched != null && pane.contains(watched) ? 1 : 0;
			if (!pane.equals(new HashSet<>(e.jobs()))) {
				problems.add(when + ": " + e.id() + "'s pane names " + pane + ", its listing " + e.jobs());
			}
			TablePayloads.Entry noJobs = new TablePayloads.Entry(e.id(), e.sizeX(), e.sizeY(), e.sizeZ(), e.blocks(), e.state(), e.hint(), List.of());
			for (ResourceLocation job : pane) {
				for (String q : List.of(BuildJobs.name(job).getString().toUpperCase(Locale.ROOT), job.getPath())) {
					if (!TableSearch.filter(all, q).contains(e)) {
						problems.add(when + ": \"" + q + "\" misses " + e.id() + ", whose pane names it");
					}
				}
			}
			if (watched != null && !pane.contains(watched)) {
				String q = BuildJobs.name(watched).getString();
				if (TableSearch.matches(e, q) && !TableSearch.matches(noJobs, q)) {
					problems.add(when + ": \"" + q + "\" finds " + e.id() + " by a job its pane doesn't name");
				}
			}
		}
		return named;
	}

	private static Optional<Set<ResourceLocation>> pane(GameTestHelper helper, ResourceLocation id) {
		Optional<TablePayloads.Details> details = TableServer.details(helper.getLevel(), null, id);
		return details.map(d -> {
			Set<ResourceLocation> out = new HashSet<>();
			d.jobs().forEach(b -> out.addAll(b.jobs()));
			return out;
		});
	}
}
