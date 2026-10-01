import sys
import re

with open('app/src/main/java/com/example/ui/screens/DashboardScreen.kt', 'r') as f:
    content = f.read()

content = re.sub(r'ModalBottomSheet\(\s*onDismissRequest', r'ModalBottomSheet(\n            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),\n            onDismissRequest', content)

with open('app/src/main/java/com/example/ui/screens/DashboardScreen.kt', 'w') as f:
    f.write(content)
