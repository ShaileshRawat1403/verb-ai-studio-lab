import re
with open('app/src/main/java/com/example/verb/ui/FileExplorerDrawer.kt', 'r') as f:
    text = f.read()

text = text.replace("import androidx.compose.material.icons.automirrored.filled.ArrowBack", "import androidx.compose.material.icons.filled.ArrowBack")
text = text.replace("                            ArrowBack,", "                            @Suppress(\"DEPRECATION\")\n                            Icons.Default.ArrowBack,")

with open('app/src/main/java/com/example/verb/ui/FileExplorerDrawer.kt', 'w') as f:
    f.write(text)
