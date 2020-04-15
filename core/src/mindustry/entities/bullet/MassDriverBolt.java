package mindustry.entities.bullet;

import arc.*;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.type.*;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.plugin.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.distribution.MassDriver.DriverBulletData;

import static mindustry.Vars.*;

public class MassDriverBolt extends BulletType{

    public MassDriverBolt(){
        super(5.3f, 50);
        collidesTiles = false;
        lifetime = 200f;
        despawnEffect = Fx.smeltsmoke;
        hitEffect = Fx.hitBulletBig;
        drag = 0.005f;
    }

    @Override
    public void draw(mindustry.entities.type.Bullet b){
        float w = 11f, h = 13f;

        Draw.color(Pal.bulletYellowBack);
        Draw.rect("shell-back", b.x, b.y, w, h, b.rot() + 90);

        Draw.color(Pal.bulletYellow);
        Draw.rect("shell", b.x, b.y, w, h, b.rot() + 90);

        Draw.reset();
    }

    @Override
    public void update(mindustry.entities.type.Bullet b){
        //data MUST be an instance of DriverBulletData
        if(!(b.getData() instanceof DriverBulletData)){
            hit(b);
            return;
        }

        float hitDst = 7f;

        DriverBulletData data = (DriverBulletData)b.getData();

        //if the target is dead, just keep flying until the bullet explodes
        if(data.to.isDead()){
            return;
        }

        float baseDst = data.from.dst(data.to);
        float dst1 = b.dst(data.from);
        float dst2 = b.dst(data.to);

        boolean intersect = false;

        //bullet has gone past the destination point: but did it intersect it?
        if(dst1 > baseDst){
            float angleTo = b.angleTo(data.to);
            float baseAngle = data.to.angleTo(data.from);

            //if angles are nearby, then yes, it did
            if(Angles.near(angleTo, baseAngle, 2f)){
                intersect = true;
                //snap bullet position back; this is used for low-FPS situations
                b.set(data.to.x + Angles.trnsx(baseAngle, hitDst), data.to.y + Angles.trnsy(baseAngle, hitDst));
            }
        }

        //if on course and it's in range of the target
        if(Math.abs(dst1 + dst2 - baseDst) < 4f && dst2 <= hitDst){
            intersect = true;
        } //else, bullet has gone off course, does not get recieved.

        if(intersect){
            data.to.handlePayload(b, data);
        }

        if(!Nydus.driver_terrain_clearing.active()) return;
        if(world.tileWorld(b.x, b.y) == null) return;
        Geometry.circle(world.tileWorld(b.x, b.y).x, world.tileWorld(b.x, b.y).y, 2, (x, y) -> {
            Tile other = world.tile(x, y);
            if(other != null && other.block instanceof StaticWall){
                other.deconstructNet();
                for(Player p : playerGroup){
                    p.syncbeacons.put(other, tilesize * 3);
                    Call.createBullet(p, Bullets.slagShot, p.getTeam(), other.drawx(), other.drawy(), 0, 0, 10000f);
                }
            }
        });
    }

    @Override
    public void despawned(mindustry.entities.type.Bullet b){
        super.despawned(b);

        if(!(b.getData() instanceof DriverBulletData)) return;

        DriverBulletData data = (DriverBulletData)b.getData();

        for(int i = 0; i < data.items.length; i++){
            int amountDropped = Mathf.random(0, data.items[i]);
            if(amountDropped > 0){
                float angle = b.rot() + Mathf.range(100f);
                Effects.effect(Fx.dropItem, Color.white, b.x, b.y, angle, content.item(i));
            }
        }
    }

    @Override
    public void hit(Bullet b, float hitx, float hity){
        super.hit(b, hitx, hity);
        despawned(b);
    }
}
