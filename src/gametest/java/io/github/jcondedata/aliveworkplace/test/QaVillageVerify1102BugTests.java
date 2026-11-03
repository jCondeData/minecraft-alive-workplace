package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** QA (qa-lane-c-1102-0655), B791 from the far side: a spec mismatch. Fails until the mod is fixed. */
public class QaVillageVerify1102BugTests implements FabricGameTest {

	/**
	 * A courier route whose filter named ONLY a removed mod's item: the route meant "carry that and nothing else", so
	 * after the load it must not carry everything (an empty filter means everything but tools, weapons and armor: the
	 * source chest would be emptied into the other).
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aRouteFilteredOnlyToAGoneItemDoesNotCarryEverything(GameTestHelper helper) {
		Villager postman = EntityType.VILLAGER.create(helper.getLevel());
		RouteData brass = new RouteData(Optional.of(new BlockPos(1, 2, 3)), Optional.of(new BlockPos(10, 2, 3)), List.of(Items.COPPER_INGOT));
		RouteData logs = new RouteData(Optional.of(new BlockPos(4, 2, 4)), Optional.of(new BlockPos(4, 2, 20)), List.of(Items.OAK_LOG));
		ModAttachments.COURIER_ROUTES.set(postman, List.of(brass, logs));
		CompoundTag saved = postman.saveWithoutId(new CompoundTag());
		ListTag routes = (ListTag) QaRemovedModItems1102BugTests.attachment(saved, "courier_routes");
		routes.getCompound(0).getList("filter", Tag.TAG_STRING).set(0, StringTag.valueOf("create:brass_ingot"));
		Villager reopened = EntityType.VILLAGER.create(helper.getLevel());
		reopened.load(saved);
		List<RouteData> back = ModAttachments.COURIER_ROUTES.get(reopened);
		helper.assertTrue(back != null && back.contains(logs), "the untouched route was lost: " + back);
		for (RouteData r : back) {
			if (r.from().equals(brass.from())) {
				helper.assertFalse(r.carries(new ItemStack(Items.COBBLESTONE, 64)) || r.carries(new ItemStack(Items.DIAMOND, 3)),
					"the route that carried only Create's brass now carries everything (filter " + r.filter() + "): its chest would be emptied");
			}
		}
		helper.succeed();
	}
}
