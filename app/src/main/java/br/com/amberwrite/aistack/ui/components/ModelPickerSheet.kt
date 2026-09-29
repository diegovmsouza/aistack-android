package br.com.amberwrite.aistack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.amberwrite.aistack.model.Provider
import br.com.amberwrite.aistack.ui.theme.AiStackBg
import br.com.amberwrite.aistack.ui.theme.AiStackFg
import br.com.amberwrite.aistack.ui.theme.AiStackFg2
import br.com.amberwrite.aistack.ui.theme.AiStackLine
import br.com.amberwrite.aistack.ui.theme.AiStackSurface
import br.com.amberwrite.aistack.ui.theme.AiStackSurface2
import br.com.amberwrite.aistack.ui.theme.ProviderClaude

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    currentProvider: Provider,
    currentEffort: String,
    onSelect: (Provider, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    var selectedProvider by remember { mutableStateOf(currentProvider) }
    var effortSliderValue by remember {
        mutableFloatStateOf(
            when (currentEffort) {
                "low" -> 0f
                "medium" -> 1f
                "high" -> 2f
                "max" -> 3f
                else -> 2f
            }
        )
    }

    val effortLabels = listOf("Baixo", "Médio", "Alto", "Máximo")
    val effortKeys = listOf("low", "medium", "high", "max")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AiStackSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Selecionar Modelo e Esforço",
                color = AiStackFg,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Carrossel de Provedores
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(Provider.entries) { provider ->
                    val isSelected = provider == selectedProvider
                    val color = Color(provider.colorHex)

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) color.copy(alpha = 0.15f) else AiStackSurface2)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) color else AiStackLine,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedProvider = provider
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BrandHero(provider = provider, sizeDp = 36)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = provider.displayName,
                            color = if (isSelected) color else AiStackFg,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Seletor de Esforço com Slider e snaps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nível de Esforço / Raciocínio",
                    color = AiStackFg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = effortLabels[effortSliderValue.toInt().coerceIn(0, 3)],
                    color = Color(selectedProvider.colorHex),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Slider(
                value = effortSliderValue,
                onValueChange = {
                    val prev = effortSliderValue.toInt()
                    effortSliderValue = it
                    if (it.toInt() != prev) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                valueRange = 0f..3f,
                steps = 2,
                colors = SliderDefaults.colors(
                    thumbColor = Color(selectedProvider.colorHex),
                    activeTrackColor = Color(selectedProvider.colorHex),
                    inactiveTrackColor = AiStackSurface2
                ),
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Mais rápido", color = AiStackFg2, fontSize = 11.sp)
                Text("Mais inteligente", color = AiStackFg2, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botão confirmar
            androidx.compose.material3.Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val chosenEffort = effortKeys[effortSliderValue.toInt().coerceIn(0, 3)]
                    onSelect(selectedProvider, chosenEffort)
                    onDismiss()
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(selectedProvider.colorHex)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Confirmar ${selectedProvider.displayName}",
                    color = AiStackBg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
