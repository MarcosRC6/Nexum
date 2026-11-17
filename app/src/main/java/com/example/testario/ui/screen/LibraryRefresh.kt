package com.example.testario.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.testario.viewmodel.LibraryViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Native pull-to-refresh for the two Biblioteca tabs. Does not re-import, delete,
 * or silently substitute any topic; the local store reconciles pending writes first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPullToRefreshBox(
    libraryViewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) {
                isRefreshing = true
                scope.launch {
                    try {
                        val count = libraryViewModel.refreshLibraryFromDisk()
                        Toast.makeText(
                            context,
                            "Biblioteca actualizada: $count temas",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            "No se pudo actualizar la Biblioteca.",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        isRefreshing = false
                    }
                }
            }
        },
        modifier = modifier,
        content = content
    )
}
