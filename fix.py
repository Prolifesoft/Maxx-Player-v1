with open('app/build.gradle.kts', 'r') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if 'buildConfigField' in line:
        lines[i] = '    buildConfigField("String", "GOOGLE_CLIENT_ID", "\\\"" + (System.getenv("GOOGLE_CLIENT_ID") ?: "") + "\\\"")\n'
with open('app/build.gradle.kts', 'w') as f:
    f.writelines(lines)
