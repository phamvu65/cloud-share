package in.phamvu.cloudshareapi.dto;

public record PdfJobResultFile(byte[] content, String fileName, String contentType) {
}
