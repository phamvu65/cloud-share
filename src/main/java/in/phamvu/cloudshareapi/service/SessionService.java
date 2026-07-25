package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.SessionDocument;
import in.phamvu.cloudshareapi.dto.response.SessionResponseDTO;
import in.phamvu.cloudshareapi.exceptions.InvalidDataException;
import in.phamvu.cloudshareapi.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SessionService {
    private final SessionRepository sessionRepository;

    public List<SessionResponseDTO> listSessions(String userId, String currentSid) {
        return sessionRepository.findByUserIdAndRevokedFalse(userId).stream()
                .map(session -> toResponseDTO(session, currentSid))
                .toList();
    }

    public void revokeSession(String userId, String sessionId) {
        log.info("Session revocation requested for user: {}, session: {}", userId, sessionId);

        SessionDocument session = sessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> {
                    log.warn("Session revocation failed - session not found: {}", sessionId);
                    return new InvalidDataException("Session not found");
                });

        session.setRevoked(true);
        session.setRevokedAt(Instant.now());
        sessionRepository.save(session);

        log.info("Session revoked successfully: {}", sessionId);
    }

    public void revokeOtherSessions(String userId, String currentSid) {
        log.info("Revoking all other sessions for user: {}", userId);

        List<SessionDocument> sessions = sessionRepository.findByUserIdAndRevokedFalseAndIdNot(userId, currentSid);
        Instant now = Instant.now();

        sessions.forEach(session -> {
            session.setRevoked(true);
            session.setRevokedAt(now);
        });
        sessionRepository.saveAll(sessions);

        log.info("Revoked {} other session(s) for user: {}", sessions.size(), userId);
    }

    private SessionResponseDTO toResponseDTO(SessionDocument session, String currentSid) {
        return SessionResponseDTO.builder()
                .id(session.getId())
                .os(session.getOs())
                .browser(session.getBrowser())
                .ipAddress(session.getIpAddress())
                .createdAt(session.getCreatedAt())
                .lastUsedAt(session.getLastUsedAt())
                .current(session.getId().equals(currentSid))
                .build();
    }
}
