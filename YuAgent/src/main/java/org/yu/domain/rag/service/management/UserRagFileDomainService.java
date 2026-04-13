package org.yu.domain.rag.service.management;

import org.springframework.stereotype.Service;
import org.yu.domain.rag.model.UserRagFileEntity;
import org.yu.domain.rag.repository.UserRagFileRepository;

@Service
public class UserRagFileDomainService {

    private final UserRagFileRepository userRagFileRepository;

    public UserRagFileDomainService(UserRagFileRepository userRagFileRepository) {
        this.userRagFileRepository = userRagFileRepository;
    }

    public UserRagFileEntity getById(String id) {
        return userRagFileRepository.selectById(id);
    }
}
