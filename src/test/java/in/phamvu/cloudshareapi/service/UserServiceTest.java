package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.UserDocument;
import in.phamvu.cloudshareapi.dto.request.SetPasswordRequestDTO;
import in.phamvu.cloudshareapi.dto.request.UpdateUserRequestDTO;
import in.phamvu.cloudshareapi.dto.response.UserResponseDTO;
import in.phamvu.cloudshareapi.exceptions.InvalidDataException;
import in.phamvu.cloudshareapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    private UserDocument.UserDocumentBuilder baseUser() {
        return UserDocument.builder()
                .id("user-1")
                .email("user@example.com")
                .username("olduser")
                .firstName("Old")
                .lastName("Name")
                .roles(Set.of("ROLE_USER"));
    }

    @Test
    void getUserProfile_existingUser_returnsMappedDto() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(baseUser().build()));

        UserResponseDTO result = userService.getUserProfile("user-1");

        assertThat(result.getId()).isEqualTo("user-1");
        assertThat(result.getEmail()).isEqualTo("user@example.com");
        assertThat(result.getUsername()).isEqualTo("olduser");
    }

    @Test
    void getUserProfile_missingUser_throws() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile("missing"))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void updateUser_missingUser_throws() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        UpdateUserRequestDTO request = new UpdateUserRequestDTO();

        assertThatThrownBy(() -> userService.updateUser("missing", request))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void updateUser_noUsernameProvided_onlyUpdatesNameFields() {
        UserDocument existing = baseUser().build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateUserRequestDTO request = new UpdateUserRequestDTO();
        request.setFirstName("New");
        request.setLastName("Name2");

        UserResponseDTO result = userService.updateUser("user-1", request);

        assertThat(result.getFirstName()).isEqualTo("New");
        assertThat(result.getUsername()).isEqualTo("olduser");
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void updateUser_newUsernameAvailable_setsUsername() {
        UserDocument existing = baseUser().build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("newname")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateUserRequestDTO request = new UpdateUserRequestDTO();
        request.setFirstName("Old");
        request.setLastName("Name");
        request.setUsername("newname");

        UserResponseDTO result = userService.updateUser("user-1", request);

        assertThat(result.getUsername()).isEqualTo("newname");
    }

    @Test
    void updateUser_usernameTakenByAnotherUser_throwsAndDoesNotSave() {
        UserDocument existing = baseUser().build();
        UserDocument otherUser = baseUser().id("user-2").username("taken").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("taken")).thenReturn(Optional.of(otherUser));

        UpdateUserRequestDTO request = new UpdateUserRequestDTO();
        request.setFirstName("Old");
        request.setLastName("Name");
        request.setUsername("taken");

        assertThatThrownBy(() -> userService.updateUser("user-1", request))
                .isInstanceOf(InvalidDataException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_usernameUnchangedBelongsToSelf_allowed() {
        UserDocument existing = baseUser().build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("olduser")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateUserRequestDTO request = new UpdateUserRequestDTO();
        request.setFirstName("Old");
        request.setLastName("Name");
        request.setUsername("olduser");

        UserResponseDTO result = userService.updateUser("user-1", request);

        assertThat(result.getUsername()).isEqualTo("olduser");
    }

    @Test
    void setPassword_noExistingPassword_setsWithoutRequiringCurrentPassword() {
        UserDocument existing = baseUser().password(null).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("newpass123")).thenReturn("encoded-newpass");

        SetPasswordRequestDTO request = new SetPasswordRequestDTO();
        request.setNewPassword("newpass123");

        userService.setPassword("user-1", request);

        assertThat(existing.getPassword()).isEqualTo("encoded-newpass");
        verify(userRepository).save(existing);
    }

    @Test
    void setPassword_existingPassword_currentPasswordMissing_throws() {
        UserDocument existing = baseUser().password("encoded-old").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));

        SetPasswordRequestDTO request = new SetPasswordRequestDTO();
        request.setNewPassword("newpass123");

        assertThatThrownBy(() -> userService.setPassword("user-1", request))
                .isInstanceOf(InvalidDataException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void setPassword_existingPassword_wrongCurrentPassword_throws() {
        UserDocument existing = baseUser().password("encoded-old").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);

        SetPasswordRequestDTO request = new SetPasswordRequestDTO();
        request.setCurrentPassword("wrong");
        request.setNewPassword("newpass123");

        assertThatThrownBy(() -> userService.setPassword("user-1", request))
                .isInstanceOf(InvalidDataException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void setPassword_existingPassword_correctCurrentPassword_updates() {
        UserDocument existing = baseUser().password("encoded-old").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("correct", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("newpass123")).thenReturn("encoded-newpass");

        SetPasswordRequestDTO request = new SetPasswordRequestDTO();
        request.setCurrentPassword("correct");
        request.setNewPassword("newpass123");

        userService.setPassword("user-1", request);

        assertThat(existing.getPassword()).isEqualTo("encoded-newpass");
    }

    @Test
    void deleteAccount_missingUser_throws() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteAccount("missing"))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void deleteAccount_alreadyDeleted_throws() {
        UserDocument existing = baseUser().deleted(true).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.deleteAccount("user-1"))
                .isInstanceOf(InvalidDataException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteAccount_active_softDeletesAndStampsTimestamp() {
        UserDocument existing = baseUser().deleted(false).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));

        userService.deleteAccount("user-1");

        assertThat(existing.isDeleted()).isTrue();
        assertThat(existing.getDeletedAt()).isNotNull();
        verify(userRepository).save(existing);
    }
}