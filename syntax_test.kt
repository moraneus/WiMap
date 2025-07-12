// Quick syntax validation test
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

fun testLazyListSyntax() {
    // Test that our LazyList syntax fix is correct
    LazyColumn {
        items(
            items = listOf("test"),
            key = { item -> "${item}_test" }
        ) { item ->
            // Content
        }
        
        item(key = "test_key") {
            // Content
        }
    }
}