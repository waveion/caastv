package com.caastv.tvapp.model.data.validation


data class OtpValidationResponse(
    val verificationId: Int,
    val mobileNumber: String,
    val verificationStatus: String,
    val responseCode: Int,
    val errorMessage: String?,
    val transactionId: String?,
    val authToken: String?
)
