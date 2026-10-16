package com.example.movie

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun MoviePanel(controller: MovieController, close: () -> Unit, stop: () -> Unit) {
    var question by remember { mutableStateOf("") }
    val revision = controller.revision
    val turns = remember(revision) { controller.session.turns.toList() }
    val scroll = rememberLazyListState()
    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty() && (scroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= turns.size - 3)
            scroll.animateScrollToItem(turns.lastIndex)
    }
    Surface {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row { Text("AI Movie Mode", Modifier.weight(1f)); TextButton(onClick = close) { Text(stringResource(R.string.movie_close)) } }
            Text(controller.status, style = MaterialTheme.typography.bodySmall)
            controller.session.movie?.let { Text("${it.title} (${it.year}) · TMDB #${it.id}") }
            if (controller.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row {
                TextButton(onClick = stop) { Text(stringResource(R.string.movie_stop)) }
                TextButton(enabled = !controller.busy && controller.active, onClick = { controller.recognize(true); close() }) { Text(stringResource(R.string.movie_new)) }
            }
            Row {
                Checkbox(controller.session.spoilerFree, onCheckedChange = { controller.spoiler(it) }, enabled = !controller.busy)
                Text(stringResource(R.string.movie_spoiler))
            }
            controller.candidates.forEach { candidate ->
                TextButton(enabled = !controller.busy, onClick = { controller.choose(candidate) }) {
                    Text("${candidate.title} (${candidate.year}) · ${(candidate.confidence * 100).toInt()}%")
                }
            }
            LazyColumn(Modifier.weight(1f), state = scroll) {
                items(turns) { turn -> Text((if (turn.role == "user") "› " else "") + turn.text, Modifier.padding(vertical = 8.dp)) }
            }
            OutlinedTextField(question, { question = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.movie_question)) }, maxLines = 3)
            Button(enabled = controller.ready && !controller.busy && question.isNotBlank(), onClick = {
                controller.ask(question); question = ""
            }) { Text(stringResource(R.string.movie_send)) }
            Text("This product uses the TMDB API but is not endorsed or certified by TMDB.", style = MaterialTheme.typography.labelSmall)
        }
    }
}
