package org.videolan.vlc.remoteaccessserver.gui.remoteaccess

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import org.videolan.tools.Settings
import org.videolan.vlc.remoteaccessserver.RemoteAccessApiTokens
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer
import java.util.Date

/**
 * Screen to create and revoke the access tokens used by AI clients (through the vlc-mcp helper)
 * and to read the connection details the helper needs. A token is only shown right after it is created.
 */
class AiAccessActivity : AppCompatActivity() {
    private lateinit var tokensText: TextView
    private lateinit var resultText: TextView
    private lateinit var copyButton: Button
    private var lastSettingsText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "AI access tokens"
        val pad = (20 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun text(value: String, selectable: Boolean = false) = TextView(this).apply {
            text = value
            textSize = 15f
            setTextIsSelectable(selectable)
            setPadding(0, pad / 2, 0, pad / 2)
        }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply { text = label; setOnClickListener { onClick() } }

        column.addView(text("Tokens let an AI client on your computer control this app through the vlc-mcp helper. " +
                "Remote Access must be enabled. The connection works over your local network (or Tailscale)."))
        column.addView(button("Create token") { createToken() })
        resultText = text("", selectable = true).also { column.addView(it) }
        copyButton = button("Copy settings") {
            lastSettingsText?.let {
                getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("VLC MCP", it))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
        }.also { it.isEnabled = false; column.addView(it) }
        column.addView(text("Existing tokens"))
        tokensText = text("").also { column.addView(it) }
        column.addView(button("Revoke all tokens") {
            RemoteAccessApiTokens.revokeAll(Settings.getInstance(this))
            lastSettingsText = null
            resultText.text = ""
            copyButton.isEnabled = false
            refreshList()
        })
        setContentView(ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(column)
        })
        refreshList()
    }

    private fun createToken() {
        val server = RemoteAccessServer.getInstance(this)
        val port = server.httpsPort()
        if (port == null) {
            Toast.makeText(this, "The Remote Access server is not running. Enable it first.", Toast.LENGTH_LONG).show()
            return
        }
        val token = RemoteAccessApiTokens.create(Settings.getInstance(this), "AI client")
        val address = server.getServerAddresses().firstOrNull()?.substringAfter("://")?.substringBefore(":") ?: "<phone-ip>"
        val fingerprint = server.certificateFingerprintSha256() ?: "unknown"
        val settingsText = "VLC_URL=https://$address:$port\nVLC_TOKEN=$token\nVLC_CERT_SHA256=$fingerprint"
        lastSettingsText = settingsText
        resultText.text = "Copy these now. The token will not be shown again.\n\n$settingsText"
        copyButton.isEnabled = true
        refreshList()
    }

    private fun refreshList() {
        val tokens = RemoteAccessApiTokens.list(Settings.getInstance(this))
        tokensText.text = if (tokens.isEmpty()) "None" else tokens.joinToString("\n") {
            "${it.name}  (${it.id}), created ${DateFormat.getMediumDateFormat(this).format(Date(it.createdAt))}"
        }
    }
}
