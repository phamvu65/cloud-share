package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.UserDocument;
import in.phamvu.cloudshareapi.dto.request.SetPasswordRequestDTO;
import in.phamvu.cloudshareapi.dto.request.UpdateUserRequestDTO;
import in.phamvu.cloudshareapi.dto.response.UserResponseDTO;
import in.phamvu.cloudshareapi.exceptions.InvalidDataException;
import in.phamvu.cloudshareapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponseDTO getUserProfile(String userId){
        UserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User profile not found: {}", userId);
                    return new InvalidDataException("User not found");
                });

        return toResponseDTO(user);
    }

    public UserResponseDTO updateUser(String userId, UpdateUserRequestDTO request){
        log.info("Updating profile for user: {}", userId);

        UserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Update failed - user not found: {}", userId);
                    return new InvalidDataException("User not found");
                });

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhotoUrl(request.getPhotoUrl());

        if (StringUtils.hasText(request.getUsername())) {
            String newUsername = request.getUsername();
            userRepository.findByUsername(newUsername)
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(existing -> {
                        log.warn("Update failed - username '{}' already taken by another user", newUsername);
                        throw new InvalidDataException("Username already exists");
                    });
            user.setUsername(newUsername);
            log.info("Username set to '{}' for user: {}", newUsername, userId);
        }

        UserDocument updatedUser = userRepository.save(user);
        log.info("User profile updated successfully: {}", userId);

        return toResponseDTO(updatedUser);
    }

    public void setPassword(String userId, SetPasswordRequestDTO request){
        log.info("Password change requested for user: {}", userId);

        UserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Password change failed - user not found: {}", userId);
                    return new InvalidDataException("User not found");
                });

        // If the account already has a password, the current one must be provided and match
        boolean hasExistingPassword = StringUtils.hasText(user.getPassword());
        if (hasExistingPassword) {
            if (!StringUtils.hasText(request.getCurrentPassword())) {
                log.warn("Password change failed - current password not provided for user: {}", userId);
                throw new InvalidDataException("Current password is required");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                log.warn("Password change failed - current password does not match for user: {}", userId);
                throw new InvalidDataException("Current password is incorrect");
            }
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password updated successfully for user: {}", userId);
    }

    public void deleteAccount(String userId){
        log.info("Account deletion requested for user: {}", userId);

        UserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Account deletion failed - user not found: {}", userId);
                    return new InvalidDataException("User not found");
                });

        if (user.isDeleted()) {
            log.warn("Account deletion skipped - user already deleted: {}", userId);
            throw new InvalidDataException("Account is already deleted");
        }

        user.setDeleted(true);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        log.info("Account soft-deleted successfully for user: {}", userId);
    }

    private UserResponseDTO toResponseDTO(UserDocument user){
        return UserResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .photoUrl(user.getPhotoUrl())
                .roles(user.getRoles())
                .build();
    }
}
