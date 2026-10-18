package com.ltw.airtransport;

import com.ltw.airtransport.block.BurnerBlock;
import com.ltw.airtransport.block.BurnerBlockEntity;
import com.ltw.airtransport.block.ControlPanelBlock;
import com.ltw.airtransport.block.ControlPanelBlockEntity;
import com.ltw.airtransport.block.EnvelopeBlock;
import com.ltw.airtransport.block.PadBeaconBlock;
import com.ltw.airtransport.block.PadBeaconBlockEntity;
import com.ltw.airtransport.block.PropellerBlock;
import com.ltw.airtransport.block.SeatBlock;
import com.ltw.airtransport.entity.AirshipEntity;
import com.ltw.airtransport.item.ShipTrackerItem;
import com.ltw.airtransport.menu.BurnerMenu;
import com.ltw.airtransport.ship.ShipChunkLoading;
import com.mojang.logging.LogUtils;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(AirTransport.ID)
public final class AirTransport {
    public static final String ID = "ltwairtransport";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

    // --- Blocks -------------------------------------------------------------------------------
    public static final DeferredBlock<Block> LANDING_PAD = BLOCKS.register("landing_pad",
        () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(2.0f, 6.0f).sound(SoundType.METAL)));
    public static final DeferredBlock<PadBeaconBlock> PAD_BEACON = BLOCKS.register("pad_beacon",
        () -> new PadBeaconBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(2.0f, 6.0f)
            .sound(SoundType.METAL).lightLevel(s -> 10)));
    public static final DeferredBlock<ControlPanelBlock> CONTROL_PANEL = BLOCKS.register("control_panel",
        () -> new ControlPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<SeatBlock> CAPTAINS_CHAIR = BLOCKS.register("captains_chair",
        () -> new SeatBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5f).sound(SoundType.WOOD).noOcclusion(), true));
    public static final DeferredBlock<SeatBlock> PASSENGER_SEAT = BLOCKS.register("passenger_seat",
        () -> new SeatBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion(), false));
    public static final DeferredBlock<BurnerBlock> BALLOON_BURNER = BLOCKS.register("balloon_burner",
        () -> new BurnerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).sound(SoundType.METAL)
            .noOcclusion().lightLevel(s -> s.getValue(BurnerBlock.LIT) ? 13 : 0)));
    public static final DeferredBlock<PropellerBlock> PROPELLER = BLOCKS.register("propeller",
        () -> new PropellerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final Map<DyeColor, DeferredBlock<EnvelopeBlock>> ENVELOPES = new EnumMap<>(DyeColor.class);
    static {
        for (DyeColor color : DyeColor.values()) {
            ENVELOPES.put(color, BLOCKS.register(color.getName() + "_balloon_envelope",
                () -> new EnvelopeBlock(color, BlockBehaviour.Properties.of().mapColor(color.getMapColor()).strength(0.8f)
                    .sound(SoundType.WOOL).ignitedByLava())));
        }
    }

    // --- Items --------------------------------------------------------------------------------
    public static final DeferredItem<BlockItem> LANDING_PAD_ITEM = ITEMS.registerSimpleBlockItem(LANDING_PAD);
    public static final DeferredItem<BlockItem> PAD_BEACON_ITEM = ITEMS.registerSimpleBlockItem(PAD_BEACON);
    public static final DeferredItem<BlockItem> CONTROL_PANEL_ITEM = ITEMS.registerSimpleBlockItem(CONTROL_PANEL);
    public static final DeferredItem<BlockItem> CAPTAINS_CHAIR_ITEM = ITEMS.registerSimpleBlockItem(CAPTAINS_CHAIR);
    public static final DeferredItem<BlockItem> PASSENGER_SEAT_ITEM = ITEMS.registerSimpleBlockItem(PASSENGER_SEAT);
    public static final DeferredItem<BlockItem> BALLOON_BURNER_ITEM = ITEMS.registerSimpleBlockItem(BALLOON_BURNER);
    public static final DeferredItem<BlockItem> PROPELLER_ITEM = ITEMS.registerSimpleBlockItem(PROPELLER);
    public static final Map<DyeColor, DeferredItem<BlockItem>> ENVELOPE_ITEMS = new EnumMap<>(DyeColor.class);
    static {
        ENVELOPES.forEach((color, block) -> ENVELOPE_ITEMS.put(color, ITEMS.registerSimpleBlockItem(block)));
    }
    public static final DeferredItem<ShipTrackerItem> SHIP_TRACKER = ITEMS.register("ship_tracker",
        () -> new ShipTrackerItem(new Item.Properties().stacksTo(1)));

    // --- Block entities, entities, menus ------------------------------------------------------
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PadBeaconBlockEntity>> PAD_BEACON_BE = BLOCK_ENTITIES.register("pad_beacon",
        () -> BlockEntityType.Builder.of(PadBeaconBlockEntity::new, PAD_BEACON.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControlPanelBlockEntity>> CONTROL_PANEL_BE = BLOCK_ENTITIES.register("control_panel",
        () -> BlockEntityType.Builder.of(ControlPanelBlockEntity::new, CONTROL_PANEL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BurnerBlockEntity>> BURNER_BE = BLOCK_ENTITIES.register("balloon_burner",
        () -> BlockEntityType.Builder.of(BurnerBlockEntity::new, BALLOON_BURNER.get()).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<AirshipEntity>> AIRSHIP = ENTITIES.register("airship",
        () -> EntityType.Builder.<AirshipEntity>of(AirshipEntity::new, MobCategory.MISC).sized(1.0f, 1.0f)
            .clientTrackingRange(16).updateInterval(1).fireImmune().build(ID + ":airship"));

    public static final DeferredHolder<MenuType<?>, MenuType<BurnerMenu>> BURNER_MENU = MENUS.register("balloon_burner",
        () -> IMenuTypeExtension.create(BurnerMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
        () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.ltwairtransport"))
            .icon(() -> new ItemStack(ENVELOPE_ITEMS.get(DyeColor.RED).get()))
            .displayItems((params, output) -> {
                output.accept(CONTROL_PANEL_ITEM.get());
                output.accept(CAPTAINS_CHAIR_ITEM.get());
                output.accept(PASSENGER_SEAT_ITEM.get());
                output.accept(BALLOON_BURNER_ITEM.get());
                output.accept(PROPELLER_ITEM.get());
                for (DyeColor color : DyeColor.values()) output.accept(ENVELOPE_ITEMS.get(color).get());
                output.accept(PAD_BEACON_ITEM.get());
                output.accept(LANDING_PAD_ITEM.get());
                output.accept(SHIP_TRACKER.get());
            }).build());

    public AirTransport(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, AirConfig.SPEC);
        bus.addListener(ShipChunkLoading::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
