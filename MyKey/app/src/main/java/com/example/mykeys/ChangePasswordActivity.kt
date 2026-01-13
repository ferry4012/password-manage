package com.example.mykeys

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var etOldPassword: TextInputEditText
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var layoutOldPassword: TextInputLayout
    private lateinit var layoutNewPassword: TextInputLayout
    private lateinit var layoutConfirmPassword: TextInputLayout
    private lateinit var btnChangePassword: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        // Setup Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "设置主密码"
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_simple)
        
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Initialize views
        layoutOldPassword = findViewById(R.id.layout_old_password)
        layoutNewPassword = findViewById(R.id.layout_new_password)
        layoutConfirmPassword = findViewById(R.id.layout_confirm_password)
        etOldPassword = findViewById(R.id.et_old_password)
        etNewPassword = findViewById(R.id.et_new_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        btnChangePassword = findViewById(R.id.btn_change_password)

        // Initialize button as disabled
        btnChangePassword.isEnabled = false

        // Setup TextWatcher to monitor input fields
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateButtonState()
            }
            
            override fun afterTextChanged(s: Editable?) {}
        }

        // Add TextWatcher to all input fields
        etOldPassword.addTextChangedListener(textWatcher)
        etNewPassword.addTextChangedListener(textWatcher)
        etConfirmPassword.addTextChangedListener(textWatcher)

        // Setup change password button
        btnChangePassword.setOnClickListener {
            changePassword()
        }
    }

    private fun updateButtonState() {
        val oldPass = etOldPassword.text.toString().trim()
        val newPass = etNewPassword.text.toString().trim()
        val confirmPass = etConfirmPassword.text.toString().trim()
        
        // Enable button only when all fields have content
        btnChangePassword.isEnabled = oldPass.isNotEmpty() && newPass.isNotEmpty() && confirmPass.isNotEmpty()
    }

    private fun changePassword() {
        val oldPass = etOldPassword.text.toString().trim()
        val newPass = etNewPassword.text.toString().trim()
        val confirmPass = etConfirmPassword.text.toString().trim()

        // Validate inputs
        if (oldPass.isEmpty()) {
            layoutOldPassword.error = "请输入旧密码"
            return
        } else {
            layoutOldPassword.error = null
        }

        if (newPass.isEmpty()) {
            layoutNewPassword.error = "请输入新密码"
            return
        } else {
            layoutNewPassword.error = null
        }

        if (confirmPass.isEmpty()) {
            layoutConfirmPassword.error = "请确认新密码"
            return
        } else {
            layoutConfirmPassword.error = null
        }

        // Verify old password
        if (!Prefs.verifyMaster(this, oldPass)) {
            layoutOldPassword.error = "旧密码错误"
            return
        } else {
            layoutOldPassword.error = null
        }

        if (newPass != confirmPass) {
            layoutConfirmPassword.error = "两次输入的新密码不一致"
            return
        } else {
            layoutConfirmPassword.error = null
        }

        // Show loading state
        btnChangePassword.isEnabled = false
        
        // Move password change logic to coroutine
        lifecycleScope.launch {
            try {
                // Get old encryption key
                val oldSalt = Prefs.getEncryptionSalt(this@ChangePasswordActivity)
                val oldEncryptionKey = EncryptionUtils.deriveEncryptionKey(oldPass, oldSalt)
                
                // Set old encryption key temporarily to decrypt existing entries
                PasswordRepository.setEncryptionKey(oldEncryptionKey)
                
                // Get all existing entries and decrypt them with old key
                val allEntries = PasswordRepository.getAll(this@ChangePasswordActivity)
                
                // Update master password
                Prefs.setMaster(this@ChangePasswordActivity, newPass)
                
                // Get new encryption key
                val newEncryptionKey = EncryptionUtils.deriveEncryptionKey(newPass, oldSalt)
                
                // Set new encryption key for re-encryption
                PasswordRepository.setEncryptionKey(newEncryptionKey)
                
                // Re-encrypt all entries with new key and update in database
                for (entry in allEntries) {
                    PasswordRepository.update(this@ChangePasswordActivity, entry)
                }
                
                runOnUiThread {
                    Toast.makeText(this@ChangePasswordActivity, "主密码修改成功", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ChangePasswordActivity, "主密码修改失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnChangePassword.isEnabled = true
                }
            }
        }
    }
}
