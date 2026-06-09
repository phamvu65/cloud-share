package in.phamvu.cloudshareapi.exceptions;

import in.phamvu.cloudshareapi.dto.response.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.Date;

import static org.springframework.http.HttpStatus.CONFLICT;
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

}
