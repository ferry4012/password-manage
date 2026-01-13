package com.example.mykeys

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ViewEntryActivity : AppCompatActivity() {
    private var entry: PasswordEntry? = null
    private var isPasswordVisible = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_entry)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_simple)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item -> onOptionsItemSelected(item) }
        val id = intent.getStringExtra("id")
        
        if (id != null) {
            lifecycleScope.launch {
                entry = PasswordRepository.find(this@ViewEntryActivity, id)
                runOnUiThread {
                    val tvTitle = findViewById<TextView>(R.id.tvTitle)
                    val tvAccount = findViewById<TextView>(R.id.tvAccount)
                    val tvPassword = findViewById<TextView>(R.id.tvPassword)
                    val tvNote = findViewById<TextView>(R.id.tvNote)
                    val btnToggle = findViewById<ImageView>(R.id.btnTogglePassword)
                    if (entry != null) {
                        toolbar.title = entry!!.title
                        tvTitle.text = entry!!.title
                        tvAccount.text = entry!!.account
                        tvPassword.text = mask(entry!!.password)
                        tvNote.text = entry!!.note
                    }
                    btnToggle.setOnClickListener {
                        if (entry != null) {
                            isPasswordVisible = !isPasswordVisible
                            tvPassword.text = if (isPasswordVisible) entry!!.password else mask(entry!!.password)
                            btnToggle.setImageResource(if (isPasswordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
                        }
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                if (entry != null) {
                    val i = android.content.Intent(this, EditEntryActivity::class.java)
                    i.putExtra("id", entry!!.id)
                    startActivity(i)
                }
                true
            }
            R.id.action_delete -> {
                if (entry != null) {
                    AlertDialog.Builder(this)
                        .setMessage(R.string.delete_confirm)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            lifecycleScope.launch {
                                PasswordRepository.delete(this@ViewEntryActivity, entry!!.id)
                                runOnUiThread {
                                    finish()
                                }
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun mask(s: String): String {
        if (s.isEmpty()) return ""
        return "••••••"
    }
}
