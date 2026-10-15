package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
class AutomaticReportJournalTest {
 @TempDir Path root; static final long DAY=86400000L; static String f(int n){return String.format("%064x",n);}
 AutomaticReportJournal journal(){return new AutomaticReportJournal(root.resolve("state.json"));}
 @Test void restartReusesQueuedIdentifier()throws Exception{var first=journal().reserve(f(1),DAY).orElseThrow();var second=journal().reserve(f(1),DAY+1).orElseThrow();assertEquals(first.retryKey(),second.retryKey());}
 @Test void sentReportIsSuppressedFor24Hours()throws Exception{var j=journal();var e=j.reserve(f(1),DAY).orElseThrow();j.finish(e.retryKey(),AutomaticReportJournal.Outcome.SENT,DAY+1);assertTrue(journal().reserve(f(1),DAY*2).isEmpty());assertTrue(journal().reserve(f(1),DAY*2+2).isPresent());}
 @Test void capIncludesCancelledReservationsAndCannotBeResetByReprepare()throws Exception{var j=journal();for(int i=0;i<3;i++){var e=j.reserve(f(1),DAY+i).orElseThrow();j.finish(e.retryKey(),AutomaticReportJournal.Outcome.CANCELLED,DAY+i);}assertTrue(journal().reserve(f(1),DAY+4).isEmpty());assertTrue(journal().reserve(f(2),DAY+4).isEmpty());}
 @Test void exhaustedRetriesDoNotCreateNewKeys()throws Exception{var j=journal();var e=j.reserve(f(1),DAY).orElseThrow();j.finish(e.retryKey(),AutomaticReportJournal.Outcome.EXHAUSTED,DAY+1);assertTrue(journal().reserve(f(1),DAY+2).isEmpty());}
 @Test void corruptedStateFailsClosedRatherThanForgettingDedupe()throws Exception{Files.writeString(root.resolve("state.json"),"not json");assertThrows(java.io.IOException.class,()->journal().reserve(f(1),DAY));}
 @Test void oversizedStateIsRefused()throws Exception{Files.writeString(root.resolve("state.json"),"x".repeat(65537));assertThrows(java.io.IOException.class,()->journal().reserve(f(1),DAY));}
 @Test void unknownEntryCannotBeMarkedSent()throws Exception{assertThrows(java.io.IOException.class,()->journal().finish("unknown-key-123",AutomaticReportJournal.Outcome.SENT,DAY));}
 @Test void expiredHistoryIsBoundedAndPruned()throws Exception{var j=journal();var e=j.reserve(f(1),DAY).orElseThrow();j.finish(e.retryKey(),AutomaticReportJournal.Outcome.SENT,DAY+1);j.reserve(f(2),DAY*10);assertFalse(Files.readString(root.resolve("state.json")).contains(e.retryKey()));}
 @Test void journalContainsNoIdentityOrReportText()throws Exception{journal().reserve(f(1),DAY);String state=Files.readString(root.resolve("state.json"));assertFalse(state.contains("username"));assertFalse(state.contains("diagnosticLog"));assertFalse(state.contains("description"));}
}
