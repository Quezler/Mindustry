package mindustry.world.blocks.power;

import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.plugin.*;
import mindustry.plugin.spidersilk.SpiderSilk.*;
import mindustry.world.*;
import mindustry.world.blocks.*;

import static mindustry.Vars.*;

public class Battery extends PowerDistributor{
    public int topRegion = reg("-top");

    public Color emptyLightColor = Color.valueOf("f8c266");
    public Color fullLightColor = Color.valueOf("fb9567");

    public Battery(String name){
        super(name);
        forcePower = true;
        outputsPower = true;
        consumesPower = true;
        update = true;
        rebuildable = false;
    }

    @Override
    public void draw(Tile tile){
        Draw.color(emptyLightColor, fullLightColor, tile.entity.power.status);
        Fill.square(tile.drawx(), tile.drawy(), tilesize * size / 2f - 1);
        Draw.color();

        Draw.rect(reg(topRegion), tile.drawx(), tile.drawy());
    }

    @Override
    public void placed(Tile tile){
        super.placed(tile);

        if(Nydus.single_use_batteries.active()){
            tile.entity.power.status = 0.1f;
            netServer.titanic.add(tile);
        }
    }

    @Override
    public void silk(Tile tile, Cons<Silk> cons){
        if(tile.block == Blocks.battery){
            if(tile.getAroundTiles(new Array<>()).count(i -> i.block == Blocks.air || i.block == Blocks.battery || i.block instanceof StaticWall) == 8){

                float aligned =
                (tile.getNearby(+3, +0) != null && tile.getNearby(+3, +0).block == Blocks.batteryLarge ? 1f : 0f) +
                (tile.getNearby(-3, -0) != null && tile.getNearby(-3, -0).block == Blocks.batteryLarge ? 1f : 0f) +
                (tile.getNearby(+0, +3) != null && tile.getNearby(+0, +3).block == Blocks.batteryLarge ? 1f : 0f) +
                (tile.getNearby(-0, -3) != null && tile.getNearby(-0, -3).block == Blocks.batteryLarge ? 1f : 0f);

                if(aligned > 0f){
                    cons.get(new Silk(tile){{
                        requirements = Blocks.batteryLarge.requirements;
                        trigger = () -> construct(Blocks.batteryLarge);
                        size = 3;
                    }});
                }
            }
        }

        if(tile.block == Blocks.batteryLarge){
            Tile daddy = tile;
            aligned(tile, t -> {
                if(t.getLinkedTilesAs(3, new Array<>()).count(i -> i.block == Blocks.air || i.block == Blocks.battery || i.block instanceof StaticWall) == 9){
                    cons.get(new Silk(t){{
                        requirements = Blocks.batteryLarge.requirements;
                        trigger = () -> construct(Blocks.batteryLarge);
                        team = daddy.getTeam();
                        size = 3;
                    }});
                }
            });
        }
    }

    private void aligned(Tile tile, Cons<Tile> cons){
        if(tile.getNearby(+3, +0) != null) cons.get(tile.getNearby(+3, +0));
        if(tile.getNearby(-3, -0) != null) cons.get(tile.getNearby(-3, -0));
        if(tile.getNearby(+0, +3) != null) cons.get(tile.getNearby(+0, +3));
        if(tile.getNearby(-0, -3) != null) cons.get(tile.getNearby(-0, -3));
    }
}
