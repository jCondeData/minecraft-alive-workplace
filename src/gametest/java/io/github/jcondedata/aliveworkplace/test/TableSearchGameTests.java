package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableSearch;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Blueprint Table's search box (36.10): the library the server sends, narrowed by name, id and job, ignoring case,
 * with a build's levels kept under it; an empty box lists everything and a search that finds nothing says so.
 */
public class TableSearchGameTests implements FabricGameTest {
	private static final ResourceLocation INN = AliveWorkplace.id("inn");
	private static final ResourceLocation FISHERS_HUT = AliveWorkplace.id("fishers_hut");
	private static final ResourceLocation FISHERMAN = ResourceLocation.withDefaultNamespace("fisherman");

	private static List<TablePayloads.Entry> library(GameTestHelper helper) {
		return TableServer.listing(helper.getLevel().getServer());
	}

	private static Set<ResourceLocation> ids(List<TablePayloads.Entry> entries) {
		Set<ResourceLocation> out = new HashSet<>();
		entries.forEach(e -> out.add(e.id()));
		return out;
	}

	/** Every level of a listed build is listed right after the build (or another of its levels). */
	private static void assertLevelsUnderTheirBuild(GameTestHelper helper, List<TablePayloads.Entry> shown, List<TablePayloads.Entry> all) {
		Set<ResourceLocation> listed = ids(all);
		Set<ResourceLocation> seen = new HashSet<>();
		for (TablePayloads.Entry e : shown) {
			BlueprintUpgrades.baseOf(e.id()).filter(listed::contains).ifPresent(base ->
				helper.assertTrue(seen.contains(base), e.id() + " is listed without " + base + " above it"));
			seen.add(e.id());
		}
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void searchFindsABuildByNameWithItsLevelsUnderIt(GameTestHelper helper) {
		List<TablePayloads.Entry> all = library(helper);
		List<TablePayloads.Entry> shown = TableSearch.filter(all, "inn");
		Set<ResourceLocation> found = ids(shown);
		helper.assertTrue(found.contains(INN), "\"inn\" should find the Inn: " + found);
		helper.assertTrue(found.contains(AliveWorkplace.id("inn_2")) && found.contains(AliveWorkplace.id("inn_3")),
			"\"inn\" should keep the Inn's levels under it: " + found);
		helper.assertFalse(found.contains(StarterBlueprints.STARTER_COTTAGE.id()), "\"inn\" listed the Starter Cottage");
		helper.assertTrue(shown.size() < all.size(), "\"inn\" didn't narrow the list (" + shown.size() + " of " + all.size() + ")");
		// Ignoring case.
		helper.assertTrue(TableSearch.filter(all, "  INN ").equals(shown), "\"INN\" should list what \"inn\" does");
		int inn = shown.indexOf(all.stream().filter(e -> e.id().equals(INN)).findFirst().orElseThrow());
		helper.assertTrue(shown.get(inn + 1).id().equals(AliveWorkplace.id("inn_2")) && shown.get(inn + 2).id().equals(AliveWorkplace.id("inn_3")),
			"the Inn's levels should sit right under it");
		// "iii" names only a third level, yet each build comes with it.
		List<TablePayloads.Entry> third = TableSearch.filter(all, "iii");
		helper.assertTrue(ids(third).contains(INN) && ids(third).contains(AliveWorkplace.id("inn_3")), "\"iii\" should list the Inn III under the Inn: " + ids(third));
		assertLevelsUnderTheirBuild(helper, third, all);
		assertLevelsUnderTheirBuild(helper, shown, all);
		// Every build listed matches, or is a level of one that does.
		Set<ResourceLocation> listed = ids(all);
		for (TablePayloads.Entry e : third) {
			boolean family = all.stream().filter(o -> TableSearch.matches(o, "iii")).anyMatch(o -> sameBuild(o.id(), e.id(), listed));
			helper.assertTrue(family, e.id() + " was listed for \"iii\" but neither it nor its levels match");
		}
		helper.succeed();
	}

	private static boolean sameBuild(ResourceLocation a, ResourceLocation b, Set<ResourceLocation> listed) {
		return root(a, listed).equals(root(b, listed));
	}

	private static ResourceLocation root(ResourceLocation id, Set<ResourceLocation> listed) {
		ResourceLocation at = id;
		while (BlueprintUpgrades.baseOf(at).filter(listed::contains).isPresent()) {
			at = BlueprintUpgrades.baseOf(at).get();
		}
		return at;
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void searchFindsABuildById(GameTestHelper helper) {
		List<TablePayloads.Entry> all = library(helper);
		// The Fisher's Hut is called "Fisher's Hut": "fishers_hut" is only in its id.
		helper.assertFalse(Blueprints.displayName(FISHERS_HUT).getString().toLowerCase(Locale.ROOT).contains("fishers_hut"), "the test's id is in the name");
		Set<ResourceLocation> found = ids(TableSearch.filter(all, "fishers_hut"));
		helper.assertTrue(found.contains(FISHERS_HUT) && found.contains(AliveWorkplace.id("fishers_hut_2")), "\"fishers_hut\" should find the Fisher's Hut by id: " + found);
		Set<ResourceLocation> full = ids(TableSearch.filter(all, "aliveworkplace:fishers_hut"));
		helper.assertTrue(full.contains(FISHERS_HUT), "the full id should find it too: " + full);
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void searchFindsBuildsByTheJobsTheyBring(GameTestHelper helper) {
		List<TablePayloads.Entry> all = library(helper);
		// The listing carries each build's jobs, worked out from its blocks (the Fisher's Hut has a barrel).
		TablePayloads.Entry hut = all.stream().filter(e -> e.id().equals(FISHERS_HUT)).findFirst().orElseThrow();
		helper.assertTrue(hut.jobs().contains(FISHERMAN), "the Fisher's Hut's listing should bring a fisherman: " + hut.jobs());
		helper.assertFalse(Blueprints.displayName(FISHERS_HUT).getString().toLowerCase(Locale.ROOT).contains("fisherman"), "the job is in the name");
		List<TablePayloads.Entry> fishers = TableSearch.filter(all, "Fisherman");
		helper.assertTrue(ids(fishers).contains(FISHERS_HUT), "\"Fisherman\" should find the Fisher's Hut by its job: " + ids(fishers));
		for (TablePayloads.Entry e : all) {
			if (e.jobs().contains(FISHERMAN)) {
				helper.assertTrue(fishers.contains(e), "\"Fisherman\" missed " + e.id() + ", which brings a fisherman");
			}
		}
		// "smith" finds every forge: each build that brings a toolsmith, weaponsmith or armorer's job named "...smith".
		List<TablePayloads.Entry> smiths = TableSearch.filter(all, "smith");
		int forges = 0;
		for (TablePayloads.Entry e : all) {
			boolean smith = e.jobs().stream().anyMatch(j -> j.getPath().endsWith("smith"));
			if (smith) {
				forges++;
				helper.assertTrue(smiths.contains(e), "\"smith\" missed " + e.id() + ", which brings " + e.jobs());
			}
		}
		helper.assertTrue(forges > 0, "no build in the library brings a smith");
		helper.assertTrue(smiths.size() < all.size(), "\"smith\" didn't narrow the list");
		// The jobs travel to the player's screen with the listing.
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		TablePayloads.Entry.CODEC.encode(buf, hut);
		TablePayloads.Entry back = TablePayloads.Entry.CODEC.decode(buf);
		helper.assertTrue(back.equals(hut), "the entry's jobs didn't survive the packet: " + back.jobs());
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSearchThatFindsNothingSaysSo(GameTestHelper helper) {
		List<TablePayloads.Entry> all = library(helper);
		helper.assertTrue(TableSearch.filter(all, "zzqxj").isEmpty(), "\"zzqxj\" found something");
		// Every word must match: a real name and a word nobody has is nothing.
		helper.assertTrue(TableSearch.filter(all, "inn zzqxj").isEmpty(), "\"inn zzqxj\" found something");
		String none = Component.translatable("screen.aliveworkplace.table.no_match", "zzqxj").getString();
		helper.assertTrue(none.equals("No builds match \"zzqxj\"."), "the empty result reads: " + none);
		String help = Component.translatable("screen.aliveworkplace.table.no_match_help").getString();
		helper.assertTrue(help.equals("Try a name, an id or a job."), "the empty result's help reads: " + help);
		String hint = Component.translatable("screen.aliveworkplace.table.search_hint").getString();
		helper.assertTrue(hint.equals("Search: name, id or job"), "the box's hint reads: " + hint);
		String count = Component.translatable("screen.aliveworkplace.table.search_count", 3, 150).getString();
		helper.assertTrue(count.equals("3 of 150 match"), "the count reads: " + count);
		String narration = Component.translatable("screen.aliveworkplace.table.search").getString();
		helper.assertTrue(narration.equals("Search the library"), "the box's name reads: " + narration);
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aClearedBoxListsEverything(GameTestHelper helper) {
		List<TablePayloads.Entry> all = library(helper);
		helper.assertTrue(TableSearch.filter(all, "inn").size() < all.size(), "\"inn\" didn't narrow the list");
		helper.assertTrue(TableSearch.filter(all, "").equals(all), "an empty box should list the whole library");
		helper.assertTrue(TableSearch.filter(all, "   ").equals(all), "a box of spaces should list the whole library");
		// A "???" (26.10) is listed with an empty box, but nothing about it can be found by a search.
		TablePayloads.Entry hidden = new TablePayloads.Entry(AliveWorkplace.id("hidden/0"), 0, 0, 0, 0, BlueprintUnlocks.State.HIDDEN.ordinal(),
			"screen.aliveworkplace.table.hint.rare");
		TablePayloads.Entry cottage = new TablePayloads.Entry(StarterBlueprints.STARTER_COTTAGE.id(), 7, 5, 9, 210);
		List<TablePayloads.Entry> withHidden = List.of(cottage, hidden);
		helper.assertTrue(TableSearch.filter(withHidden, "").equals(withHidden), "an empty box should list the \"???\" too");
		helper.assertTrue(TableSearch.filter(withHidden, "hidden").isEmpty(), "a \"???\" was found by its stand-in id");
		helper.assertTrue(TableSearch.filter(withHidden, "cottage").equals(List.of(cottage)), "\"cottage\" should list only the cottage");
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSelectedBuildHiddenByTheSearchGivesWayToTheFirstMatch(GameTestHelper helper) {
		List<TablePayloads.Entry> all = library(helper);
		ResourceLocation cottage = StarterBlueprints.STARTER_COTTAGE.id();
		List<TablePayloads.Entry> inns = TableSearch.filter(all, "inn");
		helper.assertFalse(ids(inns).contains(cottage), "the cottage should be hidden by \"inn\"");
		ResourceLocation now = TableSearch.selection(inns, cottage);
		helper.assertTrue(inns.get(0).id().equals(now), "the selection should move to the first match, was " + now);
		// A build still listed stays selected.
		helper.assertTrue(INN.equals(TableSearch.selection(inns, INN)), "the Inn should stay selected under \"inn\"");
		// Nothing listed: the pane keeps the build it showed.
		helper.assertTrue(cottage.equals(TableSearch.selection(TableSearch.filter(all, "zzqxj"), cottage)), "no match should keep the selection");
		// Clearing the box keeps the build picked while searching.
		helper.assertTrue(now.equals(TableSearch.selection(TableSearch.filter(all, ""), now)), "clearing the box changed the selection");
		helper.succeed();
	}
}
