package dev.holdfast.core.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.combat.Executions;
import dev.holdfast.core.combat.BluntCharge;
import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.net.ActionPacket;
import dev.holdfast.core.net.CoreNet;
import dev.holdfast.core.registry.CoreEntities;
import dev.holdfast.core.registry.CoreItems;
import dev.holdfast.core.rules.Posture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/** Opt-in real paired requests and raw renderer measurements; never packaged in a normal release. */
@Mod.EventBusSubscriber(modid=HoldfastCore.MODID,value=Dist.CLIENT)
public final class ExecutionItemProbe {
    private static final Gson JSON=new Gson();
    private static int tick,waiting,index=-1,group;
    private static boolean joining;
    private static volatile int targetId=-1;
    private static volatile String failure;
    private static Matrix4f head,item;
    private static float partial;
    private static double originX,originY,originZ;
    private static net.minecraft.world.entity.decoration.ArmorStand observer;
    private static boolean all(){return "all".equals(System.getProperty("holdfast.executionProbePlan"));}
    private static String weapon(){return all()?(group<2?"katana":group<4?"knife":"bat"):System.getProperty("holdfast.executionProbeWeapon","katana");}
    private static boolean firstPerson(){return all()?(group==1 || group==3):Boolean.getBoolean("holdfast.executionProbeFirstPerson");}
    private static final String[] CASES={"standing-front","standing-left","standing-right","standing-rear","crawler","prone"};
    private static Path dir(){
        Path root=Path.of(System.getProperty("holdfast.executionItemProbe"));
        return all()?root.resolve(weapon()+"-"+(firstPerson()?"fp":"tp")):root;
    }
    private static boolean enabled(){return System.getProperty("holdfast.executionItemProbe")!=null;}
    private static void write(String name,Object value){
        try {Files.createDirectories(dir());Files.writeString(dir().resolve(name),JSON.toJson(value)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(Exception e){throw new RuntimeException(e);}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END || !enabled()) return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        if(mc.player==null || mc.level==null){
            if(!joining && mc.screen!=null && mc.getOverlay()==null){joining=true;mc.createWorldOpenFlows().loadLevel(mc.screen,"HoldfastProbe");}
            if(++waiting>1800) throw new IllegalStateException("Execution probe did not join");
            return;
        }
        if(failure!=null) throw new IllegalStateException(failure);
        if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        tick++;mc.options.bobView().set(false);
        mc.options.setCameraType(firstPerson()?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_BACK);
        if(tick==1){originX=mc.player.getX();originY=mc.player.getY();originZ=mc.player.getZ();if(firstPerson())mc.options.fov().set(70);}
        if(tick<20) return;
        int next=(tick-20)/100,local=(tick-20)%100;
        if(next>=CASES.length*(all()?5:1)){HoldfastCore.LOG.info("[holdfast-execprobe] finished actual paired plan; not user acceptance");mc.stop();return;}
        if(local==0){group=next/CASES.length;index=next%CASES.length;mc.setCameraEntity(mc.player);mc.options.fov().set(firstPerson()?70:40);prepare(index);}
        if(local==20){
            if(targetId<0 || mc.level.getEntity(targetId)==null) throw new IllegalStateException("Target did not sync");
            CoreNet.CHANNEL.sendToServer(new ActionPacket(targetId));
            HoldfastCore.LOG.info("[holdfast-execprobe] requested {} target {}",CASES[index],targetId);
        }
        if(local==23) mc.getSingleplayerServer().execute(()-> {
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if(!Executions.isExecuting(p)) failure="Execution refused "+CASES[index]+": "+Executions.lastRefusal(p);
        });
        if(local==24 && !firstPerson()){
            var target=mc.level.getEntity(targetId);
            double x=(mc.player.getX()+target.getX())/2,z=(mc.player.getZ()+target.getZ())/2;
            observer=new net.minecraft.world.entity.decoration.ArmorStand(mc.level,x+3,originY+.3,z);
            observer.moveTo(x+3,originY+.3,z,90,7);
            observer.yRotO=90;observer.xRotO=7;observer.xo=x+3;observer.yo=originY+.3;observer.zo=z;
            observer.setYHeadRot(90);observer.yHeadRotO=90;observer.setYBodyRot(90);observer.yBodyRotO=90;
            mc.setCameraEntity(observer);mc.options.fov().set(40);
        }
        if(local==25 || local==26 || local==28 || local==30 || local==33 || local==37 || local==44 || local==50) {
            try {Files.createDirectories(dir());}catch(Exception e){throw new RuntimeException(e);}
            Screenshot.grab(dir().toFile(),CASES[index]+"-"+local+".png",mc.getMainRenderTarget(),m->{});
        }
        if(local==80){final int current=index;mc.getSingleplayerServer().execute(()-> {
            var w=mc.getSingleplayerServer().overworld().getEntity(targetId);
            if(!(w instanceof WalkerEntity walker) || !walker.isCorpse()) failure="No executed corpse: "+CASES[current];
            else HoldfastCore.LOG.info("[holdfast-execprobe] {} real server brain kill observed",CASES[current]);
        });}
    }
    private static void prepare(int n){
        var mc=Minecraft.getInstance();final var uuid=mc.player.getUUID();targetId=-1;
        mc.getSingleplayerServer().execute(()-> {
            var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayer(uuid);
            server.overworld().setDayTime(6000);
            for(var w:server.overworld().getEntitiesOfClass(WalkerEntity.class,p.getBoundingBox().inflate(100))) w.discard();
            p.setGameMode(GameType.CREATIVE);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(
                    weapon().equals("knife")?CoreItems.KNIFE.get():weapon().equals("bat")?CoreItems.BASEBALL_BAT.get():CoreItems.MICHONNE_KATANA.get()));
            p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            if(weapon().equals("bat")) BluntCharge.start(p);
            var w=new WalkerEntity(CoreEntities.WALKER.get(),server.overworld());
            w.setNoAi(true);w.setNoGravity(true);
            Posture posture=n==4?Posture.CRAWLER:n==5?Posture.PRONE:Posture.UPRIGHT;
            w.setPosture(posture);
            double x=originX+n*6,z=originZ;
            w.moveTo(x,originY,z,0,0);w.setYBodyRot(0);w.setYHeadRot(0);server.overworld().addFreshEntity(w);
            double bearing=n==1?-90:n==2?90:n==3?180:0,r=Math.toRadians(bearing);
            double skullOffset=n==4?9/16.0:n==5?13/16.0:0;
            double startDistance=weapon().equals("katana")?2.5:1.8;
            double px=x-Math.sin(r)*startDistance,pz=z+skullOffset+Math.cos(r)*startDistance;
            float yaw=(float)Math.toDegrees(Math.atan2(-(x-px),z+skullOffset-pz));
            p.connection.teleport(px,originY,pz,yaw,firstPerson() && posture!=Posture.UPRIGHT?45:0);p.setYHeadRot(yaw);p.setDeltaMovement(0,0,0);
            targetId=w.getId();
            int kill=dev.holdfast.core.rules.ExecutionRules.clipFor(dev.holdfast.core.combat.Weapons.held(p),posture).orElseThrow().killTick();
            write("cases.jsonl",Map.of("case",CASES[n],"posture",posture,"target",targetId,"killTick",kill,"player",List.of(px,originY,pz),"walker",List.of(x,originY,z)));
        });
    }
    @SubscribeEvent public static void pre(GeoRenderEvent.Entity.Pre event){
        if(enabled() && event.getEntity().getId()==targetId) event.getRenderer().getGeoModel().getBone("head").ifPresent(b->b.setTrackingMatrices(true));
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void camera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event){
        if(!enabled() || !firstPerson() || index<0) return;
        var mc=Minecraft.getInstance();
        if(mc.player==null) return;
        write("camera.jsonl",Map.of("case",CASES[index],"clientTick",tick,"partial",event.getPartialTick(),
                "pitch",event.getPitch(),"yaw",event.getYaw(),"roll",event.getRoll(),
                "playerYaw",mc.player.getYRot(),"state",ClientClips.describe(mc.player)));
    }
    @SubscribeEvent public static void entity(GeoRenderEvent.Entity.Post event){
        if(!enabled() || event.getEntity().getId()!=targetId) return;
        try {
            var b=event.getRenderer().getGeoModel().getBone("head").orElseThrow();
            var field=GeoEntityRenderer.class.getDeclaredField("modelRenderTranslations");field.setAccessible(true);
            head=new Matrix4f((Matrix4f)field.get(event.getRenderer())).mul(b.getModelSpaceMatrix());
        }catch(Exception e){throw new RuntimeException(e);}
    }
    @SubscribeEvent public static void item(GeoRenderEvent.Item.Post event){
        if(!enabled() || index<0 || !event.getItemStack().is(CoreItems.MICHONNE_KATANA.get())) return;
        item=new Matrix4f(event.getPoseStack().last().pose());partial=event.getPartialTick();
        if(!Files.exists(dir().resolve("blade-cubes.jsonl"))){
            var cubes=event.getModel().topLevelBones().get(0).getCubes();
            List<List<float[]>> points=new ArrayList<>();
            // Builder emits Blade_low's111 cubes first; assert total so a changed model cannot silently reuse this slice.
            if(cubes.size()!=190) throw new IllegalStateException("Reidentify blade cube range for changed model");
            for(var cube:cubes.subList(0,111)){
                var stack=new PoseStack();RenderUtils.translateToPivotPoint(stack,cube);RenderUtils.rotateMatrixAroundCube(stack,cube);RenderUtils.translateAwayFromPivotPoint(stack,cube);
                List<float[]> vertices=new ArrayList<>();
                for(var q:cube.quads()) if(q!=null) for(var v:q.vertices()) {
                    var p=stack.last().pose().transformPosition(new Vector3f(v.position()));vertices.add(new float[]{p.x,p.y,p.z});
                }
                points.add(vertices);
            }
            write("blade-cubes.jsonl",points);
        }
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(!enabled()) return;
        if(event.phase==TickEvent.Phase.START){head=null;item=null;return;}
        var mc=Minecraft.getInstance();
        if(head==null || item==null || mc.player==null || index<0) return;
        String state=ClientClips.describe(mc.player);
        if(!state.contains("action exec_katana")) return;
        write("frames.jsonl",Map.of("case",CASES[index],"clientTick",tick,"partial",partial,"state",state,
                "head",head.get(new float[16]),"item",item.get(new float[16])));
    }
}
