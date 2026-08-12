package in.phamvu.cloudshareapi.controller;

import in.phamvu.cloudshareapi.dto.PdfJobDTO;
import in.phamvu.cloudshareapi.dto.PdfJobResultFile;
import in.phamvu.cloudshareapi.dto.request.CompressPdfRequestDTO;
import in.phamvu.cloudshareapi.dto.request.ConvertFromPdfRequestDTO;
import in.phamvu.cloudshareapi.dto.request.ConvertToPdfRequestDTO;
import in.phamvu.cloudshareapi.dto.request.TranslatePdfRequestDTO;
import in.phamvu.cloudshareapi.service.PdfJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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
     * API to submit an async DOCX translate job. Returns 202 Accepted since the result
     * file does not exist yet - only the job record does. Poll GET /pdf/jobs/{id}
     * for completion.
     */
    @PostMapping("/translate")
    public ResponseEntity<PdfJobDTO> submitTranslateJob(@Valid @RequestBody TranslatePdfRequestDTO dto) {
        log.info("Initiating translate DOCX job API for file ID: {}", dto.getFileId());
        PdfJobDTO job = pdfJobService.submitTranslateJob(dto);
        log.info("Successfully submitted translate job with ID: {}", job.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    /**
     * API to submit an async "convert from PDF" job (PDF -&gt; Word/PNG/JPG/PPTX/Excel/HTML).
     * Returns 202 Accepted since the result file does not exist yet - only the job record
     * does. Poll GET /pdf/jobs/{id} for completion.
     *
     * <p>Known limitation: WORD/HTML targets go through LibreOffice's {@code writer_pdf_import}
     * filter, which unreliably reconstructs text from PDFs with subsetted Type0/CID fonts - the
     * norm for PDFs exported from Word/Google Docs/Chrome "Print to PDF". Such jobs may complete
     * with garbled text (HTML) or an empty document (WORD) even though the source PDF is valid.
     * See {@link in.phamvu.cloudshareapi.service.LibreOfficeConversionService}.
     */
    @PostMapping("/from-pdf")
    public ResponseEntity<PdfJobDTO> submitConvertFromPdfJob(@Valid @RequestBody ConvertFromPdfRequestDTO dto) {
        log.info("Initiating convert-from-PDF job API for file ID: {} to {}", dto.getFileId(), dto.getTargetFormat());
        PdfJobDTO job = pdfJobService.submitConvertFromPdfJob(dto);
        log.info("Successfully submitted convert-from-PDF job with ID: {}", job.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    /**
     * API to submit an async "convert to PDF" job (Word/PNG/JPG/PPTX/Excel/HTML -&gt; PDF).
     * Source format is auto-detected from the file's stored type. Returns 202 Accepted
     * since the result file does not exist yet - only the job record does. Poll
     * GET /pdf/jobs/{id} for completion.
     */
    @PostMapping("/to-pdf")
    public ResponseEntity<PdfJobDTO> submitConvertToPdfJob(@Valid @RequestBody ConvertToPdfRequestDTO dto) {
        log.info("Initiating convert-to-PDF job API for file ID: {}", dto.getFileId());
        PdfJobDTO job = pdfJobService.submitConvertToPdfJob(dto);
        log.info("Successfully submitted convert-to-PDF job with ID: {}", job.getId());
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

    /**
     * API to download a completed job's result (owner-only). The result is not stored
     * permanently in the system - this call is destructive: the result (and, once unused, the
     * source file) is deleted from the server right after being streamed back once. Results
     * left undownloaded are purged automatically once they expire.
     */
    @GetMapping("/jobs/{id}/download")
    public ResponseEntity<ByteArrayResource> downloadJobResult(@PathVariable("id") String id) {
        log.info("Initiating download for PDF job result. Job ID: {}", id);
        PdfJobResultFile result = pdfJobService.downloadResult(id);
        log.info("Successfully streamed and purged result for PDF job ID: {}", id);
        return ResponseEntity.ok()
                .contentType(resolveContentType(result.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .contentLength(result.content().length)
                .body(new ByteArrayResource(result.content()));
    }

    private MediaType resolveContentType(String storedType) {
        if (!StringUtils.hasText(storedType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(storedType);
        } catch (Exception e) {
            log.warn("Stored result type '{}' is not a valid media type, falling back to octet-stream", storedType);
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
