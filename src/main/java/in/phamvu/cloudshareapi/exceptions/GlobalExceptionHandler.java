package in.phamvu.cloudshareapi.exceptions;

import in.phamvu.cloudshareapi.dto.response.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.Date;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidDataException.class)
    @ResponseStatus(CONFLICT)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "409", description = "Conflict",
                    content = {@Content(mediaType = APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    name = "409 Response",
                                    summary = "Handle exception when input data is conflicted",
                                    value = """
                                            {
                                              "timestamp": "2023-10-19T06:07:35.321+00:00",
                                              "status": 409,
                                              "path": "/api/v1/...",
                                              "error": "Conflict",
                                              "message": "{data} exists, Please try again!"
                                            }
                                            """
                            ))})
    })
    public ErrorResponse handleInvalidDataException(InvalidDataException e, WebRequest request){

        return ErrorResponse.builder()
                .timestamp(new Date())
                .message(e.getMessage())
                .status(CONFLICT.value())
                .error(CONFLICT.getReasonPhrase())
                .timestamp(new Date())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(BAD_REQUEST)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException e, WebRequest request){

        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ErrorResponse.builder()
                .timestamp(new Date())
                .message(message)
                .status(BAD_REQUEST.value())
                .error(BAD_REQUEST.getReasonPhrase())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(UNAUTHORIZED)
    public ErrorResponse handleAuthenticationException(AuthenticationException e, WebRequest request){

        return ErrorResponse.builder()
                .timestamp(new Date())
                .message(e.getMessage())
                .status(UNAUTHORIZED.value())
                .error(UNAUTHORIZED.getReasonPhrase())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

    }

}
