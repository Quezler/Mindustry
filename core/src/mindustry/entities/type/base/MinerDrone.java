package mindustry.entities.type.base;

import arc.math.Mathf;
import arc.util.*;
import mindustry.content.Blocks;
import mindustry.entities.traits.MinerTrait;
import mindustry.entities.type.TileEntity;
import mindustry.entities.units.UnitState;
import mindustry.gen.Call;
import mindustry.type.Item;
import mindustry.type.ItemType;
import mindustry.world.Pos;
import mindustry.world.Tile;

import java.io.*;

import static mindustry.Vars.*;

/** A drone that only mines.*/
public class MinerDrone extends BaseDrone implements MinerTrait{
    protected Item targetItem;
    protected Tile mineTile;

    public final UnitState

    mine = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            TileEntity entity = getClosestCore();

            if(entity == null) return;

            findItem();

            //core full of the target item, do nothing
            if(targetItem != null && entity.block.acceptStack(targetItem, 1, entity.tile, MinerDrone.this) == 0){
                MinerDrone.this.clearItem();
                return;
            }

            int transfer = 1;
            if(entity.items.get(item.item) > 50 ) transfer = 25;
            if(entity.items.get(item.item) > 100) transfer = 50;
            transfer = Mathf.random(0, transfer);

            if(item.amount > transfer && Mathf.chance(0.5f) && entity.block.acceptStack(item.item, transfer, entity.tile, MinerDrone.this) > 0){
                Call.transferItemTo(item.item, transfer, x, y, entity.tile);
                item.amount -= transfer;
            }

            //if inventory is full, drop it off.
            if(item.amount >= getItemCapacity() || (targetItem != null && !acceptsItem(targetItem))){
                setState(drop);
            }else{
                if(retarget() && targetItem != null){
                    target = indexer.findClosestOre(x, y, targetItem);
                }

                if(target instanceof Tile){
                    moveTo(type.range / 1.5f);

                    if(dst(target) < type.range && mineTile != target){
                        setMineTile((Tile)target);
                    }

                    if(((Tile)target).block() != Blocks.air){
                        setState(drop);
                    }
                }else{
                    //nothing to mine anymore, core full: circle spawnpoint
                    if(getSpawner() != null){
                        target = getSpawner();

                        circle(40f);
                    }
                }
            }
        }

        public void exited(){
            setMineTile(null);
        }
    },

    drop = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            if(item.amount == 0 || item.item.type != ItemType.material){
                clearItem();
                setState(mine);
                return;
            }

            target = getClosestCore();

            if(target == null) return;

            TileEntity tile = (TileEntity)target;

            if(dst(target) < type.range){
                if(tile.tile.block().acceptStack(item.item, item.amount, tile.tile, MinerDrone.this) > 0){
                    Call.transferItemTo(item.item, item.amount, x, y, tile.tile);
                }

                clearItem();
                setState(mine);
            }

            circle(type.range / 1.8f);
        }
    };

    @Override
    public UnitState getStartState(){
        return mine;
    }

    @Override
    public void update(){
        super.update();

        updateMining();
    }

    @Override
    protected void updateRotation(){
        if(mineTile != null && shouldRotate() && mineTile.dst(this) < type.range){
            rotation = Mathf.slerpDelta(rotation, angleTo(mineTile), 0.3f);
        }else{
            rotation = Mathf.slerpDelta(rotation, velocity.angle(), 0.3f);
        }
    }

    @Override
    public boolean shouldRotate(){
        return isMining();
    }

    @Override
    public void drawOver(){
        drawMining();
    }

    @Override
    public boolean canMine(Item item){
        return type.toMine.contains(item);
    }

    @Override
    public float getMinePower(){
        return (type.minePower * team.draugfactories / type.toMine.size) + 0.1f;
    }

    @Override
    public Tile getMineTile(){
        return mineTile;
    }

    @Override
    public void setMineTile(Tile tile){
        mineTile = tile;
    }

    @Override
    public void write(DataOutput data) throws IOException{
        super.write(data);
        data.writeInt(mineTile == null || !state.is(mine) ? Pos.invalid : mineTile.pos());
    }

    @Override
    public void read(DataInput data) throws IOException{
        super.read(data);
        mineTile = world.tile(data.readInt());
    }

    protected void findItem(){
        TileEntity entity = getClosestCore();
        if(entity == null){
            return;
        }
        targetItem = item.item;
        if(targetItem == null) kill();
    }

    @Override
    public void clearItem(){
        item.amount = 1;
    }
}
