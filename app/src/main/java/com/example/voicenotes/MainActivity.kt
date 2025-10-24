package com.example.voicenotes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.voicenotes.ui.theme.VoiceNotesTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val viewModel: VoiceNoteViewModel by viewModels {
        VoiceNoteViewModelFactory(applicationContext)
    }

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.startListening()
            } else {
                android.widget.Toast.makeText(
                    this,
                    "마이크 권한이 없어 음성 인식을 사용할 수 없습니다.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoiceNotesTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    VoiceNoteScreen(viewModel = viewModel, onRequestStart = ::ensureAudioPermission)
                }
            }
        }

        lifecycle.addObserver(viewModel)
        ensureAudioPermission()
    }

    private fun ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startListening()
        } else {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

@Composable
private fun VoiceNoteScreen(viewModel: VoiceNoteViewModel, onRequestStart: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collectLatest { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "음성 메모", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Button(onClick = {
            if (uiState.isListening) {
                viewModel.stopListening()
            } else {
                onRequestStart()
            }
        }) {
            Icon(
                imageVector = if (uiState.isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null
            )
            Text(text = if (uiState.isListening) "인식 중지" else "인식 시작", modifier = Modifier.padding(start = 8.dp))
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.entries.isEmpty()) {
                item {
                    Text(
                        text = "아직 저장된 음성 메모가 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 48.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.entries) { entry ->
                    VoiceEntryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun VoiceEntryCard(entry: VoiceEntry) {
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.timestamp), ZoneId.systemDefault())
    val formattedTime = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = formattedTime, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(text = entry.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

class VoiceNoteRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("voice_notes", Context.MODE_PRIVATE)

    fun loadEntries(): List<VoiceEntry> {
        val json = preferences.getString("entries", null) ?: return emptyList()
        return runCatching {
            org.json.JSONArray(json).let { array ->
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        add(
                            VoiceEntry(
                                text = obj.getString("text"),
                                timestamp = obj.getLong("timestamp")
                            )
                        )
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    fun saveEntries(entries: List<VoiceEntry>) {
        val array = org.json.JSONArray()
        entries.forEach { entry ->
            val obj = org.json.JSONObject().apply {
                put("text", entry.text)
                put("timestamp", entry.timestamp)
            }
            array.put(obj)
        }
        preferences.edit().putString("entries", array.toString()).apply()
    }
}

class VoiceNoteViewModel(context: Context) : androidx.lifecycle.ViewModel(), androidx.lifecycle.DefaultLifecycleObserver {
    private val appContext = context.applicationContext
    private val repository = VoiceNoteRepository(appContext)
    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(VoiceNoteUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<VoiceNoteUiState> = _uiState

    private val _toastEvents = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val toastEvents = _toastEvents.receiveAsFlow()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _toastEvents.trySend("말씀해 주세요")
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            _toastEvents.trySend("인식 오류: $error")
            stopListening()
        }

        override fun onResults(results: Bundle?) {
            val spokenText = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }

            if (spokenText != null) {
                addEntry(spokenText)
            }
            stopListening()
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        speechRecognizer.setRecognitionListener(listener)
        val storedEntries = repository.loadEntries().sortedByDescending { it.timestamp }
        _uiState.value = VoiceNoteUiState(entries = storedEntries)
    }

    override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
        super.onResume(owner)
        if (_uiState.value.isListening) {
            startListening()
        }
    }

    override fun onPause(owner: androidx.lifecycle.LifecycleOwner) {
        super.onPause(owner)
        stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer.destroy()
    }

    fun startListening() {
        if (!_uiState.value.isListening) {
            if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
                runCatching {
                    speechRecognizer.startListening(recognizerIntent)
                    _uiState.value = _uiState.value.copy(isListening = true)
                }.onFailure { throwable ->
                    _toastEvents.trySend("인식을 시작할 수 없습니다: ${throwable.message}")
                }
            } else {
                _toastEvents.trySend("이 기기에서 음성 인식을 사용할 수 없습니다")
            }
        }
    }

    fun stopListening() {
        if (_uiState.value.isListening) {
            speechRecognizer.stopListening()
            _uiState.value = _uiState.value.copy(isListening = false)
        }
    }

    private fun addEntry(text: String) {
        val newEntry = VoiceEntry(text = text, timestamp = System.currentTimeMillis())
        val updated = (listOf(newEntry) + _uiState.value.entries).sortedByDescending { it.timestamp }
        _uiState.value = _uiState.value.copy(entries = updated)
        repository.saveEntries(updated)
    }
}

class VoiceNoteViewModelFactory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VoiceNoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VoiceNoteViewModel(context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class VoiceEntry(val text: String, val timestamp: Long)

data class VoiceNoteUiState(
    val entries: List<VoiceEntry> = emptyList(),
    val isListening: Boolean = false
)
