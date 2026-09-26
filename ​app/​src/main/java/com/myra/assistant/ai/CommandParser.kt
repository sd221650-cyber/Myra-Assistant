package com.myra.assistant.ai

sealed class SystemAction {
    data class MakeCall(val query: String) : SystemAction()
    data class OpenApp(val appName: String) : SystemAction()
    data class SendWhatsApp(val contact: String, val message: String) : SystemAction()
    data class ToggleFlashlight(val enable: Boolean) : SystemAction()
    data class AdjustVolume(val levelPercent: Int) : SystemAction()
    object GoHome : SystemAction()
    object GoBack : SystemAction()
    object None : SystemAction()
}

class CommandParser {

    fun parse(text: String): SystemAction {
        val lower = text.lowercase().trim()

        return when {
            lower.contains("call") || lower.contains("phone lagao") || lower.contains("dial") -> {
                val contact = lower.replace("call", "")
                    .replace("phone lagao", "")
                    .replace("dial", "")
                    .replace("ko", "")
                    .trim()
                SystemAction.MakeCall(contact)
            }
            lower.contains("open") || lower.contains("kholo") -> {
                val app = lower.replace("open", "")
                    .replace("kholo", "")
                    .trim()
                SystemAction.OpenApp(app)
            }
            lower.contains("whatsapp") -> {
                SystemAction.SendWhatsApp(contact = "", message = lower)
            }
            lower.contains("flashlight on") || lower.contains("torch on") -> {
                SystemAction.ToggleFlashlight(true)
            }
            lower.contains("flashlight off") || lower.contains("torch off") -> {
                SystemAction.ToggleFlashlight(false)
            }
            lower.contains("home") || lower.contains("home screen") -> {
                SystemAction.GoHome
            }
            lower.contains("back") || lower.contains("peeche") -> {
                SystemAction.GoBack
            }
            else -> SystemAction.None
        }
    }
}
