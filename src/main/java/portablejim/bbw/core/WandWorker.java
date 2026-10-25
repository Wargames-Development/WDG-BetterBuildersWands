package portablejim.bbw.core;

import apis.wgc.Integrations;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.IFluidBlock;
import portablejim.bbw.BetterBuildersWandsMod;
import portablejim.bbw.basics.EnumFluidLock;
import portablejim.bbw.core.conversion.CustomMapping;
import portablejim.bbw.core.wands.IWand;
import portablejim.bbw.basics.EnumLock;
import portablejim.bbw.basics.Point3d;
import portablejim.bbw.shims.IPlayerShim;
import portablejim.bbw.shims.IWorldShim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;

/**
 * Does the heavy work of working out the blocks to place and places them.
 */
public class WandWorker {
    private final IWand wand;
    private final IPlayerShim player;
    private final IWorldShim world;

    HashSet<Point3d> allCandidates = new HashSet<Point3d>();

    public WandWorker(IWand wand, IPlayerShim player, IWorldShim world) {

        this.wand = wand;
        this.player = player;
        this.world = world;
    }

    private Block outputBlock(Block source, int meta) {
        CustomMapping mapping = BetterBuildersWandsMod.instance.mappingManager.getMapping(source, meta);
        return mapping == null ? source : mapping.getPlaceBlock();
    }

    private int outputMeta(Block source, int meta) {
        CustomMapping mapping = BetterBuildersWandsMod.instance.mappingManager.getMapping(source, meta);
        return mapping == null ? meta : mapping.getPlaceMeta();
    }

    private boolean supported(Block block, int meta) {
        // Raw copying cannot honor tile-entity/flag placement, ownership or inventory contracts.
        return block != null && !block.hasTileEntity(meta)
                && !BetterBuildersWandsMod.instance.configValues.HARD_BLACKLIST_SET.contains(
                String.format("%s/%s", Block.blockRegistry.getNameForObject(block), meta));
    }

    public ItemStack getProperItemStack(IWorldShim world, IPlayerShim player, Point3d blockPos) {
        Block block = world.getBlock(blockPos);
        int meta = world.getMetadata(blockPos);
        if (!supported(block, meta) || !supported(outputBlock(block, meta), outputMeta(block, meta))) return null;
        CustomMapping mapping = BetterBuildersWandsMod.instance.mappingManager.getMapping(block, meta);
        if (mapping != null) return mapping.getItems();
        ItemStack exact = new ItemStack(block, 1, meta);
        if (Item.getItemFromBlock(block) != null && player.countItems(exact) > 0) return exact;
        return getEquivalentItemStack(blockPos);
    }

    public ItemStack getEquivalentItemStack(Point3d blockPos) {
        Block block = world.getBlock(blockPos);
        int meta = world.getMetadata(blockPos);
        //ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        ItemStack stack = null;
        CustomMapping customMapping = BetterBuildersWandsMod.instance.mappingManager.getMapping(block, meta);
        String blockString = String.format("%s/%s", Block.blockRegistry.getNameForObject(block), meta);
        if(customMapping != null) {
            stack = customMapping.getItems();
        }
        else if(block.canSilkHarvest(world.getWorld(), player.getPlayer(), blockPos.x, blockPos.y, blockPos.z, meta)) {
            stack = BetterBuildersWandsMod.instance.blockCache.getStackedBlock(world, blockPos);
        }
        else if(!BetterBuildersWandsMod.instance.configValues.SOFT_BLACKLIST_SET.contains(blockString)) {
            Item dropped = block.getItemDropped(meta, new Random(), 0);
            if (dropped != null) {
                stack = new ItemStack(dropped, block.quantityDropped(meta, 0, new Random()), block.damageDropped(meta));
            }
        }
        //ForgeEventFactory.fireBlockHarvesting(items,this.world.getWorld(), block, blockPos.x, blockPos.y, blockPos.z, world.getMetadata(blockPos), 0, 1.0F, true, this.player.getPlayer());
        return stack;
    }

    private boolean shouldContinue(Point3d currentCandidate, Block targetBlock, int targetMetadata, Block candidateSupportingBlock, int candidateSupportingMeta, AxisAlignedBB blockBB, EnumFluidLock fluidLock) {
        if(!world.blockIsAir(currentCandidate)){
            Block currrentCandidateBlock = world.getBlock(currentCandidate);
            if(!(fluidLock == EnumFluidLock.IGNORE && currrentCandidateBlock != null && (currrentCandidateBlock instanceof IFluidBlock || currrentCandidateBlock instanceof BlockLiquid))) return false;
        };
        /*if((FluidRegistry.getFluid("water").getBlock().equals(world.getBlock(currentCandidate)) || FluidRegistry.getFluid("lava").getBlock().equals(world.getBlock(currentCandidate)))
                && world.getMetadata(currentCandidate) == 0){
            return false;
        }*/
        if(!targetBlock.equals(candidateSupportingBlock)) return false;
        if(targetMetadata != candidateSupportingMeta) return false;
        Block placedBlock = outputBlock(targetBlock, targetMetadata);
        int placedMeta = outputMeta(targetBlock, targetMetadata);
        if (!canPlace(currentCandidate, placedBlock, placedMeta)) return false;
        blockBB = placedBlock.getCollisionBoundingBoxFromPool(world.getWorld(), currentCandidate.x, currentCandidate.y, currentCandidate.z);

        return !world.entitiesInBox(blockBB);

    }

    public LinkedList<Point3d> getBlockPositionList(Point3d blockLookedAt, ForgeDirection placeDirection, int maxBlocks, EnumLock directionLock, EnumLock faceLock, EnumFluidLock fluidLock) {
        allCandidates.clear();
        maxBlocks = Math.min(16384, Math.max(0, maxBlocks));
        LinkedList<Point3d> candidates = new LinkedList<Point3d>();
        LinkedList<Point3d> toPlace = new LinkedList<Point3d>();

        Block targetBlock = world.getBlock(blockLookedAt);
        int targetMetadata = world.getMetadata(blockLookedAt);
        Point3d startingPoint = blockLookedAt.move(placeDirection);

        int directionMaskInt = directionLock.mask;
        int faceMaskInt = faceLock.mask;

        if (((directionLock != EnumLock.HORIZONTAL && directionLock != EnumLock.VERTICAL) || (placeDirection != ForgeDirection.UP && placeDirection != ForgeDirection.DOWN))
                && (directionLock != EnumLock.NORTHSOUTH || (placeDirection != ForgeDirection.NORTH && placeDirection != ForgeDirection.SOUTH))
                && (directionLock != EnumLock.EASTWEST || (placeDirection != ForgeDirection.EAST && placeDirection != ForgeDirection.WEST))) {
            candidates.add(startingPoint);
        }
        while(candidates.size() > 0 && toPlace.size() < maxBlocks) {
            Point3d currentCandidate = candidates.removeFirst();

            if (!allCandidates.add(currentCandidate)) continue;
            Point3d supportingPoint = currentCandidate.move(placeDirection.getOpposite());
            Block candidateSupportingBlock = world.getBlock(supportingPoint);
            int candidateSupportingMeta = world.getMetadata(supportingPoint);
            AxisAlignedBB blockBB =targetBlock.getCollisionBoundingBoxFromPool(world.getWorld(), currentCandidate.x, currentCandidate.y, currentCandidate.z);
            if(shouldContinue(currentCandidate, targetBlock, targetMetadata, candidateSupportingBlock, candidateSupportingMeta, blockBB, fluidLock)) {
                toPlace.add(currentCandidate);

                switch (placeDirection) {
                    case DOWN:
                    case UP:
                        if((faceMaskInt & EnumLock.UP_DOWN_MASK) > 0) {
                            if ((directionMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0)
                                candidates.add(currentCandidate.move(ForgeDirection.NORTH));
                            if ((directionMaskInt & EnumLock.EAST_WEST_MASK) > 0)
                                candidates.add(currentCandidate.move(ForgeDirection.EAST));
                            if ((directionMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0)
                                candidates.add(currentCandidate.move(ForgeDirection.SOUTH));
                            if ((directionMaskInt & EnumLock.EAST_WEST_MASK) > 0)
                                candidates.add(currentCandidate.move(ForgeDirection.WEST));
                            if ((directionMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0 && (directionMaskInt & EnumLock.EAST_WEST_MASK) > 0) {
                                candidates.add(currentCandidate.move(ForgeDirection.NORTH).move(ForgeDirection.EAST));
                                candidates.add(currentCandidate.move(ForgeDirection.NORTH).move(ForgeDirection.WEST));
                                candidates.add(currentCandidate.move(ForgeDirection.SOUTH).move(ForgeDirection.EAST));
                                candidates.add(currentCandidate.move(ForgeDirection.SOUTH).move(ForgeDirection.WEST));
                            }
                        }
                        break;
                    case NORTH:
                    case SOUTH:
                        if((faceMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0) {
                            if ((directionMaskInt & EnumLock.UP_DOWN_MASK) > 0)
                                candidates.add(currentCandidate.move(ForgeDirection.UP));
                            if ((directionMaskInt & EnumLock.EAST_WEST_MASK) > 0)
                                candidates.add(currentCandidate.move(ForgeDirection.EAST));
                            if ((directionMaskInt & EnumLock.UP_DOWN_MASK) > 0)
                                candidates.add(currentCandidate.move(ForgeDirection.DOWN));
                            if ((directionMaskInt & EnumLock.EAST_WEST_MASK) > 0)
                                candidates.add(currentCandidate.move(ForgeDirection.WEST));
                            if ((directionMaskInt & EnumLock.UP_DOWN_MASK) > 0 && (directionMaskInt & EnumLock.EAST_WEST_MASK) > 0) {
                                candidates.add(currentCandidate.move(ForgeDirection.UP).move(ForgeDirection.EAST));
                                candidates.add(currentCandidate.move(ForgeDirection.UP).move(ForgeDirection.WEST));
                                candidates.add(currentCandidate.move(ForgeDirection.DOWN).move(ForgeDirection.EAST));
                                candidates.add(currentCandidate.move(ForgeDirection.DOWN).move(ForgeDirection.WEST));
                            }
                        }
                        break;
                    case WEST:
                    case EAST:
                        if((faceMaskInt & EnumLock.EAST_WEST_MASK) > 0) {
                            if ((directionMaskInt & EnumLock.UP_DOWN_MASK) > 0)
                                candidates.add(currentCandidate.move(ForgeDirection.UP));
                            if ((directionMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0)
                                candidates.add(currentCandidate.move(ForgeDirection.NORTH));
                            if ((directionMaskInt & EnumLock.UP_DOWN_MASK) > 0)
                                candidates.add(currentCandidate.move(ForgeDirection.DOWN));
                            if ((directionMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0)
                                candidates.add(currentCandidate.move(ForgeDirection.SOUTH));
                            if ((directionMaskInt & EnumLock.UP_DOWN_MASK) > 0 && (directionMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0) {
                                candidates.add(currentCandidate.move(ForgeDirection.UP).move(ForgeDirection.NORTH));
                                candidates.add(currentCandidate.move(ForgeDirection.UP).move(ForgeDirection.SOUTH));
                                candidates.add(currentCandidate.move(ForgeDirection.DOWN).move(ForgeDirection.NORTH));
                                candidates.add(currentCandidate.move(ForgeDirection.DOWN).move(ForgeDirection.SOUTH));
                            }
                        }
                }
            }
        }
        return toPlace;
    }

    private boolean canPlace(Point3d pos, Block block, int meta) {
        if (!supported(block, meta) || pos.y < 0 || pos.y >= world.getWorld().getHeight()
                || !world.getWorld().blockExists(pos.x, pos.y, pos.z)) return false;
        if (!Integrations.canPlaceBlockWGC(player.getPlayer().getUniqueID(), world.getWorld(), block, pos.x, pos.y, pos.z)) return false;
        return block.canPlaceBlockAt(world.getWorld(), pos.x, pos.y, pos.z)
                && block.canBlockStay(world.getWorld(), pos.x, pos.y, pos.z)
                && block.canReplace(world.getWorld(), pos.x, pos.y, pos.z, meta, new ItemStack(block, 1, meta));
    }

    public ArrayList<Point3d> placeBlocks(final ItemStack wandItem, LinkedList<Point3d> blockPosList, Point3d originalBlock, final ItemStack sourceItems, int side, float hitX, float hitY, float hitZ) {
        ArrayList<Point3d> placedBlocks = new ArrayList<Point3d>();
        if (world.getWorld().isRemote || sourceItems == null || sourceItems.stackSize <= 0 || side < 0 || side > 5) return placedBlocks;
        Block source = world.getBlock(originalBlock);
        int sourceMeta = world.getMetadata(originalBlock);
        final Block placedBlock = outputBlock(source, sourceMeta);
        final int placedMeta = outputMeta(source, sourceMeta);
        for (Point3d pos : blockPosList) {
            if (wandItem.stackSize <= 0 || wand.getMaxBlocks(wandItem) <= 0 || player.countItems(sourceItems) <= 0) break;
            if (!canPlace(pos, placedBlock, placedMeta)
                    || !player.getPlayer().canPlayerEdit(pos.x, pos.y, pos.z, side, new ItemStack(placedBlock, 1, placedMeta))
                    || !world.getWorld().canMineBlock(player.getPlayer(), pos.x, pos.y, pos.z)
                    || world.entitiesInBox(placedBlock.getCollisionBoundingBoxFromPool(world.getWorld(), pos.x, pos.y, pos.z))) continue;
            if (PlacementTransaction.place(world.getWorld(), player.getPlayer(), pos, placedBlock, placedMeta,
                    ForgeDirection.getOrientation(side), new PlacementTransaction.Payment() {
                        @Override public boolean consume() { return player.useItem(sourceItems); }
                    })) {
                placedBlocks.add(pos);
                if (!player.isCreative()) wand.placeBlock(wandItem, player.getPlayer());
                world.playPlaceAtBlock(pos, placedBlock);
            }
        }
        return placedBlocks;
    }
}
