package in.phamvu.cloudshareapi.service;

import in.phamvu.cloudshareapi.repository.FileMetaDataDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileMetaDataService {

    private final FileMetaDataDocumentRepository fileMetaDataDocumentRepository;
}
