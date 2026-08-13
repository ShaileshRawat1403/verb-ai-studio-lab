with open('app/build.gradle.kts', 'r') as f:
    text = f.read()
text = text.replace('buildConfigField("String", "GEMINI_API_KEY", "\\"\\\\\\"\\\\\\"\\"")', 'buildConfigField("String", "GEMINI_API_KEY", "\\"\\\\\\"\\\\\\"\\"")\n    buildConfigField("String", "OPENAI_API_KEY", "\\"\\\\\\"\\\\\\"\\"")')
with open('app/build.gradle.kts', 'w') as f:
    f.write(text)
print("Updated gradle")
