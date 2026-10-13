package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.wonder.LibraryPowers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=wonder_great_library (ROADMAP 35.14): the Great Library's time-lapse and its festival's fireworks, then inside
 * its rotunda, by the enchanting table, a scholar's research screen with the Great Library's row open: Swift Hands IV
 * done, Scholarship II, Engineering I and Trade Law I researched, Cartography being researched, the cursor on Scholarship.
 */
final class WonderLibraryScene {
	/** The rotunda's centre in the template (tools/blueprints/wonders.py, GC), and its floor's top. */
	private static final int CX = 20;
	private static final int CZ = 22;
	private static final int FLOOR = 3;

	static WonderTimelapse create() {
		return new WonderTimelapse(AliveWorkplace.id("great_library"), "wonder_great_library").finale(new Finale());
	}

	private static BlockPos at(BlueprintData.Placement placement, int x, int y, int z) {
		return placement.origin().offset(StructureTemplate.transform(new BlockPos(x, y, z), placement.mirror(), placement.rotation(), BlockPos.ZERO));
	}

	private static final class Finale implements WonderTimelapse.Finale {
		private volatile Villager scholar;
		private volatile BlockPos hall;

		@Override
		public String name() {
			return "research";
		}

		@Override
		public void ready(ServerLevel level, ServerPlayer player, BlueprintData.Placement placement, BlockPos hall) {
			this.hall = hall;
			// the player and a scholar between the door and the enchanting table, in the gap its ring of shelves leaves
			BlockPos stand = at(placement, CX, FLOOR, CZ - 4);
			Villager v = EntityType.VILLAGER.create(level);
			v.moveTo(stand.getX() + 1.5, stand.getY(), stand.getZ() + 0.5, 0, 0);
			v.setCustomName(Component.literal("Aldous"));
			v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.SCHOLAR).setLevel(3));
			v.setVillagerXp(1); // (kept a scholar without a desk of their own: nothing in the rotunda is moved)
			level.addFreshEntity(v);
			scholar = v;
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
				entity.setResearch(new Research.State(Map.of("swift_hands", 4, "hearth", 2, "drill", 1, "logistics", 1, "craftsmanship", 1,
					"scholarship", 2, "engineering", 1, "trade_law", 1), Optional.of("cartography"), Research.Topic.CARTOGRAPHY.points(1) * 2 / 5, true));
				Research.forget();
			}
			Vec3 eye = Vec3.atBottomCenterOf(stand).add(0, 1.6, 0);
			ScreenshotHarness.hoverLookingAt(player, eye, Vec3.atCenterOf(at(placement, CX, FLOOR + 1, CZ)));
			ResearchScreen.open(player, v);
		}

		@Override
		public int point() {
			return ResearchScreen.LIBRARY_SLOTS[ResearchScreen.LIBRARY_TOPICS.indexOf(Research.Topic.SCHOLARSHIP)];
		}

		@Override
		public void check(ServerLevel level, ServerPlayer player) {
			Showcase.check(hall != null && LibraryPowers.stands(level, hall), "the finished Great Library opens the second research tier");
			boolean open = player.containerMenu instanceof ChoiceMenu;
			Showcase.check(open, "the scholar's research screen is open in the rotunda");
			if (!(player.containerMenu instanceof ChoiceMenu menu)) {
				return;
			}
			String header = lore(menu.icon(ResearchScreen.LIBRARY_HEADER));
			Showcase.check(header.contains("Open while"), "the Great Library's row is open: " + header);
			boolean row = true;
			for (int i = 0; i < ResearchScreen.LIBRARY_TOPICS.size(); i++) {
				row &= !menu.icon(ResearchScreen.LIBRARY_SLOTS[i]).isEmpty() && !lore(menu.icon(ResearchScreen.LIBRARY_SLOTS[i])).contains("Needs the Great Library");
			}
			Showcase.check(row, "the row holds Swift Hands IV-V, Scholarship, Engineering, Cartography, Trade Law and Pharmacy, all open");
			Showcase.check(scholar != null && Research.bonus(scholar, Research.Topic.SWIFT_HANDS) == 4, "Swift Hands IV counts in the village");
		}

		private static String lore(ItemStack icon) {
			return String.join(" / ", icon.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.Default.NORMAL).stream().map(Component::getString).toList());
		}
	}

	private WonderLibraryScene() {
	}
}
