import re
text = "Verb Local PTY Active\nType 'help', 'curl -fsSL ... | sh'"
pathRegex = re.compile(r"(?<=^|\s)(/[a-zA-Z0-9_.-]+)+|(\./[a-zA-Z0-9_.-]+)+|~(/[a-zA-Z0-9_.-]+)*")
print("Regex compiles")
for match in pathRegex.finditer(text):
    print(match.group(0))
