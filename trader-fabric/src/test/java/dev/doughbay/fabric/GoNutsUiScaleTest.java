package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class GoNutsUiScaleTest {
 @Test void normalWindowsHaveStableReadableMenuSizes(){
  assertEquals(2,GoNutsUiScale.preferred(1440,1000));assertEquals(2,GoNutsUiScale.preferred(1920,1080));
  assertEquals(1,GoNutsUiScale.preferred(1280,720));assertEquals(3,GoNutsUiScale.preferred(2560,1440));
 }
 @Test void scaleNeverConsumesTheMinimumUsableViewport(){
  for(int width:new int[]{640,800,960,1280,1440,1920,2560,3840})for(int height:new int[]{360,480,600,720,1000,1080,1440,2160}){
   int scale=GoNutsUiScale.preferred(width,height);assertTrue(scale>=1);assertTrue(width/scale>=640);assertTrue(height/scale>=360);if(height>=400)assertTrue(height/scale-40>=360,"Reserve report footer without shrinking the minimum content viewport");
  }
 }
 @Test void tinyOrUnavailableWindowsStayAtOneRatherThanZero(){assertEquals(1,GoNutsUiScale.preferred(320,240));assertEquals(1,GoNutsUiScale.preferred(0,0));}
}
