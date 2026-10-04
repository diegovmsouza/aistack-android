package br.com.amberwrite.aistack.feature.settings

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.amberwrite.aistack.BuildConfig
import br.com.amberwrite.aistack.R
import br.com.amberwrite.aistack.feature.common.AiTextInput
import br.com.amberwrite.aistack.feature.common.FeatureScaffold
import br.com.amberwrite.aistack.feature.common.containerViewModel
import br.com.amberwrite.aistack.feature.common.label
import br.com.amberwrite.aistack.feature.devices.UnpairConfirmDialog
import br.com.amberwrite.aistack.feature.files.InfoBanner
import br.com.amberwrite.aistack.feature.files.kit.SkeletonBlock
import br.com.amberwrite.aistack.feature.files.kit.WideBreakpoint
import br.com.amberwrite.aistack.feature.files.rememberCopyText
import br.com.amberwrite.aistack.ui.designsystem.AiHaptics
import br.com.amberwrite.aistack.ui.designsystem.AiTheme
import br.com.amberwrite.aistack.ui.designsystem.HapticKind
import br.com.amberwrite.aistack.ui.designsystem.components.AiButton
import br.com.amberwrite.aistack.ui.designsystem.components.AiIconButton
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonSize
import br.com.amberwrite.aistack.ui.designsystem.components.ButtonVariant
import br.com.amberwrite.aistack.ui.designsystem.components.SurfaceCard
import br.com.amberwrite.aistack.ui.designsystem.components.TagBadge
import br.com.amberwrite.aistack.ui.designsystem.rememberAiHaptics
import br.com.amberwrite.aistack.ui.icons.Lucide

/** Estado do sistema lido ao voltar para a tela (o usuário pode ter mudado nos ajustes). */
private data class SystemStatus(
    val notificationsEnabled: Boolean = true,
    val disabledChannels: Set<String> = emptySet(),
    val batteryUnrestricted: Boolean = true
)

private fun readSystemStatus(context: Context): SystemStatus {
    val nm = NotificationManagerCompat.from(context)
    val disabled = ChannelShortcut.entries
        .filter { nm.getNotificationChannel(it.channelId)?.importance == NotificationManager.IMPORTANCE_NONE }
        .map { it.channelId }
        .toSet()
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return SystemStatus(
        notificationsEnabled = nm.areNotificationsEnabled(),
        disabledChannels = disabled,
        batteryUnrestricted = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    )
}

private fun Context.startSafely(primary: Intent, fallback: Intent? = null) {
    try {
        startActivity(primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        fallback?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    } catch (_: SecurityException) {
        fallback?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}

private fun appNotificationsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun channelIntent(context: Context, channelId: String): Intent =
    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)

@SuppressLint("BatteryLife") // Pedido explícito do usuário, com explicação antes (§4.9).
private fun batteryIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenDesignCatalog: () -> Unit,
    onUnpaired: () -> Unit
) {
    val vm = containerViewModel(key = "settings") { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberAiHaptics()
    var confirmUnpair by rememberSaveable { mutableStateOf(false) }
    var explainBattery by rememberSaveable { mutableStateOf(false) }
    var system by remember { mutableStateOf(SystemStatus()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        system = readSystemStatus(context)
    }

    FeatureScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= WideBreakpoint
            val left: @Composable ColumnScope.() -> Unit = {
                AppearanceSection(state, vm, haptics)
                NotificationsSection(
                    state = state,
                    system = system,
                    onNotifyDone = vm::setNotifyDone,
                    onOpenApp = { context.startSafely(appNotificationsIntent(context)) },
                    onOpenChannel = { id -> context.startSafely(channelIntent(context, id), appNotificationsIntent(context)) },
                    haptics = haptics
                )
                ConnectionSection(
                    state = state,
                    batteryUnrestricted = system.batteryUnrestricted,
                    onKeepConnected = vm::setKeepConnected,
                    onStartAtBoot = vm::setStartAtBoot,
                    onBattery = { explainBattery = true },
                    haptics = haptics
                )
            }
            val right: @Composable ColumnScope.() -> Unit = {
                DeviceSection(state, onDraft = vm::setNameDraft, onSave = vm::saveName, haptics = haptics)
                DesktopSection(onOpenDevices, onOpenAccounts, onOpenDesignCatalog)
                AboutSection(state)
                DangerSection(state, onUnpair = { confirmUnpair = true }, haptics = haptics)
            }
            if (wide) {
                Row(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(Modifier.weight(1f), content = left)
                    Column(Modifier.weight(1f), content = right)
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                        .widthIn(max = 720.dp)
                ) {
                    left()
                    right()
                }
            }
        }
    }

    if (explainBattery) {
        BatteryDialog(
            onConfirm = {
                explainBattery = false
                context.startSafely(batteryIntent(context), Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            },
            onDismiss = { explainBattery = false }
        )
    }

    if (confirmUnpair) {
        UnpairConfirmDialog(
            working = state.unpairing,
            onConfirm = {
                vm.unpair {
                    confirmUnpair = false
                    onUnpaired()
                }
            },
            onDismiss = { confirmUnpair = false }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Seções
// ---------------------------------------------------------------------------------------------

@Composable
private fun AppearanceSection(state: SettingsViewModel.UiState, vm: SettingsViewModel, haptics: AiHaptics) {
    Section(stringResource(R.string.settings_section_appearance)) {
        ThemePicker(selected = state.theme, onSelect = {
            haptics.perform(HapticKind.Tick)
            vm.setTheme(it)
        })
        val supported = SettingsLogic.materialYouSupported(Build.VERSION.SDK_INT)
        ToggleRow(
            title = stringResource(R.string.settings_material_you),
            description = stringResource(
                if (supported) R.string.settings_material_you_desc else R.string.settings_material_you_unsupported
            ),
            icon = Lucide.Sparkles,
            checked = state.materialYou && supported,
            enabled = supported,
            onCheckedChange = vm::setMaterialYou,
            haptics = haptics
        )
    }
}

@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val c = AiTheme.colors
    val options = listOf(
        Triple(ThemeMode.System, Lucide.SunMoon, R.string.settings_theme_system),
        Triple(ThemeMode.Light, Lucide.Sun, R.string.settings_theme_light),
        Triple(ThemeMode.Dark, Lucide.Moon, R.string.settings_theme_dark)
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(AiTheme.shapes.md)
            .background(c.surface2)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (mode, icon, label) ->
            val isSel = mode == selected
            val bg by animateColorAsState(if (isSel) c.surface else Color.Transparent, AiTheme.motion.fade(), label = "theme-bg")
            val fg by animateColorAsState(if (isSel) c.accent else c.fg2, AiTheme.motion.fade(), label = "theme-fg")
            val text = stringResource(label)
            val desc = stringResource(R.string.settings_theme_desc, text)
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(AiTheme.shapes.sm)
                    .background(bg)
                    .selectable(selected = isSel, role = Role.RadioButton, onClick = { onSelect(mode) })
                    .semantics { contentDescription = desc },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
                Text(text, style = AiTheme.typography.label, color = fg, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun NotificationsSection(
    state: SettingsViewModel.UiState,
    system: SystemStatus,
    onNotifyDone: (Boolean) -> Unit,
    onOpenApp: () -> Unit,
    onOpenChannel: (String) -> Unit,
    haptics: AiHaptics
) {
    Section(stringResource(R.string.settings_section_notifications)) {
        AnimatedVisibility(
            visible = !system.notificationsEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            InfoBanner(stringResource(R.string.settings_notifications_off), modifier = Modifier.padding(horizontal = 4.dp))
        }
        ToggleRow(
            title = stringResource(R.string.settings_notify_done),
            description = stringResource(R.string.settings_notify_done_desc),
            icon = Lucide.Bell,
            checked = state.settings.notifyDone,
            onCheckedChange = onNotifyDone,
            haptics = haptics
        )
        NavRow(
            title = stringResource(R.string.settings_notifications_all),
            description = stringResource(R.string.settings_notifications_all_desc),
            icon = Lucide.Settings,
            external = true,
            onClick = onOpenApp
        )
        ChannelShortcut.entries.forEach { ch ->
            val (title, desc) = when (ch) {
                ChannelShortcut.Pending -> R.string.settings_channel_pending to R.string.settings_channel_pending_desc
                ChannelShortcut.Progress -> R.string.settings_channel_progress to R.string.settings_channel_progress_desc
                ChannelShortcut.Done -> R.string.settings_channel_done to R.string.settings_channel_done_desc
                ChannelShortcut.Connection -> R.string.settings_channel_connection to R.string.settings_channel_connection_desc
            }
            NavRow(
                title = stringResource(title),
                description = stringResource(desc),
                icon = when (ch) {
                    ChannelShortcut.Pending -> Lucide.ShieldAlert
                    ChannelShortcut.Progress -> Lucide.LoaderCircle
                    ChannelShortcut.Done -> Lucide.CircleCheck
                    ChannelShortcut.Connection -> Lucide.Wifi
                },
                badge = if (ch.channelId in system.disabledChannels) stringResource(R.string.settings_channel_off) else null,
                external = true,
                onClick = { onOpenChannel(ch.channelId) }
            )
        }
    }
}

@Composable
private fun ConnectionSection(
    state: SettingsViewModel.UiState,
    batteryUnrestricted: Boolean,
    onKeepConnected: (Boolean) -> Unit,
    onStartAtBoot: (Boolean) -> Unit,
    onBattery: () -> Unit,
    haptics: AiHaptics
) {
    val c = AiTheme.colors
    val copy = rememberCopyText(stringResource(R.string.settings_copied))
    Section(stringResource(R.string.settings_section_connection)) {
        // URL do relay: somente leitura, com cópia.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Lucide.Link, contentDescription = null, tint = c.fg2, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    stringResource(R.string.settings_relay_url) + " · " + state.connection.label(),
                    style = AiTheme.typography.body,
                    color = c.fg
                )
                Text(
                    state.relayUrl ?: stringResource(R.string.settings_relay_none),
                    style = AiTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
                    color = c.fg3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            state.relayUrl?.let { url ->
                AiIconButton(
                    icon = Lucide.Copy,
                    contentDescription = stringResource(R.string.settings_copy_relay),
                    onClick = { copy(url) },
                    size = 48.dp
                )
            }
        }
        ToggleRow(
            title = stringResource(R.string.settings_keep_connected),
            description = stringResource(R.string.settings_keep_connected_desc),
            icon = Lucide.Wifi,
            checked = state.settings.keepConnected,
            onCheckedChange = onKeepConnected,
            haptics = haptics
        )
        ToggleRow(
            title = stringResource(R.string.settings_start_at_boot),
            description = stringResource(R.string.settings_start_at_boot_desc),
            icon = Lucide.Zap,
            checked = state.settings.startAtBoot,
            enabled = state.settings.keepConnected,
            onCheckedChange = onStartAtBoot,
            haptics = haptics
        )
        // Otimização de bateria: estado atual e pedido de isenção com explicação.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(targetState = batteryUnrestricted, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "battery-icon") { ok ->
                Icon(
                    if (ok) Lucide.BatteryFull else Lucide.BatteryLow,
                    contentDescription = null,
                    tint = if (ok) c.ok else c.warn,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.settings_battery), style = AiTheme.typography.body, color = c.fg)
                Text(
                    stringResource(if (batteryUnrestricted) R.string.settings_battery_ok else R.string.settings_battery_restricted),
                    style = AiTheme.typography.caption,
                    color = c.fg3
                )
            }
            if (!batteryUnrestricted) {
                AiButton(
                    text = stringResource(R.string.settings_battery_action),
                    onClick = onBattery,
                    size = ButtonSize.Small,
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceSection(
    state: SettingsViewModel.UiState,
    onDraft: (String) -> Unit,
    onSave: () -> Unit,
    haptics: AiHaptics
) {
    val c = AiTheme.colors
    val fadeSpec = AiTheme.motion.fade<Float>()
    Section(stringResource(R.string.settings_section_device)) {
        AnimatedContent(
            targetState = state.nameLoaded,
            transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
            label = "device-name"
        ) { loaded ->
            if (!loaded) {
                val desc = stringResource(R.string.settings_device_name_loading)
                SkeletonBlock(
                    width = null,
                    height = 56.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).semantics { contentDescription = desc },
                    shape = AiTheme.shapes.md
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiTextInput(
                        value = state.deviceNameDraft,
                        onValueChange = onDraft,
                        label = stringResource(R.string.settings_device_name),
                        singleLine = true,
                        imeAction = ImeAction.Done,
                        modifier = Modifier.weight(1f)
                    )
                    AiButton(
                        text = stringResource(R.string.settings_device_name_save),
                        onClick = onSave,
                        enabled = state.nameChanged,
                        loading = state.savingName,
                        haptic = HapticKind.Confirm,
                        haptics = haptics,
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(visible = state.nameSaved, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                TagBadge(
                    stringResource(R.string.settings_device_name_saved),
                    icon = Lucide.Check,
                    color = c.ok,
                    background = c.okSoft,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(stringResource(R.string.settings_device_name_note), style = AiTheme.typography.caption, color = c.fg3)
        }
    }
}

@Composable
private fun DesktopSection(onOpenDevices: () -> Unit, onOpenAccounts: () -> Unit, onOpenDesignCatalog: () -> Unit) {
    Section(stringResource(R.string.settings_section_desktop)) {
        NavRow(
            title = stringResource(R.string.settings_devices),
            description = stringResource(R.string.settings_devices_desc),
            icon = Lucide.Smartphone,
            onClick = onOpenDevices
        )
        NavRow(
            title = stringResource(R.string.settings_accounts),
            description = stringResource(R.string.settings_accounts_desc),
            icon = Lucide.User,
            onClick = onOpenAccounts
        )
        if (BuildConfig.DEBUG) {
            NavRow(
                title = stringResource(R.string.settings_design_catalog),
                description = stringResource(R.string.settings_design_catalog_desc),
                icon = Lucide.Layers,
                onClick = onOpenDesignCatalog
            )
        }
    }
}

@Composable
private fun AboutSection(state: SettingsViewModel.UiState) {
    Section(stringResource(R.string.settings_section_about)) {
        InfoRow(stringResource(R.string.settings_app_version), BuildConfig.VERSION_NAME, Lucide.Smartphone)
        InfoRow(
            stringResource(R.string.settings_desktop_version),
            state.desktopVersion ?: stringResource(R.string.settings_unknown),
            Lucide.Monitor
        )
    }
}

@Composable
private fun DangerSection(state: SettingsViewModel.UiState, onUnpair: () -> Unit, haptics: AiHaptics) {
    val c = AiTheme.colors
    Section(stringResource(R.string.settings_section_danger), accent = c.danger) {
        Text(
            stringResource(R.string.settings_unpair_desc),
            style = AiTheme.typography.caption,
            color = c.fg3,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        AiButton(
            text = stringResource(R.string.unpair_action),
            onClick = onUnpair,
            variant = ButtonVariant.Danger,
            leadingIcon = Lucide.Unplug,
            loading = state.unpairing,
            enabled = state.paired,
            haptic = HapticKind.LongPress,
            haptics = haptics,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Peças
// ---------------------------------------------------------------------------------------------

@Composable
private fun Section(title: String, accent: Color = AiTheme.colors.fg3, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(
            title.uppercase(),
            style = AiTheme.typography.overline,
            color = accent,
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 6.dp).semantics { heading() }
        )
        SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            content()
        }
    }
}

/** Linha inteira alternável (alvo de toque grande, papel de interruptor para leitores de tela). */
@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    haptics: AiHaptics,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val c = AiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.perform(HapticKind.Tick)
                    onCheckedChange(it)
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) c.fg2 else c.fg3, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(start = if (icon != null) 12.dp else 0.dp, end = 12.dp)) {
            Text(title, style = AiTheme.typography.body, color = if (enabled) c.fg else c.fg3)
            if (description != null) Text(description, style = AiTheme.typography.caption, color = c.fg3)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.accentFg,
                uncheckedTrackColor = c.surface2,
                uncheckedThumbColor = c.fg3,
                uncheckedBorderColor = c.line
            )
        )
    }
}

@Composable
private fun NavRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    description: String? = null,
    badge: String? = null,
    external: Boolean = false
) {
    val c = AiTheme.colors
    val externalDesc = if (external) stringResource(R.string.settings_open_system) else null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (externalDesc != null) contentDescription = listOfNotNull(title, description, badge, externalDesc).joinToString(". ")
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = c.fg2, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = AiTheme.typography.body, color = c.fg)
            if (description != null) Text(description, style = AiTheme.typography.caption, color = c.fg3)
        }
        if (badge != null) TagBadge(badge, color = c.warn, background = c.warnSoft, modifier = Modifier.padding(end = 8.dp))
        Icon(
            if (external) Lucide.ExternalLink else Lucide.ChevronRight,
            contentDescription = null,
            tint = c.fg3,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun InfoRow(title: String, value: String, icon: ImageVector) {
    val c = AiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = c.fg2, modifier = Modifier.size(20.dp))
        Text(title, style = AiTheme.typography.body, color = c.fg, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        Text(value, style = AiTheme.typography.caption.copy(fontFamily = FontFamily.Monospace), color = c.fg2)
    }
}

@Composable
private fun BatteryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = AiTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        titleContentColor = c.fg,
        textContentColor = c.fg2,
        icon = { Icon(Lucide.BatteryCharging, contentDescription = null, tint = c.accent) },
        title = { Text(stringResource(R.string.settings_battery_dialog_title), style = AiTheme.typography.heading) },
        text = { Text(stringResource(R.string.settings_battery_dialog_body), style = AiTheme.typography.body) },
        confirmButton = {
            AiButton(text = stringResource(R.string.settings_battery_dialog_confirm), onClick = onConfirm)
        },
        dismissButton = {
            AiButton(text = stringResource(R.string.settings_battery_dialog_cancel), onClick = onDismiss, variant = ButtonVariant.Ghost)
        }
    )
}
