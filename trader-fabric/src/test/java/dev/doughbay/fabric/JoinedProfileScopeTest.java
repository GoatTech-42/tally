package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class JoinedProfileScopeTest {
    @Test void onlyNativeFreshJoinedProfilesSelectStrictRecovery() {
        Path root=Path.of("test-hive").toAbsolutePath();
        assertTrue(HiveSetup.joinedProfile(root,root.resolve("joined-profiles/third")));
        assertFalse(HiveSetup.joinedProfile(root,root));
        assertFalse(HiveSetup.joinedProfile(root,root.resolve("member-profiles/owner")));
        assertFalse(HiveSetup.joinedProfile(root,root.resolve("joined-profiles/third/nested")));
        assertFalse(HiveSetup.joinedProfile(root,root.resolve("elsewhere/third")));
    }
}
