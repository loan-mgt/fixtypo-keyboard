package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.AI_CORRECTION_API_KEY
import org.futo.inputmethod.latin.uix.AI_CORRECTION_AUTO_CHECK
import org.futo.inputmethod.latin.uix.AI_CORRECTION_ENABLED
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore

val AICorrectionSettingsMenu = UserSettingsMenu(
    title = R.string.ai_correction_title,
    navPath = "aicorrection", registerNavPath = true,
    settings = listOf(
        userSettingToggleDataStore(
            title = R.string.ai_correction_enable,
            subtitle = R.string.ai_correction_enable_subtitle,
            setting = AI_CORRECTION_ENABLED
        ),
        userSettingToggleDataStore(
            title = R.string.ai_correction_auto_check,
            subtitle = R.string.ai_correction_auto_check_subtitle,
            setting = AI_CORRECTION_AUTO_CHECK
        ),
        userSettingDecorationOnly {
            AICorrectionSettingsContent()
        }
    )
)

@Composable
fun AICorrectionSettingsContent() {
    val apiKey = useDataStore(AI_CORRECTION_API_KEY)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.ai_correction_api_key),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey.value,
            onValueChange = { apiKey.setValue(it) },
            placeholder = { Text(stringResource(R.string.ai_correction_api_key_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.ai_correction_api_key_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(24.dp))
    }
}
