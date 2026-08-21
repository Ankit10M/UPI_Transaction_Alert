package com.upivoicealert.ui.auth

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OtpScreen(viewModel: OtpViewModel = hiltViewModel()) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current as Activity
    Column {
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone number") }
        )
        Button(onClick = { viewModel.sendOtp(phone, context) }) {
            Text("Send OTP")
        }
        if (state is OtpState.OTP_SENT) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("OTP") }
            )
            Button(onClick = { viewModel.verifyCode((state as OtpState.OTP_SENT).verificationId, code) }) {
                Text("Verify")
            }
            Button(onClick = { viewModel.resendOtp(context) }) {
                Text("Resend OTP")
            }
        }
        if (state is OtpState.FAILED) {
            Text("Authentication failed")
        }
    }
}
