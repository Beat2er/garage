package de.beat2er.garage.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.beat2er.garage.data.Device
import de.beat2er.garage.data.DeviceRepository
import de.beat2er.garage.ui.theme.Accent
import de.beat2er.garage.ui.theme.BgCard
import de.beat2er.garage.ui.theme.BgDark
import de.beat2er.garage.ui.theme.Success
import de.beat2er.garage.ui.theme.TextDim
import de.beat2er.garage.ui.theme.TextPrimary
import de.beat2er.garage.ui.theme.Warning

class MultiGarageWidget : GlanceAppWidget() {

    companion object {
        private const val TAG = "MultiGarageWidget"
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Load all devices from repository
        val devices = DeviceRepository(context).getDevices()

        provideContent {
            MultiWidgetContent(devices)
        }
    }

    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(MultiGarageWidget::class.java)
        for (id in ids) {
            update(context, id)
        }
    }

    @Composable
    private fun MultiWidgetContent(devices: List<Device>) {
        val state = currentState<Preferences>()

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(BgDark)
                .padding(12.dp)
        ) {
            if (devices.isEmpty()) {
                EmptyState()
            } else {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Text(
                        text = "Alle Garagen",
                        style = TextStyle(
                            color = ColorProvider(TextPrimary),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    LazyColumn {
                        items(devices, itemId = { it.id.hashCode().toLong() }) { device ->
                            DeviceRow(device, state)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DeviceRow(device: Device, state: Preferences) {
        val status = state[MultiWidgetKeys.statusKey(device.id)] ?: WidgetStatus.IDLE
        val statusText = state[MultiWidgetKeys.statusTextKey(device.id)] ?: "Bereit"

        val statusColor = when (status) {
            WidgetStatus.CONNECTING -> ColorProvider(Warning)
            WidgetStatus.TRIGGERED -> ColorProvider(Success)
            WidgetStatus.ERROR -> ColorProvider(Accent)
            else -> ColorProvider(TextDim)
        }

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(12.dp)
                    .background(BgCard)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = GlanceModifier.defaultWeight()
                    ) {
                        Text(
                            text = device.name,
                            style = TextStyle(
                                color = ColorProvider(TextPrimary),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = statusText,
                            style = TextStyle(
                                color = statusColor,
                                fontSize = 11.sp
                            ),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    TriggerButton(device)
                }
            }
        }
    }

    @Composable
    private fun TriggerButton(device: Device) {
        Box(
            modifier = GlanceModifier
                .cornerRadius(8.dp)
                .background(Accent)
                .clickable(
                    actionRunCallback<MultiTriggerAction>(
                        actionParametersOf(
                            MultiTriggerAction.PARAM_DEVICE_MAC to device.mac,
                            MultiTriggerAction.PARAM_DEVICE_NAME to device.name,
                            MultiTriggerAction.PARAM_DEVICE_PASSWORD to device.password,
                            MultiTriggerAction.PARAM_DEVICE_SWITCH_ID to device.switchId.toString(),
                            MultiTriggerAction.PARAM_DEVICE_ID to device.id
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚡",
                style = TextStyle(
                    fontSize = 16.sp
                )
            )
        }
    }

    @Composable
    private fun EmptyState() {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Alle Garagen",
                    style = TextStyle(
                        color = ColorProvider(TextPrimary),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "Keine Geräte konfiguriert",
                    style = TextStyle(
                        color = ColorProvider(TextDim),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
