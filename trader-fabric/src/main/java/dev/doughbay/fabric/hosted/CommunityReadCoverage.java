package dev.doughbay.fabric.hosted;

/** Full-read intent, successful cap termination and exhausted-book coverage are different facts. */
public final class CommunityReadCoverage {
    public static boolean capped(boolean full,boolean reachedEnd,boolean hasNext,int pages,int wanted,String termination){
        return full&&!reachedEnd&&hasNext&&wanted>0&&pages>=wanted&&"".equals(termination);
    }
    private CommunityReadCoverage(){}
}
