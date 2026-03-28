package org.gala.game

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CharacterActivity : AppCompatActivity() {

    private lateinit var charactersRecyclerView: RecyclerView
    private lateinit var addCharacterButton: FloatingActionButton
    private lateinit var loadingProgress: View
    private lateinit var titleText: TextView
    private lateinit var backButton: ImageButton

    private var worldId: Int = 0
    private var worldName: String = ""
    private var pyModule: PyObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character)

        worldId = intent.getIntExtra("world_id", 0)
        worldName = intent.getStringExtra("world_name") ?: "未知世界"

        initViews()
        initPython()
        loadCharacters()
    }

    private fun initViews() {
        charactersRecyclerView = findViewById(R.id.charactersRecyclerView)
        addCharacterButton = findViewById(R.id.addCharacterButton)
        loadingProgress = findViewById(R.id.loadingProgress)
        titleText = findViewById(R.id.titleText)
        backButton = findViewById(R.id.backButton)

        titleText.text = "$worldName - 角色管理"
        charactersRecyclerView.layoutManager = LinearLayoutManager(this)

        backButton.setOnClickListener { finish() }
        addCharacterButton.setOnClickListener { showCreateCharacterDialog() }
    }

    private fun initPython() {
        val py = Python.getInstance()
        pyModule = py.getModule("backend")
    }

    private fun loadCharacters() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val characters = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_characters_by_world", worldId)?.asList()?.map { pyObj ->
                        Character(
                            id = pyObj["id"].toString().toInt(),
                            worldId = pyObj["world_id"].toString().toInt(),
                            name = pyObj["name"].toString(),
                            background = pyObj["background"]?.toString() ?: "",
                            description = pyObj["description"]?.toString() ?: "",
                            avatarPath = pyObj["avatar_path"]?.toString(),
                            location = pyObj["location"]?.toString() ?: "",
                            gender = pyObj["gender"]?.toString() ?: "female",
                            activityScore = pyObj["activity_score"]?.toString()?.toInt() ?: 0
                        )
                    } ?: emptyList()
                }

                charactersRecyclerView.adapter = CharacterAdapter(characters) { character ->
                    editCharacter(character)
                }

                if (characters.isEmpty()) {
                    titleText.text = "$worldName - 暂无角色"
                }
            } catch (e: Exception) {
                showError("加载角色失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showCreateCharacterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_character, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.characterNameInput)
        val descInput = dialogView.findViewById<TextInputEditText>(R.id.characterDescInput)
        val genderSpinner = dialogView.findViewById<Spinner>(R.id.genderSpinner)

        ArrayAdapter.createFromResource(
            this,
            R.array.gender_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            genderSpinner.adapter = adapter
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("创建新角色")
            .setView(dialogView)
            .setPositiveButton("创建") { dialog, _ ->
                val name = nameInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                val gender = genderSpinner.selectedItem.toString()

                if (name.isNotEmpty()) {
                    createCharacter(name, desc, gender)
                } else {
                    showError("请输入角色名称")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createCharacter(name: String, description: String, gender: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("create_character", worldId, name, "", description, "", gender)
                }
                loadCharacters()
            } catch (e: Exception) {
                showError("创建角色失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun editCharacter(character: Character) {
        val intent = android.content.Intent(this, CharacterEditActivity::class.java)
        intent.putExtra("world_id", worldId)
        intent.putExtra("world_name", worldName)
        intent.putExtra("character_id", character.id)
        intent.putExtra("character_name", character.name)
        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        addCharacterButton.isEnabled = !show
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("错误")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
}

data class Character(
    val id: Int,
    val worldId: Int,
    val name: String,
    val background: String,
    val description: String,
    val avatarPath: String?,
    val location: String,
    val gender: String,
    val activityScore: Int
)

class CharacterAdapter(
    private val characters: List<Character>,
    private val onCharacterClick: (Character) -> Unit
) : RecyclerView.Adapter<CharacterAdapter.CharacterViewHolder>() {

    class CharacterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.characterNameText)
        val descText: TextView = view.findViewById(R.id.characterDescText)
        val locationText: TextView = view.findViewById(R.id.characterLocationText)
        val avatarText: TextView = view.findViewById(R.id.avatarText)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): CharacterViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_character, parent, false)
        return CharacterViewHolder(view)
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        val character = characters[position]
        holder.nameText.text = character.name
        holder.descText.text = character.description.ifEmpty { character.background.ifEmpty { "暂无描述" } }
        holder.locationText.text = character.location.ifEmpty { "位置未知" }
        holder.avatarText.text = character.name.firstOrNull()?.toString() ?: "?"
        holder.itemView.setOnClickListener { onCharacterClick(character) }
    }

    override fun getItemCount() = characters.size
}
