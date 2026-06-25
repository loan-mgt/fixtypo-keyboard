package org.futo.inputmethod.latin.uix.actions

import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ai.AICorrectionBridge

val AICorrectionAction = Action(
    icon = R.drawable.icon_stars,
    name = R.string.ai_correction_action_name,
    simplePressImpl = { _, _ ->
        AICorrectionBridge.onApply?.invoke()
    },
    windowImpl = null,
    shownInEditor = true,
)
