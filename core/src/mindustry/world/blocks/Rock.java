package mindustry.world.blocks;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.world.Block;
import mindustry.world.Tile;

public class Rock extends Block{
    protected int variants;

    public Rock(String name){
        super(name);
        breakable = true;
        alwaysReplace = true;
    }

    @Override
    public void draw(Tile tile){
        if(variants > 0){
            Draw.rect(variantRegions[Mathf.randomSeed(tile.pos(), 0, Math.max(0, variantRegions.length - 1))], tile.worldx(), tile.worldy());
        }else{
            Draw.rect(region, tile.worldx(), tile.worldy());
        }
    }

    @Override
    public TextureRegion[] generateIcons(){
        return variants == 0 ? super.generateIcons() : new TextureRegion[]{Core.atlas.find(name + "1")};
    }

    @Override
    public void load(){
        super.load();

        if(variants > 0){
            variantRegions = new TextureRegion[variants];

            for(int i = 0; i < variants; i++){
                variantRegions[i] = Core.atlas.find(name + (i + 1));
            }
        }
    }

    @Override
    public void removed(Tile tile){
        super.removed(tile);
        if(Vars.world.isGenerating()) return;
        Block block = tile.block();
        Team team = tile.getTeam();

        if(block == Blocks.rock || block == Blocks.sandBoulder || block == Blocks.shaleBoulder || block == Blocks.snowrock){
            Time.run(Mathf.random(60f * 10), () -> {
                if(tile.block == Blocks.air) tile.constructNet(block, team, (byte)0);
            });
        }
    }
}
