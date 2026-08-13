import re
with open('app/src/main/java/com/example/verb/MainActivity.kt', 'r') as f:
    text = f.read()

# We can append some code to MainActivity to dump logs to a file on disk?
