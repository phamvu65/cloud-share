package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.SessionDocument;
import in.phamvu.cloudshareapi.dto.response.SessionResponseDTO;
import in.phamvu.cloudshareapi.exceptions.InvalidDataException;
import in.phamvu.cloudshareapi.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository);
    }

    @Test
    void listSessions_marksCurrentSessionAndMapsFields() {
        SessionDocument current = SessionDocument.builder()
                .id("sid-1").userId("user-1").os("Windows").browser("Chrome 115").ipAddress("1.2.3.4")
                .createdAt(Instant.now()).lastUsedAt(Instant.now()).revoked(false).build();
        SessionDocument other = SessionDocument.builder()
                .id("sid-2").userId("user-1").os("macOS").browser("Safari 16").ipAddress("5.6.7.8")
                .createdAt(Instant.now()).lastUsedAt(Instant.now()).revoked(false).build();
        when(sessionRepository.findByUserIdAndRevokedFalse("user-1")).thenReturn(List.of(current, other));

        List<SessionResponseDTO> result = sessionService.listSessions("user-1", "sid-1");

        assertThat(result).hasSize(2);
        assertThat(result.stream().filter(SessionResponseDTO::isCurrent).map(SessionResponseDTO::getId))
                .containsExactly("sid-1");
    }

    @Test
    void revokeSession_ownedSession_marksRevokedAndSaves() {
        SessionDocument session = SessionDocument.builder().id("sid-1").userId("user-1").revoked(false).build();
        when(sessionRepository.findById("sid-1")).thenReturn(Optional.of(session));

        sessionService.revokeSession("user-1", "sid-1");

        ArgumentCaptor<SessionDocument> captor = ArgumentCaptor.forClass(SessionDocument.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().isRevoked()).isTrue();
        assertThat(captor.getValue().getRevokedAt()).isNotNull();
    }

    @Test
    void revokeSession_belongsToAnotherUser_throwsNotFound() {
        SessionDocument session = SessionDocument.builder().id("sid-1").userId("someone-else").revoked(false).build();
        when(sessionRepository.findById("sid-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.revokeSession("user-1", "sid-1"))
                .isInstanceOf(InvalidDataException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void revokeSession_notFound_throws() {
        when(sessionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.revokeSession("user-1", "missing"))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void revokeOtherSessions_revokesEverySessionExceptCurrent() {
        SessionDocument other1 = SessionDocument.builder().id("sid-2").userId("user-1").revoked(false).build();
        SessionDocument other2 = SessionDocument.builder().id("sid-3").userId("user-1").revoked(false).build();
        when(sessionRepository.findByUserIdAndRevokedFalseAndIdNot("user-1", "sid-1"))
                .thenReturn(List.of(other1, other2));

        sessionService.revokeOtherSessions("user-1", "sid-1");

        assertThat(other1.isRevoked()).isTrue();
        assertThat(other2.isRevoked()).isTrue();
        verify(sessionRepository).saveAll(anyList());
    }
}