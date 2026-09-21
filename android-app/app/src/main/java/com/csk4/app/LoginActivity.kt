package com.csk4.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etUser = findViewById<EditText>(R.id.etUser)
        val etPass = findViewById<EditText>(R.id.etPass)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val u = etUser.text.toString().trim()
            val p = etPass.text.toString()
            if (u.isEmpty() || p.isEmpty()) {
                Toast.makeText(this, "Username & password daalein", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val json = JSONObject().apply {
                put("username", u); put("password", p)
            }
            Volley.newRequestQueue(this).add(JsonObjectRequest(
                Request.Method.POST, "${Config.SERVER_URL}/api/admin/login", json,
                { resp ->
                    if (resp.optBoolean("success")) {
                        Toast.makeText(this, "✅ Login", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                },
                { Toast.makeText(this, "❌ Galat credentials", Toast.LENGTH_SHORT).show() }
            ))
        }
    }
}