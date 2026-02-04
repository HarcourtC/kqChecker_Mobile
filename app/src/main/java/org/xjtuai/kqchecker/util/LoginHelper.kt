package org.xjtuai.kqchecker.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import org.xjtuai.kqchecker.auth.TokenManager
import org.xjtuai.kqchecker.auth.WebLoginActivity

/**
 * Unified login flow handler to reduce duplicate Intent creation
 */
object LoginHelper {
  private const val TAG = "LoginHelper"

  fun createLoginIntent(context: Context): Intent {
    val config = ConfigHelper.getConfig(context)
    return Intent(context, WebLoginActivity::class.java).apply {
      putExtra(WebLoginActivity.EXTRA_LOGIN_URL, config.authLoginUrl)
      putExtra(WebLoginActivity.EXTRA_REDIRECT_PREFIX, config.authRedirectPrefix)
    }
  }

  fun launchLogin(context: Context, launcher: ActivityResultLauncher<Intent>) {
    launcher.launch(createLoginIntent(context))
  }

  /**
   * Creates a callback to handle login results
   *
   * @param context Android context
   * @param onSuccess Called with (token, sourceLabel) on successful login
   * @param onFailure Called when login fails or is canceled
   */
  fun createLoginResultHandler(
    context: Context,
    onSuccess: (token: String, source: String) -> Unit,
    onFailure: () -> Unit
  ): ActivityResultCallback<ActivityResult> = ActivityResultCallback { result ->
    val data = result.data
    val token = data?.getStringExtra(WebLoginActivity.RESULT_TOKEN)
    val tokenSource = data?.getStringExtra(WebLoginActivity.RESULT_TOKEN_SOURCE)

    if (token != null) {
      val srcLabel = getTokenSourceLabel(tokenSource)
      Log.d(TAG, "Login success, source=$tokenSource")
      onSuccess(token, srcLabel)
    } else {
      // Fallback: check TokenManager for saved token
      val saved = try {
        TokenManager(context).getAccessToken()
      } catch (e: Exception) {
        Log.e(TAG, "Error reading saved token", e)
        null
      }

      if (saved != null) {
        Log.d(TAG, "Login success (fallback to saved token)")
        onSuccess(saved, "saved")
      } else {
        Log.d(TAG, "Login failed or canceled")
        onFailure()
      }
    }
  }

  fun getTokenSourceLabel(tokenSource: String?): String = when (tokenSource) {
    "url" -> "URL parameter"
    "local" -> "LocalStorage"
    "saved" -> "saved"
    else -> tokenSource ?: "unknown"
  }
}
