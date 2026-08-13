package com.example.verb.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.verb.actions.ActionRegistry
import com.example.verb.db.CommandHistoryEntity
import com.example.verb.db.TerminalOutputEntity
import com.example.verb.db.VerbRepository
import com.example.verb.intent.IntentEngine
import com.example.verb.model.ActionResult
import com.example.verb.model.AgentMemoryStore
import com.example.verb.model.ChatMessage
import com.example.verb.model.ChatSender
import com.example.verb.model.SemanticEntity
import com.example.verb.semantic.SemanticEngine
import com.example.verb.terminal.TerminalRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class VerbTab {
    ASK,
    SYSTEM,
    TERMINAL
}

class VerbViewModel(application: Application) : AndroidViewModel(application) {

    private val intentEngine = IntentEngine()
    private val actionRegistry = ActionRegistry(application.applicationContext)
    private val semanticEngine = SemanticEngine()
    private val agentMemoryStore = AgentMemoryStore(application.applicationContext)
    val repository = VerbRepository.getInstance(application)

    val roomCommandHistory: StateFlow<List<CommandHistoryEntity>> = repository.commandHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val roomTerminalOutputs: StateFlow<List<TerminalOutputEntity>> = repository.terminalOutputs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val terminalViewModel = TerminalViewModel(application)
    

    private val _activeTab = MutableStateFlow(VerbTab.ASK)
    val activeTab: StateFlow<VerbTab> = _activeTab.asStateFlow()

    private val _queryInput = MutableStateFlow("")
    val queryInput: StateFlow<String> = _queryInput.asStateFlow()

    private val _currentActionResult = MutableStateFlow<ActionResult?>(null)
    val currentActionResult: StateFlow<ActionResult?> = _currentActionResult.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _historyList = MutableStateFlow<List<ActionResult>>(emptyList())
    val historyList: StateFlow<List<ActionResult>> = _historyList.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _activeSemanticEntity = MutableStateFlow<SemanticEntity?>(null)
    val activeSemanticEntity: StateFlow<SemanticEntity?> = _activeSemanticEntity.asStateFlow()

    private val _confirmationPendingResult = MutableStateFlow<ActionResult?>(null)
    val confirmationPendingResult: StateFlow<ActionResult?> = _confirmationPendingResult.asStateFlow()

    init {
        // Load persistent conversation history from Room DB and Memory Store
        viewModelScope.launch(Dispatchers.IO) {
            val roomMsgs = repository.loadChatMessages()
            if (roomMsgs.isNotEmpty()) {
                _chatMessages.value = roomMsgs
            } else {
                val defaultMsgs = agentMemoryStore.loadMessages()
                _chatMessages.value = defaultMsgs
                repository.saveAllChatMessages(defaultMsgs)
            }
        }

        // Execute initial default storage check on launch if history is clean
        if (_historyList.value.isEmpty()) {
            submitQuery("show me my storage")
        }
    }

    fun selectTab(tab: VerbTab) {
        _activeTab.value = tab
    }

    fun updateQueryInput(newInput: String) {
        _queryInput.value = newInput
    }

    fun clearAgentMemory() {
        agentMemoryStore.clearMemory()
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearSessionData()
            val resetMsgs = agentMemoryStore.loadMessages()
            _chatMessages.value = resetMsgs
            repository.saveAllChatMessages(resetMsgs)
        }
    }

    fun executeCommandFromChat(command: String) {
        if (command.isBlank()) return
        terminalViewModel.executeCommand(command)

        val activeTermOutput = terminalViewModel.terminalOutput.value.takeLast(400)
        val userMsg = ChatMessage(
            sender = ChatSender.USER,
            text = "Execute command in terminal: `$command`"
        )
        val agentMsg = ChatMessage(
            sender = ChatSender.AGENT,
            text = "Dispatched command `$command` to terminal session.",
            linkedTerminalSnippet = if (activeTermOutput.isNotBlank()) activeTermOutput else "Command sent to terminal: $command",
            suggestedCommands = listOf("git status", "pwd", "ls -l", "top")
        )

        val updated = _chatMessages.value + userMsg + agentMsg
        _chatMessages.value = updated
        agentMemoryStore.saveMessages(updated)

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveChatMessage(userMsg)
            repository.saveChatMessage(agentMsg)
            repository.recordTerminalOutput(
                command = command,
                output = activeTermOutput
            )
        }

        // Switch to terminal tab so user sees live output
        _activeTab.value = VerbTab.TERMINAL
    }

    fun submitIntent(intent: com.example.verb.model.VerbIntent) {
        _isExecuting.value = true
        _queryInput.value = intent.summary
        if (intent.id != "terminal.open") {
            _activeTab.value = VerbTab.ASK
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (intent.id == "terminal.open") {
                    _activeTab.value = VerbTab.TERMINAL
                    return@launch
                }
                val res = actionRegistry.executeAction(intent, confirmed = false)
                handleActionResult(res)
            } catch (e: Exception) {
                handleActionResult(unexpectedFailure(intent, e))
            } finally {
                _isExecuting.value = false
            }
        }
    }

    fun submitQuery(query: String) {
        if (query.isBlank()) return
        _isExecuting.value = true
        _queryInput.value = ""

        val currentTerminalContext = terminalViewModel.terminalOutput.value.takeLast(600)

        // 1. Append User Message
        val userMsg = ChatMessage(
            sender = ChatSender.USER,
            text = query
        )
        val currentListWithUser = _chatMessages.value + userMsg
        _chatMessages.value = currentListWithUser

        viewModelScope.launch(Dispatchers.IO) {
            var intent: com.example.verb.model.VerbIntent? = null
            try {
                val resolvedIntent = intentEngine.resolveIntent(query)
                intent = resolvedIntent
                if (resolvedIntent.id == "terminal.open") {
                    _activeTab.value = VerbTab.TERMINAL
                    val agentNavMsg = ChatMessage(
                        sender = ChatSender.AGENT,
                        text = "Opened terminal session.",
                        suggestedCommands = listOf("ls -la", "pwd", "git status")
                    )
                    val updated = _chatMessages.value + agentNavMsg
                    _chatMessages.value = updated
                    agentMemoryStore.saveMessages(updated)
                    return@launch
                }

                val actionResult = actionRegistry.executeAction(resolvedIntent, confirmed = false)
                handleActionResult(actionResult)

                // Save command execution and user chat message to Room DB
                repository.recordCommand(query, actionResult)
                repository.saveChatMessage(userMsg)

                // Generate smart agent chatbot response with memory & terminal awareness
                val (responseContent, suggestedCmds) = generateAgentAnswer(query, resolvedIntent, actionResult, currentTerminalContext)

                val agentMsg = ChatMessage(
                    sender = ChatSender.AGENT,
                    text = responseContent,
                    actionResult = actionResult,
                    suggestedCommands = suggestedCmds,
                    linkedTerminalSnippet = if (currentTerminalContext.isNotBlank()) currentTerminalContext else null
                )

                val updatedList = _chatMessages.value + agentMsg
                _chatMessages.value = updatedList
                agentMemoryStore.saveMessages(updatedList)
                repository.saveChatMessage(agentMsg)

            } catch (e: Exception) {
                val failure = unexpectedFailure(intent, e)
                handleActionResult(failure)
                val agentErrorMsg = ChatMessage(
                    sender = ChatSender.AGENT,
                    text = "I encountered an error executing that request: ${e.localizedMessage ?: "Unknown error"}",
                    actionResult = failure
                )
                val updatedList = _chatMessages.value + agentErrorMsg
                _chatMessages.value = updatedList
                agentMemoryStore.saveMessages(updatedList)
            } finally {
                _isExecuting.value = false
            }
        }
    }

    private fun generateAgentAnswer(
        query: String,
        intent: com.example.verb.model.VerbIntent,
        result: ActionResult,
        terminalContext: String
    ): Pair<String, List<String>> {
        val q = query.lowercase().trim()

        if (q.contains("who are you") || q.contains("hi") || q.contains("hello") || q.contains("what can you do")) {
            return Pair(
                "I am Verb, your persistent AI Terminal Agent. I have full memory across turns and live visibility into your active shell session.\n\nI can execute terminal commands, check system resources (storage, RAM, processes), analyze error logs, and manage files for you.",
                listOf("Check storage", "Check memory", "Show running processes", "Show files", "pwd")
            )
        }

        if (q.contains("terminal") || q.contains("output") || q.contains("log") || q.contains("what is in my terminal")) {
            val snippetSummary = if (terminalContext.isNotBlank()) {
                "Here is the recent output from your active terminal session:\n```\n${terminalContext.takeLast(300)}\n```"
            } else {
                "Your terminal session is currently clean and ready for input."
            }
            return Pair(
                "I inspected your linked terminal session.\n\n$snippetSummary",
                listOf("ls -la", "pwd", "git status", "top")
            )
        }

        if (q.contains("git")) {
            return Pair(
                "Git is available in your terminal session. Common git operations:\n• `git status`: Check modified files\n• `git log -n 5`: View recent commits\n• `git diff`: Review active changes",
                listOf("git status", "git log -n 5", "git branch", "git diff")
            )
        }

        val text = when (intent.id) {
            "storage.summary" -> "I checked your local storage system:\n• ${result.summary}"
            "memory.summary" -> "Here is your current memory utilization:\n• ${result.summary}"
            "process.list" -> "I retrieved your active system processes:\n• ${result.summary}"
            "process.stop" -> "Requested process termination for PID: ${intent.parameters["pid"] ?: "unknown"}.\n${result.summary}"
            "command.execute" -> "Executed terminal command: `${intent.parameters["command"] ?: query}`.\n${result.summary}"
            else -> "I processed your request: '${result.title}'.\n${result.summary}"
        }

        val cmds = when (intent.id) {
            "storage.summary" -> listOf("df -h", "du -sh *", "ls -la")
            "memory.summary" -> listOf("free -m", "top -n 1")
            "process.list" -> listOf("ps aux", "top")
            else -> listOf("ls -la", "pwd", "git status", "help")
        }

        return Pair(text, cmds)
    }

    fun confirmPendingAction() {
        val pending = _confirmationPendingResult.value ?: return
        _confirmationPendingResult.value = null
        _isExecuting.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val intent = pending.originalIntent
            try {
                if (intent == null) {
                    handleActionResult(unexpectedFailure(null, IllegalStateException("Missing confirmed intent.")))
                } else {
                    val res = actionRegistry.executeAction(intent, confirmed = true)
                    handleActionResult(res)
                    val confirmMsg = ChatMessage(
                        sender = ChatSender.AGENT,
                        text = "Confirmed and executed action: ${res.title}\n${res.summary}",
                        actionResult = res
                    )
                    val updated = _chatMessages.value + confirmMsg
                    _chatMessages.value = updated
                    agentMemoryStore.saveMessages(updated)
                }
            } catch (e: Exception) {
                handleActionResult(unexpectedFailure(intent, e))
            } finally {
                _isExecuting.value = false
            }
        }
    }

    fun dismissConfirmation() {
        _confirmationPendingResult.value = null
    }

    fun inspectSemanticText(text: String, contextText: String? = null) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.Default) {
            val entity = semanticEngine.analyzeText(text, contextText)
            _activeSemanticEntity.value = entity
        }
    }

    fun closeSemanticLens() {
        _activeSemanticEntity.value = null
    }

    fun openTerminal() {
        _activeTab.value = VerbTab.TERMINAL
    }

    private fun handleActionResult(result: ActionResult) {
        if (result.requiresConfirmation) {
            _confirmationPendingResult.value = result
        } else {
            _currentActionResult.value = result
            _historyList.value = listOf(result) + _historyList.value.take(9)
        }
    }

    private fun unexpectedFailure(
        intent: com.example.verb.model.VerbIntent?,
        error: Exception
    ): ActionResult = ActionResult(
        intentId = intent?.id ?: "internal.error",
        title = "Action Failed",
        summary = "Verb could not complete this action.",
        isSuccess = false,
        errorMessage = error.localizedMessage ?: "Unexpected runtime error.",
        originalIntent = intent
    )

    override fun onCleared() {
        super.onCleared()
        
    }
}

