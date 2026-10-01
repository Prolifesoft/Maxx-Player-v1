with open('app/src/main/java/com/example/ui/screens/DashboardScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("ImdbTimeFrame.WEEKLY", "ImdbTimeFrame.THIRTY_DAYS")
content = content.replace("ImdbTimeFrame.MONTHLY", "ImdbTimeFrame.SIXTY_DAYS")

with open('app/src/main/java/com/example/ui/screens/DashboardScreen.kt', 'w') as f:
    f.write(content)
