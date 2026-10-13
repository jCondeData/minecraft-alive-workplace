package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A Wonder's stages as build sites (ROADMAP 35.4, the part built so far): each stage's parts become build sites pointing
 * back at the Wonder, with the stone as their bench and the Wonder's turn and mirror; the next stage starts only when
 * every part of the one before is done, in every turn and mirrored; the Wonder, its sites and their link survive a save
 * and load; old saves read with the defaults; cancelling drops the open sites and forgets the Wonder.
 */
public class WonderStageGameTests implements FabricGameTest {
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");

	private static void finish(BuildSite site) {
		for (int i = 0; i < 20 && !site.isDone(); i++) {
			site.finishList();
		}
	}

	private static void cleanUp(ServerLevel level, UUID wonder) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite s : List.copyOf(sites.all())) {
			if (wonder.equals(s.wonder())) {
				sites.remove(s.id());
			}
		}
		WonderManager.get(level).remove(wonder);
	}

	/** Stage II never starts before stage I is done, the parts sit at their offsets in all four turns and mirrored. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wonderStagesInOrder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000); // 35.7: a stage's goods are dedicated by day
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null && folly.stages().size() == 3, "the test folly is loaded: " + folly);
		BlockPos origin = new BlockPos(4000, 70, 4000);
		BlockPos stone = origin.offset(-1, 0, -1);
		UUID builder = UUID.randomUUID();
		for (Mirror mirror : new Mirror[] {Mirror.NONE, Mirror.FRONT_BACK}) {
			for (Rotation turn : Rotation.values()) {
				WonderManager manager = WonderManager.get(level);
				WonderSite site = manager.begin(FOLLY, origin, null, "Test", 1);
				BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), origin, turn, mirror);
				List<BuildSite> first = WonderStages.start(level, site.id(), at, stone, builder);
				String where = turn + "/" + mirror;
				helper.assertTrue(first.size() == folly.stages().get(0).parts().size(), "stage I's parts " + where);
				for (int stage = 1; stage <= 3; stage++) {
					WonderSite now = manager.site(site.id()).orElseThrow();
					helper.assertTrue(now.stage() == stage && now.goingUp(), "at stage " + stage + " " + where + ": " + now);
					List<Wonder.Part> parts = folly.stages().get(stage - 1).parts();
					helper.assertTrue(now.parts().size() == parts.size(), "stage " + stage + " has its parts " + where);
					for (int i = 0; i < parts.size(); i++) {
						BuildSite part = BuildSiteManager.get(level).get(now.parts().get(i));
						BlockPos expected = origin.offset(StructureTemplate.transform(parts.get(i).offset(), mirror, turn, BlockPos.ZERO));
						helper.assertTrue(part != null && part.structure().equals(parts.get(i).part()), "part " + i + " of stage " + stage);
						helper.assertTrue(part.placement().origin().equals(expected) && part.placement().rotation() == turn
							&& part.placement().mirror() == mirror, "part placed with the Wonder's turn " + where + ": " + part.placement());
						helper.assertTrue(site.id().equals(part.wonder()) && stone.equals(part.bench()), "part points back, stone is its bench");
						helper.assertTrue(part.levelGround() == (stage == 3), "only the last stage levels the ground (stage " + stage + ")");
						helper.assertTrue(builder.equals(part.builder()), "the builder kept through stage " + stage);
					}
					WonderStages.tick(level);
					helper.assertTrue(manager.site(site.id()).orElseThrow().stage() == stage, "stage " + (stage + 1) + " began before stage " + stage + " was done " + where);
					for (UUID id : now.parts()) {
						finish(BuildSiteManager.get(level).get(id));
					}
					WonderStages.tick(level);
					if (stage < 3) {
						// 35.7: the next stage waits for its goods, then starts once they're handed in
						WonderSite next = manager.site(site.id()).orElseThrow();
						helper.assertTrue(next.stage() == stage + 1 && next.parts().isEmpty() && !next.started(), "stage " + (stage + 1) + " began without its goods " + where);
						WonderGoodsKit.handInStage(level, site.id());
						WonderStages.tick(level);
					}
				}
				WonderSite done = manager.site(site.id()).orElseThrow();
				helper.assertTrue(done.done(), "finished after stage III " + where + ": " + done);
				cleanUp(level, site.id());
			}
		}
		helper.succeed();
	}

	/** The Wonder, its stage's sites and their link survive a save and load; old saves read with defaults and stay as they were. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wonderStagesSaveAndLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000); // 35.7: a stage's goods are dedicated by day
		var registries = level.registryAccess();
		BlockPos origin = new BlockPos(4100, 70, 4000);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, origin, UUID.randomUUID(), "Jesse", 3);
		BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.CLOCKWISE_90, Mirror.NONE);
		List<BuildSite> first = WonderStages.start(level, site.id(), at, origin.offset(-1, 0, -1), null);
		first.forEach(WonderStageGameTests::finish);
		WonderStages.tick(level);
		WonderGoodsKit.handInStage(level, site.id()); // 35.7: stage II's goods
		WonderStages.tick(level);
		WonderSite mid = manager.site(site.id()).orElseThrow();
		helper.assertTrue(mid.stage() == 2, "stage II going: " + mid);

		WonderManager back = WonderManager.load(manager.save(new CompoundTag(), registries), registries);
		WonderSite read = back.site(site.id()).orElseThrow();
		helper.assertTrue(read.equals(mid), "the Wonder read back: " + read + " vs " + mid);
		BuildSite part = BuildSiteManager.get(level).get(mid.parts().get(0));
		BuildSite partBack = BuildSite.load(part.save());
		helper.assertTrue(partBack != null && site.id().equals(partBack.wonder()) && partBack.bench().equals(part.bench())
			&& partBack.placement().equals(part.placement()) && !partBack.levelGround(), "the part read back");

		// A build saved before 35.4 has no wonder and saves unchanged.
		CompoundTag old = part.save();
		old.remove("wonder");
		BuildSite plain = BuildSite.load(old);
		helper.assertTrue(plain != null && plain.wonder() == null && plain.save().equals(old), "an old build saves unchanged");
		// A Wonder saved by 35.2/35.3 (no placement, stone or parts) reads with the defaults and saves the same way.
		WonderManager bare = new WonderManager();
		WonderSite plainSite = bare.begin(FOLLY, origin, null, "", 1);
		WonderSite plainBack = WonderManager.load(bare.save(new CompoundTag(), registries), registries).site(plainSite.id()).orElseThrow();
		helper.assertTrue(plainBack.placement().isEmpty() && plainBack.stone().isEmpty() && plainBack.parts().isEmpty(), "35.3 site defaults");

		// The reloaded manager carries on: stage II finishes, then III, and the folly is done.
		for (int stage = 2; stage <= 3; stage++) {
			for (UUID id : manager.site(site.id()).orElseThrow().parts()) {
				finish(BuildSiteManager.get(level).get(id));
			}
			WonderStages.tick(level);
			if (stage < 3) {
				WonderGoodsKit.handInStage(level, site.id()); // 35.7: stage III's goods
				WonderStages.tick(level);
			}
		}
		helper.assertTrue(manager.site(site.id()).orElseThrow().done(), "the folly is finished");
		cleanUp(level, site.id());
		helper.succeed();
	}

	/** Cancelling drops the open build sites, keeps what's done, and forgets the Wonder. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wonderCancelDropsOpenSites(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000); // 35.7: a stage's goods are dedicated by day
		BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, origin, null, "Test", 1);
		BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE);
		List<BuildSite> first = WonderStages.start(level, site.id(), at, origin, null);
		first.forEach(WonderStageGameTests::finish);
		WonderStages.tick(level);
		WonderGoodsKit.handInStage(level, site.id()); // 35.7: stage II's goods, so its sites are open
		WonderStages.tick(level);
		List<UUID> open = manager.site(site.id()).orElseThrow().parts();
		helper.assertTrue(!open.isEmpty(), "stage II's sites aren't open");
		net.minecraft.world.item.ItemStack plan = WonderStages.cancel(level, site.id());
		helper.assertTrue(manager.site(site.id()).isEmpty(), "the Wonder is forgotten");
		helper.assertTrue(plan.is(io.github.jcondedata.aliveworkplace.registry.ModItems.WONDER_PLAN), "the plan comes back: " + plan);
		level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(origin).inflate(3)).forEach(e -> e.discard());
		helper.assertTrue(open.stream().allMatch(id -> BuildSiteManager.get(level).get(id) == null), "its open sites are gone");
		helper.assertTrue(first.stream().allMatch(s -> BuildSiteManager.get(level).get(s.id()) != null), "stage I's finished sites stay");
		first.forEach(s -> BuildSiteManager.get(level).remove(s.id()));
		helper.succeed();
	}
}
