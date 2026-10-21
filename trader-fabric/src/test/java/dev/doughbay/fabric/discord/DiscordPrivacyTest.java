package dev.doughbay.fabric.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DiscordPrivacyTest {
    private static final List<String> ONE = List.of("steve_trades");
    private static final List<String> TWO = List.of("steve_trades", "alexalt");

    @Test
    void ourNameBecomesAccountWhereverItIsWrittenAndNobodyElsesIsTouched() {
        assertEquals("Account: sold 64 TNT to Buyer123",
                DiscordPrivacy.hide("steve_trades: sold 64 TNT to Buyer123", ONE));
        assertEquals("Account (this client)", DiscordPrivacy.hide("STEVE_TRADES (this client)", ONE));
        // Part of a longer name is somebody else.
        assertEquals("steve_trades2 bought it", DiscordPrivacy.hide("steve_trades2 bought it", ONE));
        assertEquals("nothing here", DiscordPrivacy.hide("nothing here", ONE));
        assertEquals("steve_trades", DiscordPrivacy.hide("steve_trades", List.of()));
    }

    @Test
    void twoAccountsStayToldApart() {
        assertEquals("Account 1 lent Account 2 5M", DiscordPrivacy.hide("steve_trades lent alexalt 5M", TWO));
    }

    @Test
    void allThreeSendersUseTheSameAccountNumbersInsteadOfEachCallingItselfAccountOne() {
        var roster=List.of("Charlie","Alpha","Bravo");
        for(String source:roster) {
            var ordered=DiscordPrivacy.orderedNames(source,roster);
            assertEquals(List.of("Alpha","Bravo","Charlie"),ordered);
            assertEquals("Account 1 / Account 2 / Account 3",DiscordPrivacy.hide("Alpha / Bravo / Charlie",ordered));
        }
        assertEquals(List.of("Alpha","Bravo","Charlie"),DiscordPrivacy.orderedNames("charlie",List.of("Bravo","Alpha","Charlie","ALPHA")));
    }

    @Test
    void aMessageKeepsItsIdsAndLosesOnlyTheNamesAPersonReads() throws Exception {
        String json = "{\"content\":\"steve_trades is trading\","
                + "\"embeds\":[{\"title\":\"steve_trades\",\"description\":\"paid Buyer123\","
                + "\"thumbnail\":{\"url\":\"https://mc-heads.net/avatar/steve_trades/64\"},"
                + "\"footer\":{\"text\":\"host steve_trades\"}}],"
                + "\"components\":[{\"type\":1,\"components\":[{\"type\":3,\"custom_id\":\"account:steve_trades\","
                + "\"options\":[{\"label\":\"steve_trades (this client)\",\"value\":\"steve_trades\"}]}]}]}";
        JsonNode out = new ObjectMapper().readTree(DiscordPrivacy.payload(json, ONE));
        assertEquals("Account is trading", out.path("content").asText());
        JsonNode embed = out.path("embeds").get(0);
        assertEquals("Account", embed.path("title").asText());
        assertEquals("paid Buyer123", embed.path("description").asText());
        assertEquals("https://mc-heads.net/avatar/MHF_Steve/64", embed.path("thumbnail").path("url").asText());
        assertEquals("host Account", embed.path("footer").path("text").asText());
        JsonNode menu = out.path("components").get(0).path("components").get(0);
        // What comes back when the option is picked has to stay the real name.
        assertEquals("account:steve_trades", menu.path("custom_id").asText());
        assertEquals("steve_trades", menu.path("options").get(0).path("value").asText());
        assertEquals("Account (this client)", menu.path("options").get(0).path("label").asText());
        assertFalse(out.toString().replace("account:steve_trades", "").replace("\"value\":\"steve_trades\"", "")
                .contains("steve_trades"));
    }

    @Test
    void aBodyThatIsNotJsonIsSentAsItWas() {
        assertEquals("not json steve_trades", DiscordPrivacy.payload("not json steve_trades", ONE));
        assertEquals("{}", DiscordPrivacy.payload("{}", List.of()));
    }

    @Test
    void onlyOurOwnHeadsAreSwapped() {
        assertEquals(DiscordPrivacy.PLAIN_HEAD, DiscordPrivacy.face("Steve_Trades", ONE));
        assertEquals("Buyer123", DiscordPrivacy.face("Buyer123", ONE));
    }
}
