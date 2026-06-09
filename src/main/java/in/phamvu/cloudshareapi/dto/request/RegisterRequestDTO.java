package in.phamvu.cloudshareapi.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequestDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String password;

    @NotBlank(message = "First name is required")
    @Size(min = 2,max=50, message = "First name must be at least 2 characters long")
    private String firstName;

    @Size(min = 2,max=50, message = "Last name must be at least 2 characters long")
    @NotBlank(message = "Last name is required")
    private String lastName;
}
