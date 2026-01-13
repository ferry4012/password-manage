package com.example.mykeys

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class EditEntryActivity : AppCompatActivity() {
    private var editingId: String? = null
    
    private lateinit var layoutTitle: TextInputLayout
    private lateinit var layoutPassword: TextInputLayout
    private lateinit var etTitle: TextInputEditText
    private lateinit var etAccount: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etNote: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_entry)
        
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_simple)
        toolbar.setNavigationOnClickListener { finish() }

        layoutTitle = findViewById(R.id.layoutTitle)
        layoutPassword = findViewById(R.id.layoutPassword)
        etTitle = findViewById(R.id.etTitle)
        etAccount = findViewById(R.id.etAccount)
        etPassword = findViewById(R.id.etPassword)
        etNote = findViewById(R.id.etNote)

        editingId = intent.getStringExtra("id")
        if (editingId != null) {
            lifecycleScope.launch {
                val e = PasswordRepository.find(this@EditEntryActivity, editingId!!)
                if (e != null) {
                    runOnUiThread {
                        toolbar.title = getString(R.string.edit)
                        etTitle.setText(e.title)
                        etAccount.setText(e.account)
                        etPassword.setText(e.password)
                        etNote.setText(e.note)
                    }
                }
            }
        } else {
            toolbar.title = getString(R.string.add_new_password)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_edit, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveEntry()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Flag to prevent double saving
    private var isSaving = false
    
    private fun saveEntry() {
        // Prevent double tapping
        if (isSaving) return
        isSaving = true
        
        val t = etTitle.text?.toString()?.trim().orEmpty()
        val p = etPassword.text?.toString()?.trim().orEmpty()
        
        if (t.isEmpty()) {
            layoutTitle.error = getString(R.string.title_required)
            isSaving = false
            return
        }
        layoutTitle.error = null
        
        if (p.isEmpty()) {
            layoutPassword.error = getString(R.string.password_empty)
            isSaving = false
            return
        }
        layoutPassword.error = null
        
        val e = PasswordEntry(
            id = editingId ?: java.util.UUID.randomUUID().toString(),
            title = t,
            account = etAccount.text?.toString()?.trim().orEmpty(),
            password = p,
            note = etNote.text?.toString()?.trim().orEmpty()
        )
        
        lifecycleScope.launch {
            if (editingId == null) {
                PasswordRepository.insert(this@EditEntryActivity, e)
            } else {
                PasswordRepository.update(this@EditEntryActivity, e)
            }
            runOnUiThread {
                finish()
                isSaving = false
            }
        }
    }
}
