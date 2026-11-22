package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA lane E (2026-11-22, lane-e-1122-1913): B1371 with a drawing the mod really ships. The fix's tests add the older
 * drawing by hand; on a player's server the older drawing is a file in blueprint_history, and the site saved the
 * fingerprint of the drawing as that day's jar read it from its structure file. The commit: the site finds "the shipped
 * older drawing whose fingerprint, read as the blueprint library reads a drawing, is exactly the one the site saved" and
 * saves it as built_from. Here: the Fire Pit as v0.145.0 shipped it (its blueprint_history file loaded as that jar's
 * structure), a site begun from it, then today's jar (the template from the mod's own structure file) and a restart:
 * the site goes on as v0.145.0's drawing, and still does after a second restart.
 */
public class QaRebuildShipped1122eGameTests implements FabricGameTest {
	private static final ResourceLocation FIRE_PIT = AliveWorkplace.id("fire_pit");
	private static final String KEY = "v0.145.0";

	//$ gametest_ticks_batch AREA '400' '"qaRebuild1122eShipped"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRebuild1122eShipped")
	public void aSiteBegunFromAShippedOlderDrawingKeepsItAfterTheUpdate(GameTestHelper helper) {
		Stage s = stage(helper, FIRE_PIT, false);
		helper.assertTrue(Refreshes.olderKeys(s.level().getServer(), FIRE_PIT).contains(KEY), "(setup) the Fire Pit ships no " + KEY + " drawing: "
			+ Refreshes.olderKeys(s.level().getServer(), FIRE_PIT));
		// the jar of v0.145.0: its Fire Pit structure is the file blueprint_history keeps of it
		ResourceLocation file = AliveWorkplace.id("blueprint_history/fire_pit/" + KEY + ".nbt");
		Optional<Resource> resource = s.level().getServer().getResourceManager().getResource(file);
		helper.assertTrue(resource.isPresent(), "(setup) no file " + file);
		CompoundTag tag;
		try (InputStream in = resource.get().open()) {
			tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
		} catch (Exception e) {
			throw new IllegalStateException("(setup) couldn't read " + file, e);
		}
		s.level().getStructureManager().remove(FIRE_PIT);
		s.level().getStructureManager().getOrCreate(FIRE_PIT).load(Lookup.lookup(BuiltInRegistries.BLOCK), tag);
		Villager[] builder = {builder(helper, s)};
		BuildSite site = Builders.start(s.level(), builder[0], s.owner(), FIRE_PIT, s.placement());
		helper.assertTrue(site != null, "(setup) the v0.145.0 Fire Pit didn't start");
		UUID siteId = site.id();
		helper.runAfterDelay(20, () -> {
			// today's jar: the template comes from the mod's own structure file again; the world restarts
			s.level().getStructureManager().remove(FIRE_PIT);
			builder[0] = reload(helper, s.level(), builder[0]);
		});
		String[] firstAnswer = {null};
		helper.runAfterDelay(30, () -> {
			BuildSite now = BuildSiteManager.get(s.level()).get(siteId);
			helper.assertTrue(now != null, "the site begun from v0.145.0 ended within 10 ticks of the update");
			now.plan(s.level());
			firstAnswer[0] = now.builtFrom();
			builder[0] = reload(helper, s.level(), builder[0]); // a second restart
		});
		helper.runAfterDelay(40, () -> {
			BuildSite now = BuildSiteManager.get(s.level()).get(siteId);
			helper.assertTrue(now != null, "the site begun from v0.145.0 ended within 20 ticks of the update");
			now.plan(s.level());
			String second = now.builtFrom();
			helper.assertTrue(KEY.equals(firstAnswer[0]) && KEY.equals(second), "a Fire Pit begun from the shipped " + KEY
				+ " drawing goes on after the update as '" + firstAnswer[0] + "' and after a second restart as '" + second + "' (wanted " + KEY + " both times)");
			BuildSiteManager.get(s.level()).remove(siteId);
			AliveWorkplace.LOG.info("[qa1122e] the v0.145.0 Fire Pit site kept its drawing over the update and a second restart");
			helper.succeed();
		});
	}
}
