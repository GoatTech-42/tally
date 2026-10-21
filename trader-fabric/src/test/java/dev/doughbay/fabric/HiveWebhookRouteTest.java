package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class HiveWebhookRouteTest {
 @TempDir Path root;
 @Test void joinedAccountWithBlankDestinationUsesEnabledExistingOwnerWithoutCopyingSettings()throws Exception {
  String settings="{\"webhook.enabled\":1,\"webhook.url\":\"https://example.invalid/existing-destination\"}";
  Files.writeString(root.resolve("tuning.json"),settings);
  assertEquals("https://example.invalid/existing-destination",HiveWebhookRoute.resolve("",root));
  assertEquals(settings,Files.readString(root.resolve("tuning.json")));
  try(var files=Files.list(root)){assertEquals(1,files.count());}
 }
 @Test void explicitLocalDestinationIsPreservedAndNoUnlinkedAccountCanInherit()throws Exception {
  Files.writeString(root.resolve("tuning.json"),"{\"webhook.enabled\":1,\"webhook.url\":\"https://example.invalid/shared\"}");
  assertEquals("https://example.invalid/own",HiveWebhookRoute.resolve(" https://example.invalid/own ",root));
  assertEquals("",HiveWebhookRoute.resolve("",null));
 }
 @Test void disabledMissingOrMalformedOwnerConfigurationCannotEnablePosting()throws Exception {
  assertEquals("",HiveWebhookRoute.resolve("",root));
  Files.writeString(root.resolve("tuning.json"),"{\"webhook.enabled\":0,\"webhook.url\":\"https://example.invalid/shared\"}");
  assertEquals("",HiveWebhookRoute.resolve("",root));
  Files.writeString(root.resolve("tuning.json"),"broken");assertEquals("",HiveWebhookRoute.resolve("",root));
 }
 @Test void ownersImmediateAlertOptOutDoesNotDisableItsHourlyReportOrGetBypassed()throws Exception {
  Files.writeString(root.resolve("tuning.json"),"{\"webhook.enabled\":1,\"webhook.alerts\":0,\"webhook.url\":\"https://example.invalid/shared\"}");
  assertEquals("https://example.invalid/shared",HiveWebhookRoute.resolve("",root,false));
  assertEquals("",HiveWebhookRoute.resolve("",root,true));
 }
}
