package app.callguard.keygen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.callguard.license.LicenseCrypto
import org.json.JSONArray
import org.json.JSONObject

private data class Issued(val name: String, val id: String, val time: Long)

private class Store(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("keys", Context.MODE_PRIVATE)

    var privateKey: String?
        get() = sp.getString("private_key", null)
        set(value) = sp.edit().apply { if (value == null) remove("private_key") else putString("private_key", value) }.apply()

    fun history(): List<Issued> {
        val arr = JSONArray(sp.getString("history", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Issued(o.getString("name"), o.getString("id"), o.getLong("time"))
        }
    }

    fun add(item: Issued) {
        val arr = JSONArray()
        (listOf(item) + history()).forEach {
            arr.put(JSONObject().put("name", it.name).put("id", it.id).put("time", it.time))
        }
        sp.edit().putString("history", arr.toString()).apply()
    }
}

class KeysActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)
        setContent {
            val scheme = if (isSystemInDarkTheme()) {
                darkColorScheme(primary = Color(0xFFEF9F27))
            } else {
                lightColorScheme(primary = Color(0xFF854F0B))
            }
            MaterialTheme(colorScheme = scheme) {
                Scaffold { padding ->
                    var key by remember { mutableStateOf(store.privateKey) }
                    Column(
                        Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("CallGuard Keys", style = MaterialTheme.typography.headlineSmall)
                        if (key == null) {
                            ImportKey { store.privateKey = it; key = it }
                        } else {
                            IssueCodes(store, key!!) { store.privateKey = null; key = null }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportKey(onSaved: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Text("Paste your private key once (from PRIVATE_KEY_keep_secret.txt). It stays on this phone only.")
    OutlinedTextField(
        value = text, onValueChange = { text = it; error = false },
        label = { Text("Private key") }, minLines = 4, isError = error,
        modifier = Modifier.fillMaxWidth(),
    )
    if (error) Text("That is not a valid key.", color = MaterialTheme.colorScheme.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { clipboard.getText()?.text?.let { text = it; error = false } }) { Text("Paste") }
        Button(onClick = {
            val clean = text.filter { !it.isWhitespace() }
            val ok = try { LicenseCrypto.sign(clean, "0000-0000-0000-0000"); true } catch (e: Exception) { false }
            if (ok) onSaved(clean) else error = true
        }, enabled = text.isNotBlank()) { Text("Save key") }
    }
}

@Composable
private fun IssueCodes(store: Store, privateKey: String, onChangeKey: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var name by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(store.history()) }

    OutlinedTextField(
        value = name, onValueChange = { name = it },
        label = { Text("Customer name (optional)") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = deviceId, onValueChange = { deviceId = it; error = null },
        label = { Text("Customer Device ID") }, placeholder = { Text("A1B2-C3D4-E5F6-7890") },
        singleLine = true, isError = error != null,
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { clipboard.getText()?.text?.let { deviceId = extractId(it); error = null } }) {
            Text("Paste")
        }
        Button(onClick = {
            if (!LicenseCrypto.isValidId(deviceId)) {
                error = "Device ID must be 16 characters (0-9 and A-F)."
            } else {
                val id = LicenseCrypto.formatId(deviceId)
                val before = history.count { it.id == id }
                code = LicenseCrypto.sign(privateKey, id)
                note = if (before == 0) "First code for this phone." else "Issued $before time(s) before for this phone."
                store.add(Issued(name.trim(), id, System.currentTimeMillis()))
                history = store.history()
            }
        }) { Text("Generate code") }
    }

    code?.let { c ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Activation code", style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(c, fontFamily = FontFamily.Monospace) }
                Text(note, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(c)) }) { Text("Copy") }
                    Button(onClick = {
                        val msg = "Your CallGuard activation code:\n\n$c\n\n" +
                            "Open CallGuard, tap Paste, then Activate. This code works only on your phone."
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, msg)
                        }
                        context.startActivity(Intent.createChooser(send, "Send code"))
                    }) { Text("Share") }
                }
            }
        }
    }

    Text("Issued codes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    if (history.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodySmall)
    history.forEach { h ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(h.name.ifBlank { "No name" })
                Text(
                    "${h.id} · ${DateUtils.formatDateTime(context, h.time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { name = h.name; deviceId = h.id; error = null }) { Text("Reuse") }
        }
    }
    TextButton(onClick = onChangeKey) { Text("Remove private key from this phone") }
}

/** Accepts a whole pasted WhatsApp message and pulls the 16-character ID out of it. */
private fun extractId(text: String): String {
    val match = Regex("([0-9A-Fa-f]{4}[-\\s]?){3}[0-9A-Fa-f]{4}").find(text)
    return match?.value ?: text
}
