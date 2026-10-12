package com.example.droidinspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.droidinspector.ui.AuditScreen
import com.example.droidinspector.ui.AuditViewModel
import com.example.droidinspector.ui.theme.DroidInspectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DroidInspectorTheme {
                val auditViewModel: AuditViewModel = viewModel(
                    factory = AuditViewModel.factory(applicationContext),
                )
                AuditScreen(auditViewModel)
            }
        }
    }
}
