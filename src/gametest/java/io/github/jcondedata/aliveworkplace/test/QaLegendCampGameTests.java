package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

/**
 * QA (qa-1005-0633): the traveller's camp beside a ruined portal (ROADMAP 29.9, tools/blueprints/legend_sites.py) has
 * "a barrel with a map on it": an item frame holding a map, saved in traveller_camp.nbt. The frame is saved without
 * TileX/TileY/TileZ, so placing the camp logs vanilla's "Block-attached entity at invalid position" once; this checks the
 * frame still hangs there with its map after its survival checks.
 */
public class QaLegendCampGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos MIDDLE = new BlockPos(15, 1, 15);

	//$ gametest_ticks_batch AREA '200' '"qaLegendCampMap"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendCampMap")
	public void theTravellersCampHasItsMapOnTheBarrel(GameTestHelper helper) {
		Leftovers.clear(helper);
		Legend legend = Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_found_portal"), JsonParser.parseString(
			"{\"rarity\": \"rare\", \"job\": \"minecraft:cartographer\", \"title\": \"Found\", \"lore\": \"the lore\","
				+ " \"names\": [\"Pip\"], \"arrive\": [{\"way\": \"found\", \"site\": \"ruined_portal\"}]}").getAsJsonObject());
		Villager traveller = LegendSites.place(helper.getLevel(), LegendSites.site("ruined_portal"),
			new LegendSites.Match(helper.absolutePos(new BlockPos(2, 2, 2)), legend), helper.absolutePos(MIDDLE), RandomSource.create(29));
		helper.assertTrue(traveller != null, "the traveller's camp is set down");
		// a frame that isn't hung on a block pops off on its next checks (every 100 ticks), dropping the map
		helper.runAfterDelay(120, () -> {
			List<ItemFrame> frames = helper.getLevel().getEntitiesOfClass(ItemFrame.class, helper.getBounds(), f -> true);
			List<net.minecraft.world.entity.item.ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(
				net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds(), i -> true);
			LegendRecord.get(helper.getLevel()).forgetCaptive(traveller.getUUID());
			traveller.discard();
			helper.assertTrue(frames.stream().anyMatch(f -> f.getItem().is(Items.MAP)),
				"the camp's barrel still has its map after 120 ticks: frames " + frames.stream().map(f -> f.blockPosition() + " " + f.getItem()).toList()
					+ ", dropped " + dropped.stream().map(i -> i.getItem().toString()).toList());
			helper.succeed();
		});
	}
}
