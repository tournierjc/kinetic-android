package dev.kinetick.kinetic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kinetick.kinetic.api.Questionnaire
import dev.kinetick.kinetic.api.QuestionnaireAnswer
import dev.kinetick.kinetic.api.QuestionnaireStep

/**
 * Multi-step questionnaire from the agent (`questionnaire.ask`).
 * Selection mode 1 / "multiple" allows several options; "other" is a free-text
 * alternative on any step that allows it.
 */
@Composable
fun QuestionnaireDialog(
    questionnaire: Questionnaire,
    busy: Boolean,
    onSubmit: (List<QuestionnaireAnswer>) -> Unit,
    onDismiss: () -> Unit,
) {
    val steps = questionnaire.steps
    var index by remember(questionnaire.id) { mutableIntStateOf(0) }
    val selected = remember(questionnaire.id) {
        mutableStateMapOf<String, Set<String>>()
    }
    val other = remember(questionnaire.id) { mutableStateMapOf<String, String>() }

    val step = steps.getOrNull(index)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(questionnaire.title ?: "kcode is asking")
                if (steps.size > 1) {
                    Text(
                        "Question ${index + 1} of ${steps.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (step == null) {
                    Text("This questionnaire has no steps.")
                    return@Column
                }
                step.header?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge)
                }
                Text(step.question, style = MaterialTheme.typography.bodyLarge)
                step.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                val current = selected[step.id] ?: emptySet()
                step.options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option.id in current,
                                onClick = {
                                    val next = if (step.isMultiple) {
                                        if (option.id in current) current - option.id else current + option.id
                                    } else {
                                        setOf(option.id)
                                    }
                                    selected[step.id] = next
                                    if (!step.isMultiple) other[step.id] = ""
                                }
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (step.isMultiple) {
                            Checkbox(checked = option.id in current, onCheckedChange = null)
                        } else {
                            RadioButton(selected = option.id in current, onClick = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                option.label + if (option.recommended) "  ★" else "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            option.description?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (step.allowOther) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (step.isMultiple) {
                            Checkbox(
                                checked = (other[step.id] ?: "").isNotEmpty() ||
                                    (selected[step.id]?.contains("__other__") ?: false),
                                onCheckedChange = { checked ->
                                    selected[step.id] = if (checked) current + "__other__" else current - "__other__"
                                }
                            )
                        } else {
                            RadioButton(
                                selected = (selected[step.id] ?: emptySet()).contains("__other__"),
                                onClick = { selected[step.id] = setOf("__other__") }
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = other[step.id] ?: "",
                            onValueChange = { text ->
                                other[step.id] = text
                                if (text.isNotEmpty()) {
                                    selected[step.id] = if (step.isMultiple) current + "__other__" else setOf("__other__")
                                }
                            },
                            placeholder = { Text(step.otherPlaceholder ?: "Other…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (step != null && index < steps.lastIndex) {
                TextButton(
                    enabled = !busy,
                    onClick = { index += 1 }
                ) { Text("Next") }
            } else {
                TextButton(
                    enabled = !busy,
                    onClick = { onSubmit(buildAnswers(steps, selected, other)) }
                ) { Text(if (busy) "Sending…" else "Send") }
            }
        },
        dismissButton = {
            Row {
                if (index > 0) {
                    TextButton(enabled = !busy, onClick = { index -= 1 }) { Text("Back") }
                }
                if (step == null || !step.required) {
                    TextButton(enabled = !busy, onClick = {
                        val answers = buildAnswers(steps, selected, other).map {
                            if (it.selectedOptionIds.isEmpty() && it.otherText.isNullOrBlank()) it.copy(skipped = true)
                            else it
                        }
                        onSubmit(answers)
                    }) { Text("Skip") }
                }
                TextButton(enabled = !busy, onClick = onDismiss) { Text("Dismiss") }
            }
        }
    )
}

private fun buildAnswers(
    steps: List<QuestionnaireStep>,
    selected: Map<String, Set<String>>,
    other: Map<String, String>,
): List<QuestionnaireAnswer> = steps.map { s ->
    val picked = selected[s.id] ?: emptySet()
    val otherText = other[s.id]?.takeIf { it.isNotBlank() }
    QuestionnaireAnswer(
        stepId = s.id,
        selectedOptionIds = picked.filter { it != "__other__" },
        selectedOther = "__other__" in picked || otherText != null,
        otherText = otherText,
        skipped = picked.isEmpty() && otherText == null,
    )
}
