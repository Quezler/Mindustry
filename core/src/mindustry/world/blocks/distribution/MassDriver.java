package mindustry.world.blocks.distribution;

import arc.*;
import arc.func.*;
import arc.struct.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.pooling.Pool.*;
import arc.util.pooling.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.Effects.*;
import mindustry.entities.bullet.*;
import mindustry.entities.type.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.liquid.MassRiver.*;

import java.io.*;

import static mindustry.Vars.*;

public class MassDriver extends Block{
    public float range;
    public float rotateSpeed = 0.04f;
    public float translation = 7f;
    public int minDistribute = 10;
    public float knockback = 4f;
    public float reloadTime = 100f;
    public Effect shootEffect = Fx.shootBig2;
    public Effect smokeEffect = Fx.shootBigSmoke2;
    public Effect recieveEffect = Fx.mineBig;
    public float shake = 3f;
    public TextureRegion baseRegion;
    protected DriverGroup group;

    protected Boolf2<Tile, Tile> fireCondition = (tile, link) -> {
        return
        tile.entity.items.total() >= minDistribute && //must shoot minimum amount of items
        link.block().itemCapacity - link.entity.items.total() >= minDistribute; //must have minimum amount of space
    };

    protected Boolf<MassDriverEntity> acceptCondition = entity -> itemCapacity - entity.items.total() >= minDistribute;
    protected Boolf<MassDriverEntity> idleCondition = entity -> itemCapacity - entity.items.total() < minDistribute;

    public MassDriver(String name){
        super(name);
        update = true;
        solid = true;
        posConfig = true;
        configurable = true;
        hasItems = true;
        layer = Layer.turret;
        hasPower = true;
        outlineIcon = true;
        entityType = MassDriverEntity::new;
        group = DriverGroup.items;
    }

    @Override
    public void configured(Tile tile, Player player, int value){
        tile.<MassDriverEntity>ent().link = value;
    }

    @Override
    public TextureRegion[] generateIcons(){
        return new TextureRegion[]{Core.atlas.find(name + "-base"), Core.atlas.find(name)};
    }

    @Override
    public void load(){
        super.load();

        baseRegion = Core.atlas.find(name + "-base");
    }

    @Override
    public void update(Tile tile){
        MassDriverEntity entity = tile.ent();
        Tile link = world.tile(entity.link);
        boolean hasLink = linkValid(tile);

        //reload regardless of state
        if(entity.reload > 0f){
            entity.reload = Mathf.clamp(entity.reload - entity.delta() / reloadTime * entity.efficiency());
        }

        //cleanup waiting shooters that are not valid
        if(!shooterValid(tile, entity.currentShooter())){
            entity.waitingShooters.remove(entity.currentShooter());
        }

        //switch states
        if(entity.state == DriverState.idle){
            //start accepting when idle and there's space
            if(!entity.waitingShooters.isEmpty() && acceptCondition.get(entity)){
                entity.state = DriverState.accepting;
            }else if(hasLink){ //switch to shooting if there's a valid link.
                entity.state = DriverState.shooting;
            }
        }

        //dump when idle or accepting
        if(entity.state == DriverState.idle || entity.state == DriverState.accepting){
            if(group == DriverGroup.items){
                tryDump(tile);
            }else{
                tryDumpLiquid(tile, tile.entity.liquids.current());
            }
        }

        //skip when there's no power
        if(!entity.cons.valid()){
            return;
        }

        if(entity.state == DriverState.accepting){
            //if there's nothing shooting at this, bail - OR, items full
            if(entity.currentShooter() == null || idleCondition.get(entity)){
                entity.state = DriverState.idle;
                return;
            }

            //align to shooter rotation
            entity.rotation = Mathf.slerpDelta(entity.rotation, tile.angleTo(entity.currentShooter()) + group.rotation, rotateSpeed * entity.efficiency());
        }else if(entity.state == DriverState.shooting){
            //if there's nothing to shoot at OR someone wants to shoot at this thing, bail
            if(!hasLink || (!entity.waitingShooters.isEmpty() && acceptCondition.get(entity))){
                entity.state = DriverState.idle;
                return;
            }

            float targetRotation = tile.angleTo(link);

            if(fireCondition.get(tile, link)){
                MassDriverEntity other = link.ent();
                other.waitingShooters.add(tile);

                if(entity.reload <= 0.0001f){

                    //align to target location
                    entity.rotation = Mathf.slerpDelta(entity.rotation, targetRotation, rotateSpeed * entity.efficiency());

                    //fire when it's the first in the queue and angles are ready.
                    if(other.currentShooter() == tile &&
                    other.state == DriverState.accepting &&
                    Angles.near(entity.rotation, targetRotation, 2f) && Angles.near(other.rotation, targetRotation + 180f + group.rotation, 2f)){
                        //actually fire
                        fire(tile, link);
                        //remove waiting shooters, it's done firing
                        other.waitingShooters.remove(tile);
                        //set both states to idle
                        entity.state = DriverState.idle;
                        other.state = DriverState.idle;
                    }
                }
            }
        }
    }

    @Override
    public void draw(Tile tile){
        Draw.rect(baseRegion, tile.drawx(), tile.drawy());
    }

    @Override
    public void drawLayer(Tile tile){
        MassDriverEntity entity = tile.ent();

        Draw.rect(region,
        tile.drawx() + Angles.trnsx(entity.rotation + 180f, entity.reload * knockback),
        tile.drawy() + Angles.trnsy(entity.rotation + 180f, entity.reload * knockback), entity.rotation - 90);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Drawf.dashCircle(x * tilesize, y*tilesize, range, Pal.accent);
    }

    @Override
    public void drawConfigure(Tile tile){
        float sin = Mathf.absin(Time.time(), 6f, 1f);

        Draw.color(Pal.accent);
        Lines.stroke(1f);
        Drawf.circles(tile.drawx(), tile.drawy(), (tile.block().size / 2f + 1) * tilesize + sin - 2f, Pal.accent);

        MassDriverEntity entity = tile.ent();

        if(linkValid(tile)){
            Tile target = world.tile(entity.link);
            Drawf.circles(target.drawx(), target.drawy(), (target.block().size / 2f + 1) * tilesize + sin - 2f, Pal.place);
            Drawf.arrow(tile.drawx(), tile.drawy(), target.drawx(), target.drawy(), size * tilesize + sin, 4f + sin);
        }

        Drawf.dashCircle(tile.drawx(), tile.drawy(), range, Pal.accent);
    }

    @Override
    public boolean onConfigureTileTapped(Tile tile, Tile other){
        if(tile == other) return false;

        MassDriverEntity entity = tile.ent();

        if(entity.link == other.pos()){
            tile.configure(-1);
            return false;
        }else if(other.block() instanceof MassDriver && other.dst(tile) <= range && other.getTeam() == tile.getTeam() && ((MassDriver) tile.block()).group == ((MassDriver) other.block()).group){
            tile.configure(other.pos());
            return false;
        }

        return true;
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        //mass drivers that ouput only cannot accept items
        return tile.entity.items.total() < itemCapacity && linkValid(tile);
    }

    protected void fire(Tile tile, Tile target){
        MassDriverEntity entity = tile.ent();
        MassDriverEntity other = target.ent();

        //reset reload, use power.
        entity.reload = 1f;

        DriverBulletData data = Pools.obtain(DriverBulletData.class, DriverBulletData::new);
        data.from = entity;
        data.to = other;
        int totalUsed = 0;

        if(group == DriverGroup.items){
            for(int i = 0; i < content.items().size; i++){
                int maxTransfer = Math.min(entity.items.get(content.item(i)), ((MassDriver)tile.block()).itemCapacity - totalUsed);
                data.items[i] = maxTransfer;
                totalUsed += maxTransfer;
                entity.items.remove(content.item(i), maxTransfer);
            }
        }else{
            for(int i = 0; i < content.liquids().size; i++){
                int maxTransfer = Math.min(Mathf.round(entity.liquids.get(content.liquid(i))), Mathf.round(((MassDriver)tile.block()).liquidCapacity - totalUsed));
                data.liquids[i] = maxTransfer;
                totalUsed += maxTransfer;
                entity.liquids.remove(content.liquid(i), maxTransfer);
            }
        }

        float angle = tile.angleTo(target);

        Bullet.create(group.bullet, entity, entity.getTeam(),
        tile.drawx() + Angles.trnsx(angle, translation), tile.drawy() + Angles.trnsy(angle, translation),
        angle, 1f, 1f, data);

        Effects.effect(shootEffect, tile.drawx() + Angles.trnsx(angle, translation),
        tile.drawy() + Angles.trnsy(angle, translation), angle);

        Effects.effect(smokeEffect, tile.drawx() + Angles.trnsx(angle, translation),
        tile.drawy() + Angles.trnsy(angle, translation), angle);

        Effects.shake(shake, shake, entity);
    }

    protected void handlePayload(MassDriverEntity entity, Bullet bullet, DriverBulletData data){
        int totalItems = entity.items.total();

        //add all the items possible
        for(int i = 0; i < data.items.length; i++){
            int maxAdd = Math.min(data.items[i], itemCapacity * 2 - totalItems);
            entity.items.add(content.item(i), maxAdd);
            data.items[i] -= maxAdd;
            totalItems += maxAdd;

            if(totalItems >= itemCapacity * 2){
                break;
            }
        }

        Effects.shake(shake, shake, entity);
        Effects.effect(recieveEffect, bullet);

        entity.reload = 1f;
        bullet.remove();
    }

    protected boolean shooterValid(Tile tile, Tile other){
        if(other == null) return true;
        if(!(other.block() instanceof MassDriver)) return false;
        if(((MassDriver) tile.block()).group != ((MassDriver) other.block()).group) return false;

        MassDriverEntity entity = other.ent();
        return entity.link == tile.pos() && tile.dst(other) <= range;
    }

    protected boolean linkValid(Tile tile){
        if(tile == null) return false;
        MassDriverEntity entity = tile.ent();
        if(entity == null || entity.link == -1) return false;
        Tile link = world.tile(entity.link);

        return link != null && link.block() instanceof MassDriver && tile.dst(link) <= range && ((MassDriver) tile.block()).group == ((MassDriver) link.block()).group;
    }

    public static class DriverBulletData implements Poolable{
        public MassDriverEntity from, to;
        public int[] items = new int[content.items().size];
        public int[] liquids = new int[content.liquids().size];

        @Override
        public void reset(){
            from = null;
            to = null;
        }
    }

    public class MassDriverEntity extends TileEntity{
        int link = -1;
        public float rotation = 90;
        public float reload = 0f;
        DriverState state = DriverState.idle;
        OrderedSet<Tile> waitingShooters = new OrderedSet<>();

        Tile currentShooter(){
            return waitingShooters.isEmpty() ? null : waitingShooters.first();
        }

        public void handlePayload(Bullet bullet, DriverBulletData data){
            ((MassDriver)block).handlePayload(this, bullet, data);
        }

        @Override
        public int config(){
            return link;
        }

        @Override
        public void write(DataOutput stream) throws IOException{
            super.write(stream);
            stream.writeInt(link);
            stream.writeFloat(rotation);
            stream.writeByte((byte)state.ordinal());
        }

        @Override
        public void read(DataInput stream, byte revision) throws IOException{
            super.read(stream, revision);
            link = stream.readInt();
            rotation = stream.readFloat();
            state = DriverState.values()[stream.readByte()];
        }
    }

    enum DriverState{
        idle, //nothing is shooting at this mass driver and it does not have any target
        accepting, //currently getting shot at, unload items
        shooting,
        unloading
    }

    protected enum DriverGroup{
        items(0f, Bullets.driverBolt),
        liquids(180f, Bullets.riverBolt);

        public final float rotation;
        public final BulletType bullet;

        DriverGroup(float rotation, BulletType bullet){
            this.rotation = rotation;
            this.bullet = bullet;
        }
    }
}
