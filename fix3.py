with open('app/build.gradle.kts', 'r') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if 'buildConfigField' in line:
        lines[i] = ""
with open('app/build.gradle.kts', 'w') as f:
    f.writelines(lines)
