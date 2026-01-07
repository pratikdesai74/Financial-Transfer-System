package com.pratikdesai.transfers.controller;

import com.pratikdesai.transfers.dto.request.TransferRequest;
import com.pratikdesai.transfers.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<Void> createTransaction(@Valid @RequestBody TransferRequest request) {
        log.info("Received transfer request from account {} to account {}",
                request.getSourceAccountId(), request.getDestinationAccountId());
        transferService.transfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
