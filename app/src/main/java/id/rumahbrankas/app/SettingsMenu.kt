package id.rumahbrankas.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import java.util.Locale

private enum class SettingsPage { Root, Language, About }

internal fun currentAppLanguage(): String {
    val selected = AppCompatDelegate.getApplicationLocales()
    val language = if (selected.isEmpty) Locale.getDefault().language else selected[0]?.language.orEmpty()
    return if (language == "en") "en" else "id"
}

internal fun applyAppLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}

/** Gear anchored to the end of its parent. The menu is a small popup; the screen behind stays put. */
@Composable
internal fun SettingsGear() {
    var expanded by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(SettingsPage.Root) }
    Box {
        IconButton(onClick = {
            page = SettingsPage.Root
            expanded = true
        }) {
            Icon(Icons.Outlined.Settings, stringResource(R.string.settings_open), tint = Gold)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 6.dp),
            shape = RoundedCornerShape(22.dp),
            containerColor = Panel,
            tonalElevation = 8.dp,
            shadowElevation = 18.dp,
            border = BorderStroke(1.dp, Gold.copy(alpha = 0.34f)),
        ) {
            Column(Modifier.width(268.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
                when (page) {
                    SettingsPage.Root -> SettingsRoot(
                        onLanguage = { page = SettingsPage.Language },
                        onAbout = { page = SettingsPage.About },
                    )
                    SettingsPage.Language -> LanguagePage(
                        selected = currentAppLanguage(),
                        onBack = { page = SettingsPage.Root },
                        onSelect = { tag ->
                            if (tag != currentAppLanguage()) applyAppLanguage(tag)
                            expanded = false
                        },
                    )
                    SettingsPage.About -> AboutPage(onBack = { page = SettingsPage.Root })
                }
            }
        }
    }
}

@Composable
private fun SettingsRoot(onLanguage: () -> Unit, onAbout: () -> Unit) {
    PanelHeading(stringResource(R.string.settings_heading))
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = Gold.copy(alpha = 0.28f))
    Spacer(Modifier.height(6.dp))
    SettingsRow(Icons.Outlined.Translate, stringResource(R.string.settings_language), onLanguage)
    SettingsRow(Icons.Outlined.Info, stringResource(R.string.settings_about), onAbout)
}

@Composable
private fun LanguagePage(selected: String, onBack: () -> Unit, onSelect: (String) -> Unit) {
    PanelBackHeader(stringResource(R.string.settings_language), onBack)
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = Gold.copy(alpha = 0.28f))
    Spacer(Modifier.height(8.dp))
    LanguageChoice(stringResource(R.string.language_indonesia), selected == "id") { onSelect("id") }
    Spacer(Modifier.height(6.dp))
    LanguageChoice(stringResource(R.string.language_english), selected == "en") { onSelect("en") }
}

@Composable
private fun AboutPage(onBack: () -> Unit) {
    PanelBackHeader(stringResource(R.string.settings_about), onBack)
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = Gold.copy(alpha = 0.28f))
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.brand_name), color = Gold, fontSize = 12.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), color = Paper, fontFamily = FontFamily.Serif, fontSize = 20.sp)
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.about_summary), color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.about_recovery), color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun PanelHeading(title: String) {
    Text(title, color = Paper, fontFamily = FontFamily.Serif, fontSize = 22.sp)
}

@Composable
private fun PanelBackHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), tint = Gold)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, color = Paper, fontFamily = FontFamily.Serif, fontSize = 20.sp)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Gold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Paper, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LanguageChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) PanelRaised else Panel)
            .border(1.dp, if (selected) Gold.copy(alpha = 0.55f) else Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = if (selected) Gold else Paper, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        if (selected) Icon(Icons.Outlined.Check, null, tint = Gold, modifier = Modifier.size(18.dp))
    }
}
