package dev.doughbay.fabric;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnOrderPagesTest {
    @Test void nativePartialGridMayKeepItsNextArrowButAFullGridRequiresTraversal() {
        assertTrue(OwnOrderPages.atEnd(22,22,true));
        assertTrue(OwnOrderPages.atEnd(24,24,true));
        assertTrue(OwnOrderPages.atEnd(0,0,true));
        assertFalse(OwnOrderPages.atEnd(45,-1,true));
        assertTrue(OwnOrderPages.atEnd(45,-1,false));
        assertFalse(OwnOrderPages.atEnd(24,3,true),"Holes or incomplete loading do not establish an end");
        assertFalse(OwnOrderPages.atEnd(-1,0,false));
    }
    @Test void firstPageNeedsNativeControlsAndASettledFrameToo() {
        var pages=new OwnOrderPages();String frame=OwnOrderPages.fingerprint(List.of(row("minecraft:stone",0,10000)));
        assertEquals(OwnOrderPages.Turn.WAITING,pages.observe(5,frame,false,1000));
        assertEquals(OwnOrderPages.Turn.WAITING,pages.observe(5,frame,true,1100));
        assertEquals(OwnOrderPages.Turn.WAITING,pages.observe(5,frame,true,1849));
        assertEquals(OwnOrderPages.Turn.READY,pages.observe(5,frame,true,1850));
    }
    static AutomatedExecutionDriver.OwnOrderRow row(String item,int delivered,long expires) {
        return new AutomatedExecutionDriver.OwnOrderRow(item,item,960,100,delivered,expires);
    }
    @Test void deliveryAndCountdownChangesDoNotProveAnotherPage() {
        var first=List.of(row("minecraft:stone",0,100000));
        var changed=List.of(row("minecraft:stone",64,90000));
        assertEquals(OwnOrderPages.fingerprint(first),OwnOrderPages.fingerprint(changed));
        var pages=new OwnOrderPages();assertTrue(pages.append(first));
        pages.requested(5,OwnOrderPages.fingerprint(first),1000);
        assertEquals(OwnOrderPages.Turn.WAITING,pages.observe(5,OwnOrderPages.fingerprint(changed),true,2000));
        assertEquals(OwnOrderPages.Turn.WAITING,pages.observe(6,OwnOrderPages.fingerprint(changed),true,2000));
        assertEquals(OwnOrderPages.Turn.FAILED,pages.observe(6,OwnOrderPages.fingerprint(changed),true,13000));
    }
    @Test void newWindowAndDifferentContentsMustSettleBeforeCountingAndNinetyRowsStaySeparate() {
        var pages=new OwnOrderPages();var a=Collections.nCopies(45,row("minecraft:stone",0,10000));
        var b=Collections.nCopies(45,row("minecraft:quartz_block",0,10000));
        assertTrue(pages.append(a));pages.requested(5,OwnOrderPages.fingerprint(a),1000);
        assertEquals(OwnOrderPages.Turn.WAITING,pages.observe(6,OwnOrderPages.fingerprint(b),false,1100));
        assertEquals(OwnOrderPages.Turn.WAITING,pages.observe(6,OwnOrderPages.fingerprint(b),true,1200));
        assertEquals(OwnOrderPages.Turn.WAITING,pages.observe(6,OwnOrderPages.fingerprint(b),true,1949));
        assertEquals(OwnOrderPages.Turn.READY,pages.observe(6,OwnOrderPages.fingerprint(b),true,1950));
        assertTrue(pages.append(b));assertEquals(90,pages.rows().size());
        assertFalse(pages.append(a),"Repeating a page cannot add another forty-five orders");
        assertEquals(90,pages.rows().size());
    }
    @Test void emptyNativeEndPageIsDistinctFromThePreviousPageAndTheReadIsBounded() {
        var pages=new OwnOrderPages();var rows=List.of(row("minecraft:stone",0,10000));
        assertTrue(pages.append(rows));pages.requested(5,OwnOrderPages.fingerprint(rows),1000);
        assertEquals(OwnOrderPages.Turn.WAITING,pages.observe(6,"",false,2000));
        assertEquals(OwnOrderPages.Turn.WAITING,pages.observe(6,"",true,2001));
        assertEquals(OwnOrderPages.Turn.READY,pages.observe(6,"",true,2751));
        assertTrue(pages.append(List.of()));assertEquals(rows,pages.rows());
        pages.reset();
        for(int i=0;i<OwnOrderPages.MAX_PAGES;i++)assertTrue(pages.append(List.of(row("fixture:item"+i,0,10000))));
        assertFalse(pages.append(List.of(row("fixture:overflow",0,10000))));
    }
}
