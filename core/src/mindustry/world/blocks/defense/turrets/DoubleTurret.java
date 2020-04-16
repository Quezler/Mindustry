package mindustry.world.blocks.defense.turrets;

import arc.math.Mathf;
import mindustry.content.*;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.type.*;
import mindustry.plugin.*;
import mindustry.world.*;
import mindustry.world.meta.BlockStat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;

public class DoubleTurret extends ItemTurret{
    public float shotWidth = 2f;

    public DoubleTurret(String name){
        super(name);
        shots = 2;
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.remove(BlockStat.reload);
        stats.add(BlockStat.reload, 60f / reload, StatUnit.none);
    }

    @Override
    protected void shoot(Tile tile, BulletType ammo){
        TurretEntity entity = tile.ent();
        entity.shots++;

        int i = Mathf.signs[entity.shots % 2];

        tr.trns(entity.rotation - 90, shotWidth * i, size * tilesize / 2);
        Bullet bullet = Bullet.capture(() -> bullet(tile, ammo, entity.rotation + Mathf.range(inaccuracy)));

        effects(tile);
        useAmmo(tile);

        if(tile.block == Blocks.duo && ammo == Bullets.standardCopper && Nydus.duo_yeeter.active()){
            bullet.deathrattle = b -> {
                Tile grave = world.tile(b.tileX(), b.tileY());
                if(grave != null && grave.block == Blocks.air /* && Build.validPlace(b.getTeam(), grave.x, grave.y, Blocks.copperWall, 0) */) grave.constructNet(Blocks.copperWall, b.getTeam(), (byte)0);
            };
        }
    }
}
