package com.smartambulance.patient

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartambulance.patient.network.RetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class AuthStatus {
    object Idle : AuthStatus()
    object Loading : AuthStatus()
    object CodeSent : AuthStatus()
    data class Authenticated(val role: String, val isProfileComplete: Boolean) : AuthStatus()
    data class Error(val message: String) : AuthStatus()
}

class AuthViewModel : ViewModel() {
    var authStatus by mutableStateOf<AuthStatus>(AuthStatus.Idle)
    var timeLeft by mutableStateOf(0)
    var resendCount by mutableStateOf(0)
    val maxResends = 3

    private val maxVerifyAttempts = 5
    private var verifyAttempts by mutableStateOf(0)
    private var timerJob: Job? = null
    private var otpRequestInFlight = false
    private var pendingEmail: String? = null

    init {
        checkAuthStatus()
    }

    fun checkAuthStatus() {
        if (UserSession.isPatientLoggedIn) {
            authStatus = AuthStatus.Authenticated("patient", true)
        }
    }

    fun registerDriver(name: String, username: String, password: String, ambulanceNo: String, phone: String, upiId: String) {
        if (name.isBlank() || username.isBlank() || password.isBlank() || ambulanceNo.isBlank() || phone.isBlank() || upiId.isBlank()) {
            authStatus = AuthStatus.Error("Please fill all fields including UPI ID.")
            return
        }
        if (otpRequestInFlight) {
            authStatus = AuthStatus.Error("OTP request already in progress. Please wait.")
            return
        }

        authStatus = AuthStatus.Loading
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.registerDriver(
                    com.smartambulance.patient.network.DriverRegisterPayload(name, username, password, ambulanceNo, phone, upiId)
                )
                if (response.status == "success") {
                    UserSession.role = "patient"
                    UserSession.email = email
                    pendingEmail = null
                    timerJob?.cancel()
                    timeLeft = 0
                    authStatus = AuthStatus.Authenticated("patient", UserSession.isPatientLoggedIn)
                } else {
                    authStatus = AuthStatus.Error(response.message ?: "Registration failed.")
                }
            } catch (e: Exception) {
                authStatus = AuthStatus.Error("Network error. Please try again.")
            }
        }
    }

    fun sendEmailOtp(email: String) {
        val cleanEmail = email.trim().lowercase()
        if (!isValidEmail(cleanEmail)) {
            authStatus = AuthStatus.Error("Please enter a valid email address.")
            return
        }
        if (otpRequestInFlight) {
            authStatus = AuthStatus.Error("OTP request already in progress. Please wait.")
            return
        }
        if (resendCount >= maxResends) {
            authStatus = AuthStatus.Error("Max resend attempts reached. Try again later.")
            return
        }
        if (pendingEmail == cleanEmail && timeLeft > 0) {
            authStatus = AuthStatus.Error("Please wait $timeLeft seconds before resending OTP.")
            return
        }

        authStatus = AuthStatus.Loading
        otpRequestInFlight = true

        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.sendEmailOtp(mapOf("email" to cleanEmail))
                if (response.status == "success") {
                    pendingEmail = cleanEmail
                    UserSession.email = cleanEmail
                    UserSession.role = "patient"
                    verifyAttempts = 0
                    if (timeLeft == 0) resendCount++
                    timeLeft = 30
                    authStatus = AuthStatus.CodeSent
                    startTimer()
                } else {
                    authStatus = AuthStatus.Error(response.message ?: "Could not send OTP.")
                }
            } catch (e: Exception) {
                authStatus = AuthStatus.Error("Network error. Check your backend server and try again.")
            } finally {
                otpRequestInFlight = false
            }
        }
    }

    fun verifyOtp(otp: String) {
        val email = pendingEmail ?: UserSession.email
        if (authStatus is AuthStatus.Loading) return
        if (!isValidEmail(email)) {
            authStatus = AuthStatus.Error("Invalid session. Please request OTP again.")
            return
        }
        if (verifyAttempts >= maxVerifyAttempts) {
            authStatus = AuthStatus.Error("Too many invalid OTP attempts. Please request a new OTP.")
            return
        }
        if (otp.length != 6) {
            authStatus = AuthStatus.Error("Please enter a valid 6-digit OTP.")
            return
        }

        authStatus = AuthStatus.Loading
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.verifyEmailOtp(
                    mapOf("email" to email, "otp" to otp)
                )
                if (response.status == "success") {
                    UserSession.role = "patient"
                    UserSession.email = email
                    pendingEmail = null
                    timerJob?.cancel()
                    timeLeft = 0
                    authStatus = AuthStatus.Authenticated("patient", UserSession.isPatientLoggedIn)
                } else {
                    verifyAttempts++
                    authStatus = AuthStatus.Error(response.message ?: "Invalid OTP. Please try again.")
                }
            } catch (e: Exception) {
                authStatus = AuthStatus.Error("Network error. Please try again.")
            }
        }
    }

    fun signOut() {
        UserSession.role = ""
        UserSession.isProfileComplete = false
        pendingEmail = null
        resendCount = 0
        verifyAttempts = 0
        timeLeft = 0
        timerJob?.cancel()
        authStatus = AuthStatus.Idle
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
