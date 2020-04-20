package mindustry.plugin.spidersilk;

import arc.*;
import arc.func.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.type.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;

import java.io.*;

import static mindustry.Vars.*;

public class SpiderSilk implements ApplicationListener{

    private Interval timer = new Interval();

    public Array<Silk> silky = new Array<>();
    public Array<BulletType> bullets = new Array<>();
    public IntArray reserved = new IntArray();
    protected Array<Tile> tempTiles = new Array<>();

    @Override
    public void init(){
        bullets.addAll(
        Bullets.artilleryDense,
      //Bullets.artilleryHoming,
        Bullets.artilleryIncendiary,
        Bullets.artilleryExplosive,
        Bullets.artilleryPlastic);
    }

    @Override
    public void update(){
        if(!state.is(State.playing)) return;
        if(!timer.get(0, 60f * 10f)) return;
        rescan();
    }

    protected void rescan(){
        silky.clear();
        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                Tile tile = world.rawTile(x, y);
                tile.block.silk(tile, silky::add);
            }
        }
        silky.shuffle();
    }

    public Bullet bullet(BulletType type, Tile tile, Tile other){
        Vec2 predict = Predict.intercept(tile, other, type.speed);
        float dst = tile.dst(predict.x, predict.y);
        float maxTraveled = type.lifetime * type.speed;

        return Bullet.capture(() -> Call.createBullet(type, tile.getTeam(), tile.drawx(), tile.drawy(), tile.angleTo(other), 1f, (dst / maxTraveled)));
    }

    static public class Silk{
        public ItemStack[] requirements;
        public Tile tile;
        public Team team;
        public byte rotation;
        public int size, changed;

        public Prov<Boolean> abort = () -> tile.changed != changed || tile.rotation != rotation;

        public Runnable before  = () -> {};
        public Runnable trigger = () -> {};
        public Runnable after   = () -> {};

        public Runnable added = () -> {
            footprint().each(t -> spiderSilk.reserved.add(t.pos()));
        };

        public Runnable removed = () -> {
            footprint().each(t -> spiderSilk.reserved.removeValue(t.pos()));
        };

        public Silk(Tile tile){
            this.tile = tile;
            this.rotation = tile.rotation;
            this.team = tile.getTeam();
            this.changed = tile.changed;
        }

        protected void construct(Block block){
            tile.getLinkedTilesAs(block, spiderSilk.tempTiles).each(t -> {
                if(!team.cores().isEmpty()) team.core().items.add(block.requirements, state.rules.buildCostMultiplier);
            });

            tile.constructNet(block, team, rotation);
            tile.block.placed(tile);
        }

        public Array<Tile> footprint(){
            return tile.getLinkedTilesAs(size, spiderSilk.tempTiles);
        }
    }
}
