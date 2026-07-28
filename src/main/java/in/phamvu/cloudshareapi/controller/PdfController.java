package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.dto.PdfJobDTO;
import in.phamvu.cloudshareapi.dto.request.CompressPdfRequestDTO;
import in.phamvu.cloudshareapi.service.PdfJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pdf")
@RequiredArgsConstructor
@Slf4j(topic = "PDF-CONTROLLER")
public class PdfController {

    private final PdfJobService pdfJobService;

    /**
     * API to submit an async PDF compress job. Returns 202 Accepted since the result
     * file does not exist yet - only the job record does. Poll GET /pdf/jobs/{id}
     * for completion.
     */
    @PostMapping("/compress")
    public ResponseEntity<PdfJobDTO> submitCompressJob(@Valid @RequestBody CompressPdfRequestDTO dto) {
        log.info("Initiating compress PDF job API for file ID: {}", dto.getFileId());
        PdfJobDTO job = pdfJobService.submitCompressJob(dto);
        log.info("Successfully submitted compress job with ID: {}", job.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    /**
     * API to fetch the status/result of a specific PDF job (owner-only).
     */
    @GetMapping("/jobs/{id}")
    public ResponseEntity<PdfJobDTO> getJob(@PathVariable("id") String id) {
        log.info("Initiating fetch PDF job API for job ID: {}", id);
        PdfJobDTO job = pdfJobService.getJob(id);
        return ResponseEntity.ok(job);
    }

    /**
     * API to list all PDF jobs belonging to the currently authenticated user.
     */
    @GetMapping("/jobs")
    public ResponseEntity<List<PdfJobDTO>> listJobs() {
        log.info("Initiating fetch PDF jobs API for the current user");
        List<PdfJobDTO> jobs = pdfJobService.listJobs();
        return ResponseEntity.ok(jobs);
    }
}
