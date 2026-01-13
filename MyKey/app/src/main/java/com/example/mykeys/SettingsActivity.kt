package com.example.mykeys

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Setup Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "设置"
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_simple)
        
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Display Version Info
        val tvVersion = findViewById<TextView>(R.id.tv_version)
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        val versionName = packageInfo.versionName
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        tvVersion.text = "版本 $versionName ($versionCode)"

        // Initialize ActivityResultLaunchers
        exportFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK && it.data != null) {
                it.data?.data?.let { uri ->
                    masterPasswordForExport?.let { password ->
                        exportToFile(uri, password)
                        masterPasswordForExport = null
                    } ?: run {
                        Toast.makeText(this, "导出失败: 未获取到加密密码", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        importFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK && it.data != null) {
                it.data?.data?.let { uri ->
                    importFromFile(uri)
                }
            }
        }

        setupAutoLock()
        setupChangePassword()
        setupImportExport()
        setupLogout()
    }

    private fun setupAutoLock() {
        val switchAutoLock = findViewById<MaterialSwitch>(R.id.switch_auto_lock)
        switchAutoLock.isChecked = Prefs.isAutoLock(this)

        switchAutoLock.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAutoLock(this, isChecked)
        }
    }

    private fun setupChangePassword() {
        val cardChangePassword = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_change_password)
        cardChangePassword.setOnClickListener {
            // Navigate to ChangePasswordActivity
            val intent = Intent(this, ChangePasswordActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupImportExport() {
        // Export Card Click
        val cardExport = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_export)
        cardExport.setOnClickListener {
            exportData()
        }

        // Import Card Click
        val cardImport = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_import)
        cardImport.setOnClickListener {
            importData()
        }
    }

    // 用于保存导出时的主密码
    private var masterPasswordForExport: String? = null
    
    // ActivityResultLauncher for file export
    private lateinit var exportFileLauncher: ActivityResultLauncher<Intent>
    
    // ActivityResultLauncher for file import
    private lateinit var importFileLauncher: ActivityResultLauncher<Intent>
    
    private fun exportData() {
        // 先让用户输入加密密码
        showPasswordDialog("输入加密密码", "请输入加密密码用于加密文件") { masterPassword ->
            // 保存密码以便后续使用
            this.masterPasswordForExport = masterPassword
            
            // 然后让用户选择保存位置
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, "mykey_export_${System.currentTimeMillis()}.enc")
            }
            exportFileLauncher.launch(intent)
        }
    }

    // 导入数据
    private fun importData() {
        // 使用Storage Access Framework让用户选择文件
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        importFileLauncher.launch(intent)
    }

    // 处理文件选择结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode != RESULT_OK || data == null) {
            return
        }
        
        when (requestCode) {
            REQUEST_CODE_EXPORT -> {
                data.data?.let { uri ->
                    // 使用之前保存的主密码进行导出
                    masterPasswordForExport?.let { password ->
                        exportToFile(uri, password)
                        // 导出完成后清空密码
                        masterPasswordForExport = null
                    } ?: run {
                        Toast.makeText(this, "导出失败: 未获取到加密密码", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            REQUEST_CODE_IMPORT -> {
                data.data?.let { uri ->
                    importFromFile(uri)
                }
            }
        }
    }

    // 导出数据到文件
    private fun exportToFile(uri: android.net.Uri, masterPassword: String) {
        // 使用协程作用域启动协程
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // 1. 获取所有密码条目
                val entries = PasswordRepository.getAll(this@SettingsActivity)
                
                // 2. 转换为JSON
                val exportData = ExportImportUtils.ExportData(
                    appName = "MyKey",
                    version = ExportImportUtils.EXPORT_VERSION,
                    exportDate = System.currentTimeMillis(),
                    entries = entries
                )
                val jsonData = Gson().toJson(exportData)
                
                // 3. 使用主密码加密数据
                val encryptedData = ExportImportUtils.encryptExportData(this@SettingsActivity, jsonData, masterPassword)
                
                // 4. 保存到文件
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(encryptedData.toByteArray(Charsets.UTF_8))
                    Toast.makeText(this@SettingsActivity, "导出成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 从文件导入数据
    private fun importFromFile(uri: android.net.Uri) {
        // 显示主密码输入对话框
        showPasswordDialog("输入解密密码", "请输入解密密码用于解密文件") { masterPassword ->
            // 使用协程作用域启动协程
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    // 1. 读取文件内容
                    val inputStream = contentResolver.openInputStream(uri)
                    val encryptedData = inputStream?.bufferedReader()?.readText() ?: ""
                    inputStream?.close()
                    
                    // 2. 解密数据
                    val jsonData = ExportImportUtils.decryptExportData(this@SettingsActivity, encryptedData, masterPassword)
                    
                    // 3. 解析JSON数据
                    val importedCount = ExportImportUtils.importData(this@SettingsActivity, jsonData)
                    
                    Toast.makeText(this@SettingsActivity, "导入成功，共导入 $importedCount 条密码", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    // 显示友好的错误提示
                    val errorMessage = when {
                        // 解密密码错误
                        e is javax.crypto.BadPaddingException || 
                        e is javax.crypto.IllegalBlockSizeException ||
                        e.message?.contains("BadPaddingException") == true || 
                        e.message?.contains("IllegalBlockSizeException") == true -> "解密密码错误"
                        // 文件格式或损坏错误
                        e is com.google.gson.JsonSyntaxException ||
                        e is java.lang.IllegalStateException ||
                        e is java.io.IOException ||
                        e.message?.contains("JSON") == true ||
                        e.message?.contains("Expect") == true ||
                        e.message?.contains("Base64") == true -> "文件格式错误"
                        // 版本兼容错误
                        e is java.lang.IllegalArgumentException && e.message?.contains("不兼容的导出版本") == true -> "不兼容的导出版本"
                        // 其他解密相关错误
                        e is java.security.InvalidKeyException ||
                        e.message?.contains("InvalidKeyException") == true ||
                        e.message?.contains("GCM") == true ||
                        e.message?.contains("InvalidAlgorithmParameterException") == true -> "解密失败，请检查文件和密码"
                        // 其他错误
                        else -> "导入失败: ${e.message?.split(":")?.firstOrNull() ?: e.javaClass.simpleName}"
                    }
                    Toast.makeText(this@SettingsActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 显示主密码输入对话框
    private fun showPasswordDialog(title: String, hint: String, onPasswordEntered: (String) -> Unit) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle(title)
        
        // 创建密码输入框
        val passwordEditText = com.google.android.material.textfield.TextInputEditText(this)
        passwordEditText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        passwordEditText.hint = hint
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 20, 40, 0)
        layout.addView(passwordEditText)
        builder.setView(layout)
        
        builder.setPositiveButton("确认") { dialog, _ ->
            val password = passwordEditText.text.toString()
            if (password.isNotBlank()) {
                onPasswordEntered(password)
            } else {
                Toast.makeText(this@SettingsActivity, "密码不能为空", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        
        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.dismiss()
        }
        
        builder.show()
    }

    companion object {
        private const val REQUEST_CODE_EXPORT = 1001
        private const val REQUEST_CODE_IMPORT = 1002
    }

    private fun setupLogout() {
        findViewById<Button>(R.id.btn_logout).setOnClickListener {
            // Completely close the app
            finishAffinity() // Finish all activities in the task
            System.exit(0) // Terminate the app process
        }
    }
}
