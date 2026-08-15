package in.phamvu.cloudshareapi.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentParserTest {

    @ParameterizedTest(name = "{0} -> os={1}, browser={2}")
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36|Windows|Chrome 115.0.0.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Safari/605.1.15|macOS|Safari 16.5",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Firefox/117.0|Linux|Firefox 117.0",
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36|Android|Chrome 115.0.0.0",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Mobile/15E148 Safari/604.1|iOS|Safari 16.5",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36 Edg/115.0.1901.183|Windows|Edge 115.0.1901.183",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36 OPR/101.0.0.0|Windows|Opera 101.0.0.0",
    })
    void parse_recognizesOsAndBrowser(String userAgent, String expectedOs, String expectedBrowser) {
        UserAgentParser.ParsedUserAgent result = UserAgentParser.parse(userAgent);

        assertThat(result.os()).isEqualTo(expectedOs);
        assertThat(result.browser()).isEqualTo(expectedBrowser);
    }

    @Test
    void parse_blankUserAgent_returnsUnknown() {
        UserAgentParser.ParsedUserAgent result = UserAgentParser.parse("   ");

        assertThat(result.os()).isEqualTo("Unknown");
        assertThat(result.browser()).isEqualTo("Unknown");
    }

    @Test
    void parse_nullUserAgent_returnsUnknown() {
        UserAgentParser.ParsedUserAgent result = UserAgentParser.parse(null);

        assertThat(result.os()).isEqualTo("Unknown");
        assertThat(result.browser()).isEqualTo("Unknown");
    }

    @Test
    void parse_unrecognizedUserAgent_returnsUnknown() {
        UserAgentParser.ParsedUserAgent result = UserAgentParser.parse("SomeBot/1.0 (+https://example.com/bot)");

        assertThat(result.os()).isEqualTo("Unknown");
        assertThat(result.browser()).isEqualTo("Unknown");
    }
}
