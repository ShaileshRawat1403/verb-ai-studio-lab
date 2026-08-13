import re
with open('app/src/main/java/com/example/verb/ui/FileExplorerDrawer.kt', 'r') as f:
    text = f.read()

text = text.replace("Icon(\n                            Icons.Default.ArrowBack,", "Icon(\n                            Icons.AutoMirrored.Filled.ArrowBack,")

with open('app/src/main/java/com/example/verb/ui/FileExplorerDrawer.kt', 'w') as f:
    f.write(text)
