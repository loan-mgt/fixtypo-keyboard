package org.futo.inputmethod.latin.uix.ai

object AICorrectionBridge {
    var onApply: (() -> Unit)? = null
    var onRequest: (() -> Unit)? = null
}
