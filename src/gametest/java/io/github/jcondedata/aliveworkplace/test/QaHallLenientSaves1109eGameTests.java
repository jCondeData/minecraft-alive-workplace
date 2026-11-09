package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

/**
 * QA (lane E, saves, 2026-11-09): B826 and B827 past the one case each bug test tried. A village's banner design read
 * layer by layer (B826): every layer from removed mods, the first one gone (the rest in order, as a vanilla banner keeps
 * them), a layer whose color no longer reads, and the design as loaded saved and loaded again unchanged. The family
 * book read person by person (B827): the damaged person is a parent in the middle of three generations (the rest in
 * the order written in, the orphaned child a founder of their line with a title), an entry with a wrong-typed field,
 * and every entry damaged.
 */
public class QaHallLenientSaves1109eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static BannerPatternLayers design(HolderLookup.Provider registries) {
		var patterns = registries.lookupOrThrow(Registries.BANNER_PATTERN);
		return new BannerPatternLayers.Builder()
			.add(patterns.getOrThrow(BannerPatterns.STRIPE_TOP), DyeColor.WHITE)
			.add(patterns.getOrThrow(BannerPatterns.FLOWER), DyeColor.BLUE)
			.add(patterns.getOrThrow(BannerPatterns.BORDER), DyeColor.BLACK)
			.build();
	}

	/** A hall in red with {@link #design} and a red banner flying it; {@code damage} edits both saved layer lists the same way. */
	private static void bannerCase(GameTestHelper helper, java.util.function.Consumer<ListTag> damage, int expectLayers, boolean resave) {
		Leftovers.clear(helper);
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		BlockPos hallPos = new BlockPos(1, 2, 1);
		helper.setBlock(hallPos, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(hallPos);
		VillageBanners.Colours colours = new VillageBanners.Colours(DyeColor.RED, design(registries));
		hall.setColours(colours);
		BlockPos bannerPos = new BlockPos(4, 2, 1);
		helper.setBlock(bannerPos, Blocks.RED_BANNER);
		BannerBlockEntity banner = helper.getBlockEntity(bannerPos);
		banner.fromItem(colours.banner(), DyeColor.RED);
		helper.assertTrue(hall.colours().on(banner), "setup: the banner doesn't fly the village's colours");
		CompoundTag hallTag = hall.saveWithoutMetadata(registries);
		CompoundTag bannerTag = banner.saveWithoutMetadata(registries);
		helper.assertTrue(hallTag.get("bannerPatterns") instanceof ListTag l && l.size() == 3 && bannerTag.get("patterns") instanceof ListTag b && b.size() == 3,
			"setup: the saved designs aren't three layers: " + hallTag.get("bannerPatterns") + " / " + bannerTag.get("patterns"));
		damage.accept((ListTag) hallTag.get("bannerPatterns"));
		damage.accept((ListTag) bannerTag.get("patterns"));
		hall.loadWithComponents(hallTag, registries);
		banner.loadWithComponents(bannerTag, registries);
		helper.assertTrue(banner.getPatterns().layers().size() == expectLayers, "control: vanilla's banner kept " + banner.getPatterns().layers().size()
			+ " layers, not " + expectLayers);
		VillageBanners.Colours after = hall.colours();
		helper.assertTrue(after != null && after.base() == DyeColor.RED, "the village's colours after the load: " + after);
		helper.assertTrue(after.patterns().equals(banner.getPatterns()), "the village's design after the load isn't what a banner in the world kept: "
			+ after.patterns().layers() + " vs " + banner.getPatterns().layers());
		helper.assertTrue(after.on(banner), "the village's own banner no longer counts as its colours after the load");
		if (resave) {
			CompoundTag again = hall.saveWithoutMetadata(registries);
			hall.setColours(new VillageBanners.Colours(DyeColor.GREEN, BannerPatternLayers.EMPTY));
			hall.loadWithComponents(again, registries);
			helper.assertTrue(hall.colours() != null && hall.colours().base() == DyeColor.RED && hall.colours().patterns().equals(after.patterns()),
				"the design as loaded, saved and loaded again, changed: " + hall.colours());
		}
		helper.succeed();
	}

	private static void renamePattern(ListTag list, int index) {
		((CompoundTag) list.get(index)).putString("pattern", "gonemod:crest_" + index);
	}

	/** Every layer's pattern from a removed mod: the village keeps its red base with no layers, as the banner does, and saves it so. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaEveryBannerLayerFromARemovedModLeavesThePlainBase(GameTestHelper helper) {
		bannerCase(helper, list -> {
			for (int i = 0; i < list.size(); i++) {
				renamePattern(list, i);
			}
		}, 0, true);
	}

	/** The first layer's pattern gone: the other two stay in their order (the border over the flower), and stay so after another save. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaTheFirstBannerLayerGoneKeepsTheRestInOrder(GameTestHelper helper) {
		bannerCase(helper, list -> renamePattern(list, 0), 2, true);
	}

	/** The middle layer's color no longer reads (a hand-edited or damaged file): only that layer is dropped. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaABannerLayerWithAnUnreadableColorIsTheOnlyOneDropped(GameTestHelper helper) {
		bannerCase(helper, list -> ((CompoundTag) list.get(1)).putString("color", "chartreuse"), 2, false);
	}

	private static FamilyBook.Person person(UUID id, String name, int family, long born, UUID mother) {
		return new FamilyBook.Person(id, Component.literal(name), family, born, mother != null, -1L, mother, null, null, "");
	}

	/**
	 * Three generations of family 7 (Bram, his daughter Dara, her son Tib) and Ilse of family 9; Dara's entry damaged. The
	 * rest load in the order written in; Tib's mother isn't in the book any more, so he heads his line (generation 1, his
	 * own founder) and his family's title still reads; saved and loaded again, the same three in the same order.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaADamagedParentLeavesTheRestOfThreeGenerationsInOrder(GameTestHelper helper) {
		Leftovers.clear(helper);
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		BlockPos pos = new BlockPos(1, 2, 1);
		helper.setBlock(pos, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(pos);
		UUID bram = new UUID(0x1109L, 1), dara = new UUID(0x1109L, 2), ilse = new UUID(0x1109L, 3), tib = new UUID(0x1109L, 4);
		hall.families().put(person(bram, "Bram", 7, 10L, null));
		hall.families().put(person(dara, "Dara", 7, 20L, bram));
		hall.families().put(person(ilse, "Ilse", 9, 25L, null));
		hall.families().put(person(tib, "Tib", 7, 30L, dara));
		FamilyBook.Book book = hall.families();
		helper.assertTrue(FamilyBook.generation(book, book.get(tib)) == 3 && FamilyBook.founder(book, book.get(tib)).id().equals(bram),
			"setup: Tib isn't Bram's third generation");
		CompoundTag saved = hall.saveWithoutMetadata(registries);
		ListTag list = (ListTag) saved.get("families");
		helper.assertTrue(list.size() == 4, "setup: the saved book isn't 4 people: " + list.size());
		((CompoundTag) list.get(1)).remove("id"); // Dara's entry, damaged
		hall.loadWithComponents(saved, registries);
		book = hall.families();
		List<UUID> order = book.people().stream().map(FamilyBook.Person::id).toList();
		helper.assertTrue(order.equals(List.of(bram, ilse, tib)), "the book after the load isn't Bram, Ilse, Tib in that order: " + order);
		FamilyBook.Person t = book.get(tib);
		helper.assertTrue(FamilyBook.parents(book, t).isEmpty() && FamilyBook.grandparents(book, t).isEmpty() && FamilyBook.generation(book, t) == 1
			&& FamilyBook.founder(book, t).id().equals(tib), "Tib with his mother gone from the book isn't the head of his line");
		helper.assertTrue(!FamilyBook.familyTitle(book, t).getString().isEmpty() && FamilyBook.children(book, bram).isEmpty()
			&& FamilyBook.members(book, 7).size() == 2, "family 7 after the load: " + FamilyBook.members(book, 7));
		CompoundTag again = hall.saveWithoutMetadata(registries);
		hall.loadWithComponents(again, registries);
		List<UUID> twice = hall.families().people().stream().map(FamilyBook.Person::id).toList();
		helper.assertTrue(twice.equals(order) && hall.families().get(tib).mother().equals(dara),
			"saved and loaded again, the book changed: " + twice + " (Tib's mother on record: " + hall.families().get(tib).mother() + ")");
		helper.succeed();
	}

	/** An entry with its id but a wrong-typed field (born as text) is the one left out; with every entry damaged the book is empty, not an error. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaAWrongTypedPersonIsLeftOutAndAllDamagedIsAnEmptyBook(GameTestHelper helper) {
		Leftovers.clear(helper);
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		BlockPos pos = new BlockPos(1, 2, 1);
		helper.setBlock(pos, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(pos);
		UUID a = new UUID(0x1109L, 11), b = new UUID(0x1109L, 12);
		hall.families().put(person(a, "Mara", 7, 100L, null));
		hall.families().put(person(b, "Oswin", 8, 200L, null));
		CompoundTag saved = hall.saveWithoutMetadata(registries);
		CompoundTag typed = saved.copy();
		((CompoundTag) ((ListTag) typed.get("families")).get(0)).putString("born", "yesterday");
		hall.loadWithComponents(typed, registries);
		helper.assertTrue(hall.families().size() == 1 && hall.families().get(b) != null && hall.families().get(a) == null,
			"a person with 'born' as text: " + hall.families().people());
		CompoundTag ruined = saved.copy();
		ListTag list = (ListTag) ruined.get("families");
		for (int i = 0; i < list.size(); i++) {
			((CompoundTag) list.get(i)).remove("id");
		}
		hall.loadWithComponents(ruined, registries);
		helper.assertTrue(hall.families().size() == 0, "every entry damaged, yet the book holds " + hall.families().size());
		CompoundTag empty = hall.saveWithoutMetadata(registries);
		helper.assertTrue(!empty.contains("families"), "an empty book still writes a families list: " + empty.get("families"));
		hall.loadWithComponents(saved, registries);
		helper.assertTrue(hall.families().size() == 2, "the clean save after all that gives back " + hall.families().size() + " of 2");
		helper.succeed();
	}
}
