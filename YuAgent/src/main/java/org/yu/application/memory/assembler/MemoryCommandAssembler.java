package org.yu.application.memory.assembler;

import org.yu.domain.memory.model.CandidateMemory;
import org.yu.domain.memory.model.MemoryType;
import org.yu.interfaces.dto.memory.CreateMemoryRequest;

public class MemoryCommandAssembler {

    public static CandidateMemory toCandidate(CreateMemoryRequest req) {
        CandidateMemory cm = new CandidateMemory();
        cm.setType(MemoryType.safeOf(req.getType()));
        cm.setText(req.getText());
        cm.setImportance(req.getImportance());
        cm.setTags(req.getTags());
        cm.setData(req.getData());
        return cm;
    }
}
