package com.pratikdesai.transfers.service;

import com.pratikdesai.transfers.dto.request.TransferRequest;

public interface TransferService {

    void transfer(TransferRequest request);
}
