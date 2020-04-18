package mindustry.plugin;

import arc.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.type.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class CoreBarrage implements ApplicationListener{

    private Array<BulletType> bullets = new Array<>();

    public ObjectMap<Tile, Block> pending = new ObjectMap<>();

    @Override
    public void init(){
        ((ItemTurret)Blocks.ripple).ammo.each((item, bullet) -> bullets.add(bullet));

        bullets.addAll(
        Bullets.artilleryDense,
//        Bullets.artilleryHoming,
        Bullets.artilleryIncendiary,
        Bullets.artilleryExplosive,
        Bullets.artilleryPlastic);
    }

    public void fire(Tile tile, Tile other){
        bullets.shuffle();
        BulletType type = bullets.first();

        Team owner = tile.getTeam();
        Block charged = other.block.upgrade.get(other);

        pending.put(other, charged);
        coreWithdraw(tile.getTeam(), charged);

        Bullet fired = bullet(type, tile, other);

        fired.deathrattle = b -> Core.app.post(() -> {
            if(other.block.upgrade != null && other.block.upgrade.get(other) != null && other.block.upgrade.get(other) == pending.get(other)){
                if(!owner.cores().isEmpty()) coreDeposit(owner, other.block);
                other.block.upgrade(other);
            }else{
                if(!owner.cores().isEmpty()) coreDeposit(owner, charged);
            }
            pending.remove(other);
        });
    }

    public Array<Tile> upgradable(Team team){
        return indexer.getAllied(team, BlockFlag.upgradable).select(t -> {
            if(pending.containsKey(t)) return false;
            if(t.block.upgrade == null || t.block.upgrade.get(t) == null) return false;
            if(!coreAllow(team, t.block.upgrade.get(t))) return false;
            if((t.block.upgrade.get(t) == Blocks.armoredConveyor || t.block.upgrade.get(t) == Blocks.platedConduit) && Units.closest(team, t.drawx(), t.drawy(), tilesize * 22, u -> u instanceof Player) != null) return false;
            return true;
        }).asArray();
    }

    public boolean coreAllow(Team team, Block block){
        if(team.cores().isEmpty()) return false;
        return team.core().items.has(block.requirements, state.rules.buildCostMultiplier * 11);
    }

    public void coreWithdraw(Team team, Block block){
        for(ItemStack is: block.requirements){
            team.core().items.remove(is.item, (int)(is.amount * state.rules.buildCostMultiplier));
        }
    }

    public void coreDeposit(Team team, Block block){
        for(ItemStack is: block.requirements){
            team.core().items.add(is.item, (int)(is.amount * state.rules.buildCostMultiplier));
        }
    }

    public Bullet bullet(BulletType type, Tile tile, Tile other){
        Vec2 predict = Predict.intercept(tile, other, type.speed);
        float dst = tile.dst(predict.x, predict.y);
        float maxTraveled = type.lifetime * type.speed;

        return Bullet.capture(() -> Call.createBullet(type, tile.getTeam(), tile.drawx(), tile.drawy(), tile.angleTo(other), 1f, (dst / maxTraveled)));
    }
}
