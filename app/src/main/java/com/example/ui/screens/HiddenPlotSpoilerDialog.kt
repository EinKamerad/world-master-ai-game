package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.HiddenPlotPlan

@Composable
fun HiddenPlotSpoilerDialog(
    initialPlan: HiddenPlotPlan,
    isGeneratingAi: Boolean,
    onGenerateWithAi: (authorDirectionHint: String) -> Unit,
    onSaveManual: (HiddenPlotPlan) -> Unit,
    onDismiss: () -> Unit
) {
    var isSpoilerRevealed by remember { mutableStateOf(false) }
    var authorHint by remember { mutableStateOf("") }

    var draftArc by remember(initialPlan.plotArc) { mutableStateOf(initialPlan.plotArc) }
    var draftNpcKnowledge by remember(initialPlan.npcKnowledgeBoundaries) {
        mutableStateOf(initialPlan.npcKnowledgeBoundaries)
    }
    var draftTriggers by remember(initialPlan.triggersAndConsequences) {
        mutableStateOf(initialPlan.triggersAndConsequences)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isSpoilerRevealed) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Закулисье ИИ-Мастера")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (initialPlan.isNotEmpty()) {
                                "Статус: Скрытый режиссёрский план активен и направляет ИИ"
                            } else {
                                "Статус: План создастся автоматически на следующем ходу или по кнопке ниже"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ИИ ведёт здесь тайный план на 3–4 шага вперёд и реестр границ знаний НПС («кто о каком факте X ещё НЕ знает и не должен внезапно подозревать»), чтобы сюжет был цельным и без галлюцинаций.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                OutlinedTextField(
                    value = authorHint,
                    onValueChange = { authorHint = it },
                    label = { Text("Направление для ИИ (без спойлеров, по желанию)") },
                    placeholder = { Text("Например: добавь детективную интригу и тайный мотив трактирщику...") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hidden_plot_hint_input")
                )

                Button(
                    onClick = { onGenerateWithAi(authorHint) },
                    enabled = !isGeneratingAi,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_hidden_plot_ai_button")
                ) {
                    if (isGeneratingAi) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ИИ продумывает закулисный план...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Продумать / обновить план с ИИ")
                    }
                }

                if (!isSpoilerRevealed) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Осторожно: сюжетные спойлеры!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                text = "Скрытый план намеренно спрятан от игрока для сохранения интриги. Нажмите кнопку ниже, только если хотите лично прочитать или отредактировать тайны НПС и задумки Мастера.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            OutlinedButton(
                                onClick = { isSpoilerRevealed = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reveal_hidden_plot_spoilers_button")
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Показать скрытый план (спойлеры)")
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { isSpoilerRevealed = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Скрыть спойлеры обратно")
                    }

                    OutlinedTextField(
                        value = draftArc,
                        onValueChange = { draftArc = it },
                        label = { Text("1. Скрытая сюжетная арка и план ИИ наперёд") },
                        placeholder = { Text("К чему движется глава, истинные цели антагонистов и фоновые события...") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hidden_plot_arc_input")
                    )

                    OutlinedTextField(
                        value = draftNpcKnowledge,
                        onValueChange = { draftNpcKnowledge = it },
                        label = { Text("2. Границы знаний НПС (кто чего НЕ знает)") },
                        placeholder = {
                            Text("Пример: Стражник Маркус НЕ знает о краже печати и не должен ничего подозревать или намекать на неё без прямых улик.")
                        },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hidden_npc_knowledge_input")
                    )

                    OutlinedTextField(
                        value = draftTriggers,
                        onValueChange = { draftTriggers = it },
                        label = { Text("3. Тайные триггеры, улики и последствия") },
                        placeholder = { Text("Если герой проверит архив — найдёт письмо; если промедлит до полуночи — корабль отплывёт...") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hidden_plot_triggers_input")
                    )
                }
            }
        },
        confirmButton = {
            if (isSpoilerRevealed) {
                Button(
                    onClick = {
                        onSaveManual(
                            HiddenPlotPlan(
                                plotArc = draftArc,
                                npcKnowledgeBoundaries = draftNpcKnowledge,
                                triggersAndConsequences = draftTriggers
                            )
                        )
                        onDismiss()
                    },
                    modifier = Modifier.testTag("save_hidden_plot_button")
                ) {
                    Text("Сохранить план")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Готово")
                }
            }
        },
        dismissButton = {
            if (isSpoilerRevealed) {
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        }
    )
}
