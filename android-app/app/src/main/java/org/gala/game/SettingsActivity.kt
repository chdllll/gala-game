package org.gala.game

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var apiKey1Input: TextInputEditText
    private lateinit var apiModel1Input: TextInputEditText
    private lateinit var apiKey2Input: TextInputEditText
    private lateinit var apiModel2Input: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var loadingProgress: ProgressBar

    private var pyModule: PyObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        initPython()
        loadSettings()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)
        apiKey1Input = findViewById(R.id.apiKey1Input)
        apiModel1Input = findViewById(R.id.apiModel1Input)
        apiKey2Input = findViewById(R.id.apiKey2Input)
        apiModel2Input = findViewById(R.id.apiModel2Input)
        saveButton = findViewById(R.id.saveButton)
        loadingProgress = findViewById(R.id.loadingProgress)

        titleText.text = "API设置"

        backButton.setOnClickListener { finish() }
        saveButton.setOnClickListener { saveSettings() }
    }

    private fun initPython() {
        val py = Python.getInstance()
        pyModule = py.getModule("backend")
    }

    private fun loadSettings() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val config = withContext(Dispatchers.IO) {
                    pyModule?.callAttr("get_api_config")?.asMap()
                }

                if (config != null) {
                    apiKey1Input.setText(config["api1_key"]?.toString() ?: "")
                    apiModel1Input.setText(config["api1_model"]?.toString() ?: "")
                    apiKey2Input.setText(config["api2_key"]?.toString() ?: "")
                    apiModel2Input.setText(config["api2_model"]?.toString() ?: "")
                }
            } catch (e: Exception) {
                showError("加载设置失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun saveSettings() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val apiKey1 = apiKey1Input.text.toString().trim()
                val apiModel1 = apiModel1Input.text.toString().trim()
                val apiKey2 = apiKey2Input.text.toString().trim()
                val apiModel2 = apiModel2Input.text.toString().trim()

                withContext(Dispatchers.IO) {
                    pyModule?.callAttr("update_api_config", 
                        apiKey1.ifEmpty { null },
                        apiModel1.ifEmpty { null },
                        apiKey2.ifEmpty { null },
                        apiModel2.ifEmpty { null }
                    )
                    pyModule?.callAttr("refresh_api_client")
                }

                showToast("设置已保存")
                finish()
            } catch (e: Exception) {
                showError("保存失败: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        saveButton.isEnabled = !show
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
