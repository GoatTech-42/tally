package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.doughbay.fabric.AutomaticReportDetector.*;
class AutomaticReportDetectorTest {
 static Observation healthy(){return new Observation(true,true,true,false,true,false,false,null);}
 static Observation refused(){return new Observation(true,true,true,false,true,true,false,Signature.IMMUTABLE_POSITION_IDENTITY_CONFLICT);}
 @Test void refusalMustSurviveRecoveryGrace(){var d=new AutomaticReportDetector();d.observe(healthy(),0);assertTrue(d.observe(refused(),1).isEmpty());assertTrue(d.observe(refused(),120000).isEmpty());assertEquals(Failure.DURABLE_WRITE_REJECTED,d.observe(refused(),120001).orElseThrow().failure());}
 @Test void recoveredFailureDoesNotReport(){var d=new AutomaticReportDetector();d.observe(healthy(),0);d.observe(refused(),1);d.observe(healthy(),60000);assertTrue(d.observe(refused(),120001).isEmpty());}
 @Test void startupDatabaseMisconfigurationWithoutHealthyPersistenceIsNotAutoReported(){var d=new AutomaticReportDetector();var f=new Observation(true,true,true,false,false,false,true,null);d.observe(f,0);assertTrue(d.observe(f,600000).isEmpty());}
 @Test void writerFailureAfterHealthySessionQualifies(){var d=new AutomaticReportDetector();d.observe(healthy(),0);var f=new Observation(true,true,true,false,false,false,true,null);d.observe(f,1);assertEquals(Signature.PERSISTENCE_GENERIC_FAILURE,d.observe(f,120001).orElseThrow().signature());}
 @Test void slowSalesAndNormalHealthyPausedWaitingDoNotReport(){var d=new AutomaticReportDetector();assertTrue(d.observe(healthy(),0).isEmpty());assertTrue(d.observe(healthy(),999999999).isEmpty());}
 @Test void optOutManualStopAndIntentionalRecoveryResetTheGrace(){for(int i=0;i<3;i++){var d=new AutomaticReportDetector();d.observe(healthy(),0);d.observe(refused(),1);d.observe(new Observation(i!=0,true,i!=1,i==2,true,true,false,null),120001);assertTrue(d.observe(refused(),120002).isEmpty());}}
 @Test void disconnectClearsOldFailureAndHealthBaseline(){var d=new AutomaticReportDetector();d.observe(healthy(),0);d.observe(refused(),1);d.observe(new Observation(true,false,true,false,false,false,true,null),120001);assertTrue(d.observe(refused(),999999).isEmpty());}
 @Test void laterSuccessDetailCannotEraseSpecificRefusalCause(){var d=new AutomaticReportDetector();d.observe(healthy(),0);d.observe(refused(),1);var generic=new Observation(true,true,true,false,true,true,false,Signature.CHECKPOINT_REJECTED);assertEquals(Signature.IMMUTABLE_POSITION_IDENTITY_CONFLICT,d.observe(generic,120001).orElseThrow().signature());}
 @Test void newCauseGetsNewRecoveryGrace(){var d=new AutomaticReportDetector();d.observe(healthy(),0);d.observe(refused(),1);var failed=new Observation(true,true,true,false,false,false,true,null);assertTrue(d.observe(failed,120001).isEmpty());assertTrue(d.observe(failed,240001).isPresent());}
 @Test void classificationNeverReturnsExceptionMessages(){assertEquals(Signature.CHECKPOINT_REJECTED,classifyRefusal("token=secret user=OtherPlayer C:\\Users\\Private"));assertEquals(Signature.RETAIL_CHILD_ID_COLLISION,classifyRefusal("Retail child position id is already in use"));}
}
