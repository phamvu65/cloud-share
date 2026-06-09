package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.document.UserDocument;
import in.phamvu.cloudshareapi.dto.response.UserResponseDTO;
import in.phamvu.cloudshareapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public UserResponseDTO getUserProfile(String userId){
          UserDocument user = userRepository.findById(userId).orElseThrow(()-> new RuntimeException("User not found"));

        return UserResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles())
                .build();
    }
}
