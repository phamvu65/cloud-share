package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.document.UserCredits;
import in.phamvu.cloudshareapi.dto.FileMetaDataDTO;
import in.phamvu.cloudshareapi.service.FileMetaDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
public class FileController {

    private final FileMetaDataService fileMetadataService;
    private final in.phamvu.cloudshareapi.service.UserCreditsService userCreditsService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(@RequestPart("files") MultipartFile files[]) throws IOException {
        Map<String, Object> response = new HashMap<>();
        List<FileMetaDataDTO> list = fileMetadataService.uploadFiles(files);

        UserCredits finalCredits = userCreditsService.getUserCredits();

        response.put("files", list);
        response.put("remainingCredits", finalCredits.getCredits());
        return ResponseEntity.ok(response);
    }

}