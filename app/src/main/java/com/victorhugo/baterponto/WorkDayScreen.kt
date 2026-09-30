package com.victorhugo.baterponto

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorhugo.baterponto.ui.theme.BaterPontoTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Portuguese = Locale.forLanguageTag("pt-BR")
private val Emerald = Color(0xFF123F33)
private val Mint = Color(0xFFBCF3C9)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkDayScreen(
    day: WorkDay,
    now: LocalDateTime,
    notificationsAllowed: Boolean,
    exactAllowed: Boolean,
    onChange: (WorkDay) -> Unit,
    onEnableAlerts: (Boolean) -> Unit,
    onNotificationSettings: () -> Unit,
    onExactSettings: () -> Unit
) {
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var showGoal by rememberSaveable { mutableStateOf(false) }
    var showRest by rememberSaveable { mutableStateOf(false) }
    var showReset by rememberSaveable { mutableStateOf(false) }
    var showStandardBreak by rememberSaveable { mutableStateOf(false) }
    var showPauseChoice by rememberSaveable { mutableStateOf(false) }
    var recordingLunch by rememberSaveable { mutableStateOf(false) }
    var pointsExpanded by rememberSaveable { mutableStateOf(true) }
    val worked = day.workedMinutes(now)
    val goalTime = day.milestone(day.goalMinutes)
    val limitTime = day.milestone(600)
    val referenceDate = day.punches.firstOrNull()?.toLocalDate() ?: now.toLocalDate()
    val colors = MaterialTheme.colorScheme
    val nextEntry = day.entryAllowedAt() ?: limitTime?.plusMinutes(day.restMinutes.toLong())
    val showRestCard = day.punches.isNotEmpty() &&
        nextEntry?.toLocalTime()?.isAfter(LocalTime.of(8, 0)) == true
    val restAtTop = showRestCard && day.finished
    val scrollState = rememberScrollState()
    LaunchedEffect(day.finished, restAtTop) {
        scrollState.scrollTo(0)
        if (day.finished) pointsExpanded = false
    }
    val restCard: @Composable () -> Unit = {
        RestCard(day, now, notificationsAllowed, exactAllowed,
            onConfigure = { showRest = true }, onChange, onNotificationSettings, onExactSettings,
            onNewDay = { showReset = true })
    }

    Scaffold(containerColor = colors.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(scrollState)
                    .padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ClockMark()
                    Column(Modifier.weight(1f)) {
                        Text("Bater Ponto", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Seu tempo, com tranquilidade.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                Text(referenceDate.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Portuguese))
                    .replaceFirstChar { it.titlecase(Portuguese) },
                    style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)

                if (restAtTop) restCard()

                Surface(color = Emerald, contentColor = Color.White, shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).background(Mint, CircleShape))
                            Text(when {
                                day.finished -> "JORNADA ENCERRADA"
                                day.inStandardBreak(now) -> "PAUSA PADRÃO"
                                day.atLunch -> "EM ALMOÇO"
                                day.paused -> "FORA DO TRABALHO"
                                day.working -> "JORNADA EM ANDAMENTO"
                                else -> "VAMOS COMEÇAR?"
                            }, style = MaterialTheme.typography.labelMedium, color = Mint, letterSpacing = 1.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (day.finished) "Total trabalhado" else "Tempo trabalhado",
                                style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD4E6DB))
                            Text(durationLabel(worked), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                        }
                        if (!day.finished) LinearProgressIndicator(
                            progress = { (worked.toFloat() / day.goalMinutes).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Mint, trackColor = Color(0xFF406357)
                        )
                        Text(when {
                            day.punches.isEmpty() -> "Registre sua entrada. A gente faz as contas."
                            day.finished -> "Saída às ${day.punches.lastOrNull()?.let(::clockLabel) ?: "—"} • " + when {
                                worked < 600 -> "${600 - worked} min antes do limite"
                                worked == 600L -> "no limite de 10h"
                                else -> "${worked - 600} min acima do limite"
                            }
                            worked >= 600 -> "Limite de 10 horas atingido. Confira sua saída."
                            worked >= day.goalMinutes -> "Meta concluída • acompanhe o limite de 10h abaixo"
                            day.paused -> "Contagem pausada. Registre o retorno para recalcular a saída."
                            else -> "Faltam ${durationLabel(day.goalMinutes - worked)} para sua meta"
                        }, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE0EEE5))
                    }
                }

                if (!day.finished) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeading("Sua hora de sair", "Editar meta") { showGoal = true }
                    if (LocalDensity.current.fontScale > 1.2f) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimeCard("Meta · ${durationLabel(day.goalMinutes.toLong())}", goalTime, day, worked >= day.goalMinutes, false, Modifier.fillMaxWidth())
                            TimeCard("Limite máximo · 10h", limitTime, day, worked >= 600, true, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimeCard("Meta · ${durationLabel(day.goalMinutes.toLong())}", goalTime, day, worked >= day.goalMinutes, false, Modifier.weight(1f))
                            TimeCard("Limite máximo · 10h", limitTime, day, worked >= 600, true, Modifier.weight(1f))
                        }
                    }
                    Text(
                        when {
                            day.paused -> "A previsão continua quando você registrar o retorno."
                            day.finished -> "Horários alcançados durante esta jornada."
                            day.standardBreak == true -> "Previsão com almoço das 11:30 às 13:00, sem novas pausas."
                            else -> "Previsão sem novas pausas. Intervalos não contam como trabalho."
                        },
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                    )
                }

                Surface(shape = RoundedCornerShape(24.dp), color = colors.surface) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeading("Registros do dia")
                        if (day.punches.isNotEmpty()) {
                            Text(
                                if (day.standardBreak == true) "Pausa padrão · 11:30 às 13:00"
                                else "Almoço automático não incluído",
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
                            )
                            TextButton(onClick = { showStandardBreak = true }) {
                                Text(if (day.standardBreak == true) "Alterar pausa padrão" else "Usar pausa padrão")
                            }
                        }
                        if (day.punches.isEmpty()) {
                            Text("Tudo começa com sua entrada", style = MaterialTheme.typography.titleMedium)
                            Text("Adicione um horário ou use a hora atual. Depois, registre suas pausas e retornos.",
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        } else {
                            SectionHeading("Pontos do dia · ${day.timeline().size}", if (pointsExpanded) "Recolher" else "Expandir") {
                                pointsExpanded = !pointsExpanded
                            }
                            if (pointsExpanded) day.timeline().forEachIndexed { order, point ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = colors.secondaryContainer, modifier = Modifier.size(32.dp)) {
                                        Box(contentAlignment = Alignment.Center) { Text("${order + 1}", style = MaterialTheme.typography.labelMedium) }
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(point.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            if (point.punchIndex == null) {
                                                if (point.time.isAfter(now)) "Automático · previsto" else "Automático"
                                            } else "Registrado",
                                            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
                                        )
                                        if (point.time.toLocalDate() != referenceDate)
                                            Text(point.time.format(DateTimeFormatter.ofPattern("dd/MM")), style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(onClick = {
                                        if (point.punchIndex == null) showStandardBreak = true
                                        else editingIndex = point.punchIndex
                                    }, modifier = Modifier.semantics {
                                        contentDescription = "Consultar ${point.label}: ${clockLabel(point.time)}"
                                    }) {
                                        Text(clockLabel(point.time), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            HorizontalDivider(color = colors.outlineVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tempo fora do trabalho", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                Text(durationLabel(day.breakMinutes(now)), style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Toque em um horário para corrigir.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        if (!day.finished) {
                            Button(
                                onClick = {
                                    finishing = false
                                    recordingLunch = false
                                    adding = true
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(when {
                                    day.punches.isEmpty() -> "Registrar entrada"
                                    day.working -> "Registrar saída temporária"
                                    day.atLunch -> "Registrar retorno do almoço"
                                    else -> "Registrar retorno ao trabalho"
                                })
                            }
                            if (day.working) {
                                OutlinedButton(
                                    onClick = { showPauseChoice = true },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) { Text("Registrar almoço") }
                            }
                            if (day.punches.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        if (day.paused) {
                                            onChange(day.copy(finished = true))
                                            if (day.standardBreak == null) showStandardBreak = true
                                        }
                                        else { adding = true; finishing = true; recordingLunch = false }
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) { Text("Registrar saída definitiva") }
                            }
                        } else {
                            TextButton(onClick = { onChange(day.copy(finished = false)) }) { Text("Reabrir jornada") }
                        }
                    }
                }

                Surface(color = colors.surface, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Um lembrete para sair", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Aviso antes e ao completar 10h", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            Switch(checked = day.alertsEnabled, onCheckedChange = onEnableAlerts,
                                modifier = Modifier.semantics { contentDescription = "Alertas do limite de 10 horas" })
                        }
                        if (day.alertsEnabled) {
                            Text("Notificação com progresso", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProgressMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = day.progressMode == mode,
                                        onClick = { onChange(day.copy(progressMode = mode)) },
                                        label = { Text(mode.label) }
                                    )
                                }
                            }
                            if (day.progressMode != ProgressMode.OFF) {
                                val band = journeyBand(worked)
                                LinearProgressIndicator(
                                    progress = { (worked / 600f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = Color(band.argb)
                                )
                                Text("Verde até 8h • amarelo até 9h • laranja até 9h45 • vermelho até 10h",
                                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                Text("A barra e o tempo são atualizados a cada minuto. Expanda a notificação para ver o contador.",
                                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            Text("Me avise com antecedência de", style = MaterialTheme.typography.bodyMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(10, 15, 30).forEach { minutes ->
                                    FilterChip(selected = day.alertMinutes == minutes,
                                        onClick = { onChange(day.copy(alertMinutes = minutes)) }, label = { Text("$minutes min") })
                                }
                            }
                            when {
                                !notificationsAllowed -> {
                                    Text("Notificações bloqueadas. Permita os avisos para receber o lembrete.", color = colors.error, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = onNotificationSettings) { Text("Permitir notificações") }
                                }
                                !exactAllowed -> {
                                    Text("O Android pode atrasar os avisos. Ative os alarmes precisos para alertar no horário.", style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = onExactSettings) { Text("Ativar alarmes precisos") }
                                }
                                day.working && limitTime != null && now.isBefore(limitTime) ->
                                    Text("Avisos às ${datedClock(limitTime.minusMinutes(day.alertMinutes.toLong()), day)} e ${datedClock(limitTime, day)}",
                                        style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                                else -> Text("Os alertas aguardam uma jornada em andamento.",
                                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                            Text("O som e a vibração seguem as configurações do celular e o modo Não perturbe.",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            TextButton(onClick = onNotificationSettings) { Text("Configurar som e vibração") }
                        }
                    }
                }

                if (showRestCard && !restAtTop) restCard()
                if (!showRestCard) {
                    TextButton(onClick = { showRest = true }) {
                        Text("Configurar descanso · ${durationLabel(day.restMinutes.toLong())}")
                    }
                }
                Text("Cálculo pessoal com os horários informados. O app não registra o ponto na empresa.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (day.punches.isNotEmpty()) {
                    TextButton(onClick = { showReset = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Começar nova jornada")
                    }
                }
            }
        }
    }

    if (adding || editingIndex != null) {
        val index = editingIndex
        PunchDialog(
            title = if (index != null) "Corrigir ${day.label(index).lowercase(Portuguese)}"
                else if (finishing) "Saída definitiva"
                else if (day.punches.isEmpty()) "Entrada inicial"
                else if (day.working) { if (recordingLunch) "Saída para almoço" else "Saída temporária" }
                else if (day.atLunch) "Retorno do almoço" else "Retorno ao trabalho",
            initial = index?.let { day.punches[it] } ?: now.withSecond(0).withNano(0),
            onDismiss = { adding = false; editingIndex = null },
            onSave = { time ->
                val candidate = if (index == null) day.record(time, lunch = recordingLunch, finalExit = finishing)
                    else day.copy(punches = day.punches.toMutableList().apply { set(index, time) })
                val error = candidate.validationError()
                if (error == null) {
                    onChange(candidate)
                    adding = false
                    editingIndex = null
                    pointsExpanded = true
                    if (candidate.standardBreak == null && (day.punches.isEmpty() || finishing))
                        showStandardBreak = true
                }
                error
            },
            onDelete = if (index != null && index == day.punches.lastIndex) ({
                onChange(day.removeLast())
                editingIndex = null
            }) else null
        )
    }
    if (showPauseChoice) AlertDialog(
        onDismissRequest = { showPauseChoice = false },
        title = { Text("Como registrar o almoço?") },
        text = {
            Text("Use o intervalo automático de 11:30 às 13:00 ou registre a saída e o retorno reais. Ao salvar um almoço manual, ele substitui o automático deste dia.")
        },
        confirmButton = {
            TextButton(onClick = {
                onChange(day.copy(standardBreak = true))
                pointsExpanded = true
                showPauseChoice = false
            }) { Text("Usar 11:30 às 13:00") }
        },
        dismissButton = {
            TextButton(onClick = {
                showPauseChoice = false
                finishing = false
                recordingLunch = true
                adding = true
            }) { Text("Registrar almoço manual") }
        }
    )
    if (showStandardBreak) AlertDialog(
        onDismissRequest = { showStandardBreak = false },
        title = { Text("Registrar pausa padrão?") },
        text = { Text("11:30 às 13:00\n\nEsse intervalo será descontado dos cálculos e considerado nos alertas. Pausas manuais no mesmo período não serão descontadas duas vezes.\n\nSe o almoço teve outro horário, escolha registrar manualmente.") },
        confirmButton = {
            TextButton(onClick = { onChange(day.copy(standardBreak = true)); showStandardBreak = false }) {
                Text("Sim, usar padrão")
            }
        },
        dismissButton = {
            TextButton(onClick = { onChange(day.copy(standardBreak = false)); showStandardBreak = false }) {
                Text("Não, registrar manualmente")
            }
        }
    )
    if (showRest) GoalDialog(day.restMinutes, { showRest = false }, maxMinutes = 1439, rest = true) {
        onChange(day.copy(restMinutes = it)); showRest = false
    }
    if (showGoal) GoalDialog(day.goalMinutes, { showGoal = false }) {
        onChange(day.copy(goalMinutes = it)); showGoal = false
    }
    if (showReset) AlertDialog(
        onDismissRequest = { showReset = false },
        title = { Text("Começar uma nova jornada?") },
        text = { Text("Os registros atuais serão apagados. Sua meta e preferências de alerta serão mantidas.") },
        confirmButton = { TextButton(onClick = { onChange(day.nextDay()); showReset = false }) { Text("Começar nova") } },
        dismissButton = { TextButton(onClick = { showReset = false }) { Text("Voltar") } }
    )
}

@Composable
private fun RestCard(
    day: WorkDay, now: LocalDateTime, notificationsAllowed: Boolean, exactAllowed: Boolean,
    onConfigure: () -> Unit, onChange: (WorkDay) -> Unit,
    onNotificationSettings: () -> Unit, onExactSettings: () -> Unit,
    onNewDay: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val allowedAt = day.entryAllowedAt()
    val shown = allowedAt ?: day.milestone(600)?.plusMinutes(day.restMinutes.toLong())
    Surface(color = colors.surface, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("Quando posso entrar?", "Configurar", onConfigure)
            if (shown != null) {
                Text(if (allowedAt != null) "Entrada a partir de" else "Próxima entrada prevista",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Text(shown.format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm")),
                    style = MaterialTheme.typography.headlineSmall, color = colors.primary)
                if (allowedAt != null && !now.isBefore(allowedAt))
                    Text("Entrada liberada", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Text(
                    if (allowedAt != null) "${durationLabel(day.restMinutes.toLong())} após a saída definitiva."
                    else "Previsão com saída no limite de 10h. O aviso será agendado ao registrar a saída definitiva.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
                )
            } else {
                Text("Registre a saída definitiva para calcular sua próxima entrada.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Avisar quando a entrada estiver liberada", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium)
                Switch(day.restAlertsEnabled, { onChange(day.copy(restAlertsEnabled = it)) },
                    modifier = Modifier.semantics { contentDescription = "Aviso de entrada liberada" })
            }
            if (allowedAt == null) Text("Descanso configurado: ${durationLabel(day.restMinutes.toLong())}",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (day.restAlertsEnabled && !notificationsAllowed)
                TextButton(onClick = onNotificationSettings) { Text("Permitir notificações") }
            if (day.restAlertsEnabled && !exactAllowed)
                TextButton(onClick = onExactSettings) { Text("Ativar alarmes precisos") }
            Button(
                onClick = onNewDay, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Começar nova jornada") }
        }
    }
}

@Composable
private fun ClockMark() {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
        val ink = MaterialTheme.colorScheme.onPrimaryContainer
        Canvas(Modifier.padding(12.dp)) {
            drawCircle(ink, style = Stroke(2.dp.toPx()))
            drawLine(ink, center, Offset(center.x, size.height * 0.22f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(ink, center, Offset(size.width * 0.73f, size.height * 0.62f), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun TimeCard(title: String, time: LocalDateTime?, day: WorkDay, reached: Boolean, limit: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(20.dp),
        color = if (limit) colors.tertiaryContainer else colors.primaryContainer,
        contentColor = if (limit) colors.onTertiaryContainer else colors.onPrimaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(time?.let(::clockLabel) ?: "—", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(when {
                time == null && day.paused -> "Aguardando retorno"
                time == null && day.finished -> "Não alcançada"
                time == null -> "Aguardando entrada"
                reached -> "Horário alcançado"
                else -> "Saída prevista"
            }, style = MaterialTheme.typography.bodySmall)
            if (time != null && time.toLocalDate() != day.punches.firstOrNull()?.toLocalDate()) {
                Text(time.format(DateTimeFormatter.ofPattern("dd/MM")), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PunchDialog(
    title: String, initial: LocalDateTime, onDismiss: () -> Unit,
    onSave: (LocalDateTime) -> String?, onDelete: (() -> Unit)?
) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf(clockLabel(initial).replace(":", "")) }
    var dateText by rememberSaveable { mutableStateOf(initial.toLocalDate().toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val date = LocalDate.parse(dateText)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(text, { text = acceptTimeInput(text, it); error = null }, label = { Text("Horário · HH:mm") },
                    visualTransformation = TimeMask,
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = error != null, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        DatePickerDialog(context, { _, year, month, day ->
                            dateText = LocalDate.of(year, month + 1, day).toString(); error = null
                        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }) { Text(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) }
                    TextButton(onClick = {
                        val current = LocalDateTime.now()
                        text = clockLabel(current).replace(":", ""); dateText = current.toLocalDate().toString(); error = null
                    }) { Text("Usar agora") }
                }
                Text("Informe o horário em que você bateu o ponto.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Excluir último registro") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val time = timeInputMinutes(text)?.let { LocalTime.of(it / 60, it % 60) }
                if (time == null) error = "Use um horário válido, como 08:30."
                else {
                    val value = date.atTime(time)
                    error = if (value.isAfter(LocalDateTime.now())) "O registro não pode estar no futuro." else onSave(value)
                }
            }) { Text("Salvar horário") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun GoalDialog(initial: Int, onDismiss: () -> Unit, maxMinutes: Int = 600, rest: Boolean = false, onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("%02d%02d".format(initial / 60, initial % 60)) }
    var error by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (rest) "Descanso entre jornadas" else "Sua meta diária") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (rest) "Padrão: 11 horas após a saída definitiva. Ajuste conforme sua jornada." else "Defina sua meta diária. O limite máximo de 10h permanece fixo.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (rest) {
                        SuggestionChip(onClick = { text = "1100" }, label = { Text("11 horas") })
                        SuggestionChip(onClick = { text = "1200" }, label = { Text("12 horas") })
                    } else {
                        SuggestionChip(onClick = { text = "0800" }, label = { Text("8 horas") })

                    }
                }
                OutlinedTextField(text, { text = acceptTimeInput(text, it); error = false }, label = { Text(if (rest) "Descanso · HH:mm" else "Meta · HH:mm") },
                    visualTransformation = TimeMask, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, isError = error, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(if (error) { if (rest) "Use uma duração entre 00:01 e 23:59." else "Use uma duração entre 00:01 e 10:00." } else if (rest) "Por exemplo: 11:00" else "Por exemplo: 08:48") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val total = timeInputMinutes(text) ?: 0
                if (total in 1..maxMinutes) onSave(total) else error = true
            }) { Text(if (rest) "Salvar descanso" else "Salvar meta") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 1100)
@Composable
private fun WorkDayPreview() {
    val date = LocalDate.of(2026, 9, 29)
    BaterPontoTheme {
        WorkDayScreen(
            WorkDay(listOf(date.atTime(8, 0), date.atTime(11, 30), date.atTime(13, 0)), alertsEnabled = true),
            date.atTime(16, 24), true, true, {}, {}, {}, {}
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 1100)
@Composable
private fun WorkDayDarkPreview() {
    BaterPontoTheme(darkTheme = true) {
        WorkDayScreen(WorkDay(), LocalDateTime.of(2026, 9, 29, 8, 0), false, false, {}, {}, {}, {})
    }
}
