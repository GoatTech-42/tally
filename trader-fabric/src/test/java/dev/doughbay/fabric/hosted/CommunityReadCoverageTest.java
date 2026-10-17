package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommunityReadCoverageTest {
    @Test void onlySuccessfulFullReadsEndingAtTheCapAreCapped(){
        assertTrue(CommunityReadCoverage.capped(true,false,true,200,200,""));
        assertFalse(CommunityReadCoverage.capped(true,false,false,200,200,""));
        assertFalse(CommunityReadCoverage.capped(false,false,true,10,10,""));
        assertFalse(CommunityReadCoverage.capped(true,true,false,200,200,"last page"));
        assertFalse(CommunityReadCoverage.capped(true,false,true,100,200,"next page did not open"));
        assertFalse(CommunityReadCoverage.capped(true,false,true,200,200,"next-page arrow could not be clicked"));
    }
}
