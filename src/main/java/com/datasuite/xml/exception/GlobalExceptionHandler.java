package com.datasuite.xml.exception;

import com.datasuite.xml.dto.GlobalErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalErrorResponse> handleGenericException(Exception ex) {
        GlobalErrorResponse response = new GlobalErrorResponse("Internal Server Error: " + ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
