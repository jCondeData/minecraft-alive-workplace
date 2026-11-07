package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B928: lightning turns a villager into a witch without a death, so the death hooks never saw it and a builder's bag
 * vanished with him while his site stayed his for ever. Now the strike leaves the work as a death does. These cover what
 * QaBuilderConverted1104BugTests doesn't: the site after a save and load, Peaceful (no witch, nothing to hand on) and the
 * other workers' bags, tools and markers.
 */
public class B928GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:build_area";

	/** Struck mid-STRUCTURE, then a save and a load of the build sites: the site doesn't come back, the blueprint lies there. */
	//$ gametest_ticks_batch HUGE '6000' '"b928Reload"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "b928Reload")
	public void aStruckBuildersSiteStaysClosedAfterASaveAndLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaBuilderTerrain1102GameTests.Run r = midJob(helper, Difficulty.NORMAL);
		Map<Item, Integer> before = new HashMap<>();
		long[] struckAt = {-1};
		helper.onEachTick(() -> {
			if (struckAt[0] >= 0 || !readyToStrike(r)) {
				return;
			}
			for (Item item : r.plan().materials().keySet()) {
				before.put(item, QaBuilderTerrain1102GameTests.loose(helper, r, item));
			}
			strike(level, r.builder()[0]);
			struckAt[0] = helper.getTick();
			for (Witch w : level.getEntitiesOfClass(Witch.class, helper.getBounds().inflate(16))) {
				w.setNoAi(true);
			}
			BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(struckAt[0] >= 0 && helper.getTick() - struckAt[0] >= 40, "not struck yet (or just now)");
			helper.assertTrue(r.site() == null, "after the strike and a save and load the site is back: stage " + (r.site() == null ? "" : r.site().stage()));
			helper.assertTrue(blueprints(helper) == 1, blueprints(helper) + " blueprint(s) on the ground after the strike and a reload, not 1");
			for (Map.Entry<Item, Integer> e : before.entrySet()) {
				int now = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey());
				helper.assertTrue(now == e.getValue(), e.getKey() + " went from " + e.getValue() + " to " + now + " after the strike and a reload");
			}
		});
	}

	/** On Peaceful lightning makes no witch: the builder only burns a little and keeps his bag, his site and his blueprint. */
	//$ gametest_ticks_batch HUGE '6000' '"b928Peaceful"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "b928Peaceful")
	public void onPeacefulAStruckBuilderKeepsHisBagAndHisSite(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaBuilderTerrain1102GameTests.Run r = midJob(helper, Difficulty.PEACEFUL);
		long[] struckAt = {-1};
		helper.onEachTick(() -> {
			if (struckAt[0] >= 0 || !readyToStrike(r)) {
				return;
			}
			strike(level, r.builder()[0]);
			struckAt[0] = helper.getTick();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(struckAt[0] >= 0 && helper.getTick() - struckAt[0] >= 20, "not struck yet (or just now)");
			Villager v = r.builder()[0];
			helper.assertTrue(level.getEntitiesOfClass(Witch.class, helper.getBounds().inflate(16)).isEmpty(), "a witch formed on Peaceful");
			helper.assertTrue(v.isAlive(), "the builder didn't live through the strike on Peaceful");
			BuildSite s = r.site();
			helper.assertTrue(s != null && v.getUUID().equals(s.builder()) && ModAttachments.BUILDER_JOB.has(v),
				"the builder lost his site to a strike that didn't turn him: " + (s == null ? "closed" : "builder " + s.builder()));
			helper.assertTrue(blueprints(helper) == 0, blueprints(helper) + " blueprint(s) dropped though the builder is still at work");
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
				helper.assertFalse(r.plan().materials().containsKey(e.getItem().getItem()), "the bag spilled though he wasn't turned: " + e.getItem());
			}
		});
	}

	/**
	 * Any worker turned by lightning: what's in their bag, the owner's tool they hold and their tree farm's marker fall
	 * (out of the bolt's reach, B952, so looked for a little round the area); a block held only for the look (a
	 * builder's, drop chance 0) isn't made out of nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"b928Workers"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b928Workers")
	public void anyWorkerStruckIntoAWitchDropsTheirBagToolAndMarker(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		difficulty(helper, Difficulty.NORMAL);
		Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		worker.setNoAi(true);
		worker.setVillagerData(worker.getVillagerData().setProfession(VillagerProfession.ARMORER));
		ModAttachments.BUILDER_BAG.getOrCreate(worker).add(new ItemStack(Items.IRON_INGOT, 12));
		ModAttachments.BUILDER_BAG.getOrCreate(worker).add(new ItemStack(Items.COAL, 5));
		worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		worker.setDropChance(EquipmentSlot.MAINHAND, 2f); // the owner's: dropped, not lost
		BlockPos corner = helper.absolutePos(new BlockPos(1, 1, 1));
		ModAttachments.TREE_FARM.set(worker, new FieldJob(new BoundingBox(corner.getX(), corner.getY(), corner.getZ(),
			corner.getX() + 4, corner.getY() + 3, corner.getZ() + 4), false));
		Villager holder = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		holder.setNoAi(true);
		holder.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.OAK_PLANKS)); // shown, not carried
		holder.setDropChance(EquipmentSlot.MAINHAND, 0f);
		strike(level, worker);
		strike(level, holder);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!worker.isAlive() && !holder.isAlive(), "a villager wasn't turned");
			helper.assertTrue(level.getEntitiesOfClass(Witch.class, helper.getBounds()).size() == 2, "not two witches");
			Map<Item, Integer> ground = new HashMap<>();
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(12))) {
				ground.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
			}
			helper.assertTrue(ground.getOrDefault(Items.IRON_INGOT, 0) == 12 && ground.getOrDefault(Items.COAL, 0) == 5,
				"the worker's bag didn't fall: " + ground);
			helper.assertTrue(ground.getOrDefault(Items.IRON_AXE, 0) == 1, "the owner's axe didn't fall: " + ground);
			helper.assertTrue(ground.getOrDefault(ModItems.FIELD_MARKER, 0) == 1, "the tree farm's marker didn't fall: " + ground);
			helper.assertTrue(ground.getOrDefault(Items.OAK_PLANKS, 0) == 0, "a block held only for the look was dropped: " + ground);
			helper.succeed();
		});
	}

	private static QaBuilderTerrain1102GameTests.Run midJob(GameTestHelper helper, Difficulty difficulty) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		difficulty(helper, difficulty);
		return QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
	}

	private static void difficulty(GameTestHelper helper, Difficulty difficulty) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(difficulty, true);
		Leftovers.after(helper, () -> level.getServer().setDifficulty(was, true));
	}

	/** Mid-STRUCTURE, a few blocks up and something of the hut in his bag. */
	private static boolean readyToStrike(QaBuilderTerrain1102GameTests.Run r) {
		BuildSite s = r.site();
		return s != null && s.stage() == BuildPlan.Stage.STRUCTURE && s.cursor() >= 4
			&& !QaBuilderConverted1104GameTests.bag(r.builder()[0], r.plan()).isEmpty();
	}

	private static void strike(ServerLevel level, Villager villager) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		bolt.moveTo(villager.position());
		bolt.setVisualOnly(true);
		villager.thunderHit(level, bolt);
	}

	private static int blueprints(GameTestHelper helper) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
			n += e.getItem().is(ModItems.BLUEPRINT) ? e.getItem().getCount() : 0;
		}
		return n;
	}
}
