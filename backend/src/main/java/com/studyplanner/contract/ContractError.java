package com.studyplanner.contract;

import java.util.List;

public record ContractError(
        String code,
        String message,
        List<String> objectIds) {

    public ContractError {
        code = ContractSupport.requireText(code, "code");
        message = ContractSupport.requireText(message, "message");
        objectIds = ContractSupport.immutableList(objectIds, "objectIds");
    }
}
