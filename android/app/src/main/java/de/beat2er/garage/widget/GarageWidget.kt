package de.beat2er.garage.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.beat2er.garage.ui.theme.Accent
import de.beat2er.garage.ui.theme.BgCard
import de.beat2er.garage.ui.theme.Success
import de.beat2er.garage.ui.theme.TextDim
import de.beat2er.garage.ui.theme.TextPrimary
import de.beat2er.garage.ui.theme.Warning

class GarageWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GarageWidgetContent()
        }
    }

    @Composable
    private fun GarageWidgetContent() {
        val state = currentState<Preferences>()
        val deviceName = state[WidgetKeys.DEVICE_NAME]
        val status = state[WidgetKeys.STATUS] ?: WidgetStatus.IDLE
        val statusText = state[WidgetKeys.STATUS_TEXT] ?: "Tippen zum Auslösen"
        val isConfigured = deviceName != null

        val statusColor = when (status) {
            WidgetStatus.CONNECTING -> ColorProvider(Warning)
            WidgetStatus.TRIGGERED -> ColorProvider(Success)
            WidgetStatus.ERROR -> ColorProvider(Accent)
            else -> ColorProvider(TextDim)
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(BgCard)
                .clickable(actionRunCallback<TriggerAction>())
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isConfigured) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = deviceName!!,
                        style = TextStyle(
                            color = ColorProvider(TextPrimary),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = statusText,
                        style = TextStyle(
                            color = statusColor,
                            fontSize = 11.sp
                        ),
                        maxLines = 2
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Kein Gerät",
                        style = TextStyle(
                            color = ColorProvider(TextDim),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "Lange drücken\nzum Einrichten",
                        style = TextStyle(
                            color = ColorProvider(TextDim),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}
