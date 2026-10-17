package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.*;
import static org.junit.jupiter.api.Assertions.*;
class DashboardStoreTest {
 @TempDir Path root;
 @Test void privateCredentialRoundTripsAndLocalUnlinkRemovesIt() throws Exception {
  var store=new DashboardStore(root.resolve("instance-a"));assertNull(store.load());var link=new DashboardStore.Link(12,"gni1_"+"A".repeat(43),"Laptop");store.save(link);assertEquals(link.credential,store.load().credential);assertFalse(link.toString().contains(link.credential));
  Path file=root.resolve("instance-a/gonuts-dashboard/installation.json");var acl=Files.getFileAttributeView(file,AclFileAttributeView.class);
  if(acl!=null){assertEquals(1,acl.getAcl().size());assertEquals(Files.getOwner(file),acl.getAcl().getFirst().principal());}
  else assertEquals("rw-------",PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
  assertNull(new DashboardStore(root.resolve("instance-b")).load());store.forget();assertNull(store.load());
 }
 @Test void sameHiveUsesRandomStableIdWithoutPathsAndDifferentHivesDiffer() throws Exception {Path shared=root.resolve("private-machine/shared-config");String a=DashboardStore.hive(shared);assertEquals(a,DashboardStore.hive(shared));assertTrue(a.matches("hive_[a-f0-9]{32}"));assertFalse(a.contains("private"));assertNotEquals(a,DashboardStore.hive(root.resolve("other-hive")));}
 @Test void invalidCredentialCannotBePersisted(){assertThrows(IllegalArgumentException.class,()->new DashboardStore.Link(12,"bad","Laptop"));assertThrows(IllegalArgumentException.class,()->new DashboardStore.Link(-1,"gni1_"+"A".repeat(43),"Laptop"));}
}
