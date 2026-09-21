package dev.bee.kanjianki.multiplatform.compose.library.conventions.fixture

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.bee.kanjianki.multiplatform.compose.library.conventions.fixture.generated.resources.Res
import dev.bee.kanjianki.multiplatform.compose.library.conventions.fixture.generated.resources.foundation_label
import org.jetbrains.compose.resources.stringResource

fun platformNeutralLabel(): String = "Kani shared UI foundation"

@Composable
fun SharedFixture() {
    Text(stringResource(Res.string.foundation_label))
}
