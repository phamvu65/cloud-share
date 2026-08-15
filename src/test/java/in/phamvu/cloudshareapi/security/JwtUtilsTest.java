package in.phamvu.cloudshareapi.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private static final String SECRET = "test-only-secret-key-that-is-long-enough-for-hs256-1234567890";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 60_000L);
        ReflectionTestUtils.setField(jwtUtils, "jwtRefreshExpirationMs", 3_600_000L);
        jwtUtils.init();
    }

    @Test
    void generateAccessToken_roundTripsSubjectTypeAndSid() {
        String token = jwtUtils.generateAccessToken("user@example.com", "user-1", java.util.Set.of("USER"), "session-1");

        assertThat(jwtUtils.validateJwtToken(token)).isTrue();
        assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("user@example.com");
        assertThat(jwtUtils.getTokenTypeFromJwtToken(token)).isEqualTo("ACCESS");
        assertThat(jwtUtils.getSidFromJwtToken(token)).isEqualTo("session-1");
    }

    @Test
    void generateRefreshToken_roundTripsAsRefreshType() {
        String token = jwtUtils.generateRefreshToken("user@example.com", "user-1", "session-1");

        assertThat(jwtUtils.validateJwtToken(token)).isTrue();
        assertThat(jwtUtils.getTokenTypeFromJwtToken(token)).isEqualTo("REFRESH");
    }

    @Test
    void validateJwtToken_malformedToken_returnsFalse() {
        assertThat(jwtUtils.validateJwtToken("not-a-real-jwt-token")).isFalse();
    }

    @Test
    void validateJwtToken_expiredToken_returnsFalse() {
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", -1_000L);

        String alreadyExpiredToken = jwtUtils.generateAccessToken("user@example.com", "user-1", java.util.Set.of("USER"), "session-1");

        assertThat(jwtUtils.validateJwtToken(alreadyExpiredToken)).isFalse();
    }

    @Test
    void validateJwtToken_signedWithDifferentKey_returnsFalse() {
        JwtUtils otherJwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(otherJwtUtils, "jwtSecret", "a-completely-different-secret-key-of-sufficient-length-000000");
        ReflectionTestUtils.setField(otherJwtUtils, "jwtExpirationMs", 60_000L);
        ReflectionTestUtils.setField(otherJwtUtils, "jwtRefreshExpirationMs", 3_600_000L);
        otherJwtUtils.init();

        String tokenFromOtherIssuer = otherJwtUtils.generateAccessToken("user@example.com", "user-1", java.util.Set.of("USER"), "session-1");

        assertThat(jwtUtils.validateJwtToken(tokenFromOtherIssuer)).isFalse();
    }
}
