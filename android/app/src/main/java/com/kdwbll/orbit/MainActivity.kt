package com.kdwbll.orbit

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Approval(val id: String, val title: String, val detail: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OrbitApp() }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OrbitApp() {
    val context = LocalContext.current
    val connection = remember { RelayConnection(context) }
    val ui = remember { Handler(Looper.getMainLooper()) }
    var link by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<RelayConnection.State>(RelayConnection.State.Idle) }
    var error by remember { mutableStateOf<String?>(null) }
    val transcript = remember { mutableStateListOf<String>() }
    val approvals = remember { mutableStateListOf<Approval>() }
    DisposableEffect(connection) {
        connection.onState = { next -> ui.post { state = next } }
        connection.onEvent = { type, frame -> ui.post {
            when (type) {
                "ev" -> when (frame.optString("k")) {
                    "msg.user", "msg.assistant" -> transcript += frame.optJSONObject("d")?.optString("text").orEmpty()
                    "approval.request" -> frame.optJSONObject("d")?.let { request ->
                        val id = request.optString("id")
                        if (id.isNotBlank() && approvals.none { it.id == id }) {
                            approvals += Approval(id, request.optString("title", "需要确认"), request.optString("detail"))
                        }
                    }
                }
                "live" -> if (frame.optString("k") == "msg.delta") transcript += frame.optJSONObject("d")?.optString("text").orEmpty()
            }
        } }
        onDispose { connection.close() }
    }
    LaunchedEffect(connection) { connection.resume() }
    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Orbit") }) }) { padding ->
            Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state !is RelayConnection.State.Connected) {
                    Text("连接家里的 Mac", style = MaterialTheme.typography.headlineSmall)
                    Text("在 Mac 上运行 opa pair，然后把配对链接粘贴到这里。连接全程端到端加密。")
                    OutlinedTextField(link, { link = it }, label = { Text("opa://pair?d=…") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = {
                        error = try { connection.pair(PairingPayload.parse(link)); null } catch (e: Exception) { e.message ?: "配对链接无效" }
                    }, enabled = link.isNotBlank()) { Text("开始配对") }
                    when (val current = state) {
                        is RelayConnection.State.AwaitingMac -> Text("请在 Mac 上核对校验码：${current.sas}")
                        is RelayConnection.State.Connecting -> Text("正在建立加密连接…")
                        is RelayConnection.State.Failed -> Text(current.reason, color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                } else {
                    Text("已连接", style = MaterialTheme.typography.headlineSmall)
                    approvals.forEach { approval ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(approval.title, style = MaterialTheme.typography.titleMedium)
                                if (approval.detail.isNotBlank()) Text(approval.detail)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        connection.request("approval.respond", org.json.JSONObject().put("approvalId", approval.id).put("decision", "approve"))
                                        approvals.remove(approval)
                                    }) { Text("同意") }
                                    OutlinedButton(onClick = {
                                        connection.request("approval.respond", org.json.JSONObject().put("approvalId", approval.id).put("decision", "deny"))
                                        approvals.remove(approval)
                                    }) { Text("拒绝") }
                                }
                            }
                        }
                    }
                    transcript.forEach { Text(it) }
                    OutlinedTextField(message, { message = it }, label = { Text("发给 Orbit") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { connection.sendChat(message); message = "" }, enabled = message.isNotBlank()) { Text("发送") }
                }
            }
        }
    }
}
