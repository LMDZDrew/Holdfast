package dev.holdfast.core.rules;

import java.util.List;
import java.util.Optional;

/** Server-side static regional volumes and segment/surface math. No client pose, damage or mesh collision. */
public final class ContactGeometry {
    private ContactGeometry() {}
    public record Point(double x, double y, double z) {
        public Point add(Point b) { return new Point(x+b.x,y+b.y,z+b.z); }
        public Point subtract(Point b) { return new Point(x-b.x,y-b.y,z-b.z); }
        public Point scale(double s) { return new Point(x*s,y*s,z*s); }
        public double lengthSquared() { return x*x+y*y+z*z; }
    }
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1) {
        public boolean contains(Point p,double e) {
            return p.x>=x0-e && p.x<=x1+e && p.y>=y0-e && p.y<=y1+e && p.z>=z0-e && p.z<=z1+e;
        }
        public boolean surface(Point p,double e) {
            return contains(p,e) && Math.min(Math.min(Math.min(Math.abs(p.x-x0),Math.abs(p.x-x1)),
                    Math.min(Math.abs(p.y-y0),Math.abs(p.y-y1))),Math.min(Math.abs(p.z-z0),Math.abs(p.z-z1)))<=e;
        }
    }
    public record Volume(BodyRegion region,Box box) {}
    public record Segment(Point entry,Point exit,double fraction,boolean crossesExit) {}
    public record Contact(Point entry,Point exit,BodyRegion region,boolean crossesExit) {}

    public static List<Volume> volumes(Posture posture) {
        if(posture==Posture.UPRIGHT) return List.of(
                new Volume(BodyRegion.HEAD,new Box(-4,26,-4,4,32.5,4)),
                new Volume(BodyRegion.NECK,new Box(-4,24,-4,4,26,4)),
                new Volume(BodyRegion.TORSO,new Box(-4,12,-2,4,24,2)),
                new Volume(BodyRegion.ARM,new Box(-8,12,-2,-4,24,2)),
                new Volume(BodyRegion.ARM,new Box(4,12,-2,8,24,2)),
                new Volume(BodyRegion.LEG,new Box(-4,0,-2,4,12,2)));
        double o=posture==Posture.PRONE?HitRegions.PRONE_OFFSET:HitRegions.CRAWLER_OFFSET;
        return List.of(new Volume(BodyRegion.HEAD,new Box(-4,0,o-32.5,4,8,o-26)),
                new Volume(BodyRegion.NECK,new Box(-4,0,o-26,4,8,o-24)),
                new Volume(BodyRegion.TORSO,new Box(-8,0,o-24,8,8,o-12)),
                new Volume(posture==Posture.CRAWLER?BodyRegion.TORSO:BodyRegion.LEG,new Box(-4,0,o-12,4,8,o)));
    }

    /** Slab intersection including parallel axes, inside starts, and travel in either direction. */
    public static Optional<Segment> segment(Point start,Point end,Box box) {
        Point d=end.subtract(start);
        if(!Double.isFinite(d.lengthSquared()) || d.lengthSquared()<1e-18) return Optional.empty();
        double[] s={start.x,start.y,start.z},v={d.x,d.y,d.z};
        double[] lo={box.x0,box.y0,box.z0},hi={box.x1,box.y1,box.z1};
        double enter=Double.NEGATIVE_INFINITY,exit=Double.POSITIVE_INFINITY;
        for(int i=0;i<3;i++) {
            if(Math.abs(v[i])<1e-12) {
                if(s[i]<lo[i] || s[i]>hi[i]) return Optional.empty();
            } else {
                double a=(lo[i]-s[i])/v[i],b=(hi[i]-s[i])/v[i];
                enter=Math.max(enter,Math.min(a,b));exit=Math.min(exit,Math.max(a,b));
            }
        }
        if(enter>exit || exit<0 || enter>1) return Optional.empty();
        // Inside starts reconstruct the entry surface behind them on the same trajectory.
        return Optional.of(new Segment(start.add(d.scale(enter)),start.add(d.scale(exit)),Math.max(0,enter),exit<=1));
    }

    public static Point nearestSurface(Point p,Box b) {
        double x=clamp(p.x,b.x0,b.x1),y=clamp(p.y,b.y0,b.y1),z=clamp(p.z,b.z0,b.z1);
        if(b.surface(new Point(x,y,z),1e-9)) return new Point(x,y,z);
        double[] gaps={x-b.x0,b.x1-x,y-b.y0,b.y1-y,z-b.z0,b.z1-z};
        int k=0;for(int i=1;i<6;i++) if(gaps[i]<gaps[k]) k=i;
        return switch(k) {
            case 0 -> new Point(b.x0,y,z);case 1 -> new Point(b.x1,y,z);
            case 2 -> new Point(x,b.y0,z);case 3 -> new Point(x,b.y1,z);
            case 4 -> new Point(x,y,b.z0);default -> new Point(x,y,b.z1);
        };
    }
    private static double clamp(double x,double a,double b) { return Math.max(a,Math.min(b,x)); }
    private static double distanceSquared(Point p,Box b) {
        return p.subtract(new Point(clamp(p.x,b.x0,b.x1),clamp(p.y,b.y0,b.y1),clamp(p.z,b.z0,b.z1))).lengthSquared();
    }

    /** Closest regional surface to this bounded ray, never an entity origin or free-space point. */
    public static Point nearestToSegment(Point start,Point end,Box b) {
        Point d=end.subtract(start);double lo=0,hi=1;
        for(int i=0;i<48;i++) {
            double a=(2*lo+hi)/3,c=(lo+2*hi)/3;
            if(distanceSquared(start.add(d.scale(a)),b)<=distanceSquared(start.add(d.scale(c)),b)) hi=c;else lo=a;
        }
        return nearestSurface(start.add(d.scale((lo+hi)/2)),b);
    }

    public static Contact trace(Posture posture,Point start,Point end) { return trace(volumes(posture),start,end); }
    public static Contact trace(List<Volume> volumes,Point start,Point end) {
        Segment first=null;Volume region=null;
        for(Volume v:volumes) {
            var hit=segment(start,end,v.box);
            if(hit.isPresent() && (first==null || hit.get().fraction<first.fraction)) {first=hit.get();region=v;}
        }
        if(first!=null) return new Contact(first.entry,first.exit,region.region,first.crossesExit);
        double best=Double.POSITIVE_INFINITY;Point point=null;Point d=end.subtract(start);double length=d.lengthSquared();
        for(Volume v:volumes) {
            Point p=nearestToSegment(start,end,v.box);
            Point diff=p.subtract(start);
            double t=length<1e-18?0:clamp((diff.x*d.x+diff.y*d.y+diff.z*d.z)/length,0,1);
            double gap=p.subtract(start.add(d.scale(t))).lengthSquared();
            if(gap<best) {best=gap;point=p;region=v;}
        }
        if(region==null) throw new IllegalArgumentException("No regional volumes");
        return new Contact(point,point,region.region,false);
    }
}
