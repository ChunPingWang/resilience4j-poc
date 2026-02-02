package com.example.order.infrastructure.adapter.out.payment.mapper;

import com.example.order.application.port.out.PaymentPort.PaymentResult;
import com.example.order.application.port.out.PaymentPort.PaymentStatus;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderId;
import com.example.order.infrastructure.adapter.out.payment.dto.PaymentRequest;
import com.example.order.infrastructure.adapter.out.payment.dto.PaymentResponse;
import org.springframework.stereotype.Component;

/**
 * Mapper between domain objects and payment service DTOs.
 */
@Component
public class PaymentMapper {

    public PaymentRequest toRequest(OrderId orderId, Money amount) {
        return PaymentRequest.of(
                orderId.getValue(),
                amount.getAmount(),
                amount.getCurrency()
        );
    }

    public PaymentResult toResult(PaymentResponse response) {
        PaymentStatus status = "SUCCESS".equalsIgnoreCase(response.status())
                ? PaymentStatus.SUCCESS
                : PaymentStatus.FAILED;

        String errorMessage = status == PaymentStatus.FAILED ? response.message() : null;

        return new PaymentResult(
                response.transactionId(),
                status,
                response.message(),
                errorMessage
        );
    }
}
