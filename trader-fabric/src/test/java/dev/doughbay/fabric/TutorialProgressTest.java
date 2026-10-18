package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TutorialProgressTest {
    @TempDir Path root;
    @Test void existingUsersAreNotInterruptedAfterUpgradeOrRestart(){
        assertFalse(new TutorialProgress(root,true).takeOffer());
        assertFalse(new TutorialProgress(root,true).takeOffer());
    }
    @Test void freshInstallOfferSurvivesRestartButIsShownOnlyOnce(){
        new TutorialProgress(root,false);
        assertTrue(new TutorialProgress(root,true).takeOffer());
        assertFalse(new TutorialProgress(root,true).takeOffer());
    }
    @Test void completingOptionalGuideDoesNotRearmFirstRun(){
        var state=new TutorialProgress(root,true);state.complete("items");
        var reloaded=new TutorialProgress(root,true);
        assertTrue(reloaded.isComplete("items"));assertFalse(reloaded.takeOffer());
    }
    @Test void unreadablePreferencesDoNotForceTourOrOverwriteData() throws Exception {
        Path path=root.resolve("tutorial-progress.json");Files.writeString(path,"broken");
        var state=new TutorialProgress(root,false);assertFalse(state.takeOffer());state.complete("setup");
        assertEquals("broken",Files.readString(path));
    }
}
