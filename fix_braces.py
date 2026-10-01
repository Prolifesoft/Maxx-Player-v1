import sys

with open('app/src/main/java/com/example/ui/components/CategoryManagementDialog.kt', 'r') as f:
    lines = f.readlines()

# find "Spacer(modifier = Modifier.height(12.dp))" at the end
end_index = -1
for i in range(len(lines)-1, -1, -1):
    if "Spacer(modifier = Modifier.height(12.dp))" in lines[i]:
        end_index = i
        break

if end_index != -1:
    lines = lines[:end_index] + ["            }\n", "        }\n", "    }\n", "}\n"]

with open('app/src/main/java/com/example/ui/components/CategoryManagementDialog.kt', 'w') as f:
    f.writelines(lines)
