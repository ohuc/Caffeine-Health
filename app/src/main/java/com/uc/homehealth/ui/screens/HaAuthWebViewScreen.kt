package com.uc.homehealth.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uc.homehealth.data.HA_REDIRECT_URI
import com.uc.homehealth.ui.viewmodel.HaAuthViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HaAuthWebViewScreen(
    haUrl: String,
    onSuccess: () -> Unit,
    viewModel: HaAuthViewModel = hiltViewModel(),
) {
    val authComplete by viewModel.authComplete.collectAsStateWithLifecycle()
    val authUrl = remember(haUrl) { viewModel.buildAuthUrl(haUrl) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(authComplete) {
        if (authComplete) onSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    @Suppress("DEPRECATION")
                    settings.databaseEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val url = request.url.toString()
                            if (url.startsWith(HA_REDIRECT_URI)) {
                                val code = request.url.getQueryParameter("code")
                                if (code != null) viewModel.exchangeCode(haUrl, code)
                                return true
                            }
                            return false
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                        }
                    }
                    loadUrl(authUrl)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
