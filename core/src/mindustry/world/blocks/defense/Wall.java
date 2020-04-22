package mindustry.world.blocks.defense;

import arc.Core;
import arc.func.*;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.*;
import mindustry.content.*;
import mindustry.plugin.spidersilk.SpiderSilk.*;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.meta.BlockGroup;

public class Wall extends Block{
    public int variants = 0;

    private ObjectMap<Block, Block> upgradeMap = new ObjectMap<>();

    public Wall(String name){
        super(name);
        solid = true;
        destructible = true;
        group = BlockGroup.walls;
        buildCostMultiplier = 5f;
    }

    @Override
    public void init(){
        super.init();

        upgradeMap.put(Blocks.copperWall,        Blocks.titaniumWall);
        upgradeMap.put(Blocks.copperWallLarge,   Blocks.titaniumWallLarge);

        upgradeMap.put(Blocks.titaniumWall,      Blocks.thoriumWall);
        upgradeMap.put(Blocks.titaniumWallLarge, Blocks.thoriumWallLarge);

        upgradeMap.put(Blocks.thoriumWall,       Blocks.surgeWall);
        upgradeMap.put(Blocks.thoriumWallLarge,  Blocks.surgeWallLarge);
    }

    @Override
    public void load(){
        super.load();

        if(variants != 0){
            variantRegions = new TextureRegion[variants];

            for(int i = 0; i < variants; i++){
                variantRegions[i] = Core.atlas.find(name + (i + 1));
            }
            region = variantRegions[0];
        }
    }

    @Override
    public void draw(Tile tile){
        if(variants == 0){
            Draw.rect(region, tile.drawx(), tile.drawy());
        }else{
            Draw.rect(variantRegions[Mathf.randomSeed(tile.pos(), 0, Math.max(0, variantRegions.length - 1))], tile.drawx(), tile.drawy());
        }
    }

    @Override
    public TextureRegion[] generateIcons(){
        return new TextureRegion[]{Core.atlas.find(Core.atlas.has(name) ? name : name + "1")};
    }

    @Override
    public boolean canReplace(Block other){
        return super.canReplace(other) && health > other.health;
    }

    @Override
    public void silk(Tile tile, Cons<Silk> cons){
        upgradeMap.each((from, to) -> {
            if(tile.block == from) cons.get(new Silk(tile){{
                requirements = to.requirements;
                trigger = () -> construct(to);

                weightMultiplier = tile.entity.healthf();
            }});
        });
    }
}
