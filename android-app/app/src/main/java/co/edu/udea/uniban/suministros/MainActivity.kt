package co.edu.udea.uniban.suministros

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.edu.udea.uniban.suministros.ui.navigation.AppNavHost
import co.edu.udea.uniban.suministros.ui.theme.UnibanTheme

/** Única Activity de la app: aloja el árbol de Compose y el grafo de navegación. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnibanTheme {
                AppNavHost((application as UnibanApplication).inventoryRepository)
            }
        }
    }
}
