package portablejim.bbw.core;

import apis.wgc.Integrations;
import com.wdg.wgcore.integration.api.WGCoreIntegrationAccess;
import com.wdg.wgcore.integration.api.WGCoreIntegrationApi;
import com.wdg.wgcore.integration.model.IntegrationDecision;
import com.wdg.wgcore.integration.model.PlacementActionContext;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;
import org.junit.BeforeClass;
import org.junit.Test;
import portablejim.bbw.BetterBuildersWandsMod;
import portablejim.bbw.basics.Point3d;
import portablejim.bbw.core.conversion.CustomMapping;
import portablejim.bbw.core.conversion.CustomMappingManager;
import portablejim.bbw.core.wands.UnbreakingWand;
import portablejim.bbw.shims.BasicPlayerShim;
import portablejim.bbw.shims.BasicWorldShim;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/** Runs with real Forge events/snapshots and a small in-memory world, without a server. */
public class WandSafetyTest {
    private static Unsafe unsafe;

    @BeforeClass public static void bootstrap() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
        cpw.mods.fml.common.Loader.injectData("7","99","40","1614","1.7.10","9.05",
                new java.io.File("."), Collections.<String>emptyList());
        Field side = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
        side.setAccessible(true); side.set(null,cpw.mods.fml.relauncher.Side.SERVER);
        Bootstrap.func_151354_b();
        BetterBuildersWandsMod.logger = org.apache.logging.log4j.LogManager.getLogger("WandSafetyTest");
    }

    private static EntityPlayerMP player(World world) throws Exception {
        EntityPlayerMP player = (EntityPlayerMP) unsafe.allocateInstance(TestPlayer.class);
        player.worldObj = world;
        player.inventory = new InventoryPlayer(player);
        player.capabilities = new net.minecraft.entity.player.PlayerCapabilities();
        return player;
    }

    public static class TestPlayer extends EntityPlayerMP {
        private TestPlayer() { super(null,null,null,null); }
        @Override public UUID getUniqueID() { return new UUID(0,1); }
        @Override public boolean canPlayerEdit(int x,int y,int z,int side,ItemStack stack) { return true; }
        @Override public void addStat(net.minecraft.stats.StatBase stat,int count) { }
    }

    private static MemoryWorld world() throws Exception {
        MemoryWorld world = (MemoryWorld) unsafe.allocateInstance(MemoryWorld.class);
        Field provider = World.class.getDeclaredField("provider"); provider.setAccessible(true);
        provider.set(world, new WorldProviderSurface());
        world.capturedBlockSnapshots = new ArrayList<BlockSnapshot>();
        world.blocks = new HashMap<String, Block>();
        world.metadata = new HashMap<String, Integer>();
        return world;
    }

    public static class MemoryWorld extends World {
        Map<String, Block> blocks;
        Map<String, Integer> metadata;
        int notifications;
        private MemoryWorld() { super(null, "test", (net.minecraft.world.WorldProvider) null, null, null); }
        private String key(int x, int y, int z) { return x + "," + y + "," + z; }
        @Override public Block getBlock(int x, int y, int z) {
            Block block = blocks.get(key(x,y,z)); return block == null ? Blocks.air : block;
        }
        @Override public int getBlockMetadata(int x, int y, int z) {
            Integer value = metadata.get(key(x,y,z)); return value == null ? 0 : value;
        }
        @Override public TileEntity getTileEntity(int x, int y, int z) { return null; }
        @Override public boolean setBlock(int x, int y, int z, Block block, int meta, int flags) {
            if (captureBlockSnapshots && !restoringBlockSnapshots)
                capturedBlockSnapshots.add(BlockSnapshot.getBlockSnapshot(this,x,y,z,flags));
            blocks.put(key(x,y,z),block); metadata.put(key(x,y,z),meta); return true;
        }
        @Override public boolean setBlockMetadataWithNotify(int x,int y,int z,int meta,int flags) {
            metadata.put(key(x,y,z),meta); return true;
        }
        @Override public void markBlockForUpdate(int x,int y,int z) { }
        @Override public void markAndNotifyBlock(int x,int y,int z,Chunk chunk,Block old,Block block,int flags) { notifications++; }
        @Override public boolean blockExists(int x,int y,int z) { return true; }
        @Override public boolean canMineBlock(EntityPlayer player,int x,int y,int z) { return true; }
        @Override public List getEntitiesWithinAABB(Class type,net.minecraft.util.AxisAlignedBB box) { return Collections.emptyList(); }
        @Override public void playSoundEffect(double x,double y,double z,String sound,float volume,float pitch) { }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 2; }
        @Override public Entity getEntityByID(int id) { return null; }
    }

    public static class DenyPlacement {
        int events;
        @SubscribeEvent public void place(BlockEvent.PlaceEvent event) {
            events++;
            if (event.x == 16) event.setCanceled(true);
        }
    }

    @Test public void deniedDestinationDoesNotPayAndCannotBeRolledBackAgainByOuterForge() throws Exception {
        MemoryWorld world = world(); EntityPlayerMP player = player(world);
        world.captureBlockSnapshots = true; // Forge's outer onPlaceItemIntoWorld wrapper
        DenyPlacement protection = new DenyPlacement(); MinecraftForge.EVENT_BUS.register(protection);
        final int[] payments = {0};
        PlacementTransaction.Payment pay = new PlacementTransaction.Payment() {
            public boolean consume() { payments[0]++; return true; }
        };
        try {
            assertTrue(PlacementTransaction.place(world,player,new Point3d(15,64,0),Blocks.stone,0,ForgeDirection.UP,pay));
            assertFalse(PlacementTransaction.place(world,player,new Point3d(16,64,0),Blocks.stone,0,ForgeDirection.UP,pay));
            assertEquals(Blocks.stone,world.getBlock(15,64,0));
            assertEquals(Blocks.air,world.getBlock(16,64,0));
            assertEquals(1,payments[0]); assertEquals(2,protection.events);
            assertTrue(world.captureBlockSnapshots); assertTrue(world.capturedBlockSnapshots.isEmpty());
            assertEquals(1,world.notifications);
        } finally { MinecraftForge.EVENT_BUS.unregister(protection); }
    }

    @Test public void paymentFailureAndExceptionRestoreWorldAndCaptureFlags() throws Exception {
        MemoryWorld world = world(); EntityPlayerMP player = player(world);
        assertFalse(PlacementTransaction.place(world,player,new Point3d(0,64,0),Blocks.stone,0,ForgeDirection.UP,
                new PlacementTransaction.Payment() { public boolean consume() { return false; } }));
        assertEquals(Blocks.air,world.getBlock(0,64,0));
        try {
            PlacementTransaction.place(world,player,new Point3d(0,64,0),Blocks.stone,0,ForgeDirection.UP,
                    new PlacementTransaction.Payment() { public boolean consume() { throw new IllegalStateException("test"); } });
            fail();
        } catch (IllegalStateException expected) { }
        assertEquals(Blocks.air,world.getBlock(0,64,0));
        assertFalse(world.captureBlockSnapshots); assertFalse(world.restoringBlockSnapshots);
        assertTrue(world.capturedBlockSnapshots.isEmpty()); assertEquals(0,world.notifications);
    }

    @Test public void foreignSnapshotIsPreservedWithoutCharging() throws Exception {
        MemoryWorld world = world(); EntityPlayerMP player = player(world);
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world,3,64,0);
        world.capturedBlockSnapshots.add(snapshot);
        assertFalse(PlacementTransaction.place(world,player,new Point3d(0,64,0),Blocks.stone,0,ForgeDirection.UP,
                new PlacementTransaction.Payment() { public boolean consume() { fail("foreign transaction paid"); return true; } }));
        assertSame(snapshot,world.capturedBlockSnapshots.get(0));
    }

    @Test public void splitStackCostIsExactAndInsufficientPaymentIsNonDestructive() throws Exception {
        EntityPlayerMP player = player(world()); BasicPlayerShim shim = new BasicPlayerShim(player);
        player.inventory.mainInventory[35] = new ItemStack(Blocks.stone,1);
        player.inventory.mainInventory[34] = new ItemStack(Blocks.stone,3);
        assertTrue(shim.useItem(new ItemStack(Blocks.stone,2)));
        assertNull(player.inventory.mainInventory[35]); assertEquals(2,player.inventory.mainInventory[34].stackSize);
        assertFalse(shim.useItem(new ItemStack(Blocks.stone,3)));
        assertEquals(2,player.inventory.mainInventory[34].stackSize);
        assertFalse(shim.useItem(new ItemStack(Blocks.stone,0)));
    }

    public static class Provider extends net.minecraft.item.Item implements vazkii.botania.api.item.IBlockProvider {
        int remaining=3;
        public int getBlockCount(EntityPlayer player,ItemStack request,ItemStack stack,Block block,int meta) {
            return block==Blocks.stone?remaining:0;
        }
        public boolean provideBlock(EntityPlayer player,ItemStack request,ItemStack stack,Block block,int meta,boolean consume) {
            if (block!=Blocks.stone || remaining<=0) return false;
            if (consume) remaining--;
            return true;
        }
    }

    @Test public void providerSingleCostsWorkWithoutPartiallyChargingUnsupportedMultiCosts() throws Exception {
        EntityPlayerMP player=player(world());BasicPlayerShim shim=new BasicPlayerShim(player);
        Provider provider=new Provider();player.inventory.mainInventory[0]=new ItemStack(provider);
        assertEquals(3,shim.countItems(new ItemStack(Blocks.stone,1)));
        assertTrue(shim.useItem(new ItemStack(Blocks.stone,1)));assertEquals(2,provider.remaining);
        player.inventory.mainInventory[1]=new ItemStack(Blocks.stone,1);
        assertEquals(0,shim.countItems(new ItemStack(Blocks.stone,2)));
        assertFalse(shim.useItem(new ItemStack(Blocks.stone,2)));
        assertEquals(2,provider.remaining);assertEquals(1,player.inventory.mainInventory[1].stackSize);
        player.capabilities.isCreativeMode=true;
        assertTrue(new portablejim.bbw.shims.CreativePlayerShim(player).useItem(new ItemStack(Blocks.stone,12)));
        assertEquals(2,provider.remaining);assertEquals(1,player.inventory.mainInventory[1].stackSize);
    }

    @Test public void mappingCostsAndOutputsStayTogetherEvenWithExactSourceInInventory() throws Exception {
        MemoryWorld world = world(); EntityPlayerMP player = player(world());
        BetterBuildersWandsMod mod = new BetterBuildersWandsMod(); BetterBuildersWandsMod.instance = mod;
        mod.mappingManager = new CustomMappingManager();
        mod.configValues = (ConfigValues) unsafe.allocateInstance(ConfigValues.class);
        mod.configValues.HARD_BLACKLIST_SET = new HashSet<String>();
        world.setBlock(0,64,0,Blocks.cobblestone,0,3);
        player.inventory.mainInventory[0] = new ItemStack(Blocks.cobblestone,64);
        CustomMapping mapping = mod.mappingManager.parseMappingString("minecraft:cobblestone/0=>12*minecraft:stone/0=>minecraft:stone/0");
        assertNotNull(mapping); assertEquals(12,mapping.getItems().stackSize);
        mod.mappingManager.setMapping(mapping);
        WandWorker worker = new WandWorker(new UnbreakingWand(),new BasicPlayerShim(player),new BasicWorldShim(world));
        ItemStack cost = worker.getProperItemStack(new BasicWorldShim(world),new BasicPlayerShim(player),new Point3d(0,64,0));
        assertEquals(12,cost.stackSize); assertEquals(new ItemStack(Blocks.stone).getItem(),cost.getItem());
        cost.stackSize = 0; assertEquals(12,mapping.getItems().stackSize);
        assertNull(mod.mappingManager.parseMappingString("minecraft:stone/0=>0*minecraft:stone/0=>minecraft:stone/0"));
        assertNull(mod.mappingManager.parseMappingString("minecraft:stone/0=>999999999999999999*minecraft:stone/0=>minecraft:stone/0"));
        mod.configValues.HARD_BLACKLIST_SET.add("minecraft:stone/0");
        assertNull(worker.getProperItemStack(new BasicWorldShim(world),new BasicPlayerShim(player),new Point3d(0,64,0)));
    }

    @Test public void adapterUsesPlacementWithActualCoordinatesActorAndBlockAndDeniesUnbound() throws Exception {
        final UUID actor = UUID.randomUUID(); final MemoryWorld world = world();
        WGCoreIntegrationApi api = (WGCoreIntegrationApi) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{WGCoreIntegrationApi.class}, (proxy,method,args) -> {
                    assertEquals("evaluatePlacement",method.getName());
                    PlacementActionContext context = (PlacementActionContext) args[0];
                    assertSame(world,context.getWorld()); assertEquals(16,context.getBlockX());
                    assertEquals(64,context.getBlockY()); assertEquals(-1,context.getBlockZ());
                    assertEquals(actor,context.getAttribution().getDirectPlayerId());
                    assertEquals("minecraft:stone",context.getTargetId());
                    return null; // a missing decision must deny, even if targeting would allow
                });
        try {
            WGCoreIntegrationAccess.bind(api);
            assertFalse(Integrations.canPlaceBlockWGC(actor,world,Blocks.stone,16,64,-1));
            WGCoreIntegrationAccess.clear();
            assertFalse(Integrations.canPlaceBlockWGC(actor,world,Blocks.stone,16,64,-1));
        } finally { WGCoreIntegrationAccess.clear(); }
    }

    @Test public void legacyUndoCannotDeleteOrRefundEvenWithoutAPlayer() {
        try { new OopsCommand().processCommand(null,new String[0]); fail(); }
        catch (net.minecraft.command.WrongUsageException expected) {
            assertEquals("bbw.chat.error.undodisabled",expected.getMessage());
        }
    }

    @Test public void realWandThroughForgeWrapperHonorsBorderAndDoesNotLoseDeniedMaterials() throws Exception {
        MemoryWorld world = world(); EntityPlayerMP player = player(world);
        BetterBuildersWandsMod mod = new BetterBuildersWandsMod(); BetterBuildersWandsMod.instance = mod;
        mod.mappingManager = new CustomMappingManager();
        mod.configValues = (ConfigValues) unsafe.allocateInstance(ConfigValues.class);
        mod.configValues.HARD_BLACKLIST_SET = new HashSet<String>();
        mod.configValues.SOFT_BLACKLIST_SET = new HashSet<String>();
        portablejim.bbw.core.items.ItemUnrestrictedWand wand = new portablejim.bbw.core.items.ItemUnrestrictedWand(
                new UnbreakingWand(),"test","test");
        cpw.mods.fml.common.registry.GameRegistry.registerItem(wand,"safetyTestWand");
        ItemStack held = new ItemStack(wand); player.inventory.mainInventory[0] = held;
        player.inventory.mainInventory[1] = new ItemStack(Blocks.stone,2);
        world.setBlock(15,63,0,Blocks.stone,0,3); world.setBlock(16,63,0,Blocks.stone,0,3);
        WGCoreIntegrationApi api = (WGCoreIntegrationApi) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{WGCoreIntegrationApi.class}, (proxy,method,args) -> {
                    assertEquals("evaluatePlacement",method.getName());
                    PlacementActionContext context = (PlacementActionContext) args[0];
                    return context.getBlockX() == 16 ? null : IntegrationDecision.allow(null,null,null,null,false,false);
                });
        try {
            WGCoreIntegrationAccess.bind(api);
            assertTrue(net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(held,player,world,15,63,0,1,0.5F,1F,0.5F));
            assertEquals(Blocks.stone,world.getBlock(15,64,0)); assertEquals(Blocks.air,world.getBlock(16,64,0));
            assertEquals(1,player.inventory.mainInventory[1].stackSize);
            assertTrue(world.capturedBlockSnapshots.isEmpty()); assertFalse(world.captureBlockSnapshots);
            // A different claim-protection listener can still deny an otherwise API-allowed point.
            WGCoreIntegrationAccess.bind((WGCoreIntegrationApi) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{WGCoreIntegrationApi.class}, (proxy,method,args) -> IntegrationDecision.allow(null,null,null,null,false,false)));
            DenyPlacement protection = new DenyPlacement(); MinecraftForge.EVENT_BUS.register(protection);
            try {
                net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(held,player,world,16,63,0,1,0.5F,1F,0.5F);
                assertEquals(Blocks.air,world.getBlock(16,64,0)); assertEquals(1,player.inventory.mainInventory[1].stackSize);
            } finally { MinecraftForge.EVENT_BUS.unregister(protection); }
        } finally { WGCoreIntegrationAccess.clear(); }
    }
}
