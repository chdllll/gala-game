package org.gala.game

import android.app.Dialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GameActivity : AppCompatActivity() {

    private lateinit var backgroundImage: ImageView
    private lateinit var characterPortrait: ImageView
    private lateinit var chatScrollView: ScrollView
    private lateinit var chatContainer: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var listenButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var loadingProgress: View
    private lateinit var worldTitle: TextView
    private lateinit var locationText: TextView
    private lateinit var perspectiveContainer: LinearLayout
    private lateinit var callRequestLayout: LinearLayout
    private lateinit var callRequestText: TextView
    private lateinit var acceptCallButton: android.widget.Button
    private lateinit var rejectCallButton: android.widget.Button

    private var worldId: Int = 0
    private var worldName: String = ""
    private var currentPerspective: String = "user"
    private var currentCharacterId: Int? = null
    private var currentLocation: String = ""
    private var currentSessionId: Int = 0

    private var pyModule: PyObject? = null
    private var dialogueManager: PyObject? = null
    private var characters: List<Map<String, Any?>> = emptyList()
    private var locations: List<Map<String, Any?>> = emptyList()

    private var settingsDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        worldId = intent.getIntExtra("world_id", 0)
        worldName = intent.getStringExtra("world_name") ?: "未知世界"

        initViews()
        initPython()
        loadWorldData()
    }

    private fun initViews() {
        backgroundImage = findViewById(R.id.backgroundImage)
        characterPortrait = findViewById(R.id.characterPortrait)
        chatScrollView = findViewById(R.id.chatScrollView)
        chatContainer = findViewById(R.id.chatContainer)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        listenButton = findViewById(R.id.listenButton)
        settingsButton = findViewById(R.id.settingsButton)
        backButton = findViewById(R.id.backButton)
        loadingProgress = findViewById(R.id.loadingProgress)
        worldTitle = findViewById(R.id.worldTitle)
        locationText = findViewById(R.id.locationText)
        perspectiveContainer = findViewById(R.id.perspectiveContainer)
        callRequestLayout = findViewById(R.id.callRequestLayout)
        callRequestText = findViewById(R.id.callRequestText)
        acceptCallButton = findViewById(R.id.acceptCallButton)
        rejectCallButton = findViewById(R.id.rejectCallButton)

        worldTitle.text = worldName

        sendButton.setOnClickListener { sendMessage() }
        listenButton.setOnClickListener { onListenClicked() }
        settingsButton.setOnClickListener { showSettingsDialog() }
        backButton.setOnClickListener { finish() }
        acceptCallButton.setOnClickListener { acceptCallRequest() }
        rejectCallButton.setOnClickListener { rejectCallRequest() }
    }

    private fun initPython() {
        val py = Python.getInstance()
        pyModule = py.getModule("backend")
    }

    private fun loadWorldData() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val worldData = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_world", worldId)?.asMap()
                }

                currentLocation = worldData?.get("user_location")?.toString() ?: ""
                locationText.text = currentLocation.ifEmpty { "未知位置" }

                characters = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_characters_by_world", worldId)?.asList()?.map { pyObj ->
                        pyObj.asMap().mapKeys { it.key.toString() }
                    } ?: emptyList()
                }

                locations = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_locations", worldId)?.asList()?.map { pyObj ->
                        pyObj.asMap().mapKeys { it.key.toString() }
                    } ?: emptyList()
                }

                initDialogueManager()
                loadPerspectives()
                loadChatHistory()
                loadBackgroundImage()
                checkPendingCallRequests()

            } catch (e: Exception) {
                showError("加载数据失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun initDialogueManager() {
        val characterIdArg = if (currentPerspective == "user") null else currentCharacterId
        dialogueManager = pyModule?.callAttr("get_dialogue_manager", worldId, characterIdArg)
    }

    private fun loadPerspectives() {
        perspectiveContainer.removeAllViews()

        val userPerspective = layoutInflater.inflate(R.layout.item_perspective, perspectiveContainer, false) as TextView
        userPerspective.text = "玩家"
        userPerspective.isSelected = currentPerspective == "user"
        userPerspective.setOnClickListener { switchPerspective("user", null) }
        perspectiveContainer.addView(userPerspective)

        for (character in characters) {
            val charId = character["id"]?.toString()?.toIntOrNull() ?: continue
            val charName = character["name"]?.toString() ?: continue

            val perspectiveView = layoutInflater.inflate(R.layout.item_perspective, perspectiveContainer, false) as TextView
            perspectiveView.text = charName
            perspectiveView.isSelected = currentPerspective == "character" && currentCharacterId == charId
            perspectiveView.setOnClickListener { switchPerspective("character", charId) }
            perspectiveContainer.addView(perspectiveView)
        }
    }

    private fun switchPerspective(perspective: String, characterId: Int?) {
        if (perspective == currentPerspective && characterId == currentCharacterId) return

        showLoading(true)
        lifecycleScope.launch {
            try {
                currentPerspective = perspective
                currentCharacterId = characterId

                initDialogueManager()
                loadPerspectives()
                loadChatHistory()
                loadCharacterPortrait()

            } catch (e: Exception) {
                showError("切换视角失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private suspend fun loadChatHistory() {
        chatContainer.removeAllViews()

        val history = withContext(Dispatchers.IO) {
            dialogueManager?.callAttr("get_history")?.toString() ?: ""
        }

        if (history.isNotEmpty()) {
            val messages = history.split("\n\n")
            for (message in messages) {
                if (message.contains(": ")) {
                    val parts = message.split(": ", limit = 2)
                    if (parts.size == 2) {
                        addMessageBubble(parts[0], parts[1])
                    }
                }
            }
        } else {
            addMessageBubble("系统", "开始你的冒险吧！")
        }

        scrollToBottom()
    }

    private fun addMessageBubble(sender: String, message: String, characterId: Int? = null) {
        val messageView = layoutInflater.inflate(R.layout.item_message, chatContainer, false)

        val senderName = messageView.findViewById<TextView>(R.id.senderName)
        val messageText = messageView.findViewById<TextView>(R.id.messageText)
        val avatarImage = messageView.findViewById<ImageView>(R.id.avatarImage)

        senderName.text = sender
        messageText.text = message

        if (sender != "你" && sender != "系统") {
            val character = characters.find { it["name"]?.toString() == sender }
            val avatarPath = character?.get("avatar_path")?.toString()
            if (!avatarPath.isNullOrEmpty()) {
                loadAvatarImage(avatarImage, avatarPath)
                avatarImage.visibility = View.VISIBLE
            }
        }

        chatContainer.addView(messageView)
    }

    private fun loadAvatarImage(imageView: ImageView, path: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(path)
                imageView.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
        }
    }

    private fun loadBackgroundImage() {
        val location = locations.find { it["name"]?.toString() == currentLocation }
        val imagePath = location?.get("image_path")?.toString()

        if (!imagePath.isNullOrEmpty()) {
            try {
                val file = File(imagePath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(imagePath)
                    backgroundImage.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun loadCharacterPortrait() {
        if (currentPerspective == "user") {
            characterPortrait.visibility = View.GONE
            return
        }

        val character = characters.find { it["id"]?.toString()?.toIntOrNull() == currentCharacterId }
        val avatarPath = character?.get("avatar_path")?.toString()

        if (!avatarPath.isNullOrEmpty()) {
            try {
                val file = File(avatarPath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(avatarPath)
                    characterPortrait.setImageBitmap(bitmap)
                    characterPortrait.visibility = View.VISIBLE
                    return
                }
            } catch (e: Exception) {
            }
        }

        characterPortrait.visibility = View.GONE
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isEmpty()) return

        messageInput.text.clear()
        addMessageBubble("你", message)
        showLoading(true)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    dialogueManager?.callAttr("send_message", message)?.toString() ?: "无响应"
                }

                val senderName = if (currentPerspective == "character") {
                    characters.find { it["id"]?.toString()?.toIntOrNull() == currentCharacterId }?.get("name")?.toString() ?: "AI"
                } else {
                    "AI"
                }

                addMessageBubble(senderName, response)
                scrollToBottom()

            } catch (e: Exception) {
                addMessageBubble("系统", "错误: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun onListenClicked() {
        if (dialogueManager == null) {
            showError("对话管理器未初始化")
            return
        }
        
        showLoading(true)
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    dialogueManager?.callAttr("let_character_speak", currentLocation)?.toString() ?: "无响应"
                }
                
                if (response.isNotEmpty()) {
                    val parts = response.split(": ", limit = 2)
                    if (parts.size == 2) {
                        addMessageBubble(parts[0], parts[1])
                    } else {
                        addMessageBubble("角色", response)
                    }
                    scrollToBottom()
                }
            } catch (e: Exception) {
                showError("聆听失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showSettingsDialog() {
        settingsDialog?.dismiss()

        settingsDialog = Dialog(this).apply {
            setContentView(R.layout.dialog_game_settings)
            window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )

            findViewById<ImageButton>(R.id.closeButton).setOnClickListener { dismiss() }

            findViewById<android.widget.Button>(R.id.locationButton).setOnClickListener {
                dismiss()
                openLocationActivity()
            }

            findViewById<android.widget.Button>(R.id.activeCallButton).setOnClickListener {
                dismiss()
                openCallActivity()
            }

            findViewById<android.widget.Button>(R.id.callHistoryButton).setOnClickListener {
                dismiss()
                openCallHistoryActivity()
            }

            findViewById<android.widget.Button>(R.id.characterButton).setOnClickListener {
                dismiss()
                openCharacterActivity()
            }

            findViewById<android.widget.Button>(R.id.memoryButton).setOnClickListener {
                dismiss()
                openMemoryActivity()
            }

            findViewById<android.widget.Button>(R.id.apiSettingsButton).setOnClickListener {
                dismiss()
                openSettingsActivity()
            }

            show()
        }
    }

    private fun openLocationActivity() {
        val intent = android.content.Intent(this, LocationActivity::class.java)
        intent.putExtra("world_id", worldId)
        intent.putExtra("world_name", worldName)
        startActivity(intent)
    }

    private fun openCallActivity() {
        val intent = android.content.Intent(this, CallActivity::class.java)
        intent.putExtra("world_id", worldId)
        startActivity(intent)
    }

    private fun openCharacterActivity() {
        val intent = android.content.Intent(this, CharacterActivity::class.java)
        intent.putExtra("world_id", worldId)
        startActivity(intent)
    }

    private fun openMemoryActivity() {
        val intent = android.content.Intent(this, MemoryActivity::class.java)
        intent.putExtra("world_id", worldId)
        startActivity(intent)
    }

    private fun openSettingsActivity() {
        val intent = android.content.Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun openCallHistoryActivity() {
        val intent = android.content.Intent(this, CallHistoryActivity::class.java)
        intent.putExtra("world_id", worldId)
        startActivity(intent)
    }

    private fun checkPendingCallRequests() {
        lifecycleScope.launch {
            try {
                val requests = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_pending_call_requests", worldId)?.asList()?.map { pyObj ->
                        pyObj.asMap().mapKeys { it.key.toString() }
                    } ?: emptyList()
                }

                if (requests.isNotEmpty()) {
                    val request = requests.first()
                    val characterName = request["character_name"]?.toString() ?: "未知角色"
                    val requestId = request["id"]?.toString()?.toIntOrNull()

                    callRequestText.text = "$characterName 正在呼叫你..."
                    callRequestLayout.tag = requestId
                    callRequestLayout.visibility = View.VISIBLE
                } else {
                    callRequestLayout.visibility = View.GONE
                }

            } catch (e: Exception) {
            }
        }
    }

    private fun acceptCallRequest() {
        val requestId = callRequestLayout.tag as? Int ?: return

        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("accept_call_request", requestId)
                }

                callRequestLayout.visibility = View.GONE
                showToast("已接听通话")

            } catch (e: Exception) {
                showError("接听失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun rejectCallRequest() {
        val requestId = callRequestLayout.tag as? Int ?: return

        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("reject_call_request", requestId)
                }

                callRequestLayout.visibility = View.GONE
                showToast("已拒绝通话")

            } catch (e: Exception) {
                showError("拒绝失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun scrollToBottom() {
        chatScrollView.post {
            chatScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        sendButton.isEnabled = !show
        listenButton.isEnabled = !show
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        checkPendingCallRequests()
    }

    override fun onDestroy() {
        settingsDialog?.dismiss()
        super.onDestroy()
    }
}
