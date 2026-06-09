package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.UserDocument;
import in.phamvu.cloudshareapi.dto.userDTO;
import in.phamvu.cloudshareapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;

    public userDTO createProfile(userDTO userDTO){

        if (userRepository.existsById(userDTO.getId())) {
            return updateProfile(userDTO);
        }

        UserDocument profile = UserDocument.builder()
                .email(userDTO.getEmail())
                .firstName(userDTO.getFirstName())
                .lastName(userDTO.getLastName())
                .photoUrl(userDTO.getPhotoUrl())
                .createdAt(Instant.now())
                .build();

        profile= userRepository.save(profile);

        return userDTO.builder()
                .id(profile.getId())
                .email(profile.getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .photoUrl(profile.getPhotoUrl())
                .createdAt(profile.getCreatedAt())
                .build();
    }

    public userDTO updateProfile(userDTO userDTO) {
        Optional<UserDocument> existingProfile = userRepository.findById(userDTO.getId());

        if (existingProfile.isPresent()) {
            //update fields if provided
            if (userDTO.getEmail() != null && !userDTO.getEmail().isEmpty()) {
                existingProfile.get().setEmail(userDTO.getEmail());
            }

            if (userDTO.getFirstName() != null && !userDTO.getFirstName().isEmpty()) {
                existingProfile.get().setFirstName(userDTO.getFirstName());
            }

            if (userDTO.getLastName() != null && !userDTO.getLastName().isEmpty()) {
                existingProfile.get().setLastName(userDTO.getLastName());
            }

            if (userDTO.getPhotoUrl() != null && !userDTO.getPhotoUrl().isEmpty()) {
                existingProfile.get().setPhotoUrl(userDTO.getPhotoUrl());
            }


            return userDTO.builder()
                    .id(existingProfile.get().getId())
                    .email(existingProfile.get().getEmail())
                    .firstName(existingProfile.get().getFirstName())
                    .lastName(existingProfile.get().getLastName())
                    .createdAt(existingProfile.get().getCreatedAt())
                    .photoUrl(existingProfile.get().getPhotoUrl())
                    .build();
        }
        return null;
    }



    public void deleteProfile(String clerkId) {
        Optional<UserDocument>  existingProfile = userRepository.findById(clerkId);
        if (existingProfile != null) {
        }
    }

    public UserDocument getCurrenProfile(){
        if(SecurityContextHolder.getContext().getAuthentication() == null ){
            throw new UsernameNotFoundException("User not authenticated");
        }
       String clerkId = SecurityContextHolder.getContext().getAuthentication().getName();
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        Optional<UserDocument> profile = userRepository.findById(clerkId);
        if (profile.isEmpty()) {
            profile = Optional.of(new UserDocument());
            profile.get().setEmail(email);
            profile.get().setCreatedAt(Instant.now());

            log.info("Created new profile for user: {}", email);
        }
        return null;
    }
}
