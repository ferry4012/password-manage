package com.example.mykeys

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.widget.Toast
import android.content.Intent
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
        
        // No toolbar in the new card layout
        // val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        // setSupportActionBar(toolbar)

        val title = findViewById<TextView>(R.id.title)
        val subtitle = findViewById<TextView>(R.id.subtitle)
        val inputPasswordLayout = findViewById<TextInputLayout>(R.id.input_password_layout)
        val inputConfirmContainer = findViewById<View>(R.id.confirm_container)
        val inputConfirmLayout = findViewById<TextInputLayout>(R.id.input_confirm_layout)
        val inputPassword = findViewById<TextInputEditText>(R.id.input_password)
        val inputConfirm = findViewById<TextInputEditText>(R.id.input_confirm)
        val btnAction = findViewById<MaterialButton>(R.id.btn_action)

        // Initial state: button disabled
        btnAction.isEnabled = false

        if (Prefs.isMasterSet(this)) {
            // Unlock mode
            title.text = getString(R.string.app_name)
            subtitle.text = getString(R.string.unlock_desc)
            inputConfirmContainer.visibility = View.GONE
            btnAction.text = getString(R.string.unlock)
            inputPassword.hint = getString(R.string.enter_master_password)
            
            // Watch password input for unlock mode
            inputPassword.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    btnAction.isEnabled = s?.trim().isNullOrEmpty().not()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            
            btnAction.setOnClickListener {
                val pwd = inputPassword.text?.toString()?.trim().orEmpty()
                if (pwd.isEmpty()) {
                    inputPasswordLayout.error = getString(R.string.password_empty)
                    return@setOnClickListener
                }
                inputPasswordLayout.error = null
                
                // Show loading state
                btnAction.isEnabled = false
                
                // Show "正在解锁..." toast
                val toast = Toast.makeText(this, "正在解锁...", Toast.LENGTH_LONG)
                toast.show()
                
                // Move KDF operations to coroutine to avoid UI blocking
                lifecycleScope.launch {
                    try {
                        // First verify the master password
                        val isVerified = Prefs.verifyMaster(this@AuthActivity, pwd)
                        
                        if (isVerified) {
                            // Set encryption key - this internally calls KDF again, but we can't avoid it
                            // because it uses a different salt for encryption
                            val encryptionSalt = Prefs.getEncryptionSalt(this@AuthActivity)
                            val encryptionKey = EncryptionUtils.deriveEncryptionKey(pwd, encryptionSalt)
                            PasswordRepository.setEncryptionKey(encryptionKey)
                            
                            // Start main activity on main thread
                            runOnUiThread {
                                toast.cancel() // Cancel the loading toast
                                startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                                finish()
                            }
                        } else {
                            runOnUiThread {
                                toast.cancel() // Cancel the loading toast
                                inputPasswordLayout.error = getString(R.string.password_wrong)
                                btnAction.isEnabled = true
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            toast.cancel() // Cancel the loading toast
                            inputPasswordLayout.error = getString(R.string.password_wrong)
                            btnAction.isEnabled = true
                        }
                    }
                }
            }
        } else {
            // Setup mode
            title.text = getString(R.string.app_name)
            subtitle.text = getString(R.string.set_master_desc)
            inputConfirmContainer.visibility = View.VISIBLE
            btnAction.text = getString(R.string.setup_and_start)
            inputPassword.hint = getString(R.string.enter_master_password)
            
            // Watch both password inputs for setup mode
            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val pwd = inputPassword.text?.toString()?.trim().orEmpty()
                    val confirm = inputConfirm.text?.toString()?.trim().orEmpty()
                    btnAction.isEnabled = pwd.isNotEmpty() && confirm.isNotEmpty() && pwd == confirm
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            
            inputPassword.addTextChangedListener(textWatcher)
            inputConfirm.addTextChangedListener(textWatcher)
            
            btnAction.setOnClickListener {
                val pwd = inputPassword.text?.toString()?.trim().orEmpty()
                val confirm = inputConfirm.text?.toString()?.trim().orEmpty()
                if (pwd.isEmpty()) {
                    inputPasswordLayout.error = getString(R.string.password_empty)
                    return@setOnClickListener
                }
                inputPasswordLayout.error = null
                if (pwd != confirm) {
                    inputConfirmLayout.error = getString(R.string.password_not_match)
                    return@setOnClickListener
                }
                inputConfirmLayout.error = null
                
                // Show loading state
                btnAction.isEnabled = false
                
                // Move KDF operations to coroutine to avoid UI blocking
                lifecycleScope.launch {
                    try {
                        // Set master password
                        Prefs.setMaster(this@AuthActivity, pwd)
                        
                        // Set encryption key
                        val encryptionSalt = Prefs.getEncryptionSalt(this@AuthActivity)
                        val encryptionKey = EncryptionUtils.deriveEncryptionKey(pwd, encryptionSalt)
                        PasswordRepository.setEncryptionKey(encryptionKey)
                        
                        // Start main activity on main thread
                        runOnUiThread {
                            startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                            finish()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            inputPasswordLayout.error = getString(R.string.password_wrong)
                            btnAction.isEnabled = true
                        }
                    }
                }
            }
        }
    }
}
